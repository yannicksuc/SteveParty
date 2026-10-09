package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.client.gui.paint.PixelArt;
import fr.lordfinn.steveparty.utils.Argb;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Drawing helpers in the look of the mod's GUI textures (hop switch, router, trading stall...): flat pixel art,
 * a 1 px near-black outline with rounded corners, a light bevel on the top-left and a dark one on the bottom-right.
 * Everything is drawn with fills, so panels and buttons of any size keep crisp 1:1 pixels.
 */
public final class PartyGui {
    /** Colours of a bevelled shape: outline, highlight (top-left), body, shadow (bottom-right). */
    public record Theme(int outline, int highlight, int body, int shadow) {
        public Theme brighter() {
            return new Theme(outline, Argb.lighten(highlight, 0.35f), Argb.lighten(body, 0.18f), Argb.lighten(shadow, 0.12f));
        }
    }

    /** The light grey panel of the mod's inventories (hop_switch.png, router.png...). */
    public static final Theme PANEL = new Theme(0xFF000000, 0xFFFFFFFF, 0xFFCACACA, 0xFF515151);
    /** Goal pole: the red of its flag. */
    public static final Theme FLAG_RED = new Theme(0xFF33030A, 0xFFFF8F8F, 0xFFD9283B, 0xFF8E1022);
    /** Goal pole base: its brick and redstone. */
    public static final Theme BRICK = new Theme(0xFF2A0F05, 0xFFF2A277, 0xFFB5552C, 0xFF6B2A13);
    /** Stencil maker: its dark steel. */
    public static final Theme STEEL = new Theme(0xFF101418, 0xFFA9B4BE, 0xFF5E6974, 0xFF333A42);
    /** Buttons. */
    public static final Theme BUTTON = new Theme(0xFF000000, 0xFFFFFFFF, 0xFFE2E2E2, 0xFF8A8A8A);
    public static final Theme BUTTON_SELECTED = new Theme(0xFF3B2600, 0xFFFFF2A8, 0xFFFFC52E, 0xFFB5761A);
    public static final Theme BUTTON_PRIMARY = new Theme(0xFF08270A, 0xFFA6EF8A, 0xFF46AE2E, 0xFF1F6A14);
    public static final Theme BUTTON_DISABLED = new Theme(0xFF3A3A3A, 0xFFBDBDBD, 0xFFA3A3A3, 0xFF7A7A7A);

    public static final int TEXT_DARK = 0xFF404040;
    public static final int TEXT_SOFT = 0xFF6B6B6B;
    public static final int TEXT_ERROR = 0xFFB3202A;
    public static final int TEXT_OK = 0xFF2E7D1F;
    /** The slot's grey, see-through, over a faded item (see {@link #ghostItem}). */
    public static final int GHOST_VEIL = 0x998B8B8B;
    /** Inset (fields, boxes): dark top-left edge like the slots of the mod's textures. */
    private static final int INSET_EDGE = 0xFF354A55;
    private static final int INSET_EDGE_ERROR = 0xFFB3202A;
    /** A focused field: its own edging turns gold, like the mod's selected plates. */
    private static final int INSET_EDGE_FOCUS = 0xFFFFC52E;

    private PartyGui() {}

    /** Large shape: 2 px bevels and 2 px rounded corners, exactly like the mod's panel textures. */
    public static void panel(DrawContext context, int x, int y, int w, int h, Theme theme) {
        int o = theme.outline();
        context.fill(x + 2, y, x + w - 2, y + 1, o);
        context.fill(x + 2, y + h - 1, x + w - 2, y + h, o);
        context.fill(x, y + 2, x + 1, y + h - 2, o);
        context.fill(x + w - 1, y + 2, x + w, y + h - 2, o);
        pixel(context, x + 1, y + 1, o);
        pixel(context, x + w - 2, y + 1, o);
        pixel(context, x + 1, y + h - 2, o);
        pixel(context, x + w - 2, y + h - 2, o);

        context.fill(x + 1, y + 2, x + w - 1, y + h - 2, theme.body());
        context.fill(x + 2, y + 1, x + w - 2, y + h - 1, theme.body());

        context.fill(x + 2, y + 1, x + w - 3, y + 3, theme.highlight());
        context.fill(x + 1, y + 2, x + 3, y + h - 3, theme.highlight());
        pixel(context, x + 3, y + 3, theme.highlight());

        context.fill(x + 3, y + h - 3, x + w - 2, y + h - 1, theme.shadow());
        context.fill(x + w - 3, y + 3, x + w - 1, y + h - 2, theme.shadow());
        pixel(context, x + w - 4, y + h - 4, theme.shadow());
    }

