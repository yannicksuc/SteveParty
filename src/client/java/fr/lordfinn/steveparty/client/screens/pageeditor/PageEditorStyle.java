package fr.lordfinn.steveparty.client.screens.pageeditor;

import fr.lordfinn.steveparty.client.gui.paint.Ramp;

/** The mini-game page editor's grid (GUI px, from the page's corner) and its paper's colours, as its mock-up. */
public final class PageEditorStyle {
    public static final String KEY = "gui.steveparty.mini_game_page.";
    // The grid of the mock-up
    public static final int PW = 320, PH = 224, M = 10, LX = 10, RX = 166, CW = 144, FULL = PW - 2 * M;
    public static final int BOTTOM_Y = 198, BOTTOM_H = 16;
    /** Width of the tooltips drawn here: about forty characters. */
    public static final int TOOLTIP_WIDTH = 200;
    /** The chips of the Formats tab, and of the popups anchored to them. */
    public static final int FORMAT_CHIP_H = 16;
    /** Ticks a located pipe (or podium) blinks in the world. */
    public static final int LOCATE_TICKS = 100;

    // The paper's colours
    public static final int PAPER = 0xFFE6F3F4, PAPER3 = 0xFFC7DBDC, EDGE = 0xFF7E9192, RULE = 0xFFC3DCDE, WHITE = 0xFFFFFFFF;
    public static final int TEAL = 0xFF00B3BD, TEAL2 = 0xFF008C95, GREEN2 = 0xFF00AC82, ORANGE = 0xFFFDA757, ORANGE2 = 0xFFD88029, RED = 0xFFD9283B;
    public static final int INK = 0xFF1E3A40, INK2 = 0xFF4F6F74, INK3 = 0xFF8AA3A6, HIGHLIGHT = 0xFFFFF2A0, MARGIN_LINE = 0xFFF4C9A0;
    public static final Ramp PAPER_RAMP = Ramp.of(0x7e9192, 0xffffff, 0xe6f3f4, 0xc7dbdc);
    public static final Ramp CARD = Ramp.of(0x7e9192, 0xffffff, 0xffffff, 0xc7dbdc);
    public static final Ramp KEYCAP = Ramp.of(0x7e9192, 0xffffff, 0xd6ebec, 0xc7dbdc);
    public static final Ramp KEYCAP_OFF = Ramp.of(0x8aa3a6, 0xf0f6f6, 0xdde9ea, 0xc9d7d8);
    public static final Ramp FRAME = Ramp.of(0x005a40, 0x8ff5d0, 0x00c792, 0x00ac82);
    public static final Ramp GOLD_CARD = Ramp.of(0x8a5a00, 0xfff2a8, 0xffffff, 0xe8e2c8);
    public static final Ramp PLUS = Ramp.of(0x008c95, 0x8ff0f6, 0x00b3bd, 0x008c95);
    public static final Ramp SEGMENT_ON = Ramp.of(0x7e9192, 0xffffff, 0xa35cff, 0x7a38d0);
    public static final Ramp BADGE = Ramp.of(0x4a0808, 0xffb7ae, 0xe8413c, 0xb02e26);
    /** The dimming under a popup. */
    public static final int VEIL = 0x6E141E28;

    private PageEditorStyle() {
    }
}
