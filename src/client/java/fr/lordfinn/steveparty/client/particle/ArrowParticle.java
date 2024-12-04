package fr.lordfinn.steveparty.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class ArrowParticle extends SpriteBillboardParticle {
    private Vector3f direction = new Vector3f(0);
    protected ArrowParticle(ClientWorld level, double xCoord, double yCoord, double zCoord,
                            SpriteProvider spriteSet, double directionX, double directionY, double directionZ) {
        super(level, xCoord, yCoord, zCoord);
        this.x = xCoord;
        this.y = yCoord;
        this.z = zCoord;
        this.scale = 1F;
        this.maxAge = 5000;
        this.setSpriteForAge(spriteSet);
        this.red = 1f;
        this.green = 1f;
        this.blue = 1f;
        this.originalY = yCoord;
        this.gravityStrength = 0;
        this.direction.set(1,0.4,1).normalize();
//        this.direction.set(directionX, directionY, directionZ).normalize();
        this.setVelocity(this.direction.x, this.direction.y, this.direction.z);
        this.velocityMultiplier = 0.2f;
        tick();
    }

    public Rotator getRotator() {
        return (quaternion, camera, tickDelta) -> {
            quaternion.set(0, 0, 0, 1)
                    .rotateLocalX(calculateXAngle(this.direction.y))
                    .rotateLocalY(calculateYAngle(-this.direction.x, -this.direction.z));
        };
    }

    public static float calculateYAngle(double x, double z) {
        double angle = Math.atan2(z, x);
        if (angle < 0) {
            angle += 2 * Math.PI;
        }
        return (float) angle;
    }

    public static float calculateXAngle(double y) {
        double angle = 90 * y - 90;
        return (float) Math.toRadians(angle);
    }

    double originalY = 0;
    double amplitude = 0.3; // Change this to control how high/low the movement is

    @Override
    public void tick() {
       // updatePosition();
       // fade();
        super.tick();
    }

    private void fade() {
        this.alpha = (-(1/(float)maxAge) * age + 1);
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