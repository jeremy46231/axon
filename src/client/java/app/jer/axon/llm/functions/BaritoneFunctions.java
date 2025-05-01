package app.jer.axon.llm.functions;

import app.jer.axon.Axon;
import app.jer.axon.Utils;
import app.jer.axon.service.BaritoneService;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.pathing.goals.GoalYLevel;
import baritone.api.utils.BetterBlockPos;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.sashirestela.openai.common.function.FunctionDef;
import io.github.sashirestela.openai.common.function.FunctionExecutor;
import io.github.sashirestela.openai.common.function.Functional;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

public class BaritoneFunctions {
    public static void enrollFunctions(FunctionExecutor functionExecutor) {
        functionExecutor.enrollFunction(FunctionDef.builder()
                .name("baritone_stop")
                .description("Stop the current Baritone process")
                .functionalClass(StopTool.class)
                .strict(Boolean.TRUE)
                .build());
        functionExecutor.enrollFunction(FunctionDef.builder()
                .name("baritone_goto")
                .description("Navigate to a given location")
                .functionalClass(GotoTool.class)
                .strict(Boolean.TRUE)
                .build());
        functionExecutor.enrollFunction(FunctionDef.builder()
                .name("baritone_goto_y")
                .description("Navigate to a given y level")
                .functionalClass(GotoYTool.class)
                .strict(Boolean.TRUE)
                .build());
        functionExecutor.enrollFunction(FunctionDef.builder()
                .name("baritone_mine")
                .description("""
                        Locate and mine a specific amount of the specified block (this process will stop once enough of the block/item has been collected)""")
                .functionalClass(MiningTool.class)
                .strict(Boolean.TRUE)
                .build());
        functionExecutor.enrollFunction(FunctionDef.builder()
                .name("baritone_explore")
                .description("""
                        Move near uncached chunks around the specified coordinates to load them into Baritone's cache, so it can pathfind outside of the render distance more effectively (this process will not stop until you stop it)""")
                .functionalClass(ExploreTool.class)
                .strict(Boolean.TRUE)
                .build());
        functionExecutor.enrollFunction(FunctionDef.builder()
                .name("baritone_follow")
                .description("Follow a mob or entity (this process will not stop until you stop it)")
                .functionalClass(FollowTool.class)
                .strict(Boolean.TRUE)
                .build());
        functionExecutor.enrollFunction(FunctionDef.builder()
                .name("baritone_farm")
                .description("""
                        Automatically farm and replant crops in a specified area (this process will not stop until you stop it)""")
                .functionalClass(FarmTool.class)
                .strict(Boolean.TRUE)
                .build());
        functionExecutor.enrollFunction(FunctionDef.builder()
                .name("baritone_waypoint_add")
                .description("Add a saved waypoint at the specified coordinates")
                .functionalClass(WaypointAddTool.class)
                .strict(Boolean.TRUE)
                .build());
        functionExecutor.enrollFunction(FunctionDef.builder()
                .name("baritone_waypoint_remove")
                .description("Remove a saved waypoint at the specified coordinates")
                .functionalClass(WaypointRemoveTool.class)
                .strict(Boolean.TRUE)
                .build());
        functionExecutor.enrollFunction(FunctionDef.builder()
                .name("baritone_wait_process")
                .description("""
                        Wait until the current Baritone process is finished (make sure to inform the user about the current process before waiting until it finishes)""")
                .functionalClass(ProcessWaitTool.class)
                .strict(Boolean.TRUE)
                .build());
    }

    static class StopTool implements Functional {
        @Override
        public String execute() {
            BaritoneService.stop();
            return "Baritone is now idle";
        }
    }

    static class MiningTool implements Functional {
        @JsonPropertyDescription("""
                IDs of blocks to locate and mine, like 'dirt', 'oak_log', or 'diamond_ore'. Make sure to include *all* acceptable blocks. For example, when chopping down trees for wood, specify all  of the acceptable logs: oak_log, spruce_log, birch_log, jungle_log, acacia_log, dark_oak_log, mangrove_log, cherry_log, pale_oak_log, crimson_stem, warped_stem, and when mining diamonds, specify both diamond_ore and deepslate_diamond_ore.""")
        @JsonProperty(required = true)
        public String[] blocks;

        @JsonPropertyDescription("""
                How many blocks to collect (total across all specified blocks, including ones already in the inventory)""")
        @JsonProperty(required = true)
        public int count;

        @Override
        public String execute() {
            Axon.LOGGER.info("Baritone is now mining {} of {}", count, String.join(", ", blocks));
            BaritoneService.mine(blocks, count);
            return "Baritone is now mining " + count + " of " + String.join(", ", blocks);
        }
    }

    static class ExploreTool implements Functional {
        @JsonPropertyDescription("Where to begin exploring around")
        @JsonProperty(required = true)
        public Utils.LocationXZ position;

        @Override
        public String execute() {
            Utils.CoordinateXZ coordinates = position.getCoordinates();
            BaritoneService.explore(coordinates.x(), coordinates.z());
            return "Baritone is now exploring uncached chunks around " + coordinates.x() + ", " + coordinates.z();
        }
    }

