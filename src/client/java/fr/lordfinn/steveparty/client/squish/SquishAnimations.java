package fr.lordfinn.steveparty.client.squish;

import fr.lordfinn.steveparty.client.access.SquishStretchState;
import fr.lordfinn.steveparty.particles.KamekShapeEffect;
import fr.lordfinn.steveparty.payloads.custom.SquishAnimationPayload;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.sound.SoundCategory;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * Client-only squish (tokenization) animation: the transformation of the Tokenizer Wand's spell, in the style of
 * Kamek's growth spell. The server applies the final scale once and sends a single {@link SquishAnimationPayload};
 * here the creature reaches its new size in a few jelly pulses (each step overshoots, then squashes and stretches
 * back into shape) while Kamek's shapes and sparkles swirl around it. Interpolated every frame with the tick delta,
 * so it is smooth whatever the TPS / FPS, and costs nothing on the network.
 * <p>
 * Render thread only. No allocation per frame: one {@link Animation} per squish, looked up by entity id.
 */
public final class SquishAnimations {
    /** Number of growth pulses: the size changes by steps, like a creature swelling under the spell. */
    private static final int PULSES = 3;
    /** Squash and stretch amplitude (vertical stretch; the width compensates to keep the volume). */
    private static final float STRETCH_AMPLITUDE = 0.28F;
    /** Jelly wobble speed, in radians per tick. */
    private static final float STRETCH_SPEED = 1.3F;

    private static final Int2ObjectMap<Animation> ANIMATIONS = new Int2ObjectOpenHashMap<>();

    private SquishAnimations() {
    }

    public static void start(ClientWorld world, SquishAnimationPayload payload) {
        if (world == null) return;
        long now = world.getTime();
        // Forget animations of entities that were never rendered again (unloaded, out of sight...)
        ANIMATIONS.values().removeIf(animation -> animation.isOver(now));
        if (payload.duration() <= 0) {
            ANIMATIONS.remove(payload.entityId());
            return;
        }
        ANIMATIONS.put(payload.entityId(), new Animation(now, payload.duration(), payload.startScale(), payload.targetScale()));
    }

    public static void clear() {
        ANIMATIONS.clear();
    }

    /** Scale progress (0..1) of the stepped, overshooting growth. */
    private static float growth(float progress) {
        float stepped = progress * PULSES;
        int step = Math.min(PULSES - 1, (int) stepped);
        float local = MathHelper.clamp(stepped - step, 0, 1);
        // Each pulse: ease out with an overshoot (back easing), so it swells a little too much then settles
        float c1 = 1.9F, c3 = c1 + 1;
        float back = 1 + c3 * (float) Math.pow(local - 1, 3) + c1 * (float) Math.pow(local - 1, 2);
        return (step + back) / PULSES;
    }

    /** Applies the current animation frame of {@code entity}, if any, to its render state. */
    public static void apply(LivingEntity entity, LivingEntityRenderState state, float tickDelta) {
        if (ANIMATIONS.isEmpty()) return;
        Animation animation = ANIMATIONS.get(entity.getId());
        if (animation == null) return;

        float elapsed = (entity.getWorld().getTime() - animation.startTime) + tickDelta;
        if (elapsed < 0 || elapsed >= animation.duration) {
            ANIMATIONS.remove(entity.getId());
            return;
        }
        float progress = elapsed / animation.duration;

        float scale = MathHelper.lerp(growth(progress), animation.startScale, animation.targetScale);
        // The entity already has its final scale: render it relatively to that one
        if (animation.targetScale > 0) {
            state.baseScale *= Math.max(0.05F, scale / animation.targetScale);
        }
        // Jelly: squash and stretch, strongest right after each pulse, fading at the end
        float pulseLocal = progress * PULSES - (int) (progress * PULSES);
        float envelope = (1 - progress * 0.6F) * (0.35F + 0.65F * (1 - pulseLocal));
        float stretch = 1 + STRETCH_AMPLITUDE * envelope * MathHelper.sin(elapsed * STRETCH_SPEED);
        if (state instanceof SquishStretchState stretchState) {
            stretchState.steveparty$setStretch(stretch);
        }
    }

