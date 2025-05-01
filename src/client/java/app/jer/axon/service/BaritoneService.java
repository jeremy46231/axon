package app.jer.axon.service;

import app.jer.axon.Axon;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.cache.IWaypoint;
import baritone.api.cache.IWaypointCollection;
import baritone.api.cache.Waypoint;
import baritone.api.pathing.goals.Goal;
import baritone.api.process.IBaritoneProcess;
import baritone.api.utils.BetterBlockPos;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public class BaritoneService {
    private static final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

    public static void executeCommand(String command) {
        baritone.getCommandManager().execute(command);
    }

    public static String getTextStatus() {
        IBaritoneProcess process = baritone.getPathingControlManager().mostRecentInControl().orElse(null);
        if (process == null || !process.isActive()) {
            return "Baritone status: Idle";
        }
        String processName = process.displayName();
/*
        IPathingBehavior pathingBehavior = baritone.getPathingBehavior();
        String goal = Optional.ofNullable(pathingBehavior.getGoal()).toString();

        String status = (pathingBehavior.hasPath()
                ? pathingBehavior.isPathing() ? "Pathing" : "Current path paused"
                : "Stationary") +
                (pathingBehavior.getInProgress().isPresent() ? " (running pathfinding algorithm)" : "");

        double ticksRemainingInGoal = pathingBehavior.estimatedTicksToGoal().orElse(Double.NaN);
        String goalRemaining = String.format("%.1fs (%.0f ticks)", ticksRemainingInGoal / 20, ticksRemainingInGoal);
*/
        return String.format(
                """
                        Baritone status: Active
                        Current Baritone process: %s""",
                processName
        );
    }

    public static BetterBlockPos getPlayerPos() {
        return baritone.getPlayerContext().playerFeet();
    }

    public static void stop() {
        baritone.getPathingBehavior().cancelEverything();
    }

    public static void mine(String[] blocks, int quantity) {
        try {
            baritone.getMineProcess().mineByName(quantity, blocks);
        } catch (IllegalStateException e) {
            Axon.LOGGER.warn(
                    "Got that weird IllegalStateException when trying to mine, but it's probably fine",
                    e
            );
        }
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

    public static void customGoal(Goal goal) {
        baritone.getCustomGoalProcess().setGoalAndPath(goal);
    }

    public static IWaypointCollection waypointCollection() {
        return baritone.getWorldProvider().getCurrentWorld().getWaypoints();
    }
    public static Set<IWaypoint> getAllWaypoints() {
        return waypointCollection().getAllWaypoints();
    }
    public static @Nullable IWaypoint getWaypoint(String name) {
        Set<IWaypoint> waypoints = getAllWaypoints();
        Optional<IWaypoint> matchingWaypoint = waypoints.stream()
                .filter(waypoint -> waypoint.getName().equals(name))
                .findFirst();
        return matchingWaypoint.orElse(null);
    }
    public static @Nullable BetterBlockPos getWaypointPos(String name) {
        IWaypoint waypoint = getWaypoint(name);
        if (waypoint != null) {
            return waypoint.getLocation();
        }
        return null;
    }
    public static boolean removeWaypoint(String name) {
        Set<IWaypoint> waypoints = getAllWaypoints();
        Optional<IWaypoint> matchingWaypoint = waypoints.stream()
                .filter(waypoint -> waypoint.getName().equals(name))
                .findFirst();
        if (matchingWaypoint.isPresent()) {
            waypointCollection().removeWaypoint(matchingWaypoint.get());
            return true;
        }
        return false;
    }
    public static void addWaypoint(String name, BlockPos pos) {
        removeWaypoint(name);
        waypointCollection().addWaypoint(new Waypoint(name, IWaypoint.Tag.USER, BetterBlockPos.from(pos)));
    }
}
