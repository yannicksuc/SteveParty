package fr.lordfinn.steveparty.client.board;

import net.minecraft.entity.player.PlayerEntity;
import fr.lordfinn.steveparty.items.custom.TileLinkerBrushItem;
import fr.lordfinn.steveparty.items.custom.AbstractDestinationsSelectorItem;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileLayout;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import fr.lordfinn.steveparty.board.BoardGraph;
import fr.lordfinn.steveparty.board.BrushAim;
import fr.lordfinn.steveparty.board.ExplorerHelmet;
import fr.lordfinn.steveparty.client.gui.ToolHud;
import fr.lordfinn.steveparty.client.payloads.ClientPayloads;
import fr.lordfinn.steveparty.client.renderer.StarSpaceRenderer;
import fr.lordfinn.steveparty.payloads.custom.TileInfoPayloads;
import fr.lordfinn.steveparty.service.TileInfos;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The game info of the board spaces on the client (see docs: « Voir les infos du plateau »): one panel per space shown,
 * above the tallest token standing on it (and above the board view's plates when a board tool or the Explorer's Helmet
 * shows them), its items circling the space ({@link ItemRing}).
 * <ul>
 *     <li>the space looked at (a token on it counts), up to {@link #PANEL_DISTANCE} blocks; farther (up to the brush's
 *     reach), the same panel under the crosshair (HUD);</li>
 *     <li>where the player's own token is heading during its move, each way's end at a fork (a gold plate);</li>
 *     <li>with the Explorer's Helmet lamp lit: every space around too, reduced to its title and first line.</li>
 * </ul>
 * The infos come from the server ({@link TileInfoPayloads}): asked for what is shown, twice a second, sent back only
 * when changed. The aim and the heights are worked out once per tick; a frame only draws.
 */
public final class TileInfoClient {
    /** The panel is drawn in the world up to this distance; farther, under the crosshair. */
    private static final double PANEL_DISTANCE = 16;
    /** The infos shown are asked again this often (ticks), to follow their changes. */
    private static final int REFRESH_TICKS = 10;
    /** A space never asked yet is asked at once, but not more often than this (ticks). */
    private static final int ASK_GAP_TICKS = 2;
    /** With the helmet: the spaces this close, at most this many. */
    private static final double NEARBY_RADIUS = 8;
    private static final int NEARBY_MAX = 10;
    /** Text pixel size near by (block); it grows with the distance, up to {@link #MAX_GROW} times. */
    private static final float SCALE = 1f / 48f, MAX_GROW = 2.5f;

    private enum Kind {
        HOVER(true, WorldDraw.Plate.TEAL), FOCUS(true, WorldDraw.Plate.GOLD), NEARBY(false, WorldDraw.Plate.TEAL);

        final boolean full;
        final WorldDraw.Plate plate;

        Kind(boolean full, WorldDraw.Plate plate) {
            this.full = full;
            this.plate = plate;
        }
    }

    /** A space shown this tick, and where its panel and ring go (worked out per tick). */
    private static final class Shown {
        final BlockPos pos;
        final Kind kind;
        final Vec3d anchor;
        final double bottom, ringY, ringRadius;
        final Box bounds;

        Shown(BlockPos pos, Kind kind, Vec3d anchor, double bottom, double ringY, double ringRadius) {
            this.pos = pos;
            this.kind = kind;
            this.anchor = anchor;
            this.bottom = bottom;
            this.ringY = ringY;
            this.ringRadius = ringRadius;
            this.bounds = new Box(anchor.x - 2, anchor.y - 0.5, anchor.z - 2, anchor.x + 2, bottom + 3, anchor.z + 2);
        }
    }

    private static final Map<BlockPos, TilePanel.Layout> LAYOUTS = new HashMap<>();
    private static List<Shown> shown = List.of();
    private static List<BlockPos> focus = List.of();
    private static @Nullable BlockPos far;
    private static @Nullable ClientWorld knownWorld;
    /** How the space looked at and the destinations are shown: with the helmet's details, a building tool's. */
    private static TilePanel.View view = TilePanel.View.PLAY;
    private static boolean fresh = true;
    private static long askedAt = Long.MIN_VALUE;

    private TileInfoClient() {
    }

