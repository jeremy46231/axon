package app.jer.axon;

import app.jer.axon.command.AxonCommandRegistrar;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AxonClient implements ClientModInitializer {
    public static final String MOD_ID = "axon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void chatMessage(String message) {
        var client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.of(message.trim()), false);
        }
    }

    @Override
    public void onInitializeClient() {
        AxonCommandRegistrar.register();
    }
}
