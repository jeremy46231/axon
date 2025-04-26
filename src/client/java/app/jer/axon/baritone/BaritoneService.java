package app.jer.axon.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;

import java.util.Optional;

public class BaritoneService {
    private static final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

    public static void executeCommand(String command) {
        baritone.getCommandManager().execute(command);
    }

    public static String getTextStatus() {
        var process = baritone.getPathingControlManager().mostRecentInControl().orElse(null);
        if (process == null || !process.isActive()) {
            return "Baritone is idle";
        }
        var processName = process.displayName();

        var pathingBehavior = baritone.getPathingBehavior();
        String goal = Optional.ofNullable(pathingBehavior.getGoal()).toString();

        String status = (pathingBehavior.hasPath()
                ? pathingBehavior.isPathing() ? "Pathing" : "Current path paused"
                : "Stationary") +
                (pathingBehavior.getInProgress().isPresent() ? " (running pathfinding algorithm)" : "");

        double ticksRemainingInGoal = pathingBehavior.estimatedTicksToGoal().orElse(Double.NaN);
        String goalRemaining = String.format("%.1fs (%.0f ticks)", ticksRemainingInGoal / 20, ticksRemainingInGoal);

        return String.format(
                """
                        Current process: %s
                        Current goal: %s
                        Goal status: %s
                        Time remaining in goal: %s
                        """,
                processName,
                goal,
                status,
                goalRemaining
        );
    }

    public static void mine(String[] blocks, int quantity) {
        baritone.getMineProcess().mineByName(quantity, blocks);
    }
}
