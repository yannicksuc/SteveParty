package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.gui.ConsoleButton;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.ConsolePaint.Ramp;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.RichTextBox;
import fr.lordfinn.steveparty.client.minigame.MiniGamePageClient;
import fr.lordfinn.steveparty.client.minigame.PageImagePicker;
import fr.lordfinn.steveparty.client.renderer.DestinationsRenderer;
import fr.lordfinn.steveparty.minigame.MiniGameMode;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePageImage;
import fr.lordfinn.steveparty.minigame.MiniGamePageImages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeLink;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.minigame.MiniGamePodiumLink;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * The editor of a mini-game page, opened by right-clicking the page in hand: « the binder page » (the art sources
 * build_page_editor_v2.py). The page's paper with its punched holes, its fields written on it; three dividers on its
 * right edge (Page, Pipes, Results), an icon each, popping out to show their name while hovered.
 * <p>
 * On the Page tab, on the left its picture (picked on the player's computer, or dropped on the window) and its copies;
 * on the right its title, its description (formatted as it is shown, on ruled lines), the ways to play it accepts
 * (checkboxes) and its numbers of players. What is written is sent when the editor closes; the picture is sent as soon
 * as it is picked. A player who may not build only reads the page.
 * <p>
 * The « Pipes » tab shows the pipes linked to the page (a click on a pipe mouth, page in hand) as cards in a column
 * per role, each with a mark in the colour of its pipe: dragging a card to another column gives it that role, a
 * right click unlinks it, a click shows where the pipe is (it blinks in the world for a few seconds). The small
 * button of a column says how its players are sent to its pipes: each in turn, or at random. On its first line, what
 * is missing for the mini-game to be played in the ways it ticks; on the Page tab, the ways to play that miss pipes
 * carry a « ! ».
 * <p>
 * The « Results » tab lists the podiums, the goal pole bases and the step controllers linked to the page (a click on
 * them, page in hand), with their block's name and where they are: the podiums are the places of the mini-game (the
 * taller the column, the better the place), the bases its counters, the step controllers what ends it with a redstone
 * pulse. A click on a card shows where it is, a right click unlinks it.
 */
public class MiniGamePageEditorScreen extends Screen {
    private static final String KEY = "gui.steveparty.mini_game_page.";
    // The grid of the mock-up (GUI px, from the page's corner)
    private static final int PW = 320, PH = 224, M = 10, LX = 10, RX = 166, CW = 144;
    private static final int BOTTOM_Y = 198, BOTTOM_H = 16;
    private static final int[] HOLES = {18, 56, 94, 132, 170, 208};
    /** The dividers: 16 px high, 4 apart, the first at y 14; at rest {@code REST_OUT} px out of the page (an icon). */
    private static final int TABS_Y = 14, TAB_H = 16, TAB_GAP = 4, TAB_ROOT = 4, REST_OUT = 22, ICON_X = 3;
    /** Width of the tooltips drawn here: about forty characters. */
    private static final int TOOLTIP_WIDTH = 200;

    // The paper's colours
    private static final int PAPER = 0xFFE6F3F4, PAPER2 = 0xFFD6EBEC, PAPER3 = 0xFFC7DBDC, EDGE = 0xFF7E9192, RULE = 0xFFC3DCDE, WHITE = 0xFFFFFFFF;
    private static final int TEAL = 0xFF00B3BD, TEAL2 = 0xFF008C95, GREEN2 = 0xFF00AC82, ORANGE = 0xFFFDA757, ORANGE2 = 0xFFD88029, RED = 0xFFD9283B;
    private static final int INK = 0xFF1E3A40, INK2 = 0xFF4F6F74, INK3 = 0xFF8AA3A6, NOTE = 0xFF2F6F9A, HIGHLIGHT = 0xFFFFF2A0, MARGIN_LINE = 0xFFF4C9A0;
    private static final Ramp PAPER_RAMP = Ramp.of(0x7e9192, 0xffffff, 0xe6f3f4, 0xc7dbdc);
    private static final Ramp CARD = Ramp.of(0x7e9192, 0xffffff, 0xffffff, 0xc7dbdc);
    private static final Ramp FRAME = Ramp.of(0x005a40, 0x8ff5d0, 0x00c792, 0x00ac82);
    private static final Ramp INFO = Ramp.of(0x004a50, 0x8ff0f6, 0x00b3bd, 0x008c95);
    private static final Ramp HOLE = Ramp.of(0x7e9192, 0x9fb4b6, 0xb9cacb, 0x9fb4b6);
    private static final Ramp BADGE_RED = Ramp.of(0x4a0808, 0xffb7ae, 0xe8413c, 0xb02e26);
    private static final Ramp BADGE_GREY = Ramp.of(0x5a5a5a, 0xd0d0d0, 0xa8a8a8, 0x808080);

