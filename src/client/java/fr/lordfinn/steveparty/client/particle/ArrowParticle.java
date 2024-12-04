package fr.lordfinn.steveparty.client.particle;

import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class ArrowParticle extends SpriteBillboardParticle {
    private Vector3f direction = new Vector3f(0);
    private Vector3f distance = new Vector3f(0);
    private double speed; // Speed in blocks per second

    protected ArrowParticle(ClientWorld level, double xCoord, double yCoord, double zCoord,
                            SpriteProvider spriteSet, double distanceX, double distanceY, double distanceZ) {
        super(level, xCoord, yCoord, zCoord);
        this.x = xCoord;
        this.y = yCoord;
        this.z = zCoord;
        this.scale = 1F;
        this.speed = 2.5f;
        this.maxAge = (int) calculateMaxAge(distanceX, distanceY, distanceZ, speed);
        this.setSpriteForAge(spriteSet);
        this.red = 1f;
        this.green = 1f;
        this.blue = 1f;
        this.gravityStrength = 0;
        //this.direction.set(1,0.4,1).normalize();
        this.distance.set(distanceX, distanceY, distanceZ);
        this.direction = this.distance.normalize();
        Steveparty.LOGGER.info("Direction : " + direction.x + " " + direction.y + " " + direction.z);
        this.setVelocity(0, 0, 0);
        this.collidesWithWorld = false;
        tick();
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
        fade();
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

    private void fade() {
        float ageFraction = (float) age / (float) maxAge;

        if (ageFraction < 0.2) {
            // Fade-in effect during the first 20%
            this.alpha = ageFraction / 0.2f;
        } else if (ageFraction > 0.8) {
            // Fade-out effect during the last 20%
            this.alpha = 1 - ((ageFraction - 0.8f) / 0.2f);
        } else {
            // No change in alpha during the middle 60%
            this.alpha = 1.0f;
        }
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
    }
}