    /** Small shape (buttons): 1 px bevels and 1 px rounded corners. {@code pressed} swaps the bevels. */
    public static void button(DrawContext context, int x, int y, int w, int h, Theme theme, boolean pressed) {
        int o = theme.outline();
        context.fill(x + 1, y, x + w - 1, y + 1, o);
        context.fill(x + 1, y + h - 1, x + w - 1, y + h, o);
        context.fill(x, y + 1, x + 1, y + h - 1, o);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, o);
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, theme.body());
        int light = pressed ? theme.shadow() : theme.highlight();
        int dark = pressed ? theme.highlight() : theme.shadow();
        context.fill(x + 1, y + 1, x + w - 2, y + 2, light);
        context.fill(x + 1, y + 2, x + 2, y + h - 2, light);
        context.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, dark);
        context.fill(x + w - 2, y + 1, x + w - 1, y + h - 2, dark);
    }

    /**
     * Sunken box (text fields, info boxes): dark rounded top-left edge like the slots of the mod's textures, and
     * a white bottom-right edge. The dark edge turns red on {@code error}. When {@code focused}, the whole edging
     * (the rounded top-left edge and the bottom-right one) turns gold: the same shape, the same single pixel, no frame
     * added inside.
     */
    public static void inset(DrawContext context, int x, int y, int w, int h, int body, boolean focused, boolean error) {
        int edge = error ? INSET_EDGE_ERROR : focused ? INSET_EDGE_FOCUS : INSET_EDGE;
        int low = focused ? INSET_EDGE_FOCUS : 0xFFFFFFFF;
        PixelArt.inset(context, x, y, w, h, body, edge, low);
        if (error && !focused) context.drawBorder(x + 1, y + 1, w - 1, h - 1, 0xFFE0707A);
    }

    /** A title plate straddling the top edge of a panel: bevelled, in the block's colour, with a shadowed title. */
    public static void titlePlate(DrawContext context, TextRenderer textRenderer, int centerX, int y, int iconSpace,
                                  Text title, Theme theme) {
        int w = textRenderer.getWidth(title) + iconSpace + 20;
        int x = centerX - w / 2;
        panel(context, x, y, w, 22, theme);
        context.drawTextWithShadow(textRenderer, title, x + 10 + iconSpace, y + 7, 0xFFFFFFFF);
    }

    /** @return the x where {@link #titlePlate} draws its icon (16 px wide), for the same arguments. */
    public static int titlePlateIconX(TextRenderer textRenderer, int centerX, int iconSpace, Text title) {
        int w = textRenderer.getWidth(title) + iconSpace + 20;
        return centerX - w / 2 + 7;
    }

    /** Green tick or red cross, 7x7, for field validation. */
    public static void statusIcon(DrawContext context, int x, int y, boolean ok) {
        int color = ok ? 0xFF3CB02A : 0xFFD8323F;
        int shade = ok ? 0xFF14510C : 0xFF5E0A12;
        String[] shape = ok
                ? new String[]{"......#", ".....##", "#...##.", "##.##..", ".###...", "..#....", "......."}
                : new String[]{"##...##", ".##.##.", "..###..", "..###..", ".##.##.", "##...##", "......."};
        for (int row = 0; row < shape.length; row++) {
            for (int col = 0; col < shape[row].length(); col++) {
                if (shape[row].charAt(col) != '#') continue;
                pixel(context, x + col + 1, y + row + 1, shade);
                pixel(context, x + col, y + row, color);
            }
        }
    }

    /**
     * A faded item: what an empty slot takes, or a ghost card. The item (and its count when {@code overlay} is
     * given) under the slot's grey, drawn above the GUI's items so that it veils them.
     */
    public static void ghostItem(DrawContext context, ItemStack stack, int x, int y, @Nullable TextRenderer overlay) {
        ghostItem(context, stack, x, y, overlay, GHOST_VEIL);
    }

    /** {@link #ghostItem(DrawContext, ItemStack, int, int, TextRenderer)} under a veil of {@code veil} (ARGB). */
    public static void ghostItem(DrawContext context, ItemStack stack, int x, int y, @Nullable TextRenderer overlay, int veil) {
        context.drawItem(stack, x, y);
        if (overlay != null) context.drawItemInSlot(overlay, stack, x, y);
        veil(context, x, y, veil);
    }

    /** A 16 x 16 veil of {@code colour} (ARGB) over the item drawn at ({@code x}, {@code y}): above the GUI's items. */
    public static void veil(DrawContext context, int x, int y, int colour) {
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 250);
        context.fill(x, y, x + 16, y + 16, colour);
        context.getMatrices().pop();
    }

    public static void pixel(DrawContext context, int x, int y, int color) {
        context.fill(x, y, x + 1, y + 1, color);
    }
}
