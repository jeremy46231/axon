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
            .apiKey(System.getenv("GEMINI_API_KEY"))
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
                        You are Axon, an intelligent, efficient, and conversational Minecraft bot. You take the form a mod that controls the user's Minecraft client. You are capable of running a variety of functions to control the game.
                        
                        When writing messages to the user, remember that you are typing in chat with no special formatting. Use plain text formatting, no bold or anything. If you want to format a list, you may use "- " or "1. " to start each line, just remember that nothing will be parsing the formatting. Always clean up the raw code output found in the status and function results before sending it to the user.
                        
                        Make assumptions on behalf of the user, assume reasonable defaults and avoid asking clarifying questions if at all possible. Be concise and efficient in your responses, but if there is a clear next step, you can offer to do it. The exception to this is if the user asks you about yourself or your capabilities, in which case you should be more verbose and explain your features in detail.
                        
                        Your functions (except for wait and baritone_wait_process) are all very fast, so run them before providing any user-visible output. The baritone_* functions control Baritone, a minecraft pathfinding bot that you can use to navigate the world and perform tasks.
                        
                        If the user asks you to obtain a certain amount of the item, remember that the baritone_mine tool accepts the total count of items to have in the inventory. If the inventory already contains some of the item, decide whether you want to mine up to a total quantity, or mine the user's requested amount on top of what was already there. If the user simply asks you to mine X blocks, assume they mean X blocks on top of what they already have. If you need a certain number of blocks for a recipie or similar,  If you want to go to the surface, assume that Y level 64 is a good choice for baritone_goto_y, unless you have a waypoint on the surface in that area.
                        
                        When running a Baritone process that will go indefinitely, if you want to be done at some point, inform the user that you are running the process for a specific amount of time, then run the wait tool. More likely, you will run a Baritone tool which will complete when done. In that case, after informing the user, you can run the baritone_wait_process tool to wait for the process to finish. Pass a duration to the function to determine when to check back in on the progress. If you do not expect the timeout to be reached, do not share it with the user or talk about how you will check back in. For long processes, make sure your timeout is at least every 10 minutes, more often for shorter or more important tasks. If nothing is wrong, you can run baritone_wait_process again without sending a message to the user (though if you want to tell them something you can). Do not forget to wait for the process to end if you want to say or do anything when it's done.
                        
                        Current Status (always up-to-date):
                        %s""",
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
        Axon.statusOverlay("Generating...");

        // Send messages to LLM
        ChatRequest chatRequest = ChatRequest.builder()
                .model("gemini-2.0-flash") // "gemini-2.5-pro-exp-03-25"
                .messages(currentMessages)
                .tools(functionExecutor.getToolFunctions())
                .temperature(0.0)
                .parallelToolCalls(true)
                .build();

        Chat chatResponse;
        try {
            CompletableFuture<Chat> futureChat = llmApi.chatCompletions().create(chatRequest);
            chatResponse = futureChat.join(); // block this thread until the response is received
        } catch (Exception e) {
            Axon.LOGGER.error("LLM Thread: Error while running LLM", e);
            Axon.chatMessage(Utils.prefixText("Axon")
                    .append(Text.literal("Error while running LLM: " + e.getMessage())
                            .formatted(Formatting.RED))
            );
            return;
        }
        ChatMessage.ResponseMessage message = chatResponse.firstMessage();
        Axon.statusOverlay("Generated");

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
                Axon.LOGGER.info(
                        "LLM Thread: Executing function {} with arguments {}.",
                        function.getName(), function.getArguments()
                );
                Axon.statusOverlay("Executing function " + function.getName() + "...");

                Axon.chatMessage(Utils.prefixText("Tool Call")
                        .append(Text.literal(function.getName())
                                .formatted(Formatting.GRAY))
                        .append(Text.literal(" with arguments:\n")
                                .formatted(Formatting.DARK_GRAY))
                        .append(Text.literal(function.getArguments())
                                .formatted(Formatting.GRAY))
                );
                String result;
                try {
                    Object rawResult = functionExecutor.execute(function);
                    Axon.LOGGER.info("LLM Thread: Function {} returned: {}", function.getName(), rawResult);
                    Axon.statusOverlay("Executed " + function.getName());
                    if (rawResult instanceof CompletableFuture<?> futureResult) {
                        result = futureResult.join().toString();
                    } else {
                        result = rawResult.toString();
                    }
                } catch (RuntimeException e) {
                    Axon.LOGGER.error("LLM Thread: Error when running function", e);
                    result = "Error: " + e.getMessage();
                }
                Axon.chatMessage(Utils.prefixText("Tool Call")
                        .append(Text.literal(function.getName())
                                .formatted(Formatting.GRAY))
                        .append(Text.literal(" returned:\n")
                                .formatted(Formatting.DARK_GRAY))
                        .append(Text.literal(result)
                                .formatted(Formatting.GRAY))
                );

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
