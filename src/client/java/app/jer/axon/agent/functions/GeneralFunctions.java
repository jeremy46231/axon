package app.jer.axon.agent.functions;

import app.jer.axon.agent.AxonAgent;
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
                        Wait for a specific duration. Consider using baritone_wait_process instead, if Baritone is involved.""")
                .functionalClass(WaitTool.class)
                .strict(Boolean.TRUE)
                .build());
    }

    private static class WaitTool implements Functional {
        @JsonPropertyDescription("How many seconds to wait for")
        @JsonProperty(required = true)
        public float seconds;

        @Override
        public AxonAgent.WaitAction execute() {
            if (seconds <= 0) {
                throw new IllegalArgumentException("Timeout must be positive");
            }
            return new AxonAgent.WaitAction(
                    (long) (System.currentTimeMillis() + seconds * 1000)
            );
        }
    }
}
