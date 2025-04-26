package app.jer.axon.command;

import app.jer.axon.Axon;
import app.jer.axon.llm.LLMService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class PromptCommand implements Command<ServerCommandSource> {
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
