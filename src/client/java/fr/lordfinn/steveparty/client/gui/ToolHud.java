package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * One visual language for the HUDs of the mod's tools held in hand (the Stencil Hammer, the Wrench): small plates just
 * above the hotbar, cut like the mod's screens (teal like the Tile's, gold when active: see
 * the art sources), and a see-through hint line above them.
 */
public final class ToolHud {
    /** Size of a square box (an icon, a stencil...). */
    public static final int BOX = 24;
    /** Dark grey of the mod's screen titles: text on a plate. */
    public static final int TEXT = 0xFF3F3F3F;
    /** The hint is only a reminder: drawn see-through. */
    public static final int HINT = 0x88DDDDDD;

    public enum Plate {
        TEAL, GOLD, GREEN, RED, ORANGE;

        final Identifier sprite = Steveparty.id("board/plate_" + name().toLowerCase());
    }

    private ToolHud() {
    }

    /** Top of the boxes: right above the hotbar, or above the health / hunger rows when they are shown. */
    public static int top(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean statusBars = client.interactionManager != null && client.interactionManager.hasStatusBars();
        return context.getScaledWindowHeight() - (statusBars ? 50 : 26) - BOX;
    }

    /** A plate (nine-slice) over (x, y, width, height). */
    public static void plate(DrawContext context, int x, int y, int width, int height, Plate plate) {
        context.drawGuiTexture(RenderLayer::getGuiTextured, plate.sprite, x, y, width, height);
    }

    /** A box: a gold plate when it is the active one, teal otherwise. */
    public static void box(DrawContext context, int x, int y, boolean active) {
        plate(context, x, y, BOX, BOX, active ? Plate.GOLD : Plate.TEAL);
    }

    /** A one-line text plate {@link #BOX} high, returns its width. */
    public static int textPlate(DrawContext context, int x, int y, Text text, Plate plate) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.textRenderer.getWidth(text) + 12;
        plate(context, x, y, width, BOX, plate);
        context.drawText(client.textRenderer, text, x + 6, y + (BOX - 8) / 2, TEXT, false);
        return width;
    }

    public static int textPlateWidth(Text text) {
        return MinecraftClient.getInstance().textRenderer.getWidth(text) + 12;
    }

    /** The see-through hint line, centred above the boxes. */
    public static void hint(DrawContext context, Text hint, int centerX, int boxesTop) {
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, hint, centerX, boxesTop - 10, HINT);
    }
}
