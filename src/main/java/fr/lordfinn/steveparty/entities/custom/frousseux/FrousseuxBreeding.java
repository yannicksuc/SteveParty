package fr.lordfinn.steveparty.entities.custom.frousseux;

import fr.lordfinn.steveparty.entities.ModEntities;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Two tamed Frousseux lit with a flint and steel ({@link FrousseuxEntity#fallInLove}), each at full health, close to
 * each other: they fly together and a baby Frousseux comes out of their two flames, like a wolf's pup. The baby is
 * tamed, its owner one of its parents' owners (one or the other, half the time each), its colour a random candle's
 * ({@link FrousseuxColor#random}); it grows up in {@link #GROW_TICKS}. Each parent then rests {@link #COOLDOWN_TICKS}.
 */
public final class FrousseuxBreeding {
    /** How long a lit one looks for a mate (as an animal in love). */
    public static final int LOVE_TICKS = 600;
    /** How long a parent rests before it can be lit for a mate again (an animal's 5 minutes). */
    public static final int COOLDOWN_TICKS = 6000;
    /** How long a baby takes to grow up (an animal's 20 minutes). */
    public static final int GROW_TICKS = 24000;
    /** How far a lit one looks for a mate, in blocks. */
    public static final double MATE_RANGE = 8.0;
    /** Close enough to have their baby. */
    private static final double MEET_DISTANCE = 0.9;

    private FrousseuxBreeding() {
    }

    /** The nearest other lit, tamed, grown Frousseux within {@link #MATE_RANGE}, or null. */
    static @Nullable FrousseuxEntity findMate(FrousseuxEntity frousseux) {
        List<FrousseuxEntity> mates = frousseux.getWorld().getEntitiesByClass(FrousseuxEntity.class,
                frousseux.getBoundingBox().expand(MATE_RANGE), other -> other != frousseux && other.isInLove());
        FrousseuxEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (FrousseuxEntity other : mates) {
            double distance = frousseux.squaredDistanceTo(other);
            if (distance < best) {
                best = distance;
                nearest = other;
            }
        }
        return nearest;
    }

    /**
     * Their baby, between them: tamed by one of their owners (half the time each), a random colour, a baby for
     * {@link #GROW_TICKS}. Both parents rest. Null if it could not be spawned.
     */
    public static @Nullable FrousseuxEntity breed(ServerWorld world, FrousseuxEntity one, FrousseuxEntity other) {
        FrousseuxEntity baby = ModEntities.FROUSSEUX.create(world);
        if (baby == null) return null;
        UUID owner = world.random.nextBoolean() ? one.getOwner() : other.getOwner();
        if (owner == null) owner = one.getOwner() != null ? one.getOwner() : other.getOwner();
        Vec3d at = one.getPos().add(other.getPos()).multiply(0.5);
        baby.refreshPositionAndAngles(at.x, at.y, at.z, one.getYaw(), 0);
        baby.setOwner(owner);
        baby.setGrowTicks(GROW_TICKS);
        baby.initialize(world, world.getLocalDifficulty(baby.getBlockPos()), SpawnReason.BREEDING, null); // its colour
        one.bred();
        other.bred();
        world.spawnEntity(baby);
        world.spawnParticles(ParticleTypes.HEART, at.x, at.y + 0.5, at.z, 7, 0.3, 0.2, 0.3, 0.02);
        world.spawnParticles(ParticleTypes.FLAME, at.x, at.y + 0.4, at.z, 12, 0.2, 0.2, 0.2, 0.02);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.NEUTRAL, 0.5f, 1.6f);
        return baby;
    }

    /** A lit one flies to its mate (before following its owner); together, their baby. */
    static final class MateGoal extends Goal {
        private final FrousseuxEntity frousseux;
        private @Nullable FrousseuxEntity mate;

        MateGoal(FrousseuxEntity frousseux) {
            this.frousseux = frousseux;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (!frousseux.isInLove()) return false;
            mate = findMate(frousseux);
            return mate != null;
        }

        @Override
        public boolean shouldContinue() {
            return mate != null && mate.isAlive() && mate.isInLove() && frousseux.isInLove();
        }

        @Override
        public void stop() {
            mate = null;
            frousseux.flight().stop();
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (mate == null) return;
            frousseux.getLookControl().lookAt(mate, 10f, 10f);
            frousseux.getMoveControl().moveTo(mate.getX(), mate.getY(), mate.getZ(), 0.4);
            // one of the two has the baby (the one with the smaller id, so it is born once)
            if (frousseux.getId() < mate.getId() && frousseux.squaredDistanceTo(mate) < MEET_DISTANCE * MEET_DISTANCE
                    && frousseux.getWorld() instanceof ServerWorld world) {
                breed(world, frousseux, mate);
            }
        }
    }
}
