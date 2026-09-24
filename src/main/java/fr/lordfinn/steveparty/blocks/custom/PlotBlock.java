package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.block.Waterloggable;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.tick.ScheduledTickView;
import org.jetbrains.annotations.Nullable;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

/**
 * An 8x8x4 plastic stud, on the floor, a wall or the ceiling like a button (solid, no redstone), with or without
 * support, and waterloggable. Like the plastic blocks it floats and follows bubble columns (see {@link PlasticBlock}),
 * swapping places with the water: it pops out onto the surface lying flat (floor face, no water in it), sticks under
 * a block that stops it (ceiling face) and lies on the magma that pulled it down (floor face).
 */
public class PlotBlock extends WallMountedBlock implements Waterloggable {
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
    public static final MapCodec<PlotBlock> CODEC = Block.createCodec(PlotBlock::new);

    private static final VoxelShape FLOOR_SHAPE = Block.createCuboidShape(4, 0, 4, 12, 4, 12);
    private static final VoxelShape CEILING_SHAPE = Block.createCuboidShape(4, 12, 4, 12, 16, 12);
    // Wall shapes are named after FACING: the stud points that way, stuck on the opposite block
    private static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(4, 4, 12, 12, 12, 16);
    private static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(4, 4, 0, 12, 12, 4);
    private static final VoxelShape EAST_SHAPE = Block.createCuboidShape(0, 4, 4, 4, 12, 12);
    private static final VoxelShape WEST_SHAPE = Block.createCuboidShape(12, 4, 4, 16, 12, 12);

    public PlotBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH).with(FACE, BlockFace.FLOOR)
                .with(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends WallMountedBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE, WATERLOGGED);
    }

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState state = super.getPlacementState(ctx);
        return state == null ? null
                : state.with(WATERLOGGED, ctx.getWorld().getFluidState(ctx.getBlockPos()).isOf(Fluids.WATER));
    }

    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        // Also when dry: a stud standing in a bubble column carries the column on above itself
        world.scheduleBlockTick(pos, this, PlasticBlock.getDelay(world, pos));
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        PlasticBlock.drift(world, pos, this, PlotBlock::step);
    }

    @Nullable
    private static BlockPos step(BlockState state, ServerWorld world, BlockPos pos) {
        if (!state.get(WATERLOGGED) || PlasticBlock.isChained(world, pos)) return null;
        BlockPos target;
        BlockState moved;
        if (PlasticBlock.getCurrent(world, pos) == PlasticBlock.Current.DOWN) {
            target = pos.down();
            if (!PlasticBlock.canSinkInto(world.getBlockState(target))) {
                turn(state, world, pos, BlockFace.FLOOR); // lies on the magma that pulled it down
                return null;
            }
            moved = state;
        } else {
            target = pos.up();
            BlockState aboveState = world.getBlockState(target);
            if (PlasticBlock.canRiseInto(aboveState)) {
                moved = state; // still under water
            } else if (aboveState.isAir()) {
                moved = state.with(WATERLOGGED, false).with(FACE, BlockFace.FLOOR); // floats on the surface, flat side down
            } else {
                turn(state, world, pos, BlockFace.CEILING); // pressed under the block that stops it
                return null;
            }
        }
        // Its water stays behind as a source: no water created or lost; what stands on it rides along
        return PlasticBlock.moveWithRiders(world, pos, target, moved) ? target : PlasticBlock.retry(world, pos, state);
    }

    /** Turns the stud in place (it keeps its water). */
    private static void turn(BlockState state, ServerWorld world, BlockPos pos, BlockFace face) {
        if (state.get(FACE) != face) world.setBlockState(pos, state.with(FACE, face), Block.NOTIFY_ALL);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView,
                                                   BlockPos pos, Direction direction, BlockPos neighborPos,
                                                   BlockState neighborState, Random random) {
        if (state.get(WATERLOGGED)) tickView.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        tickView.scheduleBlockTick(pos, this, PlasticBlock.getDelay(world, pos));
        return super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
    }

    // Studs go anywhere: no support needed, and they stay put when their support goes away
    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return true;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(FACE)) {
            case FLOOR -> FLOOR_SHAPE;
            case CEILING -> CEILING_SHAPE;
            case WALL -> switch (state.get(FACING)) {
                case SOUTH -> SOUTH_SHAPE;
                case EAST -> EAST_SHAPE;
                case WEST -> WEST_SHAPE;
                default -> NORTH_SHAPE;
            };
        };
    }
}
