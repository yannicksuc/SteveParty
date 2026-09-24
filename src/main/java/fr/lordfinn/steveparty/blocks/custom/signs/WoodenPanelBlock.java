package fr.lordfinn.steveparty.blocks.custom.signs;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;

/**
 * Big wooden panel set against a wooden post: it stands on a post (fence, wall, log... see the
 * {@code steveparty:sign_posts} tag), carries on the post behind its board and turns in 16 directions.
 * Made of any planks. Stencils engrave or paint the board like a traffic sign.
 */
public class WoodenPanelBlock extends AbstractStencilSignBlock {
    public static final MapCodec<WoodenPanelBlock> CODEC = createCodec(WoodenPanelBlock::new);
    /** What a panel can stand on. */
    public static final TagKey<Block> SIGN_POSTS = TagKey.of(RegistryKeys.BLOCK, Steveparty.id("sign_posts"));

    // Model space (pixels, front facing north): the post goes on through the block, the board is in front of it
    public static final SignShapes.Box POST = new SignShapes.Box(6, 0, 6, 10, 16, 10);
    public static final SignShapes.Box BOARD = new SignShapes.Box(-4, 1, 4, 20, 15, 6);
    private static final VoxelShape[] SHAPES = SignShapes.rotations(POST, BOARD);

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
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return world.getBlockState(pos.down()).isIn(SIGN_POSTS);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES[state.get(ROTATION)];
    }
}
