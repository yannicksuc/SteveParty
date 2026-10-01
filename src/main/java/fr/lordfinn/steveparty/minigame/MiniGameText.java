package fr.lordfinn.steveparty.minigame;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * The texts of the mini-game pages (description, texts of the introduction): plain text with a small markup, the
 * formatting codes of the game written with {@code &}:
 * <ul>
 *     <li>{@code &l} bold, {@code &o} italic, {@code &n} underlined, {@code &m} struck through;</li>
 *     <li>{@code &0} to {@code &f} a colour (it ends the styles before it, like in the game);</li>
 *     <li>{@code &r} back to plain text; {@code &&} a plain {@code &}.</li>
 * </ul>
 * The markup is what is stored and edited; {@link #parse} makes the text shown (card, tooltips, chat).
 */
public final class MiniGameText {
    private MiniGameText() {
    }

    private static Formatting code(char c) {
        Formatting formatting = Formatting.byCode(Character.toLowerCase(c));
        return formatting == Formatting.OBFUSCATED ? null : formatting;
    }

    /** The text shown for {@code markup}, drawn in {@code base} where the markup says nothing. */
    public static MutableText parse(String markup, Style base) {
        MutableText text = Text.empty();
        Style style = base;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < markup.length(); i++) {
            char c = markup.charAt(i);
            if (c == '&' && i + 1 < markup.length()) {
                char next = markup.charAt(i + 1);
                if (next == '&') {
                    run.append('&');
                    i++;
                    continue;
                }
                Formatting formatting = code(next);
                if (formatting != null) {
                    if (!run.isEmpty()) {
                        text.append(Text.literal(run.toString()).setStyle(style));
                        run.setLength(0);
                    }
                    // A colour or a reset ends the styles before it
                    style = formatting == Formatting.RESET ? base : formatting.isColor() ? base.withColor(formatting) : style.withFormatting(formatting);
                    i++;
                    continue;
                }
            }
            run.append(c);
        }
        if (!run.isEmpty()) text.append(Text.literal(run.toString()).setStyle(style));
        return text;
    }

    public static MutableText parse(String markup) {
        return parse(markup, Style.EMPTY);
    }

    /** The markup without its codes: the plain text. */
    public static String strip(String markup) {
        return parse(markup).getString();
    }

    /**
     * Cuts a text in lines of about {@code width} characters, at the spaces (a word longer than a line stays whole);
     * its own line breaks are kept.
     */
    public static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
            lines.add(line.toString());
        }
        return lines;
    }
}
