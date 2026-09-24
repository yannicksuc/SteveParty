package fr.lordfinn.steveparty.blocks.custom.signs;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;

/**
 * Wooden panel cut out along a stencil: the stencil gives the board its outline (a star, an arrow...), each
 * stencil pixel being a 1.5 pixel square of wood. Without a stencil the board is a plain square. Stands on a post
 * like {@link WoodenPanelBlock}.
 */
public class WoodenCutoutPanelBlock extends AbstractStencilSignBlock {
    public static final MapCodec<WoodenCutoutPanelBlock> CODEC = createCodec(WoodenCutoutPanelBlock::new);

    /** Board of 16x16 stencil pixels of {@link #PIXEL} model pixels, from (BOARD_X, BOARD_Y) up, front at BOARD_Z. */
    public static final float PIXEL = 1.5F, BOARD_X = -4, BOARD_Y = 0, BOARD_Z = 4, BOARD_DEPTH = 2;
    public static final SignShapes.Box POST = new SignShapes.Box(6, 0, 6, 10, 16, 10);
    public static final SignShapes.Box BOARD = new SignShapes.Box(BOARD_X, BOARD_Y, BOARD_Z,
            BOARD_X + 16 * PIXEL, BOARD_Y + 16 * PIXEL, BOARD_Z + BOARD_DEPTH);
    private static final VoxelShape[] SHAPES = SignShapes.rotations(POST, BOARD);

    public WoodenCutoutPanelBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public SignMaterial getMaterialKind() {
        return SignMaterial.WOOD;
    }

    @Override
    public boolean usesSilhouette() {
        return true;
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return world.getBlockState(pos.down()).isIn(WoodenPanelBlock.SIGN_POSTS);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES[state.get(ROTATION)];
    }
}
