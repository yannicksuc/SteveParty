package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.board.BrushAim;
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
 * cell, with the arrows of their links in orange; the brush aims at them like at tiles.
 */
final class BrushOverlay {
    static final int WHITE = 0xFFFFFFFF;
    /** The colour of a ghost and of the links to it. */
    static final int DANGLING = 0xFFA030;
    /** A ghost's slab: as high as a tile's, its outline in dashes this long, this far apart, this thick. */
    private static final double GHOST_HEIGHT = 0.125, DASH = 0.14, DASH_GAP = 0.1, DASH_WIDTH = 0.025;
    /** The ghosts of the brush in hand (refreshed each tick), each with the spaces linked to it. */
    private static Map<BlockPos, List<BlockPos>> ghosts = Map.of();

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
        BlockPos aimed = BrushAim.aimed(player, world, context.tickCounter().getTickDelta(true), ghosts());
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
        CartridgeContainerBlockEntity from = BoardLinks.container(world, a);
        return from != null && BoardLinks.links(from, BoardLinks.slotOf(from, TileLinkerBrush.level(brush))).contains(b);
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
