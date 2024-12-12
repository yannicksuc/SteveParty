package fr.lordfinn.steveparty.client.particle;

import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.random.Random;
import org.joml.Vector2d;
import org.joml.Vector3f;

public class EnchantedCircularParticle extends SpriteBillboardParticle {
    private final Vector3f rotateAroundPoint; // The center point of the circle
    private final float radius;              // Radius of the circle
    private float angle;                     // Current angle in radians
    private final float angularVelocity;     // Angular velocity in radians per tick
    private final static Random random = Random.create();

    protected EnchantedCircularParticle(ClientWorld world, double x, double y, double z, SpriteProvider spriteProvider,
                                        double distance, double color, double angularVelocity) {
        super(world, 0, y, 0, 0, 0, 0);

        this.scale = 0.1f; // Adjust particle size as needed
        this.setSprite(spriteProvider.getSprite(random));
        this.gravityStrength = 0;
        this.collidesWithWorld = false;
        this.setVelocity(0, 0, 0);

        // RotateAroundPoint is initialized relative to particle's starting position
        this.rotateAroundPoint = new Vector3f((float) x, (float) y, (float) z);

        float red = (((int)color >> 16) & 0xFF) / 255.0f;
        float green = (((int)color >> 8) & 0xFF) / 255.0f;
        float blue = ((int)color & 0xFF) / 255.0f;
        this.setColor(red,green,blue);
        this.radius = (float) distance;
        this.angle = (float) (Math.random() * 2 * Math.PI);
        this.angularVelocity = (float) angularVelocity; // Adjust for speed (e.g., 0.1 radians/tick)
        this.maxAge = 50; // Particle lifetime in ticks
    }

    @Override
    public void tick() {
        if (age >= maxAge) {
            this.markDead(); // Remove particle when lifetime ends
            return;
        }

        // Update position using circular movement
        this.angle += angularVelocity;

        // Maintain constant radius for circular motion
        double offsetX = Math.cos(angle) * radius;
        double offsetZ = Math.sin(angle) * radius;

        // Update position relative to the center (rotateAroundPoint)
        this.x = rotateAroundPoint.x + offsetX;
        this.z = rotateAroundPoint.z + offsetZ;

        this.alpha = 1.0f - (float) age / maxAge; // Fade out over time
        super.tick();
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            return new EnchantedCircularParticle(world, x, y, z, spriteProvider, velocityX, velocityY, velocityZ);
        }
    }
}
