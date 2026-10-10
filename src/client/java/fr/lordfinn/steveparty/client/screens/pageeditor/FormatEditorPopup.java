package fr.lordfinn.steveparty.client.screens.pageeditor;

import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.FormatChips;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.client.gui.paint.Ramp;
import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.utils.Argb;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.client.screens.pageeditor.PageEditorStyle.*;

/** A format's editor, under its chip: its kind, its teams (or its players), « same size », its pipes. */
final class FormatEditorPopup extends PagePopup {
    private static final int ROWS_SHOWN = 3, ROW = 15;
    /** The format edited, and its draft (put in its place by « OK »). */
    final int index;
    MiniGameFormat draft;
    private int rowsScroll;

    FormatEditorPopup(PageEditor editor, int index, int anchorX, int anchorY, int anchorW) {
        super(editor, anchorX, anchorY, anchorW, 24 + 17 + ROWS_SHOWN * ROW + 1 + 17 + 10 + 6 + 14 + 5);
        this.index = index;
        this.draft = editor.formats().get(index);
    }

    @Override
    protected void anchor(DrawContext context) {
        MiniGamePageData data = editor.current().withFormats(withDraft());
        FormatChips.draw(context, font(), draft, new FormatChips.Look(true, true, !data.hasPipesFor(draft), editor.canEdit() && editor.formats().size() > 1,
                FORMAT_CHIP_H), anchorX, anchorY);
    }

    private List<MiniGameFormat> withDraft() {
        List<MiniGameFormat> list = new ArrayList<>(editor.formats());
        list.set(index, draft);
        return list;
    }

