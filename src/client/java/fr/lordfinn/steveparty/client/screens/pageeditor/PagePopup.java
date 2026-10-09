package fr.lordfinn.steveparty.client.screens.pageeditor;

import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static fr.lordfinn.steveparty.client.screens.pageeditor.PageEditorStyle.*;

/**
 * A popup anchored to a chip: the rest dimmed, the chip again on top, a gold-rimmed card under it with a pointer to it.
 * Escape or a click outside closes it ({@link #cancel}).
 */
public abstract class PagePopup {
    /** A clickable part of a popup: where, what it does, its tooltip. */
    protected record Hit(HitArea area, Runnable action, @Nullable Supplier<Text> tooltip) {
        Hit(int x, int y, int w, int h, Runnable action, @Nullable Supplier<Text> tooltip) {
            this(new HitArea(x, y, w, h), action, tooltip);
        }
    }

    protected final PageEditor editor;
    protected final int anchorX, anchorY, anchorW;
    protected int px, py, pw, ph;
    protected final List<Hit> hits = new ArrayList<>();

    protected PagePopup(PageEditor editor, int anchorX, int anchorY, int anchorW, int height) {
        this.editor = editor;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorW = anchorW;
        pw = FULL;
        ph = height;
        px = editor.left() + LX;
        py = Math.min(anchorY + FORMAT_CHIP_H + 6, editor.top() + PH - 4 - ph);
    }

    protected TextRenderer font() {
        return editor.font();
    }

    /** The chip it is anchored to, drawn again over the dimming. */
    protected abstract void anchor(DrawContext context);

    protected abstract void content(DrawContext context, int mouseX, int mouseY);

    public void cancel() {
    }

    /** Closes it (its changes kept or dropped by whoever calls it). */
    protected void close() {
        editor.openPopup(null);
    }

    public void render(DrawContext context, int mouseX, int mouseY) {
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 400);
        context.fill(0, 0, editor.screenWidth(), editor.screenHeight(), VEIL);
        anchor(context);
        ConsolePaint.box(context, px, py, pw, ph, GOLD_CARD, 1, 2);
        // The pointer, when the card is under its chip
        int ax = anchorX + anchorW / 2;
        if (py >= anchorY + FORMAT_CHIP_H + 1) {
            for (int i = 0; i < 5; i++) {
                int half = 4 - i;
                context.fill(ax - half, py - 5 + i, ax + half + 1, py - 4 + i, WHITE);
                PartyGui.pixel(context, ax - half, py - 5 + i, 0xFF8A5A00);
                PartyGui.pixel(context, ax + half, py - 5 + i, 0xFF8A5A00);
            }
            context.fill(ax - 3, py, ax + 4, py + 1, WHITE);
        }
        hits.clear();
        content(context, mouseX, mouseY);
        for (Hit hit : hits) {
            if (hit.tooltip() != null && hit.area().contains(mouseX, mouseY)) {
                PagePaint.tooltip(context, font(), List.of(hit.tooltip().get()), mouseX, mouseY);
                break;
            }
        }
        context.getMatrices().pop();
    }

    /** @return true if the click was the popup's (outside: it closes) */
    public boolean click(double mouseX, double mouseY) {
        for (Hit hit : new ArrayList<>(hits)) {
            if (hit.area().contains(mouseX, mouseY)) {
                editor.playClick();
                hit.action().run();
                return true;
            }
        }
        if (mouseX < px || mouseX >= px + pw || mouseY < py - 5 || mouseY >= py + ph) {
            cancel();
            close();
        }
        return true;
    }

    public boolean scroll(double mouseX, double mouseY, double amount) {
        return false;
    }

    /** A paper button of the popup. */
    protected void button(DrawContext context, int bx, int by, int w, int h, Text label, boolean active, boolean green, int mouseX, int mouseY, Runnable action,
                          @Nullable Supplier<Text> tooltip) {
        boolean over = active && HitArea.contains(mouseX, mouseY, bx, by, w, h);
        ConsolePaint.box(context, bx, by, w, h, !active ? KEYCAP_OFF : green ? FRAME : KEYCAP, 1, 1);
        if (over) ConsolePaint.highlight(context, bx, by, w, h, 1, TEAL2, 0);
        int tw = font().getWidth(label) - 1;
        if (green && active) context.drawText(font(), label, bx + (w - tw) / 2, by + (h - 8) / 2, WHITE, true);
        else context.drawText(font(), label, bx + (w - tw) / 2, by + (h - 7) / 2 + (h - 7) % 2, active ? INK : INK3, false);
        if (active) hits.add(new Hit(bx, by, w, h, action, tooltip));
        else if (tooltip != null) hits.add(new Hit(bx, by, w, h, () -> {
        }, tooltip));
    }
}
