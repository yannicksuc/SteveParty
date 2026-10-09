package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.minigame.RichText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A text box that edits a formatted text as it is shown (bold, italic, colours: {@link RichText}), never its codes,
 * like a text box of any application: a caret placed with the mouse, a selection by dragging, Shift + arrows or a double
 * click (a word), Ctrl + A / C / X / V, Ctrl + Z / Y (undo, redo), Home / End, Ctrl + arrows (by word), several lines
 * (Enter), word wrap, a scroll bar and the mouse wheel when the text is longer than the box, the caret always shown.
 * Formatting applies to the selection, or to what is typed next when nothing is selected (Ctrl + B, Ctrl + I, or the
 * toolbar of the screen: {@link #toggleBold}...).
 * <p>
 * The text is laid out (wrapped) only when it or the box's width changes, never per frame.
 */
public class RichTextBox extends ClickableWidget {
    private static final int PAD = 4, LINE_H = 10, SCROLLBAR = 4;
    private static final int TEXT = 0xFFE0E0E0, PLACEHOLDER = 0xFF7C868D, SELECTION = 0x903A78C8, SELECTION_IDLE = 0x60808890;
    // The look: a sunken dark box (by default), or text written on paper (lines and edges drawn by the screen)
    private boolean paper;
    private int padLeft = PAD, padTop = PAD, padRight = PAD, fixedLines;
    private int textColor = TEXT, placeholderColor = PLACEHOLDER, caretColor = 0xFFFFFFFF, selectionColor = SELECTION;
    /** One line of plain text (a title): no formatting, Enter refused, written in bold when {@code bold}. */
    private boolean plain, bold;
    private static final int UNDO_LIMIT = 100;
    private static final long MERGE_MS = 1000, DOUBLE_CLICK_MS = 300, REFUSED_MS = 400;

    private final TextRenderer font;
    private final int maxVisible, maxLines;
    private final Text placeholder;
    private final int insetBody;
    private RichText text = new RichText();
    private int caret, anchor;
    /** The style what is typed next takes, set by the formatting with nothing selected; null: the text's there. */
    private RichText.@Nullable Style typing;
    private boolean editable = true;
    private @Nullable Runnable onChange;

    // Undo
    private record Snapshot(RichText text, int caret, int anchor) {
    }

    private final Deque<Snapshot> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private long lastTypedAt;
    private boolean typingRun;

    // Layout (on change only)
    private record Line(int start, int end) {
    }

    private final List<Line> lines = new ArrayList<>();
    /** x of each caret position from the start of its line (index: the position). */
    private int[] caretX = new int[1];
    private int[] lineEndX = new int[0];
    private String shownText = "";
    private int laidOutWidth = -1;
    private boolean dirty = true;
    private int scroll;

    // Mouse
    private boolean dragging;
    private long lastClickAt;
    private int lastClickPos = -1;
    private long blinkFrom = Util.getMeasuringTimeMs();
    private long refusedAt = -10_000;

    /** What was copied last: pasted with its formatting while the clipboard still holds its text. */
    private static @Nullable RichText copied;

    public RichTextBox(TextRenderer font, int x, int y, int width, int height, Text message, Text placeholder, int maxVisible, int maxLines, int insetBody) {
        super(x, y, width, height, message);
        this.font = font;
        this.placeholder = placeholder;
        this.maxVisible = maxVisible;
        this.maxLines = maxLines;
        this.insetBody = insetBody;
    }

    // ------------------------------------------------------------------ the text

    /**
     * Writes the text on paper: no box, the text {@code padLeft} / {@code padTop} inside the widget, {@code lines} lines
     * shown, in the paper's ink.
     */
    public RichTextBox paper(int padLeft, int padTop, int lines, int ink, int placeholderInk, int selection) {
        this.paper = true;
        this.padLeft = padLeft;
        this.padTop = padTop;
        this.padRight = 2;
        this.fixedLines = lines;
        this.textColor = ink;
        this.placeholderColor = placeholderInk;
        this.caretColor = ink;
        this.selectionColor = selection;
        this.dirty = true;
        return this;
    }

    /** One line of plain text (no formatting), in bold or not. */
    public RichTextBox plain(boolean bold) {
        this.plain = true;
        this.bold = bold;
        this.dirty = true;
        return this;
    }

    public String getPlain() {
        return text.plain();
    }

    public void setPlain(String plainText) {
        text = new RichText();
        text.insert(0, plainText, RichText.Style.PLAIN);
        caret = anchor = Math.min(caret, text.length());
        typing = null;
        undo.clear();
        redo.clear();
        dirty = true;
    }

    public String getCodes() {
        return text.toCodes();
    }

    public void setCodes(String codes) {
        text = RichText.fromCodes(codes);
        caret = anchor = Math.min(caret, text.length());
        typing = null;
        undo.clear();
        redo.clear();
        dirty = true;
    }

    /** The characters shown (the counter's). */
    public int visibleLength() {
        return text.length();
    }

    public int maxVisible() {
        return maxVisible;
    }

    /** When a character was refused (the text is full): the counter can flash. */
    public long refusedAt() {
        return refusedAt;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    public void setChangedListener(@Nullable Runnable onChange) {
        this.onChange = onChange;
    }

    private int from() {
        return Math.min(caret, anchor);
    }

    private int to() {
        return Math.max(caret, anchor);
    }

    public boolean hasSelection() {
        return caret != anchor;
    }

    // ------------------------------------------------------------------ formatting (the toolbar, Ctrl + B / I)

    /** The style of what is selected, or of what would be typed (the toolbar's lit buttons). */
    private RichText.Style current() {
        if (!hasSelection() && typing != null) return typing;
        return text.styleFor(hasSelection() ? from() : caret);
    }

    public boolean isBold() {
        return hasSelection() ? text.all(from(), to(), RichText.Style::bold) : current().bold();
    }

    public boolean isItalic() {
        return hasSelection() ? text.all(from(), to(), RichText.Style::italic) : current().italic();
    }

    /** The colour of the selection (or of what is typed), 0 for none, -1 when it has several. */
    public int color() {
        if (!hasSelection()) return current().color();
        char first = text.styleAt(from()).color();
        return text.all(from(), to(), style -> style.color() == first) ? first : -1;
    }

    public void toggleBold() {
        boolean on = !isBold();
        format(style -> style.withBold(on));
    }

    public void toggleItalic() {
        boolean on = !isItalic();
        format(style -> style.withItalic(on));
    }

    public void setColor(char color) {
        format(style -> style.withColor(color));
    }

    public void clearFormatting() {
        format(style -> RichText.Style.PLAIN);
    }

    private void format(UnaryOperator<RichText.Style> change) {
        if (!editable || plain) return;
        if (hasSelection()) {
            remember(false);
            text.apply(from(), to(), change);
            changed();
        } else {
            typing = change.apply(current());
        }
    }

    // ------------------------------------------------------------------ editing

    private void remember(boolean typed) {
        long now = Util.getMeasuringTimeMs();
        // Letters typed in a row are undone together
        if (!(typed && typingRun && now - lastTypedAt < MERGE_MS && !hasSelection())) {
            undo.push(new Snapshot(text.copy(), caret, anchor));
            while (undo.size() > UNDO_LIMIT) undo.removeLast();
        }
        typingRun = typed;
        lastTypedAt = now;
        redo.clear();
    }

    private void changed() {
        dirty = true;
        blinkFrom = Util.getMeasuringTimeMs();
        if (onChange != null) onChange.run();
    }

    /** Replaces the selection with {@code inserted} (cut to what still fits); refused when nothing fits. */
    private void replaceSelection(RichText inserted, boolean typed) {
        if (!editable) return;
        int room = maxVisible - (text.length() - (to() - from()));
        RichText fitting = inserted.sub(0, MathHelper.clamp(room, 0, inserted.length()));
        // Lines: the line breaks beyond the most become spaces
        int lineRoom = maxLines - (text.lines() - countBreaks(text, from(), to()));
        for (int i = 0; i < fitting.length(); i++) {
            if (fitting.charAt(i) != '\n') continue;
            if (lineRoom > 0) lineRoom--;
            else {
                RichText.Style style = fitting.styleAt(i);
                fitting.delete(i, i + 1);
                fitting.insert(i, " ", style);
            }
        }
        if (plain) {
            // One line: as much as the line holds
            int lineWidth = width - padLeft - padRight;
            String before = text.plain().substring(0, from()), after = text.plain().substring(to());
            String add = fitting.plain().replace('\n', ' ');
            while (!add.isEmpty() && font.getWidth(Text.literal(before + add + after).setStyle(net.minecraft.text.Style.EMPTY.withBold(bold))) > lineWidth)
                add = add.substring(0, add.length() - 1);
            RichText cut = new RichText();
            cut.insert(0, add, RichText.Style.PLAIN);
            fitting = cut;
        }
        if (fitting.length() == 0 && inserted.length() > 0) {
            refusedAt = Util.getMeasuringTimeMs();
            return;
        }
        if (fitting.length() < inserted.length()) refusedAt = Util.getMeasuringTimeMs();
        remember(typed);
        int at = from();
        text.delete(from(), to());
        text.insert(at, fitting);
        caret = anchor = at + fitting.length();
        changed();
        ensureCaretShown();
    }

    private static int countBreaks(RichText text, int from, int to) {
        int n = 0;
        for (int i = from; i < to; i++) if (text.charAt(i) == '\n') n++;
        return n;
    }

    /** The style of what is typed or pasted: the one chosen, else the selection's first character's, else the caret's. */
    private RichText.Style typedStyle() {
        if (typing != null) return typing;
        return hasSelection() ? text.styleAt(from()) : text.styleFor(caret);
    }

    private void type(String typed) {
        RichText.Style style = typedStyle();
        RichText inserted = new RichText();
        inserted.insert(0, typed, style);
        replaceSelection(inserted, true);
    }

    private void deleteRange(int from, int to) {
        if (!editable || from >= to) return;
        remember(false);
        text.delete(from, to);
        caret = anchor = from;
        typing = null;
        changed();
        ensureCaretShown();
    }

    private void undo() {
        if (undo.isEmpty()) return;
        redo.push(new Snapshot(text.copy(), caret, anchor));
        restore(undo.pop());
    }

    private void redo() {
        if (redo.isEmpty()) return;
        undo.push(new Snapshot(text.copy(), caret, anchor));
        restore(redo.pop());
    }

    private void restore(Snapshot snapshot) {
        text = snapshot.text();
        caret = snapshot.caret();
        anchor = snapshot.anchor();
        typing = null;
        typingRun = false;
        changed();
        ensureCaretShown();
    }

    private void moveCaret(int position, boolean select) {
        caret = MathHelper.clamp(position, 0, text.length());
        if (!select) anchor = caret;
        typing = null;
        typingRun = false;
        blinkFrom = Util.getMeasuringTimeMs();
        ensureCaretShown();
    }

    // ------------------------------------------------------------------ keys

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isFocused() || !editable) return false;
        if (Character.isISOControl(chr) || chr == '§') return false;
        type(String.valueOf(chr));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused()) return false;
        boolean shift = Screen.hasShiftDown(), ctrl = Screen.hasControlDown();
        // AltGr is Ctrl + Alt: the characters it types are no shortcut
        if (ctrl && !Screen.hasAltDown()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_A -> {
                    anchor = 0;
                    caret = text.length();
                    return true;
                }
                case GLFW.GLFW_KEY_C -> {
                    copy();
                    return true;
                }
                case GLFW.GLFW_KEY_X -> {
                    copy();
                    if (editable) deleteRange(from(), to());
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    paste();
                    return true;
                }
                case GLFW.GLFW_KEY_Z -> {
                    if (shift) redo();
                    else undo();
                    return true;
                }
                case GLFW.GLFW_KEY_Y -> {
                    redo();
                    return true;
                }
                // Ctrl + G too: the bold of French word processors
                case GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_G -> {
                    toggleBold();
                    return true;
                }
                case GLFW.GLFW_KEY_I -> {
                    toggleItalic();
                    return true;
                }
                default -> {
                }
            }
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) deleteRange(from(), to());
                else if (caret > 0) deleteRange(ctrl ? text.wordLeft(caret) : caret - 1, caret);
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) deleteRange(from(), to());
                else if (caret < text.length()) deleteRange(caret, ctrl ? text.wordRight(caret) : caret + 1);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (editable) {
                    if (text.lines() - countBreaks(text, from(), to()) >= maxLines) refusedAt = Util.getMeasuringTimeMs();
                    else type("\n");
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (hasSelection() && !shift) moveCaret(from(), false);
                else moveCaret(ctrl ? text.wordLeft(caret) : caret - 1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (hasSelection() && !shift) moveCaret(to(), false);
                else moveCaret(ctrl ? text.wordRight(caret) : caret + 1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                layout();
                int line = lineOf(caret), target = line + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1);
                if (target < 0) moveCaret(0, shift);
                else if (target >= lines.size()) moveCaret(text.length(), shift);
                else moveCaret(positionIn(target, caretX[caret]), shift);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                layout();
                moveCaret(ctrl ? 0 : lines.get(lineOf(caret)).start(), shift);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                layout();
                moveCaret(ctrl ? text.length() : lines.get(lineOf(caret)).end(), shift);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void copy() {
        if (!hasSelection()) return;
        copied = text.sub(from(), to());
        MinecraftClient.getInstance().keyboard.setClipboard(copied.plain());
    }

    private void paste() {
        if (!editable) return;
        String clipboard = MinecraftClient.getInstance().keyboard.getClipboard();
        if (clipboard == null || clipboard.isEmpty()) return;
        RichText inserted;
        if (copied != null && copied.plain().equals(clipboard)) {
            inserted = copied.copy();
        } else {
            StringBuilder clean = new StringBuilder();
            clipboard.replace("\r\n", "\n").replace('\t', ' ').codePoints()
                    .filter(c -> c == '\n' || (!Character.isISOControl(c) && c != '§')).forEach(clean::appendCodePoint);
            inserted = new RichText();
            inserted.insert(0, clean.toString(), typedStyle());
        }
        replaceSelection(inserted, false);
    }

    // ------------------------------------------------------------------ mouse

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || button != 0 || !isMouseOver(mouseX, mouseY)) return false;
        layout();
        if (overScrollbar(mouseX)) {
            scrollTo(mouseY);
            dragging = false;
            return true;
        }
        int position = positionAt(mouseX, mouseY);
        long now = Util.getMeasuringTimeMs();
        if (position == lastClickPos && now - lastClickAt < DOUBLE_CLICK_MS) {
            // A double click: the word
            int[] word = text.wordAt(Math.min(position, Math.max(0, text.length() - 1)));
            anchor = word[0];
            caret = word[1];
            typing = null;
            lastClickAt = 0;
            dragging = false;
            return true;
        }
        lastClickAt = now;
        lastClickPos = position;
        moveCaret(position, Screen.hasShiftDown());
        dragging = true;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!dragging || button != 0) return false;
        layout();
        // Above or under the box: it scrolls
        if (mouseY < getY() + padTop) scroll = Math.max(0, scroll - 1);
        else if (mouseY > getY() + padTop + shownLines() * LINE_H) scroll = Math.min(maxScroll(), scroll + 1);
        caret = positionAt(mouseX, mouseY);
        blinkFrom = Util.getMeasuringTimeMs();
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isMouseOver(mouseX, mouseY) || verticalAmount == 0) return false;
        layout();
        scroll = MathHelper.clamp(scroll - (int) Math.signum(verticalAmount), 0, maxScroll());
        return true;
    }

    // ------------------------------------------------------------------ layout

    private int shownLines() {
        if (fixedLines > 0) return fixedLines;
        return Math.max(1, (height - 2 * PAD + 1) / LINE_H);
    }

    private int maxScroll() {
        return Math.max(0, lines.size() - shownLines());
    }

    /** The style a character is drawn in (a bold title: bold throughout). */
    private net.minecraft.text.Style style(int index) {
        net.minecraft.text.Style style = mcStyle(text.styleAt(index));
        return bold ? style.withBold(true) : style;
    }

    private static net.minecraft.text.Style mcStyle(RichText.Style style) {
        net.minecraft.text.Style out = net.minecraft.text.Style.EMPTY.withBold(style.bold()).withItalic(style.italic())
                .withUnderline(style.underline()).withStrikethrough(style.strike());
        Formatting color = style.color() == 0 ? null : Formatting.byCode(style.color());
        return color != null ? out.withColor(color) : out;
    }

    private int charWidth(int index) {
        char c = text.charAt(index);
        if (c == '\n') return 0;
        return font.getWidth(Text.literal(String.valueOf(c)).setStyle(style(index)));
    }

    /**
     * Wraps the text in lines of {@code textWidth} pixels (at the spaces; a word longer than a line is cut).
     *
     * @param out the lines, null to only count them
     * @return how many lines
     */
    private int wrap(int textWidth, @Nullable List<Line> out) {
        int count = 0;
        int start = 0, width = 0, lastSpace = -1;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                if (out != null) out.add(new Line(start, i));
                count++;
                start = i + 1;
                width = 0;
                lastSpace = -1;
                continue;
            }
            int w = charWidth(i);
            if (width + w > textWidth && i > start) {
                int end = lastSpace >= start ? lastSpace + 1 : i;
                if (out != null) out.add(new Line(start, end));
                count++;
                start = end;
                width = 0;
                for (int j = start; j < i; j++) width += charWidth(j);
                lastSpace = -1;
            }
            if (text.charAt(i) == ' ') lastSpace = i;
            width += w;
        }
        return count;
    }

    /** Lays the text out again when it or the box's width changed (never per frame). */
    private void layout() {
        if (!dirty && width == laidOutWidth) return;
        dirty = false;
        laidOutWidth = width;
        shownText = text.plain();
        // Laid out at the full width, then narrower if a scroll bar is needed
        int textWidth = width - padLeft - padRight;
        if (wrap(textWidth, null) > shownLines()) textWidth -= SCROLLBAR + 1;
        lines.clear();
        wrap(textWidth, lines);
        caretX = new int[text.length() + 1];
        lineEndX = new int[lines.size()];
        for (int l = 0; l < lines.size(); l++) {
            Line line = lines.get(l);
            int x = 0;
            for (int i = line.start(); i < line.end(); i++) {
                caretX[i] = x;
                x += charWidth(i);
            }
            lineEndX[l] = x;
            // The end of a wrapped line is the start of the next one: its x is set there
            if (!wrapped(l)) caretX[line.end()] = x;
        }
        scroll = Math.min(scroll, maxScroll());
    }

    /** Whether a line goes on on the next one (cut by the wrap, not by a line break). */
    private boolean wrapped(int line) {
        return line + 1 < lines.size() && lines.get(line + 1).start() == lines.get(line).end();
    }

    /** The x of a position on a line (its end: the end of its text). */
    private int xIn(int line, int position) {
        return position >= lines.get(line).end() ? lineEndX[line] : caretX[position];
    }

    /** The line a caret position is on (at the end of a wrapped line: the next one, where typing goes on). */
    private int lineOf(int position) {
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            boolean wrappedEnd = position == line.end() && i + 1 < lines.size() && lines.get(i + 1).start() == position;
            if (position >= line.start() && position <= line.end() && !wrappedEnd) return i;
        }
        return Math.max(0, lines.size() - 1);
    }

    /** The caret position nearest to {@code x} on a line. */
    private int positionIn(int lineIndex, int x) {
        Line line = lines.get(lineIndex);
        // The end of a wrapped line is shown on the next one: not a place on this one
        int last = wrapped(lineIndex) ? line.end() - 1 : line.end();
        int best = line.start();
        for (int i = line.start(); i <= last; i++) {
            if (Math.abs(xIn(lineIndex, i) - x) < Math.abs(xIn(lineIndex, best) - x)) best = i;
        }
        return best;
    }

    private int positionAt(double mouseX, double mouseY) {
        int line = scroll + (int) Math.floor((mouseY - getY() - padTop) / LINE_H);
        if (line < 0) return 0;
        if (line >= lines.size()) return text.length();
        return positionIn(line, (int) Math.round(mouseX - getX() - padLeft));
    }

    private void ensureCaretShown() {
        layout();
        int line = lineOf(caret);
        if (line < scroll) scroll = line;
        else if (line >= scroll + shownLines()) scroll = line - shownLines() + 1;
    }

    private boolean overScrollbar(double mouseX) {
        return lines.size() > shownLines() && mouseX >= getX() + width - padRight - SCROLLBAR;
    }

    private void scrollTo(double mouseY) {
        float t = (float) ((mouseY - getY() - padTop) / (shownLines() * LINE_H));
        scroll = MathHelper.clamp(Math.round(t * maxScroll()), 0, maxScroll());
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        layout();
        boolean refused = Util.getMeasuringTimeMs() - refusedAt < REFUSED_MS;
        if (!paper) PartyGui.inset(context, getX(), getY(), width, height, insetBody, isFocused() || refused, false);
        if (refused) context.drawBorder(getX(), getY(), width, height, 0xFFD8323F);
        int left = getX() + padLeft, top = getY() + padTop;
        // On paper the placeholder stays while it is empty (the caret before it), like a form's hint
        if (text.length() == 0 && (paper || !isFocused())) {
            context.drawText(font, placeholder, left + (plain ? 2 : 0), top, placeholderColor, false);
        }
        int shown = shownLines();
        int from = from(), to = to();
        for (int row = 0; row < shown && scroll + row < lines.size(); row++) {
            int index = scroll + row;
            Line line = lines.get(index);
            int y = top + row * LINE_H;
            // The selection, under the text (a selected line break: a bit past the line's end)
            if (from != to && from <= line.end() && to >= line.start()) {
                int sx = xIn(index, Math.max(from, line.start())), ex = xIn(index, Math.min(to, line.end()));
                if (to > line.end() && !wrapped(index)) ex += 3;
                if (ex > sx) context.fill(left + sx, y - 1, left + ex, y + LINE_H - 1, isFocused() ? selectionColor : SELECTION_IDLE);
            }
            // The text, run by run of the same style
            int i = line.start();
            while (i < line.end()) {
                int j = i + 1;
                while (j < line.end() && text.styleAt(j).equals(text.styleAt(i))) j++;
                MutableText run = Text.literal(shownText.substring(i, j)).setStyle(style(i));
                context.drawText(font, run, left + caretX[i], y, textColor, false);
                i = j;
            }
        }
        // The caret, blinking
        if (isFocused() && editable && (Util.getMeasuringTimeMs() - blinkFrom) / 500 % 2 == 0) {
            int line = lineOf(caret);
            if (line >= scroll && line < scroll + shown) {
                int cx = left + caretX[caret], cy = top + (line - scroll) * LINE_H;
                context.fill(cx, cy - 1, cx + 1, cy + LINE_H - 1, caretColor);
            }
        }
        // The scroll bar
        if (lines.size() > shown) {
            int trackX = getX() + width - padRight - SCROLLBAR + 1, trackTop = getY() + padTop, trackH = shown * LINE_H - 2;
            context.fill(trackX, trackTop, trackX + SCROLLBAR - 1, trackTop + trackH, paper ? 0x30000000 : 0x40FFFFFF);
            int thumbH = Math.max(6, trackH * shown / lines.size());
            int thumbY = trackTop + (trackH - thumbH) * scroll / Math.max(1, maxScroll());
            context.fill(trackX, thumbY, trackX + SCROLLBAR - 1, thumbY + thumbH, paper ? 0xFF8AA3A6 : 0xFFB8C0C6);
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, getMessage());
    }
}
