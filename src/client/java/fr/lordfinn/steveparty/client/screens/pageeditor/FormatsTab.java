package fr.lordfinn.steveparty.client.screens.pageeditor;

import fr.lordfinn.steveparty.client.gui.FormatChips;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.client.screens.pageeditor.PageEditorStyle.*;

/**
 * The « Formats » tab: the ways the mini-game can be played ({@link MiniGameFormat}), as chips (×, a red « ! » when the
 * page misses their pipes). « + » opens the gallery of ready formats, a click on a chip its editor: both in a popup
 * anchored to their chip, the rest dimmed (Escape or a click outside closes it).
 */
public final class FormatsTab {
    private final PageEditor editor;

    public FormatsTab(PageEditor editor) {
        this.editor = editor;
    }

    private TextRenderer font() {
        return editor.font();
    }

    private FormatChips.Look look(MiniGamePageData data, int index, boolean selected) {
        return new FormatChips.Look(true, selected, !data.hasPipesFor(data.formats().get(index)), editor.canEdit() && data.formats().size() > 1, FORMAT_CHIP_H);
    }

    /** The chips (the edited one as being edited), then « + »: {x, y, w} each, on the screen. */
    private List<int[]> chips(MiniGamePageData data) {
        List<MiniGameFormat> shown = new ArrayList<>(data.formats());
        int editing = editor.popup() instanceof FormatEditorPopup popup ? popup.index : -1;
        if (editing >= 0 && editing < shown.size()) shown.set(editing, ((FormatEditorPopup) editor.popup()).draft);
        MiniGamePageData drawn = data.withFormats(shown);
        List<int[]> at = new ArrayList<>(FormatChips.flow(font(), shown, i -> look(drawn, i, i == editing), FULL - FORMAT_CHIP_H - 3, 3));
        int px = 0, py = 0;
        if (!at.isEmpty()) {
            int[] last = at.getLast();
            px = last[0] + last[2] + 3;
            py = last[1];
            if (px + FORMAT_CHIP_H > FULL) {
                px = 0;
                py += FORMAT_CHIP_H + 3;
            }
        }
        at.add(new int[]{px, py, FORMAT_CHIP_H});
        List<int[]> screen = new ArrayList<>();
        for (int[] chip : at) screen.add(new int[]{editor.left() + LX + chip[0], editor.top() + M + 14 + chip[1], chip[2]});
        return screen;
    }

    private int chipAt(double mouseX, double mouseY) {
        List<int[]> chips = chips(editor.current());
        for (int i = 0; i < chips.size(); i++) {
            int[] chip = chips.get(i);
            if (HitArea.contains(mouseX, mouseY, chip[0], chip[1], chip[2], FORMAT_CHIP_H)) return i;
        }
        return -1;
    }

    public void draw(DrawContext context, MiniGamePageData data, int mouseX, int mouseY) {
        int lx = editor.left() + LX;
        // Its label, up to the « i » at the top right
        UiText.line(context, font(), Text.translatable(KEY + "formats.label"), lx, editor.top() + M + 2, FULL - 16, INK2, false);
        List<int[]> chips = chips(data);
        int hovered = editor.popup() == null ? chipAt(mouseX, mouseY) : -1;
        for (int i = 0; i < data.formats().size(); i++) {
            int[] chip = chips.get(i);
            FormatChips.draw(context, font(), data.formats().get(i), look(data, i, i == hovered), chip[0], chip[1]);
        }
        int[] plus = chips.getLast();
        boolean full = data.formats().size() >= MiniGamePageData.MAX_FORMATS || !editor.canEdit();
        PagePaint.plusChip(context, plus[0], plus[1], full, hovered == chips.size() - 1);
        // Under the chips (nothing below them in the tab, so it wraps): whether every format has its pipes, else the first missing
        int bottom = chips.getLast()[1] + FORMAT_CHIP_H + 6;
        Text line = null;
        for (MiniGameFormat format : data.formats()) {
            List<MiniGamePipeRole> missing = data.missing(format);
            if (missing.isEmpty()) continue;
            line = Text.translatable(KEY + "formats.chip.missing", format.name(), PagePaint.missingText(missing));
            break;
        }
        if (line == null) {
            UiText.wrapped(context, font(), Text.translatable(KEY + "formats.complete"), lx, bottom, FULL, GREEN2, false);
        } else {
            PagePaint.missingBadge(context, lx, bottom - 1);
            UiText.wrapped(context, font(), line, lx + 13, bottom, FULL - 13, RED, false);
        }
    }

    /** Over everything: the tooltip of a chip, or of « + ». */
    public void drawOverlay(DrawContext context, int mouseX, int mouseY) {
        MiniGamePageData data = editor.current();
        int chip = chipAt(mouseX, mouseY);
        if (chip >= 0 && chip < data.formats().size()) {
            PagePaint.formatTooltip(context, font(), data, chip, mouseX, mouseY);
        } else if (chip == data.formats().size()) {
            context.drawTooltip(font(), Text.translatable(data.formats().size() >= MiniGamePageData.MAX_FORMATS
                    ? KEY + "formats.full" : KEY + "formats.add", MiniGamePageData.MAX_FORMATS), mouseX, mouseY);
        }
    }

    /** « + » opens the gallery, a chip's × removes it, a chip opens its editor. @return true if the click was a chip's */
    public boolean click(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        MiniGamePageData data = editor.current();
        int chip = chipAt(mouseX, mouseY);
        if (chip < 0) return false;
        List<int[]> chips = chips(data);
        List<MiniGameFormat> formats = editor.formats();
        if (chip == data.formats().size()) {
            if (!editor.canEdit() || formats.size() >= MiniGamePageData.MAX_FORMATS) return true;
            editor.playClick();
            int[] at = chips.get(chip);
            editor.openPopup(new FormatGallery(editor, at[0], at[1], at[2]));
            return true;
        }
        int[] at = chips.get(chip);
        FormatChips.Look look = look(data, chip, false);
        if (look.removable() && mouseX >= FormatChips.removeX(font(), data.formats().get(chip), look, at[0])) {
            formats.remove(chip);
            editor.playClick();
            editor.refreshTestButton();
            return true;
        }
        if (!editor.canEdit()) return true;
        editor.playClick();
        editor.openPopup(new FormatEditorPopup(editor, chip, at[0], at[1], at[2]));
        return true;
    }
}
