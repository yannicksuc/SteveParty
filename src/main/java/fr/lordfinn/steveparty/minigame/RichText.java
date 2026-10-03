package fr.lordfinn.steveparty.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * A text with a style per character, as the page editor edits it: what {@link MiniGameText}'s markup (the {@code &}
 * codes, the stored form) means, without the codes. Read from the markup with {@link #fromCodes}, written back with
 * {@link #toCodes}: the same text, with as few codes as possible.
 * <p>
 * The markup's rules are kept: a colour ends the styles before it ({@code &c} after {@code &l} is red, not bold), so a
 * style that stops is written as its colour (or {@code &r} without colour) followed by the styles that go on.
 */
public final class RichText {
    /**
     * The style of a character.
     *
     * @param color the colour's code ({@code 'c'}: red...), 0 for none (the text's own colour)
     */
    public record Style(boolean bold, boolean italic, boolean underline, boolean strike, char color) {
        public static final Style PLAIN = new Style(false, false, false, false, (char) 0);

        public Style withBold(boolean bold) {
            return new Style(bold, italic, underline, strike, color);
        }

        public Style withItalic(boolean italic) {
            return new Style(bold, italic, underline, strike, color);
        }

        public Style withColor(char color) {
            return new Style(bold, italic, underline, strike, color);
        }
    }

    private final StringBuilder chars = new StringBuilder();
    private final List<Style> styles = new ArrayList<>();

    public RichText() {
    }

    // ------------------------------------------------------------------ markup

    private static boolean isColor(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
    }

    /** The text the markup stands for (see {@link MiniGameText}). */
    public static RichText fromCodes(String markup) {
        RichText text = new RichText();
        Style style = Style.PLAIN;
        for (int i = 0; i < markup.length(); i++) {
            char c = markup.charAt(i);
            if (c == '&' && i + 1 < markup.length()) {
                char next = Character.toLowerCase(markup.charAt(i + 1));
                if (next == '&') {
                    text.append('&', style);
                    i++;
                    continue;
                }
                Style changed = switch (next) {
                    case 'l' -> style.withBold(true);
                    case 'o' -> style.withItalic(true);
                    case 'n' -> new Style(style.bold, style.italic, true, style.strike, style.color);
                    case 'm' -> new Style(style.bold, style.italic, style.underline, true, style.color);
                    case 'r' -> Style.PLAIN;
                    default -> isColor(next) ? Style.PLAIN.withColor(next) : null;
                };
                if (changed != null) {
                    style = changed;
                    i++;
                    continue;
                }
            }
            text.append(c, style);
        }
        return text;
    }

    /** The markup of this text: its codes where its style changes, {@code &&} for a plain {@code &}. */
    public String toCodes() {
        StringBuilder out = new StringBuilder();
        Style current = Style.PLAIN;
        for (int i = 0; i < chars.length(); i++) {
            Style style = styles.get(i);
            if (!style.equals(current)) {
                boolean stops = (current.bold && !style.bold) || (current.italic && !style.italic) || (current.underline && !style.underline)
                        || (current.strike && !style.strike) || current.color != style.color;
                if (stops) {
                    // A colour (or the reset) ends every style: the ones that go on are written again
                    out.append('&').append(style.color != 0 ? style.color : 'r');
                    current = Style.PLAIN.withColor(style.color);
                }
                if (style.bold && !current.bold) out.append("&l");
                if (style.italic && !current.italic) out.append("&o");
                if (style.underline && !current.underline) out.append("&n");
                if (style.strike && !current.strike) out.append("&m");
                current = style;
            }
            char c = chars.charAt(i);
            out.append(c == '&' ? "&&" : String.valueOf(c));
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ reading

    /** The characters shown (what the counter counts). */
    public String plain() {
        return chars.toString();
    }

    public int length() {
        return chars.length();
    }

    public char charAt(int index) {
        return chars.charAt(index);
    }

    public Style styleAt(int index) {
        return styles.get(index);
    }

    /** The style a character typed at {@code caret} takes: the one before it (after it at the start). */
    public Style styleFor(int caret) {
        if (chars.isEmpty()) return Style.PLAIN;
        if (caret > 0) {
            // After a line break, the line's first character's
            if (chars.charAt(caret - 1) == '\n' && caret < chars.length()) return styles.get(caret);
            return styles.get(Math.min(caret, chars.length()) - 1);
        }
        return styles.getFirst();
    }

    /** Whether every character of the range (or the style typed at {@code from} for an empty range) is so. */
    public boolean all(int from, int to, Predicate<Style> test) {
        if (from >= to) return test.test(styleFor(from));
        for (int i = from; i < to; i++) if (chars.charAt(i) != '\n' && !test.test(styles.get(i))) return false;
        return true;
    }

    /** Lines: its line breaks plus one. */
    public int lines() {
        int lines = 1;
        for (int i = 0; i < chars.length(); i++) if (chars.charAt(i) == '\n') lines++;
        return lines;
    }

    public RichText sub(int from, int to) {
        RichText text = new RichText();
        for (int i = from; i < to; i++) text.append(chars.charAt(i), styles.get(i));
        return text;
    }

    public RichText copy() {
        return sub(0, length());
    }

    // ------------------------------------------------------------------ editing

    private void append(char c, Style style) {
        chars.append(c);
        styles.add(style);
    }

    public void insert(int at, String text, Style style) {
        for (int i = 0; i < text.length(); i++) {
            chars.insert(at + i, text.charAt(i));
            styles.add(at + i, style);
        }
    }

    public void insert(int at, RichText text) {
        for (int i = 0; i < text.length(); i++) {
            chars.insert(at + i, text.charAt(i));
            styles.add(at + i, text.styleAt(i));
        }
    }

    public void delete(int from, int to) {
        if (from >= to) return;
        chars.delete(from, to);
        styles.subList(from, to).clear();
    }

    /** Changes the style of a range. */
    public void apply(int from, int to, UnaryOperator<Style> change) {
        for (int i = from; i < to; i++) styles.set(i, change.apply(styles.get(i)));
    }

    /** Keeps the first {@code max} characters. */
    public void truncate(int max) {
        if (length() > max) delete(max, length());
    }

    // ------------------------------------------------------------------ words

    private boolean isWord(int index) {
        return index >= 0 && index < chars.length() && Character.isLetterOrDigit(chars.charAt(index));
    }

    /** The start of the word before {@code caret} (Ctrl+Left): spaces skipped, then letters. */
    public int wordLeft(int caret) {
        int i = caret;
        while (i > 0 && !isWord(i - 1)) i--;
        while (i > 0 && isWord(i - 1)) i--;
        return i;
    }

    /** The end of the word after {@code caret} (Ctrl+Right). */
    public int wordRight(int caret) {
        int i = caret;
        while (i < chars.length() && !isWord(i)) i++;
        while (i < chars.length() && isWord(i)) i++;
        return i;
    }

    /** The word round {@code index} (a double click): {start, end}, the character alone if it is none. */
    public int[] wordAt(int index) {
        if (!isWord(index)) return new int[]{index, Math.min(chars.length(), index + 1)};
        int start = index, end = index;
        while (start > 0 && isWord(start - 1)) start--;
        while (end < chars.length() && isWord(end)) end++;
        return new int[]{start, end};
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RichText text && text.chars.toString().equals(chars.toString()) && text.styles.equals(styles);
    }

    @Override
    public int hashCode() {
        return 31 * chars.toString().hashCode() + styles.hashCode();
    }

    @Override
    public String toString() {
        return toCodes();
    }
}
