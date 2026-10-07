package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.ShopLinkComponent;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.service.ShopStops;
import net.minecraft.item.ItemStack;
import fr.lordfinn.steveparty.board.BoardGraph;
import fr.lordfinn.steveparty.board.BoardRevision;
import fr.lordfinn.steveparty.board.ExplorerHelmet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.inventory.Inventory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The board view: while the Tile Linker Brush is held (either hand), or the Explorer's Helmet is worn
 * with its lamp lit (see {@link ExplorerHelmet}; the details it adds are {@link HelmetView}'s), the links of the board spaces around are drawn like the
 * paths of a Mario Party board: chevrons (the mod's arrow particle) scrolling toward the next space, one colour per
 * branch. Each space shows its distance in steps from the nearest start on a plate cut like the mod's screens (the
 * start on a green one), forks get a gold « ? », dead ends a red « ! » and spaces no start leads to an orange « ! »,
 * gently pulsing. The teleport tiles of a network are no paths: dashed arcs in the network's colour with sparkles riding
 * them join them one to the next (a teleport tile alone in its network gets a purple « ! »). Only the holder sees it (client side); the links come from the block entity data the server already
 * sends, no packet needed.
 * <p>
 * The graph is rebuilt only when the board may have changed ({@link BoardRevision}: board space or router data or
 * block state received, such a block entity or a chest loaded or removed) or when the holder moved a few blocks, with
 * a slow safety refresh; everything drawn (anchors, colours, labels, bounds) is computed then, so a frame reads no
 * block and allocates nothing per link. Only where the merchants of the shop spaces stand (entities move) is looked
 * up again twice a second, and only when there are shop spaces around.
 */
public final class BoardView {
    /** Board spaces within this many blocks are shown. */
    public static final int RADIUS = 48;
    /** The holder moved this far (blocks, Manhattan) from where the graph was built: rebuilt around them. */
    private static final int MOVE_REBUILD = 4;
    /** Rebuilt anyway after this many ticks (what no event reports, e.g. a chunk of a link target loaded). */
    private static final int SAFETY_REFRESH_TICKS = 100;
    private static final double LABEL_DISTANCE_SQ = 32 * 32;
    private static final float LABEL_SCALE = 1f / 40f;
    private static final Text WARNING = Text.literal("!"), FORK = Text.literal("?");

    /** Branch colours: the main path, then each new branch at a fork. */
    private static final int[] BRANCHES = {0xFF3A9BFF, 0xFFFFD83D, 0xFF6CCB52, 0xFFF36BAA, 0xFFB983FF, 0xFFF9901D};
    static final int UNREACHED = 0xE0C8C8C8;
    static final int INACTIVE = 0x70A0A0A0;
    static final int BROKEN = 0xFFFF3030;
    /** Teleport arcs: the sparkles riding them (the dashes are in the network's colour). */
    static final int TELEPORT_SPARKLE = 0xFFB8F6FF;
    /** Chevrons: size, gap and speed (blocks, blocks per second). */
    private static final double DOT = 0.56, SPACING = 0.72, SPEED = 1.4;
    /** The Shop Cartridge's lime green. */
    private static final int SHOP = 0xFF000000 | ShopCartridgeItem.COLOR;
    /** Where the merchants of the shop spaces are is looked up again every this many ticks. */
    private static final int SHOP_REFRESH_TICKS = 10;
    private static final Text SHOP_LABEL = Text.translatable("hud.steveparty.board.shop"),
            SHOP_MISSING_LABEL = Text.translatable("hud.steveparty.board.shop_missing");

    /** A link as drawn: its ends (lifted for inactive ones), colour, and bounds for the frustum test. */
    private record DrawnEdge(double ax, double ay, double az, double bx, double by, double bz, int color, boolean active, Box bounds) {
    }

    /** An arc between two teleport tiles of a network as drawn: sampled once, in the network's colour. */
    private record DrawnArc(WorldDraw.Arc arc, int color) {
    }

    /** A space's labels as drawn: where, what, and the reusable distance used to sort them each frame. */
    private static final class Label {
        final Vec3d anchor;
        final Box bounds;
        final @Nullable Text number;
        final WorldDraw.Plate numberPlate;
        final boolean deadEnd, unreachable, fork, alone;
        double distanceSq;
        /** A shop space: its « Shop » plate, gold when its merchant is around ({@link #shopLinked}). */
        boolean shop, shopLinked;
        /** A teleport tile alone in its network: a purple « ! ». */
        boolean teleportAlone;

        Label(Vec3d anchor, @Nullable Text number, WorldDraw.Plate numberPlate, boolean deadEnd, boolean unreachable, boolean fork, boolean alone) {
            this.anchor = anchor;
            this.bounds = new Box(anchor.x - 1.5, anchor.y - 0.5, anchor.z - 1.5, anchor.x + 1.5, anchor.y + 4, anchor.z + 1.5);
            this.number = number;
            this.numberPlate = numberPlate;
            this.deadEnd = deadEnd;
            this.unreachable = unreachable;
            this.fork = fork;
            this.alone = alone;
        }
    }

    private static @Nullable BoardGraph graph;
    private static List<DrawnEdge> edges = List.of();
    private static List<Label> labels = List.of();
    private static List<DrawnArc> arcs = List.of();
    /** The shop spaces around (Shop Cartridge), and where their merchant is (null: none around). */
    private static List<ShopSpace> shops = List.of();
    private static int shopAge;

    private static final class ShopSpace {
        final BlockPos pos;
        final Vec3d anchor;
        final @Nullable ShopLinkComponent link;
        final Label label;
        @Nullable Vec3d target;
        @Nullable Box bounds;

        ShopSpace(BlockPos pos, Vec3d anchor, @Nullable ShopLinkComponent link, Label label) {
            this.pos = pos;
            this.anchor = anchor;
            this.link = link;
            this.label = label;
        }
    }
    /** The labels near enough to be drawn this frame, reused from frame to frame. */
    private static final List<Label> SHOWN = new ArrayList<>();
    private static final Comparator<Label> FARTHEST_FIRST = (a, b) -> Double.compare(b.distanceSq, a.distanceSq);
    private static int[] counts = {0, 0, 0};
    private static @Nullable BlockPos builtAt;
    private static long builtRevision = -1;
    private static int age;
    /** What the player sees (updated each tick). */
    private static ExplorerHelmet.View view = ExplorerHelmet.View.NONE;

    private BoardView() {
    }

    static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            view = client.player == null || client.world == null ? ExplorerHelmet.View.NONE : ExplorerHelmet.view(client.player);
            if (!view.shown()) {
                clear();
                return;
            }
            BlockPos at = client.player.getBlockPos();
            if (graph == null || builtRevision != BoardRevision.client() || builtAt == null
                    || builtAt.getManhattanDistance(at) >= MOVE_REBUILD || ++age >= SAFETY_REFRESH_TICKS) {
                build(client.world, at);
            } else if (!shops.isEmpty() && ++shopAge >= SHOP_REFRESH_TICKS) {
                refreshShops(client.world);
            }
        });
        // Board spaces and routers (their links), chests (inventory tiles) appearing or going away
        ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) -> onBlockEntity(blockEntity));
        ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, world) -> onBlockEntity(blockEntity));
        WorldRenderEvents.AFTER_ENTITIES.register(BoardView::render);
    }

    private static void onBlockEntity(BlockEntity blockEntity) {
        if (blockEntity instanceof CartridgeContainerBlockEntity || blockEntity instanceof Inventory) BoardRevision.changedOnClient();
    }

    private static void clear() {
        if (graph == null) return;
        graph = null;
        edges = List.of();
        labels = List.of();
        arcs = List.of();
        shops = List.of();
        SHOWN.clear();
        counts = new int[]{0, 0, 0};
        builtAt = null;
    }

    private static void build(ClientWorld world, BlockPos at) {
        age = 0;
        builtAt = at.toImmutable();
        builtRevision = BoardRevision.client();
        BoardGraph built = BoardGraph.collect(world, at, RADIUS);
        Map<BoardGraph.Edge, Integer> colors = branchColors(built);
        Map<BlockPos, Vec3d> anchors = new HashMap<>();
        List<DrawnEdge> drawnEdges = new ArrayList<>();
        List<Label> builtLabels = new ArrayList<>();
        List<DrawnArc> drawnArcs = new ArrayList<>();
        List<ShopSpace> shopSpaces = new ArrayList<>();
        int deadEnds = 0, unreachable = 0;
        boolean hasStart = built.hasStart();
        for (BoardGraph.Node node : built.nodes()) {
            Vec3d from = anchors.computeIfAbsent(node.pos(), pos -> BrushOverlay.anchor(world, pos));
            for (BoardGraph.Edge edge : node.edges()) {
                Vec3d to = anchors.computeIfAbsent(edge.to(), pos -> BrushOverlay.anchor(world, pos));
                // Other cartridges of an Advanced Tile: a little higher, dimmed, not moving
                double lift = edge.active() ? 0 : 0.06 * (1 + edge.slot() % 4);
                Box bounds = new Box(from.x, from.y + lift, from.z, to.x, to.y + lift, to.z).expand(0.5);
                drawnEdges.add(new DrawnEdge(from.x, from.y + lift, from.z, to.x, to.y + lift, to.z, color(edge, colors), edge.active(), bounds));
            }
            if (node.teleportNetwork() != null) {
                // Each tile of a network to the next one (the last back to the first when they are 3 or more)
                List<BlockPos> network = built.teleportNetworkOf(node.pos());
                int index = network.indexOf(node.pos());
                if (index >= 0 && network.size() >= 2 && (index + 1 < network.size() || network.size() >= 3)) {
                    BlockPos next = network.get((index + 1) % network.size());
                    Vec3d to = anchors.computeIfAbsent(next, pos -> BrushOverlay.anchor(world, pos));
                    drawnArcs.add(new DrawnArc(new WorldDraw.Arc(from, to, arcHeight(from.distanceTo(to))),
                            0xE0000000 | node.teleportNetwork().color()));
                }
            }
            Integer distance = built.distance(node.pos());
            Text number = null;
            WorldDraw.Plate plate = WorldDraw.Plate.TEAL;
            if (node.start()) {
                number = Text.translatable("hud.steveparty.board.start");
                plate = WorldDraw.Plate.GREEN;
            } else if (distance != null) {
                number = Text.literal(Integer.toString(distance));
            } else if (!hasStart) {
                number = Text.literal("·");
            }
            boolean deadEnd = built.isDeadEnd(node), notReached = built.isUnreachable(node);
            if (deadEnd) deadEnds++;
            if (notReached) unreachable++;
            boolean alone = !node.start() && distance == null && hasStart;
            Label label = new Label(from, number, plate, deadEnd, notReached, built.isFork(node), alone);
            label.teleportAlone = built.isTeleportAlone(node);
            builtLabels.add(label);
            if (world.getBlockEntity(node.pos()) instanceof BoardSpaceBlockEntity space) {
                ItemStack cartridge = space.getActiveCartridgeItemStack();
                if (cartridge.getItem() instanceof ShopCartridgeItem) {
                    label.shop = true;
                    shopSpaces.add(new ShopSpace(node.pos(), from, cartridge.get(ModComponents.SHOP_LINK), label));
                }
            }
        }
        graph = built;
        edges = drawnEdges;
        labels = builtLabels;
        arcs = drawnArcs;
        shops = shopSpaces;
        refreshShops(world);
        counts = new int[]{built.nodes().size(), deadEnds, unreachable};
    }

    /**
     * Where the merchant of each shop space stands: the one chosen with the Tile Linker Brush (or where he was chosen), else the
     * nearest Boxed Trader around (the client doesn't know which stalls are whose: an estimate).
     */
    /** How high the arc between two teleport tiles {@code length} blocks apart goes. */
    private static double arcHeight(double length) {
        return Math.clamp(0.6 + 0.2 * length, 0.8, 4.0);
    }

    private static void refreshShops(ClientWorld world) {
        shopAge = 0;
        for (ShopSpace shop : shops) {
            ShopLinkComponent link = shop.link;
            Vec3d at = Vec3d.ofCenter(shop.pos);
            BoxedTraderEntity nearest = null;
            double best = Double.MAX_VALUE;
            for (BoxedTraderEntity trader : world.getEntitiesByClass(BoxedTraderEntity.class, new Box(shop.pos).expand(ShopStops.SHOP_RADIUS),
                    trader -> link == null || trader.getUuid().equals(link.trader()))) {
                double distance = trader.squaredDistanceTo(at);
                if (distance < best) {
                    best = distance;
                    nearest = trader;
                }
            }
            shop.target = nearest != null ? nearest.getPos().add(0, 0.5, 0) : link != null ? Vec3d.ofCenter(link.anchor()) : null;
            shop.bounds = shop.target == null ? null : new Box(shop.anchor, shop.target).expand(0.5);
            shop.label.shopLinked = shop.target != null;
        }
    }

    /** The current graph (null when the board view is not shown). */
    static @Nullable BoardGraph graph() {
        return graph;
    }

    /** What the player sees of the board this tick. */
    static ExplorerHelmet.View view() {
        return view;
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

    private static int color(BoardGraph.Edge edge, Map<BoardGraph.Edge, Integer> colors) {
        if (edge.target() == BoardGraph.Target.BROKEN) return BROKEN;
        if (!edge.active()) return INACTIVE;
        Integer branch = colors.get(edge);
        return branch == null ? UNREACHED : BRANCHES[branch % BRANCHES.length];
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrixStack();
        if (graph == null || client.world == null || matrices == null || client.player == null) return;
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        Camera camera = context.camera();
        Frustum frustum = context.frustum();
        Vec3d eye = camera.getPos();
        double time = (client.world.getTime() + context.tickCounter().getTickDelta(true)) / 20.0;
        double phase = time * SPEED;
        for (DrawnEdge edge : edges) {
            if (frustum != null && !frustum.isVisible(edge.bounds())) continue;
            WorldDraw.path(matrices, consumers, camera, edge.ax(), edge.ay(), edge.az(), edge.bx(), edge.by(), edge.bz(),
                    edge.color(), DOT, SPACING, edge.active() ? phase : 0, 0.45, 0.12);
        }
        for (DrawnArc arc : arcs) {
            if (frustum != null && !frustum.isVisible(arc.arc().bounds)) continue;
            WorldDraw.arc(matrices, consumers, camera, arc.arc(), arc.color(), TELEPORT_SPARKLE, phase, 0.07, 0.3);
        }
        // Shop check points: a path to their shop
        for (ShopSpace shop : shops) {
            Vec3d target = shop.target;
            if (target == null || (frustum != null && shop.bounds != null && !frustum.isVisible(shop.bounds))) continue;
            WorldDraw.path(matrices, consumers, camera, shop.anchor.x, shop.anchor.y, shop.anchor.z, target.x, target.y, target.z,
                    SHOP, DOT * 0.8, SPACING, phase, 0.45, 0);
        }
        // Labels from the farthest to the nearest: the nearest ones on top
        SHOWN.clear();
        for (Label label : labels) {
            label.distanceSq = label.anchor.squaredDistanceTo(eye);
            if (label.distanceSq <= LABEL_DISTANCE_SQ && (frustum == null || frustum.isVisible(label.bounds))) SHOWN.add(label);
        }
        SHOWN.sort(FARTHEST_FIRST);
        for (Label label : SHOWN) {
            // Readable from afar: the farther, the bigger (up to 3 times)
            float grow = (float) Math.clamp(Math.sqrt(label.distanceSq) / 7.0, 1.0, 3.0);
            labels(matrices, consumers, camera, label, LABEL_SCALE * grow, time);
        }
        consumers.draw();
    }

    private static void labels(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Label label, float scale, double time) {
        double plate = 16 * scale; // a plate's height in blocks
        Vec3d top = label.anchor.add(0, 0.25 + plate / 2, 0);
        if (label.number != null) {
            WorldDraw.plateLabel(matrices, consumers, camera, top, label.number, label.numberPlate, WorldDraw.PLATE_TEXT, scale);
        }
        if (label.deadEnd || label.unreachable) {
            // Gently pulsing, never flashing
            float pulse = 1 + 0.08f * (float) Math.sin(time * 3.0);
            Vec3d at = label.alone ? top : top.add(0, plate * 1.05, 0);
            WorldDraw.plateLabel(matrices, consumers, camera, at, WARNING,
                    label.deadEnd ? WorldDraw.Plate.RED : WorldDraw.Plate.ORANGE, WorldDraw.PLATE_TEXT, scale * pulse);
        }
        if (label.teleportAlone) {
            // A teleport tile alone in its network: to the left of the number
            float pulse = 1 + 0.08f * (float) Math.sin(time * 3.0);
            org.joml.Vector3f right = new org.joml.Vector3f(1, 0, 0).rotate(camera.getRotation());
            Vec3d beside = top.subtract(right.x() * plate * 1.05, right.y() * plate * 1.05, right.z() * plate * 1.05);
            WorldDraw.plateLabel(matrices, consumers, camera, beside, WARNING, WorldDraw.Plate.PURPLE, WorldDraw.PLATE_TEXT, scale * pulse);
        }
        if (label.fork) {
            // The junction marker, beside the number (to the right as seen from the camera)
            org.joml.Vector3f right = new org.joml.Vector3f(1, 0, 0).rotate(camera.getRotation());
            Vec3d beside = top.add(right.x() * plate * 1.05, right.y() * plate * 1.05, right.z() * plate * 1.05);
            WorldDraw.plateLabel(matrices, consumers, camera, beside, FORK, WorldDraw.Plate.GOLD, WorldDraw.PLATE_TEXT, scale);
        }
        if (label.shop) {
            // Above the other plates of the space: « Shop », orange « Shop ? » while no merchant is around
            Vec3d at = label.anchor.add(0, 0.25 + 16 * scale * 2.6, 0);
            WorldDraw.plateLabel(matrices, consumers, camera, at, label.shopLinked ? SHOP_LABEL : SHOP_MISSING_LABEL,
                    label.shopLinked ? WorldDraw.Plate.GOLD : WorldDraw.Plate.ORANGE, WorldDraw.PLATE_TEXT, scale);
        }
    }

    /** For the HUD: the spaces around, dead ends and spaces no start leads to (counted when the graph is built). */
    static int[] counts() {
        return counts;
    }
}
