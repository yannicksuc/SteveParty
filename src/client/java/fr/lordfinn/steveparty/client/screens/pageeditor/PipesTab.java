package fr.lordfinn.steveparty.client.screens.pageeditor;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.client.gui.paint.Ramp;
import fr.lordfinn.steveparty.client.renderer.DestinationsRenderer;
import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePipeLink;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.Argb;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static fr.lordfinn.steveparty.client.screens.pageeditor.PageEditorStyle.*;

/**
 * The « Pipes » tab: the pipes linked to the page (a click on a pipe mouth, page in hand) as cards in a column per role:
 * dragging a card to another column gives it that role, a right click unlinks it, a click shows where the pipe is. The
 * small button of a column says how its players are sent to its pipes: each in turn, or at random.
 */
public final class PipesTab {
    /** The columns: two rows of four. */
    private static final MiniGamePipeRole[] COLUMNS = {MiniGamePipeRole.PLAYERS, MiniGamePipeRole.TEAM_A, MiniGamePipeRole.TEAM_B,
            MiniGamePipeRole.SPECTATORS, MiniGamePipeRole.TEAM_C, MiniGamePipeRole.TEAM_D, MiniGamePipeRole.ENTRY, MiniGamePipeRole.EXIT};
    private static final int COLUMN_WIDTH = 72, COLUMN_PITCH = 76, COLUMNS_TOP = M + 18, COLUMN_ROW = 84, HEADER = 13, CARD_H = 14, CARD_PITCH = 16, CARDS_SHOWN = 4;
    private static final int COLUMN_HEIGHT = HEADER + 3 + CARDS_SHOWN * CARD_PITCH - 2;
    private static final int ORDER_BUTTON = 9;

    private final PageEditor editor;
    /** First card shown in each column. */
    private final int[] scroll = new int[COLUMNS.length];
    /** The card held by the mouse, and whether it moved (a drag) since it was pressed. */
    private @Nullable MiniGamePipeLink held;
    private boolean dragged;
    private double pressX, pressY;

    public PipesTab(PageEditor editor) {
        this.editor = editor;
    }

    private TextRenderer font() {
        return editor.font();
    }

    /** The card held is let go (another tab is shown). */
    public void drop() {
        held = null;
    }

    private int columnX(int column) {
        return editor.left() + LX + (column % 4) * COLUMN_PITCH;
    }

    private int columnY(int column) {
        return editor.top() + COLUMNS_TOP + (column / 4) * COLUMN_ROW;
    }

    /** The column under the mouse, -1 for none. */
    private int columnAt(double mouseX, double mouseY) {
        for (int column = 0; column < COLUMNS.length; column++) {
            if (HitArea.contains(mouseX, mouseY, columnX(column), columnY(column), COLUMN_WIDTH, COLUMN_HEIGHT)) return column;
        }
        return -1;
    }

    /** The card under the mouse, null for none. */
    private @Nullable MiniGamePipeLink cardAt(double mouseX, double mouseY) {
        int column = columnAt(mouseX, mouseY);
        if (column < 0) return null;
        List<MiniGamePipeLink> pipes = editor.current().pipes(COLUMNS[column]);
        double inside = mouseY - (columnY(column) + HEADER + 3);
        int row = (int) Math.floor(inside / CARD_PITCH);
        if (inside < 0 || inside - row * CARD_PITCH >= CARD_H) return null;
        int index = row + scroll[column];
        return row >= 0 && row < CARDS_SHOWN && index < pipes.size() ? pipes.get(index) : null;
    }

    /** The column whose header (its title, not its order button) is under the mouse, -1 for none. */
    private int headerAt(double mouseX, double mouseY) {
        for (int column = 0; column < COLUMNS.length; column++) {
            if (HitArea.contains(mouseX, mouseY, columnX(column), columnY(column), COLUMN_WIDTH, HEADER))
                return orderButtonAt(mouseX, mouseY) == column ? -1 : column;
        }
        return -1;
    }

