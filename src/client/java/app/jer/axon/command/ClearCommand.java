package app.jer.axon.command;

import app.jer.axon.llm.LLMService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class ClearCommand implements Command<ServerCommandSource> {
    @Override
    public int run(CommandContext<ServerCommandSource> commandContext) {
        LLMService.clearChat();
        commandContext.getSource().sendMessage(Text.literal("Conversation cleared"));

        return 1;
    }
}
