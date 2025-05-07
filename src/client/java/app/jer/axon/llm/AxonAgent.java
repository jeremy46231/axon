package app.jer.axon.llm;

import app.jer.axon.Axon;
import app.jer.axon.Utils;
import app.jer.axon.llm.functions.FunctionRegistrar;
import app.jer.axon.service.BaritoneService;
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
import java.util.concurrent.*;

public class AxonAgent {
    private static final SimpleOpenAIGeminiGoogle llmApi = SimpleOpenAIGeminiGoogle.builder()
            .apiKey(System.getenv("GEMINI_API_KEY"))
            .build();
    private static final FunctionExecutor functionExecutor = FunctionRegistrar.getFunctions();
    private static final List<ChatMessage> messages = Collections.synchronizedList(new ArrayList<>());
    // Single thread executor for the main agent loop
    private static final ExecutorService agentExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Axon-Agent-Loop");
        thread.setDaemon(true); // Allows JVM to exit if this is the only thread left
        return thread;
    });
    private static final ConcurrentLinkedQueue<AgentInput> inputQueue = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<ToolCall> toolExecutionQueue = new ConcurrentLinkedQueue<>(); // Queue for tools LLM wants to run

    private static volatile AgentState currentState = AgentState.IDLE;

    private static CompletableFuture<Chat> currentLLMRequest = null;

    private static long waitTimeoutMillis = 0; // Timestamp when the current wait expires
    private static String currentWaitingToolCallId = null; // ID of the tool call that initiated a wait

    // Called once during mod initialization
    public static void initialize() {
        agentExecutor.submit(AxonAgent::agentLoop);
        Axon.LOGGER.info("Axon agent loop started.");
    }

    private static ChatMessage.SystemMessage systemMessage() {
        return ChatMessage.SystemMessage.of(SystemPrompt.systemPrompt());
    }

    public static void userMessage(String text) {
        Axon.chatMessage(Utils.prefixText("You").append(text));
        // Enqueue for processing by the agent loop
        inputQueue.offer(new UserCommandInput(text));
    }

    public static void clearChat() {
        // Enqueue a control signal to clear chat
        inputQueue.offer(new ControlInput(ControlType.CLEAR_CHAT, "User requested chat clear"));
    }

    // --- Agent Loop ---

    private static void agentLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                processInputQueue();
                performStateAction();
                //noinspection BusyWait
                Thread.sleep(200); // 5 ticks
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Axon.LOGGER.warn("Agent loop interrupted.");
                break; // Exit loop
            } catch (Exception e) {
                Axon.LOGGER.error("Critical error in agent loop, transitioning to ERROR state", e);
                messages.add(ChatMessage.SystemMessage.of("Critical Error: " + e.getMessage()));
                currentState = AgentState.ERROR;

                inputQueue.clear();
                toolExecutionQueue.clear();
                if (currentLLMRequest != null) {
                    currentLLMRequest.cancel(true);
                }
            }
        }
        Axon.LOGGER.info("Agent loop stopped.");
    }

    private static void processInputQueue() {
        AgentInput input;

        while ((input = inputQueue.poll()) != null) {
            try {
                switch (input) {
                    case UserCommandInput uci -> handleUserInput(uci);
                    case ToolExecutionRequest ter -> handleToolExecutionRequest(ter);
                    case ToolResultInput tri -> handleToolResult(tri);
                    case LLMResponseInput lri -> handleLLMResponse(lri);
                    case ControlInput ci -> handleControlInput(ci);
                }
            } catch (Exception e) {
                Axon.LOGGER.error("Error processing input: {}", input, e);
                messages.add(ChatMessage.SystemMessage.of("Error processing input: " + e.getMessage()));
                currentState = AgentState.ERROR;
            }
        }
    }

    private static void performStateAction() {
        switch (currentState) {
            case IDLE:
                Axon.statusOverlay("Idle");
                break;
            case THINKING:
                if (currentLLMRequest != null && !currentLLMRequest.isDone()) {
                    Axon.statusOverlay("Thinking...");
                } else {
                    // Should have been handled by callback...
                    Axon.LOGGER.warn("LLM request completed but state is still THINKING.");
                }
                break;
            case EXECUTING_TOOL:
                executeNextTool();
                break;
            case WAITING_FOR_BARITONE:
                checkBaritoneWait();
                break;
            case WAITING_FOR_TIME:
                checkTimeWait();
                break;
            case ERROR:
                Axon.statusOverlay("Error - Use /a-clear to attempt a reset");
                break;
        }
    }


    // --- State-Specific Logic ---


    private static void checkBaritoneWait() {
        Axon.statusOverlay("Waiting for Baritone...");
        if (System.currentTimeMillis() > waitTimeoutMillis) {
            Axon.LOGGER.info("Baritone wait timed out.");
            inputQueue.offer(new ControlInput(ControlType.BARITONE_TIMEOUT, currentWaitingToolCallId));
            // State transition happens in handleControlInput
        } else if (!BaritoneService.isActive()) {
            Axon.LOGGER.info("Baritone process completed.");
            inputQueue.offer(new ControlInput(ControlType.BARITONE_COMPLETED, currentWaitingToolCallId));
            // State transition happens in handleControlInput
        }
        // Otherwise, continue waiting
    }

    private static void checkTimeWait() {
        Axon.statusOverlay("Waiting...");
        if (System.currentTimeMillis() > waitTimeoutMillis) {
            Axon.LOGGER.info("Timed wait completed.");
            inputQueue.offer(new ControlInput(ControlType.TIME_WAIT_COMPLETED, currentWaitingToolCallId));
            // State transition happens in handleControlInput
        }
        // Otherwise, continue waiting
    }

    private static void handleUserInput(UserCommandInput input) {
        boolean interrupt = false;
        if (currentState == AgentState.WAITING_FOR_BARITONE || currentState == AgentState.WAITING_FOR_TIME) {
            Axon.LOGGER.info("User input received during wait state, interrupting.");
            Axon.chatMessage(Utils.prefixText("Axon").append(Text.literal("Wait interrupted by user command.").formatted(Formatting.YELLOW)));
            interrupt = true;
            // Cancel Baritone if it was waiting for it
            if (currentState == AgentState.WAITING_FOR_BARITONE) {
                BaritoneService.stop();
            }
            // Clear wait state variables
            waitTimeoutMillis = 0;
            currentWaitingToolCallId = null;
        } else if (currentState == AgentState.THINKING && currentLLMRequest != null && !currentLLMRequest.isDone()) {
            Axon.LOGGER.warn("User input received while THINKING. Cancelling previous LLM request.");
            currentLLMRequest.cancel(true);
            currentLLMRequest = null;
            interrupt = true; // Treat as interrupt to thinking
        } else if (currentState == AgentState.EXECUTING_TOOL) {
            Axon.LOGGER.warn("User input received while EXECUTING_TOOL. Clearing tool queue.");
            toolExecutionQueue.clear();
            interrupt = true;
        }

        // Add user message to history
        messages.add(ChatMessage.UserMessage.of(input.command()));

        // Always transition to THINKING on user input (unless ERROR)
        if (currentState != AgentState.ERROR) {
            if (interrupt) {
                // Add a system message indicating the interruption context for the LLM
                messages.add(ChatMessage.SystemMessage.of("Processing interrupted by new user command. Previous actions stopped. Current status updated."));
            }
            triggerLLM();
        } else {
            Axon.chatMessage(Utils.prefixText("Axon").append(Text.literal("Currently in ERROR state. Use /a-clear to reset.").formatted(Formatting.RED)));
        }
    }

    private static void handleToolExecutionRequest(ToolExecutionRequest input) {
        // This input type might not be strictly necessary if handleLLMResponse directly adds to toolExecutionQueue
        // If used, it would simply add the tool call to the queue.
        toolExecutionQueue.offer(input.toolCall());
        // If IDLE, transition to EXECUTING_TOOL to start processing the queue
        if (currentState == AgentState.IDLE) {
            currentState = AgentState.EXECUTING_TOOL;
        }
    }

    private static void handleToolResult(ToolResultInput input) {
        // Add the tool result message to the history
        messages.add(ChatMessage.ToolMessage.of(input.result(), input.toolCallId()));
        Axon.LOGGER.info("Tool {} result: {}", input.toolCallId(), input.result());

        // Display result to user (optional, can be noisy)
        Axon.chatMessage(Utils.prefixText("Tool Result")
                .append(Text.literal(input.toolCallId()).formatted(Formatting.DARK_GRAY))
                .append(Text.literal(": " + input.result()).formatted(Formatting.GRAY)));

        // If we were executing tools, check if more are queued
        if (currentState == AgentState.EXECUTING_TOOL) {
            if (toolExecutionQueue.isEmpty()) {
                // All tools executed, now THINK about the results
                Axon.LOGGER.debug("Finished executing tool queue. Transitioning to THINKING.");
                triggerLLM();
            }
            // otherwise, continue executing tools
        }
    }

    private static void handleLLMResponse(LLMResponseInput input) {
        ChatMessage.ResponseMessage message = (ChatMessage.ResponseMessage) input.responseMessage();
        currentLLMRequest = null;

        Axon.statusOverlay("Processing Response");
        Axon.LOGGER.info("LLM response received.");

        // Handle LLM text message
        String messageText = Optional.ofNullable(message.getContent())
                .orElse("")
                .trim();
        if (!messageText.isEmpty()) {
            messages.add(ChatMessage.AssistantMessage.of(messageText));
            Axon.chatMessage(Utils.prefixText("Axon").append(messageText));
            Axon.LOGGER.debug("LLM Message: {}", messageText);
        }

        // Handle LLM tool calls
        List<ToolCall> toolCalls = Optional.ofNullable(message.getToolCalls()).orElse(Collections.emptyList());
        if (!toolCalls.isEmpty()) {
            Axon.LOGGER.info("LLM requested {} tool calls.", toolCalls.size());
            // Ensure IDs and add assistant message with tool calls to history
            List<ToolCall> processedToolCalls = new ArrayList<>();
            for (ToolCall tc : toolCalls) {
                String id = tc.getId() != null && !tc.getId().isEmpty() ? tc.getId() : UUID.randomUUID().toString();
                processedToolCalls.add(new ToolCall(tc.getIndex(), id, tc.getType(), tc.getFunction()));
            }
            messages.add(ChatMessage.AssistantMessage.builder()
                    .toolCalls(processedToolCalls)
                    .content(" ") // for some reason, Gemini requires a non-empty content field
                    .build());

            // Add tool calls to the execution queue
            toolExecutionQueue.addAll(processedToolCalls);

            // Transition to EXECUTING_TOOL state to start processing the queue
            currentState = AgentState.EXECUTING_TOOL;
            Axon.LOGGER.debug("Transitioning to EXECUTING_TOOL.");
        } else {
            // No tool calls, transition back to IDLE
            Axon.LOGGER.debug("No tool calls requested. Transitioning to IDLE.");
            currentState = AgentState.IDLE;
        }
    }

    private static void handleControlInput(ControlInput input) {
        Axon.LOGGER.info("Handling control input: {} - {}", input.type(), input.details());
        String toolCallId = input.details(); // Often the ID of the tool that caused the wait/timeout

        // Reset wait state regardless of outcome
        currentState = AgentState.IDLE; // Default to IDLE, might change to THINKING below
        waitTimeoutMillis = 0;
        currentWaitingToolCallId = null;

        switch (input.type()) {
            case CLEAR_CHAT:
                messages.clear();
                toolExecutionQueue.clear();
                if (currentLLMRequest != null && !currentLLMRequest.isDone()) {
                    currentLLMRequest.cancel(true);
                }
                BaritoneService.stop();
                Axon.chatMessage(Utils.prefixText("Axon")
                        .append(Text.literal("Chat cleared and actions stopped.").formatted(Formatting.GRAY))
                );
                break;

            case INTERRUPT:
                // State transition likely already handled by handleUserInput
                toolExecutionQueue.clear();
                if (currentLLMRequest != null && !currentLLMRequest.isDone()) {
                    currentLLMRequest.cancel(true);
                }
                Axon.LOGGER.info("Processing INTERRUPT signal: {}", input.details());
                break;

            case BARITONE_TIMEOUT:
                messages.add(ChatMessage.ToolMessage.of("Baritone process wait timed out.", toolCallId));
                Axon.chatMessage(Utils.prefixText("Axon").append(Text.literal("Timed out waiting for Baritone.").formatted(Formatting.YELLOW)));
                triggerLLM();
                break;

            case BARITONE_COMPLETED:
                messages.add(ChatMessage.ToolMessage.of("Baritone process finished successfully.", toolCallId));
                Axon.chatMessage(Utils.prefixText("Axon").append(Text.literal("Baritone task finished.").formatted(Formatting.GREEN)));
                triggerLLM();
                break;

            case TIME_WAIT_COMPLETED:
                messages.add(ChatMessage.ToolMessage.of("Timed wait finished.", toolCallId));
                Axon.chatMessage(Utils.prefixText("Axon").append(Text.literal("Finished waiting.").formatted(Formatting.GREEN)));
                triggerLLM(); // Think about the next step
                break;

            case LLM_ERROR:
                messages.add(ChatMessage.SystemMessage.of("Error communicating with LLM: " + input.details()));
                Axon.chatMessage(Utils.prefixText("Axon").append(Text.literal("LLM Error: " + input.details()).formatted(Formatting.RED)));
                currentState = AgentState.ERROR; // Enter error state
                break;

            case TOOL_ERROR:
                messages.add(ChatMessage.ToolMessage.of("Error executing tool: " + input.details(), toolCallId)); // Assuming details contains error + tool id
                Axon.chatMessage(Utils.prefixText("Axon").append(Text.literal("Tool Error: " + input.details()).formatted(Formatting.RED)));
                triggerLLM(); // Let LLM decide based on the error message
                break;

        }
    }

    // --- LLM Interaction ---

    private static void triggerLLM() {
        if (currentState == AgentState.THINKING && currentLLMRequest != null && !currentLLMRequest.isDone()) {
            Axon.LOGGER.warn("triggerLLM called while already thinking, ignoring.");
            return;
        }
        if (currentState == AgentState.ERROR) {
            Axon.LOGGER.warn("triggerLLM called while in ERROR state, ignoring.");
            return;
        }

        currentState = AgentState.THINKING;
        Axon.statusOverlay("Preparing Request...");
        Axon.LOGGER.info("Transitioning to THINKING state.");

        List<ChatMessage> currentMessages;
        synchronized (messages) {
            currentMessages = new ArrayList<>(messages);
            currentMessages.addFirst(systemMessage());
        }

        // Limit message history size
        int maxHistory = 500; // Keep last N messages + system prompt
        if (currentMessages.size() > maxHistory) {
            // Keep system message + last (maxHistory - 1) messages
            List<ChatMessage> trimmedMessages = new ArrayList<>();
            trimmedMessages.add(currentMessages.getFirst()); // System message
            trimmedMessages.addAll(currentMessages.subList(currentMessages.size() - (maxHistory - 1), currentMessages.size()));
            currentMessages = trimmedMessages;
            Axon.LOGGER.debug("Trimmed message history to {} messages.", maxHistory);
        }

        Axon.LOGGER.info("Sending {} messages to LLM.", currentMessages.size());
        Axon.statusOverlay("Generating...");

        // Build request
        ChatRequest chatRequest = ChatRequest.builder()
                .model("gemini-2.0-flash") // "gemini-2.5-pro-exp-03-25"
                .messages(currentMessages)
                .tools(functionExecutor.getToolFunctions())
                .temperature(0.0)
                .build();

        // Send request asynchronously
        currentLLMRequest = llmApi.chatCompletions().create(chatRequest);

        // Handle response or exception using callbacks that enqueue results/errors
        currentLLMRequest.whenCompleteAsync((chatResponse, throwable) -> {
            if (throwable != null) {
                Axon.LOGGER.error("LLM request failed", throwable);
                // Use getCause() for CompletionException
                Throwable cause = (throwable instanceof CompletionException) ? throwable.getCause() : throwable;
                inputQueue.offer(new ControlInput(ControlType.LLM_ERROR, cause.getMessage()));
                return;
            }
            if (chatResponse instanceof Chat) {
                ChatMessage.ResponseMessage responseMessage = chatResponse.firstMessage();
                inputQueue.offer(new LLMResponseInput(responseMessage));
                return;
            }
            Axon.LOGGER.error("LLM request completed with null response/message");
            inputQueue.offer(new ControlInput(ControlType.LLM_ERROR, "Received null response from API"));
        }); // Ensure callback runs on the agent thread
    }

    // --- Tool Execution ---

    private static void executeNextTool() {
        ToolCall toolCall = toolExecutionQueue.poll();
        if (toolCall == null) {
            // Queue is empty, should not be in this state ideally
            Axon.LOGGER.warn("EXECUTING_TOOL state reached with empty queue. Transitioning to IDLE.");
            currentState = AgentState.IDLE;
            return;
        }

        FunctionCall function = toolCall.getFunction();
        if (function == null) {
            Axon.LOGGER.error("Tool call has null function: {}", toolCall);
            inputQueue.offer(new ToolResultInput(toolCall.getId(), "Error: Tool call had no function definition."));
            // Continue processing queue in next iteration
            return;
        }

        Axon.LOGGER.info("Executing tool: {} with args: {}", function.getName(), function.getArguments());
        Axon.statusOverlay("Executing: " + function.getName());
        Axon.chatMessage(Utils.prefixText("Tool Call")
                .append(Text.literal(function.getName()).formatted(Formatting.GRAY))
                .append(Text.literal(" Args: " + function.getArguments()).formatted(Formatting.GRAY))
        );


        try {
            Object resultObject = functionExecutor.execute(function);
            switch (resultObject) {
                case String result -> inputQueue.offer(new ToolResultInput(toolCall.getId(), result));
                case WaitAction(long timeoutMillis) -> {
                    waitTimeoutMillis = timeoutMillis;
                    currentState = AgentState.WAITING_FOR_TIME;
                    currentWaitingToolCallId = toolCall.getId();
                    Axon.chatMessage(Utils.prefixText("Axon")
                            .append(Text.literal("Waiting for " + timeoutMillis + " milliseconds.")
                                    .formatted(Formatting.YELLOW))
                    );
                }
                case BaritoneWaitAction(long timeoutMillis) -> {
                    waitTimeoutMillis = timeoutMillis;
                    currentState = AgentState.WAITING_FOR_BARITONE;
                    currentWaitingToolCallId = toolCall.getId();
                    Axon.chatMessage(Utils.prefixText("Axon")
                            .append(Text.literal("Waiting for Baritone process to complete.")
                                    .formatted(Formatting.YELLOW))
                    );
                }
                default ->
                        throw new IllegalArgumentException("Unexpected tool result type: " + resultObject.getClass());
            }

            Axon.statusOverlay("Executed: " + function.getName());

        } catch (Exception e) {
            Axon.LOGGER.error("Error executing tool: {}", function.getName(), e);
            String errorMessage = "Error: " + e.getMessage();
            // Enqueue an error result
            inputQueue.offer(new ToolResultInput(toolCall.getId(), errorMessage));
            // currentState = AgentState.ERROR; // the LLM can recover from this kind of error
        }

        // The logic that decides whether to transition to THINKING or continue EXECUTING_TOOL is in handleToolResult
        // and that will be called when the tool result is processed.
    }

    public enum AgentState {
        IDLE,                 // Waiting for user input or trigger event
        THINKING,             // Preparing context and waiting for LLM response
        EXECUTING_TOOL,       // Running a quick synchronous tool or scheduling async ones
        WAITING_FOR_BARITONE, // Baritone process active, waiting for completion or timeout
        WAITING_FOR_TIME,     // Explicit timed wait active
        ERROR                 // An error occurred, potentially needs reset or user action
    }

    public record WaitAction(long timeoutMillis) {
    }

    public record BaritoneWaitAction(long timeoutMillis) {
    }
}
