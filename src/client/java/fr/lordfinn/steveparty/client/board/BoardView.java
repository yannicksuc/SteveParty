package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.board.BoardGraph;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.ShopLinkComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.service.ShopStops;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import fr.lordfinn.steveparty.entities.custom.HidingTraderEntity;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.board.TeleportLinks;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The board view: while the Wrench is held (either hand), the links of the board spaces around are drawn like the
 * paths of a Mario Party board: chevrons (the mod's arrow particle) scrolling toward the next space, one colour per
 * branch. Each space shows its distance in steps from the nearest start on a plate cut like the mod's screens (the
 * start on a green one), forks get a gold « ? », dead ends a red « ! » and spaces no start leads to an orange « ! »,
 * gently pulsing. A teleport tile's arrivals are no paths: dashed purple arcs with sparkles riding them (a teleport tile
 * without arrival gets a purple « ! »). Only the holder sees it (client side); the links come from the block entity data the server already
 * sends, no packet needed. Rebuilt twice a second.
 */
public final class BoardView {
    /** Board spaces within this many blocks are shown. */
    public static final int RADIUS = 48;
    private static final int REFRESH_TICKS = 10;
    private static final double LABEL_DISTANCE_SQ = 32 * 32;
    private static final float LABEL_SCALE = 1f / 40f;

    /** Branch colours: the main path, then each new branch at a fork. */
    private static final int[] BRANCHES = {0xFF3A9BFF, 0xFFFFD83D, 0xFF6CCB52, 0xFFF36BAA, 0xFFB983FF, 0xFFF9901D};
    static final int UNREACHED = 0xE0C8C8C8;
    static final int INACTIVE = 0x70A0A0A0;
    static final int BROKEN = 0xFFFF3030;
    /** Teleport arcs: the dashes, and the sparkles riding them. */
    static final int TELEPORT = 0xE0B266FF, TELEPORT_SPARKLE = 0xFFB8F6FF;
    /** Chevrons: size, gap and speed (blocks, blocks per second). */
    private static final double DOT = 0.56, SPACING = 0.72, SPEED = 1.4;

    private static @Nullable BoardGraph graph;
    private static Map<BoardGraph.Edge, Integer> colors = Map.of();
    /** Shop spaces around (Shop Cartridge), with where their merchant is (null: none around). */
    private static List<Shop> shops = List.of();
    /** The Shop Cartridge's yellow. */
    private static final int SHOP = 0xFF000000 | ShopCartridgeItem.COLOR;

