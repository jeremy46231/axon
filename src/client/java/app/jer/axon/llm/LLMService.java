package app.jer.axon.llm;

import app.jer.axon.Axon;
import app.jer.axon.Utils;
import app.jer.axon.llm.functions.FunctionRegistrar;
import app.jer.axon.service.StatusService;
import io.github.sashirestela.openai.SimpleOpenAIGeminiGoogle;
import io.github.sashirestela.openai.common.function.FunctionCall;
import io.github.sashirestela.openai.common.function.FunctionExecutor;
import io.github.sashirestela.openai.common.tool.ToolCall;
import io.github.sashirestela.openai.domain.chat.Chat;
import io.github.sashirestela.openai.domain.chat.ChatMessage;
import io.github.sashirestela.openai.domain.chat.ChatRequest;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class LLMService {
    private static final SimpleOpenAIGeminiGoogle llmApi = SimpleOpenAIGeminiGoogle.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .build();
    private static final FunctionExecutor functionExecutor = FunctionRegistrar.getFunctions();
    private static final List<ChatMessage> messages = Collections.synchronizedList(new ArrayList<>());
    private static final ExecutorService llmExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Axon-LLM-Processor");
        thread.setDaemon(true); // Allows JVM to exit if this is the only thread left
        return thread;
    });

    private static ChatMessage.SystemMessage systemMessage() {
        String status = StatusService.status();

        return ChatMessage.SystemMessage.of(String.format(
                """
                        You are Axon, an intelligent, efficient, and conversational Minecraft bot. You take the form \
                        a mod that controls the user's Minecraft client. You are capable of running a variety of \
                        functions to control the game.
                        
                        Your functions (except for wait) are all very fast, so you can run them before providing any \
                        user-visible output. The baritone_* functions control Baritone, a minecraft pathfinding bot \
                        that you can use to navigate the world and perform tasks. If the user asks you to obtain a \
                        certain amount of the item, remember that the baritone_mine tool accepts the total count of \
                        items to have in the inventory. If the inventory already contains some of the item, decide \
                        whether you want to mine up to a total quantity, or mine the user's requested amount on top \
                        of what was already there. If you want to go to the surface, assume that Y level 64 is a good \
                        choice for baritone_goto_y, unless you have a waypoint on the surface in that area.
                        
                        When writing messages to the user, remember that you are typing in chat with no special \
                        formatting. Use plain text formatting, no bold or anything. If you want to format a list, you \
                        may use "- " or "1. " to start each line, just remember that nothing will be parsing the \
                        formatting.
                        
                        Make assumptions on behalf of the user, assume reasonable defaults and avoid asking clarifying \
                        questions if at all possible. Be concise and efficient in your responses, but if there is a \
                        clear next step, you can offer to do it. The exception to this is if the user asks you about \
                        yourself or your capabilities, in which case you should be more verbose and explain your \
                        features in detail.
                        
                        Current Status (always up-to-date):
                        %s
                        """,
                status
        ));
    }

    public static void userMessage(String text) {
        messages.add(ChatMessage.UserMessage.of(text));
        Axon.chatMessage(Utils.prefixText("You").append(text));

        llmExecutor.submit(() -> runLLM(20));
    }

    public static void clearChat() {
        llmExecutor.submit(() -> {
            messages.clear();
            Axon.chatMessage(Utils.prefixText("Axon")
                    .append(Text.literal("Chat cleared.").formatted(Formatting.GRAY))
            );
        });
    }

    private static void runLLM(int maxTurns) {
        if (maxTurns <= 0) {
            Axon.LOGGER.warn("LLM processing reached max turns limit.");
            return;
        }
        FunctionExecutor functionExecutor = FunctionRegistrar.getFunctions(); // temp for dev

        // Get messages
        List<ChatMessage> currentMessages;
        synchronized (messages) {
            currentMessages = new ArrayList<>(messages);
            currentMessages.addFirst(systemMessage());
        }
        Axon.LOGGER.info("LLM Thread: Sending {} messages to LLM.", currentMessages.size());

        // Send messages to LLM
        ChatRequest chatRequest = ChatRequest.builder()
                .model("gemini-2.0-flash") // "gemini-2.5-pro-exp-03-25"
                .messages(currentMessages)
                .tools(functionExecutor.getToolFunctions())
                .temperature(0.0)
                .build();

        CompletableFuture<Chat> futureChat = llmApi.chatCompletions().create(chatRequest);
        Chat chatResponse = futureChat.join(); // block this thread until the response is received
        ChatMessage.ResponseMessage message = chatResponse.firstMessage();
        Axon.LOGGER.info("LLM Thread: Received response. {}", message);

        // Handle LLM text message
        String messageText = Optional.ofNullable(message.getContent())
                .orElse("")
                .trim();
        if (!messageText.isEmpty()) {
            messages.add(ChatMessage.AssistantMessage.of(messageText));
            Axon.chatMessage(Utils.prefixText("Axon").append(messageText));
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
                Axon.LOGGER.info(
                        "LLM Thread: Executing function {} with arguments {}.",
                        function.getName(), function.getArguments()
                );

                try {
                    Object rawResult = functionExecutor.execute(function);
                    Axon.LOGGER.info("LLM Thread: Function {} returned: {}", function.getName(), rawResult);
                    if (rawResult instanceof CompletableFuture<?> futureResult) {
                        result = futureResult.join().toString();
                    } else {
                        result = rawResult.toString();
                    }
                } catch (RuntimeException e) {
                    Axon.LOGGER.error("Error when running function", e);
                    result = "Error: " + e.getMessage();
                }
//                AxonClient.chatMessage("[" + function.getName() + "] " + function.getArguments() + " -> " + result);

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
