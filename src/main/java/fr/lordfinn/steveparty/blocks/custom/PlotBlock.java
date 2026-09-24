package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;

/**
 * An 8x8x4 plastic stud, on the floor, a wall or the ceiling like a button (solid, no redstone), with or without support.
 */
public class PlotBlock extends WallMountedBlock {
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
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH).with(FACE, BlockFace.FLOOR));
    }

    @Override
    protected MapCodec<? extends WallMountedBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE);
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
