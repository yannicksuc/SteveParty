package fr.lordfinn.steveparty.client.particle;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.particles.ParticleUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.awt.*;

import static fr.lordfinn.steveparty.particles.ParticleUtils.getFadingAlpha;

public class ArrowParticle extends SpriteBillboardParticle {
    private Vector3f direction = new Vector3f(0);
    private Vector3f distance = new Vector3f(0);
    private double speed; // Speed in blocks per second

    protected ArrowParticle(ClientWorld level, double x, double y, double z,
                            SpriteProvider spriteSet, double velocityX, double velocityY, double velocityZ) {
        super(level, x, y, z,0,0,0);
        this.scale = 1F;
        this.speed = 2.5f;
        this.setSpriteForAge(spriteSet);
        this.gravityStrength = 0;
        this.collidesWithWorld = false;
        this.setVelocity(0, 0, 0);

        float[][] decodedVelocity = ParticleUtils.decodeVelocity(new Vector3d(velocityX, velocityY, velocityZ));
        this.red = decodedVelocity[0][0];
        this.green = decodedVelocity[0][1];
        this.blue = decodedVelocity[0][2];
        this.distance.set(decodedVelocity[1][0], decodedVelocity[1][1], decodedVelocity[1][2]);
        this.maxAge = (int) calculateMaxAge(this.distance.x, this.distance.y, this.distance.z, speed);
        this.direction = this.distance.normalize();
        tick();
    }

    @Override
    public int getBrightness(float tint) {
        return 15728880; // Full brightness, equivalent to sunlight
    }

    public ArrowParticle(ClientWorld level, double x, double y, double z, SpriteProvider sprites, double distanceX, double distanceY, double distanceZ, Color color) {
        this(level, x, y, z, sprites, distanceX, distanceY, distanceZ);
        this.red = color.getRed() / 255f;
        this.green = color.getGreen() / 255f;
        this.blue = color.getBlue() / 255f;
    }


    private double calculateMaxAge(double distanceX, double distanceY, double distanceZ, double speed) {
        // Calculate the distance using the 3D Pythagorean theorem
        double distance = Math.sqrt(distanceX * distanceX + distanceY * distanceY + distanceZ * distanceZ);
        // Calculate maxAge in ticks
        return (distance / speed) * 20;
    }

    @Override
    public void tick() {
        if (age < maxAge) {
            // Move the particle based on the direction and speed
            double movementStep = speed / 20.0;
            this.x += direction.x * movementStep;
            this.y += direction.y * movementStep;
            this.z += direction.z * movementStep;
        }
        this.alpha = getFadingAlpha(age, maxAge, 0.2f, 0.2f);
        super.tick();
    }

    public Rotator getRotator() {
        return (quaternion, camera, tickDelta) -> {
            quaternion.set(0, 0, 0, 1)
                    .rotateLocalX(calculateXAngle(this.direction.y))
                    .rotateLocalY(calculateYAngle(-this.direction.x, -this.direction.z));
        };
    }

    public static float calculateYAngle(double x, double z) {
        double angle = Math.PI - Math.atan2(z, x) - 90 * Math.PI / 180;
        if (angle < 0) {
            angle += 2 * Math.PI;
        }
        return (float) angle;
    }

    public static float calculateXAngle(double y) {
        double angle = 90 * y - 90;
        return (float) Math.toRadians(angle);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider sprites;


        public Factory(SpriteProvider spriteSet) {
            this.sprites = spriteSet;
        }

        public Particle createParticle(SimpleParticleType particleType, ClientWorld level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new ArrowParticle(level, x, y, z, this.sprites, dx, dy, dz);
        }

        public Particle createParticle(SimpleParticleType particleType, ClientWorld level, double x, double y, double z,
                                       double dx, double dy, double dz, Color color) {
            return new ArrowParticle(level, x, y, z, this.sprites, dx, dy, dz, color);
        }
    }
}