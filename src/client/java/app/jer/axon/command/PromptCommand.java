package app.jer.axon.command;

import app.jer.axon.Axon;
import app.jer.axon.Utils;
import app.jer.axon.agent.AxonAgent;
import app.jer.axon.service.LLMService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class PromptCommand implements Command<ServerCommandSource> {
    @Override
    public int run(CommandContext<ServerCommandSource> commandContext) {
        String message = StringArgumentType.getString(commandContext, "message");

        if (LLMService.getApi() == null) {
            Axon.chatMessage(Utils.prefixText("Axon")
                    .append(Text.literal("No API key set. Use /a-key <key>").formatted(Formatting.RED)));
            return 0;
        }

        try {
            AxonAgent.userMessage(message);
        } catch (Exception e) {
            Axon.LOGGER.error("Error sending user prompt to agent", e);
            commandContext.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }

        return 1;
    }
}
