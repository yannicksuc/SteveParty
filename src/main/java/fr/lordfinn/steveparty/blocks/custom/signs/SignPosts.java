package fr.lordfinn.steveparty.blocks.custom.signs;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

/**
 * Signs set on a post: the wooden panels and the plastic road signs stand on a fence or a wall (any of the
 * {@code steveparty:sign_posts} tag: every fence and wall, modded ones included). The post goes on through the
 * sign's block, drawn with the model of the fence below it, and never turns: only the board does. Put against the
 * side of a post, they hang on it instead (see {@link AbstractStencilSignBlock#HUNG}).
 */
public final class SignPosts {
    public static final TagKey<Block> TAG = TagKey.of(RegistryKeys.BLOCK, Steveparty.id("sign_posts"));
    /** The post in the sign's block, in block pixels (a fence post). */
    public static final SignShapes.Box POST = new SignShapes.Box(6, 0, 6, 10, 16, 10);

    private SignPosts() {
    }

    public static boolean isPost(BlockState state) {
        return state.isIn(TAG);
    }

    public static boolean standsOnPost(WorldView world, BlockPos pos) {
        return isPost(world.getBlockState(pos.down()));
    }
}
