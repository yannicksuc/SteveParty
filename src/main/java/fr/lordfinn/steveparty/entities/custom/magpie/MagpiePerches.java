package fr.lordfinn.steveparty.entities.custom.magpie;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

/**
 * Where a wild Pie stands on a block: on what you see of it, not on its collision. A fence or a wall collides 1.5
 * blocks high (so nothing jumps over it) but its post ends at the top of the block: the bird stands on the
 * <em>outline</em> shape (the one drawn when you aim at the block), on top of the part nearest to the block's middle
 * (the post of a fence or a wall, a chain's links, the upper step of a stairs, a slab's top...), centred on it.
 * A Magpie Nest: in it (its middle) when empty, on the free corner of its rim beside a pile of coins
 * ({@link MagpieNestBlockEntity#perch}). Its favourite perches: the block tag {@code steveparty:magpie_perches} (fences, walls, logs and woods stripped or
 * not, chains).
 */
public final class MagpiePerches {
    public static final TagKey<Block> FAVOURITES = TagKey.of(RegistryKeys.BLOCK, Steveparty.id("magpie_perches"));
    /** The bird's box, as its entity type's (ModEntities.WILD_MAGPIE). */
    public static final double WIDTH = 0.5, HEIGHT = 0.6;
    /** Its feet stay this far inside the edge of the part it stands on (a stairs' step: not on the very edge). */
    private static final double EDGE = 0.125;

    private MagpiePerches() {
    }

    /** Where its feet go on the block at {@code pos} (its visual top, centred on the part nearest the middle), or null. */
    public static @Nullable Vec3d perchOn(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.getFluidState().isEmpty() || isHarmful(state)) return null;
        if (state.getCollisionShape(world, pos).isEmpty()) return null; // grass, flowers: nothing to stand on
        if (world.getBlockEntity(pos) instanceof MagpieNestBlockEntity nest) return nest.perch(); // in it, or on its rim
        VoxelShape shape = state.getOutlineShape(world, pos, ShapeContext.absent());
        if (shape.isEmpty()) return null;
        double top = shape.getMax(Direction.Axis.Y);
        Box best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Box box : shape.getBoundingBoxes()) {
            if (box.maxY < top - 1.0E-4) continue;
            double x = MathHelper.clamp(0.5, box.minX, box.maxX), z = MathHelper.clamp(0.5, box.minZ, box.maxZ);
            double distance = (x - 0.5) * (x - 0.5) + (z - 0.5) * (z - 0.5);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = box;
            }
        }
        if (best == null) return null;
        double mx = Math.min(EDGE, (best.maxX - best.minX) / 2), mz = Math.min(EDGE, (best.maxZ - best.minZ) / 2);
        double x = MathHelper.clamp(0.5, best.minX + mx, best.maxX - mx);
        double z = MathHelper.clamp(0.5, best.minZ + mz, best.maxZ - mz);
        return new Vec3d(pos.getX() + x, pos.getY() + top, pos.getZ() + z);
    }

    /**
     * A perch on {@code pos} it can stand on: {@link #perchOn} and room for the bird above it (the perch's own
     * collision aside: a fence collides above its post).
     */
    public static @Nullable Vec3d freePerchOn(BlockView world, BlockPos pos) {
        Vec3d at = perchOn(world, pos);
        return at != null && hasRoom(world, pos, at) ? at : null;
    }

    /** Nothing but air (or what has no collision and no fluid) in the bird's box standing at {@code at} on {@code perch}. */
    public static boolean hasRoom(BlockView world, BlockPos perch, Vec3d at) {
        Box body = new Box(at.x - WIDTH / 2, at.y + 1.0E-3, at.z - WIDTH / 2, at.x + WIDTH / 2, at.y + HEIGHT, at.z + WIDTH / 2);
        for (BlockPos p : BlockPos.iterate(BlockPos.ofFloored(body.minX, body.minY, body.minZ),
                BlockPos.ofFloored(body.maxX, body.maxY, body.maxZ))) {
            if (p.equals(perch)) continue;
            BlockState state = world.getBlockState(p);
            if (!state.getFluidState().isEmpty()) return false;
            VoxelShape collision = state.getCollisionShape(world, p);
            if (collision.isEmpty()) continue;
            for (Box box : collision.getBoundingBoxes()) {
                if (box.offset(p).intersects(body)) return false;
            }
        }
        return true;
    }

    public static boolean isFavourite(BlockState state) {
        return state.isIn(FAVOURITES);
    }

    /** Never on fire, a campfire, magma, a cactus or berries. */
    private static boolean isHarmful(BlockState state) {
        return state.isIn(BlockTags.FIRE) || state.isIn(BlockTags.CAMPFIRES) || state.isOf(Blocks.MAGMA_BLOCK)
                || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.POWDER_SNOW);
    }
}
