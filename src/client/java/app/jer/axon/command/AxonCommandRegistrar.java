package app.jer.axon.command;

import app.jer.axon.AxonClient;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;

public class AxonCommandRegistrar {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("a")
                    .then(CommandManager.argument("message", StringArgumentType.greedyString())
                            .executes(new PromptCommand())
                    )
            );
            dispatcher.register(CommandManager.literal("axon-clear")
                    .executes(new ClearCommand())
            );
        });
        AxonClient.LOGGER.info("Axon commands registered");
    }
}
