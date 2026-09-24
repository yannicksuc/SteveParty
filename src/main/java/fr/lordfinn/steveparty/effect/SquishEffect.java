package fr.lordfinn.steveparty.effect;

import fr.lordfinn.steveparty.StatusEffectExtension;
import fr.lordfinn.steveparty.payloads.custom.SquishAnimationPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import static fr.lordfinn.steveparty.effect.ModEffects.SQUISHED;

/**
 * Shrinks an entity into a token: its height becomes {@code 0.1 * amplifier} blocks (amplifier 0 = scale 1).
 * <p>
 * The server applies the final scale once, when the effect is applied (hitbox consistent from then on, nothing
 * sent per tick). The shrink / wobble / spin animation is purely visual and played by the clients over the
 * effect duration, from a single {@link SquishAnimationPayload}.
 */
public class SquishEffect extends StatusEffect implements StatusEffectExtension {
    private static final double BASE_MAX_HEIGHT = 0; // Base max height for scaling
    private static final double SCALE_EPSILON = 1.0E-3;

    public SquishEffect() {
        super(StatusEffectCategory.HARMFUL, 0xc150eb);
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
        if (!(entity.getWorld() instanceof ServerWorld)) return;
        EntityAttributeInstance scaleAttribute = entity.getAttributeInstance(EntityAttributes.SCALE);
        if (scaleAttribute == null) return;

        double startScale = scaleAttribute.getBaseValue();
        double targetScale = getTargetScale(entity, amplifier, scaleAttribute);
        scaleAttribute.setBaseValue(targetScale);

        StatusEffectInstance instance = entity.getStatusEffect(SQUISHED);
        int duration = instance == null || instance.isInfinite() ? 0 : instance.getDuration();
        if (duration > 0) {
            sendAnimation(entity, new SquishAnimationPayload(entity.getId(), (float) startScale, (float) targetScale, duration, amplifier));
        }
    }

    /**
     * Final scale so that the entity is {@code 0.1 * amplifier} blocks high (amplifier 0 keeps the scale 1).
     * Idempotent: the unscaled height is derived from the current height and scale base value.
     */
    private static double getTargetScale(LivingEntity entity, int amplifier, EntityAttributeInstance scaleAttribute) {
        if (amplifier == 0) return 1;
        double baseValue = scaleAttribute.getBaseValue();
        double initialHeight = baseValue > 0 ? entity.getHeight() / baseValue : 0;
        if (initialHeight <= 0) return scaleAttribute.getBaseValue();
        double maxHeight = BASE_MAX_HEIGHT + (0.1 * amplifier);
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
