package fr.lordfinn.steveparty.client.screens.pageeditor;

import fr.lordfinn.steveparty.client.gui.ConsoleButton;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.client.screens.pageeditor.PageEditorStyle.*;

/** The page editor's small drawing helpers, shared by its tabs and popups. */
public final class PagePaint {
    private PagePaint() {
    }

    /** {@code text} centred on {@code centerX}, in the picture's frame (too long, it scrolls in it). */
    public static void centred(DrawContext context, TextRenderer font, Text text, int centerX, int top, int color) {
        int room = CW - 8, w = font.getWidth(text);
        if (w - 1 <= room) UiText.line(context, font, text, centerX - (w - 1) / 2, top, w, color, false);
        else UiText.line(context, font, text, centerX - room / 2, top, room, color, false);
    }

    /** A tooltip whose long lines are cut (about forty characters wide). */
    public static void tooltip(DrawContext context, TextRenderer font, List<Text> lines, int mouseX, int mouseY) {
        List<OrderedText> wrapped = new ArrayList<>();
        for (Text line : lines) wrapped.addAll(font.wrapLines(line, TOOLTIP_WIDTH));
        context.drawOrderedTooltip(font, wrapped, mouseX, mouseY);
    }

    /** « Missing: team A, spectators ». */
    public static Text missingText(List<MiniGamePipeRole> missing) {
        MutableText roles = Text.empty();
        for (int i = 0; i < missing.size(); i++) roles.append(i == 0 ? "" : ", ").append(missing.get(i).text());
        return Text.translatable(KEY + "formats.missing", roles);
    }

    /** The red « ! » disc (9 px) before a line saying what is missing. */
    public static void missingBadge(DrawContext context, int left, int top) {
        ConsolePaint.disc(context, left, top, 9, BADGE);
        for (int yy : new int[]{2, 3, 4, 6}) PartyGui.pixel(context, left + 4, top + yy, WHITE);
    }

    /** The red cross of a 16 px « remove » button (grey when it can't be pressed). */
    public static void cross(DrawContext context, ConsoleButton button) {
        int colour = button.active ? RED : INK3;
        for (int d = 0; d < 6; d++) {
            PartyGui.pixel(context, button.getX() + 5 + d, button.getY() + 5 + d, colour);
            PartyGui.pixel(context, button.getX() + 10 - d, button.getY() + 5 + d, colour);
        }
    }

    /** A format's tooltip: its name, what it means at the draw, its missing pipes. */
    public static void formatTooltip(DrawContext context, TextRenderer font, MiniGamePageData data, int index, int mouseX, int mouseY) {
        MiniGameFormat format = data.format(index);
        if (format == null) return;
        List<Text> lines = new ArrayList<>();
        lines.add(format.name().copy().formatted(Formatting.GOLD));
        lines.add(format.meaning().copy().formatted(Formatting.GRAY));
        List<MiniGamePipeRole> missing = data.missing(format);
        if (!missing.isEmpty()) lines.add(missingText(missing).copy().formatted(Formatting.RED));
        tooltip(context, font, lines, mouseX, mouseY);
    }

    /**
     * The teal « + » chip: adds a format (its gallery). A disc of an even size ({@value PageEditorStyle#FORMAT_CHIP_H}):
     * its « + » is two pixels thick and eight long, so that it is exactly centred (pixels 4 to 11 of 0 to 15, both ways).
     * Hovered, the disc lights up round: its outline white, its body lighter.
     */
    public static void plusChip(DrawContext context, int px, int py, boolean off, boolean hovered) {
        ConsolePaint.pill(context, px, py, FORMAT_CHIP_H, FORMAT_CHIP_H, off ? KEYCAP_OFF : PLUS, false);
        if (hovered && !off) ConsolePaint.highlight(context, px, py, FORMAT_CHIP_H, FORMAT_CHIP_H, -1, WHITE, 0x40FFFFFF);
        int arm = 4, half = FORMAT_CHIP_H / 2;
        int colour = off ? INK3 : WHITE;
        context.fill(px + half - arm, py + half - 1, px + half + arm, py + half + 1, colour);
        context.fill(px + half - 1, py + half - arm, px + half + 1, py + half + arm, colour);
    }
}
