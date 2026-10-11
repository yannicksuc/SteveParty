package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlock;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlockEntity;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The bubble of a Spawn Bubble (its plate is its block model): a soap bubble of 10 pixels, its centre in the middle of
 * the block lifted as its menu says, gently bobbing and wobbling, drawn facing the camera (textures/block/spawn_bubble.png);
 * lifted, a trail of tiny bubbles (spawn_bubble_small.png) rises between it and the plate. Inside: a blue arrow
 * (models/block/spawn_bubble_arrow.json) pointing its way while no space links it; once linked, a miniature of the
 * space's mob facing its way (several spaces: each mob in turn, {@link #CYCLE} ticks each), only out of a party and
 * from {@link #MOB_RANGE} blocks: farther away the arrow, during a party nothing (the real mob is there). Placed, it
 * comes out of the plate inflating ({@link SpawnMarkerBlockEntity#INFLATE}).
 * <p>
 * The arrow and the mob are drawn first, the bubble last, on a translucent layer pushed to the camera's side of the
 * bubble: it never hides what is inside. Its item ({@link #ITEM}): the plate and the bubble with its arrow.
 */
public class SpawnBubbleRenderer implements BlockEntityRenderer<SpawnMarkerBlockEntity> {
    public static final Identifier ARROW_MODEL = Steveparty.id("block/spawn_bubble_arrow");
    private static final SpriteIdentifier BUBBLE = new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Steveparty.id("block/spawn_bubble"));
    private static final SpriteIdentifier TINY = new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Steveparty.id("block/spawn_bubble_small"));
    /** Drawn after what is inside: not one of the buffers drawn apart at the end, so the mob's layer is drawn before it. */
    private static final RenderLayer BUBBLE_LAYER = RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
    private static final RenderLayer ARROW_LAYER = RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
    /** The radius of the bubble (blocks): its texture is a 10-pixel ring in a 16-pixel square. */
    private static final float RADIUS = 5 / 16f;
    /** How big the miniature mob is at most (blocks), to fit inside. */
    private static final float MINIATURE = 6 / 16f;
    /** How long the bubble takes to come out of its plate (ticks). */
    private static final float INFLATE_TICKS = 12;
    /** How long each mob shows when several spaces link the bubble (ticks). */
    public static final int CYCLE = 40;
    /** Within this many blocks the mob shows in the bubble, farther away its arrow. */
    public static final double MOB_RANGE = 8;
    /** Between two tiny bubbles of the trail (blocks). */
    private static final float TRAIL_STEP = 5 / 16f;
    /** The display rotation of the item in the inventory (models/item/spawn_marker.json), turned back for its bubble. */
    private static final Quaternionf GUI_BACK = new Quaternionf()
            .rotationXYZ(30 * MathHelper.RADIANS_PER_DEGREE, 225 * MathHelper.RADIANS_PER_DEGREE, 0).conjugate();

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
        // Placed: out of the plate, inflating (overshooting a little), rising to its height
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
        // The bubble last, over what is inside
        VertexConsumer buffer = consumers.getBuffer(BUBBLE_LAYER);
        float wobble = MathHelper.sin(time * 0.21f + phase) * 0.035f;
        // Moved toward the camera along its line of sight (in front of what is inside, not shifted on the screen), and
        // shrunk as much as it comes closer: it looks as big as a bubble round its centre
        Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + centerY, pos.getZ() + 0.5);
        Vec3d toCamera = client.gameRenderer.getCamera().getPos().subtract(center);
        double distance = toCamera.length();
        float radius = RADIUS * grown;
        Vec3d push = distance > radius * 2 ? toCamera.multiply(radius / distance) : Vec3d.ZERO;
        float shrink = distance > radius * 2 ? (float) ((distance - radius) / distance) : 1;
        // Lit as a face looking up (the brightest): a white bubble, whichever way the camera looks
        MatrixStack.Entry lit = matrices.peek();
        matrices.push();
        matrices.translate(0.5 + push.x, centerY + push.y, 0.5 + push.z);
        matrices.multiply(client.getEntityRenderDispatcher().getRotation());
        bubble(matrices, lit, buffer, BUBBLE.getSprite(), grown * (1 + wobble) * shrink, grown * (1 - wobble) * shrink, 0, bubbleLight);
        matrices.pop();
        if (Math.abs(lift) >= 0.25f && risen >= 1) trail(client, matrices, buffer, plateTop, height + bob, time, phase, grown, light);
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
     * A bubble's quad (the whole texture: its ring in the middle) facing the camera (the matrices turned to it), pushed
     * {@code toward} blocks to the camera's side so that what is inside stays behind it.
     */
    private static void bubble(MatrixStack matrices, MatrixStack.Entry lit, VertexConsumer buffer, Sprite sprite, float width, float height,
                               float toward, int light) {
        MatrixStack.Entry entry = matrices.peek();
        float w = width / 2, h = height / 2;
        vertex(entry, lit, buffer, -w, -h, toward, sprite.getMinU(), sprite.getMaxV(), light);
        vertex(entry, lit, buffer, w, -h, toward, sprite.getMaxU(), sprite.getMaxV(), light);
        vertex(entry, lit, buffer, w, h, toward, sprite.getMaxU(), sprite.getMinV(), light);
        vertex(entry, lit, buffer, -w, h, toward, sprite.getMinU(), sprite.getMinV(), light);
    }

    private static void vertex(MatrixStack.Entry entry, MatrixStack.Entry lit, VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.vertex(entry, x, y, z).color(1f, 1f, 1f, 1f).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light)
                .normal(lit, 0, 1, 0);
    }

    /** Tiny bubbles rising (or sinking) from the plate to the lifted bubble, a little to and fro. */
    private static void trail(MinecraftClient client, MatrixStack matrices, VertexConsumer buffer, float plateTop, float center,
                              float time, float phase, float grown, int light) {
        boolean up = center > plateTop;
        float from = up ? plateTop : 0, to = up ? center - RADIUS : center + RADIUS;
        float length = Math.abs(to - from);
        int count = (int) (length / TRAIL_STEP);
        if (count <= 0) return;
        float drift = (time * 0.015f) % TRAIL_STEP;
        Sprite sprite = TINY.getSprite();
        MatrixStack.Entry lit = matrices.peek();
        for (int i = 0; i < count; i++) {
            float along = i * TRAIL_STEP + drift + TRAIL_STEP / 2;
            if (along > length) continue;
            float y = up ? from + along : from - along;
            float sway = MathHelper.sin(time * 0.12f + i * 1.7f + phase) * 0.7f / 16;
            matrices.push();
            matrices.translate(0.5 + sway, y, 0.5 + MathHelper.cos(time * 0.1f + i * 2.3f) * 0.4f / 16);
            matrices.multiply(client.getEntityRenderDispatcher().getRotation());
            bubble(matrices, lit, buffer, sprite, grown, grown, 0, light);
            matrices.pop();
        }
    }

    @Override
    public boolean rendersOutsideBoundingBox(SpawnMarkerBlockEntity marker) {
        return true;
    }

    /** Its item: the plate, and over it the bubble with its arrow, as one. */
    public static final BuiltinItemRendererRegistry.DynamicItemRenderer ITEM = (stack, mode, matrices, consumers, light, overlay) -> {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockState state = ModBlocks.SPAWN_MARKER.getDefaultState();
        BlockRenderManager blocks = client.getBlockRenderManager();
        blocks.getModelRenderer().render(matrices.peek(), consumers.getBuffer(TexturedRenderLayers.getEntityCutout()), state,
                blocks.getModel(state), 1, 1, 1, light, overlay);
        float center = (float) SpawnMarkerBlock.BUBBLE_HEIGHT;
        arrow(client, matrices, consumers, center, SpawnMarkerBlock.yaw(state), 1, light, overlay);
        matrices.push();
        matrices.translate(0.5, center, 0.5);
        MatrixStack.Entry lit = matrices.peek();
        Quaternionf facing = new Quaternionf();
        if (mode == ModelTransformationMode.GUI) {
            facing.set(GUI_BACK);
        } else {
            // Its own turn undone (in hand: the camera's frame); in the world, turned to the camera
            matrices.peek().getPositionMatrix().getNormalizedRotation(facing).conjugate();
            if (!mode.isFirstPerson()) facing.mul(client.getEntityRenderDispatcher().getRotation());
        }
        matrices.multiply(facing);
        bubble(matrices, lit, consumers.getBuffer(BUBBLE_LAYER), BUBBLE.getSprite(), 1, 1, RADIUS, light);
        matrices.pop();
    };
}
