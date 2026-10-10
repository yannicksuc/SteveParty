package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.client.gui.paint.Ramp;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * A format's pawn pictogram and its chip, as the editor's mock-ups draw them (the art sources
 * {@code pictogram}, build_page_editor_formats_v2.py {@code chip_img}): each side's pawns in its team's colour (A blue,
 * B red, C purple, D orange; up to 3 pawns, « + » after a side that may have more), the sides split by a thin grey bar;
 * free for all: four scattered green pawns; all together: four teal pawns side by side on one line. The chip is a white
 * pill (gold rimmed when selected), the pictogram, the name, the red « ! » when the page misses its pipes, its ×.
 */
public final class FormatChips {
    public static final int[] TEAM = {0xFF3A9BFF, 0xFFE8413C, 0xFFA35CFF, 0xFFF9901D};
    public static final int FREE_FOR_ALL = 0xFF4CC94A, ALL_TOGETHER = 0xFF00B3BD, BAR = 0xFF9AAAA8;
    private static final int INK = 0xFF1E3A40, INK3 = 0xFF8AA3A6, WHITE = 0xFFFFFFFF;
    private static final Ramp PAPER = Ramp.of(0x7e9192, 0xffffff, 0xffffff, 0xc7dbdc);
    private static final Ramp GOLD = Ramp.of(0x8a5a00, 0xfff2a8, 0xffffff, 0xe8e2c8);
    private static final Ramp BADGE = Ramp.of(0x4a0808, 0xffb7ae, 0xe8413c, 0xb02e26);
    private static final int[][] PAWN = {{1, 0}, {0, 1}, {1, 1}, {2, 1}, {1, 2}, {0, 3}, {1, 3}, {2, 3}, {0, 4}, {1, 4}, {2, 4}};
    /** The widest a chip goes (PageEditorStyle.CW, the tooltip's WIDTH). */
    private static final int MAX_WIDTH = 144;
    private static final int[][] PLUS = {{1, 0}, {0, 1}, {1, 1}, {2, 1}, {1, 2}};

    private FormatChips() {
    }

    /** The pictogram's pixels ({@code [y][x]}, 7 rows). */
    public static int[][] pictogram(MiniGameFormat format) {
        if (format.kind() == MiniGameFormat.Kind.ALL_TOGETHER) {
            int[][] img = new int[7][15];
            for (int k = 0; k < 4; k++) pawn(img, k * 4, 0, ALL_TOGETHER);
            for (int x = 0; x < 15; x++) img[6][x] = ALL_TOGETHER;
            return img;
        }
        if (format.kind() == MiniGameFormat.Kind.FREE_FOR_ALL) {
            int[][] img = new int[7][17];
            int[][] at = {{0, 2}, {4, 0}, {8, 2}, {12, 0}};
            for (int[] p : at) pawn(img, p[0] + 1, p[1], FREE_FOR_ALL);
            return img;
        }
        List<int[]> parts = new ArrayList<>();
        int w = 0;
        List<MiniGameFormat.Side> sides = format.sides();
        for (int i = 0; i < sides.size(); i++) {
            MiniGameFormat.Side side = sides.get(i);
            int n = MathHelper.clamp(side.min(), 1, 3);
            boolean more = side.infinite() || side.max() > side.min();
            int gw = n * 4 - 1 + (more ? 4 : 0);
            parts.add(new int[]{n, more ? 1 : 0, w});
            w += gw + (i < sides.size() - 1 ? 5 : 0);
        }
        int[][] img = new int[7][Math.max(1, w)];
        for (int i = 0; i < parts.size(); i++) {
            int n = parts.get(i)[0], x = parts.get(i)[2];
            boolean more = parts.get(i)[1] == 1;
            int colour = TEAM[i % TEAM.length];
            for (int k = 0; k < n; k++) pawn(img, x + k * 4, 1, colour);
            if (more) for (int[] p : PLUS) set(img, x + n * 4 + p[0], 2 + p[1], colour);
            if (i < parts.size() - 1) {
                int bar = x + n * 4 - 1 + (more ? 4 : 0) + 2;
                for (int y = 0; y < 7; y++) set(img, bar, y, BAR);
            }
        }
        return img;
    }

    private static void pawn(int[][] img, int x, int y, int colour) {
        for (int[] p : PAWN) set(img, x + p[0], y + p[1], colour);
    }

    private static void set(int[][] img, int x, int y, int colour) {
        if (y >= 0 && y < img.length && x >= 0 && x < img[0].length) img[y][x] = colour;
    }

    private static String key(MiniGameFormat format) {
        return "format_" + format.kind() + format.sides() + format.sameSize();
    }

    public static int pictogramWidth(MiniGameFormat format) {
        return pictogram(format)[0].length;
    }

    public static void drawPictogram(DrawContext context, MiniGameFormat format, int x, int y) {
        ConsolePaint.image(context, key(format), () -> pictogram(format), x, y);
    }

