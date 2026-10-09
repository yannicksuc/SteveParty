package fr.lordfinn.steveparty.client.screens.pageeditor;

import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.FormatChips;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.paint.Ramp;
import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import static fr.lordfinn.steveparty.client.screens.pageeditor.PageEditorStyle.*;

/** The gallery of ready formats, under the « + » chip: one click adds one. */
final class FormatGallery extends PagePopup {
    private static final int TILE_W = 145, TILE_H = 22;

    FormatGallery(PageEditor editor, int anchorX, int anchorY, int anchorW) {
        super(editor, anchorX, anchorY, anchorW, 15 + ((MiniGameFormat.GALLERY.size() + 2) / 2) * (TILE_H + 2) + 3);
    }

    @Override
    protected void anchor(DrawContext context) {
        PagePaint.plusChip(context, anchorX, anchorY, false, true);
    }

    @Override
    protected void content(DrawContext context, int mouseX, int mouseY) {
        Text title = Text.translatable(KEY + "formats.gallery");
        context.drawText(font(), title, px + 5, py + 4, INK, false);
        context.drawText(font(), title, px + 6, py + 4, INK, false);
        for (int i = 0; i <= MiniGameFormat.GALLERY.size(); i++) {
            int tx = px + 4 + (i % 2) * (TILE_W + 2), ty = py + 14 + (i / 2) * (TILE_H + 2);
            boolean over = HitArea.contains(mouseX, mouseY, tx, ty, TILE_W, TILE_H);
            ConsolePaint.box(context, tx, ty, TILE_W, TILE_H, Ramp.of(0x7e9192, 0xffffff, over ? 0xffffff : 0xe6f3f4, 0xc7dbdc), 1, 1);
            if (over) ConsolePaint.highlight(context, tx, ty, TILE_W, TILE_H, 1, TEAL2, 0);
            MiniGameFormat format = i < MiniGameFormat.GALLERY.size() ? MiniGameFormat.GALLERY.get(i) : null;
            if (format == null) {
                Text blank = Text.translatable(KEY + "formats.blank");
                context.drawText(font(), blank, tx + (TILE_W - font().getWidth(blank) + 1) / 2, ty + 8, TEAL2, false);
            } else {
                int picW = FormatChips.pictogramWidth(format);
                FormatChips.drawPictogram(context, format, tx + (TILE_W - picW) / 2, ty + 3);
                OrderedText name = PagePaint.fit(font(), format.name(), TILE_W - 6);
                context.drawText(font(), name, tx + (TILE_W - font().getWidth(name) + 1) / 2, ty + 12, INK, false);
            }
            MiniGameFormat added = format == null ? MiniGameFormat.blank() : format;
            hits.add(new Hit(tx, ty, TILE_W, TILE_H, () -> {
                if (editor.formats().size() < MiniGamePageData.MAX_FORMATS) editor.formats().add(added);
                editor.refreshTestButton();
                close();
            }, null));
        }
    }
}
