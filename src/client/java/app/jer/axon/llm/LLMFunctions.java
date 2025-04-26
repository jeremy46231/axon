package app.jer.axon.llm;

import app.jer.axon.baritone.BaritoneService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.sashirestela.openai.common.function.FunctionDef;
import io.github.sashirestela.openai.common.function.FunctionExecutor;
import io.github.sashirestela.openai.common.function.Functional;

public class LLMFunctions {
    public static FunctionExecutor getFunctions() {
        var functionExecutor = new FunctionExecutor();
        functionExecutor.enrollFunction(
                FunctionDef.builder()
                        .name("baritone_status")
                        .description("Get Baritone's current status")
                        .functionalClass(StatusTool.class)
                        .strict(Boolean.TRUE)
                        .build());
        functionExecutor.enrollFunction(
                FunctionDef.builder()
                        .name("baritone_mine")
                        .description("Instructs Baritone to locate and mine a specific amount of the specified block")
                        .functionalClass(MiningTool.class)
                        .strict(Boolean.TRUE)
                        .build());
        return functionExecutor;
    }

    static class StatusTool implements Functional {
        @Override
        public Object execute() {
            return BaritoneService.getTextStatus();
        }
    }

    static class MiningTool implements Functional {
        @JsonPropertyDescription("IDs of blocks to locate and mine, like 'dirt', 'oak_log', or 'diamond_ore'")
        @JsonProperty(required = true)
        public String[] blocks;

        @JsonPropertyDescription("How many blocks to collect (total across all specified blocks)")
        @JsonProperty(required = true)
        public int count;

        @Override
        public Object execute() {
            BaritoneService.mine(blocks, count);
            return BaritoneService.getTextStatus();
        }
    }
}

