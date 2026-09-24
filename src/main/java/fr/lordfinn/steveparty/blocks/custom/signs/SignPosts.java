package fr.lordfinn.steveparty.blocks.custom.signs;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

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

    /** @return the shape of the post alone of this fence or wall (its unconnected state). */
    public static VoxelShape postShape(BlockState post) {
        return post.getBlock().getDefaultState().getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
    }

    /**
     * @return how far (pixels) the post the sign at {@code pos} stands on, or hangs on, reaches from the middle of its
     * block towards the front of the sign; NaN without a post
     */
    public static double reach(BlockView world, BlockPos pos, BlockState sign) {
        Direction hung = AbstractStencilSignBlock.hungFacing(sign);
        VoxelShape post;
        // (air first: block shapes are cached before the tags are loaded, with nothing around)
        if (hung != null) {
            // The real post behind it (a wall may have no pillar there)
            BlockPos at = pos.offset(hung.getOpposite());
            BlockState support = world.getBlockState(at);
            if (support.isAir() || !isPost(support)) return Double.NaN;
            post = support.getOutlineShape(world, at);
        } else {
            BlockState below = world.getBlockState(pos.down());
            if (below.isAir() || !isPost(below)) return Double.NaN;
            post = postShape(below);
        }
        return reach(post, SignShapes.angleDegrees(sign.get(AbstractStencilSignBlock.ROTATION)));
    }

    /**
     * @param post         shape of the post, in its block
     * @param angleDegrees turn of the sign's model (see {@link SignShapes#angleDegrees(int)})
     * @return how far (pixels) the post reaches from the middle of its block towards the front of the turned sign
     */
    public static double reach(@Nullable VoxelShape post, float angleDegrees) {
        if (post == null || post.isEmpty()) return Double.NaN;
        double angle = Math.toRadians(angleDegrees);
        // The model's front (-z) once turned, like the client model does
        double fx = -Math.sin(angle), fz = -Math.cos(angle);
        double rx = fx > 0 ? post.getMax(Direction.Axis.X) * 16 - 8 : 8 - post.getMin(Direction.Axis.X) * 16;
        double rz = fz > 0 ? post.getMax(Direction.Axis.Z) * 16 - 8 : 8 - post.getMin(Direction.Axis.Z) * 16;
        return rx * Math.abs(fx) + rz * Math.abs(fz);
    }
}
