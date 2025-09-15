package fr.lordfinn.steveparty.blocks.custom;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;

public class StarFragmentsBlock extends Block {
    public StarFragmentsBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected boolean hasSidedTransparency(BlockState state) {
        return true;
    }

    @Override
    public boolean isSideInvisible(BlockState state, BlockState adjacentState, Direction side) {
        if (adjacentState.isOf(this)) {
            return true;
        }
        return super.isSideInvisible(state, adjacentState, side);
    }
}