    @Override
    protected void content(DrawContext context, int mouseX, int mouseY) {
        int cx = px + 6, cy = py + 3, cw = pw - 12;
        MiniGamePageData data = editor.current().withFormats(withDraft());
        // « Modifier : » and the chip, live
        Text edit = Text.translatable(KEY + "formats.edit");
        FormatChips.Look editLook = new FormatChips.Look(true, true, !data.hasPipesFor(draft), false, FORMAT_CHIP_H);
        int editW = Math.min(font().getWidth(edit), Math.max(0, cw - FormatChips.width(font(), draft, editLook) - 3));
        UiText.line(context, font(), edit, cx, cy + 4, editW, INK2, false);
        FormatChips.draw(context, font(), draft, editLook, cx + editW + 3, cy);
        // The kind: a segmented control (its segments share the row when their names are too long for it)
        int ky = cy + 21;
        UiText.line(context, font(), Text.translatable(KEY + "formats.kind"), cx, ky + 3, 42, INK2, false);
        int sx = cx + 44;
        MiniGameFormat.Kind[] kinds = MiniGameFormat.Kind.values();
        int[] segments = new int[kinds.length];
        for (int i = 0; i < kinds.length; i++) segments[i] = font().getWidth(Text.translatable(KEY + "formats.kind." + kinds[i].key())) - 1 + 10;
        segments = share(cw - 44 - 2 * (kinds.length - 1), segments);
        for (int i = 0; i < kinds.length; i++) {
            MiniGameFormat.Kind kind = kinds[i];
            Text label = Text.translatable(KEY + "formats.kind." + kind.key());
            int w = segments[i];
            boolean on = draft.kind() == kind, over = HitArea.contains(mouseX, mouseY, sx, ky, w, 13);
            ConsolePaint.box(context, sx, ky, w, 13, on ? SEGMENT_ON : KEYCAP, 1, 1);
            if (over && !on) ConsolePaint.highlight(context, sx, ky, w, 13, 1, TEAL2, 0);
            if (on) UiText.line(context, font(), label, sx + 5, ky + 3, w - 9, WHITE, true);
            else UiText.line(context, font(), label, sx + 5, ky + 3, w - 9, INK2, false);
            hits.add(new Hit(sx, ky, w, 13, () -> draft = draft.withKind(kind), null));
            sx += w + 2;
        }
        // One row per team (3 shown, it scrolls past), or the players without teams
        int ry = ky + 17;
        List<MiniGameFormat.Side> sides = draft.sides();
        boolean teams = draft.kind() == MiniGameFormat.Kind.TEAMS;
        rowsScroll = Math.max(0, Math.min(rowsScroll, sides.size() - ROWS_SHOWN));
        for (int row = 0; row < ROWS_SHOWN && row + rowsScroll < sides.size(); row++) {
            sideRow(context, cx, cw, ry + row * ROW, row + rowsScroll, sides.get(row + rowsScroll), sides.size(), teams, mouseX, mouseY);
        }
        if (sides.size() > ROWS_SHOWN) {
            // The scroll bar of the team rows
            int track = ROWS_SHOWN * ROW - 3;
            context.fill(cx + cw - 5, ry, cx + cw - 1, ry + track, PAPER3);
            int thumb = track * ROWS_SHOWN / sides.size(), top = ry + (track - thumb) * rowsScroll / Math.max(1, sides.size() - ROWS_SHOWN);
            context.fill(cx + cw - 5, top, cx + cw - 1, top + thumb, EDGE);
        }
        // « + équipe », « même taille »
        int ay = ry + ROWS_SHOWN * ROW + 1;
        if (teams) {
            Text add = Text.translatable(KEY + "formats.add_team");
            int aw = font().getWidth(add) - 1 + 10;
            button(context, cx, ay, aw, 13, add, sides.size() < MiniGameFormat.MAX_SIDES, false, mouseX, mouseY, () -> {
                draft = draft.withAddedSide();
                rowsScroll = Math.max(0, draft.sides().size() - ROWS_SHOWN);
            }, null);
            int bx = cx + aw + 6;
            ConsolePaint.box(context, bx, ay + 2, 9, 9, CARD, 1, 1);
            if (draft.sameSize()) {
                int[][] tick = {{2, 4}, {3, 5}, {4, 6}, {5, 5}, {6, 4}, {7, 3}};
                for (int[] p : tick) {
                    PartyGui.pixel(context, bx + p[0], ay + 2 + p[1], GREEN2);
                    PartyGui.pixel(context, bx + p[0], ay + 2 + p[1] - 1, GREEN2);
                }
            }
            Text same = Text.translatable(KEY + "formats.same_size");
            int sameW = Math.min(font().getWidth(same), Math.max(0, cx + cw - bx - 12));
            UiText.line(context, font(), same, bx + 12, ay + 3, sameW, draft.sameSize() ? INK : INK2, false);
            hits.add(new Hit(bx, ay, 12 + sameW, 13, () -> draft = draft.withSameSize(!draft.sameSize()), null));
        }
        // Its pipes
        int cyCheck = ay + 17;
        List<MiniGamePipeRole> missing = data.missing(draft);
        if (missing.isEmpty()) {
            UiText.line(context, font(), Text.translatable(KEY + "formats.pipes_ok"), cx, cyCheck, cw, GREEN2, false);
        } else {
            PagePaint.missingBadge(context, cx, cyCheck - 1);
            UiText.line(context, font(), PagePaint.missingText(missing), cx + 13, cyCheck, cw - 13, RED, false);
        }
        // Annuler / OK
        int oky = cyCheck + 16;
        button(context, px + pw - 6 - 124, oky, 60, 14, Text.translatable(KEY + "formats.cancel"), true, false, mouseX, mouseY, this::close, null);
        button(context, px + pw - 6 - 60, oky, 60, 14, Text.translatable(KEY + "formats.ok"), true, true, mouseX, mouseY, () -> {
            editor.formats().set(index, draft);
            editor.refreshTestButton();
            close();
        }, null);
    }

