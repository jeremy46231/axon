package app.jer.axon.command;

import app.jer.axon.Axon;
import app.jer.axon.llm.LLMService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class CommandRegistrar {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((
                dispatcher,
                registryAccess,
                environment
        ) -> {
            dispatcher.register(CommandManager.literal("a")
                    .then(CommandManager.argument("message", StringArgumentType.greedyString())
                            .executes(new PromptCommand())
                    )
            );
            dispatcher.register(CommandManager.literal("a-clear")
                    .executes(new ClearCommand())
            );
        });
        Axon.LOGGER.info("Axon commands registered");
    }

    public static class PromptCommand implements Command<ServerCommandSource> {
        @Override
        public int run(CommandContext<ServerCommandSource> commandContext) {
            String message = StringArgumentType.getString(commandContext, "message");

            try {
                LLMService.userMessage(message);
            } catch (Exception e) {
                Axon.LOGGER.error("Error while running user prompt", e);
                commandContext.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            }

            return 1;
        }
    }

    public static class ClearCommand implements Command<ServerCommandSource> {
        @Override
        public int run(CommandContext<ServerCommandSource> commandContext) {
            LLMService.clearChat();
            return 1;
        }
    }
}
