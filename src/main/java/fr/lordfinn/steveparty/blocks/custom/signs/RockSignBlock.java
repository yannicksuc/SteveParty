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
 * Standing stone, like a small menhir of a dolmen: a slightly leaning stone with a 16x16 front and one or two
 * pebbles at its foot (which ones depends on where it stands, the big stone never changes). Made of any rock,
 * modded ones included. Stencils really engrave it (the engraved pixels are dug in), and a dye paints the engraving.
 */
public class RockSignBlock extends AbstractStencilSignBlock {
    public static final MapCodec<RockSignBlock> CODEC = createCodec(RockSignBlock::new);

    /** How much the stone leans back, around the bottom of its back (drawn by the client model). */
    public static final float TILT_DEGREES = 10;
    /** The stone in model space (pixels, front facing north) before leaning: its front is the 16x16 engraving. */
    public static final float FRONT_Z = 5, BACK_Z = 11, HEIGHT = 16;

    // Rough outline of the leaning stone and its top
    private static final VoxelShape[] SHAPES = SignShapes.rotations(
            new SignShapes.Box(0, 0, 5, 16, 8, 12.5),
            new SignShapes.Box(0, 8, 6, 16, 16, 14),
            new SignShapes.Box(2, 16, 8, 14, 19, 14.5));

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
