package fr.lordfinn.steveparty.blocks.custom.pipe;

import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

/** The one solid block a pipe is fixed to, if any (a pipe connects to any number of pipes but one solid block). */
public enum PipeSolid implements StringIdentifiable {
    NONE(null), DOWN(Direction.DOWN), UP(Direction.UP), NORTH(Direction.NORTH), SOUTH(Direction.SOUTH),
    WEST(Direction.WEST), EAST(Direction.EAST);

    private final @Nullable Direction direction;

    PipeSolid(@Nullable Direction direction) {
        this.direction = direction;
    }

    public @Nullable Direction direction() {
        return direction;
    }

    public static PipeSolid of(@Nullable Direction direction) {
        return direction == null ? NONE : values()[direction.ordinal() + 1];
    }

    @Override
    public String asString() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
