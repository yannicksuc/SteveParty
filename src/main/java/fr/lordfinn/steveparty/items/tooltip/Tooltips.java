package fr.lordfinn.steveparty.items.tooltip;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The one way the mod writes an item's tooltip (rules: Workshop docs/tooltips-ux.md), in this order:
 * <ol>
 *     <li>{@link #tags}: a line of badges just under the name, always shown ({@link Tag});</li>
 *     <li>{@link #state}: what this very stack holds or is set to, a neutral label and a coloured value;</li>
 *     <li>{@link #summary}: one short grey line, what it is for;</li>
 *     <li>{@link #more}: everything else (controls, crafting, rules), shown while Shift is held, else a dim hint.</li>
 * </ol>
 * Long lines are cut at {@link #WIDTH} characters, colours kept. Common code (appendTooltip runs on the client only,
 * but lives in classes a dedicated server loads): the Shift key is read through {@link #setShiftProbe}, set by the client.
 */
public final class Tooltips {
    /** About the widest a tooltip line should be, in characters (a tooltip line doesn't wrap by itself). */
    public static final int WIDTH = 40;
    private static final String KEY = "tooltip.steveparty.";
    /** Marks the badge line, so that tags added later (client callbacks) join it. */
    private static final String TAG_LINE_MARK = "steveparty:tags";

    // ------------------------------------------------------------------ colours, defined once
    public static final Formatting LABEL = Formatting.GRAY;
    public static final Formatting TEXT = Formatting.GRAY;
    public static final Formatting VALUE = Formatting.WHITE;
    public static final Formatting COINS = Formatting.GOLD;
    public static final Formatting SETTING = Formatting.AQUA;
    public static final Formatting GOOD = Formatting.GREEN;
    public static final Formatting BAD = Formatting.RED;
    public static final Formatting LOOK = Formatting.LIGHT_PURPLE;
    public static final Formatting KEYS = Formatting.YELLOW;
    public static final Formatting SECTION = Formatting.WHITE;
    public static final Formatting DIM = Formatting.DARK_GRAY;

    private static BooleanSupplier shiftProbe = () -> false;
    private static @Nullable Boolean forcedMore;
    private static boolean wrapping = true;

    private final List<Text> lines;

    private Tooltips(List<Text> lines) {
        this.lines = lines;
    }

    /** Writes into {@code lines}, the tooltip being built (its first line, the name, already there). */
    public static Tooltips of(List<Text> lines) {
        return new Tooltips(lines);
    }

    /** The client tells how to read the Shift key (Screen.hasShiftDown). */
    public static void setShiftProbe(BooleanSupplier probe) {
        shiftProbe = probe;
    }

    /** Tests: show the Shift part (true), the hint (false), or read the key again (null); no line cut (keys kept). */
    public static void forTests(@Nullable Boolean more) {
        forcedMore = more;
        wrapping = more == null;
    }

    public static boolean showsMore() {
        return forcedMore != null ? forcedMore : shiftProbe.getAsBoolean();
    }

    // ------------------------------------------------------------------ tags

    /** The badges of an item, always shown on one line under its name. */
    public enum Tag {
        CARTRIDGE(0xE89A3C), BOARD_SPACE(0x6EC6F0), POWER_UP(0xFF77FF), CONSUMED(0xFF5555), DIE(0xF0F0F0),
        DICE_FACE(0xD8D8D8), DICE_MODULE(0x7FA8FF), NEGATIVE(0xFF5555), PREMIUM(0xFFC94A), CURSED(0xB04CE0),
        MINI_GAME(0x6FE38A), SHOP(0xF2C230), TOOL(0xA9C6E3), PARTY(0xC79BFF), CONFIGURABLE(0xFCB017),
        STAMPED(0xFF77FF), SWITCHABLE(0x55FFFF), LINKED_COPY(0x55FFFF), COSTUME(0xD9A066), CREATURE(0x9BCB2C);

        public final int color;

        Tag(int color) {
            this.color = color;
        }

        public Text label() {
            return Text.translatable(KEY + "tag." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    public Tooltips tags(Tag... tags) {
        for (Tag tag : tags) tag(lines, tag.label(), tag.color);
        return this;
    }

    /** Adds the badge {@code [label]} in {@code rgb} to the badge line under the name (made if missing). */
    public static void tag(List<Text> lines, Text label, int rgb) {
        MutableText badge = Text.literal("[").append(label).append("]").styled(s -> s.withColor(TextColor.fromRgb(rgb)));
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            if (lines.get(i) instanceof MutableText line && TAG_LINE_MARK.equals(line.getStyle().getInsertion())) {
                line.append(" ").append(badge);
                return;
            }
        }
        lines.add(Math.min(1, lines.size()), Text.empty().styled(s -> s.withInsertion(TAG_LINE_MARK)).append(badge));
    }

    public static void tag(List<Text> lines, Tag tag) {
        tag(lines, tag.label(), tag.color);
    }

    // ------------------------------------------------------------------ state and summary

    /** "Label : value": {@code labelKey} translated in grey, {@code value} as given (coloured by its meaning). */
    public Tooltips state(String labelKey, Text value) {
        return state(Text.translatable(KEY + "state", Text.translatable(labelKey), value));
    }

    /** A state line already written (its parts coloured), grey by default. */
    public Tooltips state(Text line) {
        add(lines, line, TEXT, "");
        return this;
    }

    /** A state line that warns (something missing, broken), in red. */
    public Tooltips warn(Text line) {
        add(lines, line, BAD, "");
        return this;
    }

    public Tooltips summary(String key, Object... args) {
        return summary(Text.translatable(key, args));
    }

    /** What it is for: a sentence, so its first letter is a capital (descriptions shared with other places aren't). */
    public Tooltips summary(Text text) {
        int first = lines.size();
        add(lines, text, TEXT, "");
        if (wrapping && lines.size() > first) lines.set(first, capitalized(lines.get(first)));
        return this;
    }

    /** {@code line} (a line cut by {@link #add}: literal parts) with its first letter as a capital. */
    private static Text capitalized(Text line) {
        MutableText copy = line.copyContentOnly().setStyle(line.getStyle());
        boolean done = false;
        for (Text part : line.getSiblings()) {
            String string = part.getString();
            if (!done && !string.isEmpty()) {
                copy.append(Text.literal(string.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + string.substring(1)).setStyle(part.getStyle()));
                done = true;
            } else {
                copy.append(part);
            }
        }
        return copy;
    }

    /**
     * What is behind Shift: written only while Shift is held, else replaced by the hint « Shift: more info » (no hint
     * when there is nothing more). Call it last.
     */
    public Tooltips more(Consumer<More> details) {
        More more = new More();
        details.accept(more);
        if (more.isEmpty()) return this;
        if (showsMore()) more.writeTo(lines);
        else lines.add(Text.translatable(KEY + "more").formatted(DIM));
        return this;
    }

    /** The part behind Shift, written section by section in a set order, whatever the order of the calls. */
    public static final class More {
        private final List<Text> details = new ArrayList<>();
        private final List<Text> use = new ArrayList<>();
        private final List<Text> craft = new ArrayList<>();
        private final List<Text> notes = new ArrayList<>();

        /** A longer description, first, without a title. */
        public More detail(String key, Object... args) {
            details.add(Text.translatable(key, args));
            return this;
        }

        public More detail(Text text) {
            details.add(text);
            return this;
        }

        /** "[key] action", under « How to use ». */
        public More use(Text key, String actionKey, Object... args) {
            return use(key, Text.translatable(actionKey, args));
        }

        public More use(Text key, Text action) {
            use.add(Text.empty().append(Keys.badge(key)).append(" ").append(action));
            return this;
        }

        /** A line under « How to use » with no key (a way to use it that isn't a click). */
        public More use(String key, Object... args) {
            use.add(Text.translatable(key, args));
            return this;
        }

        public More craft(String key, Object... args) {
            craft.add(Text.translatable(key, args));
            return this;
        }

        public More note(String key, Object... args) {
            notes.add(Text.translatable(key, args));
            return this;
        }

        public More note(Text text) {
            notes.add(text);
            return this;
        }

        boolean isEmpty() {
            return details.isEmpty() && use.isEmpty() && craft.isEmpty() && notes.isEmpty();
        }

        void writeTo(List<Text> lines) {
            for (Text line : details) add(lines, line, TEXT, "");
            section(lines, "use", use);
            section(lines, "craft", craft);
            section(lines, "notes", notes);
        }

        private static void section(List<Text> lines, String title, List<Text> body) {
            if (body.isEmpty()) return;
            lines.add(Text.translatable(KEY + "section." + title).formatted(SECTION));
            for (Text line : body) add(lines, line, TEXT, " ");
        }
    }

    // ------------------------------------------------------------------ values

    public static MutableText value(Object value) {
        return text(value).formatted(VALUE);
    }

    public static MutableText coins(Object value) {
        return text(value).formatted(COINS);
    }

    public static MutableText setting(Object value) {
        return text(value).formatted(SETTING);
    }

    public static MutableText good(Object value) {
        return text(value).formatted(GOOD);
    }

    public static MutableText bad(Object value) {
        return text(value).formatted(BAD);
    }

    public static MutableText look(Object value) {
        return text(value).formatted(LOOK);
    }

    public static MutableText rgb(Object value, int rgb) {
        return text(value).styled(s -> s.withColor(TextColor.fromRgb(rgb)));
    }

    private static MutableText text(Object value) {
        return value instanceof Text t ? t.copy() : Text.literal(String.valueOf(value));
    }

    // ------------------------------------------------------------------ keys

    /** The names of the controls, as bound by the player. */
    public static final class Keys {
        private Keys() {
        }

        public static Text use() {
            return Text.keybind("key.use");
        }

        public static Text attack() {
            return Text.keybind("key.attack");
        }

        public static Text sneak() {
            return Text.keybind("key.sneak");
        }

        public static Text scroll() {
            return Text.translatable(KEY + "key.scroll");
        }

        /** Something held or done rather than a key (« Dye in the other hand »...). */
        public static Text of(String key, Object... args) {
            return Text.translatable(key, args);
        }

        /** Keys pressed together: « Left Shift + Right Button ». */
        public static Text combo(Text... keys) {
            MutableText combo = Text.empty();
            for (int i = 0; i < keys.length; i++) {
                if (i > 0) combo.append(" + ");
                combo.append(keys[i]);
            }
            return combo;
        }

        public static Text sneakUse() {
            return combo(sneak(), use());
        }

        public static Text sneakScroll() {
            return combo(sneak(), scroll());
        }

        static MutableText badge(Text key) {
            return Text.literal("[").append(key).append("]").formatted(KEYS);
        }
    }

    // ------------------------------------------------------------------ cutting long lines

    /** Adds {@code text} in lines of at most {@link #WIDTH} characters, its colours kept; the next lines indented. */
    public static void add(List<Text> lines, Text text, Formatting base, String indent) {
        if (!wrapping) {
            lines.add(Text.literal(indent).append(text.copy()).formatted(base));
            return;
        }
        List<List<Piece>> words = new ArrayList<>();
        List<Piece> word = new ArrayList<>();
        text.visit((style, string) -> {
            StringBuilder run = new StringBuilder();
            for (char c : string.toCharArray()) {
                if (c == ' ' || c == '\n') {
                    if (!run.isEmpty()) word.add(new Piece(run.toString(), style));
                    run.setLength(0);
                    if (!word.isEmpty()) words.add(new ArrayList<>(word));
                    word.clear();
                    if (c == '\n') words.add(List.of());
                } else {
                    run.append(c);
                }
            }
            if (!run.isEmpty()) word.add(new Piece(run.toString(), style));
            return Optional.empty();
        }, Style.EMPTY.withColor(base));
        if (!word.isEmpty()) words.add(word);
        // French punctuation (« : », « ; », « ! », « ? », « » ») never starts a line: kept with the word before
        for (int i = words.size() - 1; i > 0; i--) {
            List<Piece> w = words.get(i), before = words.get(i - 1);
            if (w.isEmpty() || before.isEmpty() || !";:!?»".contains(w.getFirst().string.substring(0, 1))) continue;
            List<Piece> joined = new ArrayList<>(before);
            joined.add(new Piece(" ", w.getFirst().style));
            joined.addAll(w);
            words.set(i - 1, joined);
            words.remove(i);
        }
        for (int i = words.size() - 2; i >= 0; i--) {
            List<Piece> w = words.get(i), after = words.get(i + 1);
            if (w.isEmpty() || after.isEmpty() || !w.getLast().string.endsWith("«")) continue;
            List<Piece> joined = new ArrayList<>(w);
            joined.add(new Piece(" ", w.getLast().style));
            joined.addAll(after);
            words.set(i, joined);
            words.remove(i + 1);
        }

        MutableText line = Text.literal(indent);
        int length = indent.length();
        boolean empty = true;
        String next = indent + "  ";
        for (List<Piece> w : words) {
            int size = w.stream().mapToInt(p -> p.string.length()).sum();
            if (w.isEmpty() || (!empty && length + 1 + size > WIDTH)) {
                lines.add(line);
                line = Text.literal(next);
                length = next.length();
                empty = true;
                if (w.isEmpty()) continue;
            }
            if (!empty) {
                line.append(" ");
                length++;
            }
            for (Piece p : w) line.append(Text.literal(p.string).setStyle(p.style));
            length += size;
            empty = false;
        }
        if (!empty) lines.add(line);
    }

    private record Piece(String string, Style style) {
    }
}
