package fr.lordfinn.steveparty.client.gui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * Text in a box, whatever its language: it never goes out of its box and is never cut short.
 * <ul>
 *     <li>{@link #wrapped}: on as many lines as it needs (measure the height with {@link #height} to size the box);</li>
 *     <li>{@link #line}: on one line; too long, in a screen it stays still, cut at its box, and scrolls slowly (in the
 *     reading direction, from its start) only while the mouse is over it, its whole text in a tooltip; in the HUD,
 *     where no mouse goes, it keeps scrolling.</li>
 * </ul>
 * The mod draws every text through here: {@code ./gradlew checkUiText} fails on a {@code drawText} elsewhere.
 */
public final class UiText {
    /** Height of a line of text. */
    public static final int LINE_H = 10;
    /** Widest a tooltip line goes before wrapping. */
    private static final int TOOLTIP_W = 200;
    /** Speed of a scrolling line (pixels per second) and its pause at each end. */
    private static final float SCROLL_SPEED = 18F;
    private static final long SCROLL_PAUSE_MS = 1200;

    /** The text of the line under the mouse that does not fit, shown in a tooltip after the screen (see {@link #initialize}). */
    private static @Nullable Text hovered;
    /** The box of the line the mouse is over (screen pixels), and since when: its scroll starts from its beginning. */
    private static int[] hoveredBox = new int[4];
    private static long hoveredSince, hoveredSeen;
    /** Another tooltip was drawn during this frame of the screen (an item's, a panel's...): this one is not drawn over it. */
    private static boolean otherTooltip;
    /** True while this class draws its own tooltip (not another one). */
    private static boolean drawingOwn;

    private UiText() {
    }

    /** Shows the tooltip of a hovered line that does not fit, on top of every screen. Once, by the client initializer. */
    public static void initialize() {
        ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> {
            ScreenEvents.beforeRender(screen).register((s, context, mouseX, mouseY, delta) -> otherTooltip = false);
            ScreenEvents.afterRender(screen).register((s, context, mouseX, mouseY, delta) -> {
                Text text = hovered;
                hovered = null;
                // The screen drew a tooltip of its own (a slot's item, an (i), a panel): that one is shown, not this one over it
                if (otherTooltip) return;
                // A hovered widget with a tooltip of its own: the same
                for (var child : s.children()) {
                    if (child instanceof net.minecraft.client.gui.widget.ClickableWidget widget && widget.visible
                            && widget.isHovered() && widget.getTooltip() != null) return;
                }
                if (text == null) return;
                drawingOwn = true;
                try {
                    context.drawOrderedTooltip(client.textRenderer, wrap(client.textRenderer, text, TOOLTIP_W), mouseX, mouseY);
                } finally {
                    drawingOwn = false;
                }
            });
        });
    }

    /** A tooltip is being drawn (every one goes through {@code DrawContextTooltipMixin}). */
    public static void tooltipDrawn() {
        if (!drawingOwn) otherTooltip = true;
    }

    /**
     * The screen shows its own tooltip this frame (a button's, a panel's): the one of a line that does not fit is not
     * drawn over it. Called by whoever draws that tooltip.
     */
    public static void cancelTooltip() {
        hovered = null;
    }

    // ---------------------------------------------------------------- on several lines

    /** {@code text} cut into lines at most {@code width} pixels wide (on words; a word too long is cut). */
    public static List<OrderedText> wrap(TextRenderer font, StringVisitable text, int width) {
        return font.wrapLines(text, Math.max(1, width));
    }

    /** The height {@code text} takes on lines of {@code width} pixels. */
    public static int height(TextRenderer font, StringVisitable text, int width) {
        return wrap(font, text, width).size() * LINE_H;
    }

    /** Draws {@code text} on lines of {@code width} pixels from ({@code x}, {@code y}). @return the height drawn */
    public static int wrapped(DrawContext context, TextRenderer font, StringVisitable text, int x, int y, int width, int color, boolean shadow) {
        return wrapped(context, font, text, x, y, width, color, shadow, false);
    }

    /** Same, each line centered in the box if {@code centered}. @return the height drawn */
    public static int wrapped(DrawContext context, TextRenderer font, StringVisitable text, int x, int y, int width,
                              int color, boolean shadow, boolean centered) {
        List<OrderedText> lines = wrap(font, text, width);
        for (int i = 0; i < lines.size(); i++) {
            OrderedText line = lines.get(i);
            int lx = centered ? x + (width - font.getWidth(line)) / 2 : x;
            context.drawText(font, line, lx, y + i * LINE_H, color, shadow);
        }
        return lines.size() * LINE_H;
    }

    // ---------------------------------------------------------------- on one line

    /** Draws {@code text} on one line in the box ({@code x}, {@code y}, {@code width}): see {@link UiText}. */
    public static void line(DrawContext context, TextRenderer font, Text text, int x, int y, int width, int color, boolean shadow) {
        line(context, font, text, x, y, width, color, shadow, false);
    }

    /** Same, centered in the box when it fits. */
    public static void centered(DrawContext context, TextRenderer font, Text text, int x, int y, int width, int color, boolean shadow) {
        line(context, font, text, x, y, width, color, shadow, true);
    }

    /** Draws a plain string on one line in the box. */
    public static void line(DrawContext context, TextRenderer font, String text, int x, int y, int width, int color, boolean shadow) {
        line(context, font, Text.literal(text), x, y, width, color, shadow, false);
    }

    /** Draws a plain string on one line, centered in the box when it fits. */
    public static void centered(DrawContext context, TextRenderer font, String text, int x, int y, int width, int color, boolean shadow) {
        line(context, font, Text.literal(text), x, y, width, color, shadow, true);
    }

    /**
     * Draws a line already cut (an {@link OrderedText}) in the box: as {@link #line(DrawContext, TextRenderer, Text, int,
     * int, int, int, boolean)}, its tooltip under the mouse included.
     */
    public static void line(DrawContext context, TextRenderer font, OrderedText text, int x, int y, int width, int color, boolean shadow) {
        orderedLine(context, font, text, x, y, width, color, shadow, false);
    }

    /** Same, centered in the box when it fits. */
    public static void centered(DrawContext context, TextRenderer font, OrderedText text, int x, int y, int width, int color, boolean shadow) {
        orderedLine(context, font, text, x, y, width, color, shadow, true);
    }

    private static void orderedLine(DrawContext context, TextRenderer font, OrderedText text, int x, int y, int width, int color,
                                    boolean shadow, boolean center) {
        int textWidth = font.getWidth(text);
        if (textWidth <= width) {
            context.drawText(font, text, center ? x + (width - textWidth) / 2 : x, y, color, shadow);
            return;
        }
        if (width <= 0) return;
        int[] box = onScreen(context, x, y - 1, x + width, y + LINE_H);
        float offset = offset(box, textWidth - width, Text.literal(plain(text)));
        context.enableScissor(box[0], box[1], box[2], box[3]);
        context.getMatrices().push();
        context.getMatrices().translate(-offset, 0, 0);
        context.drawText(font, text, x, y, color, shadow);
        context.getMatrices().pop();
        context.disableScissor();
    }

    /** The characters of an {@link OrderedText}, its styles dropped (for its tooltip). */
    private static String plain(OrderedText text) {
        StringBuilder out = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            out.appendCodePoint(codePoint);
            return true;
        });
        return out.toString();
    }

    private static void line(DrawContext context, TextRenderer font, Text text, int x, int y, int width, int color,
                             boolean shadow, boolean center) {
        int textWidth = font.getWidth(text);
        if (textWidth <= width) {
            context.drawText(font, text, center ? x + (width - textWidth) / 2 : x, y, color, shadow);
            return;
        }
        if (width <= 0) return;
        // Too long: cut at its box, scrolling under the mouse (always in the HUD), the mouse over it shows it whole
        int[] box = onScreen(context, x, y - 1, x + width, y + LINE_H);
        float offset = offset(box, textWidth - width, text);
        context.enableScissor(box[0], box[1], box[2], box[3]);
        context.getMatrices().push();
        context.getMatrices().translate(-offset, 0, 0);
        context.drawText(font, text, x, y, color, shadow);
        context.getMatrices().pop();
        context.disableScissor();
    }

    /**
     * How far a line {@code travel} pixels too long is scrolled now, only in the reading direction: still at its
     * start, to its end, still at its end, then back to its start at once.
     */
    static float scrollOffset(int travel) {
        return scrollOffset(travel, Util.getMeasuringTimeMs());
    }

    /** Same, {@code elapsedMs} into its scroll. */
    static float scrollOffset(int travel, long elapsedMs) {
        long moveMs = Math.max(1, (long) (travel / SCROLL_SPEED * 1000F));
        long cycle = 2 * SCROLL_PAUSE_MS + moveMs;
        long t = elapsedMs % cycle;
        if (t < SCROLL_PAUSE_MS) return 0;
        if (t < SCROLL_PAUSE_MS + moveMs) return (t - SCROLL_PAUSE_MS) / (float) moveMs * travel;
        return travel;
    }

    /** The box (x1, y1, x2, y2) on the screen: where the current transformation puts it (a panel sliding in...). */
    private static int[] onScreen(DrawContext context, int x1, int y1, int x2, int y2) {
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        Vector3f a = matrix.transformPosition(new Vector3f(x1, y1, 0)), b = matrix.transformPosition(new Vector3f(x2, y2, 0));
        return new int[]{Math.round(Math.min(a.x, b.x)), Math.round(Math.min(a.y, b.y)),
                Math.round(Math.max(a.x, b.x)), Math.round(Math.max(a.y, b.y))};
    }

    /**
     * How far a line {@code travel} pixels too long, drawn in {@code box}, is scrolled now: in the HUD it keeps
     * scrolling; in a screen it stays at its start unless the mouse is over it, then it scrolls from its start and its
     * whole text is shown in a tooltip.
     */
    private static float offset(int[] box, int travel, Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen == null) return scrollOffset(travel);
        double scale = client.getWindow().getScaleFactor();
        double mouseX = client.mouse.getX() / scale, mouseY = client.mouse.getY() / scale;
        if (mouseX < box[0] || mouseX >= box[2] || mouseY < box[1] || mouseY >= box[3]) return 0;
        hovered = text;
        long now = Util.getMeasuringTimeMs();
        // Another line, or the same one hovered again: from its start
        if (!java.util.Arrays.equals(box, hoveredBox) || now - hoveredSeen > 250) {
            hoveredBox = box;
            hoveredSince = now;
        }
        hoveredSeen = now;
        return scrollOffset(travel, now - hoveredSince);
    }
}
