package app.jer.axon;

import app.jer.axon.service.BaritoneService;
import baritone.api.utils.BetterBlockPos;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class Utils {
    public static String removeMinecraftPrefix(String originalID) {
        if (originalID.startsWith("minecraft:")) {
            return originalID.substring("minecraft:".length());
        }
        return originalID;
    }

    public static MutableText prefixText(String name) {
        return Text.empty()
                .append(Text.literal("[").formatted(Formatting.DARK_AQUA))
                .append(Text.literal(name).formatted(Formatting.AQUA))
                .append(Text.literal("] ").formatted(Formatting.DARK_AQUA));
    }

    public static class LocationXZ {
        @JsonPropertyDescription("X and Z coordinates (specify one of position or waypoint, or neither to use the " +
                "current position)")
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
    ) {}

    public static class LocationXYZ {
        @JsonPropertyDescription("X, Y, and Z coordinates (specify one of position or waypoint, or neither to use the" +
                " current position)")
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
        @JsonPropertyDescription("X, Y (optional), and Z coordinates (specify one of position or waypoint, or neither" +
                " to use the current XYZ position)")
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
            // Integer so it can be null
            Integer y,
            @JsonProperty(required = true)
            int z
    ) {
    }
}
