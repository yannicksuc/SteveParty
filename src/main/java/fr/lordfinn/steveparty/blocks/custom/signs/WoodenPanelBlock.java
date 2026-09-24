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
 * Big wooden panel on a post: it stands on any fence or wall ({@link SignPosts}), whose post goes on behind the
 * board without turning, and the board turns in 16 directions. Made of any planks. Stencils engrave or paint its
 * 16x16 middle like a traffic sign.
 */
public class WoodenPanelBlock extends AbstractStencilSignBlock {
    public static final MapCodec<WoodenPanelBlock> CODEC = createCodec(WoodenPanelBlock::new);

    // Model space (pixels, front facing north): 24x18 board with a rail at the top and the bottom, in front of the post
    public static final SignShapes.Box BOARD = new SignShapes.Box(-4, 0, 2, 20, 18, 5);
    private static final SignShapes.BoardOutline OUTLINE = new SignShapes.BoardOutline(BOARD);

    public WoodenPanelBlock(Settings settings) {
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
    public boolean hangsOnPosts() {
        return true;
    }

    @Override
    public float boardCenterY() {
        return (float) (BOARD.y1() + BOARD.y2()) / 2;
    }

    @Override
    public float boardBack() {
        return (float) (BOARD.z2());
    }

    @Override
    protected boolean canStandAt(WorldView world, BlockPos pos) {
        return SignPosts.standsOnPost(world, pos);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return OUTLINE.get(this, world, pos, state);
    }
}
