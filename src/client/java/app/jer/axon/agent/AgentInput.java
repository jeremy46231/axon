package app.jer.axon.agent;

import io.github.sashirestela.openai.common.tool.ToolCall;
import io.github.sashirestela.openai.domain.chat.ChatMessage;

/**
 * Types of internal control signals.
 */
enum ControlType {
    INTERRUPT,          // General interruption (e.g., user command during wait)
    CLEAR_CHAT,         // Request to clear chat history
    BARITONE_TIMEOUT,   // Baritone wait timed out
    BARITONE_COMPLETED, // Baritone process completed successfully
    TIME_WAIT_COMPLETED,// Timed wait completed
    LLM_ERROR,          // Error during LLM API call
    TOOL_ERROR          // Error during tool execution
}

/**
 * Represents different types of inputs that can be processed by the agent loop.
 */
public sealed interface AgentInput permits UserCommandInput, ToolExecutionRequest, ToolResultInput, LLMResponseInput, ControlInput {
}

/**
 * User command received via chat.
 */
record UserCommandInput(String command) implements AgentInput {
}

/**
 * Request to execute a specific tool call from the LLM.
 */
record ToolExecutionRequest(ToolCall toolCall) implements AgentInput {
}

/**
 * Result received after executing a tool.
 */
record ToolResultInput(String toolCallId, String result) implements AgentInput {
}

/**
 * Response received from the LLM API.
 */
record LLMResponseInput(ChatMessage responseMessage) implements AgentInput {
}

/**
 * Internal control signals for the agent loop.
 */
record ControlInput(ControlType type, String details) implements AgentInput {
}
