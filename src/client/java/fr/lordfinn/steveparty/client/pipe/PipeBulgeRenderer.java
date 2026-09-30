package fr.lordfinn.steveparty.client.pipe;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock;
import fr.lordfinn.steveparty.entities.custom.PipeCarrierEntity;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Where a traveller goes by, the pipe swells then settles back with a small cartoon bounce: a ring of the pipe's wall,
 * a little wider than the pipe, drawn round each piece of pipe it goes through, sized from how long ago it went by
 * (from its carrier's path: nothing is sent). Only while something travels.
 */
public final class PipeBulgeRenderer {
    /** How much wider the pipe gets as the traveller goes by. */
    private static final float SWELL = 0.28f;
    /** Ticks it starts swelling before the traveller gets there, and settles after. */
    private static final float BEFORE = 3, AFTER = 14;

    private PipeBulgeRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(PipeBulgeRenderer::render);
    }

    /** How much wider the pipe is {@code ticks} ticks after the traveller went by (negative: before). */
    static float swell(float ticks) {
        if (ticks < -BEFORE || ticks > AFTER) return 0;
        if (ticks < 0) {
            float t = 1 + ticks / BEFORE;
            return SWELL * t * t;
        }
        return (float) (SWELL * Math.cos(ticks * 0.75) * Math.exp(-ticks * 0.28));
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        MatrixStack matrices = context.matrixStack();
        if (world == null || matrices == null || PipeCarrierEntity.CLIENT_CARRIERS.isEmpty()) return;
        PipeCarrierEntity.CLIENT_CARRIERS.removeIf(carrier -> carrier.isRemoved() || carrier.getWorld() != world);
        float tickDelta = context.tickCounter().getTickDelta(false);
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
        Vec3d camera = context.camera().getPos();
        boolean drawn = false;
        for (PipeCarrierEntity carrier : PipeCarrierEntity.CLIENT_CARRIERS) {
            List<Vec3d> points = carrier.points();
            double speed = carrier.speed();
            if (points.size() < 3 || speed <= 0) continue;
            double travelled = Math.min(carrier.travelled() + speed * tickDelta, carrier.length());
            for (int i = 1; i < points.size() - 1; i++) {
                float swell = swell((float) ((travelled - carrier.lengthTo(i)) / speed));
                if (swell <= 0.005f) continue;
                Vec3d center = points.get(i);
                BlockPos pos = BlockPos.ofFloored(center);
                BlockState state = world.getBlockState(pos);
                if (!(state.getBlock() instanceof PipeBlock pipe)) continue;
                Vec3d way = points.get(i + 1).subtract(points.get(i - 1));
                Direction.Axis axis = Direction.getFacing(way.x, way.y, way.z).getAxis();
                Sprite sprite = client.getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)
                        .apply(PipeModelPlugin.bodyTexture(pipe.kind(), ModBlocks.COLORS[pipe.color()], "body"));
                int light = WorldRenderer.getLightmapCoordinates(world, pos);
                matrices.push();
                matrices.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
                ring(matrices.peek(), buffer, sprite, axis, 7 / 16f * (1 + swell), 6.5f / 16f, light);
                matrices.pop();
                drawn = true;
            }
        }
        if (drawn) consumers.draw(RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
    }

    /** The 4 walls of a square tube along {@code axis}, {@code half} across and {@code length} long each way. */
    private static void ring(MatrixStack.Entry entry, VertexConsumer buffer, Sprite sprite, Direction.Axis axis, float half, float length, int light) {
        int a = axis.ordinal(), b = (a + 1) % 3, c = (a + 2) % 3;
        float u0 = sprite.getMinU() + (sprite.getMaxU() - sprite.getMinU()) / 16f, u1 = sprite.getMaxU() - (sprite.getMaxU() - sprite.getMinU()) / 16f;
        float v0 = sprite.getMinV(), v1 = sprite.getMaxV();
        for (int side : new int[]{b, c}) {
            int across = side == b ? c : b;
            for (float sign : new float[]{-1, 1}) {
                float[][] corners = new float[4][3];
                float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
                float[] alongs = {-length, length, length, -length};
                float[] acrosses = {-half, -half, half, half};
                float[] normal = new float[3];
                normal[side] = sign;
                for (int i = 0; i < 4; i++) {
                    corners[i][side] = sign * half;
                    corners[i][a] = alongs[i];
                    corners[i][across] = acrosses[i];
                    buffer.vertex(entry, corners[i][0], corners[i][1], corners[i][2])
                            .color(0xFFFFFFFF)
                            .texture(uv[i][0], uv[i][1])
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(light)
                            .normal(entry, normal[0], normal[1], normal[2]);
                }
            }
        }
    }
}
