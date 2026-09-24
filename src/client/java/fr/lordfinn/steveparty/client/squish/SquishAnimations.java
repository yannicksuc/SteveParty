package fr.lordfinn.steveparty.client.squish;

import fr.lordfinn.steveparty.payloads.custom.SquishAnimationPayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Client-only squish (tokenization) animation. The server applies the final scale once and sends a single
 * {@link SquishAnimationPayload}; the rendered scale, spin and wobble are then interpolated here every frame with
 * the tick delta, so the animation is smooth whatever the TPS / FPS, and costs nothing on the network.
 * <p>
 * Render thread only. No allocation per frame: one {@link Animation} per squish, looked up by entity id.
 */
public final class SquishAnimations {
    /** Relative amplitude of the wobble (the envelope brings it back to 0 at the start and at the end). */
    private static final float OSCILLATION_AMPLITUDE = 0.3F;
    /** Wobble speed in radians per tick (about one wobble every 7 ticks, like the former server animation). */
    private static final float OSCILLATION_SPEED = 0.87F;
    /** Spin speed in degrees per tick, as the former server rotation: base + amplifier * multiplier. */
    private static final float BASE_ROTATION_SPEED = 2.0F;
    private static final float AMPLIFIER_ROTATION_MULTIPLIER = 0.5F;

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
        float rotationSpeed = BASE_ROTATION_SPEED + payload.amplifier() * AMPLIFIER_ROTATION_MULTIPLIER;
        // Whole turns only: the entity ends facing the direction it really has (no snap at the end)
        float turns = Math.max(1, Math.round(rotationSpeed * payload.duration() / 360.0F));
        ANIMATIONS.put(payload.entityId(), new Animation(now, payload.duration(),
                payload.startScale(), payload.targetScale(), turns * 360.0F));
    }

    public static void clear() {
        ANIMATIONS.clear();
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

        // Shrink (ease out) from the initial scale to the final one, with a wobble fading in and out
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
        float scale = MathHelper.lerp(eased, animation.startScale, animation.targetScale);
        float wobble = 1.0F + OSCILLATION_AMPLITUDE * MathHelper.sin(MathHelper.PI * progress)
                * MathHelper.sin(elapsed * OSCILLATION_SPEED);
        // The entity already has its final scale: render it relatively to that one
        if (animation.targetScale > 0) {
            state.baseScale *= scale * wobble / animation.targetScale;
        }

        // Spin (smoothstep: accelerates then slows down), head aligned with the body, neutral pitch
        float spinProgress = progress * progress * (3.0F - 2.0F * progress);
        state.bodyYaw += animation.totalSpin * spinProgress;
        state.pitch = 0.0F;
    }

    private record Animation(long startTime, int duration, float startScale, float targetScale, float totalSpin) {
        boolean isOver(long now) {
            return now < startTime || now - startTime >= duration;
        }
    }
}
