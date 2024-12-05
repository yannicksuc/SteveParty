package fr.lordfinn.steveparty.effect;

import fr.lordfinn.steveparty.StatusEffectExtension;
import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.server.world.ServerWorld;

public class SquishEffect extends StatusEffect implements StatusEffectExtension {

    public SquishEffect() {
        super(StatusEffectCategory.HARMFUL, 0xc150eb);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyUpdateEffect(ServerWorld world, LivingEntity entity, int amplifier) {
/*        if (entity instanceof LivingEntity livingEntity) {
            this.amplifier = amplifier;
        }*/
        return true;
    }

/*    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        // Apply the effect's behavior (e.g., change scale or other properties)
        if (!entity.world.isClient) {
            // Server-side logic (e.g., modify the entity's scale and rotation)
            float scale = 1.0f + (0.5f * amplifier); // Example scaling logic
            entity.setScale(scale);
            entity.setYaw(entity.getYaw() + 1.0f); // Example rotation logic
        } else {
            // Client-side logic (e.g., particle effects or visual changes)
            World world = entity.world;
            world.addParticle(ParticleTypes.HAPPY_VILLAGER, entity.getX(), entity.getY(), entity.getZ(), 0, 0, 0);
        }
    }*/

    private void setScale(LivingEntity entity, double value) {
        EntityAttributeInstance scaleAttribute = entity.getAttributeInstance(EntityAttributes.SCALE);

        if (scaleAttribute != null) {
            scaleAttribute.setBaseValue(value);
        }
    }


    public void onRemoved(LivingEntity livingEntityMixin) {
        Steveparty.LOGGER.info("SquishEffect removed");
    }
}

/*
package fr.lordfinn.steveparty.effect;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class SquishEffect {
    private final Entity entity;
    private final float duration;
    private final float startScale;
    private final float endScale;
    private final float rotationSpeed;

    private float elapsedTime = 0;
    private float

    public SquishEffect(Entity entity, float duration, float startScale, float endScale, float rotationSpeed) {
        this.entity = entity;
        this.duration = duration;
        this.startScale = startScale;
        this.endScale = endScale;
        this.rotationSpeed = rotationSpeed;
        // Adjust the scale based on the mob's height
        double height = entity.getHeight();
        double maxHeight = 1.5; // Set this to the maximum size you want to allow

        if (height > maxHeight) {
            double scaleFactor = maxHeight / height;
            EntityAttributeInstance scaleAttribute = mob.getAttributeInstance(EntityAttributes.SCALE);

            if (scaleAttribute != null) {
                scaleAttribute.setBaseValue(scaleFactor);
            }
        }
    }

    public void tick() {
        if (!(entity instanceof LivingEntity))
            return;
        if (elapsedTime < duration) {
            elapsedTime += 1; // Assuming this is called per tick. Adjust as necessary for frame rate.

            // Calculate the current scale based on an ease-in-out function
            float t = elapsedTime / duration;
            float ease = MathHelper.clamp(t * t * (3.0f - 2.0f * t), 0.0f, 1.0f); // Smooth ease-in-out
            float currentScale = MathHelper.lerp(ease, startScale, endScale);

            // Apply the scale
            EntityAttributeInstance scaleAttribute = ((LivingEntity)entity).getAttributeInstance(EntityAttributes.SCALE);
            if (scaleAttribute != null) {
                scaleAttribute.setBaseValue(currentScale);
            }

            // Apply rotation
            float rotationAngle = rotationSpeed * elapsedTime; // Rotate over time
            entity.setYaw(rotationAngle); // Adjust based on how you want the rotation to apply (e.g., pitch, roll)

            // Make sure to reset the state when done if necessary
            if (elapsedTime >= duration) {
                scaleAttribute.setBaseValue(1.0f);
                entity.setYaw(0); // Reset rotation to default
            }
        }
    }
}*/

