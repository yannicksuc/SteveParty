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
 *     <li>{@link #line}: on one line; too long, it scrolls slowly back and forth inside its box, and its whole text is
 *     shown in a tooltip while the mouse is over it (in a screen).</li>
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

    private UiText() {
    }

    /** Shows the tooltip of a hovered line that does not fit, on top of every screen. Once, by the client initializer. */
    public static void initialize() {
        ScreenEvents.BEFORE_INIT.register((client, screen, width, height) ->
                ScreenEvents.afterRender(screen).register((s, context, mouseX, mouseY, delta) -> {
                    Text text = hovered;
                    hovered = null;
                    if (text != null) context.drawOrderedTooltip(client.textRenderer, wrap(client.textRenderer, text, TOOLTIP_W), mouseX, mouseY);
                }));
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
        float offset = scrollOffset(textWidth - width);
        int[] box = onScreen(context, x, y - 1, x + width, y + LINE_H);
        context.enableScissor(box[0], box[1], box[2], box[3]);
        context.getMatrices().push();
        context.getMatrices().translate(-offset, 0, 0);
        context.drawText(font, text, x, y, color, shadow);
        context.getMatrices().pop();
        context.disableScissor();
        noteHover(box, Text.literal(plain(text)));
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
        // Too long: it scrolls inside its box, and the mouse over it shows it whole
        float offset = scrollOffset(textWidth - width);
        int[] box = onScreen(context, x, y - 1, x + width, y + LINE_H);
        context.enableScissor(box[0], box[1], box[2], box[3]);
        context.getMatrices().push();
        context.getMatrices().translate(-offset, 0, 0);
        context.drawText(font, text, x, y, color, shadow);
        context.getMatrices().pop();
        context.disableScissor();
        noteHover(box, text);
    }

    /** How far a line {@code travel} pixels too long is scrolled now: still, to its end, still, back. */
    static float scrollOffset(int travel) {
        long moveMs = Math.max(1, (long) (travel / SCROLL_SPEED * 1000F));
        long cycle = 2 * (SCROLL_PAUSE_MS + moveMs);
        long t = Util.getMeasuringTimeMs() % cycle;
        if (t < SCROLL_PAUSE_MS) return 0;
        if (t < SCROLL_PAUSE_MS + moveMs) return (t - SCROLL_PAUSE_MS) / (float) moveMs * travel;
        if (t < 2 * SCROLL_PAUSE_MS + moveMs) return travel;
        return travel - (t - 2 * SCROLL_PAUSE_MS - moveMs) / (float) moveMs * travel;
    }

    /** The box (x1, y1, x2, y2) on the screen: where the current transformation puts it (a panel sliding in...). */
    private static int[] onScreen(DrawContext context, int x1, int y1, int x2, int y2) {
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        Vector3f a = matrix.transformPosition(new Vector3f(x1, y1, 0)), b = matrix.transformPosition(new Vector3f(x2, y2, 0));
        return new int[]{Math.round(Math.min(a.x, b.x)), Math.round(Math.min(a.y, b.y)),
                Math.round(Math.max(a.x, b.x)), Math.round(Math.max(a.y, b.y))};
    }

    /** In a screen, the mouse over a line that does not fit: its whole text in a tooltip. */
    private static void noteHover(int[] box, Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen == null) return;
        double scale = client.getWindow().getScaleFactor();
        double mouseX = client.mouse.getX() / scale, mouseY = client.mouse.getY() / scale;
        if (mouseX >= box[0] && mouseX < box[2] && mouseY >= box[1] && mouseY < box[3]) hovered = text;
    }
}