    /** The three dividers: their name's key, their colour, their icon (8 x 8). */
    private enum Tab {
        PAGE("page", Ramp.of(0x004a50, 0x8ff0f6, 0x00b3bd, 0x008c95),
                new String[]{"######..", "#....##.", "#.##..#.", "#.....#.", "#.###.#.", "#.....#.", "#######.", "........"}),
        PIPES("pipes", Ramp.of(0x004a33, 0x8ff5d0, 0x00c792, 0x00ac82),
                new String[]{"..####..", "..#..#..", "..#..#..", ".######.", ".#....#.", ".######.", "..#..#..", "..####.."}),
        RESULTS("podiums", Ramp.of(0x5a2800, 0xffd6a0, 0xfda757, 0xd88029),
                new String[]{"...###..", "...#.#..", "####.#..", "#..#.###", "#..#.#.#", "#..#.#.#", "########", "........"});

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
    private final float[] tabOut = {REST_OUT, REST_OUT, REST_OUT};
    private long lastFrame = Util.getMeasuringTimeMs();
    private RichTextBox titleBox;
    private RichTextBox descriptionBox;
    /** The description's toolbar: bold, italic, colour (its palette), clear the formatting. */
    private Tool boldTool, italicTool, colorTool, clearTool;
    private boolean palette;
    private final EnumSet<MiniGameMode> modes;
    private int minPlayers, maxPlayers;
    private final Checkbox[] modeBoxes = new Checkbox[MiniGameMode.values().length];
    private ConsoleButton removeButton;
    /**
     * The « Test » button, on every tab: plays the mini-game out of any party with those near its pipes, or stops the
     * test being played. Whether it can is asked to the server every second ({@link #onTestStatus}).
     */
    private ConsoleButton testButton;
    private fr.lordfinn.steveparty.minigame.MiniGameTest.@Nullable Status testStatus;
    private int testPlayers, testMode = -1, testPoll;

    /** The picture just picked, shown until the server says what became of it. */
    private @Nullable MiniGamePageImage pendingImage;
    private boolean picking;
    private @Nullable Text status;
    private boolean statusIsError;
    private boolean opened;

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
    /** What the tooltips of the ways to play were made for (the pipes and the ways ticked). */
    private int modeTooltipsKey;
    /** Ticks a located pipe blinks in the world. */
    private static final int LOCATE_TICKS = 100;
    /** The description's toolbar: tools of {@code TOOL} px, on the line of its label, at its right; its ruled lines. */
    private static final int TOOL = 14, TOOL_GAP = 2, TOOLBAR_Y = M + 31, AREA_Y = M + 48, AREA_H = 51;
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
        this.modes = EnumSet.copyOf(data.modes());
        this.minPlayers = data.minPlayers();
        this.maxPlayers = data.maxPlayers();
        this.titleValue = data.title();
        this.descriptionValue = data.description();
        if (status != null) this.status = Text.literal(status);
        else if (!this.canEdit) this.status = Text.translatable(KEY + "status.read_only");
    }

    /** The page as the server has it now (its picture may change while the editor is open). */
    private MiniGamePageData current() {
        MiniGamePageData known = MiniGamePageClient.page(page);
        return known != null ? known : saved;
    }

    /** How far a divider goes out of the page to show its name. */
    private int fullOut(Tab each) {
        return 14 + textRenderer.getWidth(Text.translatable(KEY + "tab." + each.key)) + (each == tab ? 14 : 8);
    }

