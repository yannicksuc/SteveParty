package fr.lordfinn.steveparty.entities.custom.frousseux;

import fr.lordfinn.steveparty.blocks.custom.frousseux.FrousseuxCandleHolderBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * A tamed Frousseux and its owner: a lamp that floats along, never in the way.
 * <ul>
 *     <li><b>Where it floats</b>: by its owner's head, but never in front of them: at a shoulder, above and behind,
 *     or behind ({@link #SLOTS}), always outside a cone of {@link #CONE_HALF_ANGLE} degrees around where they look,
 *     in open air (never in a block). It keeps its place while it fits and moves on smoothly when they turn.</li>
 *     <li><b>Reaching for it</b> ({@link #reachesFor}): flint and steel in hand, it comes in front of them, within
 *     reach (to relight it). Sneaking with an empty hand, it stays put, so they can turn to it and click it.</li>
 *     <li><b>Never in the crosshair</b>: their crosshair goes through it unless they reach for it
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

    /** Whether cobwebs let {@code entity} through: a player within {@link #WEB_RANGE} blocks of their Frousseux. */
    public static boolean shieldsFromWebs(Entity entity) {
        if (!(entity instanceof PlayerEntity player) || player.isSpectator()) return false;
        return !player.getWorld().getEntitiesByClass(FrousseuxEntity.class, player.getBoundingBox().expand(WEB_RANGE),
                frousseux -> frousseux.isAlive() && !frousseux.isBoardActor() && frousseux.isOwner(player)
                        && frousseux.squaredDistanceTo(player) <= WEB_RANGE * WEB_RANGE).isEmpty();
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
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
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
            } else if (centre == null || !reachesFor(owner)) { // sneaking with an empty hand: it stays put
                if (--repick <= 0 || !fits(world, owner, slot(owner, SLOTS[slot]))) {
                    repick = 5;
                    slot = pickSlot(world, owner);
                }
                centre = slot(owner, SLOTS[slot]);
            }
            Vec3d feet = centre.subtract(0, FrousseuxEntity.HEIGHT / 2, 0);
            double distance = frousseux.getPos().distanceTo(feet);
            if (frousseux.squaredDistanceTo(owner) > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
                frousseux.flight().stop();
                frousseux.requestTeleport(feet.x, feet.y, feet.z);
                return;
            }
            frousseux.getMoveControl().moveTo(feet.x, feet.y, feet.z, MathHelper.clamp(distance * 0.2, 0.05, 0.7));
            // nearly still: it looks where its owner looks
            if (frousseux.getVelocity().horizontalLengthSquared() < 0.0025) {
                float yaw = MathHelper.stepUnwrappedAngleTowards(frousseux.getYaw(), owner.getHeadYaw(), 8f);
                frousseux.setYaw(yaw);
                frousseux.setBodyYaw(yaw);
                frousseux.setHeadYaw(yaw);
            }
        }

        /** Its place now: the one it has while it fits, else the first one that does, else behind. */
        private int pickSlot(World world, PlayerEntity owner) {
            if (fits(world, owner, slot(owner, SLOTS[slot]))) return slot;
            for (int i = 0; i < SLOTS.length; i++) if (fits(world, owner, slot(owner, SLOTS[i]))) return i;
            return SLOTS.length - 1;
        }
    }
}
