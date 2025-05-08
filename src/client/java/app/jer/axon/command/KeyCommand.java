package app.jer.axon.command;

import app.jer.axon.Axon;
import app.jer.axon.Utils;
import app.jer.axon.service.LLMService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class KeyCommand implements Command<ServerCommandSource> {
    @Override
    public int run(CommandContext<ServerCommandSource> commandContext) {
        String key = StringArgumentType.getString(commandContext, "key");
        LLMService.setKey(key);
        Axon.chatMessage(Utils.prefixText("Axon")
                .append(Text.literal("Key set successfully").formatted(Formatting.DARK_GREEN)));
        return 1;
    }
}
