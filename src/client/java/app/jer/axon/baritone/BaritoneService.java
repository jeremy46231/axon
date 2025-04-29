package app.jer.axon.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.process.IBaritoneProcess;
import baritone.api.utils.BetterBlockPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

public class BaritoneService {
    private static final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

    public static void executeCommand(String command) {
        baritone.getCommandManager().execute(command);
    }

    public static String getTextStatus() {
        IBaritoneProcess process = baritone.getPathingControlManager().mostRecentInControl().orElse(null);
        if (process == null || !process.isActive()) {
            return "Baritone is idle";
        }
        String processName = process.displayName();

        IPathingBehavior pathingBehavior = baritone.getPathingBehavior();
        String goal = Optional.ofNullable(pathingBehavior.getGoal()).toString();
/*
        String status = (pathingBehavior.hasPath()
                ? pathingBehavior.isPathing() ? "Pathing" : "Current path paused"
                : "Stationary") +
                (pathingBehavior.getInProgress().isPresent() ? " (running pathfinding algorithm)" : "");

        double ticksRemainingInGoal = pathingBehavior.estimatedTicksToGoal().orElse(Double.NaN);
        String goalRemaining = String.format("%.1fs (%.0f ticks)", ticksRemainingInGoal / 20, ticksRemainingInGoal);
*/
        return String.format(
                """
                        Current process: %s
                        Current goal: %s
                        """,
                processName,
                goal
        );
    }

    public static BetterBlockPos getPlayerPos() {
        return baritone.getPlayerContext().playerFeet();
    }

    public static void stop() {
        baritone.getPathingBehavior().cancelEverything();
    }

    public static void mine(String[] blocks, int quantity) {
        MinecraftClient.getInstance().execute(() -> baritone.getMineProcess().mineByName(quantity, blocks));
    }

    public static void explore(int x, int z) {
        baritone.getExploreProcess().explore(x, z);
    }
    public static void explore() {
        BetterBlockPos position = getPlayerPos();
        baritone.getExploreProcess().explore(
                position.x,
                position.z
        );
    }

    public static void farm(int range, @Nullable BlockPos origin) {
        baritone.getFarmProcess().farm(range, origin);
    }

    public static void follow(Predicate<Entity> filter) {
        baritone.getFollowProcess().follow(filter);
    }
}
