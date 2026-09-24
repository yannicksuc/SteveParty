package fr.lordfinn.steveparty.client.particle;

import fr.lordfinn.steveparty.particles.MulaSparkleEffect;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;

/**
 * The Mula's little lights, full bright and tinted:
 * <ul>
 *   <li>twinkle: a four-point star that twinkles (pulses) and fades, floating where it was put (orbits, rings,
 *   comet tails);</li>
 *   <li>star bit: a little faceted star that is thrown, spins and falls like confetti (showers, bursts);</li>
 *   <li>z: rises slowly, swaying and growing, and fades (sleeping Mula).</li>
 * </ul>
 */
public class MulaSparkleParticle extends SpriteBillboardParticle {
    private final int style;
    private final float baseScale;
    private final float phase;
    private final float spin;

    protected MulaSparkleParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz,
                                  MulaSparkleEffect effect, SpriteProvider sprites) {
        super(world, x, y, z);
        this.style = effect.style();
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
        setSprite(sprites.getSprite(MathHelper.clamp(style, 0, 2), 2));
        int color = effect.color();
        setColor(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f);
        this.phase = random.nextFloat() * MathHelper.TAU;
        this.spin = (random.nextFloat() - 0.5f) * 0.5f;
        // only star bits spin; twinkles and z's stay upright (a turned twinkle reads as an "x")
        this.angle = this.prevAngle = style == MulaSparkleEffect.STAR_BIT ? random.nextFloat() * MathHelper.TAU : 0f;
        this.collidesWithWorld = false;
        switch (style) {
            case MulaSparkleEffect.STAR_BIT -> {
                this.baseScale = 0.11f * effect.scale();
                this.maxAge = 28 + random.nextInt(18);
                this.gravityStrength = 0.45f;
                this.velocityMultiplier = 0.95f;
                this.collidesWithWorld = true;
            }
            case MulaSparkleEffect.Z -> {
                this.baseScale = 0.12f * effect.scale();
                this.maxAge = 44 + random.nextInt(10);
                this.gravityStrength = 0f;
                this.velocityMultiplier = 0.985f;
            }
            default -> {
                this.baseScale = 0.09f * effect.scale();
                this.maxAge = 10 + random.nextInt(10);
                this.gravityStrength = 0f;
                this.velocityMultiplier = 0.9f;
            }
        }
        this.scale = baseScale;
    }

    @Override
    public void tick() {
        super.tick();
        float life = (float) age / maxAge;
        this.alpha = life < 0.6f ? 1f : Math.max(0f, 1f - (life - 0.6f) / 0.4f);
        switch (style) {
            case MulaSparkleEffect.STAR_BIT -> {
                this.prevAngle = this.angle;
                this.angle += onGround ? 0 : spin;
                this.scale = baseScale * (0.9f + 0.15f * MathHelper.sin(age * 0.7f + phase));
            }
            case MulaSparkleEffect.Z -> {
                this.velocityX += 0.0025 * MathHelper.cos(age * 0.22f + phase);
                this.scale = baseScale * (0.55f + 0.6f * life);
            }
            default -> this.scale = baseScale * (0.55f + 0.55f * Math.abs(MathHelper.sin(age * 0.8f + phase)))
                    * (1f - 0.4f * life);
        }
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
    public static class Factory implements ParticleFactory<MulaSparkleEffect> {
        private final SpriteProvider sprites;

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(MulaSparkleEffect effect, ClientWorld world, double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            return new MulaSparkleParticle(world, x, y, z, velocityX, velocityY, velocityZ, effect, sprites);
        }
    }
}