    /** The column whose order button is under the mouse, -1 for none. */
    private int orderButtonAt(double mouseX, double mouseY) {
        for (int column = 0; column < COLUMNS.length; column++) {
            if (!COLUMNS[column].hasOrder()) continue;
            if (HitArea.contains(mouseX, mouseY, columnX(column) + COLUMN_WIDTH - 12, columnY(column) + 2, ORDER_BUTTON, ORDER_BUTTON)) return column;
        }
        return -1;
    }

    private static Text cardText(MiniGamePipeLink link) {
        BlockPos pos = link.mouth().pos();
        return Text.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    /** The tab's first line: whether every format has its pipes, what is missing otherwise. */
    private static List<Text> status(MiniGamePageData data) {
        List<Text> lines = new ArrayList<>();
        if (data.pipeLinks().isEmpty()) {
            lines.add(Text.translatable(KEY + "pipes.none"));
            return lines;
        }
        for (MiniGameFormat format : data.formats()) {
            List<MiniGamePipeRole> missing = data.missing(format);
            if (!missing.isEmpty()) lines.add(Text.translatable(KEY + "formats.chip.missing", format.name(), PagePaint.missingText(missing)));
        }
        return lines;
    }

    // ------------------------------------------------------------------ drawing

    public void draw(DrawContext context, MiniGamePageData data, int mouseX, int mouseY) {
        List<Text> missing = status(data);
        Text line = missing.isEmpty() ? Text.translatable(KEY + "pipes.complete") : missing.getFirst();
        // Up to the « i » at the top right
        UiText.line(context, font(), line, editor.left() + LX, editor.top() + M + 2, FULL - 16, missing.isEmpty() ? GREEN2 : RED, false);

        int hovered = held != null && dragged ? columnAt(mouseX, mouseY) : -1;
        for (int column = 0; column < COLUMNS.length; column++) {
            MiniGamePipeRole role = COLUMNS[column];
            List<MiniGamePipeLink> pipes = data.pipes(role);
            int cx = columnX(column), cy = columnY(column);
            scroll[column] = Math.max(0, Math.min(scroll[column], pipes.size() - CARDS_SHOWN));
            if (column == hovered) context.fill(cx - 1, cy - 1, cx + COLUMN_WIDTH + 1, cy + COLUMN_HEIGHT + 1, 0x4000B3BD);
            // Header: the role, in the colour of its pipes
            context.fill(cx, cy, cx + COLUMN_WIDTH, cy + HEADER, Argb.opaque(role.color()));
            boolean light = Argb.luminance(role.color()) > 150;
            // Its room: up to its order button, else the header's width (its last column of shadow may go past)
            int room = role.hasOrder() ? 55 : COLUMN_WIDTH - 5;
            if (light) UiText.line(context, font(), role.text(), cx + 3, cy + 3, room, INK, false);
            else UiText.line(context, font(), role.text(), cx + 3, cy + 2, room, WHITE, true);
            if (role.hasOrder()) {
                // How its players are sent to its pipes: each in turn, or at random
                drawOrderButton(context, cx + COLUMN_WIDTH - 12, cy + 2, data.isRandom(role), light, orderButtonAt(mouseX, mouseY) == column);
            }
            for (int row = 0; row < CARDS_SHOWN && row + scroll[column] < pipes.size(); row++) {
                MiniGamePipeLink link = pipes.get(row + scroll[column]);
                if (link.equals(held) && dragged) continue;
                int top = cy + HEADER + 3 + row * CARD_PITCH;
                boolean over = held == null && link.equals(cardAt(mouseX, mouseY));
                drawCard(context, link, cx, top, over);
            }
            // No pipe of this role yet: which pipe to click, page in hand
            if (pipes.isEmpty()) drawEmptyColumn(context, role, cx, cy + HEADER + 3);
            // More cards than shown: marks
            if (scroll[column] > 0) UiText.line(context, font(), "▲", cx + COLUMN_WIDTH - 8, cy + HEADER + 6, 8, INK3, false);
            if (scroll[column] + CARDS_SHOWN < pipes.size()) UiText.line(context, font(), "▼", cx + COLUMN_WIDTH - 8, cy + COLUMN_HEIGHT - 9, 8, INK3, false);
        }
    }

    private static String dyeName(String dye) {
        return Text.translatable("color.minecraft." + dye).getString().toLowerCase(Locale.ROOT);
    }

    /** The colours of the pipes of a role, in words (« green or lime »), lower case; glass and mini-game pipes said too. */
    private static Text pipeColours(MiniGamePipeRole role) {
        List<String> names = new ArrayList<>();
        for (String dye : role.pipeColors()) names.add(dyeName(dye));
        if (role == MiniGamePipeRole.SPECTATORS) names.add(Text.translatable(KEY + "pipes.colour.glass").getString());
        String joined = names.size() <= 1 ? String.join("", names)
                : String.join(", ", names.subList(0, names.size() - 1)) + " " + Text.translatable(KEY + "pipes.colour.or").getString() + " " + names.getLast();
        return Text.literal(joined);
    }

    /** An empty column: a dashed card « + a green pipe » (the tooltip of its header says more). */
    private void drawEmptyColumn(DrawContext context, MiniGamePipeRole role, int left, int top) {
        int right = left + COLUMN_WIDTH, bottom = top + CARD_H;
        for (int px = left; px < right; px += 3) {
            context.fill(px, top, Math.min(px + 2, right), top + 1, INK3);
            context.fill(px, bottom - 1, Math.min(px + 2, right), bottom, INK3);
        }
        for (int py = top; py < bottom; py += 3) {
            context.fill(left, py, left + 1, Math.min(py + 2, bottom), INK3);
            context.fill(right - 1, py, right, Math.min(py + 2, bottom), INK3);
        }
        context.fill(left + 3, top + 3, left + 6, bottom - 3, Argb.opaque(role.color()));
        String first = role.pipeColors().isEmpty() ? "" : dyeName(role.pipeColors().getFirst());
        UiText.line(context, font(), Text.translatable(KEY + "pipes.empty", first), left + 9, top + 4, COLUMN_WIDTH - 11, INK2, false);
    }

    /** The 9 px toggle of a column: two arrows for « each in turn », a dice face for « at random ». */
    private void drawOrderButton(DrawContext context, int left, int top, boolean random, boolean light, boolean hovered) {
        ConsolePaint.box(context, left, top, ORDER_BUTTON, ORDER_BUTTON, new Ramp(0x78000000, 0x5AFFFFFF, hovered && editor.canEdit() ? 0x50FFFFFF : 0x28000000, 0x3C000000), 1, 1);
        int ink = light ? INK : WHITE;
        if (random) {
            for (int[] dot : new int[][]{{2, 2}, {6, 2}, {4, 4}, {2, 6}, {6, 6}}) PartyGui.pixel(context, left + dot[0], top + dot[1], ink);
        } else {
            context.fill(left + 2, top + 3, left + 7, top + 4, ink);
            context.fill(left + 2, top + 6, left + 7, top + 7, ink);
            PartyGui.pixel(context, left + 6, top + 2, ink);
            PartyGui.pixel(context, left + 2, top + 7, ink);
        }
    }

    /** The colour of the pipe a card stands for (0xRRGGBB): the pipe's, whatever its role; its role's when not known. */
    private static int pipeColor(MiniGamePipeLink link) {
        if (link.kind() == PipeKind.GLASS) return 0xD6ECF2;
        if (link.color() == MiniGamePipeLink.UNKNOWN_COLOR) return link.role().color();
        DyeColor dye = DyeColor.byName(ModBlocks.COLORS[link.color()], null);
        return dye == null ? link.role().color() : dye.getEntityColor() & 0xFFFFFF;
    }

    private void drawCard(DrawContext context, MiniGamePipeLink link, int left, int top, boolean hovered) {
        ConsolePaint.box(context, left, top, COLUMN_WIDTH, CARD_H, CARD, 1, 1);
        if (hovered) ConsolePaint.highlight(context, left, top, COLUMN_WIDTH, CARD_H, 1, TEAL2, 0);
        // The mark of its pipe: plain for plastic, a window in a windowed pipe, half see-through for glass
        int color = Argb.opaque(pipeColor(link));
        int markLeft = left + 2, markTop = top + 2, markRight = left + 5, markBottom = top + 12;
        context.fill(markLeft, markTop, markRight, markBottom, color);
        switch (link.kind()) {
            case WINDOWED -> context.fill(markLeft + 1, markTop + 2, markRight - 1, markBottom - 2, 0xFFEAF6FA);
            case GLASS, STAINED_GLASS -> {
                int half = markTop + (markBottom - markTop) / 2;
                for (int py = half; py < markBottom; py++) {
                    for (int px = markLeft; px < markRight; px++) {
                        PartyGui.pixel(context, px, py, ((px - markLeft) + (py - half)) % 2 == 0 ? 0xFFFFFFFF : 0xFFB9B9B9);
                    }
                }
            }
            default -> {
            }
        }
        // « x y z », or « x z » when that is too long for the card (the tooltip has it all)
        Text text = cardText(link);
        if (font().getWidth(text) - 1 > COLUMN_WIDTH - 12) text = Text.literal(link.mouth().pos().getX() + " " + link.mouth().pos().getZ());
        UiText.line(context, font(), text, left + 8, top + 4, COLUMN_WIDTH - 11, INK, false);
    }

    /** Over everything: the card being dragged, or the tooltip of what is under the mouse. */
    public void drawOverlay(DrawContext context, int mouseX, int mouseY) {
        if (held != null && dragged) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200);
            drawCard(context, held, mouseX - COLUMN_WIDTH / 2, mouseY - CARD_H / 2, true);
            context.getMatrices().pop();
            return;
        }
        List<Text> lines = tooltip(mouseX, mouseY);
        if (lines != null) PagePaint.tooltip(context, font(), lines, mouseX, mouseY);
    }

    private @Nullable List<Text> tooltip(int mouseX, int mouseY) {
        int x = editor.left(), y = editor.top();
        // The first line, when cut or when more is missing: all of it
        if (HitArea.contains(mouseX, mouseY, x + LX, y + M, PW - M - 16 - LX, 12)) {
            List<Text> missing = status(editor.current());
            if (!missing.isEmpty()) {
                List<Text> lines = new ArrayList<>();
                for (Text each : missing) lines.add(each.copy().formatted(Formatting.RED));
                return lines;
            }
        }
        int order = orderButtonAt(mouseX, mouseY);
        if (order >= 0) {
            boolean random = editor.current().isRandom(COLUMNS[order]);
            List<Text> lines = new ArrayList<>();
            lines.add(Text.translatable(KEY + (random ? "pipes.order.random" : "pipes.order.in_turn")).formatted(Formatting.GOLD));
            lines.add(Text.translatable(KEY + (random ? "pipes.order.random.hint" : "pipes.order.in_turn.hint")).formatted(Formatting.GRAY));
            if (editor.canEdit()) lines.add(Text.translatable(KEY + "pipes.order.change").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
            return lines;
        }
        int header = headerAt(mouseX, mouseY);
        int column = header >= 0 ? header : columnAt(mouseX, mouseY);
        if (column >= 0 && (header >= 0 || editor.current().pipes(COLUMNS[column]).isEmpty())) {
            MiniGamePipeRole role = COLUMNS[column];
            List<Text> lines = new ArrayList<>();
            lines.add(roleName(role));
            lines.add(Text.translatable(role.translationKey() + ".hint").formatted(Formatting.GRAY));
            lines.add(Text.translatable(KEY + "pipes.column.colours", pipeColours(role)).formatted(Formatting.GRAY));
            if (role == MiniGamePipeRole.ENTRY) lines.add(Text.translatable(KEY + "pipes.column.entry").formatted(Formatting.GRAY));
            lines.add(Text.translatable(KEY + "pipes.column.how", pipeColours(role)).formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
            return lines;
        }
        MiniGamePipeLink link = cardAt(mouseX, mouseY);
        if (link == null) return null;
        List<Text> lines = new ArrayList<>();
        lines.add(roleName(link.role()));
        lines.add(Text.translatable(KEY + "pipes.card.position", cardText(link), link.mouth().dimension().getValue().getPath()).formatted(Formatting.GRAY));
        lines.add(Text.translatable(link.role().translationKey() + ".hint").formatted(Formatting.GRAY));
        lines.add(Text.translatable(KEY + (editor.canEdit() ? "pipes.card.hint" : "pipes.card.hint.read_only")).formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        return lines;
    }

    /** A role's name in its pipes' colour (the entry's white too light: grey). */
    private static Text roleName(MiniGamePipeRole role) {
        return Text.empty().append(role.text()).styled(style -> style.withColor(role == MiniGamePipeRole.ENTRY ? 0xB8B8B8 : role.color()));
    }

    // ------------------------------------------------------------------ input

    /** Shows where a linked pipe is: it blinks in the world for a few seconds (seen through the menu, and once it is closed). */
    private void locate(MiniGamePipeLink link) {
        DestinationsRenderer.locate(link.mouth(), LOCATE_TICKS);
        MinecraftClient client = editor.client();
        if (client != null && client.player != null) client.player.playSound(ModSounds.SELECT_SOUND_EVENT, 1.0F, 1.4F);
        boolean here = client != null && client.world != null && client.world.getRegistryKey().equals(link.mouth().dimension());
        editor.setStatus(Text.translatable(KEY + (here ? "status.located" : "status.located_elsewhere"), cardText(link)), false);
    }

    private void setRole(MiniGamePipeLink link, @Nullable MiniGamePipeRole role) {
        if (!editor.canEdit() || role == link.role()) return;
        editor.send(new MiniGamePagePayloads.PipeRole(editor.hand(), editor.pageId(), link.mouth(), role == null ? -1 : role.ordinal()));
        editor.playClick();
    }

    /** An order button switches its column's order, a right click on a card unlinks it, a left one takes it. */
    public boolean click(double mouseX, double mouseY, int button) {
        int order = orderButtonAt(mouseX, mouseY);
        if (order >= 0 && button == 0) {
            if (editor.canEdit()) {
                MiniGamePipeRole role = COLUMNS[order];
                editor.send(new MiniGamePagePayloads.PipeOrder(editor.hand(), editor.pageId(), role.ordinal(), !editor.current().isRandom(role)));
                editor.playClick();
            }
            return true;
        }
        MiniGamePipeLink link = cardAt(mouseX, mouseY);
        if (link != null && button == 1) {
            setRole(link, null);
            return true;
        }
        if (link != null && button == 0) {
            held = link;
            dragged = false;
            pressX = mouseX;
            pressY = mouseY;
            return true;
        }
        return false;
    }

    /** @return true while a card is held (the drag is the tab's) */
    public boolean drag(double mouseX, double mouseY) {
        if (held != null && (Math.abs(mouseX - pressX) > 3 || Math.abs(mouseY - pressY) > 3)) dragged = true;
        return held != null;
    }

    /** The card held let go: dropped on a column, that role; not moved, where its pipe is. @return true if one was held */
    public boolean release(double mouseX, double mouseY, int button) {
        if (held == null || button != 0) return false;
        MiniGamePipeLink link = held;
        held = null;
        if (dragged) {
            // Dropped on a column: that role
            int column = columnAt(mouseX, mouseY);
            if (column >= 0) setRole(link, COLUMNS[column]);
        } else {
            // A click: where the pipe is
            locate(link);
        }
        dragged = false;
        return true;
    }

    /** The wheel over a column scrolls its cards. @return true if it was over one */
    public boolean scroll(double mouseX, double mouseY, double verticalAmount) {
        int column = columnAt(mouseX, mouseY);
        if (column < 0) return false;
        scroll[column] = Math.max(0, scroll[column] - (int) Math.signum(verticalAmount));
        return true;
    }
}