    /** Kamek's magic around the transforming creatures: a swirl of shapes and a sprinkle of sparkles. */
    public static void tick(ClientWorld world) {
        if (world == null || ANIMATIONS.isEmpty()) return;
        long now = world.getTime();
        Random random = world.random;
        for (Int2ObjectMap.Entry<Animation> entry : ANIMATIONS.int2ObjectEntrySet()) {
            Animation animation = entry.getValue();
            if (animation.isOver(now)) continue;
            Entity entity = world.getEntityById(entry.getIntKey());
            if (entity == null) continue;
            long age = now - animation.startTime;
            float progress = (float) age / animation.duration;
            double width = Math.max(0.4, entity.getWidth());
            double height = Math.max(0.4, entity.getHeight());
            // Two shape streams spiralling up around the creature
            for (int arm = 0; arm < 2; arm++) {
                double angle = age * 0.55 + arm * Math.PI;
                double radius = width * 0.7 + 0.3;
                double y = entity.getY() + height * ((age * 0.08 + arm * 0.5) % 1.0);
                world.addParticle(KamekShapeEffect.shape(0.9F, 0.8F, 10), entity.getX() + Math.cos(angle) * radius, y,
                        entity.getZ() + Math.sin(angle) * radius, -Math.sin(angle) * 0.04, 0.02, Math.cos(angle) * 0.04);
            }
            // Sparkles sprinkled from above, like Kamek sprinkling his spell on a creature
            if (random.nextFloat() < 0.8F) {
                world.addParticle(KamekShapeEffect.sparkle(1.0F, 0.95F, 14, KamekShapeEffect.RANDOM_COLOR),
                        entity.getX() + (random.nextDouble() - 0.5) * width * 1.6, entity.getY() + height + 0.4,
                        entity.getZ() + (random.nextDouble() - 0.5) * width * 1.6, 0, -0.06, 0);
            }
            // A burst on each growth pulse
            int pulseTick = Math.max(1, animation.duration / PULSES);
            if (age > 0 && age % pulseTick == 0 && progress < 1) {
                for (int i = 0; i < 8; i++) {
                    world.addParticle(KamekShapeEffect.shape(1.0F, 0.82F, 0), entity.getX(), entity.getY() + height / 2, entity.getZ(),
                            (random.nextDouble() - 0.5) * 0.35, (random.nextDouble() - 0.2) * 0.3, (random.nextDouble() - 0.5) * 0.35);
                }
            }
            playPulseSounds(world, entity, animation, age, pulseTick);
        }
    }

    /**
     * A jelly boing with bubbles as each growth pulse starts (higher and higher when the creature grows, lower and
     * lower when it shrinks), and a sparkle tail as it settles. Played by every client showing the animation, at the
     * creature: everyone around hears it.
     */
    private static void playPulseSounds(ClientWorld world, Entity entity, Animation animation, long age, int pulseTick) {
        if (age % pulseTick == 1 && age / pulseTick < PULSES) {
            int pulse = (int) (age / pulseTick);
            boolean grows = animation.targetScale >= animation.startScale;
            float pitch = grows ? 0.9F + 0.2F * pulse : 1.3F - 0.2F * pulse;
            world.playSound(entity.getX(), entity.getY(), entity.getZ(), ModSounds.TOKEN_SPELL_BOING, SoundCategory.NEUTRAL,
                    1.0F, pitch, false);
            world.playSound(entity.getX(), entity.getY(), entity.getZ(), ModSounds.TOKEN_SPELL_BUBBLE, SoundCategory.NEUTRAL,
                    1.0F, pitch, false);
        }
        if (age == animation.duration - 3) {
            world.playSound(entity.getX(), entity.getY(), entity.getZ(), ModSounds.TOKEN_SPELL_SPARKLE_TAIL, SoundCategory.NEUTRAL,
                    1.0F, 1.0F, false);
        }
    }

    private record Animation(long startTime, int duration, float startScale, float targetScale) {
        boolean isOver(long now) {
            return now < startTime || now - startTime >= duration;
        }
    }
}
