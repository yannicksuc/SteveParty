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
 * Wooden panel cut out along a stencil: a one block board (one pixel of wood per stencil pixel) whose outline is
 * cut with a stencil and an axe. Uncut, it is a plain square. Stands on a post like {@link WoodenPanelBlock}.
 */
public class WoodenCutoutPanelBlock extends AbstractStencilSignBlock {
    public static final MapCodec<WoodenCutoutPanelBlock> CODEC = createCodec(WoodenCutoutPanelBlock::new);

    /** Board of 16x16 stencil pixels, one model pixel each, front at BOARD_Z. */
    public static final float BOARD_X = 0, BOARD_Y = 0, BOARD_Z = 3, BOARD_DEPTH = 2;
    public static final SignShapes.Box BOARD = new SignShapes.Box(BOARD_X, BOARD_Y, BOARD_Z, BOARD_X + 16, BOARD_Y + 16, BOARD_Z + BOARD_DEPTH);
    private static final VoxelShape[] SHAPES = SignShapes.rotations(new SignShapes.Box[]{BOARD}, new SignShapes.Box[]{SignPosts.POST});
    private static final VoxelShape[] HUNG_SHAPES = SignShapes.hung(SignShapes.rotations(BOARD));

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
    public boolean hangsOnPosts() {
        return true;
    }

    @Override
    protected boolean canStandAt(WorldView world, BlockPos pos) {
        return SignPosts.standsOnPost(world, pos);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shape(state, SHAPES, HUNG_SHAPES);
    }
}
