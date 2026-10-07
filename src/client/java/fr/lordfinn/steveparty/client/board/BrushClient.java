package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.board.BoardText;
import fr.lordfinn.steveparty.board.BrushAim;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.client.gui.ToolHud;
import fr.lordfinn.steveparty.client.gui.wheel.ToolWheel;
import fr.lordfinn.steveparty.items.custom.TileLinkerBrushItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The Tile Linker Brush on the client: its wheel (left click, see {@link BrushWheel}), its HUD above the hotbar, the
 * aimed tile and the stroke shown in the world (see {@link BrushOverlay}), and the board view (with the Explorer's
 * Helmet's details, see {@link HelmetView}).
 */
public final class BrushClient {
    private static final int INSET = (ToolHud.BOX - 16) / 2;
    private static final ItemStack NO_CARTRIDGE_ICON = new ItemStack(fr.lordfinn.steveparty.items.ModItems.BOARD_SPACE_BEHAVIOR);

    private BrushClient() {
    }

    public static void initialize() {
        ToolWheel.register(new BrushWheel());
        HudRenderCallback.EVENT.register(BrushClient::renderBrushHud);
        ClientTickEvents.END_CLIENT_TICK.register(BrushClient::strokeTrail);
        BrushOverlay.initialize();
        BoardView.initialize();
        HelmetView.initialize();
    }

    static boolean holdsBrush(MinecraftClient client) {
        return client.player != null && client.player.getMainHandStack().getItem() instanceof TileLinkerBrushItem;
    }

    /** While painting: redstone dust where the brush is, on the tile it paints (the stroke drawn freely). */
    private static void strokeTrail(MinecraftClient client) {
        if (client.player == null || client.world == null || client.currentScreen != null || !holdsBrush(client)
                || !client.options.useKey.isPressed() || ToolWheel.isOpen()) return;
        BlockPos aimed = BrushAim.aimed(client.player, client.world, 1f);
        if (aimed == null) return;
        Vec3d at = BrushOverlay.anchor(client.world, aimed);
        var random = client.world.getRandom();
        for (int i = 0; i < 2; i++) {
            client.world.addParticle(new DustParticleEffect(new org.joml.Vector3f(1f, 0.1f, 0.05f), 1.2f),
                    at.x + (random.nextDouble() - 0.5) * 0.6, at.y - 0.1, at.z + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
        }
    }

    /**
     * The Tile Linker Brush HUD, in the tools' look: the brush, the cartridge a new linked tile will get (and how many
     * are left), a plate with its level, the board summary, and the controls.
     */
    private static void renderBrushHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.currentScreen != null || client.player == null || client.world == null || !holdsBrush(client)
                || ToolWheel.isOpen()) return;
        ItemStack brush = client.player.getMainHandStack();
        Text level = Text.translatable("hud.steveparty.tile_linker_brush.panel", TileLinkerBrush.levelText(TileLinkerBrush.level(brush)));
        ItemStack cartridge = BoardLinks.cartridgeSource(client.player);
        int left = BoardLinks.cartridgesLeft(client.player);
        // A cartridge is only needed for a kind picked on the wheel (swapped in), or for an aimed space left empty
        boolean needed = TileLinkerBrush.cartridge(brush) != null || aimsAtEmptySpace(client, brush);
        List<ToolHud.Element> tool = new ArrayList<>();
        tool.add(ToolHud.element(ToolHud.BOX, (x, y) -> {
            ToolHud.box(context, x, y, true);
            context.drawItem(brush, x + INSET, y + INSET);
        }));
        if (needed) tool.add(ToolHud.element(ToolHud.BOX, (x, y) -> cartridgeBox(context, x, y, cartridge, left)));
        if (needed && cartridge.isEmpty()) {
            Text none = BoardText.Plate.DEAD_END.of(Text.translatable("hud.steveparty.wrench.no_cartridge"));
            tool.add(ToolHud.element(ToolHud.textPlateWidth(none), (x, y) -> ToolHud.textPlate(context, x, y, none, ToolHud.Plate.RED)));
        }
        tool.add(ToolHud.element(ToolHud.textPlateWidth(level), (x, y) -> ToolHud.textPlate(context, x, y, level, ToolHud.Plate.GREEN)));
        List<List<ToolHud.Element>> groups = new ArrayList<>(List.of(tool));
        Text board = boardSummary();
        if (board != null) {
            ToolHud.Plate boardPlate = BoardView.counts()[1] + BoardView.counts()[2] > 0 ? ToolHud.Plate.ORANGE : ToolHud.Plate.GREEN;
            groups.add(List.of(ToolHud.element(ToolHud.textPlateWidth(board), (x, y) -> ToolHud.textPlate(context, x, y, board, boardPlate))));
        }
        int y = ToolHud.rows(context, groups, 4);
        ToolHud.hint(context, Text.translatable("hud.steveparty.tile_linker_brush.hint"), context.getScaledWindowWidth() / 2, y);
    }

    /** Whether the brush aims at a board space whose slot (the brush's level) holds no cartridge. */
    private static boolean aimsAtEmptySpace(MinecraftClient client, ItemStack brush) {
        BlockPos aimed = BrushAim.aimed(client.player, client.world, 1f);
        if (aimed == null) return false;
        var container = BoardLinks.container(client.world, aimed);
        return container instanceof fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity
                && container.getStack(BoardLinks.slotOf(container, TileLinkerBrush.level(brush))).isEmpty();
    }

    /** The board around, summed up (null: no board space around). */
    private static @org.jetbrains.annotations.Nullable Text boardSummary() {
        int[] counts = BoardView.counts();
        if (counts[0] == 0) return null;
        if (counts[1] + counts[2] == 0) {
            return BoardText.Plate.OK.of(Text.translatable("hud.steveparty.board.summary.ok", Text.translatable("hud.steveparty.board.spaces", counts[0])));
        }
        return Text.translatable("hud.steveparty.board.summary.problems",
                BoardText.Plate.NUMBER.of(Text.translatable("hud.steveparty.board.spaces", counts[0])),
                (counts[1] > 0 ? BoardText.Plate.DEAD_END : BoardText.Plate.MUTED).of(Text.translatable("hud.steveparty.board.dead_ends", counts[1])),
                (counts[2] > 0 ? BoardText.Plate.UNREACHABLE : BoardText.Plate.MUTED).of(Text.translatable("hud.steveparty.board.unreachable", counts[2])));
    }

    /**
     * The cartridge box: the icon of the cartridge the next new space will get, with how many are left (like a hotbar
     * stack; nothing in creative), or a red box with a greyed Cartridge when survival has none left (its hint is a
     * plate of its own).
     */
    private static void cartridgeBox(DrawContext context, int x, int y, ItemStack cartridge, int left) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!cartridge.isEmpty()) {
            ToolHud.box(context, x, y, false);
            context.drawItem(cartridge, x + INSET, y + INSET);
            if (left >= 0) context.drawItemInSlot(client.textRenderer, cartridge, x + INSET, y + INSET, Integer.toString(left));
            return;
        }
        ToolHud.plate(context, x, y, ToolHud.BOX, ToolHud.BOX, ToolHud.Plate.RED);
        context.drawItem(client.player != null ? new ItemStack(BoardLinks.cartridgeKind(client.player)) : NO_CARTRIDGE_ICON, x + INSET, y + INSET);
        context.fill(x + INSET, y + INSET, x + INSET + 16, y + INSET + 16, 200, 0x80C4C4C4); // greyed
    }
}