    @Override
    protected void init() {
        // The page and its dividers at rest, centred; a popped divider stays on the screen
        int maxOut = 0;
        // (the same place on every tab: as if each divider were the selected one, the longest)
        for (Tab each : Tab.values()) maxOut = Math.max(maxOut, 14 + textRenderer.getWidth(Text.translatable(KEY + "tab." + each.key)) + 14);
        x = Math.max(2, Math.min((width - PW - REST_OUT) / 2, width - 2 - PW - maxOut));
        y = Math.max(2, (height - PH) / 2);
        // init() runs again on every resize and tab change: keep what the player already typed
        keepTexts();
        String title = titleValue, description = descriptionValue;
        int lx = x + LX, rx = x + RX;
        boolean pageTab = tab == Tab.PAGE;

        // ---- The bottom row: « Test » and « Done » on the right column's edges
        int right = pageTab ? rx + CW : x + PW - M;
        testButton = addDrawableChild(new ConsoleButton(right - 124, y + BOTTOM_Y, 60, BOTTOM_H, Text.translatable(KEY + "test"),
                ConsoleButton.Kind.PAPER_TEAL, null, this::clickTest));
        addDrawableChild(new ConsoleButton(right - 60, y + BOTTOM_Y, 60, BOTTOM_H, net.minecraft.screen.ScreenTexts.DONE, ConsoleButton.Kind.PAPER_GREEN, null, this::close));
        refreshTestButton();
        if (testStatus == null) queryTest();
        if (!pageTab) {
            titleBox = null;
            descriptionBox = null;
            boldTool = italicTool = colorTool = clearTool = null;
            palette = false;
            removeButton = null;
            java.util.Arrays.fill(modeBoxes, null);
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

        // ---- Left: copies, on the bottom row
        ConsoleButton copy = addDrawableChild(new ConsoleButton(lx, y + BOTTOM_Y, 70, BOTTOM_H, Text.translatable(KEY + "copy"), ConsoleButton.Kind.PAPER, null, () -> {
            save();
            send(new MiniGamePagePayloads.Action(hand, page, MiniGamePagePayloads.Action.Kind.COPY));
        }));
        copy.setTooltip(Tooltip.of(Text.translatable(KEY + "copy.hint")));
        copy.active = canEdit;
        ConsoleButton unlink = addDrawableChild(new ConsoleButton(lx + 74, y + BOTTOM_Y, 70, BOTTOM_H, Text.translatable(KEY + "unlink"), ConsoleButton.Kind.PAPER, null, () -> {
            save();
            send(new MiniGamePagePayloads.Action(hand, page, MiniGamePagePayloads.Action.Kind.UNLINK));
        }));
        unlink.setTooltip(Tooltip.of(Text.translatable(KEY + "unlink.hint")));
        unlink.active = canEdit && linked;

        // ---- Right: the title, written on its line
        titleBox = new RichTextBox(textRenderer, rx, y + M + 15, CW, 12, Text.translatable(KEY + "field.title"),
                Text.translatable(KEY + "field.title.placeholder"), MiniGamePageData.MAX_TITLE_LENGTH, 1, PAPER)
                .paper(2, 2, 1, INK, INK3, 0xFF9FD8FF).plain(true);
        titleBox.setPlain(title);
        titleBox.setEditable(canEdit);
        addDrawableChild(titleBox);

        // The description: edited as it is shown (never its codes), on ruled lines
        descriptionBox = new RichTextBox(textRenderer, rx, y + AREA_Y, CW, 42, Text.translatable(KEY + "field.description"),
                Text.translatable(KEY + "field.description.placeholder"), MiniGamePageData.MAX_DESCRIPTION_LENGTH, MiniGamePageData.MAX_DESCRIPTION_LINES, PAPER)
                .paper(6, 2, 4, INK, INK3, 0xFF9FD8FF);
        descriptionBox.setCodes(description);
        descriptionBox.setEditable(canEdit);
        addDrawableChild(descriptionBox);

        // Its toolbar, on the line of its label: on the selection, or on what is typed next
        int tx = rx + CW - 4 * TOOL - 3 * TOOL_GAP, ty = y + TOOLBAR_Y;
        boldTool = tool(tx, ty, "bold", "Ctrl+B", () -> descriptionBox.toggleBold(), () -> descriptionBox.isBold());
        italicTool = tool(tx + (TOOL + TOOL_GAP), ty, "italic", "Ctrl+I", () -> descriptionBox.toggleItalic(), () -> descriptionBox.isItalic());
        colorTool = tool(tx + 2 * (TOOL + TOOL_GAP), ty, "color", null, () -> palette = !palette, () -> palette);
        clearTool = tool(tx + 3 * (TOOL + TOOL_GAP), ty, "clear", null, () -> descriptionBox.clearFormatting(), () -> false);
        palette = false;

        // ---- Right: the ways to play, ticked (at least one): « Each for themselves », then the teams
        int cx = rx + 46;
        for (MiniGameMode mode : MiniGameMode.values()) {
            Text label = mode == MiniGameMode.FREE_FOR_ALL ? mode.text() : Text.literal(Integer.toString(mode.teams()));
            Checkbox box = mode == MiniGameMode.FREE_FOR_ALL ? new Checkbox(rx, y + M + 113, label, mode)
                    : new Checkbox(cx, y + M + 129, label, mode);
            if (mode != MiniGameMode.FREE_FOR_ALL) cx += box.getWidth() + 8;
            box.active = canEdit;
            modeBoxes[mode.ordinal()] = addDrawableChild(box);
        }

        // ---- Right: players
        stepper(rx + 20, y + M + 162, () -> minPlayers, value -> {
            minPlayers = value;
            if (maxPlayers < minPlayers) maxPlayers = minPlayers;
        });
        stepper(rx + 80 + 20, y + M + 162, () -> maxPlayers, value -> {
            maxPlayers = value;
            if (minPlayers > maxPlayers) minPlayers = maxPlayers;
        });

        modeTooltipsKey = 0;
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
            if (active && (isHovered() || isFocused())) context.drawBorder(left, top, TOOL, TOOL, TEAL2);
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
                    // The colour of the selection (or of what is typed next) under three stripes: the palette
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

    /** A way to play: a box ticked in green, its name, a « ! » when the page misses its pipes. */
    private final class Checkbox extends PressableWidget {
        private final MiniGameMode mode;

        Checkbox(int left, int top, Text label, MiniGameMode mode) {
            super(left, top, 12 + textRenderer.getWidth(label) - 1 + (current().missing(mode).isEmpty() ? 0 : 12), 13, label);
            this.mode = mode;
        }

        @Override
        public void onPress() {
            toggle(mode);
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean on = modes.contains(mode);
            int left = getX(), cy = getY() + 2;
            ConsolePaint.box(context, left, cy, 9, 9, CARD, 1, 1);
            if (active && (isHovered() || isFocused())) context.drawBorder(left, cy, 9, 9, TEAL2);
            if (on) {
                int[][] tick = {{2, 4}, {3, 5}, {4, 6}, {5, 5}, {6, 4}, {7, 3}};
                for (int[] p : tick) {
                    PartyGui.pixel(context, left + p[0], cy + p[1], GREEN2);
                    PartyGui.pixel(context, left + p[0], cy + p[1] - 1, GREEN2);
                }
            }
            int labelWidth = textRenderer.getWidth(getMessage()) - 1;
            context.drawText(textRenderer, getMessage(), left + 12, getY() + 3, on ? INK : INK2, false);
            if (!current().missing(mode).isEmpty()) {
                // Its pipes are missing: red when it is ticked, grey otherwise
                int bx = left + 12 + labelWidth + 3;
                ConsolePaint.disc(context, bx, cy, 9, on ? BADGE_RED : BADGE_GREY);
                for (int yy : new int[]{2, 3, 4, 6}) PartyGui.pixel(context, bx + 4, cy + yy, WHITE);
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
        return x + RX + CW - PALETTE_WIDTH;
    }

    private int paletteY() {
        return y + M + 47;
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
        if (palette && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            palette = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * The tooltips of the ways to play: what each is, the pipes it needs and those that are missing. Made again only
     * when the pipes or the ways ticked change.
     */
    private void refreshModeTooltips(MiniGamePageData data) {
        int key = 31 * data.pipeLinks().hashCode() + modes.hashCode() + 1;
        if (key == modeTooltipsKey || modeBoxes[0] == null) return;
        modeTooltipsKey = key;
        for (MiniGameMode mode : MiniGameMode.values()) {
            net.minecraft.text.MutableText text = Text.translatable(mode.translationKey()).formatted(Formatting.GOLD).append("\n")
                    .append(Text.translatable(mode.translationKey() + ".hint").formatted(Formatting.WHITE)).append("\n")
                    .append(Text.translatable(mode.translationKey() + ".pipes").formatted(Formatting.GRAY));
            List<MiniGamePipeRole> missing = data.missing(mode);
            if (!missing.isEmpty()) {
                net.minecraft.text.MutableText roles = Text.empty();
                for (int i = 0; i < missing.size(); i++) roles.append(i == 0 ? "" : ", ").append(missing.get(i).text());
                text.append("\n").append(Text.translatable(KEY + "field.type.missing", roles).formatted(Formatting.RED));
            }
            text.append("\n").append(Text.translatable(KEY + "field.type.hint").formatted(Formatting.DARK_GRAY));
            modeBoxes[mode.ordinal()].setTooltip(Tooltip.of(text));
        }
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

    /** « − value + » : the two 13 px buttons of a number of players. */
    private void stepper(int left, int top, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
        ConsoleButton minus = addDrawableChild(new ConsoleButton(left, top, 13, 13, Text.literal("-"), ConsoleButton.Kind.PAPER, null, () -> {
            int step = hasShiftDown() ? 4 : 1;
            setter.accept(Math.max(MiniGamePageData.MIN_PLAYERS, getter.getAsInt() - step));
        }));
        ConsoleButton plus = addDrawableChild(new ConsoleButton(left + 31, top, 13, 13, Text.literal("+"), ConsoleButton.Kind.PAPER, null, () -> {
            int step = hasShiftDown() ? 4 : 1;
            setter.accept(Math.min(MiniGamePageData.MAX_PLAYERS, getter.getAsInt() + step));
        }));
        minus.active = plus.active = canEdit;
    }

    private void toggle(MiniGameMode mode) {
        // At least one way to play
        if (modes.contains(mode)) {
            if (modes.size() > 1) modes.remove(mode);
        } else {
            modes.add(mode);
        }
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

    /** The server says whether the page can be tested now, with how many players and in which way to play. */
    public void onTestStatus(UUID about, int status, int players, int mode) {
        if (!about.equals(page)) return;
        fr.lordfinn.steveparty.minigame.MiniGameTest.Status[] values = fr.lordfinn.steveparty.minigame.MiniGameTest.Status.values();
        testStatus = status >= 0 && status < values.length ? values[status] : null;
        testPlayers = players;
        testMode = mode;
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
        if (!canEdit) why = Text.translatable(KEY + "status.read_only");
        else if (testStatus == null) why = Text.translatable(KEY + "test.tooltip.unknown");
        else if (ready) {
            MiniGameMode[] ways = MiniGameMode.values();
            why = Text.translatable(KEY + "test.tooltip.ready", testPlayers, testMode >= 0 && testMode < ways.length ? ways[testMode].text() : Text.empty());
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

    /** Sends the texts and settings if they changed. */
    private void save() {
        if (!canEdit) return;
        keepTexts();
        MiniGamePageData edited = saved.withTexts(titleValue, descriptionValue).withModes(modes).withPlayers(minPlayers, maxPlayers);
        if (edited.title().equals(saved.title()) && edited.description().equals(saved.description())
                && edited.modes().equals(saved.modes()) && edited.minPlayers() == saved.minPlayers() && edited.maxPlayers() == saved.maxPlayers()) return;
        saved = edited;
        send(new MiniGamePagePayloads.Edit(hand, page, edited.title(), edited.description(), MiniGameMode.toMask(edited.modes()),
                edited.minPlayers(), edited.maxPlayers()));
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
        if (tab == Tab.PAGE) refreshModeTooltips(data);
        super.render(context, mouseX, mouseY, delta);
        if (overInfo(mouseX, mouseY) && !palette) drawInfo(context, mouseX, mouseY);
        else if (tab == Tab.PIPES) drawPipesOverlay(context, mouseX, mouseY);
        else if (tab == Tab.RESULTS) drawPodiumsOverlay(context, mouseX, mouseY);
        else drawPageOverlay(context, mouseX, mouseY);
        if (palette && descriptionBox != null) drawPalette(context, mouseX, mouseY);
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
            default -> drawPageTab(context, data, lx, rx);
        }
        drawInfoButton(context);
        // The status line: on the Page tab under the copies' note, else at the bottom left
        if (status != null) {
            int colour = statusIsError ? RED : GREEN2;
            if (tab == Tab.PAGE) {
                List<OrderedText> lines = textRenderer.wrapLines(status, CW + 1);
                for (int i = 0; i < Math.min(2, lines.size()); i++) context.drawText(textRenderer, lines.get(i), lx, y + M + 166 + i * 10, colour, false);
            } else {
                context.drawText(textRenderer, fit(status, PW - M - 124 - 6 - LX), lx, y + BOTTOM_Y + 4, colour, false);
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
     * selected one of the page's paper joined to it, its colour as a band on its end. A divider shows its icon; hovered,
     * it slides out to show its name.
     */
    private void drawBinderPage(DrawContext context, int mouseX, int mouseY) {
        long now = Util.getMeasuringTimeMs();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        Tab hovered = tabAt(mouseX, mouseY);
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
        // Its colour as a band on its end
        ConsolePaint.pill(context, x + PW + selOut - 12, sy + 1, 12, TAB_H - 2, new Ramp(0, tab.ramp.hi(), tab.ramp.body(), tab.ramp.shadow()), false);
        // The punched holes, the header's two teal lines, the footer's orange line
        for (int hy : HOLES) ConsolePaint.disc(context, x + 2, y + hy, 6, HOLE);
        context.fill(x + 10, y + 3, x + PW - 10, y + 4, TEAL);
        context.fill(x + 10, y + 5, x + PW - 10, y + 6, TEAL2);
        context.fill(x + 10, y + PH - 5, x + PW - 10, y + PH - 3, ORANGE);
        // Their icons, and their names as far as they are out
        for (Tab each : Tab.values()) {
            int ty = tabY(each), out = Math.round(tabOut[each.ordinal()]);
            boolean selected = each == tab;
            ConsolePaint.pattern(context, "page_tab_" + each.key + (selected ? "_ink" : "_white"), each.icon,
                    Map.of('#', selected ? each.ramp.shadow() : WHITE), x + PW + ICON_X, ty + 4);
            int labelLeft = x + PW + 14, labelRight = x + PW + out - (selected ? 12 : 6);
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

    /** The « i »: a 12 px teal disc at the top right of the tab's content. */
    private int infoX() {
        return x + (tab == Tab.PAGE ? RX + CW : PW - M) - 12;
    }

    private boolean overInfo(double mouseX, double mouseY) {
        return mouseX >= infoX() && mouseX < infoX() + 12 && mouseY >= y + M && mouseY < y + M + 12;
    }

    private void drawInfoButton(DrawContext context) {
        int ix = infoX(), iy = y + M;
        ConsolePaint.disc(context, ix, iy, 12, INFO);
        for (int yy : new int[]{3, 5, 6, 7, 8, 9}) PartyGui.pixel(context, ix + 5, iy + yy, WHITE);
    }

    private void drawInfo(DrawContext context, int mouseX, int mouseY) {
        drawWrappedTooltip(context, List.of(Text.translatable(KEY + "tab." + tab.key).formatted(Formatting.GOLD),
                Text.translatable(KEY + tab.key + ".info").formatted(Formatting.GRAY)), mouseX, mouseY);
    }

    // ------------------------------------------------------------------ the « Page » tab

    private void drawPageTab(DrawContext context, MiniGamePageData data, int lx, int rx) {
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
        if (data.image() != null && pendingImage == null && !data.image().uploader().isEmpty()) {
            context.drawText(textRenderer, fit(Text.translatable(KEY + "image.by", data.image().uploader()), CW), lx, top + 103, INK3, false);
        }
        // ---- Copies
        context.drawText(textRenderer, Text.translatable(KEY + "link"), lx, top + 116, INK2, false);
        List<OrderedText> note = textRenderer.wrapLines(Text.translatable(KEY + (linked ? "link.linked" : "link.single")), CW + 1);
        for (int i = 0; i < Math.min(3, note.size()); i++) context.drawText(textRenderer, note.get(i), lx, top + 132 + i * 10, linked ? NOTE : INK2, false);

        // ---- The title, written on its line (highlighted while it is being written)
        context.drawText(textRenderer, Text.translatable(KEY + "field.title"), rx, top + 2, INK2, false);
        boolean titleFocused = titleBox != null && titleBox.isFocused();
        if (titleFocused) context.fill(rx, top + 23, rx + CW, top + 27, HIGHLIGHT);
        context.fill(rx, top + 27, rx + CW, top + 28, titleFocused ? TEAL2 : EDGE);
        // ---- The description: its label and toolbar, its ruled lines and margin, its counter on the fifth line
        context.drawText(textRenderer, Text.translatable(KEY + "field.description"), rx, top + 35, INK2, false);
        int ay = y + AREA_Y;
        boolean descriptionFocused = descriptionBox != null && descriptionBox.isFocused();
        for (int ly = ay + 9; ly < ay + AREA_H; ly += 10) context.fill(rx, ly, rx + CW, ly + 1, descriptionFocused ? 0xFFA8D2D6 : RULE);
        context.fill(rx + 2, ay, rx + 3, ay + AREA_H, MARGIN_LINE);
        if (descriptionBox != null) {
            // The characters shown, out of how many: orange near the end, red at it (and when one was refused)
            int count = descriptionBox.visibleLength(), max = descriptionBox.maxVisible();
            boolean refused = Util.getMeasuringTimeMs() - descriptionBox.refusedAt() < 600;
            int color = count >= max || refused ? RED : count >= max * 9 / 10 ? ORANGE2 : INK3;
            String counter = count + "/" + max;
            context.drawText(textRenderer, counter, rx + CW - textRenderer.getWidth(counter), ay + 42, color, false);
        }
        // ---- The ways to play
        context.drawText(textRenderer, Text.translatable(KEY + "field.type"), rx, top + 103, INK2, false);
        context.drawText(textRenderer, Text.translatable(KEY + "field.teams"), rx, top + 132, INK2, false);
        // ---- Players
        context.drawText(textRenderer, Text.translatable(KEY + "field.players"), rx, top + 148, INK2, false);
        for (int i = 0; i < 2; i++) {
            int gx = rx + i * 80;
            context.drawText(textRenderer, Text.translatable(KEY + (i == 0 ? "field.players.min" : "field.players.max")), gx, top + 165, INK2, false);
            String value = String.valueOf(i == 0 ? minPlayers : maxPlayers);
            int vx = gx + 34 + (16 - textRenderer.getWidth(value)) / 2;
            context.drawText(textRenderer, value, vx, top + 165, INK, false);
            context.drawText(textRenderer, value, vx + 1, top + 165, INK, false);
        }
    }

    private void drawPageOverlay(DrawContext context, int mouseX, int mouseY) {
        // Nothing over the page tab but its widgets' tooltips
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

    /** The tab's first line: whether every way to play ticked has its pipes, what is missing otherwise. */
    private List<Text> pipesStatus(MiniGamePageData data) {
        List<Text> lines = new ArrayList<>();
        if (data.pipeLinks().isEmpty()) {
            lines.add(Text.translatable(KEY + "pipes.none"));
            return lines;
        }
        MiniGamePageData edited = data.withModes(modes);
        for (MiniGameMode mode : MiniGameMode.values()) {
            if (!modes.contains(mode)) continue;
            List<MiniGamePipeRole> missing = edited.missing(mode);
            if (missing.isEmpty()) continue;
            net.minecraft.text.MutableText roles = Text.empty();
            for (int i = 0; i < missing.size(); i++) roles.append(i == 0 ? "" : ", ").append(missing.get(i).text());
            lines.add(Text.translatable(KEY + "pipes.missing", mode.text(), roles));
        }
        return lines;
    }

    private void drawPipes(DrawContext context, MiniGamePageData data, int mouseX, int mouseY) {
        List<Text> missing = pipesStatus(data);
        Text line = missing.isEmpty() ? Text.translatable(KEY + "pipes.complete") : missing.getFirst();
        context.drawText(textRenderer, fit(line, PW - 2 * M - 16), x + LX, y + M + 2, missing.isEmpty() ? GREEN2 : RED, false);

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
        if (hovered) context.drawBorder(left, top, COLUMN_WIDTH, CARD_H, TEAL2);
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
        if (client != null) client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.2F));
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

    /** What a card's block does: a podium's place (the taller, the better), « counter », « ends the game ». */
    private Text podiumRole(MiniGamePodiumLink link, List<MiniGamePodiumLink> links) {
        if (link.kind() != MiniGamePodiumLink.Kind.PODIUM) return Text.translatable(KEY + "podiums.card.sub." + link.kind().key());
        int height = podiumHeight(link);
        if (height < 0) return Text.translatable(KEY + "podiums.card.sub.podium");
        java.util.TreeSet<Integer> taller = new java.util.TreeSet<>();
        for (MiniGamePodiumLink other : links) {
            int h = podiumHeight(other);
            if (h > height) taller.add(h);
        }
        int place = taller.size() + 1;
        return place == 1 ? Text.translatable(KEY + "podiums.card.sub.first") : Text.translatable(KEY + "podiums.card.sub.place", place);
    }

    private static int podiumColour(MiniGamePodiumLink link, Text role, int index) {
        return switch (link.kind()) {
            case COUNTER -> 0xFF3A9BFF;
            case STEP_CONTROLLER -> 0xFFA35CFF;
            default -> 0xFFFFD83D;
        };
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
        int lx = x + LX, cardW = PW - 2 * M;
        // The first line: what is linked (without any podium the mini-game names no winner)
        if (!data.hasPodium()) {
            context.drawText(textRenderer, fit(Text.translatable(KEY + "podiums.none"), cardW - 16), lx, y + M + 2, ORANGE2, false);
        } else {
            long podiums = links.stream().filter(link -> link.kind() == MiniGamePodiumLink.Kind.PODIUM).count();
            long counters = links.stream().filter(link -> link.kind() == MiniGamePodiumLink.Kind.COUNTER).count();
            context.drawText(textRenderer, fit(Text.translatable(KEY + "podiums.complete", podiums, counters, links.size() - podiums - counters), cardW - 16),
                    lx, y + M + 2, INK2, false);
        }
        podiumScroll = Math.max(0, Math.min(podiumScroll, links.size() - RESULT_ROWS));
        MiniGamePodiumLink hovered = podiumAt(mouseX, mouseY);
        // The places, from the block's colour: gold, silver, bronze by place
        for (int index = podiumScroll; index < links.size() && index < podiumScroll + RESULT_ROWS; index++) {
            MiniGamePodiumLink link = links.get(index);
            int top = podiumCardY(index);
            ConsolePaint.box(context, lx, top, cardW, RESULT_H, CARD, 1, 1);
            if (link == hovered) context.drawBorder(lx, top, cardW, RESULT_H, TEAL2);
            Text role = podiumRole(link, links);
            int place = link.kind() == MiniGamePodiumLink.Kind.PODIUM ? placeOf(link, links) : 0;
            int colour = place == 1 ? 0xFFFFD83D : place == 2 ? 0xFFC9D3DA : place == 3 ? 0xFFD98A4A : podiumColour(link, role, index);
            context.fill(lx + 2, top + 2, lx + 6, top + 14, colour);
            // Its block's name (bold), what it does, where it is (greyed, at the right)
            String where = podiumPos(link);
            int whereWidth = textRenderer.getWidth(where) - 1;
            int room = cardW - 6 - whereWidth - 6 - 10;
            OrderedText name = fit(podiumName(link), room);
            int nameWidth = textRenderer.getWidth(name);
            context.drawText(textRenderer, name, lx + 10, top + 5, INK, false);
            context.drawText(textRenderer, name, lx + 11, top + 5, INK, false);
            if (room - nameWidth - 6 > 12) context.drawText(textRenderer, fit(role, room - nameWidth - 6), lx + 10 + nameWidth + 6, top + 5, INK2, false);
            context.drawText(textRenderer, where, lx + cardW - 6 - whereWidth, top + 5, INK3, false);
        }
        if (podiumScroll > 0) context.drawText(textRenderer, "▲", x + PW - M - 8, y + RESULTS_TOP - 9, INK3, false);
        if (podiumScroll + RESULT_ROWS < links.size()) context.drawText(textRenderer, "▼", x + PW - M - 8, y + RESULTS_TOP + RESULT_ROWS * RESULT_PITCH, INK3, false);
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
        if (palette) {
            // The palette open: a swatch picks its colour, a click elsewhere closes it
            int swatch = swatchAt(mouseX, mouseY);
            palette = false;
            if (swatch >= 0 && descriptionBox != null && button == 0) {
                descriptionBox.setColor(PALETTE[swatch]);
                setFocused(descriptionBox);
                if (client != null) client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.2F));
            }
            return true;
        }
        Tab divider = tabAt(mouseX, mouseY);
        if (divider != null && button == 0) {
            showTab(divider);
            return true;
        }
        if (tab == Tab.PAGE) {
            boolean handled = super.mouseClicked(mouseX, mouseY, button);
            // After a tool, the typing goes on in the description
            Element focused = getFocused();
            if (focused == boldTool || focused == italicTool || focused == clearTool) setFocused(descriptionBox);
            return handled;
        }
        if (tab == Tab.RESULTS) {
            MiniGamePodiumLink link = podiumAt(mouseX, mouseY);
            if (link != null && button == 1) {
                if (canEdit) {
                    send(new MiniGamePagePayloads.PodiumUnlink(hand, page, link.pos()));
                    if (client != null) client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.2F));
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
                    if (client != null) client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
