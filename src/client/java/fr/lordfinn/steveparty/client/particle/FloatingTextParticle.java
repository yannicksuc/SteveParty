package fr.lordfinn.steveparty.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

public class FloatingTextParticle extends SpriteBillboardParticle {
    public final String text;

    public FloatingTextParticle(ClientWorld world, double x, double y, double z, String text) {
        super(world, x, y, z);
        this.text = text;
        this.maxAge = 40; // 2 seconds
        this.velocityY = 0.02; // drift upward
    }

    @Override
    public void tick() {
        super.tick();
        this.alpha = 1.0f - ((float) this.age / this.maxAge); // fade out
    }

    @Override
    public void buildGeometry(VertexConsumer vc, Camera camera, float tickDelta) {
        // Do nothing — real rendering happens in our custom renderer
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.NO_RENDER; // don’t let MC try to render this
    }

    public double getParticleX() { return this.x; }
    public double getParticleY() { return this.y; }
    public double getParticleZ() { return this.z; }
    public float getAlpha() { return this.alpha; }
    public String getText() { return this.text; }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            return new FloatingTextParticle(world, x, y, z, "+1 Coin!");
        }
    }
}

