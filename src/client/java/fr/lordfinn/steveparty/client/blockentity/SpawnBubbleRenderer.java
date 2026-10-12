package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlock;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlockEntity;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The bubble of a Spawn Bubble (its plate is its block model): a translucent cube of 10 pixels, its centre in the
 * middle of the block lifted as its menu says, slowly floating up and down ({@link #BOB_HEIGHT}, {@link #BOB_SPEED}),
 * its film wobbling and swaying like a soap bubble's ({@link #WOBBLE}, {@link #SWAY})
 * with what is inside. Its faces are a soap film (textures/block/spawn_bubble_iridescent.png, pixels 3 to 12, an
 * animated texture): an almost clear middle, an iridescent rim (cyan, lavender, pink, a touch of yellow-green) whose
 * hues slowly turn, a glint sweeping across, a highlight at the top left; its item keeps the still spawn_bubble.png. Lifted, tiny bubbles (spawn_bubble_small.png, pixels
 * 6 to 8, tinted blue to pink) rise between it and the plate, each its own size, pace and sway, faster as they go up,
 * popping at the end ({@link #trail}). Inside: a blue arrow (models/block/spawn_bubble_arrow.json) pointing its way while no
 * space links it; once linked, a miniature of the space's mob facing its way (several spaces: each mob in turn,
 * {@link #CYCLE} ticks each), only out of a party and from {@link #MOB_RANGE} blocks: farther away the arrow, during a
 * party nothing (the real mob is there). Placed, it comes out of the plate growing ({@link SpawnMarkerBlockEntity#INFLATE}).
 * <p>
 * What is inside stays 2 pixels or more away from the faces (no z-fighting) and is drawn first; the cube comes last,
 * on a translucent layer that is not one of the buffers drawn apart at the end (requesting it draws the content's
 * layer at once) and whose quads are sorted back to front: the content shows through every face. Its item is a plain
 * block model (models/item/spawn_marker.json: the plate, the arrow, the cube).
 */
public class SpawnBubbleRenderer implements BlockEntityRenderer<SpawnMarkerBlockEntity> {
    public static final Identifier ARROW_MODEL = Steveparty.id("block/spawn_bubble_arrow");
    private static final SpriteIdentifier BUBBLE = new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Steveparty.id("block/spawn_bubble_iridescent"));
    private static final SpriteIdentifier TINY = new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Steveparty.id("block/spawn_bubble_small"));
    private static final RenderLayer BUBBLE_LAYER = RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
    private static final RenderLayer ARROW_LAYER = RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
    /** Half the side of the bubble's cube (blocks), and where its face is in its texture (pixels). */
    private static final float HALF = 5 / 16f, FACE_FROM = 3, FACE_TO = 13;
    /** Half the side of a tiny cube of the trail (blocks), and where its face is in its texture (pixels). */
    private static final float TINY_HALF = 1.5f / 16, TINY_FROM = 6, TINY_TO = 9;
    /** How big the miniature mob is at most (blocks): well inside the cube. */
    private static final float MINIATURE = 5f / 16;
    /** How long the bubble takes to come out of its plate (ticks). */
    private static final float INFLATE_TICKS = 12;
    /** How long each mob shows when several spaces link the bubble (ticks). */
    public static final int CYCLE = 40;
    /** Within this many blocks the mob shows in the bubble, farther away its arrow. */
    public static final double MOB_RANGE = 8;
    /** About one tiny bubble of the trail for this many blocks of it. */
    private static final float TRAIL_STEP = 3 / 16f;
    /** How far from the middle a tiny bubble starts at most (blocks): anywhere under the bubble. */
    private static final float SCATTER = 4.5f / 16;
    /** How fast a tiny bubble rises on average (blocks per tick). */
    private static final float TRAIL_SPEED = 0.022f;
    /** How high the bubble floats over its rest height at most (blocks): never down into its plate. */
    private static final float BOB_HEIGHT = 3f / 16;
    /** How fast it floats up and down (radians per tick: about 4 s up and down). */
    private static final float BOB_SPEED = 0.08f;
    /** Its soap film's wobble: how much it squashes and stretches (share of its size), how fast (radians per tick). */
    private static final float WOBBLE = 0.05f, WOBBLE_SPEED = 0.17f;
    /** How far it sways off upright (degrees), and how fast (radians per tick, two slightly different ways). */
    private static final float SWAY = 4f, SWAY_SPEED_X = 0.05f, SWAY_SPEED_Z = 0.037f;
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

    /** The miniatures, one per kind of mob (empty: it cannot be made on the client), made again in another world. */
    private static final Map<EntityType<?>, Optional<Entity>> MINIATURES = new HashMap<>();
    private static World miniaturesWorld;

    public SpawnBubbleRenderer(BlockEntityRendererFactory.Context context) {
    }

    /** Its arrow's model, baked with the others. */
    public static void registerModels() {
        ModelLoadingPlugin.register(context -> context.addModels(ARROW_MODEL));
    }

    @Override
    public void render(SpawnMarkerBlockEntity marker, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers,
                       int light, int overlay) {
        World world = marker.getWorld();
        if (world == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        float time = world.getTime() + tickDelta;
        // Its floating, wobbling and trail run on the clock, in ticks (wrapped each hour: precise as a float), not on the
        // world's time: a long-lived world's time as a float loses its fractions, and a frozen one would stop them
        float anim = (Util.getMeasuringTimeMs() % 3_600_000L) / 50f;
        BlockPos pos = marker.getPos();
        float yaw = SpawnMarkerBlock.yaw(marker.getCachedState());
        float lift = (float) marker.getLift();
        // Placed: out of the plate, growing (overshooting a little), rising to its height
        float grown = 1, risen = 1;
        if (marker.inflatedAt != Long.MIN_VALUE) {
            float t = MathHelper.clamp((time - marker.inflatedAt) / INFLATE_TICKS, 0, 1);
            float back = t - 1;
            grown = 1 + 2.70158f * back * back * back + 1.70158f * back * back;
            risen = 1 - (1 - t) * (1 - t) * (1 - t);
        }
        if (grown <= 0.01f) return;
        float plateTop = (float) SpawnMarkerBlock.PLATE_HEIGHT;
        float height = (float) SpawnMarkerBlock.BUBBLE_HEIGHT + lift;
        float phase = (pos.hashCode() & 63) * 0.1f;
        // Floating: the cube and what is inside (arrow or mob) bob together, from its rest height up
        float bob = (1 - MathHelper.cos(anim * BOB_SPEED + phase)) / 2 * BOB_HEIGHT;
        float centerY = plateTop + (height + bob - plateTop) * risen;
        int bubbleLight = lift == 0 ? light
                : WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(pos.getX() + 0.5, pos.getY() + centerY, pos.getZ() + 0.5));
        // Inside: the arrow, or the mob (out of a party, close enough), or nothing during a party
        List<EntityType<?>> mobs = marker.getLinkedMobs();
        boolean mobShown = false;
        if (!mobs.isEmpty()) {
            if (marker.isPartyRunning()) mobShown = true; // the real mob is there: an empty bubble
            else if (client.player != null && client.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + centerY, pos.getZ() + 0.5) <= MOB_RANGE * MOB_RANGE) {
                EntityType<?> kind = mobs.get((int) ((world.getTime() / CYCLE) % mobs.size()));
                mobShown = miniature(world, kind, pos, centerY, yaw, time, tickDelta, grown, matrices, consumers, bubbleLight);
            }
        }
        if (!mobShown) arrow(client, matrices, consumers, centerY, yaw, grown, bubbleLight, overlay);
        // The cube last, over what is inside
        VertexConsumer buffer = consumers.getBuffer(BUBBLE_LAYER);
        // A soap bubble, not a glass cube: its film wobbles (taller and thinner, then squatter) and it sways a little
        float squash = WOBBLE * MathHelper.sin(anim * WOBBLE_SPEED + phase);
        matrices.push();
        matrices.translate(0.5f, centerY, 0.5f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(SWAY * MathHelper.sin(anim * SWAY_SPEED_X + phase)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(SWAY * MathHelper.sin(anim * SWAY_SPEED_Z + phase * 1.7f)));
        matrices.scale(1 - squash / 2, 1 + squash, 1 - squash / 2);
        cube(matrices.peek(), buffer, BUBBLE.getSprite(), 0, 0, 0, HALF * grown, FACE_FROM, FACE_TO, 1, 1, 1, 1, bubbleLight);
        matrices.pop();
        if (Math.abs(lift) >= 0.25f && risen >= 1) trail(matrices, buffer, plateTop, height + bob, anim, pos.hashCode(), light);
    }

    /** The blue arrow at the bubble's centre, pointing its way. */
    private static void arrow(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider consumers, float centerY, float yaw,
                              float scale, int light, int overlay) {
        BakedModel model = client.getBakedModelManager().getModel(ARROW_MODEL);
        if (model == null) return;
        matrices.push();
        matrices.translate(0.5, centerY, 0.5);
        // The model points north: turned to the yaw (0 south, 90 west, 180 north)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - yaw));
        matrices.scale(scale, scale, scale);
        matrices.translate(-0.5, -0.5, -0.5);
        client.getBlockRenderManager().getModelRenderer().render(matrices.peek(), consumers.getBuffer(ARROW_LAYER), null, model,
                1, 1, 1, light, overlay);
        matrices.pop();
    }

    /** The mob {@code kind} in small at the bubble's centre, facing {@code yaw}. @return whether it could be drawn */
    private static boolean miniature(World world, EntityType<?> kind, BlockPos pos, float centerY, float yaw, float time, float tickDelta,
                                     float grown, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        if (miniaturesWorld != world) {
            MINIATURES.clear();
            miniaturesWorld = world;
        }
        Entity mob = MINIATURES.computeIfAbsent(kind, type -> {
            try {
                Entity created = type.create(world);
                // Never ticked: the Shop's merchant would stay closed in his box
                if (created instanceof BoxedTraderEntity trader) trader.comeOut();
                return Optional.ofNullable(created);
            } catch (RuntimeException e) {
                Steveparty.LOGGER.warn("No miniature of {} for the spawn bubbles", EntityType.getId(type), e);
                return Optional.empty();
            }
        }).orElse(null);
        if (mob == null) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        EntityRenderer<? super Entity> renderer = client.getEntityRenderDispatcher().getRenderer(mob);
        if (renderer == null) return false;
        float size = Math.max(mob.getWidth(), mob.getHeight());
        float scale = (size <= 0 ? 1 : MINIATURE / size) * grown;
        mob.setPos(pos.getX() + 0.5, pos.getY() + centerY, pos.getZ() + 0.5);
        mob.age = (int) time;
        mob.setYaw(yaw);
        mob.prevYaw = yaw;
        mob.setPitch(0);
        if (mob instanceof LivingEntity living) {
            living.bodyYaw = living.prevBodyYaw = yaw;
            living.headYaw = living.prevHeadYaw = yaw;
        }
        matrices.push();
        matrices.translate(0.5, centerY - mob.getHeight() * scale / 2, 0.5);
        matrices.scale(scale, scale, scale);
        try {
            renderer.render(mob, yaw, tickDelta, matrices, consumers, light);
        } catch (RuntimeException e) {
            // A renderer that needs a mob in the world: the arrow instead, for good
            Steveparty.LOGGER.warn("No miniature of {} for the spawn bubbles", EntityType.getId(kind), e);
            MINIATURES.put(kind, Optional.empty());
            matrices.pop();
            return false;
        }
        matrices.pop();
        return true;
    }

    /**
     * A cube of half side {@code half} around ({@code x}, {@code y}, {@code z}), each face the square of pixels
     * {@code from}..{@code to} of {@code sprite} tinted {@code r}, {@code g}, {@code b}, {@code a}; both sides of each
     * face drawn (the layer does not cull).
     */
    private static void cube(MatrixStack.Entry entry, VertexConsumer buffer, Sprite sprite, float x, float y, float z, float half,
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
     * Tiny bubbles rising between the plate and the lifted bubble (from the plate up to it, or from it up to the plate's
     * underside when it hangs below). Each one lives one rise: its size, pace, sway and tint drawn at random for that
     * life (from {@code seed}, the life's number and its index), its start staggered and its spot anywhere under the
     * bubble ({@link #SCATTER}); it swells out of nothing, goes
     * faster as it rises, sways more and more, grows a little and pops (shrinks and fades) at the top. A few cubes per
     * bubble, no state kept.
     */
    private static void trail(MatrixStack matrices, VertexConsumer buffer, float plateTop, float center, float time, int seed, int light) {
        boolean up = center > plateTop;
        float low = up ? plateTop : center + HALF, high = up ? center - HALF : 0;
        float length = high - low;
        if (length <= TINY_HALF * 2) return;
        int count = Math.max(2, Math.round(length / TRAIL_STEP));
        Sprite sprite = TINY.getSprite();
        MatrixStack.Entry entry = matrices.peek();
        for (int i = 0; i < count; i++) {
            // Its own pace, the same all its lives (so lives follow each other without a jump)
            float pace = 0.7f + 0.6f * random(seed, i, -1);
            float lifeTicks = length / (TRAIL_SPEED * pace);
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
            // Its own spot over the plate for this life (evenly over a disc under the bubble), drawn a little toward
            // the middle as it rises
            float spotAngle = random(seed ^ life, i, 5) * MathHelper.TAU;
            float spotRadius = SCATTER * MathHelper.sqrt(random(seed ^ life, i, 6)) * (1 - 0.3f * progress);
            float x = 0.5f + MathHelper.cos(spotAngle) * spotRadius + MathHelper.sin(time * swaySpeed + swayAt) * sway;
            float z = 0.5f + MathHelper.sin(spotAngle) * spotRadius + MathHelper.cos(time * swaySpeed * 0.8f + swayAt * 1.3f) * sway * 0.7f;
            float y = Math.min(low + along, high - half);
            cube(entry, buffer, sprite, x, y, z, half, TINY_FROM, TINY_TO, MathHelper.lerp(pink, TINT_BLUE[0], TINT_PINK[0]),
                    MathHelper.lerp(pink, TINT_BLUE[1], TINT_PINK[1]), MathHelper.lerp(pink, TINT_BLUE[2], TINT_PINK[2]), alpha, light);
        }
    }

    /** A number in [0, 1) the same for the same {@code a}, {@code b}, {@code c}, scattered otherwise. */
    private static float random(int a, int b, int c) {
        int h = a * 0x9E3779B1 ^ b * 0x85EBCA77 ^ c * 0xC2B2AE3D;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h >>> 8) / 16777216f;
    }

    @Override
    public boolean rendersOutsideBoundingBox(SpawnMarkerBlockEntity marker) {
        return true;
    }
}
