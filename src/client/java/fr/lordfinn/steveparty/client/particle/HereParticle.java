package fr.lordfinn.steveparty.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

public class HereParticle extends SpriteBillboardParticle {
    protected HereParticle(ClientWorld level, double xCoord, double yCoord, double zCoord,
                           SpriteProvider spriteSet, double xd, double yd, double zd) {
        super(level, xCoord, yCoord, zCoord, xd, yd, zd);

        this.velocityMultiplier = 0F;
        this.x = xCoord;
        this.y = yCoord;
        this.z = zCoord;
        this.scale = 1F;
        this.maxAge = 150;
        this.setSpriteForAge(spriteSet);


        this.red = 1f;
        this.green = 1f;
        this.blue = 1f;
        this.originalY = yCoord;
        this.velocityX = 0;
        this.velocityY = 0;
        this.velocityZ = 0;
        this.gravityStrength = 0;
        tick();
    }

    public Rotator getRotator() {
        return Rotator.Y_AND_W_ONLY;
    }

    double originalY = 0;
    double amplitude = 0.3; // Change this to control how high/low the movement is
    double frequency = 0.002;  // Change this to control the speed of oscillation

    private void updatePosition() {
        this.y = originalY + amplitude * Math.cos(frequency * System.currentTimeMillis()); // `originalY` is the starting y position
    }

    @Override
    public void tick() {
        updatePosition();
        fade();
        super.tick();
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
            return new HereParticle(level, x, y, z, this.sprites, dx, dy, dz);
        }
    }
}