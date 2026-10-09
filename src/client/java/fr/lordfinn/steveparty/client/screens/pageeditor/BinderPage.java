package fr.lordfinn.steveparty.client.screens.pageeditor;

import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.paint.Ramp;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static fr.lordfinn.steveparty.client.screens.pageeditor.PageEditorStyle.*;

/**
 * The page (its paper, punched holes, header and footer lines) and its dividers: the others behind the page, the
 * selected one of the page's paper joined to it, its colour as a band on its end. A divider shows its icon, centred in
 * its part seen at rest; hovered, it slides out to show its name.
 */
public final class BinderPage {
    /** The dividers: 16 px high, 4 apart, the first at y 14; at rest {@code REST_OUT} px out of the page (an icon). */
    public static final int REST_OUT = 22;
    private static final int TABS_Y = 14, TAB_H = 16, TAB_GAP = 4, TAB_ROOT = 4, BAND = 6, ICON = 8;
    private static final int[] HOLES = {18, 56, 94, 132, 170, 208};
    private static final Ramp HOLE = Ramp.of(0x7e9192, 0x9fb4b6, 0xb9cacb, 0x9fb4b6);

    private final PageEditor editor;
    /** How far each divider is out of the page now (it slides out while hovered). */
    private final float[] tabOut = {REST_OUT, REST_OUT, REST_OUT, REST_OUT};
    private long lastFrame = Util.getMeasuringTimeMs();

    public BinderPage(PageEditor editor) {
        this.editor = editor;
    }

    private static Text name(BinderTab each) {
        return Text.translatable(KEY + "tab." + each.key);
    }

    /** How far the widest divider goes out of the page to show its name (the page is placed so that it stays on the screen). */
    public static int maxOut(TextRenderer font) {
        int maxOut = 0;
        for (BinderTab each : BinderTab.values()) maxOut = Math.max(maxOut, ICON + 10 + font.getWidth(name(each)) + BAND + 8);
        return maxOut;
    }

    /** Where a divider's label starts, from the page's right edge: after its icon. */
    private static int labelStart(BinderTab each, BinderTab selected) {
        return iconX(each, selected) + ICON + 4;
    }

    /** The icon's left, from the page's right edge: centred in the part of the divider seen at rest. */
    private static int iconX(BinderTab each, BinderTab selected) {
        int visible = each == selected ? REST_OUT - BAND : REST_OUT;
        return (visible - ICON) / 2;
    }

    /** How far a divider goes out of the page to show its name. */
    private int fullOut(BinderTab each, BinderTab selected) {
        return labelStart(each, selected) + editor.font().getWidth(name(each)) + (each == selected ? BAND + 4 : 6);
    }

    private int tabY(BinderTab each) {
        return editor.top() + TABS_Y + each.ordinal() * (TAB_H + TAB_GAP);
    }

    /** The divider under the mouse (its part out of the page), null for none. */
    public @Nullable BinderTab tabAt(double mouseX, double mouseY) {
        for (BinderTab each : BinderTab.values()) {
            int ty = tabY(each);
            if (mouseX >= editor.left() + PW && mouseX < editor.left() + PW + tabOut[each.ordinal()] && mouseY >= ty + 1 && mouseY < ty + TAB_H - 1) return each;
        }
        return null;
    }

