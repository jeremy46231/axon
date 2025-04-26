package app.jer.axon.llm;

import app.jer.axon.AxonClient;
import io.github.sashirestela.openai.SimpleOpenAIGeminiGoogle;
import io.github.sashirestela.openai.common.function.FunctionExecutor;
import io.github.sashirestela.openai.common.tool.ToolCall;
import io.github.sashirestela.openai.domain.chat.ChatMessage;
import io.github.sashirestela.openai.domain.chat.ChatRequest;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class LLMService {
    private static final SimpleOpenAIGeminiGoogle llmApi = SimpleOpenAIGeminiGoogle.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .build();
    private static final FunctionExecutor functionExecutor = LLMFunctions.getFunctions();
    private static final ArrayList<ChatMessage> messages = initialMessages();

    private static ArrayList<ChatMessage> initialMessages() {
        var messages = new ArrayList<ChatMessage>();
        messages.add(ChatMessage.SystemMessage.of("You are Axon, an intelligent, efficient, and conversational Minecraft bot. You can chat with the user in Minecraft, using plaintext with no special formatting."));
        return messages;
    }

    public static void userMessage(String text) {
        messages.add(ChatMessage.UserMessage.of(text, "User"));
        AxonClient.chatMessage("[User] " + text);
        runLLM(5);
    }

    public static void clearChat() {
        messages.clear();
        messages.addAll(initialMessages());
    }

    private static void runLLM(int maxTurns) {
        FunctionExecutor functionExecutor = LLMFunctions.getFunctions(); // temp for dev

        AxonClient.LOGGER.info("Messages: {}", messages);

        var chatRequest = ChatRequest.builder()
                .model("gemini-2.0-flash")
                .messages(messages)
                .tools(functionExecutor.getToolFunctions())
                .temperature(0.0)
                .maxCompletionTokens(300)
                .build();
        var futureChat = llmApi.chatCompletions().create(chatRequest);
        var chatResponse = futureChat.join();

        var message = chatResponse.firstMessage();
        var messageText = Optional.ofNullable(message.getContent())
                .orElse("")
                .trim();
        if (!messageText.isEmpty()) {
            messages.add(ChatMessage.AssistantMessage.of(messageText));
            AxonClient.chatMessage("[Axon] " + messageText);
        }

        var toolCalls = message.getToolCalls();
        if (toolCalls != null) {
            // make sure that all tool calls have an associated ID
            toolCalls = toolCalls.stream()
                    .map(toolCall -> {
                        var id = toolCall.getId();
                        if (id == null || id.isEmpty()) {
                            // if no ID, generate one
                            id = UUID.randomUUID().toString();
                        }
                        var newToolCall = new ToolCall(
                                toolCall.getIndex(),
                                id,
                                toolCall.getType(),
                                toolCall.getFunction()
                        );
                        return newToolCall;
                    })
                    .collect(Collectors.toList());

            var toolCallMessage = ChatMessage.AssistantMessage.builder()
                    .toolCalls(toolCalls)
                    .content(" ")
                    .build();
            messages.add(toolCallMessage);

            for (var toolCall : toolCalls) {
                var function = toolCall.getFunction();
                if (function == null) continue;
                String result = "";
                try {
                    result = Optional.ofNullable(functionExecutor.execute(function)).orElse("").toString();
                } catch (RuntimeException e) {
                    AxonClient.LOGGER.error("Error when running function", e);
                    result = "Error: " + e.toString();
                }
                var toolMessage = ChatMessage.ToolMessage.of(result, toolCall.getId());
                AxonClient.chatMessage("[" + function.getName() + "] " + function.getArguments() + " -> " + result);
                messages.add(toolMessage);
            }
            if (maxTurns > 1) {
                runLLM(maxTurns - 1);
            }
        }

    }
}
