package app.jer.axon.command;

import app.jer.axon.Axon;
import app.jer.axon.service.MeteorService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;

public class DebugCommand implements Command<ServerCommandSource> {
    @Override
    public int run(CommandContext<ServerCommandSource> commandContext) {
        MeteorService.initialize();

        Axon.chatMessage("Debug command executed");
        return 1;
    }
}
