package app.jer.axon.llm;

import app.jer.axon.AxonClient;
import app.jer.axon.baritone.BaritoneService;
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
        functionExecutor.enrollFunction(
                FunctionDef.builder()
                        .name("baritone_status")
                        .description("Get Baritone's current status")
                        .functionalClass(StatusTool.class)
                        .strict(Boolean.TRUE)
                        .build());
        functionExecutor.enrollFunction(
                FunctionDef.builder()
                        .name("baritone_stop")
                        .description("Stop the current Baritone process")
                        .functionalClass(StopTool.class)
                        .strict(Boolean.TRUE)
                        .build());
        functionExecutor.enrollFunction(
                FunctionDef.builder()
                        .name("baritone_mine")
                        .description("Locate and mine a specific amount of the specified block (this process will stop once enough of the block/item has been collected)")
                        .functionalClass(MiningTool.class)
                        .strict(Boolean.TRUE)
                        .build());
        functionExecutor.enrollFunction(
                FunctionDef.builder()
                        .name("baritone_explore")
                        .description("Move near uncached chunks around the specified coordinates to load them into Baritone's cache, so it can pathfind outside of the render distance more effectively (this process will not stop until you stop it)")
                        .functionalClass(ExploreTool.class)
                        .strict(Boolean.TRUE)
                        .build());
        functionExecutor.enrollFunction(
                FunctionDef.builder()
                        .name("baritone_follow")
                        .description("Follow a mob or entity (this process will not stop until you stop it)")
                        .functionalClass(FollowTool.class)
                        .strict(Boolean.TRUE)
                        .build());
        functionExecutor.enrollFunction(
                FunctionDef.builder()
                        .name("baritone_farm")
                        .description("Automatically farm and replant crops in a specified area (this process will not stop until you stop it)")
                        .functionalClass(FarmTool.class)
                        .strict(Boolean.TRUE)
                        .build());
    }

    static class StatusTool implements Functional {
        @Override
        public String execute() {
            return BaritoneService.getTextStatus();
        }
    }

    static class StopTool implements Functional {
        @Override
        public String execute() {
            BaritoneService.stop();
            return "Baritone is now idle";
        }
    }

    static class MiningTool implements Functional {
        @JsonPropertyDescription("IDs of blocks to locate and mine, like 'dirt', 'oak_log', or 'diamond_ore'")
        @JsonProperty(required = true)
        public String[] blocks;

        @JsonPropertyDescription("How many blocks to collect (total across all specified blocks, including ones already in the inventory)")
        @JsonProperty(required = true)
        public int count;

        @Override
        public String execute() {
            AxonClient.LOGGER.info("Baritone is now mining {} of {}", count, String.join(", ", blocks));
            BaritoneService.mine(blocks, count);
            return "Baritone is now mining " + count + " of " + String.join(", ", blocks);
        }
    }

    static class ExploreTool implements Functional {
        @JsonPropertyDescription("Where to begin exploring around")
        @JsonProperty(required = true)
        public LocationXZ position;

        @Override
        public String execute() {
            CoordinateXZ coordinates = position.getCoordinates();
            BaritoneService.explore(coordinates.x, coordinates.z);
            return "Baritone is now exploring uncached chunks around " + coordinates.x + ", " + coordinates.z;
        }
    }

    static class FarmTool implements Functional {
        @JsonPropertyDescription("The center of the area to farm")
        @JsonProperty(required = true)
        public LocationXYZ position;

        @JsonPropertyDescription("The radius of the area to farm (use 0 to farm everything up to the maximum distance of 256 blocks)")
        @JsonProperty(required = true)
        public int radius;

        @Override
        public String execute() {
            BetterBlockPos coordinates = position.getCoordinates();
            BaritoneService.farm(radius, coordinates);
            return "Baritone is now farming " + radius + " blocks around " + coordinates.x + ", " + coordinates.y + ", " + coordinates.z;
        }
    }

    static class FollowTool implements Functional {
        @JsonPropertyDescription("Follow only players, as opposed to any kind of entity")
        @JsonProperty(required = true)
        public boolean playersOnly;

        @JsonPropertyDescription("What to follow? If playersOnly is true, this is a list of usernames, otherwise it's a list of entity types (like 'skeleton', 'horse', etc). Optional, if not provided, follow the nearest player/entity of any type")
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
                    return "Baritone is now following " + String.join(", ", targets);
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


    public static class LocationXZ {
        @JsonPropertyDescription("X and Z coordinates (specify one of position or waypoint, or neither to use the current position)")
        public CoordinateXZ position;

        @JsonPropertyDescription("The name of a saved waypoint")
        public String waypoint;

        public CoordinateXZ getCoordinates() {
            if (position != null) {
                return position;
            } else if (waypoint != null) {
                throw new IllegalArgumentException("Waypoint is not supported yet");
            } else {
                BetterBlockPos playerPosition = BaritoneService.getPlayerPos();
                return new CoordinateXZ(playerPosition.x, playerPosition.z);
            }
        }
    }

    public record CoordinateXZ(
            @JsonProperty(required = true)
            int x,
            @JsonProperty(required = true)
            int z
    ) {
    }

    public static class LocationXYZ {
        @JsonPropertyDescription("X, Y, and Z coordinates (specify one of position or waypoint, or neither to use the current position)")
        public CoordinateXYZ position;

        @JsonPropertyDescription("The name of a saved waypoint")
        public String waypoint;

        public BetterBlockPos getCoordinates() {
            if (position != null) {
                return new BetterBlockPos(position.x, position.y, position.z);
            } else if (waypoint != null) {
                throw new IllegalArgumentException("Waypoint is not supported yet");
            } else {
                return BaritoneService.getPlayerPos();
            }
        }
    }

    public record CoordinateXYZ(
            @JsonProperty(required = true)
            int x,
            @JsonProperty(required = true)
            int y,
            @JsonProperty(required = true)
            int z
    ) {
    }

    public static class LocationXZOptionalY {
        @JsonPropertyDescription("X, Y (optional), and Z coordinates (specify one of position or waypoint, or neither to use the current XYZ position)")
        public CoordinateXZOptionalY position;

        @JsonPropertyDescription("The name of a saved waypoint")
        public String waypoint;

        public CoordinateXZOptionalY getCoordinates() {
            if (position != null) {
                return position;
            } else if (waypoint != null) {
                throw new IllegalArgumentException("Waypoint is not supported yet");
            } else {
                BetterBlockPos coordinates = BaritoneService.getPlayerPos();
                return new CoordinateXZOptionalY(coordinates.x, coordinates.y, coordinates.z);
            }
        }
    }

    public record CoordinateXZOptionalY(
            @JsonProperty(required = true)
            int x,
            int y,
            @JsonProperty(required = true)
            int z
    ) {
    }
}
