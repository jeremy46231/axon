package app.jer.axon;

import app.jer.axon.command.CommandRegistrar;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Axon implements ClientModInitializer {
    public static final String MOD_ID = "axon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void chatMessage(Text message) {
        MinecraftClient.getInstance().execute(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                client.player.sendMessage(message, false);
            }
        });
    }
    public static void chatMessage(String message) {
        chatMessage(Text.of(message));
    }

    public static void statusOverlay(Text message) {
        MinecraftClient.getInstance().execute(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                client.player.sendMessage(message, true);
            }
        });
    }
    public static void statusOverlay(String message) {
        statusOverlay(Text.of(message));
    }

    @Override
    public void onInitializeClient() {
        CommandRegistrar.register();
    }
}
