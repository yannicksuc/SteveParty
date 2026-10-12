package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/**
 * What the soap bubbles of the mod are drawn with (the Spawn Bubble, the Bubbler and its bubbles): the iridescent film
 * (textures/block/spawn_bubble_iridescent.png, an animated texture, its face pixels 3 to 12), a cube of it
 * ({@link #cube}), and the trail of tiny bubbles rising from a plate ({@link #trail}).
 */
public final class BubbleFilm {
    /** The soap film, and the tiny bubbles' (spawn_bubble_small.png, its face pixels 6 to 8). */
    public static final SpriteIdentifier FILM = new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Steveparty.id("block/spawn_bubble_iridescent"));
    public static final SpriteIdentifier TINY = new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Steveparty.id("block/spawn_bubble_small"));
    /** Where the film's face is in its texture (pixels). */
    public static final float FACE_FROM = 3, FACE_TO = 13;
    /**
     * Whether a big bubble's film has more pixels ({@link #tier}): off, every bubble has the 10 x 10 film, its pixels
     * as big as the bubble (like a ghast).
     */
    public static final boolean TIERS = true;
    /**
     * The film with more pixels, for the Bubbler's bubbles as they grow (spawn_bubble_iridescent_N.png, N pixels a
     * face): 10, 20, 40 and 64. Same design and cycle, an L-shaped highlight.
     */
    private static final Tier[] FILM_TIERS = {
            new Tier(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Steveparty.id("block/spawn_bubble_iridescent_10")), 10, FACE_FROM, FACE_TO),
            new Tier(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Steveparty.id("block/spawn_bubble_iridescent_20")), 20, 3, 13),
            new Tier(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Steveparty.id("block/spawn_bubble_iridescent_40")), 40, 3, 13),
            new Tier(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Steveparty.id("block/spawn_bubble_iridescent_64")), 64, 0, 16)};

    /** A film's texture, its pixels a face, and where the face is in it (sixteenths of it, as for a 16 x 16 one). */
    public record Tier(SpriteIdentifier sprite, int pixels, float from, float to) {
    }
    /** Half the side of a tiny cube of a trail (blocks), and where its face is in its texture (pixels). */
    public static final float TINY_HALF = 1.5f / 16, TINY_FROM = 6, TINY_TO = 9;
    /** How fast a tiny bubble rises on average (blocks per tick). */
    public static final float TRAIL_SPEED = 0.022f;
    /** The tints of the tiny bubbles, from blue to pink (RGB, 0..1). */
    private static final float[] TINT_BLUE = {0.70f, 0.85f, 1f}, TINT_PINK = {1f, 0.75f, 0.92f};
    /** The faces of a cube: their corners (bit 0: x, bit 1: y, bit 2: z), the first one at the top left of the texture, and normals. */
    private static final int[][] FACES = {
            {0b010, 0b000, 0b100, 0b110}, // west (x0): z0..z1
            {0b111, 0b101, 0b001, 0b011}, // east (x1)
            {0b011, 0b001, 0b000, 0b010}, // north (z0)
            {0b110, 0b100, 0b101, 0b111}, // south (z1)
            {0b010, 0b110, 0b111, 0b011}, // up (y1)
            {0b100, 0b000, 0b001, 0b101}}; // down (y0)
    private static final float[][] NORMALS = {{-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}, {0, 1, 0}, {0, -1, 0}};

    private BubbleFilm() {
    }

    /**
     * The film for a cube of {@code side} blocks: the one nearest the world's 16 pixels per block (nearest by ratio),
     * the 10 x 10 one if {@link #TIERS} is off.
     */
    public static Tier tier(float side) {
        if (!TIERS) return FILM_TIERS[0];
        float wanted = side * 16;
        Tier best = FILM_TIERS[0];
        for (Tier tier : FILM_TIERS) {
            if (Math.abs(Math.log(tier.pixels() / wanted)) < Math.abs(Math.log(best.pixels() / wanted))) best = tier;
        }
        return best;
    }

    /**
     * A cube of half side {@code half} around ({@code x}, {@code y}, {@code z}), each face the square of pixels
     * {@code from}..{@code to} of {@code sprite} tinted {@code r}, {@code g}, {@code b}, {@code a}; both sides of each
     * face drawn (the layer does not cull).
     */
    public static void cube(MatrixStack.Entry entry, VertexConsumer buffer, Sprite sprite, float x, float y, float z, float half,
                            float from, float to, float r, float g, float b, float a, int light) {
        float u0 = sprite.getFrameU(from / 16), u1 = sprite.getFrameU(to / 16);
        float v0 = sprite.getFrameV(from / 16), v1 = sprite.getFrameV(to / 16);
        float[] us = {u0, u0, u1, u1}, vs = {v0, v1, v1, v0};
        for (int face = 0; face < FACES.length; face++) {
            float[] n = NORMALS[face];
            for (int corner = 0; corner < 4; corner++) {
                int bits = FACES[face][corner];
                buffer.vertex(entry, (bits & 0b001) != 0 ? x + half : x - half, (bits & 0b010) != 0 ? y + half : y - half,
                                (bits & 0b100) != 0 ? z + half : z - half)
                        .color(r, g, b, a).texture(us[corner], vs[corner]).overlay(OverlayTexture.DEFAULT_UV).light(light)
                        .normal(entry, n[0], n[1], n[2]);
            }
        }
    }

    /**
     * Tiny bubbles rising from {@code low} to {@code high} over ({@code cx}, {@code cz}), anywhere within
     * {@code scatter} blocks of it. Each one lives one rise: its size, pace, sway and tint drawn at random for that life
     * (from {@code seed}, the life's number and its index), its start staggered; it swells out of nothing, goes faster
     * as it rises, sways more and more, grows a little and pops (shrinks and fades) at the top. About one for
     * {@code step} blocks of the trail at rest ({@code restLength}: its pace and number never follow a trail whose
     * length changes as its bubble floats, else each tiny bubble's place in its rise would jump every frame). A few
     * cubes per trail, no state kept.
     */
    public static void trail(MatrixStack.Entry entry, VertexConsumer buffer, float cx, float cz, float low, float high, float restLength,
                             float step, float scatter, float time, int seed, int light) {
        float length = high - low;
        if (length <= TINY_HALF * 2) return;
        restLength = Math.max(restLength, TINY_HALF * 2);
        int count = Math.max(2, Math.round(restLength / step));
        Sprite sprite = TINY.getSprite();
        for (int i = 0; i < count; i++) {
            // Its own pace, the same all its lives (so lives follow each other without a jump)
            float pace = 0.7f + 0.6f * random(seed, i, -1);
            float lifeTicks = restLength / (TRAIL_SPEED * pace);
            float age = time / lifeTicks + (i + 0.5f * random(seed, i, -2)) / count;
            int life = MathHelper.floor(age);
            float progress = age - life;
            // This life's look
            float size = 0.5f + 0.6f * random(seed ^ life, i, 0);
            float swayAt = random(seed ^ life, i, 1) * MathHelper.TAU;
            float swaySpeed = 0.12f + 0.12f * random(seed ^ life, i, 2);
            float swayWide = (0.4f + 0.8f * random(seed ^ life, i, 3)) / 16;
            float pink = random(seed ^ life, i, 4);
            // Faster as it rises, sways more, swells out of the plate and pops at the top
            float along = length * progress * (0.6f + 0.4f * progress);
            float sway = swayWide * (0.3f + 0.7f * progress);
            float swell = MathHelper.clamp(progress / 0.12f, 0, 1);
            float pop = MathHelper.clamp((1 - progress) / 0.15f, 0, 1);
            float half = TINY_HALF * size * (0.85f + 0.3f * progress) * swell * (0.4f + 0.6f * pop);
            if (half <= 0.002f) continue;
            float alpha = 0.35f + 0.65f * pop;
            // Its own spot over the plate for this life (evenly over a disc), drawn a little toward the middle as it rises
            float spotAngle = random(seed ^ life, i, 5) * MathHelper.TAU;
            float spotRadius = scatter * MathHelper.sqrt(random(seed ^ life, i, 6)) * (1 - 0.3f * progress);
            float x = cx + MathHelper.cos(spotAngle) * spotRadius + MathHelper.sin(time * swaySpeed + swayAt) * sway;
            float z = cz + MathHelper.sin(spotAngle) * spotRadius + MathHelper.cos(time * swaySpeed * 0.8f + swayAt * 1.3f) * sway * 0.7f;
            float y = Math.min(low + along, high - half);
            cube(entry, buffer, sprite, x, y, z, half, TINY_FROM, TINY_TO, MathHelper.lerp(pink, TINT_BLUE[0], TINT_PINK[0]),
                    MathHelper.lerp(pink, TINT_BLUE[1], TINT_PINK[1]), MathHelper.lerp(pink, TINT_BLUE[2], TINT_PINK[2]), alpha, light);
        }
    }

    /** A number in [0, 1) the same for the same {@code a}, {@code b}, {@code c}, scattered otherwise. */
    public static float random(int a, int b, int c) {
        int h = a * 0x9E3779B1 ^ b * 0x85EBCA77 ^ c * 0xC2B2AE3D;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h >>> 8) / 16777216f;
    }
}
