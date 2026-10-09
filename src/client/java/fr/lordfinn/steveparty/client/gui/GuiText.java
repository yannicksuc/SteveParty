package fr.lordfinn.steveparty.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

/** Text that must fit in a width: cut with « … » when it is longer. */
public final class GuiText {
    public static final String ELLIPSIS = "…";

    private GuiText() {
    }

    /** {@code text} as it fits in {@code width} pixels: as is, or cut with « … ». */
    public static String fit(TextRenderer textRenderer, String text, int width) {
        return textRenderer.getWidth(text) <= width ? text : cut(textRenderer, text, width);
    }

    /** {@code text} as it fits in {@code width} pixels: as is, or cut with « … ». */
    public static OrderedText fit(TextRenderer textRenderer, Text text, int width) {
        if (textRenderer.getWidth(text) <= width) return text.asOrderedText();
        return Language.getInstance().reorder(cut(textRenderer, text, width));
    }

    /** {@code text} cut to end with « … » within {@code width} pixels (the spaces before the « … » dropped). */
    public static String cut(TextRenderer textRenderer, String text, int width) {
        return textRenderer.trimToWidth(text, Math.max(0, width - textRenderer.getWidth(ELLIPSIS))).stripTrailing() + ELLIPSIS;
    }

    /** {@code text} cut to end with « … » within {@code width} pixels, its styles kept. */
    public static StringVisitable cut(TextRenderer textRenderer, StringVisitable text, int width) {
        return StringVisitable.concat(textRenderer.trimToWidth(text, Math.max(0, width - textRenderer.getWidth(ELLIPSIS))), StringVisitable.plain(ELLIPSIS));
    }
}
