package fr.lordfinn.steveparty.blocks.custom;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/**
 * Plastic fence: made of plastic, it floats like the plastic blocks ({@link PlasticBlock}): under water it rises
 * until its top is level with the surface, bubble columns carry it, magma pulls it down, a chain holds it, and it
 * never cuts a bubble column. A sign hung on it (or standing on it) holds it too: it would fall off.
 */
public class PlasticFenceBlock extends FenceBlock {
    public PlasticFenceBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        PlasticBlock.scheduleStep(world, world, pos, this);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                   WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        // Water arriving, a bubble column forming, or a chain holding it being broken: try again
        PlasticBlock.scheduleStep(world, world, pos, this);
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        PlasticBlock.drift(world, pos, this, PlasticFenceBlock::step);
    }

    @Nullable
    private static BlockPos step(BlockState state, ServerWorld world, BlockPos pos, PlasticBlock.Flow flow) {
        if (!state.get(WATERLOGGED) || PlasticBlock.isHeld(world, pos, flow.current())) return null;
        BlockPos target;
        BlockState moved;
        if (flow.current() == PlasticBlock.Current.DOWN) {
            target = pos.down();
            if (!PlasticBlock.canSinkInto(world.getBlockState(target))) return null; // resting on the magma
            moved = state;
        } else {
            target = pos.up();
            // Like the plastic block, it stays in the water: its top level with the surface
            if (!PlasticBlock.canRiseInto(world.getBlockState(target))) return null;
            moved = state;
        }
        // Linked to the fences around its new place
        moved = Block.postProcessState(moved, world, target);
        return PlasticBlock.moveWithRiders(world, pos, target, moved, flow) ? target : PlasticBlock.retry(world, pos, state, flow);
    }
}
