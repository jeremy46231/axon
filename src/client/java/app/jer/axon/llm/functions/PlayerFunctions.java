package app.jer.axon.llm.functions;

import app.jer.axon.service.PlayerService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.sashirestela.openai.common.function.FunctionDef;
import io.github.sashirestela.openai.common.function.FunctionExecutor;
import io.github.sashirestela.openai.common.function.Functional;

public class PlayerFunctions {
    public static void enrollFunctions(FunctionExecutor functionExecutor) {
        functionExecutor.enrollFunction(FunctionDef.builder()
                .name("drop_items")
                .description("Drop inventory items, up to a stack")
                .functionalClass(DropItemsTool.class)
                .strict(Boolean.TRUE)
                .build());
        functionExecutor.enrollFunction(FunctionDef.builder()
                .name("swap_items")
                .description("Swap two inventory items")
                .functionalClass(SwapItemsTool.class)
                .strict(Boolean.TRUE)
                .build());
    }

    private static class DropItemsTool implements Functional {
        @JsonPropertyDescription("""
                The inventory slot index to drop (0-8 are the hotbar, 9-35 are the inventory, 36-39 are the armor slots from bottom to top, and 40 is the offhand)""")
        @JsonProperty(required = true)
        public int stackIndex;

        @JsonPropertyDescription("The amount of items to drop (defaults to the whole stack)")
        public Integer amount;

        @Override
        public String execute() {
            PlayerService.dropItem(stackIndex, amount != null ? amount : 64);
            return "Dropped items from slot " + stackIndex;
        }
    }

    private static class SwapItemsTool implements Functional {
        @JsonPropertyDescription("The first inventory slot index to swap")
        @JsonProperty(required = true)
        public int slotA;

        @JsonPropertyDescription("The second inventory slot index to swap")
        @JsonProperty(required = true)
        public int slotB;

        @Override
        public String execute() {
            PlayerService.swapItems(slotA, slotB);
            return "Swapped items in slots " + slotA + " and " + slotB;
        }
    }
}
