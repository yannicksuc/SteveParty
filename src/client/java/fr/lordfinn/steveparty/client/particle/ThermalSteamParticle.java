package fr.lordfinn.steveparty.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

/**
 * The Trichaudron's steam (the sprites of vanilla 26.2's geyser, 8 frames each, played over the particle's life): a puff
 * that swells, slows down, rises a little and fades. One class, three kinds:
 * <ul>
 *     <li>{@link #plume}: the blast's thick plume, big and quick, keeping most of its speed;</li>
 *     <li>{@link #base}: the vent's wisps at rest, small and slow, drifting up;</li>
 *     <li>{@link #poof}: the cloud at the nozzle and at the impact, big and short.</li>
 * </ul>
 */
public class ThermalSteamParticle extends SpriteBillboardParticle {
    private final SpriteProvider sprites;
    private final float startScale, endScale;

    protected ThermalSteamParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz,
                                   SpriteProvider sprites, float scale, float grow, int life, float drag, float rise) {
        super(world, x, y, z);
        this.sprites = sprites;
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
        this.velocityMultiplier = drag;
        this.gravityStrength = -rise;
        this.startScale = scale * (0.8f + random.nextFloat() * 0.4f);
        this.endScale = startScale * grow;
        this.scale = startScale;
        this.maxAge = life + random.nextInt(Math.max(1, life / 3));
        this.collidesWithWorld = false;
        float shade = 0.9f + random.nextFloat() * 0.1f;
        setColor(shade, shade, shade);
        setSpriteForAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (dead) return;
        setSpriteForAge(sprites);
        float t = (float) age / maxAge;
        scale = startScale + (endScale - startScale) * t;
        alpha = t < 0.7f ? 1 : 1 - (t - 0.7f) / 0.3f;
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Environment(EnvType.CLIENT)
    public static ParticleFactory<SimpleParticleType> plume(SpriteProvider sprites) {
        return (type, world, x, y, z, vx, vy, vz) ->
                new ThermalSteamParticle(world, x, y, z, vx, vy, vz, sprites, 0.9f, 2.2f, 18, 0.9f, 0.004f);
    }

    @Environment(EnvType.CLIENT)
    public static ParticleFactory<SimpleParticleType> base(SpriteProvider sprites) {
        return (type, world, x, y, z, vx, vy, vz) ->
                new ThermalSteamParticle(world, x, y, z, vx, vy, vz, sprites, 0.35f, 2.0f, 30, 0.96f, 0.002f);
    }

    @Environment(EnvType.CLIENT)
    public static ParticleFactory<SimpleParticleType> poof(SpriteProvider sprites) {
        return (type, world, x, y, z, vx, vy, vz) ->
                new ThermalSteamParticle(world, x, y, z, vx, vy, vz, sprites, 1.0f, 1.8f, 12, 0.8f, 0.003f);
    }
}