    static class FarmTool implements Functional {
        @JsonPropertyDescription("The center of the area to farm (default to the player's current position)")
        @JsonProperty(required = true)
        public Utils.LocationXYZ position;

        @JsonPropertyDescription("""
                The radius of the area to farm (set to 0 for the default: farm everything up to the maximum distance of 256 blocks)""")
        @JsonProperty(required = true)
        public int radius;

        @Override
        public String execute() {
            BetterBlockPos coordinates = position.getCoordinates();
            BaritoneService.farm(radius, coordinates);
            return "Baritone is now farming " + radius + " blocks around " + coordinates.x + ", " + coordinates.y +
                    ", " + coordinates.z;
        }
    }

    static class FollowTool implements Functional {
        @JsonPropertyDescription("Follow only players, as opposed to any kind of entity")
        @JsonProperty(required = true)
        public boolean playersOnly;

        @JsonPropertyDescription("""
                What to follow? If playersOnly is true, this is a list of usernames, otherwise it's a list of entity types (like 'skeleton', 'horse', etc). Optional, if not provided, follow the nearest player/entity of any type.""")
        public String[] targets;

        @Override
        public String execute() {
            if (targets == null || targets.length == 0) {
                if (playersOnly) {
                    BaritoneService.follow(PlayerEntity.class::isInstance);
                    return "Baritone is now following the nearest player";
                }
                BaritoneService.follow(LivingEntity.class::isInstance);
                return "Baritone is now following the nearest entity";
            } else {
                if (playersOnly) {
                    BaritoneService.follow(s -> {
                        if (!(s instanceof PlayerEntity)) {
                            return false;
                        }
                        String username = s.getName().getString();
                        for (String target : targets) {
                            if (username.equalsIgnoreCase(target)) {
                                return true;
                            }
                        }
                        return false;
                    });
                    return "Baritone is now following users " + String.join(", ", targets);
                }
                BaritoneService.follow(s -> {
                    for (String target : targets) {
                        if (s.getType().toString().equalsIgnoreCase(target)) {
                            return true;
                        }
                    }
                    return false;
                });
                return "Baritone is now following all entities of type " + String.join(", ", targets);
            }
        }
    }

    static class GotoTool implements Functional {
        @JsonPropertyDescription("Where to navigate to")
        @JsonProperty(required = true)
        public Utils.LocationXZOptionalY position;

        @Override
        public String execute() {
            Utils.CoordinateXZOptionalY coordinates = position.getCoordinates();
            if (coordinates.y() == null) {
                BaritoneService.customGoal(new GoalXZ(coordinates.x(), coordinates.z()));
                return "Baritone is now going to " + coordinates.x() + ", " + coordinates.z() + " (at any Y level)";
            } else {
                BaritoneService.customGoal(new GoalBlock(coordinates.x(), coordinates.y(), coordinates.z()));
                return "Baritone is now going to " + coordinates.x() + ", " + coordinates.y() + ", " + coordinates.z();
            }
        }
    }
    static class GotoYTool implements Functional {
        @JsonPropertyDescription("What Y level to go to")
        @JsonProperty(required = true)
        public int yLevel;

        @Override
        public String execute() {
            BaritoneService.customGoal(new GoalYLevel(yLevel));
            return "Baritone is now going to Y level " + yLevel;
        }
    }

    static class WaypointAddTool implements Functional {
        @JsonPropertyDescription("The name of the waypoint")
        @JsonProperty(required = true)
        public String name;

        @JsonPropertyDescription("""
                The position of the waypoint (leave empty to default to the player's current position)""")
        @JsonProperty(required = true)
        public Utils.LocationXYZ position;

        @Override
        public String execute() {
            BetterBlockPos coordinates = position.getCoordinates();
            BaritoneService.addWaypoint(name, coordinates);
            return "Baritone has added a waypoint called '" + name + "' at XYZ " + coordinates.x + ", " + coordinates.y + ", " + coordinates.z;
        }
    }
    static class WaypointRemoveTool implements Functional {
        @JsonPropertyDescription("The exact name of the waypoint to remove")
        @JsonProperty(required = true)
        public String name;

        @Override
        public String execute() {
            BetterBlockPos oldPos = BaritoneService.getWaypointPos(name);
            if (oldPos == null) {
                return "Error: Baritone has no waypoint called '" + name + "'";
            }
            BaritoneService.removeWaypoint(name);
            return "Baritone has removed the waypoint called '" + name + "' at XYZ " + oldPos.x + ", " + oldPos.y + ", " + oldPos.z;
        }
    }

    static class ProcessWaitTool implements Functional {
        @JsonPropertyDescription("""
                The maximum amount of time to wait for the process to finish (in seconds). When the time is up, the process will not be stopped, but you will have a chance to reevaluate the current status and decide whether to continue waiting or stop the process.""")
        @JsonProperty(required = true)
        public long timeout;

        @Override
        public String execute() {
            if (!BaritoneService.isActive()) {
                return "Error: The Baritone process is not active";
            }
            long elapsedTime = 0;
            while (elapsedTime < timeout) {
                if (!BaritoneService.isActive()) {
                    return "The Baritone process has finished after " + elapsedTime + " seconds.";
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                elapsedTime++;
            }
            return "The Baritone process is still active after " + timeout + " seconds.";
        }
    }
}
