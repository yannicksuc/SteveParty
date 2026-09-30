package fr.lordfinn.steveparty.client.pipe;

import fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeGeometry;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeShape;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a traveller goes by, the pipe swells then settles back with a small cartoon bounce: the outside walls of each
 * piece of pipe it goes through, drawn again a little wider, sized from how long ago it went by (from its carrier's
 * path: nothing is sent). Only while something travels. Its own walls (same quads, same texture), so that the swelling
 * looks like the pipe whatever its kind and follows it round bends and junctions: on a straight length a ring
 * {@link #LENGTH} long, elsewhere the whole piece (elbow, junction) swollen from its middle.
 */
public final class PipeBulgeRenderer {
    /** How much wider the pipe gets as the traveller goes by. */
    private static final float SWELL = 0.28f;
    /** Ticks it starts swelling before the traveller gets there, and settles after. */
    private static final float BEFORE = 3, AFTER = 14;
    /** Length of the ring on a straight length (pixels). */
    private static final float LENGTH = 13;
    /** Outside wall quads (corners, then texture coordinates) of each kind and shape key. */
    private static final Map<Integer, List<float[][][]>> WALLS = new ConcurrentHashMap<>();

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
        RenderLayer layer = RenderLayer.getItemEntityTranslucentCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        VertexConsumer buffer = consumers.getBuffer(layer);
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
                BlockPos pos = BlockPos.ofFloored(points.get(i));
                BlockState state = world.getBlockState(pos);
                if (!(state.getBlock() instanceof PipeBlock pipe)) continue;
                Vec3d way = points.get(i + 1).subtract(points.get(i - 1));
                Direction.Axis axis = Direction.getFacing(way.x, way.y, way.z).getAxis();
                Sprite sprite = client.getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)
                        .apply(PipeModelPlugin.texture(pipe.kind(), pipe.color(), PipeGeometry.OUTER));
                int light = WorldRenderer.getLightmapCoordinates(world, pos);
                matrices.push();
                matrices.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
                swollen(matrices.peek(), buffer, sprite, pipe.kind(), PipeShape.key(state), axis, swell, light);
                matrices.pop();
                drawn = true;
            }
        }
        if (drawn) consumers.draw(layer);
    }

    /** Are all the openings of this piece of pipe (joints and ends) on {@code axis}: a straight length along it? */
    static boolean straight(int key, Direction.Axis axis) {
        PipeShape.Face[] faces = PipeShape.faces(PipeShape.maskOf(key), PipeShape.solidOf(key));
        for (Direction dir : Direction.values()) {
            PipeShape.Face face = faces[dir.ordinal()];
            boolean open = face == PipeShape.Face.CONNECTED || face == PipeShape.Face.MOUTH || face == PipeShape.Face.CAPPED;
            if (open && dir.getAxis() != axis) return false;
        }
        return true;
    }

    /** The outside walls of a pipe, {@code 1 + swell} times as wide from its middle (a ring on a straight length). */
    private static void swollen(MatrixStack.Entry entry, VertexConsumer buffer, Sprite sprite, PipeKind kind, int key, Direction.Axis axis,
                                float swell, int light) {
        List<float[][][]> walls = WALLS.computeIfAbsent(kind.ordinal() * PipeShape.KEYS + key, k -> {
            List<float[][][]> list = new ArrayList<>();
            PipeGeometry.build(key, kind, (facing, corners, uvs, part, cull) -> {
                if (part != PipeGeometry.OUTER) return;
                float[][] normal = {{facing.getOffsetX(), facing.getOffsetY(), facing.getOffsetZ()}};
                list.add(new float[][][]{corners, uvs, normal});
            });
            return List.copyOf(list);
        });
        boolean ring = straight(key, axis);
        float[] scale = new float[3];
        for (int i = 0; i < 3; i++) scale[i] = ring && i == axis.ordinal() ? LENGTH / 16 : 1 + swell;
        for (float[][][] wall : walls) {
            float[] normal = wall[2][0];
            for (int v = 0; v < 4; v++) {
                float[] corner = wall[0][v];
                buffer.vertex(entry, (8 + (corner[0] - 8) * scale[0]) / 16, (8 + (corner[1] - 8) * scale[1]) / 16, (8 + (corner[2] - 8) * scale[2]) / 16)
                        .color(0xFFFFFFFF)
                        .texture(sprite.getFrameU(wall[1][v][0] / 16), sprite.getFrameV(wall[1][v][1] / 16))
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(light)
                        .normal(entry, normal[0], normal[1], normal[2]);
            }
        }
    }
}
