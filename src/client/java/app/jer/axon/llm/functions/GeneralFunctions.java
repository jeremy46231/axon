package app.jer.axon.llm.functions;

import app.jer.axon.llm.LLMService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.sashirestela.openai.common.function.FunctionDef;
import io.github.sashirestela.openai.common.function.FunctionExecutor;
import io.github.sashirestela.openai.common.function.Functional;

public class GeneralFunctions {
    public static void enrollFunctions(FunctionExecutor functionExecutor) {
        functionExecutor.enrollFunction(FunctionDef.builder()
                .name("wait")
                .description("""
                        Pause execution for a specific duration. The agent will enter a WAITING_FOR_TIME state and resume thinking after the duration.""")
                .functionalClass(WaitTool.class)
                .strict(Boolean.TRUE)
                .build());
    }

    private static class WaitTool implements Functional {
        @JsonPropertyDescription("How many seconds to wait for")
        @JsonProperty(required = true)
        public float seconds;

        @Override
        public LLMService.WaitAction execute() {
            if (seconds <= 0) {
                throw new IllegalArgumentException("Timeout must be positive");
            }
            return new LLMService.WaitAction(
                    (long) (System.currentTimeMillis() + seconds * 1000)
            );
        }
    }
}
