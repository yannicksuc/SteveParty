package fr.lordfinn.steveparty.client.particle;

import fr.lordfinn.steveparty.particles.StarFlareEffect;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Solar eruption of a star fragments block: a round flame blob bursts out of a face, follows an arc of circle around the
 * block's centre (a prominence hugging the star's limb), stretches along its path while drifting outwards, breaks up
 * into wisps and fades. Full bright; the quad is stretched along the velocity and turned towards the camera.
 * Sprites: 8 frames per colour (the art sources), motion towards +u.
 */
public class StarFlareParticle extends SpriteBillboardParticle {
    private static final int FRAMES_PER_COLOUR = 8;
    private static final int SPRITE_COUNT = 6 * FRAMES_PER_COLOUR;
    private static final float SIZE = 0.5f;         // blob diameter in blocks
    private static final float MAX_STRETCH = 1.1f;  // extra length at the most stretched
    private static final float OUTWARD = 0.35f;     // radius gained over the life (the flame leaves the star)

    private final SpriteProvider sprites;
    private final int colour;
    private final double cx, cy, cz;
    private final Vec3d normal, tangent;
    private final float radius, sweep;

    protected StarFlareParticle(ClientWorld world, double x, double y, double z, StarFlareEffect effect,
                                SpriteProvider sprites) {
        super(world, x, y, z);
        this.sprites = sprites;
        this.colour = MathHelper.clamp(effect.colour(), 0, 5);
        this.cx = x;
        this.cy = y;
        this.cz = z;
        this.normal = Vec3d.of(Direction.byId(effect.face()).getVector());
        this.tangent = Vec3d.of(Direction.byId(effect.tangent()).getVector());
        this.radius = effect.radius();
        this.sweep = effect.sweep();
        this.maxAge = Math.max(4, effect.life());
        this.collidesWithWorld = false;
        this.gravityStrength = 0f;
        this.velocityX = this.velocityY = this.velocityZ = 0;
        Vec3d p = arc(0f);
        setPos(p.x, p.y, p.z);
        this.prevPosX = p.x;
        this.prevPosY = p.y;
        this.prevPosZ = p.z;
        updateSprite();
    }

    /** Life fraction 0..1 -> position on the arc. */
    private Vec3d arc(float s) {
        float theta = sweep * (1f - (1f - s) * (1f - s));          // bursts out fast, slows down along the limb
        float r = radius + OUTWARD * s * s;
        double c = MathHelper.cos(theta) * r, n = MathHelper.sin(theta) * r;
        return new Vec3d(cx + normal.x * c + tangent.x * n, cy + normal.y * c + tangent.y * n,
                cz + normal.z * c + tangent.z * n);
    }

    private void updateSprite() {
        int frame = Math.min(FRAMES_PER_COLOUR - 1, age * FRAMES_PER_COLOUR / maxAge);
        setSprite(sprites.getSprite(colour * FRAMES_PER_COLOUR + frame, SPRITE_COUNT - 1));
    }

    @Override
    public void tick() {
        this.prevPosX = this.x;
        this.prevPosY = this.y;
        this.prevPosZ = this.z;
        if (this.age++ >= this.maxAge) {
            markDead();
            return;
        }
        Vec3d p = arc(Math.min(1f, (float) age / maxAge));
        setPos(p.x, p.y, p.z);
        float s = (float) age / maxAge;
        this.alpha = s < 0.7f ? 1f : Math.max(0f, 1f - (s - 0.7f) / 0.3f);
        updateSprite();
    }

    @Override
    public void buildGeometry(VertexConsumer vc, Camera camera, float tickDelta) {
        float s = MathHelper.clamp((age + tickDelta) / maxAge, 0f, 1f);
        Vec3d pos = arc(s);
        Vec3d dir = arc(Math.min(1f, s + 0.02f)).subtract(arc(Math.max(0f, s - 0.02f)));
        if (dir.lengthSquared() < 1e-8) dir = tangent;
        dir = dir.normalize();
        Vec3d cam = camera.getPos();
        Vec3d toCam = cam.subtract(pos);
        Vec3d side = dir.crossProduct(toCam);
        if (side.lengthSquared() < 1e-8) side = normal.crossProduct(dir);
        side = side.normalize();

        float stretch = MathHelper.clamp((s - 0.2f) / 0.5f, 0f, 1f);  // round at first, then drawn out
        float half = SIZE * (0.6f + 0.4f * Math.min(1f, s * 4f)) / 2f;
        float halfLen = half * (1f + MAX_STRETCH * stretch);
        float halfWid = half * (1f - 0.25f * stretch);

        float px = (float) (pos.x - cam.x), py = (float) (pos.y - cam.y), pz = (float) (pos.z - cam.z);
        Vector3f d = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z).mul(halfLen);
        Vector3f w = new Vector3f((float) side.x, (float) side.y, (float) side.z).mul(halfWid);
        float u0 = getMinU(), u1 = getMaxU(), v0 = getMinV(), v1 = getMaxV();
        int light = getBrightness(tickDelta);
        // tail (-d) at u0, head (+d) at u1; both windings so culling never hides it
        vertex(vc, px - d.x - w.x, py - d.y - w.y, pz - d.z - w.z, u0, v1, light);
        vertex(vc, px - d.x + w.x, py - d.y + w.y, pz - d.z + w.z, u0, v0, light);
        vertex(vc, px + d.x + w.x, py + d.y + w.y, pz + d.z + w.z, u1, v0, light);
        vertex(vc, px + d.x - w.x, py + d.y - w.y, pz + d.z - w.z, u1, v1, light);
        vertex(vc, px + d.x - w.x, py + d.y - w.y, pz + d.z - w.z, u1, v1, light);
        vertex(vc, px + d.x + w.x, py + d.y + w.y, pz + d.z + w.z, u1, v0, light);
        vertex(vc, px - d.x + w.x, py - d.y + w.y, pz - d.z + w.z, u0, v0, light);
        vertex(vc, px - d.x - w.x, py - d.y - w.y, pz - d.z - w.z, u0, v1, light);
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
        return 0xF000F0;
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
