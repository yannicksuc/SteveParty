package fr.lordfinn.steveparty.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.AnimatedParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

/**
 * End rod sparkle that keeps its speed (vanilla end rod particles slow down right away) and fades quickly, gone as soon
 * as it hits a block: the shooting star trail of the dice forge.
 */
public class ForgeBeamParticle extends AnimatedParticle {
    /** End rod sparkle color. */
    private static final int TARGET_COLOR = 0xF2DEC9;

    protected ForgeBeamParticle(ClientWorld world, double x, double y, double z,
                                double velocityX, double velocityY, double velocityZ, SpriteProvider sprites) {
        super(world, x, y, z, sprites, 0f);
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        this.velocityMultiplier = 1f; // keeps its speed
        this.scale *= 0.55f + random.nextFloat() * 0.4f;
        this.maxAge = 10 + random.nextInt(12);
        setTargetColor(TARGET_COLOR);
        setSpriteForAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (onGround) markDead(); // reached the forge
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider sprites;

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            return new ForgeBeamParticle(world, x, y, z, velocityX, velocityY, velocityZ, sprites);
        }
    }
}
