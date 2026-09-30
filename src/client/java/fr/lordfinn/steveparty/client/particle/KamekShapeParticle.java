package fr.lordfinn.steveparty.client.particle;

import fr.lordfinn.steveparty.particles.KamekShapeEffect;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;

/**
 * Kamek-style magic of the Tokenizer Wand, full bright:
 * <ul>
 *   <li>shape: an outlined circle, triangle or square that pops in, spins, then shrinks and fades;</li>
 *   <li>sparkle: a four-point sparkle that twinkles (pulses) upright and fades.</li>
 * </ul>
 */
public class KamekShapeParticle extends SpriteBillboardParticle {
    /** Sprites of kamek_shape.json: three shapes, then the sparkle. */
    private static final int SHAPES = 3, SPARKLE_SPRITE = 3, SPRITES = 4;

    private final boolean sparkle;
    private final float baseScale;
    private final float spin;
    private final float phase;

    protected KamekShapeParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz,
                                 KamekShapeEffect effect, SpriteProvider sprites) {
        super(world, x, y, z);
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
        this.sparkle = effect.style() == KamekShapeEffect.SPARKLE;
        setSprite(sprites.getSprite(sparkle ? SPARKLE_SPRITE : random.nextInt(SHAPES), SPRITES - 1));
        int color = effect.color() == KamekShapeEffect.RANDOM_COLOR
                ? KamekShapeEffect.COLORS[random.nextInt(KamekShapeEffect.COLORS.length)] : effect.color();
        setColor(((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F);
        this.baseScale = 0.1F * effect.scale() * (0.8F + random.nextFloat() * 0.4F);
        this.scale = sparkle ? baseScale : baseScale * 0.4F;
        this.maxAge = effect.life() > 0 ? effect.life() : 12 + random.nextInt(10);
        this.velocityMultiplier = MathHelper.clamp(effect.friction(), 0F, 1F);
        this.gravityStrength = 0F;
        this.collidesWithWorld = false;
        this.spin = sparkle ? 0 : (random.nextBoolean() ? 1 : -1) * (0.15F + random.nextFloat() * 0.2F);
        this.angle = this.prevAngle = sparkle ? 0 : random.nextFloat() * MathHelper.TAU;
        this.phase = random.nextFloat() * MathHelper.TAU;
    }

    @Override
    public void tick() {
        super.tick();
        float life = (float) age / maxAge;
        this.prevAngle = this.angle;
        this.angle += spin;
        float fade = life > 0.7F ? 1F - (life - 0.7F) / 0.3F : 1F;
        if (sparkle) {
            this.scale = baseScale * (0.55F + 0.45F * Math.abs(MathHelper.sin(age * 0.9F + phase))) * (0.4F + 0.6F * fade);
        } else {
            // Pops in, then shrinks and fades at the end
            float pop = Math.min(1F, age / 3F);
            this.scale = baseScale * (0.4F + 0.6F * pop) * (0.4F + 0.6F * fade);
        }
        this.alpha = Math.max(0F, fade);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getBrightness(float tint) {
        return 0xF000F0;
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<KamekShapeEffect> {
        private final SpriteProvider sprites;

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(KamekShapeEffect effect, ClientWorld world, double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            return new KamekShapeParticle(world, x, y, z, velocityX, velocityY, velocityZ, effect, sprites);
        }
    }
}
