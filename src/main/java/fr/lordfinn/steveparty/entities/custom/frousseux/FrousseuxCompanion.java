package fr.lordfinn.steveparty.entities.custom.frousseux;

import fr.lordfinn.steveparty.blocks.custom.frousseux.FrousseuxCandleHolderBlock;
import fr.lordfinn.steveparty.entities.PetTeleports;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * A tamed Frousseux and its owner: a lamp that floats along, never in the way.
 * <ul>
 *     <li><b>Lit or dark</b>: in a lit place (ambient light {@link #LIT_FROM} or more, its own left out:
 *     {@link #ambientLight}) it floats in front of its owner, a little to the side and below their eyes, seen and
 *     clicked as any mob. In the dark (caves) it becomes the mining lamp below. It switches after a short while of the
 *     other light ({@link #MODE_DELAY}), with a dead band ({@link #DARK_UNDER}), moving there smoothly.</li>
 *     <li><b>Where it floats in the dark</b>: by its owner's head, but never in front of them: at a shoulder, above and behind,
 *     or behind ({@link #SLOTS}), always outside a cone of {@link #CONE_HALF_ANGLE} degrees around where they look,
 *     in open air (never in a block). It keeps its place while it fits and moves on smoothly when they turn.</li>
 *     <li><b>Reaching for it</b> ({@link #reachesFor}): flint and steel in hand, it comes in front of them, within
 *     reach (to relight it). Sneaking with an empty hand, it stays put, so they can turn to it and click it.</li>
 *     <li><b>In the dark, never in the crosshair</b>: their crosshair goes through it unless they reach for it
 *     ({@link FrousseuxEntity#canHit}): their clicks go to the block or the mob behind it.</li>
 *     <li><b>Its owner's word</b> (an empty hand, see FrousseuxEntity#interactMob): sneaking, a following one sits
 *     and stays; a sitting one gets up on a plain click, and turns into a candle holder on a sneaking one.</li>
 *     <li><b>Cobwebs</b>: within {@link #WEB_RANGE} blocks of one of its Frousseux, they don't slow its owner
 *     ({@link #shieldsFromWebs}, CobwebBlockMixin).</li>
 * </ul>
 */
public final class FrousseuxCompanion {
    /** Its owner within this many blocks of it walks through cobwebs. */
    public static final double WEB_RANGE = 8.0;
    /** Farther than this from its owner, it pops back to them. */
    static final double TELEPORT_DISTANCE = 14.0;
    /** Never in this cone around its owner's look (half its angle, degrees): 30 asked, a margin for its lag. */
    static final double CONE_HALF_ANGLE = 38.0;
    private static final double CONE_COS = Math.cos(Math.toRadians(CONE_HALF_ANGLE));
    /**
     * Its places by its owner, from their eyes, in their facing: {right, up, back} in blocks. The shoulders first,
     * then above and behind, then behind (a narrow tunnel).
     */
    static final double[][] SLOTS = {{0.85, 0.3, 0.45}, {-0.85, 0.3, 0.45}, {0, 0.8, 0.5}, {0.45, 0.05, 1.1},
            {-0.45, 0.05, 1.1}, {0, 0.05, 1.4}};

    /** In the lit mode from this ambient light; back to the dark mode at this or less; after this long (ticks). */
    public static final int LIT_FROM = 5, DARK_UNDER = 3, MODE_DELAY = 40;
    /**
     * Its places in a lit place, in front of its owner (their body's facing, so turning the head to look at it
     * doesn't move it away): a little to the left (away from the hand in view) and below the eyes, then to the
     * right, then nearer.
     */
    static final double[][] LIT_SLOTS = {{-0.75, -0.3, -2.1}, {0.75, -0.3, -2.1}, {-0.45, -0.3, -1.3}, {0, -0.3, -1.0}};

    private FrousseuxCompanion() {
    }

    /** Its owner reaches for it: flint and steel in a hand, or sneaking with an empty hand. */
    public static boolean reachesFor(PlayerEntity player) {
        return holdsFlint(player) || player.isSneaking() && player.getMainHandStack().isEmpty();
    }

    static boolean holdsFlint(PlayerEntity player) {
        return player.getMainHandStack().isOf(Items.FLINT_AND_STEEL) || player.getOffHandStack().isOf(Items.FLINT_AND_STEEL);
    }

    /** A slot's place (the Frousseux's centre). */
    static Vec3d slot(PlayerEntity owner, double[] slot) {
        float yaw = owner.getHeadYaw() * MathHelper.RADIANS_PER_DEGREE;
        double sin = MathHelper.sin(yaw), cos = MathHelper.cos(yaw);
        // facing (-sin, cos), its right (-cos, -sin)
        return owner.getEyePos().add(-cos * slot[0] + sin * slot[2], slot[1], -sin * slot[0] - cos * slot[2]);
    }

    /** How far around its owner light sources are looked for (blocks, horizontally and vertically). */
    static final int SOURCE_REACH = 7, SOURCE_REACH_Y = 4;

    /**
     * The light at {@code at} without the Frousseux's own: the sky's (by the time of day and the weather), or the
     * brightest light source around (torches, lanterns, glowstone, lava...: its light less its distance, walls not
     * counted). The light engine can't tell its own light from the rest (its light is the brightest near it), so
     * the sources are looked for, leaving out the invisible light blocks (its own, and any other Frousseux's).
     * A few thousand block reads: asked once a second.
     */
    public static int ambientLight(World world, BlockPos at) {
        int best = world.getLightLevel(LightType.SKY, at) - world.getAmbientDarkness();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int dy = -SOURCE_REACH_Y; dy <= SOURCE_REACH_Y; dy++) {
            for (int dx = -SOURCE_REACH; dx <= SOURCE_REACH; dx++) {
                for (int dz = -SOURCE_REACH; dz <= SOURCE_REACH; dz++) {
                    int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (15 - distance <= best) continue; // can't beat what is found already
                    pos.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    BlockState state = world.getBlockState(pos);
                    int luminance = state.getLuminance();
                    if (luminance - distance > best && !state.isOf(Blocks.LIGHT)) best = luminance - distance;
                }
            }
        }
        return MathHelper.clamp(best, 0, 15);
    }

    /** A lit-mode place's spot (the Frousseux's centre), in its owner's body facing. */
    static Vec3d litSlot(PlayerEntity owner, double[] slot) {
        float yaw = owner.getBodyYaw() * MathHelper.RADIANS_PER_DEGREE;
        double sin = MathHelper.sin(yaw), cos = MathHelper.cos(yaw);
        return owner.getEyePos().add(-cos * slot[0] + sin * slot[2], slot[1], -sin * slot[0] - cos * slot[2]);
    }

    /** In front of its owner, where it is seen (lit mode): the first lit-mode place in open air. */
    static Vec3d litSpot(World world, PlayerEntity owner) {
        Vec3d eye = owner.getEyePos();
        for (double[] slot : LIT_SLOTS) {
            Vec3d at = litSlot(owner, slot);
            if (!FrousseuxFlight.solid(world, BlockPos.ofFloored(at))
                    && !FrousseuxFlight.solid(world, BlockPos.ofFloored(eye.add(at.subtract(eye).multiply(0.5))))) return at;
        }
        return inFront(world, owner);
    }

    /** A place it may float at: out of the cone of its owner's look, in open air, with open air on the way there. */
    static boolean fits(World world, PlayerEntity owner, Vec3d centre) {
        Vec3d eye = owner.getEyePos();
        Vec3d to = centre.subtract(eye);
        double length = to.length();
        if (length < 1.0E-3) return false;
        if (owner.getRotationVec(1.0f).dotProduct(to) / length > CONE_COS) return false;
        return !FrousseuxFlight.solid(world, BlockPos.ofFloored(centre))
                && !FrousseuxFlight.solid(world, BlockPos.ofFloored(eye.add(to.multiply(0.5))));
    }

    /** In front of its owner, within reach (they hold flint and steel); nearer if a wall is there. */
    static Vec3d inFront(World world, PlayerEntity owner) {
        Vec3d eye = owner.getEyePos(), look = owner.getRotationVec(1.0f);
        for (double distance = 1.4; distance > 0.6; distance -= 0.2) {
            Vec3d at = eye.add(look.multiply(distance)).add(0, -0.25, 0);
            if (!FrousseuxFlight.solid(world, BlockPos.ofFloored(at))) return at;
        }
        return eye.add(look.multiply(0.7));
    }

    /** Within this of its owner's camera it fades out (its flame half), within this it is gone (blocks). */
    public static final double FADE_DISTANCE = 3.0, GONE_DISTANCE = 1.5;

    /**
     * How much a following Frousseux fades for {@code viewer}, its owner (never for anyone else): never when they
     * reach for it (sneaking with an empty hand, flint and steel) or it sits; gone when right by their camera
     * (crossing in front of it, or on them); faded (its body nearly invisible, its flame half) a little farther, but
     * for one in front of them in a lit place, where it is meant to be looked at.
     */
    public static int fadeFor(FrousseuxEntity frousseux, PlayerEntity viewer, Vec3d camera) {
        if (!frousseux.isTamed() || !frousseux.isOwner(viewer) || frousseux.isSitting() || reachesFor(viewer)) {
            return FrousseuxEntity.FADE_NONE;
        }
        double distance = camera.distanceTo(frousseux.getBoundingBox().getCenter());
        if (distance < GONE_DISTANCE) return FrousseuxEntity.FADE_ON;
        if (distance < FADE_DISTANCE && !frousseux.isLitMode()) return FrousseuxEntity.FADE_NEAR;
        return FrousseuxEntity.FADE_NONE;
    }

    /** Whether cobwebs let {@code entity} through: a player within {@link #WEB_RANGE} blocks of their Frousseux. */
    public static boolean shieldsFromWebs(Entity entity) {
        if (!(entity instanceof PlayerEntity player) || player.isSpectator()) return false;
        return !player.getWorld().getEntitiesByClass(FrousseuxEntity.class, player.getBoundingBox().expand(WEB_RANGE),
                frousseux -> frousseux.isAlive() && !frousseux.isBoardActor() && frousseux.isOwner(player)
                        && frousseux.squaredDistanceTo(player) <= WEB_RANGE * WEB_RANGE).isEmpty();
    }

    /** Where it lands by its owner after a teleport (its feet): its place by them now (lit or dark mode). */
    static Vec3d arrivalSpot(FrousseuxEntity frousseux, PlayerEntity owner) {
        World world = owner.getWorld();
        Vec3d centre = null;
        if (frousseux.isLitMode()) centre = litSpot(world, owner);
        else for (double[] slot : SLOTS) {
            Vec3d at = slot(owner, slot);
            if (fits(world, owner, at)) {
                centre = at;
                break;
            }
        }
        if (centre == null) centre = owner.getEyePos().add(0, 0.5, 0);
        return centre.subtract(0, FrousseuxEntity.HEIGHT / 2, 0);
    }

    /** A sitting one turned into a candle holder ({@link FrousseuxCandleHolderBlock}), where it floats. */
    static void toCandleHolder(FrousseuxEntity frousseux, ServerWorld world, PlayerEntity player) {
        FrousseuxCandleHolderBlock.fallAsleep(frousseux, world, player);
    }

    /** Following its owner about, by their head, out of their way. */
    static final class Follow extends Goal {
        private final FrousseuxEntity frousseux;
        private @Nullable PlayerEntity owner;
        private int slot;
        private int repick;
        private @Nullable Vec3d centre;

        Follow(FrousseuxEntity frousseux) {
            this.frousseux = frousseux;
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            if (!frousseux.isTamed() || frousseux.isSitting() || frousseux.isBoardActor() || !frousseux.isAlive()) return false;
            owner = frousseux.getOwnerPlayer();
            return owner != null && owner.isAlive() && !owner.isSpectator();
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void start() {
            repick = 0;
            centre = null;
        }

        @Override
        public void stop() {
            owner = null;
            frousseux.flight().stop();
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (owner == null) return;
            World world = frousseux.getWorld();
            if (holdsFlint(owner)) {
                centre = inFront(world, owner);
            } else if (frousseux.isLitMode()) { // a lit place: in front, seen, clicked as usual
                centre = litSpot(world, owner);
            } else if (centre == null || !reachesFor(owner)) { // sneaking with an empty hand: it stays put
                if (--repick <= 0 || !fits(world, owner, slot(owner, SLOTS[slot]))) {
                    repick = 5;
                    slot = pickSlot(world, owner);
                }
                centre = slot(owner, SLOTS[slot]);
            }
            Vec3d feet = centre.subtract(0, FrousseuxEntity.HEIGHT / 2, 0);
            double distance = frousseux.getPos().distanceTo(feet);
            // too far behind: it pops back by them (their teleports: PetTeleports, as soon as they happen)
            if (frousseux.squaredDistanceTo(owner) > TELEPORT_DISTANCE * TELEPORT_DISTANCE
                    && owner.getWorld() instanceof ServerWorld ownerWorld) {
                frousseux.flight().stop();
                PetTeleports.bring(frousseux, ownerWorld, feet, frousseux.getYaw());
                return;
            }
            frousseux.getMoveControl().moveTo(feet.x, feet.y, feet.z, MathHelper.clamp(distance * 0.2, 0.05, 0.7));
            // where it looks is its look goals' (FrousseuxEntity): at its owner when close, about it otherwise
        }

        /** Its place now: the one it has while it fits, else the first one that does, else behind. */
        private int pickSlot(World world, PlayerEntity owner) {
            if (fits(world, owner, slot(owner, SLOTS[slot]))) return slot;
            for (int i = 0; i < SLOTS.length; i++) if (fits(world, owner, slot(owner, SLOTS[i]))) return i;
            return SLOTS.length - 1;
        }
    }
}