    /** What a chip shows. */
    public record Look(boolean name, boolean selected, boolean invalid, boolean removable, int height) {
        public static final Look READ_ONLY = new Look(false, false, false, false, 13);
    }

    public static int width(TextRenderer font, MiniGameFormat format, Look look) {
        int w = 5 + pictogramWidth(format) + 5;
        if (look.name()) w += 4 + nameWidth(font, format, look) - 1;
        if (look.invalid()) w += 12;
        if (look.removable()) w += 11;
        return w;
    }

    /**
     * The room of a chip's name: its width, but a chip is never wider than {@link #MAX_WIDTH} (the narrowest area chips
     * flow in: the page's column, the tooltip); a longer name scrolls in it.
     */
    private static int nameWidth(TextRenderer font, MiniGameFormat format, Look look) {
        int others = 5 + pictogramWidth(format) + 5 + 4 - 1 + (look.invalid() ? 12 : 0) + (look.removable() ? 11 : 0);
        return Math.max(1, Math.min(font.getWidth(format.name()), MAX_WIDTH - others));
    }

    /** Draws the chip at ({@code x}, {@code y}); returns its width. */
    public static int draw(DrawContext context, TextRenderer font, MiniGameFormat format, Look look, int x, int y) {
        int w = width(font, format, look), h = look.height();
        ConsolePaint.pill(context, x, y, w, h, look.selected() ? GOLD : PAPER, false);
        int px = x + 5;
        drawPictogram(context, format, px, y + (h - 7) / 2);
        px += pictogramWidth(format) + 4;
        if (look.name()) {
            Text name = format.name();
            int nameWidth = nameWidth(font, format, look);
            UiText.line(context, font, name, px, y + (h - 7) / 2 + ((h - 7) % 2), nameWidth, INK, false);
            px += nameWidth - 1 + 4;
        }
        if (look.invalid()) {
            int by = y + (h - 9) / 2;
            ConsolePaint.disc(context, px, by, 9, BADGE);
            for (int yy : new int[]{2, 3, 4, 6}) context.fill(px + 4, by + yy, px + 5, by + yy + 1, WHITE);
            px += 12;
        }
        if (look.removable()) {
            int cy = y + (h - 5) / 2;
            for (int d = 0; d < 5; d++) {
                context.fill(px + 1 + d, cy + d, px + 2 + d, cy + d + 1, INK3);
                context.fill(px + 5 - d, cy + d, px + 6 - d, cy + d + 1, INK3);
            }
        }
        return w;
    }

    /**
     * Where chips laid left to right go, a new row when one does not fit {@code width}: {x, y, w} of each, from the
     * top left of the area.
     */
    public static List<int[]> flow(TextRenderer font, List<MiniGameFormat> formats, IntFunction<Look> looks, int width, int gap) {
        List<int[]> at = new ArrayList<>();
        int x = 0, y = 0;
        for (int i = 0; i < formats.size(); i++) {
            Look look = looks.apply(i);
            int w = width(font, formats.get(i), look);
            if (x > 0 && x + w > width) {
                x = 0;
                y += look.height() + 3;
            }
            at.add(new int[]{x, y, w});
            x += w + gap;
        }
        return at;
    }

    /** Draws chips laid by {@link #flow}; returns the height they take. */
    public static int drawFlow(DrawContext context, TextRenderer font, List<MiniGameFormat> formats, IntFunction<Look> looks,
                               int x, int y, int width, int gap) {
        List<int[]> at = flow(font, formats, looks, width, gap);
        int bottom = 0;
        for (int i = 0; i < formats.size(); i++) {
            draw(context, font, formats.get(i), looks.apply(i), x + at.get(i)[0], y + at.get(i)[1]);
            bottom = Math.max(bottom, at.get(i)[1] + looks.apply(i).height());
        }
        return bottom;
    }

    /**
     * Why those near the pipes can't play: the closest format and what it misses.
     *
     * @param shortfall format index, role ordinal (-1: the teams are not of the same size), count, min, max (255: no limit)
     */
    public static Text shortfallText(MiniGamePageData page, int[] shortfall) {
        MiniGameFormat format = page.format(shortfall[0]);
        Text name = format == null ? Text.empty() : format.name();
        if (shortfall[1] < 0) return Text.translatable("format.steveparty.shortfall.size", name);
        MiniGamePipeRole role = MiniGamePipeRole.byOrdinal(shortfall[1]);
        MiniGameFormat.Side range = new MiniGameFormat.Side(shortfall[3], shortfall[4] >= 255 ? MiniGameFormat.Side.INFINITE : shortfall[4]);
        return Text.translatable("format.steveparty.shortfall.count", name, role == null ? Text.empty() : role.text(), shortfall[2], range.rangeText());
    }

    /** The x of a chip's × (its right part), for the clicks. */
    public static int removeX(TextRenderer font, MiniGameFormat format, Look look, int x) {
        return x + width(font, format, look) - 5 - 11;
    }
}
