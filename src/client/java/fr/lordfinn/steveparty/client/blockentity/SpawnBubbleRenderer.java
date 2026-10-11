package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlock;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlockEntity;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The bubble of a Spawn Bubble (its plate is its block model): a translucent cube of 10 pixels (each face
 * textures/block/spawn_bubble.png, pixels 3 to 12), its centre in the middle of the block lifted as its menu says,
 * gently bobbing with what is inside; lifted, a trail of tiny 3-pixel cubes (spawn_bubble_small.png, pixels 6 to 8)
 * rises between it and the plate. Inside: a blue arrow (models/block/spawn_bubble_arrow.json) pointing its way while no
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
    private static final SpriteIdentifier BUBBLE = new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Steveparty.id("block/spawn_bubble"));
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
    /** Between two tiny cubes of the trail (blocks). */
    private static final float TRAIL_STEP = 5 / 16f;
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
        // Floating: the cube and what is inside bob together
        float bob = MathHelper.sin(time * 0.09f + phase) * 0.6f / 16;
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
        cube(matrices.peek(), buffer, BUBBLE.getSprite(), 0.5f, centerY, 0.5f, HALF * grown, FACE_FROM, FACE_TO, bubbleLight);
        if (Math.abs(lift) >= 0.25f && risen >= 1) trail(matrices, buffer, plateTop, height + bob, time, phase, light);
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
                return Optional.ofNullable(type.create(world));
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
     * {@code from}..{@code to} of {@code sprite}; both sides of each face drawn (the layer does not cull).
     */
    private static void cube(MatrixStack.Entry entry, VertexConsumer buffer, Sprite sprite, float x, float y, float z, float half,
                             float from, float to, int light) {
        float u0 = sprite.getFrameU(from / 16), u1 = sprite.getFrameU(to / 16);
        float v0 = sprite.getFrameV(from / 16), v1 = sprite.getFrameV(to / 16);
        float[] us = {u0, u0, u1, u1}, vs = {v0, v1, v1, v0};
        for (int face = 0; face < FACES.length; face++) {
            float[] n = NORMALS[face];
            for (int corner = 0; corner < 4; corner++) {
                int bits = FACES[face][corner];
                buffer.vertex(entry, (bits & 0b001) != 0 ? x + half : x - half, (bits & 0b010) != 0 ? y + half : y - half,
                                (bits & 0b100) != 0 ? z + half : z - half)
                        .color(1f, 1f, 1f, 1f).texture(us[corner], vs[corner]).overlay(OverlayTexture.DEFAULT_UV).light(light)
                        .normal(entry, n[0], n[1], n[2]);
            }
        }
    }

    /** Tiny cubes rising (or sinking) from the plate to the lifted bubble, a little to and fro. */
    private static void trail(MatrixStack matrices, VertexConsumer buffer, float plateTop, float center, float time, float phase, int light) {
        boolean up = center > plateTop;
        float from = up ? plateTop : 0, to = up ? center - HALF : center + HALF;
        float length = Math.abs(to - from);
        int count = (int) (length / TRAIL_STEP);
        if (count <= 0) return;
        float drift = (time * 0.015f) % TRAIL_STEP;
        Sprite sprite = TINY.getSprite();
        MatrixStack.Entry entry = matrices.peek();
        for (int i = 0; i < count; i++) {
            float along = i * TRAIL_STEP + drift + TRAIL_STEP / 2;
            if (along > length - TINY_HALF) continue;
            float y = up ? from + along : from - along;
            float swayX = MathHelper.sin(time * 0.12f + i * 1.7f + phase) * 0.7f / 16;
            float swayZ = MathHelper.cos(time * 0.1f + i * 2.3f) * 0.4f / 16;
            cube(entry, buffer, sprite, 0.5f + swayX, y, 0.5f + swayZ, TINY_HALF, TINY_FROM, TINY_TO, light);
        }
    }

    @Override
    public boolean rendersOutsideBoundingBox(SpawnMarkerBlockEntity marker) {
        return true;
    }
}
