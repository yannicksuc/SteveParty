package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.board.BrushAim;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.client.renderer.GlowingCuboidRenderer;
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

/**
 * What the Tile Linker Brush shows its holder (and only them: everything is drawn client side): its anchor (the last
 * tile painted) framed in gold, the tile aimed at, found the way the player sees it (see {@link BrushAim}), framed, and
 * the ghost path from the anchor to it: green, a link the stroke would paint; red, one it would erase.
 */
final class BrushOverlay {
    static final int GREEN = 0xFF4CFF4C;
    static final int RED = 0xFFFF4040;
    static final int GOLD = 0xFFFFD83D;
    static final int WHITE = 0xFFFFFFFF;

    private BrushOverlay() {
    }

    static void initialize() {
        // After the block entities (where the vanilla block outline is drawn), not AFTER_ENTITIES: the see-through
        // frame writes depth, and drawn before them it hid what a tile's renderer draws under it
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(BrushOverlay::render);
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
        BlockPos anchor = TileLinkerBrush.anchor(brush, world);
        if (anchor != null && BoardLinks.container(world, anchor) == null) anchor = null;
        if (anchor != null) frame(matrices, consumers, camera, world, anchor, GOLD);
        BlockPos aimed = BrushAim.aimed(player, world, context.tickCounter().getTickDelta(true));
        if (aimed != null && !aimed.equals(anchor)) {
            int color = WHITE;
            if (anchor != null) {
                color = linked(world, brush, anchor, aimed) ? RED : GREEN;
                ghostPath(matrices, consumers, camera, world, anchor, aimed, color);
            }
            frame(matrices, consumers, camera, world, aimed, color);
        }
        consumers.draw();
    }

    /** A link between the two, either way, in the slot of the brush's level: the stroke would erase it. */
    private static boolean linked(ClientWorld world, ItemStack brush, BlockPos a, BlockPos b) {
        CartridgeContainerBlockEntity from = BoardLinks.container(world, a), to = BoardLinks.container(world, b);
        int level = TileLinkerBrush.level(brush);
        return from != null && BoardLinks.links(from, BoardLinks.slotOf(from, level)).contains(b)
                || to != null && BoardLinks.links(to, BoardLinks.slotOf(to, level)).contains(a);
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

    /** The path the click would make: chevrons of the board view, bigger and faster, in the colour of what it does. */
    private static void ghostPath(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, ClientWorld world,
                                  BlockPos from, BlockPos to, int color) {
        double phase = (world.getTime() + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true)) / 20.0 * 3.0;
        WorldDraw.path(matrices, consumers, camera, anchor(world, from).add(0, 0.05, 0), anchor(world, to).add(0, 0.05, 0),
                color, 0.55, 0.5, phase, 0.4, 0);
    }
}
