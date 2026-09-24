package fr.lordfinn.steveparty.effect;

import fr.lordfinn.steveparty.StatusEffectExtension;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.payloads.custom.SquishAnimationPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import static fr.lordfinn.steveparty.effect.ModEffects.SQUISHED;

/**
 * Shrinks an entity into a token.
 * <ul>
 *     <li>Exact size: a token whose size was chosen with the Tokenizer Wand spell
 *     ({@link TokenizedEntityInterface#steveparty$getTokenSize()}, see {@link #squishToSize}) ends with its biggest
 *     dimension (height, or width if wider than tall) equal to that size, in blocks.</li>
 *     <li>Otherwise (e.g. {@code /effect}), the coarse amplifier is used: the height becomes {@code 0.1 * amplifier}
 *     blocks (amplifier 0 = scale 1).</li>
 * </ul>
 * The server applies the final scale once, when the effect is applied (hitbox consistent from then on, nothing
 * sent per tick). The shrink / wobble / spin animation is purely visual and played by the clients over the
 * effect duration, from a single {@link SquishAnimationPayload}.
 */
public class SquishEffect extends StatusEffect implements StatusEffectExtension {
    private static final double SCALE_EPSILON = 1.0E-3;

    public SquishEffect() {
        super(StatusEffectCategory.HARMFUL, 0xc150eb);
    }

    /**
     * Squishes {@code mob} so that its biggest dimension becomes exactly {@code sizeInBlocks} (stored on the token,
     * so the final tick and later re-squishes keep it). Can be replayed on a token that is already squished or still
     * squishing (resize): the animation restarts from its current scale. Server side.
     */
    public static void squishToSize(MobEntity mob, float sizeInBlocks, int duration) {
        ((TokenizedEntityInterface) mob).steveparty$setTokenSize(sizeInBlocks);
        int amplifier = amplifierForSize(sizeInBlocks);
        StatusEffectInstance instance = new StatusEffectInstance(SQUISHED, duration, amplifier);
        if (mob.hasStatusEffect(SQUISHED)) {
            // Still squishing (resized right away): replacing the effect does not call onApplied
            mob.setStatusEffect(instance, null);
            startSquish(mob, amplifier, duration);
        } else {
            mob.addStatusEffect(instance);
        }
    }

    /** Amplifier matching a size (drives the spin speed of the client animation). */
    public static int amplifierForSize(float sizeInBlocks) {
        return Math.max(1, Math.round(sizeInBlocks * 10));
    }

    /**
     * Scale attribute base value for which the biggest dimension of {@code entity} (standing) is {@code sizeInBlocks}.
     * Independent of the current scale and of when the hitbox was last recomputed.
     */
    public static double scaleForSize(LivingEntity entity, double sizeInBlocks) {
        float currentScale = entity.getScale();
        EntityDimensions dimensions = entity.getDimensions(EntityPose.STANDING);
        double unscaledSize = currentScale > 0 ? Math.max(dimensions.width(), dimensions.height()) / currentScale : 0;
        if (unscaledSize <= 0) {
            EntityAttributeInstance scaleAttribute = entity.getAttributeInstance(EntityAttributes.SCALE);
            return scaleAttribute == null ? 1 : scaleAttribute.getBaseValue();
        }
        return sizeInBlocks / unscaledSize;
    }

    /** Only the last tick does something: make sure the entity ends at its final scale. */
    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return duration == 1;
    }

    @Override
    public void onApplied(LivingEntity entity, int amplifier) {
        super.onApplied(entity, amplifier);
        entity.setGlowing(true);
        StatusEffectInstance instance = entity.getStatusEffect(SQUISHED);
        int duration = instance == null || instance.isInfinite() ? 0 : instance.getDuration();
        startSquish(entity, amplifier, duration);
    }

    /** Applies the final scale at once and lets the clients play the animation. Server side only. */
    private static void startSquish(LivingEntity entity, int amplifier, int duration) {
        if (!(entity.getWorld() instanceof ServerWorld)) return;
        EntityAttributeInstance scaleAttribute = entity.getAttributeInstance(EntityAttributes.SCALE);
        if (scaleAttribute == null) return;

        double startScale = scaleAttribute.getBaseValue();
        double targetScale = getTargetScale(entity, amplifier, scaleAttribute);
        scaleAttribute.setBaseValue(targetScale);

        if (duration > 0) {
            sendAnimation(entity, new SquishAnimationPayload(entity.getId(), (float) startScale, (float) targetScale, duration, amplifier));
        }
    }

    /**
     * Final scale: the token's chosen size if any, else {@code 0.1 * amplifier} blocks high (amplifier 0 keeps the
     * scale 1). Idempotent: the unscaled size is derived from the current dimensions and scale.
     */
    private static double getTargetScale(LivingEntity entity, int amplifier, EntityAttributeInstance scaleAttribute) {
        if (entity instanceof TokenizedEntityInterface token && token.steveparty$getTokenSize() > 0) {
            return scaleForSize(entity, token.steveparty$getTokenSize());
        }
        if (amplifier == 0) return 1;
        // Body height (a token's hitbox also includes its base), computed live from the current scale
        float currentScale = entity.getScale();
        double initialHeight = currentScale > 0 ? entity.getDimensions(EntityPose.STANDING).height() / currentScale : 0;
        if (initialHeight <= 0) return scaleAttribute.getBaseValue();
        double maxHeight = 0.1 * amplifier;
        return maxHeight / initialHeight;
    }

    private static void sendAnimation(LivingEntity entity, SquishAnimationPayload payload) {
        for (ServerPlayerEntity player : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(player, payload);
        }
        // A player does not track itself
        if (entity instanceof ServerPlayerEntity self) {
            ServerPlayNetworking.send(self, payload);
        }
    }

    @Override
    public boolean applyUpdateEffect(ServerWorld world, LivingEntity entity, int amplifier) {
        EntityAttributeInstance scaleAttribute = entity.getAttributeInstance(EntityAttributes.SCALE);
        if (scaleAttribute != null) {
            // Normally the scale is already final (set in onApplied): only fix it if it drifted, e.g. an entity
            // saved mid-squish by an older version of the mod
            double targetScale = getTargetScale(entity, amplifier, scaleAttribute);
            if (Math.abs(scaleAttribute.getBaseValue() - targetScale) > SCALE_EPSILON) {
                scaleAttribute.setBaseValue(targetScale);
            }
        }
        return true;
    }

    @Override
    public void steveparty$onRemoved(LivingEntity livingEntity) {
        livingEntity.setGlowing(false);
        livingEntity.getWorld().playSound(livingEntity, livingEntity.getBlockPos(),
                SoundEvent.of(Identifier.ofVanilla("entity.zombie_villager.converted")),
                SoundCategory.PLAYERS, 0.6F, 2.0F);
    }
}
