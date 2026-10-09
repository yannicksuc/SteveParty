package fr.lordfinn.steveparty.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * One visual language for the HUDs of the mod's tools held in hand: small plates just above the hotbar (a tool's whole
 * HUD is laid out by {@link ToolHudPanel}), cut like the mod's screens (teal like the Tile's, gold when active: see
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
        TEAL, GOLD, GREEN, RED, ORANGE, PURPLE;

        final Identifier sprite = Steveparty.id("board/plate_" + name().toLowerCase());
    }

    private ToolHud() {
    }

    /**
     * Top of the boxes: where vanilla writes the held item's name, which is not shown while a tool HUD is (see
     * InGameHudToolHudMixin): just above the hotbar, or the health, armour and air rows (as high as they climb).
     */
    public static int top(DrawContext context) {
        return bottom(context) - BOX;
    }

    /** Bottom of a tool HUD: just above the hotbar, or the health, armour and air rows (as high as they climb). */
    public static int bottom(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean statusBars = client.interactionManager != null && client.interactionManager.hasStatusBars();
        int above = statusBars && client.player != null ? statusRowsTop(client.player) : HOTBAR_TOP;
        return context.getScaledWindowHeight() - above - 2;
    }

    /** The top of the hotbar above the screen's bottom. */
    private static final int HOTBAR_TOP = 23;

    /** How high the status rows reach above the screen's bottom: hearts (several rows), armour, air. */
    private static int statusRowsTop(PlayerEntity player) {
        float health = Math.max((float) player.getAttributeValue(EntityAttributes.GENERIC_MAX_HEALTH), player.getHealth());
        int absorption = MathHelper.ceil(player.getAbsorptionAmount());
        int lines = MathHelper.ceil((health + absorption) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (lines - 2), 3);
        int left = 39 + (lines - 1) * rowHeight + (player.getArmor() > 0 ? 10 : 0);
        int right = player.getAir() < player.getMaxAir() || player.isSubmergedInWater() ? 49 : 39;
        return Math.max(left, right);
    }

    /** A plate (nine-slice) over (x, y, width, height). */
    public static void plate(DrawContext context, int x, int y, int width, int height, Plate plate) {
        RenderSystem.enableBlend();
        context.drawGuiTexture(plate.sprite, x, y, width, height);
        RenderSystem.disableBlend();
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

    /**
     * The see-through hint, centred above the boxes: wrapped on several lines (going up) when wider than the screen
     * (large GUI scales, long translations).
     */
    public static void hint(DrawContext context, Text hint, int centerX, int boxesTop) {
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        var lines = textRenderer.wrapLines(hint, available(context));
        occupy(boxesTop - 10 * lines.size());
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            int y = boxesTop - 10 * (lines.size() - i);
            context.drawCenteredTextWithShadow(textRenderer, line, centerX, y, HINT);
        }
    }

    // ---------------------------------------------------------------- room for the action bar

    /** Top of the tool HUD drawn last (its hint included), and how many vanilla HUD frames ago. */
    private static int occupiedTop;
    private static int framesSinceDrawn = Integer.MAX_VALUE;

    /**
     * How far up a vanilla HUD text whose bottom is {@code bottomFromScreenBottom} pixels above the screen's bottom
     * must go to clear the tool HUD (0 when none is shown). Called by the action bar renderer, before the tool HUDs of
     * the frame: the previous frame's layout is used.
     */
    public static int liftFor(DrawContext context, int bottomFromScreenBottom) {
        if (framesSinceDrawn != Integer.MAX_VALUE) framesSinceDrawn++;
        if (framesSinceDrawn > 4) return 0;
        return Math.max(0, context.getScaledWindowHeight() - bottomFromScreenBottom - (occupiedTop - 2));
    }

    /** A tool HUD reaching up to {@code top} is drawn this frame (the action bar goes above it). */
    public static void occupy(int top) {
        occupiedTop = top;
        framesSinceDrawn = 0;
    }

    /** Top of the tool HUD drawn in the last frames (its hint included), -1 when none is shown. */
    public static int occupiedTop() {
        return framesSinceDrawn <= 4 ? occupiedTop : -1;
    }

    // ---------------------------------------------------------------- layout

    /** Something drawn in a HUD row: a box, a plate... */
    public interface Element {
        int width();

        void draw(DrawContext context, int x, int y);
    }

    public static Element element(int width, BiConsumer<Integer, Integer> draw) {
        return new Element() {
            @Override
            public int width() {
                return width;
            }

            @Override
            public void draw(DrawContext context, int x, int y) {
                draw.accept(x, y);
            }
        };
    }

    /** Room for a row: the screen width but a small margin on each side. */
    public static int available(DrawContext context) {
        return context.getScaledWindowWidth() - 16;
    }

    /**
     * Draws groups of elements centred right above the hotbar: all on one row if they fit, else one row per group
     * (the last group on the lowest row). Gaps of {@code gap} pixels between elements.
     *
     * @return the top of the highest row (where the hint goes above)
     */
    public static int rows(DrawContext context, List<List<Element>> groups, int gap) {
        List<List<Element>> rows = new ArrayList<>();
        List<Element> all = new ArrayList<>();
        groups.forEach(all::addAll);
        if (width(all, gap) <= available(context)) rows.add(all);
        else rows.addAll(groups);
        int bottom = top(context);
        int centerX = context.getScaledWindowWidth() / 2;
        for (int i = 0; i < rows.size(); i++) {
            List<Element> row = rows.get(i);
            int y = bottom - (rows.size() - 1 - i) * (BOX + 2);
            int x = centerX - width(row, gap) / 2;
            for (Element element : row) {
                element.draw(context, x, y);
                x += element.width() + gap;
            }
        }
        return bottom - (rows.size() - 1) * (BOX + 2);
    }

    private static int width(List<Element> row, int gap) {
        int width = 0;
        for (Element element : row) width += element.width();
        return width + Math.max(0, row.size() - 1) * gap;
    }
}
