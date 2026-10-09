package fr.lordfinn.steveparty.client.screens.partycontroller;

import fr.lordfinn.steveparty.client.gui.paint.Ramp;

import static fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.*;

/** The Party Controller dashboard's grid (GUI px, from the panel's corner) and colours, as its approved mock-up. */
public final class DashboardStyle {
    public static final String KEY = "gui.steveparty.party_controller.";
    // The grid of the mock-up
    public static final int BEZEL = 4, CX = CONTENT_X, CY = CONTENT_Y, CW = CONTENT_WIDTH, CONTENT_BOTTOM = PANEL_HEIGHT - CONTENT_Y;
    public static final int ROW_H = 12, BTN_H = 18, STEP = 16;
    public static final int INFO_X = CX + CW - ROW_H;
    public static final int BUTTON_Y = CY + 87;
    /** Players: the rows. */
    public static final int ROWS_Y = CY + 16, ROW = 18, PLAYER_ROWS = 4;
    public static final int TOOLTIP_WIDTH = 236;

    // The colours of the mock-up: the block's yellows, its dark screen
    public static final Ramp FRAME = Ramp.of(0x9a5200, 0xffdf62, 0xffc600, 0xffaa00);
    public static final Ramp TAB_IDLE = Ramp.of(0x9a5200, 0xffffff, 0xecf7fe, 0xbcc3bf);
    public static final Ramp TAB_IDLE_HOVER = Ramp.of(0x9a5200, 0xffffff, 0xfdfeff, 0xd4dad7);
    public static final Ramp CHIP_GAME = Ramp.of(0x0b2a12, 0xc9f2c0, 0x8fd67f, 0x4e9a45);
    public static final Ramp CHIP_EVENT = Ramp.of(0x3a2400, 0xffe2a8, 0xf4b24a, 0xb5761a);
    public static final Ramp NEUTRAL = Ramp.of(0x2f3a44, 0xffffff, 0xc9d3da, 0x8a96a0);
    public static final Ramp RANK_GOLD = Ramp.of(0x5b2e00, 0xfff87e, 0xffc900, 0xff9e00);
    public static final Ramp RANK_SILVER = Ramp.of(0x2f3a44, 0xffffff, 0xd6dde3, 0xa7b2bc);
    public static final Ramp RANK_BRONZE = Ramp.of(0x4a2410, 0xffd2a8, 0xd98a4a, 0xa8612c);
    public static final Ramp OK = Ramp.of(0x0e3a12, 0xc6f5ae, 0x6ccb52, 0x45a03a);
    public static final Ramp ERROR = Ramp.of(0x4a0808, 0xffb7ae, 0xe8413c, 0xb02e26);
    public static final Ramp WARN = Ramp.of(0x5a2800, 0xffd6a0, 0xf9901d, 0xcc6a10);
    public static final Ramp SWITCH_ON = Ramp.of(0x08270a, 0xa6ef8a, 0x46ae2e, 0x1f6a14);
    public static final Ramp SWITCH_OFF = Ramp.of(0x0d0a18, 0x6a66a8, 0x3d3a66, 0x2c2858);
    public static final Ramp KNOB = Ramp.of(0x2f3a44, 0xffffff, 0xffffff, 0xdfe6ea);
    public static final Ramp HEAD_FRAME = Ramp.of(0x1b1937, 0xffffff, 0xffffff, 0xdfe6ea);
    /** The red box of a refusal flashed on the page. */
    public static final Ramp FLASH = Ramp.of(0x33030a, 0xff8f8f, 0xd9283b, 0x8e1022);
    public static final int SCREEN = 0xFF1B1937, SCREEN_EDGE = 0xFF0D0A18;
    public static final int SLOT_BODY = 0xFF2C2858, SLOT_EDGE = 0xFF0D0A18, SLOT_LOW = 0xFF6A66A8;
    /** The colours of a greyed dice slot (not usable yet). */
    public static final int SLOT_OFF_BODY = 0xFF15122C, SLOT_OFF_LOW = 0xFF2C2858;
    /** The see-through slot colour over a faded item (a ghost), over a die of the list while dice are not restricted. */
    public static final int GHOST_VEIL = 0xA6000000 | (SLOT_BODY & 0xFFFFFF), DICE_OFF_VEIL = 0xA6000000 | (SLOT_OFF_BODY & 0xFFFFFF);
    public static final int INK = 0xFFE0EEF3, INK_SOFT = 0xFF9E9CC8, INK_DIM = 0xFF5E5C88, INK_GOLD = 0xFFFFD24A, INK_RED = 0xFFFF8F8F,
            INK_GREEN = 0xFF8FE07A, INK_WARN = 0xFFFFB54A, INK_BLUE = 0xFF8FC0FF, WHITE = 0xFFFFFFFF;
    public static final int GOLD_DARK = 0xFF5B2E00, GOLD_LIGHT = 0xFFFFE3A3;
    public static final int TAB_INK = 0xFF4A3A10, TAB_RED = 0xFFB3202A, TAB_WARN = 0xFFB36200;

    private DashboardStyle() {
    }
}
