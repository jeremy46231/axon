package app.jer.axon.service;

import app.jer.axon.Utils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class PlayerService {
    private static @NotNull PlayerEntity getPlayer() {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            throw new IllegalStateException("Player is not loaded");
        }
        return player;
    }
    private static @NotNull World getWorld() {
        World world = MinecraftClient.getInstance().world;
        if (world == null) {
            throw new IllegalStateException("World is not loaded");
        }
        return world;
    }

    public static String getTextStatus() {
        PlayerEntity player = getPlayer();
        String username = player.getName().getString();
        String onlinePlayers = onlinePlayers();
        String dimension = Utils.removeMinecraftPrefix(player.getWorld().getRegistryKey().getValue().toString());
        String time = getTimeString();
        String position = getPositionString();
        String biome = getBiomeString();
        String weather = getWeatherString();
        String health = String.format("%.1f/20", player.getHealth());
        String hunger = getHungerString(player);
        String inventory = getInventoryString();

        return String.format(
                """
                        Username: %s
                        Online Players: %s
                        Dimension: %s
                        Time: %s
                        Position: %s
                        Biome: %s
                        Weather: %s
                        Health: %s
                        Hunger: %s
                        Inventory: %s""",
                username, onlinePlayers, dimension, time,
                position, biome, weather, health, hunger,
                inventory
        );
    }

    private static String getHungerString(PlayerEntity player) {
        return String.format(
                "%d/20 (%.1f saturation)",
                player.getHungerManager().getFoodLevel(),
                player.getHungerManager().getSaturationLevel()
        );
    }

    private static String getPositionString() {
        BlockPos blockPos = getPlayer().getBlockPos();
        Vec3d exactPos = getPlayer().getPos();
        return String.format("%d, %d, %d (exact: %.2f, %.2f, %.2f)",
                blockPos.getX(), blockPos.getY(), blockPos.getZ(),
                exactPos.x, exactPos.y, exactPos.z
        );
    }

    public static String getInventoryString() {
        PlayerInventory inventory = getPlayer().getInventory();

        StringBuilder sb = new StringBuilder();

        Map<String, Integer> normalItems = new HashMap<>();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack itemStack = inventory.getStack(i);
            if (itemStack.isEmpty()) continue;
            String id = Utils.removeMinecraftPrefix(itemStack.getItem().toString());
            int count = itemStack.getCount();

            ComponentChanges nbt = itemStack.getComponentChanges();
            if (!nbt.isEmpty()) {
                if (count > 1) {
                    sb.append(count).append("x ");
                }
                sb.append(id);
                if (!nbt.isEmpty()) {
                    sb.append(" (");
                    for (Map.Entry<ComponentType<?>, Optional<?>> entry : nbt.entrySet()) {
                        sb.append(Utils.removeMinecraftPrefix(entry.getKey().toString())).append(": ").append(entry.getValue().orElse(null)).append(", ");
                    }
                    sb.delete(sb.length() - 2, sb.length());
                    sb.append(")");
                }

                sb.append(", ");
                continue;
            }

            if (normalItems.containsKey(id)) {
                normalItems.put(id, normalItems.get(id) + count);
            } else {
                normalItems.put(id, count);
            }
        }

        for (Map.Entry<String, Integer> entry : normalItems.entrySet()) {
            String id = entry.getKey();
            int count = entry.getValue();
            sb.append(count).append("x ").append(id).append(", ");
        }

        if (!sb.isEmpty()) {
            sb.delete(sb.length() - 2, sb.length());
        }
        String result = sb.toString();
        if (result.isEmpty()) {
            return "<empty>";
        }
        return result;
    }


    private static String getTimeString() {
        long timeOfDay = getWorld().getTime() % 24000L;
        StringBuilder sb = new StringBuilder();
        sb.append(timeOfDay);
        // assume sunny weather, risk underestimating availability
        if (timeOfDay >= 12542L && timeOfDay <= 23460L) {
            sb.append(" (beds can be used)");
        }
        // assume rainy weather, risk overestimating danger
        if (timeOfDay >= 12969L && timeOfDay <= 23031L) {
            sb.append(" (monsters spawning)");
        }
        return sb.toString();
    }
    private static String getWeatherString() {
        World world = getWorld();
        if (world.isThundering()) {
            return "Thundering";
        }
        if (world.isRaining()) {
            return "Raining";
        }
        return "Clear";
    }
    private static String getBiomeString() {
        BlockPos blockPos = getPlayer().getBlockPos();
        if (blockPos == null) return "Unknown";
        var biomeKey = getWorld().getBiome(blockPos).getKey();
        return biomeKey
                .map(biomeRegistryKey -> Utils.removeMinecraftPrefix(biomeRegistryKey.toString()))
                .orElse("Unknown");
    }
    private static String onlinePlayers() {
        var onlinePlayers = getWorld().getPlayers();
        if (onlinePlayers.isEmpty()) {
            return "No players online";
        }
        return onlinePlayers.stream()
                .filter(otherPlayer -> otherPlayer != null && otherPlayer != getPlayer())
                .map(otherPlayer -> otherPlayer.getName().getString())
                .collect(Collectors.joining(", "));
    }
}
