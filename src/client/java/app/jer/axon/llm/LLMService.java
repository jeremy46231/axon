package app.jer.axon.llm;

import app.jer.axon.AxonClient;
import io.github.sashirestela.openai.SimpleOpenAIGeminiGoogle;
import io.github.sashirestela.openai.common.function.FunctionCall;
import io.github.sashirestela.openai.common.function.FunctionExecutor;
import io.github.sashirestela.openai.common.tool.ToolCall;
import io.github.sashirestela.openai.domain.chat.Chat;
import io.github.sashirestela.openai.domain.chat.ChatMessage;
import io.github.sashirestela.openai.domain.chat.ChatRequest;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class LLMService {
    private static final SimpleOpenAIGeminiGoogle llmApi = SimpleOpenAIGeminiGoogle.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .build();
    private static final FunctionExecutor functionExecutor = LLMFunctions.getFunctions();
    private static final List<ChatMessage> messages = Collections.synchronizedList(initialMessages());

    private static ArrayList<ChatMessage> initialMessages() {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.SystemMessage.of("You are Axon, an intelligent, efficient, and conversational Minecraft bot. You can chat with the user in Minecraft, using plaintext with no special formatting."));
        return messages;
    }

    public static void userMessage(String text) {
        messages.add(ChatMessage.UserMessage.of(text));
        AxonClient.chatMessage("[User] " + text);

        llmExecutor.submit(() -> runLLM(5));
    }
    public static void clearChat() {
        llmExecutor.submit(() -> {
            resetMessages();
            AxonClient.chatMessage("Chat cleared");
        });
    }


    private static final ExecutorService llmExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Axon-LLM-Processor");
        thread.setDaemon(true); // Allows JVM to exit if this is the only thread left
        return thread;
    });
    private static void resetMessages() {
        messages.clear();
        messages.addAll(initialMessages());
    }
    private static void runLLM(int maxTurns) {
        if (maxTurns <= 0) {
            AxonClient.LOGGER.warn("LLM processing reached max turns limit.");
            return;
        }
        FunctionExecutor functionExecutor = LLMFunctions.getFunctions(); // temp for dev

        // Get messages
        List<ChatMessage> currentMessagesSnapshot;
        synchronized (messages) { // Synchronize for safe copying
            currentMessagesSnapshot = new ArrayList<>(messages);
        }
        AxonClient.LOGGER.info("LLM Thread: Sending {} messages to LLM.", currentMessagesSnapshot.size());

        // Send messages to LLM
        ChatRequest chatRequest = ChatRequest.builder()
                .model("gemini-2.0-flash")
                .messages(currentMessagesSnapshot)
                .tools(functionExecutor.getToolFunctions())
                .temperature(0.0)
                .maxCompletionTokens(300)
                .build();

        CompletableFuture<Chat> futureChat = llmApi.chatCompletions().create(chatRequest);
        Chat chatResponse = futureChat.join();
        ChatMessage.ResponseMessage message = chatResponse.firstMessage();
        AxonClient.LOGGER.info("LLM Thread: Received response.");

        // Handle LLM text message
        String messageText = Optional.ofNullable(message.getContent())
                .orElse("")
                .trim();
        if (!messageText.isEmpty()) {
            messages.add(ChatMessage.AssistantMessage.of(messageText));
            AxonClient.chatMessage("[Axon] " + messageText);
        }

        // Handle LLM tool calls
        List<ToolCall> toolCalls = Optional.ofNullable(message.getToolCalls()).orElse(Collections.emptyList());
        boolean requiresFollowUp = false;

        if (!toolCalls.isEmpty()) {
            // Make sure that all tool calls have an associated ID
            toolCalls = toolCalls.stream()
                    .map(toolCall -> {
                        String id = toolCall.getId();
                        if (id == null || id.isEmpty()) {
                            // if no ID, generate one
                            id = UUID.randomUUID().toString();
                        }
                        return new ToolCall(
                                toolCall.getIndex(),
                                id,
                                toolCall.getType(),
                                toolCall.getFunction()
                        );
                    })
                    .collect(Collectors.toList());

            // Add the tool call message to the chat
            ChatMessage.AssistantMessage toolCallMessage = ChatMessage.AssistantMessage.builder()
                    .toolCalls(toolCalls)
                    .content(" ")
                    .build();
            messages.add(toolCallMessage);

            // Execute the tool calls sequentially
            for (ToolCall toolCall : toolCalls) {
                FunctionCall function = toolCall.getFunction();
                if (function == null) continue;
                String result;
                AxonClient.LOGGER.info("LLM Thread: Executing function {} with arguments {}.", function.getName(), function.getArguments());

                try {
                    Object rawResult = functionExecutor.execute(function);
                    AxonClient.LOGGER.info("LLM Thread: Function {} returned {}.", function.getName(), rawResult);
                    if (rawResult instanceof CompletableFuture<?> futureResult) {
                        result = futureResult.join().toString();
                    } else {
                        result = rawResult.toString();
                    }
                } catch (RuntimeException e) {
                    AxonClient.LOGGER.error("Error when running function", e);
                    result = "Error: " + e.getMessage();
                }
                AxonClient.chatMessage("[" + function.getName() + "] " + function.getArguments() + " -> " + result);

                ChatMessage.ToolMessage toolMessage = ChatMessage.ToolMessage.of(result, toolCall.getId());
                requiresFollowUp = true;
                messages.add(toolMessage);
            }
        }

        // If a tool ran, run the LLM again
        if (requiresFollowUp) {
            runLLM(maxTurns - 1);
        }
    }
}
