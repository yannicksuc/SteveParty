package fr.lordfinn.steveparty.entities.custom;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Carried by Mulas at night, like balloons: a player holding Mulas on leads, with enough lift between them, rises with
 * them when they go up into the night sky, is carried where they drift (no steering), and is set down gently at dawn.
 * <p>
 * Lift of each Mula held (its fullness): more than {@value #BIG} full = {@value #LIFT_BIG}, more than {@value #MEDIUM}
 * = {@value #LIFT_MEDIUM}, else {@value #LIFT_SMALL}; carried when the sum reaches 1 (one big Mula, two half full, or
 * three of any size). Only Mulas whose lead holder is that player count; only at night; never over the void, never
 * higher than {@value #MAX_ABOVE_GROUND} blocks above the ground (the night band of the Mulas).
 * <p>
 * Server-safe: the player is moved by vanilla effects (Levitation to rise, Slow Falling to hover and to come down; no
 * flight is ever granted, so no "flying is not enabled" kick), refreshed 4 times a second while carried. Letting go (a
 * lead dropped or broken, a Mula bursting, too little lift) or the dawn gives Slow Falling until the ground, so nobody
 * falls hard; no fall damage while carried. Cost: one small entity query per player 4 times a second.
 */
public final class MulaLift {
    private MulaLift() {
    }

    /** Fullness thresholds and the lift they give (the user's values). */
    public static final double BIG = 0.6, MEDIUM = 0.3;
    public static final float LIFT_BIG = 1f, LIFT_MEDIUM = 0.5f, LIFT_SMALL = 0.34f;
    /** Carried at this height above the ground (blocks); never higher than the max. */
    public static final double TARGET_ABOVE_GROUND = 12, MAX_ABOVE_GROUND = 20;
    /** Every this many ticks. */
    public static final int PERIOD = 5;
    /** After letting go: Slow Falling until the ground, at most this long (ticks). */
    public static final int RELEASE_TICKS = 600;
    /** Gentle drift where the Mulas go (blocks/tick added to the player's velocity). */
    private static final double DRIFT = 0.025;

    private static final class State {
        boolean carried;
        int releaseTicks;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    public static void initialize() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % PERIOD != 0) return;
            for (ServerPlayerEntity player : world.getPlayers()) update(world, player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUuid()));
    }

    /** Lift of one Mula, from how full it is (0..1). */
    public static float liftOf(double fullness) {
        if (fullness > BIG) return LIFT_BIG;
        if (fullness > MEDIUM) return LIFT_MEDIUM;
        return LIFT_SMALL;
    }

    /** Enough lift to carry a player. */
    public static boolean lifts(float total) {
        return total >= 1f - 1.0E-4f;
    }

    public static float liftOf(List<MulaEntity> mulas) {
        float total = 0;
        for (MulaEntity m : mulas) total += liftOf(m.getHunger() / (double) MulaEntity.MAX_HUNGER);
        return total;
    }

    public static boolean isCarried(ServerPlayerEntity player) {
        State s = STATES.get(player.getUuid());
        return s != null && s.carried;
    }

    /** Server, 4 times a second per player: carried or not, and the effects that move them. */
    public static void update(ServerWorld world, ServerPlayerEntity player) {
        State state = STATES.get(player.getUuid());
        List<MulaEntity> mulas = world.getEntitiesByClass(MulaEntity.class, player.getBoundingBox().expand(12),
                m -> m.isAlive() && !m.isBursting() && m.getLeashHolder() == player);
        if (mulas.isEmpty() && state == null) return;
        if (state == null) {
            state = new State();
            STATES.put(player.getUuid(), state);
        }
        double ground = groundBelow(world, player);
        boolean carry = world.isNight() && !world.getDimension().hasCeiling() && !player.isSpectator()
                && !player.getAbilities().flying && !Double.isNaN(ground) && lifts(liftOf(mulas));
        for (MulaEntity m : mulas) m.setCarrying(carry);
        if (carry) {
            if (!state.carried) {
                // the lift starts: a happy Allay voice from the Mulas
                MulaEntity first = mulas.get(0);
                world.playSound(null, first.getX(), first.getY(), first.getZ(), SoundEvents.ENTITY_ALLAY_ITEM_GIVEN,
                        SoundCategory.NEUTRAL, 0.5f, 1.3f * first.voice());
            }
            state.carried = true;
            state.releaseTicks = 0;
            player.fallDistance = 0;
            double above = player.getY() - ground;
            double target = Math.min(TARGET_ABOVE_GROUND, MAX_ABOVE_GROUND);
            if (above < target - 0.5 && above < MAX_ABOVE_GROUND) {
                // rising: gently (Levitation I), a little faster with plenty of lift (II at most)
                int amplifier = liftOf(mulas) >= 2f ? 1 : 0;
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, PERIOD * 3, amplifier, true, false, true));
            } else {
                removeOurLevitation(player);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, PERIOD * 3, 0, true, false, true));
            }
            // carried where they drift: a slow turning breeze
            double a = world.getTime() / 600.0 + (player.getId() % 7);
            player.addVelocity(Math.cos(a) * DRIFT, 0, Math.sin(a) * DRIFT);
            player.velocityModified = true;
            return;
        }
        if (state.carried) {
            // let go, or the dawn: slow falling to the ground
            state.carried = false;
            state.releaseTicks = RELEASE_TICKS;
            removeOurLevitation(player);
        }
        if (state.releaseTicks > 0) {
            state.releaseTicks -= PERIOD;
            if (player.isOnGround()) {
                state.releaseTicks = 0;
            } else {
                player.fallDistance = 0;
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, PERIOD * 6, 0, true, false, true));
            }
        }
        if (mulas.isEmpty() && state.releaseTicks <= 0) STATES.remove(player.getUuid());
    }

    /** Only the Levitation it gave (ambient), never one from a shulker or a potion. */
    private static void removeOurLevitation(ServerPlayerEntity player) {
        StatusEffectInstance levitation = player.getStatusEffect(StatusEffects.LEVITATION);
        if (levitation != null && levitation.isAmbient()) player.removeStatusEffect(StatusEffects.LEVITATION);
    }

    /** Ground under the player (first solid block within 64), or NaN over the void. */
    private static double groundBelow(ServerWorld world, ServerPlayerEntity player) {
        BlockPos.Mutable pos = new BlockPos.Mutable(MathHelper.floor(player.getX()), MathHelper.floor(player.getY()),
                MathHelper.floor(player.getZ()));
        for (int i = 0; i < 64 && pos.getY() > world.getBottomY(); i++) {
            if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) return pos.getY() + 1.0;
            pos.move(0, -1, 0);
        }
        return Double.NaN;
    }
}
