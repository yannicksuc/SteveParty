package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlock;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlockEntity;
import fr.lordfinn.steveparty.client.board.SpawnMarkerView;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.SpriteAtlasTexture;
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
 * A Spawn Bubble, all of it (its block draws nothing): its plate (its block model), and its bubble, a translucent cube
 * of 10 pixels, its centre in the middle of the block lifted as its menu says, slowly floating up and down ({@link #BOB_HEIGHT}, {@link #BOB_SPEED}),
 * its film wobbling and swaying like a soap bubble's ({@link #WOBBLE}, {@link #SWAY})
 * with what is inside. Its faces are a soap film (textures/block/spawn_bubble_iridescent.png, pixels 3 to 12, an
 * animated texture): an almost clear middle, an iridescent rim (cyan, lavender, pink, a touch of yellow-green) whose
 * hues slowly turn, a glint sweeping across, a highlight at the top left; its item keeps the still spawn_bubble.png. Lifted, tiny bubbles (spawn_bubble_small.png, pixels
 * 6 to 8, tinted blue to pink) rise between it and the plate, each its own size, pace and sway, faster as they go up,
 * popping at the end ({@link #trail}). Inside: a blue arrow (models/block/spawn_bubble_arrow.json) pointing its way while no
 * space links it; once linked, a miniature of the space's mob facing its way (several spaces: each mob in turn,
 * {@link #CYCLE} ticks each), from {@link #MOB_RANGE} blocks: farther away the arrow; nothing while its real mob lives
 * on it (« always visible », a party running). During a party nothing of it shows, plate included, but to those who build the
 * board (SpawnMarkerView#sees). Placed, it comes out of the plate growing ({@link SpawnMarkerBlockEntity#INFLATE}).
 * <p>
 * What is inside stays 2 pixels or more away from the faces (no z-fighting) and is drawn first; the cube comes last,
 * on a translucent layer that is not one of the buffers drawn apart at the end (requesting it draws the content's
 * layer at once) and whose quads are sorted back to front: the content shows through every face. Its item is a plain
 * block model (models/item/spawn_marker.json: the plate, the arrow, the cube).
 */
public class SpawnBubbleRenderer implements BlockEntityRenderer<SpawnMarkerBlockEntity> {
    public static final Identifier ARROW_MODEL = Steveparty.id("block/spawn_bubble_arrow");
    private static final RenderLayer BUBBLE_LAYER = RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
    private static final RenderLayer ARROW_LAYER = RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
    /** Half the side of the bubble's cube (blocks). */
    private static final float HALF = 5 / 16f;
    /** How big the miniature mob is at most (blocks): well inside the cube. */
    private static final float MINIATURE = 5f / 16;
    /** How long the bubble takes to come out of its plate (ticks). */
    private static final float INFLATE_TICKS = 24;
    /** How long a bubble first seen waits for its inflating event before being drawn whole (ticks). */
    private static final long FIRST_TICKS = 3;
    /** How long each mob shows when several spaces link the bubble (ticks). */
    public static final int CYCLE = 40;
    /** Within this many blocks the mob shows in the bubble, farther away its arrow. */
    public static final double MOB_RANGE = 8;
    /** About one tiny bubble of the trail for this many blocks of it. */
    private static final float TRAIL_STEP = 3 / 16f;
    /** How far from the middle a tiny bubble starts at most (blocks): anywhere under the bubble. */
    private static final float SCATTER = 4.5f / 16;
    /** How high the bubble floats over its rest height at most (blocks): never down into its plate. */
    private static final float BOB_HEIGHT = 3f / 16;
    /** How fast it floats up and down (radians per tick: about 4 s up and down). */
    private static final float BOB_SPEED = 0.08f;
    /** Its soap film's wobble: how much it squashes and stretches (share of its size), how fast (radians per tick). */
    private static final float WOBBLE = 0.05f, WOBBLE_SPEED = 0.17f;
    /** How far it sways off upright (degrees), and how fast (radians per tick, two slightly different ways). */
    private static final float SWAY = 4f, SWAY_SPEED_X = 0.05f, SWAY_SPEED_Z = 0.037f;
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
        // During a party nothing of it shows (plate included), but to those who build the board
        if (marker.isPartyRunning() && (client.player == null || !SpawnMarkerView.sees(client.player))) return;
        plate(client, marker.getCachedState(), matrices, consumers, light, overlay);
        float time = world.getTime() + tickDelta;
        // Its floating, wobbling and trail run on the clock, in ticks (wrapped each hour: precise as a float), not on the
        // world's time: a long-lived world's time as a float loses its fractions, and a frozen one would stop them
        float anim = (Util.getMeasuringTimeMs() % 3_600_000L) / 50f;
        BlockPos pos = marker.getPos();
        float yaw = SpawnMarkerBlock.yaw(marker.getCachedState());
        float lift = (float) marker.getLift();
        // Placed: out of the plate, growing (overshooting a little), rising to its height
        float grown = 1, risen = 1;
        // Just placed, the block comes a tick or two before its inflating event: nothing drawn until then (else one
        // frame of the whole bubble before it starts from nothing); one coming into view waits as long, unnoticed
        if (marker.seenAt == Long.MIN_VALUE) marker.seenAt = world.getTime();
        if (marker.inflatedAt == Long.MIN_VALUE && world.getTime() - marker.seenAt < FIRST_TICKS) return;
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
        // Inside: the arrow, or the mob (close enough), or nothing when its real mob lives on it (always visible, a party
        // running); otherwise it only comes when a token lands, so the bubble keeps showing which mob it is
        List<EntityType<?>> mobs = marker.getLinkedMobs();
        boolean mobShown = false;
        if (!mobs.isEmpty()) {
            if (marker.isPartyRunning() && marker.isResident()) mobShown = true; // the real mob is there: an empty bubble
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
        BubbleFilm.cube(matrices.peek(), buffer, BubbleFilm.FILM.getSprite(), 0, 0, 0, HALF * grown, BubbleFilm.FACE_FROM, BubbleFilm.FACE_TO,
                1, 1, 1, 1, bubbleLight);
        matrices.pop();
        if (Math.abs(lift) >= 0.25f && risen >= 1) trail(matrices, buffer, plateTop, height, bob, anim, pos.hashCode(), light);
    }

    /** Its plate: the block's own model (turned by its blockstate), drawn here so that it can be hidden. */
    private static void plate(MinecraftClient client, BlockState state, MatrixStack matrices, VertexConsumerProvider consumers,
                              int light, int overlay) {
        BakedModel model = client.getBlockRenderManager().getModel(state);
        client.getBlockRenderManager().getModelRenderer().render(matrices.peek(), consumers.getBuffer(ARROW_LAYER), state, model,
                1, 1, 1, light, overlay);
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
     * Tiny bubbles rising between the plate and the lifted bubble (from the plate up to it, or from it up to the plate's
     * underside when it hangs below), anywhere under the bubble ({@link #SCATTER}; see {@link BubbleFilm#trail}).
     */
    private static void trail(MatrixStack matrices, VertexConsumer buffer, float plateTop, float rest, float bob, float time, int seed,
                              int light) {
        float center = rest + bob;
        boolean up = center > plateTop;
        float low = up ? plateTop : center + HALF, high = up ? center - HALF : 0;
        float restLength = up ? rest - HALF - plateTop : -rest - HALF;
        BubbleFilm.trail(matrices.peek(), buffer, 0.5f, 0.5f, low, high, restLength, TRAIL_STEP, SCATTER, time, seed, light);
    }

    @Override
    public boolean rendersOutsideBoundingBox(SpawnMarkerBlockEntity marker) {
        return true;
    }
}
