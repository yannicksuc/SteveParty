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
        BrushTrail.initialize();
        BoardView.initialize();
        HelmetView.initialize();
    }

    static boolean holdsBrush(MinecraftClient client) {
        return client.player != null && client.player.getMainHandStack().getItem() instanceof TileLinkerBrushItem;
    }

    /** While painting: a little dust of the paint's colour where the brush is, on the tile it paints. */
    private static void strokeTrail(MinecraftClient client) {
        if (client.world == null || !BrushTrail.painting(client.player)) return;
        BlockPos aimed = BrushAim.aimed(client.player, client.world, 1f, BrushOverlay.ghosts());
        if (aimed == null) return;
        Vec3d at = BrushOverlay.anchor(client.world, aimed);
        var random = client.world.getRandom();
        int rgb = BrushTrail.color(client.player.getActiveItem());
        var color = new org.joml.Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
        for (int i = 0; i < 2; i++) {
            client.world.addParticle(new DustParticleEffect(color, 1.2f),
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
        int brushLevel = TileLinkerBrush.level(brush);
        Text level = brushLevel == TileLinkerBrush.POWERED ? Text.translatable("wheel.steveparty.brush.powered")
                : Text.translatable("wheel.steveparty.brush.levels.current", brushLevel);
        ItemStack cartridge = BoardLinks.cartridgeSource(client.player);
        int left = BoardLinks.cartridgesLeft(client.player);
        // A cartridge is only needed for a kind picked on the wheel (swapped in), or for an aimed space left empty
        boolean needed = TileLinkerBrush.cartridge(brush) != null || aimsAtEmptySpace(client, brush);
        List<ToolHud.Element> tool = new ArrayList<>();
        if (needed) tool.add(ToolHud.element(ToolHud.BOX, (x, y) -> cartridgeBox(context, x, y, cartridge, left)));
        if (needed && cartridge.isEmpty()) {
            Text none = BoardText.Plate.DEAD_END.of(Text.translatable("hud.steveparty.wrench.no_cartridge"));
            tool.add(ToolHud.element(ToolHud.textPlateWidth(none), (x, y) -> ToolHud.textPlate(context, x, y, none, ToolHud.Plate.RED)));
        }
        tool.add(ToolHud.element(ToolHud.textPlateWidth(level), (x, y) -> ToolHud.textPlate(context, x, y, level, ToolHud.Plate.GREEN)));
        List<List<ToolHud.Element>> groups = new ArrayList<>(List.of(tool));
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
