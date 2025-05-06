package app.jer.axon.llm;

public enum AgentState {
    IDLE,                 // Waiting for user input or trigger event
    THINKING,             // Preparing context and waiting for LLM response
    EXECUTING_TOOL,       // Running a quick synchronous tool or scheduling async ones
    WAITING_FOR_BARITONE, // Baritone process active, waiting for completion or timeout
    WAITING_FOR_TIME,     // Explicit timed wait active
    ERROR                 // An error occurred, potentially needs reset or user action
}
