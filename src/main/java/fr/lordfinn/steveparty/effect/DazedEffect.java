package fr.lordfinn.steveparty.effect;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.particles.MulaSparkleEffect;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;

import static fr.lordfinn.steveparty.effect.ModEffects.DAZED;

/**
 * Dazed by a Glandouille (its charge, a frosty one sliding into you, one shot out of a tower): rooted to the spot for
 * {@link #TICKS}, no walking, no jumping (its movement speed and jump strength are brought to 0, which the client
 * follows), stars turning around the head. Never any damage.
 */
public class DazedEffect extends StatusEffect {
    public static final int TICKS = 40;
    /** A Glandouille touching someone over several ticks (its charge, its flight, its slide): one daze. */
    public static final int CONTACT_TICKS = 10;
    private static final UUID NO_SOURCE = new UUID(0, 0);
    /** Who dazed whom last, and when (forgotten with the dazed entity). */
    private static final Map<LivingEntity, LastDaze> LAST_DAZES = new WeakHashMap<>();

    private record LastDaze(UUID source, long tick) {
    }
    private static final int STAR_COLOR = 0xFFE066;

    public DazedEffect() {
        super(StatusEffectCategory.HARMFUL, STAR_COLOR);
        addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, Steveparty.id("effect.dazed.speed"), -1.0,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(EntityAttributes.GENERIC_JUMP_STRENGTH, Steveparty.id("effect.dazed.jump"), -1.0,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /**
     * Dazes {@code entity} on the spot (its run stops there), with a dizzy "bonk". Dazes add up: already dazed, it is
     * for {@link #TICKS} more (Glandouilles thrown at someone one after the other keep him rooted, however fast they
     * come). Only the same Glandouille still touching it ({@code source}, within {@link #CONTACT_TICKS}) counts once:
     * false then. Server side.
     */
    public static boolean daze(LivingEntity entity, @Nullable Entity source) {
        if (entity.getWorld().isClient) return false;
        long now = entity.getWorld().getTime();
        LastDaze last = LAST_DAZES.get(entity);
        if (source != null && last != null && last.source.equals(source.getUuid()) && now - last.tick <= CONTACT_TICKS) return false;
        LAST_DAZES.put(entity, new LastDaze(source == null ? NO_SOURCE : source.getUuid(), now));
        StatusEffectInstance dazed = entity.getStatusEffect(DAZED);
        int ticks = dazed == null ? TICKS : dazed.getDuration() + TICKS;
        entity.addStatusEffect(new StatusEffectInstance(DAZED, ticks, 0, false, false, true));
        Vec3d v = entity.getVelocity();
        entity.setVelocity(0, Math.min(v.y, 0), 0);
        entity.velocityModified = true;
        entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(), ModSounds.GLANDOUILLE_BONK,
                SoundCategory.PLAYERS, 1f, 1.5f);
        return true;
    }

    /** The stars: three of them every few ticks. */
    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return duration % 4 == 0;
    }

    @Override
    public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld() instanceof ServerWorld world) {
            double angle = entity.age * 0.6;
            double r = 0.45;
            double y = entity.getY() + entity.getHeight() + 0.2;
            for (int i = 0; i < 3; i++) {
                double a = angle + i * Math.PI * 2 / 3;
                world.spawnParticles(new MulaSparkleEffect(STAR_COLOR, 0.8f, MulaSparkleEffect.STAR_BIT),
                        entity.getX() + Math.cos(a) * r, y, entity.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }
        return true;
    }
}