    private record Shop(BlockPos pos, boolean linked, @Nullable Vec3d target) {
    }
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
                colors = branchColors(graph);
                shops = shops(client.world, graph);
            }
        });
        WorldRenderEvents.AFTER_ENTITIES.register(BoardView::render);
    }

    /**
     * The shop spaces of the graph, and where their merchant stands: the one chosen with the Wrench (or where he was
     * chosen), else the nearest Hiding Trader around (the client doesn't know which stalls are whose: an estimate).
     */
    private static List<Shop> shops(ClientWorld world, BoardGraph graph) {
        List<Shop> result = new java.util.ArrayList<>();
        for (BoardGraph.Node node : graph.nodes()) {
            if (!(world.getBlockEntity(node.pos()) instanceof BoardSpaceBlockEntity space)) continue;
            ItemStack cartridge = space.getActiveCartridgeItemStack();
            if (!(cartridge.getItem() instanceof ShopCartridgeItem)) continue;
            ShopLinkComponent link = cartridge.get(ModComponents.SHOP_LINK);
            Vec3d at = Vec3d.ofCenter(node.pos());
            Vec3d target = world.getEntitiesByClass(HidingTraderEntity.class, new Box(node.pos()).expand(ShopStops.SHOP_RADIUS),
                            trader -> link == null || trader.getUuid().equals(link.trader())).stream()
                    .min(java.util.Comparator.comparingDouble(trader -> trader.squaredDistanceTo(at)))
                    .map(trader -> trader.getPos().add(0, 0.5, 0))
                    .orElse(link != null ? Vec3d.ofCenter(link.anchor()) : null);
            result.add(new Shop(node.pos(), target != null, target));
        }
        return result;
    }

    /** The current graph (null when the Wrench is not held). */
    static @Nullable BoardGraph graph() {
        return graph;
    }

    static boolean holdsWrench(ClientPlayerEntity player) {
        return player.getMainHandStack().getItem() instanceof WrenchItem || player.getOffHandStack().getItem() instanceof WrenchItem;
    }

    /**
     * The colour of each active link: walking from the starts, a path keeps its colour, and at a fork the first way
     * keeps it while each other way opens a new branch colour.
     */
    private static Map<BoardGraph.Edge, Integer> branchColors(BoardGraph graph) {
        Map<BoardGraph.Edge, Integer> result = new HashMap<>();
        Map<BlockPos, Integer> nodeColor = new HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        for (BoardGraph.Node node : graph.nodes()) {
            if (node.start()) {
                nodeColor.put(node.pos(), 0);
                queue.add(node.pos());
            }
        }
        int next = 1;
        while (!queue.isEmpty()) {
            BoardGraph.Node node = graph.node(queue.poll());
            if (node == null) continue;
            int color = nodeColor.get(node.pos());
            List<BoardGraph.Edge> ways = node.edges().stream().filter(e -> e.active() && e.target() != BoardGraph.Target.BROKEN).toList();
            for (int i = 0; i < ways.size(); i++) {
                BoardGraph.Edge edge = ways.get(i);
                int edgeColor = i == 0 ? color : next++;
                result.put(edge, edgeColor);
                if (!nodeColor.containsKey(edge.to())) {
                    nodeColor.put(edge.to(), edgeColor);
                    queue.add(edge.to());
                }
            }
        }
        return result;
    }

    private static int color(BoardGraph.Edge edge) {
        if (edge.target() == BoardGraph.Target.BROKEN) return BROKEN;
        if (!edge.active()) return INACTIVE;
        Integer branch = colors.get(edge);
        return branch == null ? UNREACHED : BRANCHES[branch % BRANCHES.length];
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
        double time = (world.getTime() + context.tickCounter().getTickDelta(true)) / 20.0;
        double phase = time * SPEED;
        for (BoardGraph.Node node : shown.nodes()) {
            Vec3d from = WrenchOverlay.anchor(world, node.pos());
            for (BoardGraph.Edge edge : node.edges()) {
                Vec3d to = WrenchOverlay.anchor(world, edge.to());
                // Other cartridges of an Advanced Tile: a little higher, dimmed, not moving
                Vec3d lift = edge.active() ? Vec3d.ZERO : new Vec3d(0, 0.06 * (1 + edge.slot() % 4), 0);
                WorldDraw.path(matrices, consumers, camera, from.add(lift), to.add(lift), color(edge), DOT, SPACING,
                        edge.active() ? phase : 0, 0.45, 0.12);
            }
            if (node.teleports() != null) {
                for (BlockPos arrival : node.teleports()) {
                    Vec3d to = WrenchOverlay.anchor(world, arrival);
                    boolean boardSpace = shown.node(arrival) != null || BoardLinks.isBoardSpace(world, arrival);
                    WorldDraw.arc(matrices, consumers, camera, from, to, TeleportLinks.arcHeight(from.distanceTo(to)),
                            boardSpace ? TELEPORT : BROKEN, TELEPORT_SPARKLE, phase, 0.07, 0.3);
                }
            }
        }
        // Shop check points: an emerald path to their shop
        for (Shop shop : shops) {
            if (shop.target() == null) continue;
            WorldDraw.path(matrices, consumers, camera, WrenchOverlay.anchor(world, shop.pos()), shop.target(), SHOP, DOT * 0.8, SPACING,
                    phase, 0.45, 0);
        }
        // Labels from the farthest to the nearest: the nearest ones on top
        List<BoardGraph.Node> labelled = new java.util.ArrayList<>(shown.nodes().stream()
                .filter(node -> WrenchOverlay.anchor(world, node.pos()).squaredDistanceTo(eye) <= LABEL_DISTANCE_SQ).toList());
        labelled.sort(java.util.Comparator.comparingDouble((BoardGraph.Node node) -> WrenchOverlay.anchor(world, node.pos()).squaredDistanceTo(eye)).reversed());
        for (BoardGraph.Node node : labelled) {
            Vec3d from = WrenchOverlay.anchor(world, node.pos());
            double distance = Math.sqrt(from.squaredDistanceTo(eye));
            // Readable from afar: the farther, the bigger (up to 3 times)
            float grow = (float) Math.clamp(distance / 7.0, 1.0, 3.0);
            float scale = LABEL_SCALE * grow;
            labels(matrices, consumers, camera, shown, node, from, scale, time);
            for (Shop shop : shops) {
                if (!shop.pos().equals(node.pos())) continue;
                // Above the other plates of the space: « Shop », orange « Shop ? » while no merchant is around
                Vec3d at = from.add(0, 0.25 + 16 * scale * 2.6, 0);
                WorldDraw.plateLabel(matrices, consumers, camera, at,
                        Text.translatable(shop.linked() ? "hud.steveparty.board.shop" : "hud.steveparty.board.shop_missing"),
                        shop.linked() ? WorldDraw.Plate.GOLD : WorldDraw.Plate.ORANGE, WorldDraw.PLATE_TEXT, scale);
            }
        }
        consumers.draw();
    }

    private static void labels(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, BoardGraph graph,
                               BoardGraph.Node node, Vec3d anchor, float scale, double time) {
        double plate = 16 * scale; // a plate's height in blocks
        Vec3d top = anchor.add(0, 0.25 + plate / 2, 0);
        Integer distance = graph.distance(node.pos());
        if (node.start()) {
            WorldDraw.plateLabel(matrices, consumers, camera, top, Text.translatable("hud.steveparty.board.start"),
                    WorldDraw.Plate.GREEN, WorldDraw.PLATE_TEXT, scale);
        } else if (distance != null) {
            WorldDraw.plateLabel(matrices, consumers, camera, top, Text.literal(Integer.toString(distance)),
                    WorldDraw.Plate.TEAL, WorldDraw.PLATE_TEXT, scale);
        } else if (!graph.hasStart()) {
            WorldDraw.plateLabel(matrices, consumers, camera, top, Text.literal("·"), WorldDraw.Plate.TEAL, WorldDraw.PLATE_TEXT, scale);
        }
        boolean deadEnd = graph.isDeadEnd(node), unreachable = graph.isUnreachable(node);
        if (deadEnd || unreachable) {
            // Gently pulsing, never flashing
            float pulse = 1 + 0.08f * (float) Math.sin(time * 3.0);
            boolean alone = !node.start() && distance == null && graph.hasStart();
            Vec3d at = alone ? top : top.add(0, plate * 1.05, 0);
            WorldDraw.plateLabel(matrices, consumers, camera, at, Text.literal("!"),
                    deadEnd ? WorldDraw.Plate.RED : WorldDraw.Plate.ORANGE, WorldDraw.PLATE_TEXT, scale * pulse);
        }
        if (node.teleportsNowhere()) {
            // A teleport tile sending nowhere: to the left of the number
            float pulse = 1 + 0.08f * (float) Math.sin(time * 3.0);
            org.joml.Vector3f right = new org.joml.Vector3f(1, 0, 0).rotate(camera.getRotation());
            Vec3d beside = top.subtract(right.x() * plate * 1.05, right.y() * plate * 1.05, right.z() * plate * 1.05);
            WorldDraw.plateLabel(matrices, consumers, camera, beside, Text.literal("!"), WorldDraw.Plate.PURPLE, WorldDraw.PLATE_TEXT, scale * pulse);
        }
        if (graph.isFork(node)) {
            // The junction marker, beside the number (to the right as seen from the camera)
            org.joml.Vector3f right = new org.joml.Vector3f(1, 0, 0).rotate(camera.getRotation());
            Vec3d beside = top.add(right.x() * plate * 1.05, right.y() * plate * 1.05, right.z() * plate * 1.05);
            WorldDraw.plateLabel(matrices, consumers, camera, beside, Text.literal("?"), WorldDraw.Plate.GOLD, WorldDraw.PLATE_TEXT, scale);
        }
    }

    /** For the HUD: the spaces around, dead ends and spaces no start leads to. */
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