    /** A team's row (its letter), or the players': « from [−] N [+] to [−] M [+] », and its × for a team. */
    private void sideRow(DrawContext context, int cx, int cw, int rowY, int side, MiniGameFormat.Side range, int sides, boolean teams, int mouseX, int mouseY) {
        int x0 = cx;
        // The row's words share what its letter (or players), steppers and × leave of it
        Text players = Text.translatable(KEY + "formats.players"), from = Text.translatable(KEY + "formats.from"),
                to = Text.translatable(KEY + "formats.to");
        int[] words = teams ? share(cw - 21 - 3 - 16 - 2 - 35 - 5 - 2 - 35, font().getWidth(from), font().getWidth(to))
                : share(cw - 4 - 2 - 35 - 5 - 2 - 35, font().getWidth(players), font().getWidth(from), font().getWidth(to));
        int fromW = words[teams ? 0 : 1], toW = words[teams ? 1 : 2];
        if (teams) {
            MiniGamePipeRole role = MiniGamePipeRole.ofTeam(side);
            int colour = FormatChips.TEAM[side];
            ConsolePaint.box(context, x0, rowY, 12, 12, new Ramp(0xFF1E1E1E, lighter(colour), colour, darker(colour)), 1, 1);
            String letter = String.valueOf((char) ('A' + side));
            UiText.centered(context, font(), letter, x0 + 1, rowY + 2, 11, WHITE, true);
            hits.add(new Hit(x0, rowY, 12, 12, () -> {
            }, () -> role.text()));
            x0 += 16;
        } else {
            UiText.line(context, font(), players, x0, rowY + 3, words[0], INK2, false);
            x0 += words[0] + 4;
        }
        UiText.line(context, font(), from, x0, rowY + 3, fromW, INK2, false);
        x0 += fromW + 2;
        x0 = stepper(context, x0, rowY, Integer.toString(range.min()), mouseX, mouseY,
                range.min() > 1, () -> draft = draft.withSide(side, new MiniGameFormat.Side(range.min() - 1, range.max())),
                range.min() < MiniGameFormat.MAX_COUNT, () -> draft = draft.withSide(side, new MiniGameFormat.Side(range.min() + 1,
                        range.infinite() ? range.max() : Math.max(range.max(), range.min() + 1)))) + 5;
        UiText.line(context, font(), to, x0, rowY + 3, toW, INK2, false);
        x0 += toW + 2;
        stepper(context, x0, rowY, range.infinite() ? "∞" : Integer.toString(range.max()), mouseX, mouseY,
                range.infinite() || range.max() > range.min(), () -> draft = draft.withSide(side,
                        new MiniGameFormat.Side(range.min(), range.infinite() ? MiniGameFormat.MAX_COUNT : range.max() - 1)),
                !range.infinite(), () -> draft = draft.withSide(side,
                        new MiniGameFormat.Side(range.min(), range.max() >= MiniGameFormat.MAX_COUNT ? MiniGameFormat.Side.INFINITE : range.max() + 1)));
        if (teams) {
            boolean removable = sides > MiniGameFormat.MIN_SIDES;
            int bx = cx + cw - 21;
            button(context, bx, rowY, 12, 12, Text.empty(), removable, false, mouseX, mouseY, () -> draft = draft.withoutSide(side),
                    () -> Text.translatable(KEY + "formats.remove_team"));
            int colour = removable ? RED : INK3;
            for (int d = 0; d < 4; d++) {
                PartyGui.pixel(context, bx + 4 + d, rowY + 4 + d, colour);
                PartyGui.pixel(context, bx + 7 - d, rowY + 4 + d, colour);
            }
        }
    }

    /** « [−] N [+] »: returns its right. */
    private int stepper(DrawContext context, int sx, int sy, String value, int mouseX, int mouseY, boolean canLess, Runnable less, boolean canMore, Runnable more) {
        button(context, sx, sy, 11, 12, Text.literal("-"), canLess, false, mouseX, mouseY, less, null);
        // Bold (drawn twice), centred between its two buttons; too wide, it scrolls there
        int vw = font().getWidth(value) - 1;
        int vx = vw + 1 <= 11 ? sx + 12 + (11 - vw) / 2 : sx + 12, vRoom = vw + 1 <= 11 ? vw + 1 : 10;
        UiText.line(context, font(), value, vx, sy + 3, vRoom, INK, false);
        UiText.line(context, font(), value, vx + 1, sy + 3, vRoom, INK, false);
        button(context, sx + 24, sy, 11, 12, Text.literal("+"), canMore, false, mouseX, mouseY, more, null);
        return sx + 35;
    }

    @Override
    public boolean scroll(double mouseX, double mouseY, double amount) {
        rowsScroll = Math.max(0, rowsScroll - (int) Math.signum(amount));
        return true;
    }

    /** {@code widths} as they are if they hold in {@code room} together, else each cut down in proportion to share it. */
    private static int[] share(int room, int... widths) {
        int total = 0;
        for (int w : widths) total += w;
        if (total <= room) return widths;
        int[] shared = new int[widths.length];
        for (int i = 0; i < widths.length; i++) shared[i] = Math.max(0, widths[i] * Math.max(0, room) / total);
        return shared;
    }

    private static int lighter(int colour) {
        return Argb.opaque(Argb.lerp(colour, 0xFFFFFFFF, 0.5f));
    }

    private static int darker(int colour) {
        return Argb.opaque(Argb.lerp(colour, 0xFF000000, 0.25f));
    }
}
