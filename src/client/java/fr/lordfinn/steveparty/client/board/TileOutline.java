package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TilePartBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileShape;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

/**
 * The block outline of an aimed tile is its slab as it is drawn ({@link TileShape}: overhang, 45 degree turn, slope),
 * not its block's box; the vanilla outline is skipped for it.
 */
public final class TileOutline {
    private static final int[][] EDGES = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    private TileOutline() {
    }

    public static void initialize() {
        WorldRenderEvents.BLOCK_OUTLINE.register((context, outline) -> {
            BlockState state = outline.blockState();
            if (!(state.getBlock() instanceof ATileBlock) && !(state.getBlock() instanceof TilePartBlock)) return true;
            ClientWorld world = MinecraftClient.getInstance().world;
            if (world == null) return true;
            TileShape shape = TileShape.of(world, BoardSpaces.resolve(world, outline.blockPos()));
            if (shape == null) return true;
            draw(context, outline.vertexConsumer(), shape, new Vec3d(outline.cameraX(), outline.cameraY(), outline.cameraZ()));
            return false;
        });
    }

    private static void draw(WorldRenderContext context, VertexConsumer lines, TileShape shape, Vec3d cam) {
        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;
        MatrixStack.Entry entry = matrices.peek();
        Vec3d[] c = shape.corners();
        for (int[] edge : EDGES) {
            Vec3d a = c[edge[0]].subtract(cam), b = c[edge[1]].subtract(cam);
            Vec3d n = b.subtract(a).normalize();
            // The vanilla outline's colour
            lines.vertex(entry, (float) a.x, (float) a.y, (float) a.z).color(0, 0, 0, 0.4f).normal(entry, (float) n.x, (float) n.y, (float) n.z);
            lines.vertex(entry, (float) b.x, (float) b.y, (float) b.z).color(0, 0, 0, 0.4f).normal(entry, (float) n.x, (float) n.y, (float) n.z);
        }
    }
}
