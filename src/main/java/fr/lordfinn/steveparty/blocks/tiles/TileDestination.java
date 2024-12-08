package fr.lordfinn.steveparty.blocks.tiles;

import net.minecraft.util.math.BlockPos;

public record TileDestination(BlockPos position, boolean isTile) {

    @Override
    public String toString() {
        return "PositionWithType{" +
                "position=" + position +
                ", isTile=" + isTile +
                '}';
    }
}
