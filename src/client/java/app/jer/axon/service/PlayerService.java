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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PlayerService {
    static PlayerEntity player = MinecraftClient.getInstance().player;

    public static String getTextStatus() {
        BlockPos blockPos = player.getBlockPos();
        Vec3d exactPos = player.getPos();
        String dimension = Utils.removeMinecraftPrefix(player.getWorld().getRegistryKey().getValue().toString());
        String username = player.getName().getString();
        String position = String.format("%d, %d, %d (exact: %.2f, %.2f, %.2f)",
                blockPos.getX(), blockPos.getY(), blockPos.getZ(),
                exactPos.x, exactPos.y, exactPos.z
        );
        String health = String.format("%.1f/20", player.getHealth());
        String hunger = String.format("%d/20 (%.1f saturation)", player.getHungerManager().getFoodLevel(), player.getHungerManager().getSaturationLevel());
        String inventory = getInventoryString();

        return String.format(
                """
                        Username: %s
                        Dimension: %s
                        Position: %s
                        Health: %s
                        Hunger: %s
                        Inventory: %s""",
                username, dimension, position, health, hunger, inventory
        );
    }

    public static String getInventoryString() {
        PlayerInventory inventory = player.getInventory();

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
}
