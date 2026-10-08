package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.board.BrushAim;
import fr.lordfinn.steveparty.board.BrushLinkable;
import fr.lordfinn.steveparty.board.BrushLinks;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.client.renderer.GlowingCuboidRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the Tile Linker Brush shows its holder (and only them: everything is drawn client side): the tile aimed at,
 * found the way the player sees it (see {@link BrushAim}), framed. Nothing is ever selected: while a stroke is held,
 * the frame tells what reaching that tile does (green, a link painted; red, one erased; white, nothing), and the paint
 * left behind and the arrows of the stroke are {@link BrushTrail}. The ghosts of the board spaces around (their
 * dangling links, to a cell without board space: see {@link BoardLinks#dangling}) are drawn as a dashed slab in that
 * cell, with the arrows of their links in orange; the brush aims at them like at tiles. The links of the other holders
 * of a cartridge around (a Hop Switch to the blocks it switches, an inventory tile or a Piggy Bank to its chests...: see
 * {@link BrushLinks}) flow from the holder to its targets in the colour of their kind; the brush aims at what the last
 * holder painted (or its anchor) links.
 */
final class BrushOverlay {
    static final int WHITE = 0xFFFFFFFF;
    /** The colour of a ghost and of the links to it. */
    static final int DANGLING = 0xFFA030;
    /** A ghost's slab: as high as a tile's, its outline in dashes this long, this far apart, this thick. */
    private static final double GHOST_HEIGHT = 0.125, DASH = 0.14, DASH_GAP = 0.1, DASH_WIDTH = 0.025;
    /** The ghosts of the brush in hand (refreshed each tick), each with the spaces linked to it. */
    private static Map<BlockPos, List<BlockPos>> ghosts = Map.of();
    /** The holders' links the board view does not draw, refreshed every {@link #HOLDERS_REFRESH} ticks. */
    private static List<HolderLinks> holders = List.of();
    private static final int HOLDERS_REFRESH = 10;
    private static int holdersAge;

    /** A holder of a cartridge, its targets and their colour (see BrushLinkable). */
    private record HolderLinks(Vec3d from, List<Vec3d> to, int color) {
    }

    private BrushOverlay() {
    }

    static void initialize() {
        // After the block entities (where the vanilla block outline is drawn), not AFTER_ENTITIES: the see-through
        // frame writes depth, and drawn before them it hid what a tile's renderer draws under it
        // After the translucent blocks: drawn before, the ghosts would hide the stained glass behind them
        WorldRenderEvents.LAST.register(BrushOverlay::render);
        ClientTickEvents.END_CLIENT_TICK.register(BrushOverlay::tick);
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ItemStack brush = player == null ? ItemStack.EMPTY : player.getMainHandStack();
        ghosts = client.world == null || !TileLinkerBrush.isBrush(brush) ? Map.of()
                : BrushAim.ghosts(player, client.world, TileLinkerBrush.level(brush));
        if (client.world == null || !TileLinkerBrush.isBrush(brush)) {
            holders = List.of();
            holdersAge = HOLDERS_REFRESH;
        } else if (++holdersAge >= HOLDERS_REFRESH) {
            holdersAge = 0;
            holders = holders(client.world, player, TileLinkerBrush.level(brush));
        }
    }

    /** The links of the holders around (Hop Switches, inventory tiles, Piggy Banks...) the board view does not draw. */
    private static List<HolderLinks> holders(ClientWorld world, ClientPlayerEntity player, int level) {
        List<HolderLinks> found = new java.util.ArrayList<>();
        for (BrushLinkable kind : BrushLinks.around(world, player.getEyePos(), BoardView.RADIUS, level)) {
            if (kind.drawnByBoardView()) continue;
            List<BlockPos> targets = kind.targets(world);
            if (targets.isEmpty()) continue;
            List<Vec3d> to = new java.util.ArrayList<>(targets.size());
            for (BlockPos target : targets) to.add(anchor(world, target));
            found.add(new HolderLinks(anchor(world, kind.holder()), to, kind.color()));
        }
        return found;
    }

    /** What the brush aims at besides holders and ghosts: what the stroke's last holder links, else its anchor's. */
    static java.util.function.Predicate<BlockPos> targets(ClientWorld world, ItemStack brush) {
        BlockPos from = BrushTrail.lastTile() != null ? BrushTrail.lastTile() : TileLinkerBrush.anchor(brush, world);
        int level = TileLinkerBrush.level(brush);
        return target -> BrushLinks.aims(world, from, level, target);
    }

    /** The cells of the ghosts the brush in hand aims at. */
    static Set<BlockPos> ghosts() {
        return ghosts.keySet();
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        MatrixStack matrices = context.matrixStack();
        if (player == null || world == null || matrices == null) return;
        ItemStack brush = player.getMainHandStack();
        if (!TileLinkerBrush.isBrush(brush) || fr.lordfinn.steveparty.client.gui.wheel.ToolWheel.isOpen()) return;
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        Camera camera = context.camera();
        ghosts(matrices, consumers, camera, world, context.tickCounter().getTickDelta(true));
        holderLinks(matrices, consumers, camera, world, context.tickCounter().getTickDelta(true));
        BlockPos aimed = BrushAim.aimed(player, world, context.tickCounter().getTickDelta(true), ghosts(), targets(world, brush));
        if (aimed != null) {
            BlockPos last = BrushTrail.lastTile();
            int color = last == null || last.equals(aimed) ? WHITE : 0xFF000000 | BrushTrail.outcome(world, brush, last, aimed);
            frame(matrices, consumers, camera, world, aimed, color);
        }
        consumers.draw();
    }

    /** Each ghost: a dashed see-through slab in its cell, and orange arrows flowing to it from the spaces linked to it. */
    private static void ghosts(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, ClientWorld world, float tickDelta) {
        if (ghosts.isEmpty()) return;
        float now = world.getTime() + tickDelta;
        float pulse = 0.55f + 0.25f * (float) Math.sin(now / 5.0);
        double phase = now / 20.0 * 2.5;
        for (Map.Entry<BlockPos, List<BlockPos>> ghost : ghosts.entrySet()) {
            BlockPos pos = ghost.getKey();
            Vec3d min = Vec3d.of(pos).add(0.0625, 0.002, 0.0625), max = min.add(0.875, GHOST_HEIGHT, 0.875);
            WorldDraw.box(matrices, consumers, camera, min, max, DANGLING, 0.12f * pulse);
            // Opaque dashes: see-through, they would look under the stained glass the ghost lies on
            dashedOutline(matrices, consumers, camera, min, max, 1f);
            Vec3d to = BoardSpaces.standPos(world, pos).add(0, 0.2, 0);
            for (BlockPos from : ghost.getValue()) {
                WorldDraw.path(matrices, consumers, camera, anchor(world, from), to, 0xC0000000 | DANGLING, 0.4, 0.45, phase, 0.3, 0);
            }
        }
    }

    /** Arrows flowing from each holder around to its targets, in the colour of the kind of link. */
    private static void holderLinks(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, ClientWorld world, float tickDelta) {
        if (holders.isEmpty()) return;
        double phase = (world.getTime() + tickDelta) / 20.0 * 2.5;
        for (HolderLinks links : holders) {
            for (Vec3d to : links.to()) {
                WorldDraw.path(matrices, consumers, camera, links.from(), to, 0xC0000000 | links.color(), 0.4, 0.45, phase, 0.3, 0);
            }
        }
    }

    /** The 8 horizontal edges of the box (min, max), in dashes. */
    private static void dashedOutline(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Vec3d min, Vec3d max, float alpha) {
        for (double y : new double[]{min.y, max.y}) {
            dashes(matrices, consumers, camera, new Vec3d(min.x, y, min.z), new Vec3d(max.x, y, min.z), alpha);
            dashes(matrices, consumers, camera, new Vec3d(max.x, y, min.z), new Vec3d(max.x, y, max.z), alpha);
            dashes(matrices, consumers, camera, new Vec3d(max.x, y, max.z), new Vec3d(min.x, y, max.z), alpha);
            dashes(matrices, consumers, camera, new Vec3d(min.x, y, max.z), new Vec3d(min.x, y, min.z), alpha);
        }
    }

    /** Dashes from {@code a} to {@code b} (along x or z): thin boxes. */
    private static void dashes(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Vec3d a, Vec3d b, float alpha) {
        double length = a.distanceTo(b);
        Vec3d unit = b.subtract(a).multiply(1 / length), half = new Vec3d(DASH_WIDTH / 2, DASH_WIDTH / 2, DASH_WIDTH / 2);
        for (double t = 0; t < length; t += DASH + DASH_GAP) {
            Vec3d start = a.add(unit.multiply(t)), end = a.add(unit.multiply(Math.min(t + DASH, length)));
            Vec3d min = new Vec3d(Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z)).subtract(half);
            Vec3d max = new Vec3d(Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z)).add(half);
            WorldDraw.box(matrices, consumers, camera, min, max, DANGLING, alpha);
        }
    }

    /** A link from {@code a} to {@code b} in the slot of the brush's level: the stroke would erase it (not the way back). */
    static boolean linked(ClientWorld world, ItemStack brush, BlockPos a, BlockPos b) {
        for (BrushLinkable kind : BrushLinks.of(world, a, TileLinkerBrush.level(brush))) if (kind.linked(world, b)) return true;
        return false;
    }

    /** Where links start and end on a board space: a little above the middle of its surface. */
    static Vec3d anchor(ClientWorld world, BlockPos pos) {
        return BoardSpaces.standPos(world, pos).add(0, 0.2, 0);
    }

    /** A pulsing highlight on a board space, where it is seen (lowered, sloped, all 4 blocks of a large tile). */
    static void frame(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, ClientWorld world, BlockPos pos, int argb) {
        float pulse = 0.25f + 0.15f * (float) Math.sin(world.getTime() / 3.0);
        GlowingCuboidRenderer.drawBlockBox(matrices, consumers, pos,
                ((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, pulse);
    }
}
