package app.jer.axon.service;

import app.jer.axon.Utils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;

public class PlayerService {
    private static @NotNull ClientPlayerEntity getPlayer() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
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
        ClientPlayerEntity player = getPlayer();
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
                        Online players: %s
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

    private static String getHungerString(ClientPlayerEntity player) {
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

        for (int index = 0; index < inventory.size(); index++) {
            ItemStack itemStack = inventory.getStack(index);

            sb.append(index);
            if (index <= 8) {
                sb.append(" (hotbar)");
            } else if (index == 36) {
                sb.append(" (boots)");
            } else if (index == 37) {
                sb.append(" (leggings)");
            } else if (index == 38) {
                sb.append(" (chestplate)");
            } else if (index == 39) {
                sb.append(" (helmet)");
            } else if (index >= 40) {
                sb.append(" (offhand)");
            }
            sb.append(": ");

            if (itemStack.isEmpty()) {
                sb.append("<empty>");
            } else {
                int count = itemStack.getCount();
                String id = Utils.removeMinecraftPrefix(itemStack.getItem().toString());
                sb.append(count).append("x ").append(id);
            }
            sb.append(", ");
        }
        if (sb.length() > 2) {
            sb.delete(sb.length() - 2, sb.length());
        }
        return sb.toString();
    }

    private static String getTimeString() {
        long timeOfDay = getWorld().getTime() % 24000;
        StringBuilder sb = new StringBuilder();
        sb.append(timeOfDay);
        // assume sunny weather, risk underestimating availability
        if (timeOfDay >= 12542 && timeOfDay < 23460) {
            sb.append(" (beds can be used)");
        }
        // assume rainy weather, risk overestimating danger
        if (timeOfDay >= 12969 && timeOfDay < 23031) {
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

    private static int getScreenIndex(ScreenHandler screenHandler, Inventory inventory, int inventoryIndex) {
        for (var slot : screenHandler.slots) {
            if (slot.inventory != inventory) continue;
            if (slot.getIndex() != inventoryIndex) continue;
            return slot.id;
        }
        throw new IllegalStateException("Slot not found");
    }

    public static void swapItems(int slotA, int slotB) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        assert interactionManager != null;
        ClientPlayerEntity player = client.player;
        assert player != null;
        PlayerInventory playerInventory = player.getInventory();
        ScreenHandler screenHandler = player.currentScreenHandler;
        assert screenHandler != null;
        int syncId = screenHandler.syncId;

        int slotAScreenIndex = getScreenIndex(screenHandler, playerInventory, slotA);
        int slotBScreenIndex = getScreenIndex(screenHandler, playerInventory, slotB);

        if (0 <= slotA && slotA < 9) {
            interactionManager.clickSlot(syncId, slotBScreenIndex, slotA, SlotActionType.SWAP, player);
            return;
        }
        if (0 <= slotB && slotB < 9) {
            interactionManager.clickSlot(syncId, slotAScreenIndex, slotB, SlotActionType.SWAP, player);
            return;
        }

        int tempSlot = 8;
        interactionManager.clickSlot(syncId, slotAScreenIndex, tempSlot, SlotActionType.SWAP, player);
        interactionManager.clickSlot(syncId, slotBScreenIndex, tempSlot, SlotActionType.SWAP, player);
        interactionManager.clickSlot(syncId, slotAScreenIndex, tempSlot, SlotActionType.SWAP, player);
    }

    public static void dropItem(int stackIndex, int quantity) {
        if (quantity <= 0) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        assert interactionManager != null;
        ClientPlayerEntity player = client.player;
        assert player != null;
        PlayerInventory playerInventory = player.getInventory();
        ScreenHandler screenHandler = player.currentScreenHandler;
        assert screenHandler != null;
        int syncId = screenHandler.syncId;

        ItemStack itemStack = playerInventory.getStack(stackIndex);
        if (itemStack.isEmpty()) {
            // Slot is already empty, nothing to drop
            return;
        }
        int currentCount = itemStack.getCount();

        int stackScreenIndex = getScreenIndex(screenHandler, playerInventory, stackIndex);

        if (quantity >= currentCount) {
            // Requested quantity is >= actual count, so drop the whole stack (button = 1)
            interactionManager.clickSlot(syncId, stackScreenIndex, 1, SlotActionType.THROW, player);
        } else {
            // Requested quantity is less than actual count, drop one item 'quantity' times (button = 0)
            for (int i = 0; i < quantity; i++) {
                interactionManager.clickSlot(syncId, stackScreenIndex, 0, SlotActionType.THROW, player);
            }
        }
    }
}
