package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.board.BoardGraph;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * The board view: while the Wrench is held (either hand), every link of the board spaces around is drawn as an arrow,
 * and each board space shows its distance in steps from the nearest start, coloured: start green, dead end red, no
 * start leads there orange, fork purple. Only the holder sees it (client side); the links come from the block entity
 * data the server already sends, no packet needed. Rebuilt twice a second.
 */
public final class BoardView {
    /** Board spaces within this many blocks are shown. */
    public static final int RADIUS = 48;
    private static final int REFRESH_TICKS = 10;
    private static final double LABEL_DISTANCE_SQ = 32 * 32;
    private static final float LABEL_SCALE = 1f / 48f;

    static final int LINK = 0xE0FFFFFF;
    static final int LINK_INACTIVE = 0x70A0A0A0;
    static final int BROKEN = 0xFFFF3030;
    static final int START = 0xFF55FF55;
    static final int DEAD_END = 0xFFFF5050;
    static final int UNREACHABLE = 0xFFFFA030;
    static final int FORK = 0xFFD070FF;
    static final int NORMAL = 0xFFFFFFFF;

    private static @Nullable BoardGraph graph;
    private static int age;

    private BoardView() {
    }

    static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null || !holdsWrench(client.player)) {
                graph = null;
                return;
            }
            if (graph == null || ++age >= REFRESH_TICKS) {
                age = 0;
                graph = BoardGraph.collect(client.world, client.player.getBlockPos(), RADIUS);
            }
        });
        WorldRenderEvents.AFTER_ENTITIES.register(BoardView::render);
    }

    /** The current graph (null when the Wrench is not held). */
    static @Nullable BoardGraph graph() {
        return graph;
    }

    static boolean holdsWrench(ClientPlayerEntity player) {
        return player.getMainHandStack().getItem() instanceof WrenchItem || player.getOffHandStack().getItem() instanceof WrenchItem;
    }

    private static void render(WorldRenderContext context) {
        BoardGraph shown = graph;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        MatrixStack matrices = context.matrixStack();
        if (shown == null || world == null || matrices == null || client.player == null) return;
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        Camera camera = context.camera();
        Vec3d eye = camera.getPos();
        for (BoardGraph.Node node : shown.nodes()) {
            Vec3d from = WrenchOverlay.anchor(world, node.pos());
            for (BoardGraph.Edge edge : node.edges()) {
                Vec3d to = WrenchOverlay.anchor(world, edge.to());
                int color = edge.target() == BoardGraph.Target.BROKEN ? BROKEN : edge.active() ? LINK : LINK_INACTIVE;
                // Several cartridges linking the same way: shifted a little so both show
                Vec3d shift = edge.active() ? Vec3d.ZERO : new Vec3d(0, 0.08 * (1 + edge.slot() % 4), 0);
                WorldDraw.arrow(matrices, consumers, camera, from.add(shift), to.add(shift), color, 0.62);
            }
            if (from.squaredDistanceTo(eye) > LABEL_DISTANCE_SQ) continue;
            WorldDraw.label(matrices, consumers, camera, from.add(0, 0.55, 0), label(shown, node), color(shown, node), LABEL_SCALE);
        }
        consumers.draw();
    }

    private static Text label(BoardGraph graph, BoardGraph.Node node) {
        Integer distance = graph.distance(node.pos());
        String number = distance != null ? Integer.toString(distance) : graph.hasStart() ? "?" : "·";
        if (graph.isDeadEnd(node)) return Text.translatable("hud.steveparty.board.dead_end", number);
        if (graph.isFork(node)) return Text.translatable("hud.steveparty.board.fork", number, node.activeBoardSpaceLinks());
        if (node.start()) return Text.translatable("hud.steveparty.board.start");
        return Text.literal(number);
    }

    private static int color(BoardGraph graph, BoardGraph.Node node) {
        if (graph.isDeadEnd(node)) return DEAD_END;
        if (node.start()) return START;
        if (graph.isUnreachable(node)) return UNREACHABLE;
        if (graph.isFork(node)) return FORK;
        return NORMAL;
    }

    /** For the HUD: dead ends and board spaces no start leads to, around. */
    static int[] counts() {
        BoardGraph shown = graph;
        if (shown == null) return new int[]{0, 0, 0};
        int deadEnds = 0, unreachable = 0;
        for (BoardGraph.Node node : shown.nodes()) {
            if (shown.isDeadEnd(node)) deadEnds++;
            if (shown.isUnreachable(node)) unreachable++;
        }
        return new int[]{shown.nodes().size(), deadEnds, unreachable};
    }
}