    /** The page and its dividers, the {@code hovered} one sliding out. */
    public void draw(DrawContext context, BinderTab tab, @Nullable BinderTab hovered) {
        TextRenderer font = editor.font();
        int x = editor.left(), y = editor.top();
        long now = Util.getMeasuringTimeMs();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        for (BinderTab each : BinderTab.values()) {
            float target = each == hovered ? fullOut(each, tab) : REST_OUT;
            float out = tabOut[each.ordinal()];
            out += (target - out) * (1 - (float) Math.exp(-dt * 16));
            if (Math.abs(target - out) < 0.5f) out = target;
            tabOut[each.ordinal()] = out;
        }
        // The dividers at rest behind the page
        for (BinderTab each : BinderTab.values()) {
            if (each == tab) continue;
            int out = Math.round(tabOut[each.ordinal()]);
            ConsolePaint.pill(context, x + PW - TAB_ROOT - TAB_H + 1, tabY(each) + 1, TAB_ROOT + TAB_H + out, TAB_H - 2, each.ramp, true);
            context.fill(x + PW - TAB_ROOT, tabY(each) + TAB_H - 1, x + PW + out - 4, tabY(each) + TAB_H, 0x40000000);
        }
        // The selected one: the page's paper
        int selOut = Math.round(tabOut[tab.ordinal()]), sy = tabY(tab);
        ConsolePaint.pill(context, x + PW - TAB_ROOT - TAB_H + 1, sy + 1, TAB_ROOT + TAB_H + selOut, TAB_H - 2, PAPER_RAMP, false);
        // The page, its drop shadow
        context.fill(x + 2, y + PH, x + PW, y + PH + 1, 0x69000000);
        context.fill(x + 2, y + PH + 1, x + PW, y + PH + 2, 0x32000000);
        ConsolePaint.box(context, x, y, PW, PH, PAPER_RAMP, 1, 1);
        // The selected divider joins the page: no edge between them
        context.fill(x + PW - 2, sy + 2, x + PW + 1, sy + 3, WHITE);
        context.fill(x + PW - 2, sy + 3, x + PW + 1, sy + TAB_H - 3, PAPER);
        context.fill(x + PW - 2, sy + TAB_H - 3, x + PW + 1, sy + TAB_H - 2, PAPER3);
        // Its colour as a band on its end (the paper's straight edge on its left)
        ConsolePaint.pill(context, x + PW + selOut - 12, sy + 1, 12, TAB_H - 2, new Ramp(0, tab.ramp.hi(), tab.ramp.body(), tab.ramp.shadow()), false);
        context.fill(x + PW + selOut - 12, sy + 3, x + PW + selOut - BAND - 1, sy + TAB_H - 3, PAPER);
        context.fill(x + PW + selOut - 12, sy + 2, x + PW + selOut - BAND - 1, sy + 3, WHITE);
        context.fill(x + PW + selOut - 12, sy + TAB_H - 3, x + PW + selOut - BAND - 1, sy + TAB_H - 2, PAPER3);
        // The punched holes, the header's two teal lines, the footer's orange line
        for (int hy : HOLES) ConsolePaint.disc(context, x + 2, y + hy, 6, HOLE);
        context.fill(x + 10, y + 3, x + PW - 10, y + 4, TEAL);
        context.fill(x + 10, y + 5, x + PW - 10, y + 6, TEAL2);
        context.fill(x + 10, y + PH - 5, x + PW - 10, y + PH - 3, ORANGE);
        // Their icons, and their names as far as they are out
        for (BinderTab each : BinderTab.values()) {
            int ty = tabY(each), out = Math.round(tabOut[each.ordinal()]);
            boolean selected = each == tab;
            ConsolePaint.pattern(context, "page_tab2_" + each.key + (selected ? "_ink" : "_white"), each.icon,
                    Map.of('#', selected ? each.ramp.shadow() : WHITE), x + PW + iconX(each, tab), ty + (TAB_H - ICON) / 2);
            int labelLeft = x + PW + labelStart(each, tab), labelRight = x + PW + out - (selected ? BAND + 2 : 5);
            if (labelRight > labelLeft + 2) {
                Text label = name(each);
                context.enableScissor(labelLeft, ty, labelRight, ty + TAB_H);
                if (selected) {
                    context.drawText(font, label, labelLeft, ty + 4, INK, false);
                    context.drawText(font, label, labelLeft + 1, ty + 4, INK, false);
                } else {
                    context.drawText(font, label, labelLeft, ty + 4, WHITE, true);
                }
                context.disableScissor();
            }
        }
    }

    /** The « i » (the same as every menu of the mod's), at the top right of the tab's content. */
    public int infoX() {
        return editor.left() + PW - M - 12;
    }

    public boolean overInfo(double mouseX, double mouseY) {
        return HitArea.contains(mouseX, mouseY, infoX(), editor.top() + M, 12, 12);
    }
}
