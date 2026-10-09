package fr.lordfinn.steveparty.client.renderer;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.client.payloads.ClientPayloads;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.payloads.custom.StarSpacesPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
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
 * from it and a golden beacon beam up to the build height, so that it is seen from afar. The spaces come from
 * {@link StarSpacesPayload}.
 */
@Environment(EnvType.CLIENT)
public final class StarSpaceRenderer {
    /** Over the space's surface, in blocks; its size; degrees per tick it turns. */
    private static final double HEIGHT = 1.6;
    private static final float SCALE = 1.26F, SPIN = 3.0F;
    /** Farther than this, the star and its beam are not drawn (beyond any board; a beacon's range too), in blocks. */
    private static final double MAX_DISTANCE = 256;
    /** The beam's colour: the Star space's yellow (ARGB). */
    private static final int BEAM_COLOR = 0xFFFFD83D;
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
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (stars.isEmpty() || context.matrixStack() == null) return;
            MinecraftClient client = MinecraftClient.getInstance();
            ClientWorld world = client.world;
            if (world == null) return;
            if (star.isEmpty()) star = new ItemStack(ModItems.PARTY_STAR);
            MatrixStack matrices = context.matrixStack();
            Vec3d camera = context.camera().getPos();
            float tickDelta = context.tickCounter().getTickDelta(true);
            float time = world.getTime() + tickDelta;
            VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
            // The stars first, opaque: the item layer is translucent, and its faces (unsorted, partly see-through on the
            // texture's mipmaps from afar) showed the cubes behind them through the front ones. Drawn before the beams
            // so that the beam's glow tints the part of the star inside it.
            BakedModel model = client.getItemRenderer().getModel(star, world, null, 0);
            VertexConsumerProvider opaque = layer -> consumers.getBuffer(
                    layer == TexturedRenderLayers.getEntityTranslucentCull() ? TexturedRenderLayers.getEntityCutout() : layer);
            for (BlockPos space : stars) {
                Vec3d at = BoardSpaces.standPos(world, space);
                if (at.squaredDistanceTo(camera) > MAX_DISTANCE * MAX_DISTANCE) continue;
                matrices.push();
                matrices.translate(at.x - camera.x, at.y + HEIGHT + 0.12 * MathHelper.sin(time * 0.08F) - camera.y, at.z - camera.z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * SPIN));
                matrices.scale(SCALE, SCALE, SCALE);
                // No display transform (the item's "fixed" one shifts it off its centre): the model's box centred on
                // the beam's axis, so that it turns on the spot
                client.getItemRenderer().renderItem(star, ModelTransformationMode.NONE, false, matrices, opaque, 0xF000F0,
                        OverlayTexture.DEFAULT_UV, model);
                matrices.pop();
            }
            consumers.draw();
            // The beams: from each space's surface up to the build height (the vanilla beam, centred on its cell)
            for (BlockPos space : stars) {
                Vec3d at = BoardSpaces.standPos(world, space);
                if (at.squaredDistanceTo(camera) > MAX_DISTANCE * MAX_DISTANCE) continue;
                int bottom = MathHelper.floor(at.y);
                matrices.push();
                matrices.translate(at.x - 0.5 - camera.x, bottom - camera.y, at.z - 0.5 - camera.z);
                BeaconBlockEntityRenderer.renderBeam(matrices, consumers, BeaconBlockEntityRenderer.BEAM_TEXTURE, tickDelta,
                        1.0F, world.getTime(), 0, Math.max(1, world.getTopY() - bottom), BEAM_COLOR, 0.2F, 0.25F);
                matrices.pop();
            }
            consumers.draw();
        });
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
            world.addImportantParticle(ParticleTypes.END_ROD, true, at.x + dx, at.y + HEIGHT, at.z + dz, 0, 0.12, 0);
            if (world.random.nextInt(3) == 0)
                world.addImportantParticle(ParticleTypes.WAX_OFF, true, at.x + dx, at.y + HEIGHT + 0.3, at.z + dz, 0, 0.6, 0);
        }
    }

    /** A new connection: no star known. */
    public static void clear() {
        stars = List.of();
    }
}
