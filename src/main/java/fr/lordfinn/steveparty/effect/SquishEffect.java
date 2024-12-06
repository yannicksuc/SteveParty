package fr.lordfinn.steveparty.effect;

import fr.lordfinn.steveparty.StatusEffectExtension;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;

import static fr.lordfinn.steveparty.effect.ModEffects.SQUISHED;

public class SquishEffect extends StatusEffect implements StatusEffectExtension {
    private static final double BASE_MAX_HEIGHT = 0; // Base max height for scaling
    private static final double OSCILLATION_AMPLITUDE = 0.5; // Amplitude of the oscillation
    private static final double OSCILLATION_FREQUENCY = 0.1; // Frequency of the oscillation
    private static final double BASE_ROTATION_SPEED = 2; // Base rotation speed
    private static final double AMPLIFIER_ROTATION_MULTIPLIER = 0.5; // Frequency of the oscillation
    private static final Map <LivingEntity, Double> maxScaleFactors = new HashMap<>();
    private static final Map <LivingEntity, Long> maxDurations = new HashMap<>();
    private static final Map <LivingEntity, Long> startTimestamps = new HashMap<>();


    public SquishEffect() {
        super(StatusEffectCategory.HARMFUL, 0xc150eb);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true;
    }

    @Override
    public void onApplied(LivingEntity entity, int amplifier) {
        super.onApplied(entity, amplifier);
        maxDurations.remove(entity);
        startTimestamps.remove(entity);
        maxScaleFactors.remove(entity);
        entity.setGlowing(true);
    }

    private double resetScaleFactor(LivingEntity entity, int amplifier) {
        double height = entity.getHeight();
        EntityAttributeInstance scaleAttribute = entity.getAttributeInstance(EntityAttributes.SCALE);
        double initialHeight = 0;
        if (scaleAttribute != null) {
            initialHeight = height / scaleAttribute.getBaseValue();
        }
        double maxHeight = BASE_MAX_HEIGHT + (0.1 * amplifier);
        double scaleFactor = maxHeight / initialHeight;
        maxScaleFactors.put(entity, amplifier == 0 ? 1 : scaleFactor);
        maxDurations.put(entity, getCurrentDuration(entity));
        startTimestamps.put(entity, System.currentTimeMillis());
        return scaleFactor;
    }


    private Long getMaxDuration(LivingEntity entity) {
        return maxDurations.get(entity);
    }

    private long getStartTimestamp(LivingEntity entity) {
        return startTimestamps.get(entity);
    }

    private long getCurrentDuration(LivingEntity entity) {
        StatusEffectInstance statusEffectInstance = getStatusEffect(entity);
        if (statusEffectInstance != null) {
            return statusEffectInstance.getDuration();
        }
        return -1L;
    }

    private StatusEffectInstance getStatusEffect(LivingEntity entity) {
        return entity.getStatusEffect(SQUISHED);
    }

    @Override
    public boolean applyUpdateEffect(ServerWorld world, LivingEntity entity, int amplifier) {
        double scaleFactor;
        if (maxScaleFactors.containsKey(entity))
            scaleFactor = maxScaleFactors.get(entity);
        else
            scaleFactor = resetScaleFactor(entity, amplifier);
        long duration = getCurrentDuration(entity);

        // Apply a smooth scaling with oscillation over time
        applyScalingWithOscillation(entity, scaleFactor, duration);
        applyRotation(entity, amplifier);

        return true;
    }

    private void applyRotation(LivingEntity entity, int amplifier) {
        float rotationSpeed = (float) (BASE_ROTATION_SPEED + (amplifier * AMPLIFIER_ROTATION_MULTIPLIER)); // Amplifier increases speed slightly

        // Get the current yaw (horizontal rotation) of the entity
        float currentYaw = entity.getYaw();

        // Apply the rotation increment
        float newYaw = (currentYaw + rotationSpeed) % 360; // Ensure the rotation stays within 0-360 degrees

        // Update the entity's yaw and head yaw
        entity.setYaw(newYaw);
        entity.setHeadYaw(newYaw);

        // If applicable, set pitch to a consistent value
        entity.setPitch(0); // Keep pitch neutral, or modify as needed

        // Ensure the rotation is synced with the client
        if (entity instanceof ServerPlayerEntity) {
            ((ServerPlayerEntity) entity).networkHandler.sendPacket(new EntityS2CPacket.Rotate(entity.getId(), (byte) (newYaw * 256 / 360), (byte) (entity.getPitch() * 256 / 360), entity.isOnGround()));
        }
    }

    private void applyScalingWithOscillation(LivingEntity entity, double targetScale, long duration) {
        EntityAttributeInstance scaleAttribute = entity.getAttributeInstance(EntityAttributes.SCALE);
        if (scaleAttribute == null) return;
        long startTimestamp = getStartTimestamp(entity);
        long endTimestamp = startTimestamp + getMaxDuration(entity) * 1000L / 20L;
        long currentTime = System.currentTimeMillis();
        double currentScale = scaleAttribute.getBaseValue();
        //Steveparty.LOGGER.info("MAX Duration: " + getMaxDuration(entity));
        //Steveparty.LOGGER.info("Current scale: " + currentScale + ", Target scale: " + targetScale);
        //Steveparty.LOGGER.info("Current time: " + currentTime + ", Start timestamp: " + startTimestamp + ", End timestamp: " + endTimestamp);
        //Steveparty.LOGGER.info("Duration: " + duration);


        float progress = (float) (currentTime - startTimestamp) / (endTimestamp - startTimestamp);
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        //Steveparty.LOGGER.info("Progress: " + progress);

        // Calculate the oscillating scale factor using a sine function

        double oscillation = 1 + OSCILLATION_AMPLITUDE * Math.sin(OSCILLATION_FREQUENCY * (getMaxDuration(entity) / Math.PI) * duration * (Math.PI * 2));
        //Steveparty.LOGGER.info("Oscillation: " + oscillation);

        // Calculate the final scale with interpolation and oscillation
        double easedScale = MathHelper.lerp(
                MathHelper.clamp(progress, 0.0, 1.0), // Time clamped to ensure interpolation is smooth
                currentScale,
                targetScale * oscillation
        );

        scaleAttribute.setBaseValue(easedScale);
    }

    @Override
    public void steveparty$onRemoved(LivingEntity livingEntity) {
        EntityAttributeInstance scaleAttribute = livingEntity.getAttributeInstance(EntityAttributes.SCALE);
        if (scaleAttribute != null && maxScaleFactors.containsKey(livingEntity)) {
            double maxHeight = maxScaleFactors.remove(livingEntity);
            scaleAttribute.setBaseValue(maxHeight);
        }
        livingEntity.setGlowing(false);
        livingEntity.getWorld().playSound(livingEntity, livingEntity.getBlockPos(),
                SoundEvent.of(Identifier.ofVanilla("entity.zombie_villager.converted")),
                SoundCategory.PLAYERS, 0.6F, 2.0F);
    }
}
