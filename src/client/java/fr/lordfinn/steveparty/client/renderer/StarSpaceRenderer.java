package fr.lordfinn.steveparty.client.renderer;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.client.payloads.ClientPayloads;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.payloads.custom.StarSpacesPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * The party stars (see fr.lordfinn.steveparty.service.PartyStars): a big Party Star floating and turning over each
 * star space holding one, drawn at any distance (no entity, nothing ticks on the server), with a few gold sparks rising
 * from it and a gold {@link HaloBeam} up to the build height, so that it is seen from afar. The spaces come from
 * {@link StarSpacesPayload}.
 * <p>
 * The star is drawn once the block entities are (in BEFORE_DEBUG_RENDER), opaque, its depth written: before the
 * translucent terrain, so that the water or the glass in front of it blends over it, and in the depth the beam is
 * tested against (the star shows inside its beam, its far side hidden behind it). The beam, which writes no depth, is
 * drawn after the translucent terrain (in AFTER_TRANSLUCENT, see {@link HaloBeam}): over the board tile under it (a
 * block entity: drawn before it, it painted its top face over the beam's foot) and over the water behind it (drawn
 * before it, the water painted over it).
 */
@Environment(EnvType.CLIENT)
public final class StarSpaceRenderer {
    /** Over the space's surface, in blocks; its size; degrees per tick it turns. */
    private static final double HEIGHT = 1.6;
    private static final float SCALE = 1.26F, SPIN = 3.0F;
    /** Farther than this, the star and its beam are not drawn (beyond any board; a beacon's range too), in blocks. */
    private static final double MAX_DISTANCE = 256;
    /** The beam's colour: a warm gold, a little deeper than the Star space's yellow (see {@link HaloBeam}). */
    private static final int BEAM_COLOR = 0xFFFFC93C;
    /** Every how many ticks a spark rises from a star. */
    private static final int SPARK_INTERVAL = 3;

    /** The server tells every few seconds; nothing heard for this long (its party ended, its controller broken): no star. */
    private static final long FORGET_AFTER_TICKS = 300;

    private static List<BlockPos> stars = List.of();
    private static long heardAt;
    private static ItemStack star = ItemStack.EMPTY;

    private StarSpaceRenderer() {
    }

    public static void initialize() {
        ClientPayloads.receive(StarSpacesPayload.ID, (payload, context) -> {
            stars = List.copyOf(payload.spaces());
            heardAt = context.client().world == null ? 0 : context.client().world.getTime();
        });
        ClientTickEvents.END_CLIENT_TICK.register(StarSpaceRenderer::tick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(StarSpaceRenderer::renderStars);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(StarSpaceRenderer::renderBeams);
    }

    /**
     * The stars, opaque: the item layer is translucent, and its faces (unsorted, partly see-through on the texture's
     * mipmaps from afar) showed the cubes behind them through the front ones.
     */
    private static void renderStars(WorldRenderContext context) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (stars.isEmpty() || context.matrixStack() == null || world == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (star.isEmpty()) star = new ItemStack(ModItems.PARTY_STAR);
        MatrixStack matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        float time = world.getTime() + context.tickCounter().getTickDelta(true);
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        BakedModel model = client.getItemRenderer().getModel(star, world, null, 0);
        VertexConsumerProvider opaque = layer -> consumers.getBuffer(
                layer == TexturedRenderLayers.getEntityTranslucentCull() ? TexturedRenderLayers.getEntityCutout() : layer);
        for (BlockPos space : stars) {
            Vec3d at = BoardSpaces.standPos(world, space);
            if (at.squaredDistanceTo(camera) > MAX_DISTANCE * MAX_DISTANCE) continue;
            // its host holds it (StarHosts): the star is in the Mula's hands, its beam stays
            if (MulaEntity.isHostedOnClient(world, space)) continue;
            matrices.push();
            matrices.translate(at.x - camera.x, at.y + HEIGHT + 0.12 * MathHelper.sin(time * 0.08F) - camera.y, at.z - camera.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * SPIN));
            matrices.scale(SCALE, SCALE, SCALE);
            // No display transform (the item's "fixed" one shifts it off its centre): the model's box centred on
            // the beam's axis, so that it turns on the spot
            client.getItemRenderer().renderItem(star, ModelTransformationMode.NONE, false, matrices, opaque, LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    OverlayTexture.DEFAULT_UV, model);
            matrices.pop();
        }
        consumers.draw();
    }

    /** The beams: from each space's surface up to the build height, centred on its cell. */
    private static void renderBeams(WorldRenderContext context) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (stars.isEmpty() || context.matrixStack() == null || world == null) return;
        MatrixStack matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        float time = world.getTime() + context.tickCounter().getTickDelta(true);
        VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        for (BlockPos space : stars) {
            Vec3d at = BoardSpaces.standPos(world, space);
            if (at.squaredDistanceTo(camera) > MAX_DISTANCE * MAX_DISTANCE) continue;
            int bottom = MathHelper.floor(at.y);
            matrices.push();
            matrices.translate(at.x - camera.x, bottom - camera.y, at.z - camera.z);
            HaloBeam.render(matrices, consumers, BEAM_COLOR, HaloBeam.DEFAULT_RADIUS, Math.max(1, world.getTopY() - bottom),
                    1.0F, time);
            matrices.pop();
        }
        consumers.draw();
    }

    /** A gold spark rising from each star now and then (always spawned, so that it is seen from afar). */
    private static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (stars.isEmpty() || world == null || client.isPaused()) return;
        if (Math.abs(world.getTime() - heardAt) > FORGET_AFTER_TICKS) {
            stars = List.of();
            return;
        }
        if (world.getTime() % SPARK_INTERVAL != 0) return;
        for (BlockPos space : stars) {
            if (!world.isChunkLoaded(space)) continue;
            Vec3d at = BoardSpaces.standPos(world, space);
            double dx = (world.random.nextDouble() - 0.5) * 0.6, dz = (world.random.nextDouble() - 0.5) * 0.6;
            // Each spark its own start and push: they fade out all along the beam, never under one ceiling
            double rise = 0.05 + world.random.nextDouble() * 0.22, from = world.random.nextDouble() * 0.8;
            world.addImportantParticle(ParticleTypes.END_ROD, true, at.x + dx, at.y + HEIGHT + from, at.z + dz, 0, rise, 0);
            if (world.random.nextInt(3) == 0)
                world.addImportantParticle(ParticleTypes.WAX_OFF, true, at.x + dx, at.y + HEIGHT + 0.3 + from, at.z + dz, 0,
                        0.3 + world.random.nextDouble() * 0.6, 0);
        }
    }

    /** The party star floats over this space (what is drawn above a space goes over it). */
    public static boolean hasStar(BlockPos space) {
        return stars.contains(space);
    }

    /** A new connection: no star known. */
    public static void clear() {
        stars = List.of();
    }
}
