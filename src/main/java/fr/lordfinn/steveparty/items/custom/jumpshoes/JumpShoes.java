package fr.lordfinn.steveparty.items.custom.jumpshoes;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The Triple Jump Shoes' moves, shared by the client (which moves the player, for responsiveness) and the server
 * (which checks what the client reports, spares the fall damage and shows the effects to the others).
 * <ul>
 *   <li>triple jump: chained jumps ({@link TripleJumpChain}), the 2nd and 3rd higher;</li>
 *   <li>wall slide: falling against a wall slows the fall ({@link #SLIDE_SPEED}); jump then kicks off it
 *   ({@link #kickDirection});</li>
 *   <li>double jump, with the « Double Jump » enchantment: one more jump in the air.</li>
 * </ul>
 */
public final class JumpShoes {
    public static final RegistryKey<Enchantment> DOUBLE_JUMP = RegistryKey.of(RegistryKeys.ENCHANTMENT, Steveparty.id("double_jump"));

    /** Fastest fall (blocks per tick) while sliding down a wall: about 2 blocks per second. */
    public static final double SLIDE_SPEED = 0.1;
    /** Vertical speed of the double jump: about 1.7 blocks high. */
    public static final double DOUBLE_JUMP_SPEED = 0.5;
    /** Vertical and horizontal speeds of a wall kick: about 2 blocks high, 4 to 5 blocks away. */
    public static final double KICK_UP_SPEED = 0.55;
    public static final double KICK_AWAY_SPEED = 0.4;
    /** The share of a wall kick's direction that always points away from the wall (cosine of 60°). */
    public static final double MIN_AWAY = 0.5;
    /** How far from the body a wall still counts, for the client and for the server (latency). */
    public static final double CLIENT_REACH = 0.08;
    public static final double SERVER_REACH = 0.4;
    /** Ticks after leaving a wall during which jump still kicks off it. */
    public static final int KICK_GRACE_TICKS = 3;

    /** What the client reports, and the server relays to the others for their effects. */
    public enum Action {
        JUMP_2, JUMP_3, DOUBLE_JUMP, SLIDE_START, SLIDE_STOP, WALL_KICK;

        private static final Action[] VALUES = values();

        public static @Nullable Action byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : null;
        }
    }

    /** A jump off the ground by the client's own player wearing the shoes (set by the client). */
    public static Consumer<PlayerEntity> clientJump = player -> {
    };

    private JumpShoes() {
    }

    public static boolean wears(PlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.FEET).isOf(ModItems.TRIPLE_JUMP_SHOES);
    }

    /** True if the worn shoes carry « Double Jump ». */
    public static boolean hasDoubleJump(PlayerEntity player) {
        return hasDoubleJump(player.getEquippedStack(EquipmentSlot.FEET));
    }

    public static boolean hasDoubleJump(ItemStack stack) {
        if (!stack.isOf(ModItems.TRIPLE_JUMP_SHOES)) return false;
        for (RegistryEntry<Enchantment> enchantment : stack.getEnchantments().getEnchantments()) {
            if (enchantment.matchesKey(DOUBLE_JUMP)) return true;
        }
        return false;
    }

    /** No move of the shoes in water or lava, sneaking, flying, gliding, riding or climbing. */
    public static boolean blocked(PlayerEntity player) {
        return player.isTouchingWater() || player.isInLava() || player.isSneaking() || player.getAbilities().flying
                || player.isFallFlying() || player.hasVehicle() || player.isClimbing() || player.isSpectator();
    }

    /**
     * The side of a wall the player's body touches (within {@code reach}), the one most in the direction of
     * {@code toward}; null if none. Ladders, vines and scaffolding are not walls.
     */
    public static @Nullable Direction wallSide(PlayerEntity player, double reach, Vec3d toward) {
        Direction best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Direction side : Direction.Type.HORIZONTAL) {
            if (!touchesWall(player, side, reach)) continue;
            double score = toward.x * side.getOffsetX() + toward.z * side.getOffsetZ();
            if (score > bestScore) {
                bestScore = score;
                best = side;
            }
        }
        return best;
    }

    /** True if a wall, body high, stands on {@code side} of the player within {@code reach} (not a ladder...). */
    public static boolean touchesWall(PlayerEntity player, Direction side, double reach) {
        World world = player.getWorld();
        Box box = player.getBoundingBox();
        Box probe = new Box(box.minX, box.minY + 0.3, box.minZ, box.maxX, box.maxY - 0.2, box.maxZ)
                .offset(side.getOffsetX() * reach, 0, side.getOffsetZ() * reach);
        boolean wall = false;
        for (BlockPos pos : BlockPos.iterate(MathHelper.floor(probe.minX), MathHelper.floor(probe.minY), MathHelper.floor(probe.minZ),
                MathHelper.floor(probe.maxX), MathHelper.floor(probe.maxY), MathHelper.floor(probe.maxZ))) {
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) continue;
            VoxelShape shape = state.getCollisionShape(world, pos, ShapeContext.of(player));
            if (shape.isEmpty() || !intersects(shape, pos, probe)) continue;
            if (state.isIn(BlockTags.CLIMBABLE) || state.isOf(Blocks.SCAFFOLDING)) return false;
            wall = true;
        }
        return wall;
    }

    private static boolean intersects(VoxelShape shape, BlockPos pos, Box probe) {
        for (Box part : shape.getBoundingBoxes()) {
            if (part.offset(pos).intersects(probe)) return true;
        }
        return false;
    }

    /** The block of the wall on {@code side} touched by the player, at chest height. */
    public static BlockPos wallBlock(PlayerEntity player, Direction side) {
        double out = player.getWidth() / 2 + 0.1;
        return BlockPos.ofFloored(player.getX() + side.getOffsetX() * out, player.getY() + 1.0,
                player.getZ() + side.getOffsetZ() * out);
    }

    /**
     * The horizontal direction (unit) of a wall kick: where the player looks ({@code lookX, lookZ}), turned so that
     * it always goes away from the wall ({@code awayX, awayZ}, unit) by at least {@link #MIN_AWAY}, keeping its
     * sideways part. Looking into the wall (or straight up or down) kicks straight away from it.
     */
    public static Vec3d kickDirection(double lookX, double lookZ, double awayX, double awayZ) {
        double length = Math.hypot(lookX, lookZ);
        if (length < 1.0E-4) return new Vec3d(awayX, 0, awayZ);
        double x = lookX / length, z = lookZ / length;
        double along = x * awayX + z * awayZ;
        if (along >= MIN_AWAY) return new Vec3d(x, 0, z);
        double sideX = x - along * awayX, sideZ = z - along * awayZ;
        double side = Math.hypot(sideX, sideZ);
        if (side < 1.0E-4) return new Vec3d(awayX, 0, awayZ);
        double sideShare = Math.sqrt(1 - MIN_AWAY * MIN_AWAY) / side;
        return new Vec3d(awayX * MIN_AWAY + sideX * sideShare, 0, awayZ * MIN_AWAY + sideZ * sideShare);
    }

    /** The fall distance counted only from {@code launchY} (where the boosted jump, the slide or the kick began). */
    public static float clampFall(float fallDistance, double launchY, double y) {
        return (float) Math.min(fallDistance, Math.max(0, launchY - y));
    }
}
