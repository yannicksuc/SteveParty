package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.gui.ConsoleButton;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.ConsolePaint.Ramp;
import fr.lordfinn.steveparty.client.gui.FormatChips;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.RichTextBox;
import fr.lordfinn.steveparty.client.minigame.MiniGamePageClient;
import fr.lordfinn.steveparty.client.minigame.PageImagePicker;
import fr.lordfinn.steveparty.client.renderer.DestinationsRenderer;
import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePageImage;
import fr.lordfinn.steveparty.minigame.MiniGamePageImages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeLink;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.minigame.MiniGamePodiumLink;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The editor of a mini-game page, opened by right-clicking the page in hand: « the binder page » (the art sources
 * build_page_editor_v2.py, build_page_editor_formats_v3.py). The page's paper with its punched holes, its fields
 * written on it; four dividers on its right edge (Page, Formats, Pipes, Results), an icon each, popping out to show
 * their name while hovered.
 * <ul>
 *     <li><b>Page</b>: its picture (picked on the player's computer, or dropped on the window), its title, its formats
 *     (read only: pawn chips, « modifier » leads to the Formats tab), its description across the page (formatted as it
 *     is shown, on ruled lines, scrolling), Copy / Unlink. What is written is sent when the editor closes; the picture
 *     as soon as it is picked. A player who may not build only reads the page.</li>
 *     <li><b>Formats</b>: the ways the mini-game can be played ({@link MiniGameFormat}), as chips (×, a red « ! » when
 *     the page misses their pipes). « + » opens the gallery of ready formats, a click on a chip its editor: both in a
 *     popup anchored to their chip, the rest dimmed (Escape or a click outside closes it).</li>
 *     <li><b>Pipes</b>: the pipes linked to the page (a click on a pipe mouth, page in hand) as cards in a column per
 *     role: dragging a card to another column gives it that role, a right click unlinks it, a click shows where the
 *     pipe is. The small button of a column says how its players are sent to its pipes: each in turn, or at random.</li>
 *     <li><b>Results</b>: the podiums, goal pole bases and step controllers linked to the page, with their block's name
 *     and where they are. A click shows where a card's block is, a right click unlinks it.</li>
 * </ul>
 */
public class MiniGamePageEditorScreen extends Screen {
    private static final String KEY = "gui.steveparty.mini_game_page.";
    // The grid of the mock-up (GUI px, from the page's corner)
    private static final int PW = 320, PH = 224, M = 10, LX = 10, RX = 166, CW = 144, FULL = PW - 2 * M;
    private static final int BOTTOM_Y = 198, BOTTOM_H = 16;
    private static final int[] HOLES = {18, 56, 94, 132, 170, 208};
    /** The dividers: 16 px high, 4 apart, the first at y 14; at rest {@code REST_OUT} px out of the page (an icon). */
    private static final int TABS_Y = 14, TAB_H = 16, TAB_GAP = 4, TAB_ROOT = 4, REST_OUT = 22, BAND = 6, ICON = 8;
    /** Width of the tooltips drawn here: about forty characters. */
    private static final int TOOLTIP_WIDTH = 200;

    // The paper's colours
    private static final int PAPER = 0xFFE6F3F4, PAPER3 = 0xFFC7DBDC, EDGE = 0xFF7E9192, RULE = 0xFFC3DCDE, WHITE = 0xFFFFFFFF;
    private static final int TEAL = 0xFF00B3BD, TEAL2 = 0xFF008C95, GREEN2 = 0xFF00AC82, ORANGE = 0xFFFDA757, ORANGE2 = 0xFFD88029, RED = 0xFFD9283B;
    private static final int INK = 0xFF1E3A40, INK2 = 0xFF4F6F74, INK3 = 0xFF8AA3A6, HIGHLIGHT = 0xFFFFF2A0, MARGIN_LINE = 0xFFF4C9A0;
    private static final Ramp PAPER_RAMP = Ramp.of(0x7e9192, 0xffffff, 0xe6f3f4, 0xc7dbdc);
    private static final Ramp CARD = Ramp.of(0x7e9192, 0xffffff, 0xffffff, 0xc7dbdc);
    private static final Ramp KEYCAP = Ramp.of(0x7e9192, 0xffffff, 0xd6ebec, 0xc7dbdc);
    private static final Ramp KEYCAP_OFF = Ramp.of(0x8aa3a6, 0xf0f6f6, 0xdde9ea, 0xc9d7d8);
    private static final Ramp FRAME = Ramp.of(0x005a40, 0x8ff5d0, 0x00c792, 0x00ac82);
    private static final Ramp HOLE = Ramp.of(0x7e9192, 0x9fb4b6, 0xb9cacb, 0x9fb4b6);
    private static final Ramp GOLD_CARD = Ramp.of(0x8a5a00, 0xfff2a8, 0xffffff, 0xe8e2c8);
    private static final Ramp PLUS = Ramp.of(0x008c95, 0x8ff0f6, 0x00b3bd, 0x008c95);
    private static final Ramp SEGMENT_ON = Ramp.of(0x7e9192, 0xffffff, 0xa35cff, 0x7a38d0);
    private static final Ramp BADGE = Ramp.of(0x4a0808, 0xffb7ae, 0xe8413c, 0xb02e26);
    private static final int VEIL = 0x6E141E28;

    /** The four dividers: their name's key, their colour, their icon (8 x 8, centred in the visible part). */
    private enum Tab {
        PAGE("page", Ramp.of(0x004a50, 0x8ff0f6, 0x00b3bd, 0x008c95),
                new String[]{".#####..", ".#...##.", ".#....#.", ".#.##.#.", ".#....#.", ".#.##.#.", ".#....#.", ".######."}),
        FORMATS("formats", Ramp.of(0x2e0a4a, 0xe3c6ff, 0xa35cff, 0x7a38d0),
                new String[]{"........", ".#....#.", "###..###", ".#....#.", "###..###", "###..###", "###..###", "........"}),
        PIPES("pipes", Ramp.of(0x004a33, 0x8ff5d0, 0x00c792, 0x00ac82),
                new String[]{"########", "#......#", "########", ".#....#.", ".#....#.", ".#....#.", ".#....#.", ".######."}),
        RESULTS("podiums", Ramp.of(0x5a2800, 0xffd6a0, 0xfda757, 0xd88029),
                new String[]{"........", "...##...", "...##...", "######..", "######..", "########", "########", "........"});

        final String key;
        final Ramp ramp;
        final String[] icon;

        Tab(String key, Ramp ramp, String[] icon) {
            this.key = key;
            this.ramp = ramp;
            this.icon = icon;
        }
    }

    private final Hand hand;
    private final UUID page;
    private final boolean canEdit;
    private final boolean linked;
    /** What the server has (the texts and settings as they were sent last). */
    private MiniGamePageData saved;

    private int x, y;
    private Tab tab = Tab.PAGE;
    /** How far each divider is out of the page now (it slides out while hovered). */
    private final float[] tabOut = {REST_OUT, REST_OUT, REST_OUT, REST_OUT};
    private long lastFrame = Util.getMeasuringTimeMs();
    private RichTextBox titleBox;
    private RichTextBox descriptionBox;
    /** The description's toolbar: bold, italic, colour (its palette), clear the formatting. */
    private Tool boldTool, italicTool, colorTool, clearTool;
    private boolean palette;
    /** The formats as edited (sent when the editor closes). */
    private final List<MiniGameFormat> formats;
    private ConsoleButton removeButton;
    /**
     * The « Test » button, on every tab: plays the mini-game out of any party with those near its pipes, or stops the
     * test being played. Whether it can is asked to the server every second ({@link #onTestStatus}).
     */
    private ConsoleButton testButton;
    private fr.lordfinn.steveparty.minigame.MiniGameTest.@Nullable Status testStatus;
    private int testPlayers, testFormat = -1, testPoll;
    private int[] testShortfall = new int[0];

    /** The picture just picked, shown until the server says what became of it. */
    private @Nullable MiniGamePageImage pendingImage;
    private boolean picking;
    private @Nullable Text status;
    private boolean statusIsError;
    private boolean opened;
    /** The popup open over the page (a format's editor, the gallery), null for none. */
    private @Nullable Popup popup;

    // ---- The « Pipes » tab
    /** The columns: two rows of four. */
    private static final MiniGamePipeRole[] COLUMNS = {MiniGamePipeRole.PLAYERS, MiniGamePipeRole.TEAM_A, MiniGamePipeRole.TEAM_B,
            MiniGamePipeRole.SPECTATORS, MiniGamePipeRole.TEAM_C, MiniGamePipeRole.TEAM_D, MiniGamePipeRole.ENTRY, MiniGamePipeRole.EXIT};
    private static final int COLUMN_WIDTH = 72, COLUMN_PITCH = 76, COLUMNS_TOP = M + 18, COLUMN_ROW = 84, HEADER = 13, CARD_H = 14, CARD_PITCH = 16, CARDS_SHOWN = 4;
    private static final int ORDER_BUTTON = 9;
    /** The podium cards: full width, scrolled by card. */
    private static final int RESULT_PITCH = 18, RESULT_H = 16, RESULTS_TOP = M + 18, RESULT_ROWS = 9;
    private int podiumScroll;
    /** What is typed in the fields, kept while the other tab is shown. */
    private String titleValue, descriptionValue;
    /** First card shown in each column. */
    private final int[] scroll = new int[COLUMNS.length];
    /** The card held by the mouse, and whether it moved (a drag) since it was pressed. */
    private @Nullable MiniGamePipeLink held;
    private boolean dragged;
    private double pressX, pressY;
    /** Ticks a located pipe blinks in the world. */
    private static final int LOCATE_TICKS = 100;
    /** The description: its label and toolbar right under the picture's buttons, its ruled lines down to the bottom row. */
    private static final int TOOL = 14, TOOL_GAP = 2, TOOLBAR_Y = M + 104, AREA_Y = TOOLBAR_Y + 17;
    private static final int AREA_LINES = (BOTTOM_Y - 4 - AREA_Y) / 10, AREA_H = AREA_LINES * 10 + 1;
    /** The page tab's formats: their label and the chips under it, in the right column. */
    private static final int FORMATS_Y = M + 34, CHIPS_Y = FORMATS_Y + 12;
    /** The palette's colours (0: the text's own), in two rows; its swatches. */
    private static final char[] PALETTE = {0, 'f', '7', 'c', '6', 'e', 'a', 'b', '9', 'd'};
    private static final int SWATCH = 12, SWATCH_GAP = 2, PALETTE_COLUMNS = 5;
    private static final int PALETTE_WIDTH = PALETTE_COLUMNS * SWATCH + (PALETTE_COLUMNS - 1) * SWATCH_GAP + 6, PALETTE_HEIGHT = 2 * SWATCH + SWATCH_GAP + 6;
    private static final Identifier EMPTY_PAGE = fr.lordfinn.steveparty.Steveparty.id("mini_game_page/empty");

    public MiniGamePageEditorScreen(Hand hand, MiniGamePageData data, boolean canEdit, boolean linked, @Nullable String status) {
        super(Text.translatable(KEY + "title"));
        this.hand = hand;
        this.page = data.id();
        this.canEdit = canEdit && !MiniGamePageData.NO_ID.equals(data.id());
        this.linked = linked;
        this.saved = data;
        this.formats = new ArrayList<>(data.formats());
        this.titleValue = data.title();
        this.descriptionValue = data.description();
        if (status != null) this.status = Text.literal(status);
        else if (!this.canEdit) this.status = Text.translatable(KEY + "status.read_only");
    }

    /** The page as the server has it now (its picture may change while the editor is open), with the formats edited here. */
    private MiniGamePageData current() {
        MiniGamePageData known = MiniGamePageClient.page(page);
        return (known != null ? known : saved).withFormats(formats);
    }

    /** Where a divider's label starts, from the page's right edge: after its icon. */
    private int labelStart(Tab each) {
        return iconX(each) + ICON + 4;
    }

    /** The icon's left, from the page's right edge: centred in the part of the divider seen at rest. */
    private int iconX(Tab each) {
        int visible = each == tab ? REST_OUT - BAND : REST_OUT;
        return (visible - ICON) / 2;
    }

    /** How far a divider goes out of the page to show its name. */
    private int fullOut(Tab each) {
        return labelStart(each) + textRenderer.getWidth(Text.translatable(KEY + "tab." + each.key)) + (each == tab ? BAND + 4 : 6);
    }

    @Override
    protected void init() {
        // The page and its dividers at rest, centred; a popped divider stays on the screen (the same place on every tab)
        int maxOut = 0;
        for (Tab each : Tab.values()) maxOut = Math.max(maxOut, ICON + 10 + textRenderer.getWidth(Text.translatable(KEY + "tab." + each.key)) + BAND + 8);
        x = Math.max(2, Math.min((width - PW - REST_OUT) / 2, width - 2 - PW - maxOut));
        y = Math.max(2, (height - PH) / 2);
        // init() runs again on every resize and tab change: keep what the player already typed
        keepTexts();
        String title = titleValue, description = descriptionValue;
        int lx = x + LX, rx = x + RX;
        popup = null;

        // ---- The bottom row: « Test » and « Done » on the right edge of the content
        int right = x + PW - M;
        testButton = addDrawableChild(new ConsoleButton(right - 124, y + BOTTOM_Y, 60, BOTTOM_H, Text.translatable(KEY + "test"),
                ConsoleButton.Kind.PAPER_TEAL, null, this::clickTest));
        addDrawableChild(new ConsoleButton(right - 60, y + BOTTOM_Y, 60, BOTTOM_H, net.minecraft.screen.ScreenTexts.DONE, ConsoleButton.Kind.PAPER_GREEN, null, this::close));
        refreshTestButton();
        if (testStatus == null) queryTest();
        if (tab != Tab.PAGE) {
            titleBox = null;
            descriptionBox = null;
            boldTool = italicTool = colorTool = clearTool = null;
            palette = false;
            removeButton = null;
            return;
        }

        // ---- Left: picture
        ConsoleButton choose = addDrawableChild(new ConsoleButton(lx, y + M + 84, CW - 20, 16, Text.translatable(KEY + "image.choose"),
                ConsoleButton.Kind.PAPER_TEAL, null, this::pickImage));
        choose.setTooltip(Tooltip.of(Text.translatable(KEY + "image.choose.hint", MiniGamePageImages.MAX_WIDTH, MiniGamePageImages.MAX_HEIGHT)));
        choose.active = canEdit;
        // « Remove »: a square button with a cross, its name in its tooltip
        removeButton = addDrawableChild(new ConsoleButton(lx + CW - 16, y + M + 84, 16, 16, Text.translatable(KEY + "image.remove"),
                ConsoleButton.Kind.PAPER, null, () -> {
            pendingImage = null;
            send(new MiniGamePagePayloads.Action(hand, page, MiniGamePagePayloads.Action.Kind.CLEAR_IMAGE));
        })).decoration((context, button) -> {
            int colour = button.active ? RED : INK3;
            for (int d = 0; d < 6; d++) {
                PartyGui.pixel(context, button.getX() + 5 + d, button.getY() + 5 + d, colour);
                PartyGui.pixel(context, button.getX() + 10 - d, button.getY() + 5 + d, colour);
            }
        });
        removeButton.setTooltip(Tooltip.of(Text.translatable(KEY + "image.remove")));

        // ---- The bottom row, on the left: the copies
        ConsoleButton copy = addDrawableChild(new ConsoleButton(lx, y + BOTTOM_Y, 70, BOTTOM_H, Text.translatable(KEY + "copy"), ConsoleButton.Kind.PAPER, null, () -> {
            save();
            send(new MiniGamePagePayloads.Action(hand, page, MiniGamePagePayloads.Action.Kind.COPY));
        }));
        copy.setTooltip(Tooltip.of(Text.empty().append(Text.translatable(KEY + (linked ? "link.linked" : "link.single")).formatted(Formatting.GRAY))
                .append("\n").append(Text.translatable(KEY + "copy.hint"))));
        copy.active = canEdit;
        ConsoleButton unlink = addDrawableChild(new ConsoleButton(lx + 74, y + BOTTOM_Y, 70, BOTTOM_H, Text.translatable(KEY + "unlink"), ConsoleButton.Kind.PAPER, null, () -> {
            save();
            send(new MiniGamePagePayloads.Action(hand, page, MiniGamePagePayloads.Action.Kind.UNLINK));
        }));
        unlink.setTooltip(Tooltip.of(Text.empty().append(Text.translatable(KEY + (linked ? "link.linked" : "link.single")).formatted(Formatting.GRAY))
                .append("\n").append(Text.translatable(KEY + "unlink.hint"))));
        unlink.active = canEdit && linked;

        // ---- Right: the title, written on its line
        titleBox = new RichTextBox(textRenderer, rx, y + M + 15, CW, 12, Text.translatable(KEY + "field.title"),
                Text.translatable(KEY + "field.title.placeholder"), MiniGamePageData.MAX_TITLE_LENGTH, 1, PAPER)
                .paper(2, 2, 1, INK, INK3, 0xFF9FD8FF).plain(true);
        titleBox.setPlain(title);
        titleBox.setEditable(canEdit);
        addDrawableChild(titleBox);

        // The description, across the page: edited as it is shown (never its codes), on ruled lines, the counter on the last
        descriptionBox = new RichTextBox(textRenderer, lx, y + AREA_Y, FULL, (AREA_LINES - 1) * 10 + 2, Text.translatable(KEY + "field.description"),
                Text.translatable(KEY + "field.description.placeholder"), MiniGamePageData.MAX_DESCRIPTION_LENGTH, MiniGamePageData.MAX_DESCRIPTION_LINES, PAPER)
                .paper(6, 2, AREA_LINES - 1, INK, INK3, 0xFF9FD8FF);
        descriptionBox.setCodes(description);
        descriptionBox.setEditable(canEdit);
        addDrawableChild(descriptionBox);

        // Its toolbar, on the line of its label: on the selection, or on what is typed next
        int tx = lx + FULL - 4 * TOOL - 3 * TOOL_GAP, ty = y + TOOLBAR_Y;
        boldTool = tool(tx, ty, "bold", "Ctrl+B", () -> descriptionBox.toggleBold(), () -> descriptionBox.isBold());
        italicTool = tool(tx + (TOOL + TOOL_GAP), ty, "italic", "Ctrl+I", () -> descriptionBox.toggleItalic(), () -> descriptionBox.isItalic());
        colorTool = tool(tx + 2 * (TOOL + TOOL_GAP), ty, "color", null, () -> palette = !palette, () -> palette);
        clearTool = tool(tx + 3 * (TOOL + TOOL_GAP), ty, "clear", null, () -> descriptionBox.clearFormatting(), () -> false);
        palette = false;

        if (canEdit && !opened && saved.title().isEmpty()) setInitialFocus(titleBox);
        if (!opened && client != null && client.player != null) {
            opened = true;
            client.player.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.8F, 1.0F);
        }
    }

    private Tool tool(int left, int top, String name, @Nullable String shortcut, Runnable action, BooleanSupplier lit) {
        Tool tool = new Tool(left, top, name, action, lit);
        net.minecraft.text.MutableText tooltip = Text.translatable(KEY + "format." + name);
        if (shortcut != null) tooltip.append(Text.literal("  " + shortcut).formatted(Formatting.GRAY));
        tooltip.append("\n").append(Text.translatable(KEY + "format." + name + ".hint").formatted(Formatting.DARK_GRAY));
        tool.setTooltip(Tooltip.of(tooltip));
        tool.active = canEdit;
        return addDrawableChild(tool);
    }

    /** A tool of the description's toolbar: a paper key, yellow when its format is on. */
    private final class Tool extends PressableWidget {
        private final String name;
        private final Runnable action;
        private final BooleanSupplier lit;

        Tool(int left, int top, String name, Runnable action, BooleanSupplier lit) {
            super(left, top, TOOL, TOOL, Text.translatable(KEY + "format." + name));
            this.name = name;
            this.action = action;
            this.lit = lit;
        }

        @Override
        public void onPress() {
            if (canEdit && descriptionBox != null) action.run();
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int left = getX(), top = getY();
            boolean on = active && lit.getAsBoolean();
            ConsolePaint.box(context, left, top, TOOL, TOOL, Ramp.of(0x7e9192, 0xffffff, on ? 0xfff2a0 : 0xd6ebec, 0xc7dbdc), 1, 1);
            if (active && (isHovered() || isFocused())) ConsolePaint.highlight(context, left, top, TOOL, TOOL, 1, TEAL2, 0);
            int ink = active ? INK : INK3;
            switch (name) {
                case "bold" -> {
                    Text letter = Text.translatable(KEY + "format.bold.letter");
                    context.drawText(textRenderer, letter, left + 4, top + 4, ink, false);
                    context.drawText(textRenderer, letter, left + 5, top + 4, ink, false);
                }
                case "italic" -> {
                    for (int i = 0; i < 7; i++) PartyGui.pixel(context, left + 8 - i / 3, top + 3 + i, ink);
                }
                case "color" -> {
                    int[] stripes = {0xFFE8413C, 0xFFFFD83D, 0xFF3A9BFF};
                    for (int i = 0; i < 3; i++) context.fill(left + 3 + i * 3, top + 4, left + 6 + i * 3, top + 10, active ? stripes[i] : INK3);
                    int colour = descriptionBox == null ? 0 : descriptionBox.color();
                    if (colour > 0) context.fill(left + 3, top + 11, left + 12, top + 12, swatchColor((char) colour));
                }
                default -> {
                    context.drawText(textRenderer, "T", left + 4, top + 4, ink, false);
                    for (int i = 0; i < 8; i++) PartyGui.pixel(context, left + 3 + i, top + 10 - i, active ? RED : INK3);
                }
            }
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }

    /** What a colour of the palette looks like (0: the text's own colour). */
    private static int swatchColor(char code) {
        Formatting formatting = code == 0 ? null : Formatting.byCode(code);
        return formatting != null && formatting.getColorValue() != null ? 0xFF000000 | formatting.getColorValue() : WHITE;
    }

    private int paletteX() {
        return x + LX + FULL - PALETTE_WIDTH;
    }

    private int paletteY() {
        return y + TOOLBAR_Y + TOOL + 2;
    }

    /** The swatch of the palette under the mouse, -1 for none. */
    private int swatchAt(double mouseX, double mouseY) {
        for (int i = 0; i < PALETTE.length; i++) {
            int sx = paletteX() + 3 + (i % PALETTE_COLUMNS) * (SWATCH + SWATCH_GAP), sy = paletteY() + 3 + (i / PALETTE_COLUMNS) * (SWATCH + SWATCH_GAP);
            if (mouseX >= sx && mouseX < sx + SWATCH && mouseY >= sy && mouseY < sy + SWATCH) return i;
        }
        return -1;
    }

    /** The palette, open under the toolbar: its swatches, the colour in use outlined. */
    private void drawPalette(DrawContext context, int mouseX, int mouseY) {
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300);
        int px = paletteX(), py = paletteY();
        ConsolePaint.box(context, px, py, PALETTE_WIDTH, PALETTE_HEIGHT, CARD, 1, 1);
        int current = descriptionBox.color(), hovered = swatchAt(mouseX, mouseY);
        for (int i = 0; i < PALETTE.length; i++) {
            int sx = px + 3 + (i % PALETTE_COLUMNS) * (SWATCH + SWATCH_GAP), sy = py + 3 + (i / PALETTE_COLUMNS) * (SWATCH + SWATCH_GAP);
            int c = swatchColor(PALETTE[i]);
            ConsolePaint.box(context, sx, sy, SWATCH, SWATCH, new Ramp(EDGE, PALETTE[i] == 0 ? WHITE : c, PALETTE[i] == 0 ? WHITE : c, PALETTE[i] == 0 ? PAPER3 : c), 1, 1);
            if (PALETTE[i] == 0) for (int d = 0; d < 8; d++) PartyGui.pixel(context, sx + 2 + d, sy + 9 - d, RED);
            if (i == hovered) context.drawBorder(sx - 1, sy - 1, SWATCH + 2, SWATCH + 2, TEAL2);
            else if (PALETTE[i] == current) context.drawBorder(sx - 1, sy - 1, SWATCH + 2, SWATCH + 2, 0xFFFFC52E);
        }
        if (hovered >= 0) {
            context.drawTooltip(textRenderer, Text.translatable(KEY + "format.color." + (PALETTE[hovered] == 0 ? "none" : String.valueOf(PALETTE[hovered]))), mouseX, mouseY);
        }
        context.getMatrices().pop();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && popup != null) {
            popup.cancel();
            popup = null;
            return true;
        }
        if (palette && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            palette = false;
            return true;
        }
        if (popup != null) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return popup == null && super.charTyped(chr, modifiers);
    }

    private void keepTexts() {
        if (titleBox != null) titleValue = titleBox.getPlain();
        if (descriptionBox != null) descriptionValue = descriptionBox.getCodes();
    }

    private void showTab(Tab tab) {
        if (tab == this.tab) return;
        keepTexts();
        this.tab = tab;
        held = null;
        if (client != null) client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.2F));
        clearAndInit();
    }

    private void playClick() {
        if (client != null) client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.2F));
    }

    // ------------------------------------------------------------------ picture

    private void pickImage() {
        if (picking || !canEdit) return;
        picking = true;
        setStatus(Text.translatable(KEY + "status.picking"), false);
        PageImagePicker.pick(this::onPicked);
    }

    /** A picture file dropped on the window. */
    @Override
    public void filesDragged(List<Path> paths) {
        if (picking || !canEdit || paths.isEmpty() || tab != Tab.PAGE) return;
        picking = true;
        setStatus(Text.translatable(KEY + "status.picking"), false);
        PageImagePicker.read(paths.get(0), this::onPicked);
    }

    private void onPicked(PageImagePicker.Result result) {
        picking = false;
        if (client == null || client.currentScreen != this) return;
        if (result.cancelled()) {
            setStatus(null, false);
            return;
        }
        if (result.bytes() == null || result.image() == null) {
            setStatus(result.error(), true);
            return;
        }
        byte[] bytes = result.bytes();
        String hash = MiniGamePageImages.hash(bytes);
        MiniGamePageClient.putImage(hash, result.image());
        pendingImage = new MiniGamePageImage(hash, result.image().getWidth(), result.image().getHeight(), bytes.length, "", null);
        int total = (bytes.length + MiniGamePagePayloads.CHUNK_SIZE - 1) / MiniGamePagePayloads.CHUNK_SIZE;
        for (int index = 0; index < total; index++) {
            int from = index * MiniGamePagePayloads.CHUNK_SIZE;
            send(new MiniGamePagePayloads.Upload(hand, page, index, total,
                    java.util.Arrays.copyOfRange(bytes, from, Math.min(bytes.length, from + MiniGamePagePayloads.CHUNK_SIZE))));
        }
        setStatus(Text.translatable(KEY + "status.sending"), false);
    }

    // ------------------------------------------------------------------ test

    private void queryTest() {
        if (!MiniGamePageData.NO_ID.equals(page)) send(new MiniGamePagePayloads.TestQuery(hand, page));
    }

    @Override
    public void tick() {
        super.tick();
        if (++testPoll % 20 == 0) queryTest();
    }

    /** The server says whether the page can be tested now, with how many players, in which format, or what it misses. */
    public void onTestStatus(UUID about, int status, int players, int format, int[] shortfall) {
        if (!about.equals(page)) return;
        fr.lordfinn.steveparty.minigame.MiniGameTest.Status[] values = fr.lordfinn.steveparty.minigame.MiniGameTest.Status.values();
        testStatus = status >= 0 && status < values.length ? values[status] : null;
        testPlayers = players;
        testFormat = format;
        testShortfall = shortfall;
        refreshTestButton();
    }

    private boolean testRunning() {
        return testStatus == fr.lordfinn.steveparty.minigame.MiniGameTest.Status.RUNNING;
    }

    /** « Test », or « Stop the test » while one is played; greyed, with why in its tooltip, when the page can't be tested. */
    private void refreshTestButton() {
        if (testButton == null) return;
        boolean ready = testStatus == fr.lordfinn.steveparty.minigame.MiniGameTest.Status.READY;
        testButton.setMessage(Text.translatable(KEY + (testRunning() ? "test.stop" : "test")));
        testButton.active = canEdit && (ready || testRunning());
        Text why;
        MiniGamePageData data = current();
        if (!canEdit) why = Text.translatable(KEY + "status.read_only");
        else if (testStatus == null) why = Text.translatable(KEY + "test.tooltip.unknown");
        else if (ready) {
            MiniGameFormat format = data.format(testFormat);
            why = Text.translatable(KEY + "test.tooltip.ready", testPlayers, format == null ? Text.empty() : format.name());
        } else if (testStatus == fr.lordfinn.steveparty.minigame.MiniGameTest.Status.NOT_ENOUGH && testShortfall.length == 5) {
            why = Text.translatable(KEY + "test.tooltip.not_enough.format", FormatChips.shortfallText(data, testShortfall));
        } else why = Text.translatable(KEY + "test.tooltip." + testStatus.name().toLowerCase(java.util.Locale.ROOT));
        testButton.setTooltip(Tooltip.of(ready || testRunning() ? why : why.copy().formatted(Formatting.RED)));
    }

    private void clickTest() {
        if (!canEdit) return;
        if (testRunning()) {
            send(new MiniGamePagePayloads.TestAction(hand, page, false));
        } else {
            // What is written is the mini-game tested
            save();
            send(new MiniGamePagePayloads.TestAction(hand, page, true));
        }
        close();
    }

    /** The server's answer to the last request. */
    public void onStatus(UUID about, MiniGamePagePayloads.Status.Code code) {
        if (!about.equals(page)) return;
        boolean error = code == MiniGamePagePayloads.Status.Code.IMAGE_REFUSED || code == MiniGamePagePayloads.Status.Code.COPY_NEEDS_PAPER
                || code == MiniGamePagePayloads.Status.Code.NOT_ALLOWED;
        if (code == MiniGamePagePayloads.Status.Code.IMAGE_SAVED || code == MiniGamePagePayloads.Status.Code.IMAGE_REFUSED) pendingImage = null;
        setStatus(Text.translatable(KEY + "status." + code.name().toLowerCase(java.util.Locale.ROOT)), error);
    }

    private void setStatus(@Nullable Text text, boolean error) {
        status = text;
        statusIsError = error;
    }

    /** The editor is about to be opened again on the same hand: saves, and gives its status line to the next one. */
    public @Nullable String handOver() {
        save();
        return status == null || statusIsError ? null : status.getString();
    }

    // ------------------------------------------------------------------ saving

    private static void send(net.minecraft.network.packet.CustomPayload payload) {
        if (ClientPlayNetworking.canSend(payload.getId())) ClientPlayNetworking.send(payload);
    }

    /** Sends the texts and formats if they changed. */
    private void save() {
        if (!canEdit) return;
        keepTexts();
        MiniGamePageData edited = saved.withTexts(titleValue, descriptionValue).withFormats(formats);
        if (edited.title().equals(saved.title()) && edited.description().equals(saved.description()) && edited.formats().equals(saved.formats())) return;
        saved = edited;
        send(new MiniGamePagePayloads.Edit(hand, page, edited.title(), edited.description(), edited.formats()));
    }

    @Override
    public void removed() {
        save();
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MiniGamePageData data = current();
        MiniGamePageImage image = pendingImage != null ? pendingImage : data.image();
        if (removeButton != null) removeButton.active = canEdit && image != null;
        // Under a popup nothing is hovered
        int mx = popup != null ? -1000 : mouseX, my = popup != null ? -1000 : mouseY;
        super.render(context, mx, my, delta);
        if (overInfo(mx, my) && !palette) drawInfo(context, mouseX, mouseY);
        else if (tab == Tab.PIPES) drawPipesOverlay(context, mx, my);
        else if (tab == Tab.RESULTS) drawPodiumsOverlay(context, mx, my);
        else if (tab == Tab.FORMATS) drawFormatsOverlay(context, mx, my);
        else drawPageOverlay(context, mx, my);
        if (palette && descriptionBox != null) drawPalette(context, mouseX, mouseY);
        if (popup != null) popup.render(context, mouseX, mouseY);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        drawBinderPage(context, mouseX, mouseY);
        MiniGamePageData data = current();
        int lx = x + LX, rx = x + RX;
        switch (tab) {
            case PIPES -> drawPipes(context, data, mouseX, mouseY);
            case RESULTS -> drawPodiums(context, data, mouseX, mouseY);
            case FORMATS -> drawFormats(context, data, mouseX, mouseY);
            default -> drawPageTab(context, data, lx, rx, mouseX, mouseY);
        }
        ConsolePaint.infoButton(context, infoX(), y + M);
        // The status line: on the Page tab under the formats, else at the bottom left
        if (status != null) {
            int colour = statusIsError ? RED : GREEN2;
            if (tab == Tab.PAGE) {
                List<OrderedText> lines = textRenderer.wrapLines(status, CW + 1);
                int top = Math.max(y + M + 80, y + CHIPS_Y + pageChipsHeight(data) + 3);
                for (int i = 0; i < Math.min(2, lines.size()) && top + i * 10 + 8 <= y + TOOLBAR_Y - 2; i++)
                    context.drawText(textRenderer, lines.get(i), rx, top + i * 10, colour, false);
            } else {
                context.drawText(textRenderer, fit(status, FULL - 124 - 6), lx, y + BOTTOM_Y + 4, colour, false);
            }
        }
    }

    // ------------------------------------------------------------------ the page and its dividers

    private int tabY(Tab each) {
        return y + TABS_Y + each.ordinal() * (TAB_H + TAB_GAP);
    }

    /** The divider under the mouse (its part out of the page), null for none. */
    private @Nullable Tab tabAt(double mouseX, double mouseY) {
        for (Tab each : Tab.values()) {
            int ty = tabY(each);
            if (mouseX >= x + PW && mouseX < x + PW + tabOut[each.ordinal()] && mouseY >= ty + 1 && mouseY < ty + TAB_H - 1) return each;
        }
        return null;
    }

    /**
     * The page (its paper, punched holes, header and footer lines) and its dividers: the others behind the page, the
     * selected one of the page's paper joined to it, its colour as a band on its end. A divider shows its icon, centred
     * in its part seen at rest; hovered, it slides out to show its name.
     */
    private void drawBinderPage(DrawContext context, int mouseX, int mouseY) {
        long now = Util.getMeasuringTimeMs();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        Tab hovered = popup == null ? tabAt(mouseX, mouseY) : null;
        for (Tab each : Tab.values()) {
            float target = each == hovered ? fullOut(each) : REST_OUT;
            float out = tabOut[each.ordinal()];
            out += (target - out) * (1 - (float) Math.exp(-dt * 16));
            if (Math.abs(target - out) < 0.5f) out = target;
            tabOut[each.ordinal()] = out;
        }
        // The dividers at rest behind the page
        for (Tab each : Tab.values()) {
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
        for (Tab each : Tab.values()) {
            int ty = tabY(each), out = Math.round(tabOut[each.ordinal()]);
            boolean selected = each == tab;
            ConsolePaint.pattern(context, "page_tab2_" + each.key + (selected ? "_ink" : "_white"), each.icon,
                    Map.of('#', selected ? each.ramp.shadow() : WHITE), x + PW + iconX(each), ty + (TAB_H - ICON) / 2);
            int labelLeft = x + PW + labelStart(each), labelRight = x + PW + out - (selected ? BAND + 2 : 5);
            if (labelRight > labelLeft + 2) {
                Text label = Text.translatable(KEY + "tab." + each.key);
                context.enableScissor(labelLeft, ty, labelRight, ty + TAB_H);
                if (selected) {
                    context.drawText(textRenderer, label, labelLeft, ty + 4, INK, false);
                    context.drawText(textRenderer, label, labelLeft + 1, ty + 4, INK, false);
                } else {
                    context.drawText(textRenderer, label, labelLeft, ty + 4, WHITE, true);
                }
                context.disableScissor();
            }
        }
    }

    /** The « i » (the same as every menu of the mod's), at the top right of the tab's content. */
    private int infoX() {
        return x + PW - M - 12;
    }

    private boolean overInfo(double mouseX, double mouseY) {
        return mouseX >= infoX() && mouseX < infoX() + 12 && mouseY >= y + M && mouseY < y + M + 12;
    }

    private void drawInfo(DrawContext context, int mouseX, int mouseY) {
        drawWrappedTooltip(context, List.of(Text.translatable(KEY + "tab." + tab.key).formatted(Formatting.GOLD),
                Text.translatable(KEY + tab.key + ".info").formatted(Formatting.GRAY)), mouseX, mouseY);
    }

    // ------------------------------------------------------------------ the « Page » tab

    private FormatChips.Look pageLook(MiniGamePageData data, int index) {
        return new FormatChips.Look(false, false, !data.hasPipesFor(data.formats().get(index)), false, 13);
    }

    private int pageChipsHeight(MiniGamePageData data) {
        List<int[]> at = FormatChips.flow(textRenderer, data.formats(), i -> pageLook(data, i), CW, 3);
        return at.isEmpty() ? 0 : at.getLast()[1] + 13;
    }

    /** The format chip of the Page tab under the mouse, -1 for none. */
    private int pageChipAt(double mouseX, double mouseY) {
        MiniGamePageData data = current();
        List<int[]> at = FormatChips.flow(textRenderer, data.formats(), i -> pageLook(data, i), CW, 3);
        for (int i = 0; i < at.size(); i++) {
            int cx = x + RX + at.get(i)[0], cy = y + CHIPS_Y + at.get(i)[1];
            if (mouseX >= cx && mouseX < cx + at.get(i)[2] && mouseY >= cy && mouseY < cy + 13) return i;
        }
        return -1;
    }

    private Text modifyLink() {
        return Text.translatable(KEY + "formats.modify");
    }

    private boolean overModifyLink(double mouseX, double mouseY) {
        int w = textRenderer.getWidth(modifyLink());
        return mouseX >= x + RX + CW - w && mouseX < x + RX + CW && mouseY >= y + FORMATS_Y - 1 && mouseY < y + FORMATS_Y + 10;
    }

    private void drawPageTab(DrawContext context, MiniGamePageData data, int lx, int rx, int mouseX, int mouseY) {
        int top = y + M;
        // ---- The picture, in its green frame
        ConsolePaint.box(context, lx, top, CW, 81, FRAME, 1, 1);
        MiniGamePageImage image = pendingImage != null ? pendingImage : data.image();
        int px = lx + 3, py = top + 3, pw = CW - 6, ph = 75;
        if (image == null) {
            context.fill(px, py, px + pw, py + ph, 0xFFE6FFF4);
            context.drawGuiTexture(RenderLayer::getGuiTextured, EMPTY_PAGE, lx + (CW - 16) / 2, top + 22, 16, 16, 0xCCFFFFFF);
            centered(context, Text.translatable(KEY + "image.none"), lx + CW / 2, top + 44, GREEN2);
            if (canEdit) centered(context, Text.translatable(KEY + "image.drop_hint"), lx + CW / 2, top + 56, INK3);
        } else {
            MiniGamePageClient.Picture picture = MiniGamePageClient.picture(image, pw, ph);
            if (picture != null) picture.draw(context, px, py, pw, ph, 0xFFFFFFFF);
            else {
                context.fill(px, py, px + pw, py + ph, 0xFFE6FFF4);
                centered(context, Text.translatable(KEY + "image.loading"), lx + CW / 2, top + 34, INK3);
            }
        }

        // ---- The title, written on its line (highlighted while it is being written)
        context.drawText(textRenderer, Text.translatable(KEY + "field.title"), rx, top + 2, INK2, false);
        boolean titleFocused = titleBox != null && titleBox.isFocused();
        if (titleFocused) context.fill(rx, top + 23, rx + CW, top + 27, HIGHLIGHT);
        context.fill(rx, top + 27, rx + CW, top + 28, titleFocused ? TEAL2 : EDGE);
        // ---- The formats: read only, « modifier » leads to their tab
        context.drawText(textRenderer, Text.translatable(KEY + "tab.formats"), rx, y + FORMATS_Y, INK2, false);
        Text link = modifyLink();
        int lw = textRenderer.getWidth(link) - 1;
        boolean overLink = popup == null && overModifyLink(mouseX, mouseY);
        context.drawText(textRenderer, link, rx + CW - lw, y + FORMATS_Y, overLink ? TEAL : TEAL2, false);
        context.fill(rx + CW - lw, y + FORMATS_Y + 8, rx + CW, y + FORMATS_Y + 9, overLink ? TEAL : TEAL2);
        FormatChips.drawFlow(context, textRenderer, data.formats(), i -> pageLook(data, i), rx, y + CHIPS_Y, CW, 3);

        // ---- The description, across the page: its label and toolbar, its ruled lines and margin, its counter on the last line
        context.drawText(textRenderer, Text.translatable(KEY + "field.description"), lx, y + TOOLBAR_Y + 3, INK2, false);
        int ay = y + AREA_Y;
        boolean descriptionFocused = descriptionBox != null && descriptionBox.isFocused();
        for (int ly = ay + 9; ly < ay + AREA_H; ly += 10) context.fill(lx, ly, lx + FULL, ly + 1, descriptionFocused ? 0xFFA8D2D6 : RULE);
        context.fill(lx + 2, ay, lx + 3, ay + AREA_H, MARGIN_LINE);
        if (descriptionBox != null) {
            // The characters shown, out of how many: orange near the end, red at it (and when one was refused)
            int count = descriptionBox.visibleLength(), max = descriptionBox.maxVisible();
            boolean refused = Util.getMeasuringTimeMs() - descriptionBox.refusedAt() < 600;
            int color = count >= max || refused ? RED : count >= max * 9 / 10 ? ORANGE2 : INK3;
            String counter = count + "/" + max;
            context.drawText(textRenderer, counter, lx + FULL - textRenderer.getWidth(counter), ay + (AREA_LINES - 1) * 10 + 2, color, false);
        }
    }

    private void drawPageOverlay(DrawContext context, int mouseX, int mouseY) {
        int chip = pageChipAt(mouseX, mouseY);
        if (chip >= 0) drawFormatTooltip(context, current(), chip, mouseX, mouseY);
        else if (overModifyLink(mouseX, mouseY)) context.drawTooltip(textRenderer, Text.translatable(KEY + "formats.modify.hint"), mouseX, mouseY);
    }

    /** A format's tooltip: its name, what it means at the draw, its missing pipes. */
    private void drawFormatTooltip(DrawContext context, MiniGamePageData data, int index, int mouseX, int mouseY) {
        MiniGameFormat format = data.format(index);
        if (format == null) return;
        List<Text> lines = new ArrayList<>();
        lines.add(format.name().copy().formatted(Formatting.GOLD));
        lines.add(format.meaning().copy().formatted(Formatting.GRAY));
        List<MiniGamePipeRole> missing = data.missing(format);
        if (!missing.isEmpty()) lines.add(missingText(missing).copy().formatted(Formatting.RED));
        drawWrappedTooltip(context, lines, mouseX, mouseY);
    }

    private static Text missingText(List<MiniGamePipeRole> missing) {
        net.minecraft.text.MutableText roles = Text.empty();
        for (int i = 0; i < missing.size(); i++) roles.append(i == 0 ? "" : ", ").append(missing.get(i).text());
        return Text.translatable(KEY + "formats.missing", roles);
    }

    // ------------------------------------------------------------------ the « Formats » tab

    private static final int FORMAT_CHIP_H = 16;

    private FormatChips.Look formatLook(MiniGamePageData data, int index, boolean selected) {
        return new FormatChips.Look(true, selected, !data.hasPipesFor(data.formats().get(index)), canEdit && data.formats().size() > 1, FORMAT_CHIP_H);
    }

    /** The chips of the Formats tab (the edited one as being edited), then « + »: {x, y, w} each, on the screen. */
    private List<int[]> formatChips(MiniGamePageData data) {
        List<MiniGameFormat> shown = new ArrayList<>(data.formats());
        int editing = popup instanceof FormatEditor editor ? editor.index : -1;
        if (editing >= 0 && editing < shown.size()) shown.set(editing, ((FormatEditor) popup).draft);
        MiniGamePageData drawn = data.withFormats(shown);
        List<int[]> at = new ArrayList<>(FormatChips.flow(textRenderer, shown, i -> formatLook(drawn, i, i == editing), FULL - FORMAT_CHIP_H - 3, 3));
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
        for (int[] chip : at) screen.add(new int[]{x + LX + chip[0], y + M + 14 + chip[1], chip[2]});
        return screen;
    }

    private int formatChipAt(double mouseX, double mouseY) {
        List<int[]> chips = formatChips(current());
        for (int i = 0; i < chips.size(); i++) {
            int[] chip = chips.get(i);
            if (mouseX >= chip[0] && mouseX < chip[0] + chip[2] && mouseY >= chip[1] && mouseY < chip[1] + FORMAT_CHIP_H) return i;
        }
        return -1;
    }

    private void drawFormats(DrawContext context, MiniGamePageData data, int mouseX, int mouseY) {
        int lx = x + LX;
        context.drawText(textRenderer, Text.translatable(KEY + "formats.label"), lx, y + M + 2, INK2, false);
        List<int[]> chips = formatChips(data);
        int hovered = popup == null ? formatChipAt(mouseX, mouseY) : -1;
        for (int i = 0; i < data.formats().size(); i++) {
            int[] chip = chips.get(i);
            FormatChips.draw(context, textRenderer, data.formats().get(i), formatLook(data, i, i == hovered), chip[0], chip[1]);
        }
        int[] plus = chips.getLast();
        boolean full = data.formats().size() >= MiniGamePageData.MAX_FORMATS || !canEdit;
        drawPlusChip(context, plus[0], plus[1], full, hovered == chips.size() - 1);
        // Under the chips: whether every format has its pipes, else the first missing
        int bottom = chips.getLast()[1] + FORMAT_CHIP_H + 6;
        Text line = null;
        for (MiniGameFormat format : data.formats()) {
            List<MiniGamePipeRole> missing = data.missing(format);
            if (missing.isEmpty()) continue;
            line = Text.translatable(KEY + "formats.chip.missing", format.name(), missingText(missing));
            break;
        }
        if (line == null) {
            context.drawText(textRenderer, fit(Text.translatable(KEY + "formats.complete"), FULL), lx, bottom, GREEN2, false);
        } else {
            ConsolePaint.disc(context, lx, bottom - 1, 9, BADGE);
            for (int yy : new int[]{2, 3, 4, 6}) PartyGui.pixel(context, lx + 4, bottom - 1 + yy, WHITE);
            context.drawText(textRenderer, fit(line, FULL - 13), lx + 13, bottom, RED, false);
        }
    }

    /**
     * The teal « + » chip: adds a format (its gallery). A disc of an even size ({@value #FORMAT_CHIP_H}): its « + » is
     * two pixels thick and eight long, so that it is exactly centred (pixels 4 to 11 of 0 to 15, both ways). Hovered,
     * the disc lights up round: its outline white, its body lighter.
     */
    private void drawPlusChip(DrawContext context, int px, int py, boolean off, boolean hovered) {
        ConsolePaint.pill(context, px, py, FORMAT_CHIP_H, FORMAT_CHIP_H, off ? KEYCAP_OFF : PLUS, false);
        if (hovered && !off) ConsolePaint.highlight(context, px, py, FORMAT_CHIP_H, FORMAT_CHIP_H, -1, WHITE, 0x40FFFFFF);
        int arm = 4, half = FORMAT_CHIP_H / 2;
        int colour = off ? INK3 : WHITE;
        context.fill(px + half - arm, py + half - 1, px + half + arm, py + half + 1, colour);
        context.fill(px + half - 1, py + half - arm, px + half + 1, py + half + arm, colour);
    }

    private void drawFormatsOverlay(DrawContext context, int mouseX, int mouseY) {
        MiniGamePageData data = current();
        int chip = formatChipAt(mouseX, mouseY);
        if (chip >= 0 && chip < data.formats().size()) {
            drawFormatTooltip(context, data, chip, mouseX, mouseY);
        } else if (chip == data.formats().size()) {
            context.drawTooltip(textRenderer, Text.translatable(data.formats().size() >= MiniGamePageData.MAX_FORMATS
                    ? KEY + "formats.full" : KEY + "formats.add", MiniGamePageData.MAX_FORMATS), mouseX, mouseY);
        }
    }

    private boolean clickFormats(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        MiniGamePageData data = current();
        int chip = formatChipAt(mouseX, mouseY);
        if (chip < 0) return false;
        List<int[]> chips = formatChips(data);
        if (chip == data.formats().size()) {
            if (!canEdit || formats.size() >= MiniGamePageData.MAX_FORMATS) return true;
            playClick();
            int[] at = chips.get(chip);
            popup = new Gallery(at[0], at[1], at[2]);
            return true;
        }
        int[] at = chips.get(chip);
        FormatChips.Look look = formatLook(data, chip, false);
        if (look.removable() && mouseX >= FormatChips.removeX(textRenderer, data.formats().get(chip), look, at[0])) {
            formats.remove(chip);
            playClick();
            refreshTestButton();
            return true;
        }
        if (!canEdit) return true;
        playClick();
        popup = new FormatEditor(chip, at[0], at[1], at[2]);
        return true;
    }

    // ------------------------------------------------------------------ popups over the page

    /** A clickable part of a popup: where, what it does, its tooltip. */
    private record Hit(int x, int y, int w, int h, Runnable action, @Nullable Supplier<Text> tooltip) {
        boolean over(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }
    }

    /**
     * A popup anchored to a chip: the rest dimmed, the chip again on top, a gold-rimmed card under it with a pointer to
     * it. Escape or a click outside closes it ({@link #cancel}).
     */
    private abstract class Popup {
        final int anchorX, anchorY, anchorW;
        int px, py, pw, ph;
        final List<Hit> hits = new ArrayList<>();

        Popup(int anchorX, int anchorY, int anchorW, int height) {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorW = anchorW;
            pw = FULL;
            ph = height;
            px = x + LX;
            py = Math.min(anchorY + FORMAT_CHIP_H + 6, y + PH - 4 - ph);
        }

        abstract void anchor(DrawContext context);

        abstract void content(DrawContext context, int mouseX, int mouseY);

        void cancel() {
        }

        void render(DrawContext context, int mouseX, int mouseY) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 400);
            context.fill(0, 0, width, height, VEIL);
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
                if (hit.tooltip() != null && hit.over(mouseX, mouseY)) {
                    drawWrappedTooltip(context, List.of(hit.tooltip().get()), mouseX, mouseY);
                    break;
                }
            }
            context.getMatrices().pop();
        }

        /** @return true if the click was the popup's (outside: it closes) */
        boolean click(double mouseX, double mouseY) {
            for (Hit hit : new ArrayList<>(hits)) {
                if (hit.over(mouseX, mouseY)) {
                    playClick();
                    hit.action().run();
                    return true;
                }
            }
            if (mouseX < px || mouseX >= px + pw || mouseY < py - 5 || mouseY >= py + ph) {
                cancel();
                popup = null;
            }
            return true;
        }

        boolean scroll(double mouseX, double mouseY, double amount) {
            return false;
        }

        /** A paper button of the popup. */
        void button(DrawContext context, int bx, int by, int w, int h, Text label, boolean active, boolean green, int mouseX, int mouseY, Runnable action,
                    @Nullable Supplier<Text> tooltip) {
            boolean over = active && mouseX >= bx && mouseX < bx + w && mouseY >= by && mouseY < by + h;
            ConsolePaint.box(context, bx, by, w, h, !active ? KEYCAP_OFF : green ? FRAME : KEYCAP, 1, 1);
            if (over) ConsolePaint.highlight(context, bx, by, w, h, 1, TEAL2, 0);
            int tw = textRenderer.getWidth(label) - 1;
            if (green && active) context.drawText(textRenderer, label, bx + (w - tw) / 2, by + (h - 8) / 2, WHITE, true);
            else context.drawText(textRenderer, label, bx + (w - tw) / 2, by + (h - 7) / 2 + (h - 7) % 2, active ? INK : INK3, false);
            if (active) hits.add(new Hit(bx, by, w, h, action, tooltip));
            else if (tooltip != null) hits.add(new Hit(bx, by, w, h, () -> {
            }, tooltip));
        }
    }

    /** The gallery of ready formats, under the « + » chip: one click adds one. */
    private final class Gallery extends Popup {
        private static final int TILE_W = 145, TILE_H = 22;

        Gallery(int anchorX, int anchorY, int anchorW) {
            super(anchorX, anchorY, anchorW, 15 + ((MiniGameFormat.GALLERY.size() + 2) / 2) * (TILE_H + 2) + 3);
        }

        @Override
        void anchor(DrawContext context) {
            drawPlusChip(context, anchorX, anchorY, false, true);
        }

        @Override
        void content(DrawContext context, int mouseX, int mouseY) {
            Text title = Text.translatable(KEY + "formats.gallery");
            context.drawText(textRenderer, title, px + 5, py + 4, INK, false);
            context.drawText(textRenderer, title, px + 6, py + 4, INK, false);
            for (int i = 0; i <= MiniGameFormat.GALLERY.size(); i++) {
                int tx = px + 4 + (i % 2) * (TILE_W + 2), ty = py + 14 + (i / 2) * (TILE_H + 2);
                boolean over = mouseX >= tx && mouseX < tx + TILE_W && mouseY >= ty && mouseY < ty + TILE_H;
                ConsolePaint.box(context, tx, ty, TILE_W, TILE_H, Ramp.of(0x7e9192, 0xffffff, over ? 0xffffff : 0xe6f3f4, 0xc7dbdc), 1, 1);
                if (over) ConsolePaint.highlight(context, tx, ty, TILE_W, TILE_H, 1, TEAL2, 0);
                MiniGameFormat format = i < MiniGameFormat.GALLERY.size() ? MiniGameFormat.GALLERY.get(i) : null;
                if (format == null) {
                    Text blank = Text.translatable(KEY + "formats.blank");
                    context.drawText(textRenderer, blank, tx + (TILE_W - textRenderer.getWidth(blank) + 1) / 2, ty + 8, TEAL2, false);
                } else {
                    int picW = FormatChips.pictogramWidth(format);
                    FormatChips.drawPictogram(context, format, tx + (TILE_W - picW) / 2, ty + 3);
                    OrderedText name = fit(format.name(), TILE_W - 6);
                    context.drawText(textRenderer, name, tx + (TILE_W - textRenderer.getWidth(name) + 1) / 2, ty + 12, INK, false);
                }
                MiniGameFormat added = format == null ? MiniGameFormat.blank() : format;
                hits.add(new Hit(tx, ty, TILE_W, TILE_H, () -> {
                    if (formats.size() < MiniGamePageData.MAX_FORMATS) formats.add(added);
                    refreshTestButton();
                    popup = null;
                }, null));
            }
        }
    }

    /** A format's editor, under its chip: its kind, its teams (or its players), « same size », its pipes. */
    private final class FormatEditor extends Popup {
        private static final int ROWS_SHOWN = 3, ROW = 15;
        final int index;
        MiniGameFormat draft;
        private int rowsScroll;

        FormatEditor(int index, int anchorX, int anchorY, int anchorW) {
            super(anchorX, anchorY, anchorW, 24 + 17 + ROWS_SHOWN * ROW + 1 + 17 + 10 + 6 + 14 + 5);
            this.index = index;
            this.draft = formats.get(index);
        }

        @Override
        void anchor(DrawContext context) {
            MiniGamePageData data = current().withFormats(withDraft());
            FormatChips.draw(context, textRenderer, draft, new FormatChips.Look(true, true, !data.hasPipesFor(draft), canEdit && formats.size() > 1,
                    FORMAT_CHIP_H), anchorX, anchorY);
        }

        private List<MiniGameFormat> withDraft() {
            List<MiniGameFormat> list = new ArrayList<>(formats);
            list.set(index, draft);
            return list;
        }

        @Override
        void content(DrawContext context, int mouseX, int mouseY) {
            int cx = px + 6, cy = py + 3, cw = pw - 12;
            MiniGamePageData data = current().withFormats(withDraft());
            // « Modifier : » and the chip, live
            Text edit = Text.translatable(KEY + "formats.edit");
            context.drawText(textRenderer, edit, cx, cy + 4, INK2, false);
            FormatChips.draw(context, textRenderer, draft, new FormatChips.Look(true, true, !data.hasPipesFor(draft), false, FORMAT_CHIP_H),
                    cx + textRenderer.getWidth(edit) + 3, cy);
            // The kind: a segmented control
            int ky = cy + 21;
            context.drawText(textRenderer, Text.translatable(KEY + "formats.kind"), cx, ky + 3, INK2, false);
            int sx = cx + 44;
            for (MiniGameFormat.Kind kind : MiniGameFormat.Kind.values()) {
                Text label = Text.translatable(KEY + "formats.kind." + kind.key());
                int w = textRenderer.getWidth(label) - 1 + 10;
                boolean on = draft.kind() == kind, over = mouseX >= sx && mouseX < sx + w && mouseY >= ky && mouseY < ky + 13;
                ConsolePaint.box(context, sx, ky, w, 13, on ? SEGMENT_ON : KEYCAP, 1, 1);
                if (over && !on) ConsolePaint.highlight(context, sx, ky, w, 13, 1, TEAL2, 0);
                if (on) context.drawText(textRenderer, label, sx + 5, ky + 3, WHITE, true);
                else context.drawText(textRenderer, label, sx + 5, ky + 3, INK2, false);
                hits.add(new Hit(sx, ky, w, 13, () -> draft = draft.withKind(kind), null));
                sx += w + 2;
            }
            // One row per team (3 shown, it scrolls past), or the players without teams
            int ry = ky + 17;
            List<MiniGameFormat.Side> sides = draft.sides();
            boolean teams = draft.kind() == MiniGameFormat.Kind.TEAMS;
            rowsScroll = Math.max(0, Math.min(rowsScroll, sides.size() - ROWS_SHOWN));
            for (int row = 0; row < ROWS_SHOWN && row + rowsScroll < sides.size(); row++) {
                int side = row + rowsScroll, rowY = ry + row * ROW;
                int x0 = cx;
                if (teams) {
                    MiniGamePipeRole role = MiniGamePipeRole.ofTeam(side);
                    int colour = FormatChips.TEAM[side];
                    ConsolePaint.box(context, x0, rowY, 12, 12, new Ramp(0xFF1E1E1E, lighter(colour), colour, darker(colour)), 1, 1);
                    String letter = String.valueOf((char) ('A' + side));
                    context.drawText(textRenderer, letter, x0 + (12 - textRenderer.getWidth(letter) + 1) / 2, rowY + 2, WHITE, true);
                    hits.add(new Hit(x0, rowY, 12, 12, () -> {
                    }, () -> role.text()));
                    x0 += 16;
                } else {
                    Text players = Text.translatable(KEY + "formats.players");
                    context.drawText(textRenderer, players, x0, rowY + 3, INK2, false);
                    x0 += textRenderer.getWidth(players) + 4;
                }
                MiniGameFormat.Side range = sides.get(side);
                Text from = Text.translatable(KEY + "formats.from");
                context.drawText(textRenderer, from, x0, rowY + 3, INK2, false);
                x0 += textRenderer.getWidth(from) + 2;
                x0 = stepper(context, x0, rowY, Integer.toString(range.min()), mouseX, mouseY,
                        range.min() > 1, () -> draft = draft.withSide(side, new MiniGameFormat.Side(range.min() - 1, range.max())),
                        range.min() < MiniGameFormat.MAX_COUNT, () -> draft = draft.withSide(side, new MiniGameFormat.Side(range.min() + 1,
                                range.infinite() ? range.max() : Math.max(range.max(), range.min() + 1)))) + 5;
                Text to = Text.translatable(KEY + "formats.to");
                context.drawText(textRenderer, to, x0, rowY + 3, INK2, false);
                x0 += textRenderer.getWidth(to) + 2;
                stepper(context, x0, rowY, range.infinite() ? "∞" : Integer.toString(range.max()), mouseX, mouseY,
                        range.infinite() || range.max() > range.min(), () -> draft = draft.withSide(side,
                                new MiniGameFormat.Side(range.min(), range.infinite() ? MiniGameFormat.MAX_COUNT : range.max() - 1)),
                        !range.infinite(), () -> draft = draft.withSide(side,
                                new MiniGameFormat.Side(range.min(), range.max() >= MiniGameFormat.MAX_COUNT ? MiniGameFormat.Side.INFINITE : range.max() + 1)));
                if (teams) {
                    boolean removable = sides.size() > MiniGameFormat.MIN_SIDES;
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
                int aw = textRenderer.getWidth(add) - 1 + 10;
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
                context.drawText(textRenderer, same, bx + 12, ay + 3, draft.sameSize() ? INK : INK2, false);
                hits.add(new Hit(bx, ay, 12 + textRenderer.getWidth(same), 13, () -> draft = draft.withSameSize(!draft.sameSize()), null));
            }
            // Its pipes
            int cyCheck = ay + 17;
            List<MiniGamePipeRole> missing = data.missing(draft);
            if (missing.isEmpty()) {
                context.drawText(textRenderer, fit(Text.translatable(KEY + "formats.pipes_ok"), cw), cx, cyCheck, GREEN2, false);
            } else {
                ConsolePaint.disc(context, cx, cyCheck - 1, 9, BADGE);
                for (int yy : new int[]{2, 3, 4, 6}) PartyGui.pixel(context, cx + 4, cyCheck - 1 + yy, WHITE);
                context.drawText(textRenderer, fit(missingText(missing), cw - 14), cx + 13, cyCheck, RED, false);
            }
            // Annuler / OK
            int oky = cyCheck + 16;
            button(context, px + pw - 6 - 124, oky, 60, 14, Text.translatable(KEY + "formats.cancel"), true, false, mouseX, mouseY, () -> popup = null, null);
            button(context, px + pw - 6 - 60, oky, 60, 14, Text.translatable(KEY + "formats.ok"), true, true, mouseX, mouseY, () -> {
                formats.set(index, draft);
                refreshTestButton();
                popup = null;
            }, null);
        }

        /** « [−] N [+] »: returns its right. */
        private int stepper(DrawContext context, int sx, int sy, String value, int mouseX, int mouseY, boolean canLess, Runnable less, boolean canMore, Runnable more) {
            button(context, sx, sy, 11, 12, Text.literal("-"), canLess, false, mouseX, mouseY, less, null);
            int vw = textRenderer.getWidth(value) - 1;
            context.drawText(textRenderer, value, sx + 12 + (11 - vw) / 2, sy + 3, INK, false);
            context.drawText(textRenderer, value, sx + 13 + (11 - vw) / 2, sy + 3, INK, false);
            button(context, sx + 24, sy, 11, 12, Text.literal("+"), canMore, false, mouseX, mouseY, more, null);
            return sx + 35;
        }

        @Override
        boolean scroll(double mouseX, double mouseY, double amount) {
            rowsScroll = Math.max(0, rowsScroll - (int) Math.signum(amount));
            return true;
        }
    }

    private static int lighter(int colour) {
        return net.minecraft.util.math.ColorHelper.lerp(0.5f, colour, 0xFFFFFFFF) | 0xFF000000;
    }

    private static int darker(int colour) {
        return net.minecraft.util.math.ColorHelper.lerp(0.25f, colour, 0xFF000000) | 0xFF000000;
    }

    // ------------------------------------------------------------------ the « Pipes » tab

    private int columnX(int column) {
        return x + LX + (column % 4) * COLUMN_PITCH;
    }

    private int columnY(int column) {
        return y + COLUMNS_TOP + (column / 4) * COLUMN_ROW;
    }

    private static final int COLUMN_HEIGHT = HEADER + 3 + CARDS_SHOWN * CARD_PITCH - 2;

    /** The column under the mouse, -1 for none. */
    private int columnAt(double mouseX, double mouseY) {
        for (int column = 0; column < COLUMNS.length; column++) {
            int cx = columnX(column), cy = columnY(column);
            if (mouseX >= cx && mouseX < cx + COLUMN_WIDTH && mouseY >= cy && mouseY < cy + COLUMN_HEIGHT) return column;
        }
        return -1;
    }

    /** The card under the mouse, null for none. */
    private @Nullable MiniGamePipeLink cardAt(double mouseX, double mouseY) {
        int column = columnAt(mouseX, mouseY);
        if (column < 0) return null;
        List<MiniGamePipeLink> pipes = current().pipes(COLUMNS[column]);
        double inside = mouseY - (columnY(column) + HEADER + 3);
        int row = (int) Math.floor(inside / CARD_PITCH);
        if (inside < 0 || inside - row * CARD_PITCH >= CARD_H) return null;
        int index = row + scroll[column];
        return row >= 0 && row < CARDS_SHOWN && index < pipes.size() ? pipes.get(index) : null;
    }

    private static Text cardText(MiniGamePipeLink link) {
        net.minecraft.util.math.BlockPos pos = link.mouth().pos();
        return Text.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    private static boolean lightColour(int rgb) {
        int luminance = (((rgb >> 16) & 0xFF) * 299 + ((rgb >> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
        return luminance > 150;
    }

    /** The tab's first line: whether every format has its pipes, what is missing otherwise. */
    private List<Text> pipesStatus(MiniGamePageData data) {
        List<Text> lines = new ArrayList<>();
        if (data.pipeLinks().isEmpty()) {
            lines.add(Text.translatable(KEY + "pipes.none"));
            return lines;
        }
        for (MiniGameFormat format : data.formats()) {
            List<MiniGamePipeRole> missing = data.missing(format);
            if (!missing.isEmpty()) lines.add(Text.translatable(KEY + "formats.chip.missing", format.name(), missingText(missing)));
        }
        return lines;
    }

    private void drawPipes(DrawContext context, MiniGamePageData data, int mouseX, int mouseY) {
        List<Text> missing = pipesStatus(data);
        Text line = missing.isEmpty() ? Text.translatable(KEY + "pipes.complete") : missing.getFirst();
        context.drawText(textRenderer, fit(line, FULL - 16), x + LX, y + M + 2, missing.isEmpty() ? GREEN2 : RED, false);

        int hovered = held != null && dragged ? columnAt(mouseX, mouseY) : -1;
        for (int column = 0; column < COLUMNS.length; column++) {
            MiniGamePipeRole role = COLUMNS[column];
            List<MiniGamePipeLink> pipes = data.pipes(role);
            int cx = columnX(column), cy = columnY(column);
            scroll[column] = Math.max(0, Math.min(scroll[column], pipes.size() - CARDS_SHOWN));
            if (column == hovered) context.fill(cx - 1, cy - 1, cx + COLUMN_WIDTH + 1, cy + COLUMN_HEIGHT + 1, 0x4000B3BD);
            // Header: the role, in the colour of its pipes
            int color = 0xFF000000 | role.color();
            context.fill(cx, cy, cx + COLUMN_WIDTH, cy + HEADER, color);
            boolean light = lightColour(role.color());
            int room = role.hasOrder() ? 54 : COLUMN_WIDTH - 6;
            if (light) context.drawText(textRenderer, fit(role.text(), room), cx + 3, cy + 3, INK, false);
            else context.drawText(textRenderer, fit(role.text(), room), cx + 3, cy + 2, WHITE, true);
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
            // More cards than shown: marks
            if (scroll[column] > 0) context.drawText(textRenderer, "▲", cx + COLUMN_WIDTH - 8, cy + HEADER + 6, INK3, false);
            if (scroll[column] + CARDS_SHOWN < pipes.size()) context.drawText(textRenderer, "▼", cx + COLUMN_WIDTH - 8, cy + COLUMN_HEIGHT - 9, INK3, false);
        }
    }

    /** The column whose order button is under the mouse, -1 for none. */
    private int orderButtonAt(double mouseX, double mouseY) {
        for (int column = 0; column < COLUMNS.length; column++) {
            if (!COLUMNS[column].hasOrder()) continue;
            int left = columnX(column) + COLUMN_WIDTH - 12, top = columnY(column) + 2;
            if (mouseX >= left && mouseX < left + ORDER_BUTTON && mouseY >= top && mouseY < top + ORDER_BUTTON) return column;
        }
        return -1;
    }

    /** The 9 px toggle of a column: two arrows for « each in turn », a dice face for « at random ». */
    private void drawOrderButton(DrawContext context, int left, int top, boolean random, boolean light, boolean hovered) {
        ConsolePaint.box(context, left, top, ORDER_BUTTON, ORDER_BUTTON, new Ramp(0x78000000, 0x5AFFFFFF, hovered && canEdit ? 0x50FFFFFF : 0x28000000, 0x3C000000), 1, 1);
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
        if (link.kind() == fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind.GLASS) return 0xD6ECF2;
        if (link.color() == MiniGamePipeLink.UNKNOWN_COLOR) return link.role().color();
        net.minecraft.util.DyeColor dye = net.minecraft.util.DyeColor.byName(fr.lordfinn.steveparty.blocks.ModBlocks.COLORS[link.color()], null);
        return dye == null ? link.role().color() : dye.getEntityColor() & 0xFFFFFF;
    }

    private void drawCard(DrawContext context, MiniGamePipeLink link, int left, int top, boolean hovered) {
        ConsolePaint.box(context, left, top, COLUMN_WIDTH, CARD_H, CARD, 1, 1);
        if (hovered) ConsolePaint.highlight(context, left, top, COLUMN_WIDTH, CARD_H, 1, TEAL2, 0);
        // The mark of its pipe: plain for plastic, a window in a windowed pipe, half see-through for glass
        int color = 0xFF000000 | pipeColor(link);
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
        if (textRenderer.getWidth(text) - 1 > COLUMN_WIDTH - 12) text = Text.literal(link.mouth().pos().getX() + " " + link.mouth().pos().getZ());
        context.drawText(textRenderer, fit(text, COLUMN_WIDTH - 12), left + 8, top + 4, INK, false);
    }

    /** A tooltip whose long lines are cut (about forty characters wide). */
    private void drawWrappedTooltip(DrawContext context, List<Text> lines, int mouseX, int mouseY) {
        List<OrderedText> wrapped = new ArrayList<>();
        for (Text line : lines) wrapped.addAll(textRenderer.wrapLines(line, TOOLTIP_WIDTH));
        context.drawOrderedTooltip(textRenderer, wrapped, mouseX, mouseY);
    }

    /** Over everything: the card being dragged, or the tooltip of what is under the mouse. */
    private void drawPipesOverlay(DrawContext context, int mouseX, int mouseY) {
        if (held != null && dragged) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200);
            drawCard(context, held, mouseX - COLUMN_WIDTH / 2, mouseY - CARD_H / 2, true);
            context.getMatrices().pop();
            return;
        }
        // The first line, when cut or when more is missing: all of it
        if (mouseX >= x + LX && mouseX < x + PW - M - 16 && mouseY >= y + M && mouseY < y + M + 12) {
            List<Text> missing = pipesStatus(current());
            if (!missing.isEmpty()) {
                List<Text> lines = new ArrayList<>();
                for (Text each : missing) lines.add(each.copy().formatted(Formatting.RED));
                drawWrappedTooltip(context, lines, mouseX, mouseY);
                return;
            }
        }
        int order = orderButtonAt(mouseX, mouseY);
        if (order >= 0) {
            boolean random = current().isRandom(COLUMNS[order]);
            List<Text> lines = new ArrayList<>();
            lines.add(Text.translatable(KEY + (random ? "pipes.order.random" : "pipes.order.in_turn")).formatted(Formatting.GOLD));
            lines.add(Text.translatable(KEY + (random ? "pipes.order.random.hint" : "pipes.order.in_turn.hint")).formatted(Formatting.GRAY));
            if (canEdit) lines.add(Text.translatable(KEY + "pipes.order.change").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
            drawWrappedTooltip(context, lines, mouseX, mouseY);
            return;
        }
        MiniGamePipeLink link = cardAt(mouseX, mouseY);
        if (link == null) return;
        List<Text> lines = new ArrayList<>();
        lines.add(Text.empty().append(link.role().text()).styled(style -> style.withColor(link.role() == MiniGamePipeRole.ENTRY ? 0xB8B8B8 : link.role().color())));
        lines.add(Text.translatable(KEY + "pipes.card.position", cardText(link), link.mouth().dimension().getValue().getPath()).formatted(Formatting.GRAY));
        lines.add(Text.translatable(link.role().translationKey() + ".hint").formatted(Formatting.GRAY));
        lines.add(Text.translatable(KEY + (canEdit ? "pipes.card.hint" : "pipes.card.hint.read_only")).formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        drawWrappedTooltip(context, lines, mouseX, mouseY);
    }

    /** Shows where a linked pipe is: it blinks in the world for a few seconds (seen through the menu, and once it is closed). */
    private void locate(MiniGamePipeLink link) {
        DestinationsRenderer.locate(link.mouth(), LOCATE_TICKS);
        if (client != null && client.player != null) client.player.playSound(fr.lordfinn.steveparty.sounds.ModSounds.SELECT_SOUND_EVENT, 1.0F, 1.4F);
        boolean here = client != null && client.world != null && client.world.getRegistryKey().equals(link.mouth().dimension());
        setStatus(Text.translatable(KEY + (here ? "status.located" : "status.located_elsewhere"), cardText(link)), false);
    }

    private void setRole(MiniGamePipeLink link, @Nullable MiniGamePipeRole role) {
        if (!canEdit || role == link.role()) return;
        send(new MiniGamePagePayloads.PipeRole(hand, page, link.mouth(), role == null ? -1 : role.ordinal()));
        playClick();
    }

    // ------------------------------------------------------------------ the « Results » tab

    /**
     * A linked block's name: the block there (« Podium d'or ») when it is loaded here, else its kind's block (« Socle
     * de mât d'arrivée », « Contrôleur de pas », « Podium »).
     */
    private Text podiumName(MiniGamePodiumLink link) {
        if (loadedHere(link)) {
            net.minecraft.block.Block block = client.world.getBlockState(link.pos().pos()).getBlock();
            boolean expected = switch (link.kind()) {
                case PODIUM -> block instanceof fr.lordfinn.steveparty.blocks.custom.PodiumBlock;
                case COUNTER -> block == fr.lordfinn.steveparty.blocks.ModBlocks.GOAL_POLE_BASE;
                default -> block == fr.lordfinn.steveparty.blocks.ModBlocks.STEP_CONTROLLER;
            };
            if (expected) return block.getName();
        }
        return switch (link.kind()) {
            case PODIUM -> Text.translatable("block.steveparty.podium");
            case COUNTER -> fr.lordfinn.steveparty.blocks.ModBlocks.GOAL_POLE_BASE.getName();
            default -> fr.lordfinn.steveparty.blocks.ModBlocks.STEP_CONTROLLER.getName();
        };
    }

    private boolean loadedHere(MiniGamePodiumLink link) {
        return client != null && client.world != null && client.world.getRegistryKey().equals(link.pos().dimension())
                && client.world.isChunkLoaded(link.pos().pos());
    }

    /** A podium's height (half blocks), -1 when it is not loaded here. */
    private int podiumHeight(MiniGamePodiumLink link) {
        if (link.kind() != MiniGamePodiumLink.Kind.PODIUM || !loadedHere(link)) return -1;
        if (!fr.lordfinn.steveparty.blocks.custom.PodiumBlock.isPodium(client.world.getBlockState(link.pos().pos()))) return -1;
        return fr.lordfinn.steveparty.blocks.custom.PodiumBlock.heightOf(client.world, link.pos().pos());
    }

    /** A podium's place among the page's (0 when not known). */
    private int placeOf(MiniGamePodiumLink link, List<MiniGamePodiumLink> links) {
        int height = podiumHeight(link);
        if (height < 0) return 0;
        java.util.TreeSet<Integer> taller = new java.util.TreeSet<>();
        for (MiniGamePodiumLink other : links) {
            int h = podiumHeight(other);
            if (h > height) taller.add(h);
        }
        return taller.size() + 1;
    }

    /** What a card's block does: a podium's place (the taller, the better), « counter », « ends the game ». */
    private Text podiumRole(MiniGamePodiumLink link, List<MiniGamePodiumLink> links) {
        if (link.kind() != MiniGamePodiumLink.Kind.PODIUM) return Text.translatable(KEY + "podiums.card.sub." + link.kind().key());
        int place = placeOf(link, links);
        if (place <= 0) return Text.translatable(KEY + "podiums.card.sub.podium");
        return place == 1 ? Text.translatable(KEY + "podiums.card.sub.first") : Text.translatable(KEY + "podiums.card.sub.place", place);
    }

    private static String podiumPos(MiniGamePodiumLink link) {
        net.minecraft.util.math.BlockPos pos = link.pos().pos();
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private int podiumCardY(int index) {
        return y + RESULTS_TOP + (index - podiumScroll) * RESULT_PITCH;
    }

    /** The linked block whose card is under the mouse, null for none. */
    private @Nullable MiniGamePodiumLink podiumAt(double mouseX, double mouseY) {
        List<MiniGamePodiumLink> links = current().podiumLinks();
        for (int index = podiumScroll; index < links.size() && index < podiumScroll + RESULT_ROWS; index++) {
            int top = podiumCardY(index);
            if (mouseX >= x + LX && mouseX < x + PW - M && mouseY >= top && mouseY < top + RESULT_H) return links.get(index);
        }
        return null;
    }

    private void drawPodiums(DrawContext context, MiniGamePageData data, int mouseX, int mouseY) {
        List<MiniGamePodiumLink> links = data.podiumLinks();
        int lx = x + LX;
        // The first line: what is linked (without any podium the mini-game names no winner)
        if (!data.hasPodium()) {
            context.drawText(textRenderer, fit(Text.translatable(KEY + "podiums.none"), FULL - 16), lx, y + M + 2, ORANGE2, false);
        } else {
            long podiums = links.stream().filter(link -> link.kind() == MiniGamePodiumLink.Kind.PODIUM).count();
            long counters = links.stream().filter(link -> link.kind() == MiniGamePodiumLink.Kind.COUNTER).count();
            context.drawText(textRenderer, fit(Text.translatable(KEY + "podiums.complete", podiums, counters, links.size() - podiums - counters), FULL - 16),
                    lx, y + M + 2, INK2, false);
        }
        podiumScroll = Math.max(0, Math.min(podiumScroll, links.size() - RESULT_ROWS));
        MiniGamePodiumLink hovered = podiumAt(mouseX, mouseY);
        for (int index = podiumScroll; index < links.size() && index < podiumScroll + RESULT_ROWS; index++) {
            MiniGamePodiumLink link = links.get(index);
            int top = podiumCardY(index);
            ConsolePaint.box(context, lx, top, FULL, RESULT_H, CARD, 1, 1);
            if (link == hovered) ConsolePaint.highlight(context, lx, top, FULL, RESULT_H, 1, TEAL2, 0);
            Text role = podiumRole(link, links);
            int place = link.kind() == MiniGamePodiumLink.Kind.PODIUM ? placeOf(link, links) : 0;
            int colour = place == 1 ? 0xFFFFD83D : place == 2 ? 0xFFC9D3DA : place == 3 ? 0xFFD98A4A
                    : link.kind() == MiniGamePodiumLink.Kind.COUNTER ? 0xFF3A9BFF : link.kind() == MiniGamePodiumLink.Kind.STEP_CONTROLLER ? 0xFFA35CFF : 0xFFFFD83D;
            context.fill(lx + 2, top + 2, lx + 6, top + 14, colour);
            // Its block's name (bold), what it does, where it is (greyed, at the right)
            String where = podiumPos(link);
            int whereWidth = textRenderer.getWidth(where) - 1;
            int room = FULL - 6 - whereWidth - 6 - 10;
            OrderedText name = fit(podiumName(link), room);
            int nameWidth = textRenderer.getWidth(name);
            context.drawText(textRenderer, name, lx + 10, top + 5, INK, false);
            context.drawText(textRenderer, name, lx + 11, top + 5, INK, false);
            if (room - nameWidth - 6 > 12) context.drawText(textRenderer, fit(role, room - nameWidth - 6), lx + 10 + nameWidth + 6, top + 5, INK2, false);
            context.drawText(textRenderer, where, lx + FULL - 6 - whereWidth, top + 5, INK3, false);
        }
        if (podiumScroll > 0) context.drawText(textRenderer, "▲", x + PW - M - 8, y + RESULTS_TOP - 9, INK3, false);
        if (podiumScroll + RESULT_ROWS < links.size()) context.drawText(textRenderer, "▼", x + PW - M - 8, y + RESULTS_TOP + RESULT_ROWS * RESULT_PITCH, INK3, false);
    }

    private void drawPodiumsOverlay(DrawContext context, int mouseX, int mouseY) {
        if (!current().hasPodium() && mouseX >= x + LX && mouseX < x + PW - M - 16 && mouseY >= y + M && mouseY < y + M + 12) {
            drawWrappedTooltip(context, List.of(Text.translatable(KEY + "podiums.none").formatted(Formatting.GOLD)), mouseX, mouseY);
            return;
        }
        MiniGamePodiumLink link = podiumAt(mouseX, mouseY);
        if (link == null) return;
        List<Text> lines = new ArrayList<>();
        lines.add(podiumName(link).copy().formatted(Formatting.GOLD));
        lines.add(Text.translatable(KEY + "podiums.card." + link.kind().key() + ".hint").formatted(Formatting.GRAY));
        lines.add(Text.translatable(KEY + (canEdit ? "podiums.card.hint" : "pipes.card.hint.read_only")).formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        drawWrappedTooltip(context, lines, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (popup != null) return popup.click(mouseX, mouseY);
        if (palette) {
            // The palette open: a swatch picks its colour, a click elsewhere closes it
            int swatch = swatchAt(mouseX, mouseY);
            palette = false;
            if (swatch >= 0 && descriptionBox != null && button == 0) {
                descriptionBox.setColor(PALETTE[swatch]);
                setFocused(descriptionBox);
                playClick();
            }
            return true;
        }
        Tab divider = tabAt(mouseX, mouseY);
        if (divider != null && button == 0) {
            showTab(divider);
            return true;
        }
        if (tab == Tab.PAGE) {
            if (button == 0 && overModifyLink(mouseX, mouseY)) {
                playClick();
                showTab(Tab.FORMATS);
                return true;
            }
            boolean handled = super.mouseClicked(mouseX, mouseY, button);
            // After a tool, the typing goes on in the description
            Element focused = getFocused();
            if (focused == boldTool || focused == italicTool || focused == clearTool) setFocused(descriptionBox);
            return handled;
        }
        if (tab == Tab.FORMATS && clickFormats(mouseX, mouseY, button)) return true;
        if (tab == Tab.RESULTS) {
            MiniGamePodiumLink link = podiumAt(mouseX, mouseY);
            if (link != null && button == 1) {
                if (canEdit) {
                    send(new MiniGamePagePayloads.PodiumUnlink(hand, page, link.pos()));
                    playClick();
                }
                return true;
            }
            if (link != null && button == 0) {
                DestinationsRenderer.locate(link.pos(), LOCATE_TICKS);
                if (client != null && client.player != null) client.player.playSound(fr.lordfinn.steveparty.sounds.ModSounds.SELECT_SOUND_EVENT, 1.0F, 1.4F);
                boolean here = client != null && client.world != null && client.world.getRegistryKey().equals(link.pos().dimension());
                setStatus(Text.translatable(KEY + (here ? "status.located" : "status.located_elsewhere"), podiumPos(link)), false);
                return true;
            }
        }
        if (tab == Tab.PIPES) {
            int order = orderButtonAt(mouseX, mouseY);
            if (order >= 0 && button == 0) {
                if (canEdit) {
                    MiniGamePipeRole role = COLUMNS[order];
                    send(new MiniGamePagePayloads.PipeOrder(hand, page, role.ordinal(), !current().isRandom(role)));
                    playClick();
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
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (popup != null) return true;
        if (held != null && (Math.abs(mouseX - pressX) > 3 || Math.abs(mouseY - pressY) > 3)) dragged = true;
        return held != null || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (held != null && button == 0) {
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
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (popup != null) return popup.scroll(mouseX, mouseY, verticalAmount);
        if (tab == Tab.RESULTS && verticalAmount != 0) {
            podiumScroll = Math.max(0, podiumScroll - (int) Math.signum(verticalAmount));
            return true;
        }
        int column = tab == Tab.PIPES ? columnAt(mouseX, mouseY) : -1;
        if (column >= 0) {
            scroll[column] = Math.max(0, scroll[column] - (int) Math.signum(verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void centered(DrawContext context, Text text, int centerX, int top, int color) {
        OrderedText line = fit(text, CW - 8);
        context.drawText(textRenderer, line, centerX - (textRenderer.getWidth(line) - 1) / 2, top, color, false);
    }

    private OrderedText fit(Text text, int width) {
        if (textRenderer.getWidth(text) - 1 <= width) return text.asOrderedText();
        return net.minecraft.util.Language.getInstance().reorder(net.minecraft.text.StringVisitable.concat(
                textRenderer.trimToWidth(text, Math.max(0, width - textRenderer.getWidth("…"))), net.minecraft.text.StringVisitable.plain("…")));
    }
}
