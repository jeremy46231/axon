package app.jer.axon.llm.functions;

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
                        Wait for a certain amount of time (useful to wait for processes that take time to finish, make sure to inform the user before running this)""")
                .functionalClass(WaitTool.class)
                .strict(Boolean.TRUE)
                .build());
    }

    public static class WaitTool implements Functional {
        @JsonPropertyDescription("How many seconds to wait for")
        @JsonProperty(required = true)
        public float seconds;

        @Override
        public String execute() {
            try {
                Thread.sleep((long) (seconds * 1000));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return "Waited for " + seconds + " seconds";
        }
    }
}