    public static void initialize() {
        ClientPayloads.receive(TileInfoPayloads.Info.ID, (payload, context) -> {
            if (context.client().world == knownWorld) LAYOUTS.put(payload.space(), new TilePanel.Layout(payload.info()));
        });
        ClientPayloads.receive(TileInfoPayloads.Focus.ID, (payload, context) -> focus = List.copyOf(payload.spaces()));
        ClientTickEvents.END_CLIENT_TICK.register(TileInfoClient::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(TileInfoClient::render);
        HudRenderCallback.EVENT.register(TileInfoClient::renderHud);
    }

    /** A new connection: nothing known. */
    public static void clear() {
        LAYOUTS.clear();
        focus = List.of();
        shown = List.of();
        far = null;
        knownWorld = null;
        fresh = true;
    }

    // ---------------------------------------------------------------- per tick

    private static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world != knownWorld) {
            LAYOUTS.clear();
            focus = List.of();
            knownWorld = world;
            fresh = true;
        }
        Entity viewer = client.getCameraEntity();
        if (world == null || client.player == null || viewer == null) {
            shown = List.of();
            far = null;
            return;
        }
        Vec3d eye = viewer.getCameraPosVec(1f);
        view = TilePanel.View.of(ExplorerHelmet.view(client.player).details(), building(client.player));
        List<Shown> next = new ArrayList<>();
        List<BlockPos> asked = new ArrayList<>();
        far = null;
        BlockPos hovered = hovered(world, viewer, eye);
        if (hovered != null) {
            asked.add(hovered);
            if (BoardSpaces.standPos(world, hovered).squaredDistanceTo(eye) <= PANEL_DISTANCE * PANEL_DISTANCE) {
                next.add(shown(world, viewer, hovered, Kind.HOVER));
            } else {
                far = hovered;
            }
        }
        for (BlockPos pos : focus) {
            if (asked.contains(pos) || !world.isChunkLoaded(pos)) continue;
            asked.add(pos);
            next.add(shown(world, viewer, pos, Kind.FOCUS));
        }
        if (ExplorerHelmet.view(client.player).details()) nearby(world, viewer, eye, next, asked);
        shown = next;
        ask(world, asked);
    }

    /** A building tool in either hand: the Tile Linker Brush, the Wrench, a cartridge. */
    private static boolean building(PlayerEntity player) {
        for (ItemStack stack : player.getHandItems()) {
            if (stack.getItem() instanceof TileLinkerBrushItem || stack.getItem() instanceof AbstractDestinationsSelectorItem) return true;
        }
        return false;
    }

    /** With the helmet: the spaces around (the nearest first), reduced. */
    private static void nearby(ClientWorld world, Entity viewer, Vec3d eye, List<Shown> next, List<BlockPos> asked) {
        BoardGraph graph = BoardView.graph();
        if (graph == null) return;
        List<BlockPos> around = new ArrayList<>();
        for (BoardGraph.Node node : graph.nodes()) {
            if (!asked.contains(node.pos()) && Vec3d.ofCenter(node.pos()).squaredDistanceTo(eye) <= NEARBY_RADIUS * NEARBY_RADIUS)
                around.add(node.pos());
        }
        around.sort((a, b) -> Double.compare(Vec3d.ofCenter(a).squaredDistanceTo(eye), Vec3d.ofCenter(b).squaredDistanceTo(eye)));
        for (BlockPos pos : around) {
            if (asked.size() >= TileInfos.MAX_ASKED || next.size() >= NEARBY_MAX + 2) break;
            asked.add(pos);
            next.add(shown(world, viewer, pos, Kind.NEARBY));
        }
    }

    /** The board space looked at: a token standing on it, else the space itself, the way the brush aims. */
    private static @Nullable BlockPos hovered(ClientWorld world, Entity viewer, Vec3d eye) {
        Vec3d look = viewer.getRotationVec(1f);
        BlockPos aimed = BrushAim.along(world, viewer, eye, look);
        if (aimed != null && !(world.getBlockEntity(aimed) instanceof BoardSpaceBlockEntity)) aimed = null;
        double aimedDistance = aimed == null ? Double.MAX_VALUE : BoardSpaces.standPos(world, aimed).squaredDistanceTo(eye);
        Vec3d end = eye.add(look.multiply(PANEL_DISTANCE));
        EntityHitResult hit = ProjectileUtil.raycast(viewer, eye, end, viewer.getBoundingBox().stretch(look.multiply(PANEL_DISTANCE)).expand(1),
                entity -> entity instanceof LivingEntity && !entity.isSpectator() && entity != viewer, PANEL_DISTANCE * PANEL_DISTANCE);
        if (hit != null && hit.getPos().squaredDistanceTo(eye) < aimedDistance) {
            BoardSpaceBlockEntity space = BoardSpaces.boardSpaceOf(hit.getEntity());
            if (space != null) return space.getPos();
        }
        return aimed;
    }

    /** Where the panel of {@code pos} goes: over the tallest creature standing on it, the trap's badge, the party star. */
    private static Shown shown(ClientWorld world, Entity viewer, BlockPos pos, Kind kind) {
        Vec3d anchor = BrushOverlay.anchor(world, pos);
        BlockState state = world.getBlockState(pos);
        double radius = 0.85;
        if (state.getBlock() instanceof ATileBlock) {
            TileLayout layout = state.get(ATileBlock.SIZE);
            radius = layout.isLarge() ? 1.35 : layout.size() == TileSize.SMALL ? 0.6 : 0.85;
        }
        double top = anchor.y + 0.9;
        Box over = new Box(anchor.x - radius, anchor.y - 0.3, anchor.z - radius, anchor.x + radius, anchor.y + 4, anchor.z + radius);
        for (Entity entity : world.getOtherEntities(viewer, over, entity -> entity instanceof LivingEntity && !entity.isSpectator())) {
            // A name tag floats half a block over its head
            double head = entity.getBoundingBox().maxY + (entity.shouldRenderName() || entity.hasCustomName() ? 0.55 : 0.1);
            top = Math.max(top, head);
        }
        if (world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity space && space.getTrapMark() != null) top = Math.max(top, anchor.y + 2.15);
        if (StarSpaceRenderer.hasStar(pos)) top = Math.max(top, anchor.y + 2.9);
        double ringY = anchor.y + 0.95;
        return new Shown(pos, kind, anchor, Math.max(top + 0.1, ringY + 0.45), ringY, radius + 0.15);
    }

    /** Asks the server for what is shown: what it never got at once, the rest every {@link #REFRESH_TICKS} ticks. */
    private static void ask(ClientWorld world, List<BlockPos> asked) {
        if (asked.isEmpty() || !ClientPlayNetworking.canSend(TileInfoPayloads.Request.ID)) return;
        long now = world.getTime();
        boolean unknown = false;
        for (BlockPos pos : asked) {
            if (!LAYOUTS.containsKey(pos)) {
                unknown = true;
                break;
            }
        }
        long since = now - askedAt;
        if (since >= 0 && since < (unknown ? ASK_GAP_TICKS : REFRESH_TICKS)) return;
        askedAt = now;
        ClientPlayNetworking.send(new TileInfoPayloads.Request(fresh, List.copyOf(asked)));
        fresh = false;
    }

    // ---------------------------------------------------------------- drawing

    private static void render(WorldRenderContext context) {
        List<Shown> current = shown;
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrixStack();
        if (current.isEmpty() || matrices == null || client.world == null || client.options.hudHidden) return;
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        Camera camera = context.camera();
        Frustum frustum = context.frustum();
        Vec3d eye = camera.getPos();
        float time = (client.world.getTime() % 24000) + context.tickCounter().getTickDelta(true);
        for (int i = 0, n = current.size(); i < n; i++) {
            Shown space = current.get(i);
            TilePanel.Layout layout = LAYOUTS.get(space.pos);
            TilePanel.View shownAs = space.kind.full ? view : TilePanel.View.COMPACT;
            if (layout == null || !layout.hasContent(shownAs) || frustum != null && !frustum.isVisible(space.bounds)) continue;
            double distance = Math.sqrt(space.anchor.squaredDistanceTo(eye));
            float scale = SCALE * (float) Math.clamp(distance / 7.0, 1.0, MAX_GROW);
            double bottom = Math.max(space.bottom, space.anchor.y + HelmetView.heightAbove(space.anchor, distance) + 0.1);
            TilePanel.draw(matrices, consumers, camera, client.world, space.anchor.x, bottom, space.anchor.z, layout,
                    shownAs, space.kind.plate, scale);
            if (space.kind.full && !layout.info.ring().isEmpty()) {
                ItemRing.render(matrices, consumers, camera, client.world, space.anchor.x, space.ringY, space.anchor.z,
                        space.ringRadius, layout.info.ring(), layout.ringLabels, time);
                consumers.draw();
            }
        }
    }

    /** The space looked at from afar: its panel under the crosshair. */
    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockPos pos = far;
        if (pos == null || client.options.hudHidden || client.currentScreen != null) return;
        TilePanel.Layout layout = LAYOUTS.get(pos);
        if (layout == null || !layout.hasContent(view)) return;
        TilePanel.drawHud(context, layout, view, context.getScaledWindowWidth() / 2, context.getScaledWindowHeight() / 2 + 14);
    }
}
