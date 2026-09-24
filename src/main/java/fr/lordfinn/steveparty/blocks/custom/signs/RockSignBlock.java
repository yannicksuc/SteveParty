package fr.lordfinn.steveparty.blocks.custom.signs;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;

/**
 * Standing stone, like a small menhir of a dolmen: a slightly leaning stone up to 1.5 blocks high with one or two
 * pebbles at its foot (which ones depends on where it stands, the big stone never changes). Made of any rock,
 * modded ones included. Stencils engrave it, or paint it with a dye.
 */
public class RockSignBlock extends AbstractStencilSignBlock {
    public static final MapCodec<RockSignBlock> CODEC = createCodec(RockSignBlock::new);

    // Rough outline of the leaning stone (model space, front facing north)
    private static final VoxelShape[] SHAPES = SignShapes.rotations(
            new SignShapes.Box(3, 0, 2, 13, 6, 8),
            new SignShapes.Box(3, 6, 4, 13, 12, 10.5),
            new SignShapes.Box(3, 12, 6, 13, 18, 13),
            new SignShapes.Box(4, 18, 8, 12, 21, 14));

    public RockSignBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public SignMaterial getMaterialKind() {
        return SignMaterial.ROCK;
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        BlockPos below = pos.down();
        return world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES[state.get(ROTATION)];
    }
}
