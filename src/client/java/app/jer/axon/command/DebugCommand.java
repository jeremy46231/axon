package app.jer.axon.command;

import app.jer.axon.Axon;
import app.jer.axon.service.PlayerService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;

public class DebugCommand implements Command<ServerCommandSource> {
    @Override
    public int run(CommandContext<ServerCommandSource> commandContext) {
        try {
            PlayerService.dropItem(1, 63);
        } catch (Exception e) {
            Axon.chatMessage("Error: " + e.getMessage());
            return 0;
        }

        Axon.chatMessage("Debug command executed");
        return 1;
    }
}
