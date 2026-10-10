package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeSpawnMarker;
import fr.lordfinn.steveparty.board.CartridgeLinks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.board.BoardGraph;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.board.BoardText;
import fr.lordfinn.steveparty.board.BrushAim;
import fr.lordfinn.steveparty.board.ExplorerHelmet;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.ShopLinkComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.utils.Argb;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What the Explorer's Helmet adds to the board view (see {@link BoardView}): the details of the spaces, on small plates
 * stacked above their step plate. The space aimed at gets them all (active cartridge and its colour, active slot and
 * the redstone power of a space with several slots, cartridges held, links out and in, check point, chests of an
 * inventory space, shop) with its destinations framed in green, the spaces leading to it in blue and its chests in gold;
 * the spaces within {@link #DETAIL_RADIUS} blocks a short summary (cartridge, links); the others only the board view.
 * <p>
 * Built at most every {@link #REBUILD_TICKS} ticks, or when the aimed space, the board or the player's block changes:
 * a frame only draws what was built (at most {@link #MAX_DETAILED} spaces, {@link #MAX_FRAMES} frames).
 * <p>
 * Its key ({@code key.steveparty.explorer_helmet_lamp}, G by default: free in vanilla and in the mod) switches the
 * helmet's lamp, and the view with it, without taking the helmet off.
 */
public final class HelmetView {
    /** Spaces this close (blocks) get their summary. */
    static final int DETAIL_RADIUS = 8;
    private static final double DETAIL_RADIUS_SQ = DETAIL_RADIUS * DETAIL_RADIUS;
    /** At most this many spaces get details (the aimed one first, then the nearest). */
    static final int MAX_DETAILED = 10;
    /** At most this many frames around the aimed space's destinations, sources and chests. */
    static final int MAX_FRAMES = 24;
    private static final int REBUILD_TICKS = 10;
    private static final float LABEL_SCALE = 1f / 40f, DETAIL_SCALE = 0.8f;
    static final int OUT = 0xFF4CFF4C, IN = 0xFF3A9BFF, CHEST = 0xFFFFD83D, MARKER = 0xFF000000 | CartridgeLinks.SPAWN_COLOR;

    private record Line(Text text, WorldDraw.Plate plate) {
    }

    /** A space's details as drawn: its anchor (where the board view's labels are), and its lines from top to bottom. */
    private record Detail(Vec3d anchor, Box bounds, List<Line> lines, boolean shop) {
    }

    private static KeyBinding lampKey;
    private static List<Detail> details = List.of();
    private static List<BlockPos> outFrames = List.of(), inFrames = List.of(), chestFrames = List.of(), markerFrames = List.of();
    /** Per board graph: the spaces linked to each space, the spaces a router drives. */
    private static @Nullable BoardGraph indexed;
    private static Map<BlockPos, List<BlockPos>> incoming = Map.of();
    private static Set<BlockPos> routed = Set.of();
    private static @Nullable BlockPos aimed, builtAt;
    private static int age;

    private HelmetView() {
    }

    static void initialize() {
        lampKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.steveparty.explorer_helmet_lamp",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.steveparty"));
        ClientTickEvents.END_CLIENT_TICK.register(HelmetView::tick);
        // After the board view's labels: the details on top of them
        WorldRenderEvents.AFTER_ENTITIES.register(HelmetView::renderDetails);
        // Like the brush's frames: after the block entities (see BrushOverlay)
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(HelmetView::renderFrames);
    }

    private static void tick(MinecraftClient client) {
        while (lampKey.wasPressed()) {
            if (client.player == null) continue;
            if (!ExplorerHelmet.wears(client.player)) {
                client.player.sendMessage(Text.translatable("message.steveparty.explorer_helmet.not_worn"), true);
            } else if (ClientPlayNetworking.canSend(ExplorerHelmet.ToggleLamp.ID)) {
                ClientPlayNetworking.send(new ExplorerHelmet.ToggleLamp());
            }
        }
        BoardGraph graph = BoardView.graph();
        if (client.player == null || client.world == null || graph == null || !BoardView.view().details()) {
            clear();
            return;
        }
        if (graph != indexed) index(client.world, graph);
        BlockPos aim = BrushAim.aimed(client.player, client.world, 1f);
        if (aim != null && graph.node(aim) == null) aim = null;
        BlockPos at = client.player.getBlockPos();
        boolean moved = builtAt == null || !builtAt.equals(at);
        if (age++ >= REBUILD_TICKS || moved || !Objects.equals(aim, aimed)) {
            aimed = aim;
            build(client.world, graph, client.player.getEyePos());
            builtAt = at.toImmutable();
        }
    }

    private static void clear() {
        if (indexed == null && details.isEmpty()) return;
        details = List.of();
        outFrames = inFrames = chestFrames = markerFrames = List.of();
        indexed = null;
        incoming = Map.of();
        routed = Set.of();
        aimed = builtAt = null;
        age = 0;
    }

    /** Once per board graph: the links into each space, and the spaces routers drive. */
    private static void index(ClientWorld world, BoardGraph graph) {
        indexed = graph;
        age = REBUILD_TICKS; // rebuilt right away
        Map<BlockPos, List<BlockPos>> into = new HashMap<>();
        for (BoardGraph.Node node : graph.nodes()) {
            for (BoardGraph.Edge edge : node.edges()) {
                if (edge.active()) into.computeIfAbsent(edge.to(), pos -> new ArrayList<>()).add(node.pos());
            }
        }
        Set<BlockPos> driven = new HashSet<>();
        for (BoardGraph.Router router : graph.routers()) {
            CartridgeContainerBlockEntity container = BoardLinks.container(world, router.pos());
            if (container != null) driven.addAll(BoardLinks.links(container, 0));
        }
        incoming = into;
        routed = driven;
    }

    private static void build(ClientWorld world, BoardGraph graph, Vec3d eye) {
        age = 0;
        List<BoardGraph.Node> near = new ArrayList<>();
        for (BoardGraph.Node node : graph.nodes()) {
            if (node.pos().equals(aimed)) continue;
            if (Vec3d.ofCenter(node.pos()).squaredDistanceTo(eye) <= DETAIL_RADIUS_SQ) near.add(node);
        }
        near.sort(Comparator.comparingDouble(node -> Vec3d.ofCenter(node.pos()).squaredDistanceTo(eye)));
        List<Detail> built = new ArrayList<>();
        BoardGraph.Node aimedNode = aimed != null ? graph.node(aimed) : null;
        if (aimedNode != null) built.add(detail(world, graph, aimedNode, true));
        for (BoardGraph.Node node : near) {
            if (built.size() >= MAX_DETAILED) break;
            built.add(detail(world, graph, node, false));
        }
        details = built;
        frames(world, aimedNode);
    }

    private static Detail detail(ClientWorld world, BoardGraph graph, BoardGraph.Node node, boolean full) {
        Vec3d anchor = BrushOverlay.anchor(world, node.pos());
        List<Line> lines = new ArrayList<>();
        BoardSpaceBlockEntity space = world.getBlockEntity(node.pos()) instanceof BoardSpaceBlockEntity s ? s : null;
        ItemStack cartridge = space != null ? space.getActiveCartridgeItemStack() : ItemStack.EMPTY;
        boolean slots = space != null && space.size() > 1;
        // The active cartridge, a square in its colour: a plain tile's one is already on the board view's plate
        WorldDraw.Plate namePlate = WorldDraw.Plate.GOLD;
        if (!slots) {
            // nothing: the board view names it
        } else if (cartridge.getItem() instanceof CartridgeItem item) {
            lines.add(new Line(Text.literal("■ ").withColor(visible(item.menuColor(cartridge))).append(cartridge.getName()), namePlate));
        } else {
            lines.add(new Line(BoardText.Plate.MUTED.of(Text.translatable("hud.steveparty.explorer_helmet.no_cartridge")), namePlate));
        }
        if (slots && space != null) {
            int slot = space.getActiveSlot();
            lines.add(new Line(Text.translatable(routed.contains(node.pos()) ? "hud.steveparty.explorer_helmet.slot.router"
                    : "hud.steveparty.explorer_helmet.slot", slot + 1, space.size(), slot), WorldDraw.Plate.GOLD));
        }
        if (full && slots) lines.add(new Line(Text.translatable("hud.steveparty.explorer_helmet.cartridges", node.cartridges()), WorldDraw.Plate.TEAL));
        long out = node.activeBoardSpaceLinks();
        int in = incoming.getOrDefault(node.pos(), List.of()).size();
        lines.add(new Line(Text.translatable("hud.steveparty.explorer_helmet.links",
                Text.literal(Long.toString(out)).withColor(0x1D7A1D), Text.literal(Integer.toString(in)).withColor(0x1C4FA8)), WorldDraw.Plate.TEAL));
        if (full) {
            if (!node.step()) lines.add(new Line(Text.translatable("hud.steveparty.explorer_helmet.checkpoint"), WorldDraw.Plate.GREEN));
            if (CartridgeContainers.linksContainers(cartridge)) {
                if (node.inventoryIssue() == BoardGraph.InventoryIssue.NO_CHEST) {
                    // No chest of its own: the bank of the party running on its board
                    lines.add(new Line(Text.translatable("hud.steveparty.explorer_helmet.no_chest"), WorldDraw.Plate.GOLD));
                } else {
                    MutableText chests = Text.translatable("hud.steveparty.explorer_helmet.chests", CartridgeContainers.in(cartridge, world).size());
                    lines.add(new Line(node.inventoryIssue() == BoardGraph.InventoryIssue.CHEST_GONE
                            ? Text.translatable("hud.steveparty.explorer_helmet.chest_gone") : chests,
                            node.inventoryIssue() == null ? WorldDraw.Plate.GOLD : WorldDraw.Plate.RED));
                }
            }
            if (cartridge.getItem() instanceof ShopCartridgeItem) {
                ShopLinkComponent link = cartridge.get(ModComponents.SHOP_LINK);
                lines.add(new Line(link != null ? Text.translatable("hud.steveparty.explorer_helmet.shop", BoardText.pos(link.anchor()))
                        : Text.translatable("hud.steveparty.explorer_helmet.shop.nearest"), WorldDraw.Plate.GOLD));
            }
        }
        double top = anchor.y + 4;
        Box bounds = new Box(anchor.x - 1.5, anchor.y, anchor.z - 1.5, anchor.x + 1.5, top + 0.5 * lines.size(), anchor.z + 1.5);
        return new Detail(anchor, bounds, List.copyOf(lines), cartridge.getItem() instanceof ShopCartridgeItem);
    }

    /** A colour readable on the light plates: a white cartridge's square is drawn light grey. */
    private static int visible(int rgb) {
        return Argb.luminance(rgb) > 215 ? 0xA8A8A8 : rgb & 0xFFFFFF;
    }

    /** The aimed space's destinations, the spaces leading to it and its chests. */
    private static void frames(ClientWorld world, @Nullable BoardGraph.Node node) {
        if (node == null) {
            outFrames = inFrames = chestFrames = markerFrames = List.of();
            return;
        }
        List<BlockPos> out = new ArrayList<>(), in = new ArrayList<>(), chests = new ArrayList<>();
        int budget = MAX_FRAMES;
        for (BoardGraph.Edge edge : node.edges()) {
            if (budget <= 0) break;
            if (edge.active() && edge.target() == BoardGraph.Target.BOARD_SPACE && !out.contains(edge.to())) {
                out.add(edge.to());
                budget--;
            }
        }
        for (BlockPos from : incoming.getOrDefault(node.pos(), List.of())) {
            if (budget <= 0) break;
            if (!in.contains(from)) {
                in.add(from);
                budget--;
            }
        }
        if (world.getBlockEntity(node.pos()) instanceof BoardSpaceBlockEntity space
                && CartridgeContainers.linksContainers(space.getActiveCartridgeItemStack())) {
            for (BlockPos chest : CartridgeContainers.in(space.getActiveCartridgeItemStack(), world)) {
                if (budget-- <= 0) break;
                chests.add(chest);
            }
        }
        outFrames = out;
        inFrames = in;
        chestFrames = chests;
        // Its Spawn Marker, where its mob appears
        BlockPos marker = world.getBlockEntity(node.pos()) instanceof BoardSpaceBlockEntity space
                ? CartridgeSpawnMarker.marker(space.getActiveCartridgeItemStack(), world) : null;
        markerFrames = marker == null ? List.of() : List.of(marker);
    }

    private static void renderDetails(WorldRenderContext context) {
        if (details.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrixStack();
        if (client.world == null || matrices == null) return;
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        Camera camera = context.camera();
        Frustum frustum = context.frustum();
        Vec3d eye = camera.getPos();
        for (Detail detail : details) {
            if (frustum != null && !frustum.isVisible(detail.bounds())) continue;
            // The board view's scale (its labels grow with the distance), a little smaller
            float grow = (float) Math.clamp(Math.sqrt(detail.anchor().squaredDistanceTo(eye)) / 7.0, 1.0, 3.0);
            float base = LABEL_SCALE * grow, scale = base * DETAIL_SCALE;
            double basePlate = 16 * base, plate = 16 * scale;
            // Above the board view's plates (step number, warning, and the shop's on top)
            double bottom = 0.25 + basePlate * (detail.shop() ? 3.15 : 2.1) + plate / 2;
            int count = detail.lines().size();
            for (int i = 0; i < count; i++) {
                Line line = detail.lines().get(i);
                Vec3d at = detail.anchor().add(0, bottom + plate * 1.1 * (count - 1 - i), 0);
                WorldDraw.plateLabel(matrices, consumers, camera, at, line.text(), line.plate(), WorldDraw.PLATE_TEXT, scale);
            }
        }
        consumers.draw();
    }

    /**
     * How high (blocks, from {@code anchor}) the board view and the helmet's details of the space at {@code anchor}
     * reach, seen from {@code distance} blocks: what is drawn above a space goes higher (the game info panel). 0 when
     * no board view is shown.
     */
    static double heightAbove(Vec3d anchor, double distance) {
        if (!BoardView.view().shown()) return 0;
        float grow = (float) Math.clamp(distance / 7.0, 1.0, 3.0);
        double plate = 16 * LABEL_SCALE * grow;
        // The step plate, a warning above it, the shop's plate on top
        double height = 0.25 + plate * 3.2;
        for (int i = 0, n = details.size(); i < n; i++) {
            Detail detail = details.get(i);
            if (detail.anchor().equals(anchor)) {
                double bottom = 0.25 + plate * (detail.shop() ? 3.15 : 2.1);
                height = Math.max(height, bottom + plate * DETAIL_SCALE * 1.1 * detail.lines().size());
                break;
            }
        }
        return height;
    }

    private static void renderFrames(WorldRenderContext context) {
        if (outFrames.isEmpty() && inFrames.isEmpty() && chestFrames.isEmpty() && markerFrames.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrixStack();
        ClientWorld world = client.world;
        if (world == null || matrices == null) return;
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        Camera camera = context.camera();
        for (BlockPos pos : outFrames) BrushOverlay.frame(matrices, consumers, camera, world, pos, OUT);
        for (BlockPos pos : inFrames) BrushOverlay.frame(matrices, consumers, camera, world, pos, IN);
        for (BlockPos pos : chestFrames) BrushOverlay.frame(matrices, consumers, camera, world, pos, CHEST);
        for (BlockPos pos : markerFrames) BrushOverlay.frame(matrices, consumers, camera, world, pos, MARKER);
        consumers.draw();
    }
}
