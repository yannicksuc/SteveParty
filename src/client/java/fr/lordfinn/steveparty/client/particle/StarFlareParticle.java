package fr.lordfinn.steveparty.client.particle;

import fr.lordfinn.steveparty.blocks.custom.StarFragmentsBlock;
import fr.lordfinn.steveparty.particles.StarFlareEffect;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Crescent slash of a star fragments block, in the spirit of vanilla's sweep attack particle: pixel-art crescent frames
 * (appear, full crescent, tail wears off, breaks into pixels, sparks) drawn on a 2x2-block quad lying in a plane through
 * the block's centre, so the crescent traces an arc of circle around the block starting from an exposed face.
 * Camera independent (double sided), full bright, translucent particle sheet (fine with Iris).
 * Sprites: 32x32 per frame, 16 px per block (the art sources); the arc starts on the
 * sprite's +u axis (mapped to the face normal) and turns towards +v (mapped to the roll tangent).
 */
public class StarFlareParticle extends SpriteBillboardParticle {
    private static final int FRAMES_PER_COLOUR = 6;
    private static final int SPRITE_COUNT = StarFragmentsBlock.COLOUR_COUNT * FRAMES_PER_COLOUR;
    private static final float SPRITE_ARC_RADIUS = 12.5f / 16f; // arc radius drawn in the sprite, in blocks at scale 1

    private final SpriteProvider sprites;
    private final int colour;
    private final double cx, cy, cz;
    private final float ux, uy, uz, vx, vy, vz;               // quad half axes (face normal, tangent), scaled

    protected StarFlareParticle(ClientWorld world, double x, double y, double z, StarFlareEffect effect,
                                SpriteProvider sprites) {
        super(world, x, y, z);
        this.sprites = sprites;
        this.colour = MathHelper.clamp(effect.colour(), 0, StarFragmentsBlock.COLOUR_COUNT - 1);
        this.cx = x;
        this.cy = y;
        this.cz = z;
        Direction face = Direction.byId(effect.face());
        Vec3d n = Vec3d.of(face.getVector());
        Vec3d a = Vec3d.of((face.getAxis() == Direction.Axis.Y ? Direction.EAST : Direction.UP).getVector());
        Vec3d b = n.crossProduct(a);
        float c = MathHelper.cos(effect.roll()), s = MathHelper.sin(effect.roll());
        Vec3d t = a.multiply(c).add(b.multiply(s));
        float half = effect.radius() / SPRITE_ARC_RADIUS;            // the sprite's arc lands on the wanted radius
        this.ux = (float) n.x * half;
        this.uy = (float) n.y * half;
        this.uz = (float) n.z * half;
        this.vx = (float) t.x * half;
        this.vy = (float) t.y * half;
        this.vz = (float) t.z * half;
        this.maxAge = Math.max(6, effect.life());
        this.collidesWithWorld = false;
        this.gravityStrength = 0f;
        this.velocityX = this.velocityY = this.velocityZ = 0;
        updateSprite();
        setBoundingBox(new Box(x - half, y - half, z - half, x + half, y + half, z + half));
    }

    private void updateSprite() {
        int frame = Math.min(FRAMES_PER_COLOUR - 1, age * FRAMES_PER_COLOUR / maxAge);
        setSprite(sprites.getSprite(colour * FRAMES_PER_COLOUR + frame, SPRITE_COUNT - 1));
    }

    @Override
    public void tick() {
        if (this.age++ >= this.maxAge) {
            markDead();
            return;
        }
        updateSprite();
    }

    @Override
    public void buildGeometry(VertexConsumer vc, Camera camera, float tickDelta) {
        Vec3d cam = camera.getPos();
        float ox = (float) (cx - cam.x), oy = (float) (cy - cam.y), oz = (float) (cz - cam.z);
        float u0 = getMinU(), u1 = getMaxU(), v0 = getMinV(), v1 = getMaxV();
        int light = getBrightness(tickDelta);
        // corners: texture u runs along the face normal, v along the tangent
        float ax = ox - ux - vx, ay = oy - uy - vy, az = oz - uz - vz;   // (u0, v0)
        float bx = ox + ux - vx, by = oy + uy - vy, bz = oz + uz - vz;   // (u1, v0)
        float cx2 = ox + ux + vx, cy2 = oy + uy + vy, cz2 = oz + uz + vz; // (u1, v1)
        float dx = ox - ux + vx, dy = oy - uy + vy, dz = oz - uz + vz;   // (u0, v1)
        vertex(vc, ax, ay, az, u0, v0, light);
        vertex(vc, bx, by, bz, u1, v0, light);
        vertex(vc, cx2, cy2, cz2, u1, v1, light);
        vertex(vc, dx, dy, dz, u0, v1, light);
        vertex(vc, dx, dy, dz, u0, v1, light);                          // back side
        vertex(vc, cx2, cy2, cz2, u1, v1, light);
        vertex(vc, bx, by, bz, u1, v0, light);
        vertex(vc, ax, ay, az, u0, v0, light);
    }

    private void vertex(VertexConsumer vc, float x, float y, float z, float u, float v, int light) {
        vc.vertex(x, y, z).texture(u, v).color(red, green, blue, alpha).light(light);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getBrightness(float tint) {
        return LightmapTextureManager.MAX_LIGHT_COORDINATE;
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<StarFlareEffect> {
        private final SpriteProvider sprites;

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(StarFlareEffect effect, ClientWorld world, double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            return new StarFlareParticle(world, x, y, z, effect, sprites);
        }
    }
}
