package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.blocks.custom.PartyController.MiniGameGains;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyBank;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Blocker;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Phase;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.BasicGameGeneratorStep;
import fr.lordfinn.steveparty.client.gui.ConsoleButton;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.ConsolePaint.Ramp;
import fr.lordfinn.steveparty.client.gui.PartyButton;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.party.HudPaint;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.PartyCardItem;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.Page;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.*;
import static fr.lordfinn.steveparty.sounds.ModSounds.CLOSE_TILE_GUI_SOUND_EVENT;
import static fr.lordfinn.steveparty.sounds.ModSounds.OPEN_TILE_GUI_SOUND_EVENT;

/**
 * The Party Controller's dashboard: the block's little computer, as its approved mock-up (the art sources
 * build_party_controller_screen_v3.py, « direction 1, v3 »). Its screen in the gold bezel, the selected tab and the
 * bezel one shape opening into it; the inventory in its own bezel below. What a tab is for is in the « i » of its top
 * right corner, what a slot or a control does in a short tooltip; a tab that blocks the party says it with its red label.
 * <ul>
 *     <li><b>Status</b>: before a party, a checklist of what a party needs (board, tokens on the start tiles, mini-game
 *     catalogue, bank, rounds), each line leading to the tab where it is fixed, and « Start the party » (active only
 *     when ready, else it says why); during a party, the round, a timeline of its steps (the current one framed, the
 *     past ones dimmed: wheel or drag to scroll it), what is happening now, the step and the leader; once over, the
 *     standings and « Start a new party ». « Follow » shows the party's HUDs.</li>
 *     <li><b>Players</b>: the tokens in the turn order with their rank, player, stars and coins (it scrolls).</li>
 *     <li><b>Program</b>: the catalogue slot (its mini-games in its tooltip), what the party will be made of, the
 *     party's program (2 rows of 12 card slots, the default party as ghost cards while it is empty, see
 *     {@link BasicGameGeneratorStep#defaultProgram}) and, under them, the timeline of what it will play.</li>
 *     <li><b>Gains</b>: the bank's Inventory Cartridge, the Coin and Star items above their columns (click with an item to
 *     pick it, with an empty hand to go back to the default one), what each place earns.</li>
 *     <li><b>Settings</b>: the rounds, the practice round.</li>
 * </ul>
 * Everything shown comes from the server ({@link PartyDashboardData}), every action is checked there.
 */
public class PartyControllerScreen extends HandledScreen<PartyControllerScreenHandler> {
    private static final String KEY = "gui.steveparty.party_controller.";
    // The grid of the mock-up (GUI px, from the panel's corner)
    private static final int BEZEL = 4, CX = CONTENT_X, CY = CONTENT_Y, CW = CONTENT_WIDTH, CONTENT_BOTTOM = PANEL_HEIGHT - CONTENT_Y;
    private static final int ROW_H = 12, BTN_H = 18, STEP = 16, TAB_PAD = 5, TAB_GAP = 2, TAB_LABEL_Y = 7;
    private static final int INFO_X = CX + CW - ROW_H;
    private static final int BUTTON_Y = CY + 87;
    /** Status: the running party's timeline (its round labels, then its 20 px chips), the three lines under it. */
    private static final int STEPS_LABELS_Y = CY + 15, STEPS_CHIP = 20, STEPS_LEFT = CX + 1, STEPS_WIDTH = 10 * 22 - 2;
    private static final int LINES_Y = CY + 50, LINE = 13;
    /** Status before a party: the checklist. */
    private static final int CHECK_Y = CY + 16, CHECK_ROW = 14;
    /** Players: the rows. */
    private static final int ROWS_Y = CY + 16, ROW = 18, PLAYER_ROWS = 4;
    /** Program: the summary line, the timeline under the cards (16 px chips on the cards' columns). */
    private static final int SUMMARY_Y = CY + 21, PROGRAM_LABELS_Y = CY + 72, PROGRAM_CHIP = 16, PROGRAM_LEFT = PROGRAM_X;
    private static final int PROGRAM_WIDTH = PROGRAM_COLUMNS * 18 - 2;
    /** The rows of round numbers above the chips of a timeline, the gap between two chips. */
    private static final int TIMELINE_LABELS = 10, TIMELINE_GAP = 2;
    /** The running party's timeline keeps this many past steps on the left of the current one. */
    private static final int TIMELINE_PAST = 2;
    /** Gains: a row per place. */
    private static final int GAINS_Y = CY + 21, GAINS_ROW = 17, GAINS_FIELD = GAINS_COLUMN - 2 * STEP - 4;
    /** Settings: a row per setting. */
    private static final int SETTINGS_Y = CY + 18, SETTINGS_ROW = 22;
    private static final int TOOLTIP_WIDTH = 236;

    // The colours of the mock-up: the block's yellows, its dark screen
    private static final Ramp FRAME = Ramp.of(0x9a5200, 0xffdf62, 0xffc600, 0xffaa00);
    private static final Ramp TAB_IDLE = Ramp.of(0x9a5200, 0xffffff, 0xecf7fe, 0xbcc3bf);
    private static final Ramp TAB_IDLE_HOVER = Ramp.of(0x9a5200, 0xffffff, 0xfdfeff, 0xd4dad7);
    private static final Ramp TEAL = Ramp.of(0x002a2a, 0xa0ffff, 0x00bbbb, 0x008c8c);
    private static final Ramp CHIP_GAME = Ramp.of(0x0b2a12, 0xc9f2c0, 0x8fd67f, 0x4e9a45);
    private static final Ramp CHIP_EVENT = Ramp.of(0x3a2400, 0xffe2a8, 0xf4b24a, 0xb5761a);
    private static final Ramp NEUTRAL = Ramp.of(0x2f3a44, 0xffffff, 0xc9d3da, 0x8a96a0);
    private static final Ramp RANK_GOLD = Ramp.of(0x5b2e00, 0xfff87e, 0xffc900, 0xff9e00);
    private static final Ramp RANK_SILVER = Ramp.of(0x2f3a44, 0xffffff, 0xd6dde3, 0xa7b2bc);
    private static final Ramp RANK_BRONZE = Ramp.of(0x4a2410, 0xffd2a8, 0xd98a4a, 0xa8612c);
    private static final Ramp OK = Ramp.of(0x0e3a12, 0xc6f5ae, 0x6ccb52, 0x45a03a);
    private static final Ramp ERROR = Ramp.of(0x4a0808, 0xffb7ae, 0xe8413c, 0xb02e26);
    private static final Ramp WARN = Ramp.of(0x5a2800, 0xffd6a0, 0xf9901d, 0xcc6a10);
    private static final Ramp SWITCH_ON = Ramp.of(0x08270a, 0xa6ef8a, 0x46ae2e, 0x1f6a14);
    private static final Ramp SWITCH_OFF = Ramp.of(0x0d0a18, 0x6a66a8, 0x3d3a66, 0x2c2858);
    private static final Ramp KNOB = Ramp.of(0x2f3a44, 0xffffff, 0xffffff, 0xdfe6ea);
    private static final Ramp HEAD_FRAME = Ramp.of(0x1b1937, 0xffffff, 0xffffff, 0xdfe6ea);
    private static final int SCREEN = 0xFF1B1937, SCREEN_EDGE = 0xFF0D0A18;
    private static final int SLOT_BODY = 0xFF2C2858, SLOT_EDGE = 0xFF0D0A18, SLOT_LOW = 0xFF6A66A8;
    private static final int INK = 0xFFE0EEF3, INK_SOFT = 0xFF9E9CC8, INK_DIM = 0xFF5E5C88, INK_GOLD = 0xFFFFD24A, INK_RED = 0xFFFF8F8F,
            INK_GREEN = 0xFF8FE07A, INK_WARN = 0xFFFFB54A, INK_BLUE = 0xFF8FC0FF, WHITE = 0xFFFFFFFF;
    private static final int GOLD_DARK = 0xFF5B2E00, GOLD_LIGHT = 0xFFFFE3A3;
    private static final int TAB_INK = 0xFF4A3A10, TAB_RED = 0xFFB3202A, TAB_WARN = 0xFFB36200;
    private static final float MARQUEE_SPEED = 28F;
    private static final long MARQUEE_PAUSE_MS = 700;

    private boolean openSoundPlayed;
    private int playersScroll;
    /** A local refusal shown on the page for a few seconds (currency slots). */
    private @Nullable Text flash;
    private long flashUntil;
    /** The data the widgets were built for: rebuilt when a new one arrives. */
    private @Nullable PartyDashboardData builtFor;
    /** The first step shown of each timeline; the running party's follows the current step until it is scrolled. */
    private int stepsScroll, programScroll;
    private boolean stepsScrolled;
    private int stepsFollowed = -1;
    private double timelineDrag;
    /** The step of a timeline under the mouse (drawn this frame), null for none. */
    private @Nullable Text hoveredStep;
    /** The text scrolling under the mouse (too long for its place), and since when. */
    private @Nullable String marqueeText;
    private long marqueeStart;

    public PartyControllerScreen(PartyControllerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = WIDTH;
        this.backgroundHeight = INVENTORY_Y + INVENTORY_PANEL_HEIGHT;
    }

    private @Nullable PartyDashboardData data() {
        return handler.getData();
    }

    private Page page() {
        return handler.getPage();
    }

    @Override
    protected void init() {
        super.init();
        // The tabs above the panel: the whole dashboard centred
        y = Math.max(TABS_HEIGHT, (height - backgroundHeight - TABS_HEIGHT) / 2 + TABS_HEIGHT);
        builtFor = data();
        addTabs();
        PartyDashboardData data = data();
        if (data != null) {
            switch (page()) {
                case STATE -> addStateButtons(data);
                case PLAYERS, PROGRAM -> {}
                case GAINS -> addGainsButtons(data);
                case SETTINGS -> addSettingsButtons(data);
            }
        }
        if (!openSoundPlayed && client != null && client.player != null)
            client.player.playSound(OPEN_TILE_GUI_SOUND_EVENT, 1.0F, 1.0F);
        openSoundPlayed = true;
    }

    private void showPage(Page page) {
        if (page == page()) return;
        handler.setPage(page);
        playersScroll = 0;
        clearAndInit();
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        // New state from the server: the buttons follow (enabled, labels, tooltips)
        if (data() != builtFor) clearAndInit();
    }

    // ------------------------------------------------------------------ tabs

    /** Left edge (from the panel's) and width of each tab: its name and 5 px each side, 2 px apart. */
    private final int[] tabLeft = new int[Page.values().length], tabWide = new int[Page.values().length];

    private void layoutTabs() {
        Page[] tabs = Page.values();
        int room = WIDTH - 2 * BEZEL - TAB_GAP * (tabs.length - 1), total = 0;
        for (Page tab : tabs) total += textRenderer.getWidth(tabName(tab)) - 1 + 2 * TAB_PAD;
        int left = BEZEL;
        for (Page tab : tabs) {
            int index = tab.ordinal();
            // Names too long for the row (a wordy language): tabs of the same width, their names cut
            tabWide[index] = total > room ? room / tabs.length : textRenderer.getWidth(tabName(tab)) - 1 + 2 * TAB_PAD;
            tabLeft[index] = left;
            left += tabWide[index] + TAB_GAP;
        }
    }

    private Text tabName(Page tab) {
        return Text.translatable(KEY + "tab." + key(tab));
    }

    private void addTabs() {
        layoutTabs();
        for (Page tab : Page.values()) {
            int index = tab.ordinal();
            PartyButton button = new PartyButton(x + tabLeft[index], y - TABS_HEIGHT, tabWide[index], TABS_HEIGHT, tabName(tab), b -> showPage(tab)) {
                @Override
                protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                    // Drawn with the panel (drawBackground): the button only handles clicks, focus and tooltip
                }
            };
            button.setTooltip(Tooltip.of(tabTooltip(tab)));
            addDrawableChild(button);
        }
    }

    private Text tabTooltip(Page tab) {
        Text description = Text.translatable(KEY + "tab." + key(tab) + ".tooltip");
        Badge badge = badge(tab);
        if (badge == null) return description;
        return Text.empty().append(badge.reason.copy().formatted(badge.error ? Formatting.RED : Formatting.YELLOW)).append("\n")
                .append(description.copy().formatted(Formatting.GRAY));
    }

    private record Badge(boolean error, Text reason) {}

    /** What needs the player's attention on a tab (its label turns red, or orange), null for nothing. */
    private @Nullable Badge badge(Page tab) {
        PartyDashboardData data = data();
        if (data == null) return null;
        switch (tab) {
            case STATE -> {
                if (data.phase() == Phase.RUNNING) return null;
                Blocker blocker = data.launchBlocker();
                if (blocker == Blocker.NO_BOARD || blocker == Blocker.NO_START || blocker == Blocker.NO_TOKEN)
                    return new Badge(true, blockerText(blocker));
                if (data.board().errors() + data.board().warnings() > 0)
                    return new Badge(false, Text.translatable(KEY + "badge.board", data.board().errors() + data.board().warnings()));
                return null;
            }
            case PLAYERS -> {
                if (data.phase() != Phase.SETUP && data.players().stream().anyMatch(player -> !player.online()))
                    return new Badge(false, Text.translatable(KEY + "badge.offline"));
                if (data.phase() == Phase.SETUP && data.players().isEmpty())
                    return new Badge(true, blockerText(Blocker.NO_TOKEN));
                return null;
            }
            case PROGRAM -> {
                return catalogueBadge(data);
            }
            default -> {
                return null;
            }
        }
    }

    /** What is wrong with the mini-games: no catalogue, an empty one, mini-games without their pipes. Null: nothing. */
    private static @Nullable Badge catalogueBadge(PartyDashboardData data) {
        if (!data.hasCatalogue()) return new Badge(true, Text.translatable(KEY + "badge.no_catalogue"));
        if (data.pages().isEmpty()) return new Badge(true, Text.translatable(KEY + "badge.empty_catalogue"));
        long notPlayable = data.pages().stream().filter(p -> p.playable() == 0).count();
        if (notPlayable > 0) return new Badge(false, Text.translatable(KEY + "badge.no_pipes", notPlayable));
        return null;
    }

    private static String key(Page page) {
        return page.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The tabs at rest, behind the panel (their foot under its top edge). */
    private void drawIdleTabs(DrawContext context, int mouseX, int mouseY) {
        for (Page tab : Page.values()) {
            if (tab == page()) continue;
            int index = tab.ordinal(), tx = x + tabLeft[index], tw = tabWide[index], ty = y - TABS_HEIGHT;
            boolean hovered = mouseX >= tx && mouseX < tx + tw && mouseY >= ty && mouseY < y;
            ConsolePaint.box(context, tx, ty, tw, TABS_HEIGHT + 3, hovered ? TAB_IDLE_HOVER : TAB_IDLE, 2, 2);
        }
    }

    /** The tabs' names: dark on the tabs at rest, light in the selected one; red (orange) on a tab that blocks (warns). */
    private void drawTabLabels(DrawContext context) {
        for (Page tab : Page.values()) {
            int index = tab.ordinal(), tw = tabWide[index];
            int tx = x + tabLeft[index], ty = y - TABS_HEIGHT + TAB_LABEL_Y;
            Badge badge = badge(tab);
            OrderedText label = fit(tabName(tab), tw - 2 * TAB_PAD + 1);
            int lx = tx + (tw - textRenderer.getWidth(label) + 1) / 2;
            if (tab == page()) {
                context.drawText(textRenderer, label, lx, ty, badge == null ? WHITE : badge.error ? INK_RED : INK_WARN, true);
            } else {
                dark(context, label, lx, ty, badge == null ? TAB_INK : badge.error ? TAB_RED : TAB_WARN, WHITE);
            }
        }
    }

    // ------------------------------------------------------------------ buttons of the pages

    private void addStateButtons(PartyDashboardData data) {
        // Follow the party: its HUDs on screen (at the bottom right during a party, else at the bottom left)
        Text followText = Text.translatable(KEY + (data.following() ? "follow.on" : "follow.off"));
        int followWidth = textRenderer.getWidth(followText) - 1 + 20;
        int followX = data.phase() == Phase.RUNNING ? CX + CW - followWidth : CX;
        ConsoleButton follow = addDrawableChild(new ConsoleButton(x + followX, y + BUTTON_Y, followWidth, BTN_H, followText,
                data.following() ? ConsoleButton.Kind.GOLD : ConsoleButton.Kind.SCREEN, null, () -> click(BUTTON_FOLLOW)));
        follow.setTooltip(Tooltip.of(Text.translatable(KEY + "follow.tooltip")));

        if (data.phase() == Phase.RUNNING) return;
        // The main action (the board is checked again every two seconds while the dashboard is open, and at the launch)
        Blocker blocker = data.launchBlocker();
        Text launchText = Text.translatable(KEY + (data.phase() == Phase.ENDED ? "launch.again" : "launch"));
        int launchWidth = textRenderer.getWidth(launchText) - 1 + 20;
        ConsoleButton launch = addDrawableChild(new ConsoleButton(x + CX + CW - launchWidth, y + BUTTON_Y, launchWidth, BTN_H, launchText,
                ConsoleButton.Kind.GREEN, null, () -> {
            click(BUTTON_LAUNCH);
            close();
        }));
        launch.active = blocker == Blocker.NONE;
        launch.setTooltip(Tooltip.of(blocker == Blocker.NONE
                ? Text.translatable(KEY + "launch.tooltip")
                : Text.empty().append(Text.translatable(KEY + "launch.blocked").formatted(Formatting.RED)).append("\n")
                        .append(blockerText(blocker).copy().formatted(Formatting.GRAY))));
    }

    private void addSettingsButtons(PartyDashboardData data) {
        int rowY = y + SETTINGS_Y + 1, sx = x + CX + CW - 68;
        boolean editable = data.canEdit() && data.phase() != Phase.RUNNING;
        Text why = !data.canEdit() ? Text.translatable(KEY + "locked") : Text.translatable(KEY + "settings.rounds.running");
        ConsoleButton minus = addDrawableChild(new ConsoleButton(sx, rowY, STEP, STEP, Text.literal("-"), ConsoleButton.Kind.SCREEN, null,
                () -> click(BUTTON_ROUNDS_DOWN, Screen.hasShiftDown() ? 5 : 1)));
        ConsoleButton plus = addDrawableChild(new ConsoleButton(sx + 52, rowY, STEP, STEP, Text.literal("+"), ConsoleButton.Kind.SCREEN, null,
                () -> click(BUTTON_ROUNDS_UP, Screen.hasShiftDown() ? 5 : 1)));
        minus.active = editable && data.roundsSetting() > PartyControllerEntity.MIN_ROUNDS;
        plus.active = editable && data.roundsSetting() < PartyControllerEntity.MAX_ROUNDS;
        minus.setTooltip(Tooltip.of(editable ? Text.translatable(KEY + "settings.rounds.less") : why));
        plus.setTooltip(Tooltip.of(editable ? Text.translatable(KEY + "settings.rounds.more") : why));
        // A practice round before each mini-game: a switch, its state on its left
        Text state = Text.translatable(KEY + (data.practiceRound() ? "settings.practice.on" : "settings.practice.off"));
        int width = textRenderer.getWidth(state) - 1 + 4 + 24;
        PracticeSwitch practice = addDrawableChild(new PracticeSwitch(x + CX + CW - width, y + SETTINGS_Y + SETTINGS_ROW, width, BTN_H, state, data.practiceRound()));
        practice.active = data.canEdit();
        practice.setTooltip(Tooltip.of(data.canEdit() ? Text.translatable(KEY + "settings.practice.tooltip") : Text.translatable(KEY + "locked")));
    }

    private void addGainsButtons(PartyDashboardData data) {
        for (int row = 0; row < MiniGameGains.ROWS; row++) {
            for (PartyCurrency currency : new PartyCurrency[]{PartyCurrency.COIN, PartyCurrency.STAR}) {
                int left = x + (currency == PartyCurrency.COIN ? GAINS_COIN_X : GAINS_STAR_X), top = y + GAINS_Y + row * GAINS_ROW;
                int amount = data.gains().amount(currency, row);
                int less = gainButton(row, currency, false), more = gainButton(row, currency, true);
                ConsoleButton minus = addDrawableChild(new ConsoleButton(left, top, STEP, STEP, Text.literal("-"), ConsoleButton.Kind.SCREEN, null,
                        () -> click(less, Screen.hasShiftDown() ? 5 : 1)));
                ConsoleButton plus = addDrawableChild(new ConsoleButton(left + GAINS_COLUMN - STEP, top, STEP, STEP, Text.literal("+"), ConsoleButton.Kind.SCREEN, null,
                        () -> click(more, Screen.hasShiftDown() ? 5 : 1)));
                minus.active = data.canEdit() && amount > 0;
                plus.active = data.canEdit() && amount < MiniGameGains.MAX;
                Text why = data.canEdit() ? Text.translatable(KEY + "gains.step") : Text.translatable(KEY + "locked");
                minus.setTooltip(Tooltip.of(why));
                plus.setTooltip(Tooltip.of(why));
            }
        }
    }

    private void click(int button) {
        click(button, 1);
    }

    private void click(int button, int times) {
        if (client == null || client.interactionManager == null) return;
        for (int i = 0; i < times; i++) client.interactionManager.clickButton(handler.syncId, button);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // The tabs at rest, then the panel and the selected tab as one shape, the inventory in its own bezel
        drawIdleTabs(context, mouseX, mouseY);
        int selected = page().ordinal();
        ConsolePaint.tabbedBezel(context, x, y - TABS_HEIGHT, WIDTH, TABS_HEIGHT, PANEL_HEIGHT, tabLeft[selected], tabWide[selected], BEZEL, FRAME,
                SCREEN, SCREEN_EDGE);
        ConsolePaint.bezel(context, x, y + INVENTORY_Y, WIDTH, INVENTORY_PANEL_HEIGHT, BEZEL, FRAME, SCREEN, SCREEN_EDGE);
        drawTabLabels(context);
        for (Slot slot : handler.slots) {
            if (slot.isEnabled()) ConsolePaint.inset(context, x + slot.x - 1, y + slot.y - 1, 17, 17, SLOT_BODY, SLOT_EDGE, SLOT_LOW);
        }
        // The empty catalogue and bank slots show, faded, the item they take (the currency slots are never empty)
        ghostItem(context, handler.getSlot(SLOT_CATALOGUE), ModItems.MINI_GAMES_CATALOGUE);
        ghostItem(context, handler.getSlot(SLOT_BANK), ModItems.INVENTORY_CARTRIDGE);
    }

    private void ghostItem(DrawContext context, Slot slot, Item item) {
        if (!slot.isEnabled() || slot.hasStack()) return;
        ghost(context, new ItemStack(item), x + slot.x, y + slot.y, false);
    }

    /** A faded item (an empty slot's, a ghost card): under the slot's colour, its count with it. */
    private void ghost(DrawContext context, ItemStack stack, int gx, int gy, boolean count) {
        context.drawItem(stack, gx, gy);
        if (count) context.drawStackOverlay(textRenderer, stack, gx, gy);
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 250);
        context.fill(gx, gy, gx + 16, gy + 16, 0xA6000000 | (SLOT_BODY & 0xFFFFFF));
        context.getMatrices().pop();
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        PartyDashboardData data = data();
        if (data == null) {
            OrderedText loading = fit(Text.translatable(KEY + "loading"), CW);
            context.drawText(textRenderer, loading, (WIDTH - textRenderer.getWidth(loading)) / 2, PANEL_HEIGHT / 2 - 4, INK_SOFT, true);
            return;
        }
        int mx = mouseX - x, my = mouseY - y;
        switch (page()) {
            case STATE -> drawState(context, data, mx, my);
            case PLAYERS -> drawPlayers(context, data, mx, my);
            case PROGRAM -> drawProgram(context, data, mx, my);
            case GAINS -> drawGains(context, data);
            case SETTINGS -> drawSettings(context, data);
        }
        infoButton(context, INFO_X, infoY());
        if (flash != null && Util.getMeasuringTimeMs() < flashUntil) {
            int w = textRenderer.getWidth(flash) + 8;
            int fx = (WIDTH - w) / 2, fy = CONTENT_BOTTOM - 14;
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 300);
            ConsolePaint.box(context, fx, fy, w, 14, Ramp.of(0x33030a, 0xff8f8f, 0xd9283b, 0x8e1022), 1, 1);
            context.drawTextWithShadow(textRenderer, flash, fx + 4, fy + 3, WHITE);
            context.getMatrices().pop();
        }
    }

    /** The « i » of the tab: on its header row, or centred on its slot's row (Program, Gains). */
    private int infoY() {
        return page() == Page.PROGRAM || page() == Page.GAINS ? CY + 3 : CY;
    }

    /** The « i »: a 12 px teal LED button, its « i » with its shadow. */
    private static void infoButton(DrawContext context, int ix, int iy) {
        ConsolePaint.disc(context, ix, iy, ROW_H, TEAL);
        int[][] light = {{5, 2}, {5, 4}, {5, 5}, {5, 6}, {5, 7}, {5, 8}}, dark = {{6, 3}, {6, 5}, {6, 6}, {6, 7}, {6, 8}, {6, 9}};
        for (int[] p : dark) PartyGui.pixel(context, ix + p[0], iy + p[1], 0xFF006666);
        for (int[] p : light) PartyGui.pixel(context, ix + p[0], iy + p[1], WHITE);
    }

    /**
     * A header row (12 px): its title (or a pill) on the left, « Read only » before the « i » for who may not change
     * what the tab sets.
     */
    private void header(DrawContext context, Text title, boolean readOnly) {
        int room = INFO_X - 4 - CX;
        if (readOnly) {
            Text locked = Text.translatable(KEY + "read_only");
            int lw = textRenderer.getWidth(locked) - 1;
            context.drawText(textRenderer, locked, INFO_X - 4 - lw, CY + 2, INK_RED, true);
            room -= lw + 6;
        }
        context.drawText(textRenderer, fit(title, room), CX, CY + 2, WHITE, true);
    }

    /** A gold pill on the header row, its label dark (the round). */
    private void headerPill(DrawContext context, Text label) {
        int w = textRenderer.getWidth(label) - 1 + 10;
        ConsolePaint.pill(context, CX, CY, w, ROW_H, FRAME, true);
        dark(context, label.asOrderedText(), CX + 5, CY + 2, GOLD_DARK, GOLD_LIGHT);
    }

    /** Dark text with a light shadow (the mock-ups' {@code dark}). */
    private void dark(DrawContext context, OrderedText text, int tx, int ty, int colour, int shade) {
        context.drawText(textRenderer, text, tx + 1, ty + 1, shade, false);
        context.drawText(textRenderer, text, tx, ty, colour, false);
    }

    private void light(DrawContext context, Text text, int tx, int ty, int colour) {
        context.drawText(textRenderer, text, tx, ty, colour, true);
    }

    private void drawSmallItem(DrawContext context, ItemStack stack, int ix, int iy, int size) {
        if (stack.isEmpty()) return;
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(ix, iy, 0);
        matrices.scale(size / 16f, size / 16f, 1);
        context.drawItem(stack, 0, 0);
        matrices.pop();
    }

    private ItemStack currency(PartyCurrency currency) {
        return handler.getSlot(currency == PartyCurrency.STAR ? SLOT_STAR : SLOT_COIN).getStack();
    }

    /** « [star] 3  [coin] 12 » at the right of a row (56 px). */
    private void drawCounts(DrawContext context, int stars, int coins, int cx, int cy) {
        drawSmallItem(context, currency(PartyCurrency.STAR), cx, cy, 8);
        light(context, Text.literal(Integer.toString(stars)), cx + 10, cy, WHITE);
        drawSmallItem(context, currency(PartyCurrency.COIN), cx + 26, cy, 8);
        light(context, Text.literal(Integer.toString(coins)), cx + 36, cy, WHITE);
    }

    private Text blockerText(Blocker blocker) {
        return Text.translatable(KEY + "blocker." + blocker.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** A scroll bar on the screen: a dark track, a lighter thumb. */
    private static void scrollbar(DrawContext context, int sx, int sy, int height, int scroll, int maxScroll, int shown, int total) {
        context.fill(sx, sy, sx + 3, sy + height, SLOT_BODY);
        int thumb = Math.max(8, height * shown / Math.max(shown, total));
        int ty = sy + (maxScroll == 0 ? 0 : (height - thumb) * scroll / maxScroll);
        context.fill(sx, ty, sx + 3, ty + thumb, SLOT_LOW);
    }

    // ------------------------------------------------------------------ one-line texts that may not fit

    private OrderedText fit(Text text, int width) {
        if (textRenderer.getWidth(text) - 1 <= width) return text.asOrderedText();
        String ellipsis = "…";
        String cut = textRenderer.trimToWidth(text.getString(), Math.max(0, width - textRenderer.getWidth(ellipsis)));
        return Text.literal(cut.stripTrailing() + ellipsis).setStyle(text.getStyle()).asOrderedText();
    }

    /**
     * Draws {@code text} (light, shadowed) in a box {@code maxWidth} wide (panel coordinates): as is when it fits; else
     * cut with « … », and scrolling back and forth, clipped to the box, while {@code hovered}.
     */
    private void drawFitted(DrawContext context, Text text, int tx, int ty, int maxWidth, int color, boolean hovered) {
        int width = textRenderer.getWidth(text) - 1;
        if (width <= maxWidth || !hovered) {
            context.drawText(textRenderer, fit(text, maxWidth), tx, ty, color, true);
            return;
        }
        long now = Util.getMeasuringTimeMs();
        String string = text.getString();
        if (!string.equals(marqueeText)) {
            marqueeText = string;
            marqueeStart = now;
        }
        int travel = width - maxWidth;
        long moveMs = Math.max(1, (long) (travel / MARQUEE_SPEED * 1000F));
        long t = (now - marqueeStart) % (2 * (MARQUEE_PAUSE_MS + moveMs));
        float offset;
        if (t < MARQUEE_PAUSE_MS) offset = 0;
        else if (t < MARQUEE_PAUSE_MS + moveMs) offset = (t - MARQUEE_PAUSE_MS) / (float) moveMs * travel;
        else if (t < 2 * MARQUEE_PAUSE_MS + moveMs) offset = travel;
        else offset = travel - (t - 2 * MARQUEE_PAUSE_MS - moveMs) / (float) moveMs * travel;
        // The scissor is in screen coordinates, the page is drawn from the panel's corner
        context.enableScissor(x + tx, y + ty - 1, x + tx + maxWidth + 1, y + ty + 10);
        context.getMatrices().push();
        context.getMatrices().translate(-offset, 0, 0);
        context.drawText(textRenderer, text, tx, ty, color, true);
        context.getMatrices().pop();
        context.disableScissor();
    }

    private static boolean in(int mx, int my, int left, int top, int width, int height) {
        return mx >= left && mx < left + width && my >= top && my < top + height;
    }

    // ------------------------------------------------------------------ Status page

    /** A line of the checklist: its state, its text, its hint (tooltip) and the tab that fixes it. */
    private record Check(int state, Text text, Text hint, @Nullable Page target) {
        static final int OK = 0, WARN = 1, ERROR = 2, INFO = 3;
    }

    private List<Check> checklist(PartyDashboardData data) {
        List<Check> checks = new ArrayList<>();
        PartyDashboardData.Board board = data.board();
        // The board
        if (board.spaces() == 0) {
            checks.add(new Check(Check.ERROR, Text.translatable(KEY + "check.board.none"), Text.translatable(KEY + "check.board.none.hint"), null));
        } else if (board.starts() == 0) {
            checks.add(new Check(Check.ERROR, Text.translatable(KEY + "check.board.no_start", board.spaces()), Text.translatable(KEY + "check.board.no_start.hint"), null));
        } else {
            long problems = board.errors() + board.warnings();
            MutableTextList hint = new MutableTextList();
            for (PartyDashboardData.Issue issue : board.issues())
                hint.add(Text.literal("• ").append(Text.translatable("message.steveparty.board.summary." + issue.key(), issue.count())));
            hint.add(Text.translatable(KEY + "check.board.hint"));
            checks.add(new Check(problems > 0 ? Check.WARN : Check.OK,
                    problems > 0 ? Text.translatable(KEY + "check.board.warnings", board.spaces(), board.starts(), problems)
                            : Text.translatable(KEY + "check.board.ok", board.spaces(), board.starts()),
                    hint.join(), null));
        }
        // The tokens on the start tiles
        int tokens = board.startTokens().size();
        int free = Math.max(0, board.starts() - tokens);
        checks.add(tokens == 0
                ? new Check(Check.ERROR, Text.translatable(KEY + "check.tokens.none"), Text.translatable(KEY + "check.tokens.hint"), Page.PLAYERS)
                : new Check(Check.OK, free > 0 ? Text.translatable(KEY + "check.tokens.free", tokens, free) : Text.translatable(KEY + "check.tokens.ok", tokens),
                Text.translatable(KEY + "check.tokens.hint"), Page.PLAYERS));
        // The mini-games
        long notPlayable = data.pages().stream().filter(p -> p.playable() == 0).count();
        if (!data.hasCatalogue()) {
            checks.add(new Check(Check.WARN, Text.translatable(KEY + "check.catalogue.none"), Text.translatable(KEY + "check.catalogue.none.hint"), Page.PROGRAM));
        } else if (data.pages().isEmpty()) {
            checks.add(new Check(Check.WARN, Text.translatable(KEY + "check.catalogue.empty"), Text.translatable(KEY + "check.catalogue.empty.hint"), Page.PROGRAM));
        } else if (notPlayable > 0) {
            checks.add(new Check(Check.WARN, Text.translatable(KEY + "check.catalogue.no_pipes", data.pages().size(), notPlayable), Text.translatable(KEY + "check.catalogue.no_pipes.hint"), Page.PROGRAM));
        } else {
            checks.add(new Check(Check.OK, Text.translatable(KEY + "check.catalogue.ok", data.pages().size()), Text.translatable(KEY + "check.catalogue.ok.hint"), Page.PROGRAM));
        }
        // The bank of the gains (never blocks the launch: a party can be played without gains), and the currencies
        PartyBank.Status bank = data.bank();
        Text bankHint = Text.empty().append(Text.translatable(KEY + "check.bank.hint")).append("\n").append(Text.translatable(KEY + "check.currencies.hint"));
        checks.add(switch (bank.state()) {
            case NONE -> new Check(Check.WARN, Text.translatable(KEY + "check.bank.none"), bankHint, Page.GAINS);
            case MISSING -> new Check(Check.WARN, Text.translatable(KEY + "check.bank.missing"), bankHint, Page.GAINS);
            case SHORT -> new Check(Check.WARN, Text.translatable(KEY + "check.bank.short", bank.coins(), currency(PartyCurrency.COIN).getName(),
                    bank.stars(), currency(PartyCurrency.STAR).getName()), bankHint, Page.GAINS);
            case OK -> new Check(Check.INFO, Text.translatable(KEY + "check.bank.ok", bank.coins(), currency(PartyCurrency.COIN).getName(),
                    bank.stars(), currency(PartyCurrency.STAR).getName()), bankHint, Page.GAINS);
        });
        checks.add(new Check(Check.INFO, Text.translatable(KEY + "check.rounds", data.roundsSetting()), Text.translatable(KEY + "check.rounds.hint"), Page.SETTINGS));
        return checks;
    }

    /** The bank's tooltip: what it is, what it holds, why it can't pay. */
    private List<Text> bankTooltip(PartyBank.Status bank) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable(KEY + "gains.bank.title").formatted(Formatting.GOLD));
        switch (bank.state()) {
            case NONE -> lines.add(Text.translatable(KEY + "gains.bank.none.hint").formatted(Formatting.GRAY));
            case MISSING -> lines.add(Text.translatable(KEY + "gains.bank.missing.hint").formatted(Formatting.RED));
            case OK, SHORT -> {
                lines.add(Text.translatable(KEY + "gains.bank.holds", bank.coins(), currency(PartyCurrency.COIN).getName(),
                        bank.stars(), currency(PartyCurrency.STAR).getName()).formatted(Formatting.GRAY));
                if (bank.state() == PartyBank.State.SHORT) lines.add(Text.translatable(KEY + "gains.bank.short").formatted(Formatting.GOLD));
                lines.add(Text.translatable(KEY + "gains.bank.hint").formatted(Formatting.DARK_GRAY));
            }
        }
        return lines;
    }

    /** Joins texts with line breaks (tooltips). */
    private static final class MutableTextList {
        private final List<Text> texts = new ArrayList<>();

        void add(Text text) {
            texts.add(text);
        }

        Text join() {
            var joined = Text.empty();
            for (int i = 0; i < texts.size(); i++) {
                if (i > 0) joined.append("\n");
                joined.append(texts.get(i));
            }
            return joined;
        }
    }

    private void drawState(DrawContext context, PartyDashboardData data, int mx, int my) {
        switch (data.phase()) {
            case SETUP -> {
                header(context, Text.translatable(KEY + "state.setup"), false);
                List<Check> checks = checklist(data);
                for (int i = 0; i < checks.size(); i++) {
                    Check check = checks.get(i);
                    int ly = CHECK_Y + i * CHECK_ROW;
                    boolean hovered = check.target() != null && in(mx, my, CX, ly, CW, ROW_H);
                    if (hovered) context.fill(CX - 2, ly, CX + CW + 2, ly + ROW_H, 0x22FFFFFF);
                    Ramp disc = switch (check.state()) {
                        case Check.OK -> OK;
                        case Check.ERROR -> ERROR;
                        case Check.WARN -> WARN;
                        default -> NEUTRAL;
                    };
                    ConsolePaint.disc(context, CX, ly + 2, 8, disc);
                    int color = switch (check.state()) {
                        case Check.OK -> INK;
                        case Check.ERROR -> INK_RED;
                        case Check.WARN -> INK_WARN;
                        default -> INK_SOFT;
                    };
                    drawFitted(context, check.text(), CX + 12, ly + 2, CW - 12, color, hovered);
                }
            }
            case RUNNING -> {
                headerPill(context, data.round() == 0 ? Text.translatable("hud.steveparty.party.round.start")
                        : Text.translatable(KEY + "state.round", data.round(), data.rounds()));
                // The steps of the party: the current one stays near the left until the timeline is scrolled
                PartyDashboardData.Timeline steps = data.steps();
                int current = steps.offset() + steps.current();
                if (current != stepsFollowed) {
                    stepsFollowed = current;
                    stepsScrolled = false;
                }
                if (!stepsScrolled) stepsScroll = Math.max(0, steps.current() - TIMELINE_PAST);
                stepsScroll = drawTimeline(context, data, steps, STEPS_LEFT, STEPS_LABELS_Y, STEPS_WIDTH, STEPS_CHIP, stepsScroll, data.round(), mx, my);
                // What is happening now, then the step and the leader
                drawFitted(context, data.action(), CX, LINES_Y, CW, WHITE, in(mx, my, CX, LINES_Y - 1, CW, 10));
                if (!data.actionDetail().getString().isEmpty())
                    drawFitted(context, data.actionDetail(), CX, LINES_Y + LINE, CW, INK_SOFT, in(mx, my, CX, LINES_Y + LINE - 1, CW, 10));
                net.minecraft.text.MutableText step = Text.translatable(KEY + "state.step", data.stepIndex() + 1, data.stepCount());
                PartyLiveData.Standing leader = leader(data);
                if (leader != null) step.append(" · ").append(Text.translatable(KEY + "state.leader", leader.tokenName()));
                drawFitted(context, step, CX, LINES_Y + 2 * LINE, CW, INK_SOFT, in(mx, my, CX, LINES_Y + 2 * LINE - 1, CW, 10));
            }
            case ENDED -> {
                List<PartyLiveData.Standing> players = data.players();
                int[] ranks = PartyLiveData.ranks(players);
                List<Integer> order = byRank(ranks);
                // The winner in the title, the standings under it
                header(context, players.isEmpty() ? Text.translatable(KEY + "state.ended")
                        : Text.translatable(KEY + "state.winner", players.get(order.getFirst()).tokenName()), false);
                for (int i = 0; i < Math.min(order.size(), PLAYER_ROWS); i++) {
                    int index = order.get(i);
                    playerRow(context, data, players.get(index), i, ranks[index], ROWS_Y + i * ROW, CW, false, true, mx, my);
                }
            }
        }
    }

    private static @Nullable PartyLiveData.Standing leader(PartyDashboardData data) {
        List<PartyLiveData.Standing> players = data.players();
        if (players.isEmpty() || players.stream().allMatch(p -> p.stars() == 0 && p.coins() == 0)) return null;
        int[] ranks = PartyLiveData.ranks(players);
        return players.get(byRank(ranks).getFirst());
    }

    /** Indexes sorted by rank, the turn order between ties. */
    private static List<Integer> byRank(int[] ranks) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < ranks.length; i++) order.add(i);
        order.sort((a, b) -> Integer.compare(ranks[a], ranks[b]));
        return order;
    }

    private static Ramp rankRamp(int rank) {
        return switch (rank) {
            case 1 -> RANK_GOLD;
            case 2 -> RANK_SILVER;
            case 3 -> RANK_BRONZE;
            default -> NEUTRAL;
        };
    }

    /** A 12 px disc in the rank's colour, its number dark. */
    private void rankDisc(DrawContext context, int rank, int dx, int dy) {
        Ramp ramp = rankRamp(rank);
        ConsolePaint.disc(context, dx, dy, ROW_H, ramp);
        Text number = Text.literal(Integer.toString(Math.min(rank, 9)));
        dark(context, number.asOrderedText(), dx + (ROW_H - textRenderer.getWidth(number) + 1) / 2, dy + 2, ramp.outline(), ramp.hi());
    }

    private Text owner(PartyLiveData.Standing player) {
        if (player.owner().isEmpty()) return Text.translatable("hud.steveparty.party.anyone");
        if (!player.online()) return Text.translatable("hud.steveparty.party.offline", player.ownerName());
        return Text.literal(player.ownerName());
    }

    // ------------------------------------------------------------------ Players page

    private void drawPlayers(DrawContext context, PartyDashboardData data, int mx, int my) {
        List<PartyLiveData.Standing> players = data.players();
        header(context, Text.translatable(data.phase() == Phase.SETUP ? KEY + "players.setup" : KEY + "players.party", players.size()), false);
        if (players.isEmpty()) {
            context.drawText(textRenderer, fit(Text.translatable(KEY + "check.tokens.none"), CW), CX, ROWS_Y + 4, INK_RED, true);
            return;
        }
        boolean ranked = data.phase() != Phase.SETUP && players.stream().anyMatch(p -> p.stars() != 0 || p.coins() != 0);
        int[] ranks = PartyLiveData.ranks(players);
        int maxScroll = Math.max(0, players.size() - PLAYER_ROWS);
        playersScroll = Math.clamp(playersScroll, 0, maxScroll);
        int rowWidth = CW - (maxScroll > 0 ? 6 : 0);
        for (int i = playersScroll; i < Math.min(players.size(), playersScroll + PLAYER_ROWS); i++) {
            playerRow(context, data, players.get(i), i, ranks[i], ROWS_Y + (i - playersScroll) * ROW, rowWidth, i == data.currentPlayer(), ranked, mx, my);
        }
        if (maxScroll > 0)
            scrollbar(context, CX + CW - 3, ROWS_Y, PLAYER_ROWS * ROW - 2, playersScroll, maxScroll, PLAYER_ROWS, players.size());
    }

    /** A player's row: its turn, its rank, its head, its token and player, its stars and coins. */
    private void playerRow(DrawContext context, PartyDashboardData data, PartyLiveData.Standing player, int index, int rank, int ry, int rowWidth,
                           boolean current, boolean ranked, int mx, int my) {
        boolean hovered = in(mx, my, CX, ry, rowWidth, ROW - 2);
        ConsolePaint.inset(context, CX, ry, rowWidth - 1, 15, current ? 0xFF3D3460 : SLOT_BODY, current ? 0xFFFFC52E : SLOT_EDGE, SLOT_LOW);
        light(context, Text.literal(Integer.toString(index + 1)), CX + 4, ry + 4, INK_SOFT);
        if (ranked) rankDisc(context, rank, CX + 14, ry + 2);
        ConsolePaint.box(context, CX + 30, ry + 3, 10, 10, HEAD_FRAME, 1, 1);
        head(context, player, CX + 31, ry + 4);
        int countsX = CX + rowWidth - 56;
        int namesRoom = countsX - (CX + 44) - 4, tokenRoom = Math.min(36, namesRoom / 2), ownerRoom = namesRoom - tokenRoom - 4;
        drawFitted(context, Text.literal(player.tokenName()), CX + 44, ry + 4, tokenRoom, WHITE, hovered);
        int ownerColor = player.owner().isEmpty() ? INK_SOFT : player.online() ? INK_BLUE : INK_WARN;
        context.drawText(textRenderer, fit(owner(player), ownerRoom), CX + 44 + tokenRoom + 4, ry + 4, ownerColor, true);
        drawCounts(context, player.stars(), player.coins(), countsX, ry + 4);
    }

    /** A token's head, 8 x 8: its player's skin's face, else its colour. */
    private static void head(DrawContext context, PartyLiveData.Standing player, int hx, int hy) {
        if (player.owner().isPresent()) {
            Identifier skin = fr.lordfinn.steveparty.client.utils.SkinUtils.getPlayerSkin(player.owner().get());
            context.drawTexture(RenderLayer::getGuiTextured, skin, hx, hy, 8, 8, 8, 8, 8, 8, 64, 64);
            context.drawTexture(RenderLayer::getGuiTextured, skin, hx, hy, 40, 8, 8, 8, 8, 8, 64, 64);
        } else {
            context.fill(hx, hy, hx + 8, hy + 8, player.color() < 0 ? 0xFF8B8B8B : 0xFF000000 | player.color());
        }
    }

    // ------------------------------------------------------------------ Program page

    /** The real cards of the program, in reading order. */
    private List<ItemStack> cards() {
        List<ItemStack> cards = new ArrayList<>();
        for (int i = 0; i < PartyControllerEntity.PROGRAM_SLOTS; i++) {
            ItemStack stack = handler.getSlot(PROGRAM_FIRST_SLOT + i).getStack();
            if (!stack.isEmpty()) cards.add(stack);
        }
        return cards;
    }

    private static Item cardItem(PartyCardItem.CardType type) {
        return switch (type) {
            case TURNS -> ModItems.PARTY_CARD_TURNS;
            case MINIGAME -> ModItems.PARTY_CARD_MINIGAME;
            case EVENT -> ModItems.PARTY_CARD_EVENT;
            case REPEAT -> ModItems.PARTY_CARD_REPEAT;
            case SEQUENCE_START -> ModItems.PARTY_CARD_SEQUENCE_START;
        };
    }

    /**
     * The ghost cards of the program: with no real card, the default party as cards (never items: only drawn), in
     * the first slots; empty as soon as a real card is placed (the program is then the real cards only).
     */
    private List<ItemStack> ghosts(PartyDashboardData data) {
        if (!cards().isEmpty()) return List.of();
        List<ItemStack> ghosts = new ArrayList<>();
        for (BasicGameGeneratorStep.ExpandedCard card : BasicGameGeneratorStep.defaultProgram(data.roundsSetting()))
            ghosts.add(new ItemStack(cardItem(card.type()), card.count()));
        return ghosts;
    }

    /**
     * The catalogue slot and what it brings (its mini-games are in its tooltip), what the party will be made of, the
     * card slots (the default party as ghost cards while no card is placed), the timeline of what they will play.
     */
    private void drawProgram(DrawContext context, PartyDashboardData data, int mx, int my) {
        int tx = CX + 22, room = INFO_X - 4 - tx;
        if (!data.canEdit()) {
            Text locked = Text.translatable(KEY + "read_only");
            int lw = textRenderer.getWidth(locked) - 1;
            context.drawText(textRenderer, locked, INFO_X - 4 - lw, CY + 5, INK_RED, true);
            room -= lw + 6;
        }
        // Next to the catalogue: its mini-games, or what is wrong with them
        Badge problem = catalogueBadge(data);
        long played = data.pages().stream().filter(p -> p.played() > 0).count();
        Text catalogue = problem != null ? problem.reason() : Text.translatable(KEY + "program.catalogue.summary", data.pages().size(), played);
        drawFitted(context, catalogue, tx, CY + 5, room, problem == null ? WHITE : problem.error() ? INK_RED : INK_WARN, in(mx, my, tx, CY + 4, room, 10));
        // What the party will be made of
        List<ItemStack> cards = cards();
        BasicGameGeneratorStep.Summary made = BasicGameGeneratorStep.summary(cards, data.roundsSetting());
        Text summary = data.phase() == Phase.RUNNING ? Text.translatable(KEY + "program.running")
                : Text.translatable(cards.isEmpty() ? KEY + "program.default" : KEY + "program.summary", made.turns(), made.miniGames(), made.events());
        drawFitted(context, summary, CX, SUMMARY_Y, CW, INK_SOFT, in(mx, my, CX, SUMMARY_Y - 1, CW, 10));
        // The default party, as ghost cards
        List<ItemStack> ghosts = ghosts(data);
        for (int i = 0; i < ghosts.size() && i < PartyControllerEntity.PROGRAM_SLOTS; i++) {
            ghost(context, ghosts.get(i), PROGRAM_X + (i % PROGRAM_COLUMNS) * 18, PROGRAM_Y + (i / PROGRAM_COLUMNS) * 18, true);
        }
        // Under the cards, on their columns: what the program will play, its loops unrolled
        programScroll = drawTimeline(context, data, data.program(), PROGRAM_LEFT, PROGRAM_LABELS_Y, PROGRAM_WIDTH, PROGRAM_CHIP, programScroll, 1, mx, my);
    }

    // ------------------------------------------------------------------ timelines

    private static final Identifier ICON_DICE = fr.lordfinn.steveparty.Steveparty.id("party_hud/dice"),
            ICON_GEAR = fr.lordfinn.steveparty.Steveparty.id("party_hud/gear"), ICON_STEPS = fr.lordfinn.steveparty.Steveparty.id("party_hud/steps"),
            ICON_CLOCK = fr.lordfinn.steveparty.Steveparty.id("party_hud/clock"),
            ICON_CROWN = fr.lordfinn.steveparty.Steveparty.id("party_hud/crown"), ICON_MARKER = fr.lordfinn.steveparty.Steveparty.id("party_hud/marker");

    private static @Nullable Identifier icon(PartyDashboardData.StepKind kind) {
        return switch (kind) {
            case START_ROLLS -> ICON_DICE;
            case PREPARING -> ICON_GEAR;
            case TURN, TURNS -> ICON_STEPS;
            case MINI_GAME -> null;
            case EVENT -> ICON_CLOCK;
            case END -> ICON_CROWN;
            case OTHER -> ICON_MARKER;
        };
    }

    /** « Round 2 · Mini-game », « Round 3 · Steve's turn »: what a step of a timeline is, in one line. */
    private Text stepText(PartyDashboardData data, PartyDashboardData.TimelineStep step, boolean current) {
        PartyLiveData.Standing player = step.player() >= 0 && step.player() < data.players().size() ? data.players().get(step.player()) : null;
        Text what = switch (step.kind()) {
            case START_ROLLS -> Text.translatable(KEY + "action.start_rolls");
            case PREPARING -> Text.translatable("hud.steveparty.party.preparing");
            case TURN -> player == null ? Text.translatable(KEY + "timeline.turn.unknown") : Text.translatable(KEY + "timeline.turn", player.tokenName());
            case TURNS -> Text.translatable(KEY + "timeline.turns");
            case MINI_GAME -> Text.translatable(KEY + "timeline.mini_game");
            case EVENT -> step.value() > 0 ? Text.translatable(KEY + "timeline.event.channel", step.value()) : Text.translatable(KEY + "timeline.event");
            case END -> Text.translatable(KEY + "timeline.end");
            case OTHER -> Text.translatable(KEY + "timeline.other");
        };
        Text line = step.round() > 0 ? Text.translatable(KEY + "timeline.in_round", step.round(), what) : what;
        return current ? Text.translatable(KEY + "timeline.current", line) : line;
    }

    /**
     * A timeline: a row of chips, one per step (the head of the player whose turn it is on his colour, the mini-game's
     * gamepad, else what the step is), the current one framed in gold, the past ones dimmed; above the first chip of
     * each round its name (« Round 4 », « R4 » when short of room), the round {@code highlight} in gold. Shown from
     * {@code scroll}; « +N » after the last step sent when the party has more.
     *
     * @return the scroll, kept within the timeline
     */
    private int drawTimeline(DrawContext context, PartyDashboardData data, PartyDashboardData.Timeline timeline, int left, int top, int width,
                             int chip, int scroll, int highlight, int mx, int my) {
        List<PartyDashboardData.TimelineStep> steps = timeline.steps();
        int pitch = chip + TIMELINE_GAP, shown = (width + TIMELINE_GAP) / pitch;
        int total = steps.size() + (timeline.more() > 0 ? 2 : 0);
        scroll = Math.clamp(scroll, 0, Math.max(0, total - shown));
        int chipY = top + TIMELINE_LABELS;
        // More on the left
        if (scroll > 0 || timeline.offset() > 0) light(context, Text.literal("‹"), left - 5, chipY + (chip - 8) / 2, INK_SOFT);
        // More on the right: steps (or the « +N » after them) past the last chip shown
        if (scroll + shown < steps.size() + (timeline.more() > 0 ? 1 : 0))
            light(context, Text.literal("›"), left + shown * pitch - TIMELINE_GAP + 2, chipY + (chip - 8) / 2, INK_SOFT);
        for (int slot = 0; slot < shown; slot++) {
            int index = scroll + slot, cx = left + slot * pitch;
            if (index >= steps.size()) {
                // « +N »: the steps the party has after those sent
                if (index == steps.size() && timeline.more() > 0)
                    light(context, Text.literal("+" + timeline.more()), cx + 2, chipY + (chip - 8) / 2, INK_SOFT);
                break;
            }
            PartyDashboardData.TimelineStep step = steps.get(index);
            boolean current = index == timeline.current(), past = timeline.current() >= 0 && index < timeline.current();
            boolean hovered = in(mx, my, cx, chipY, chip, chip);
            // The round it begins (and, on the first chip shown, the round it is in)
            boolean begins = step.round() > 0 && (index == 0 || steps.get(index - 1).round() != step.round());
            if (step.round() > 0 && (begins || slot == 0)) {
                int room = pitch - TIMELINE_GAP;
                for (int next = index + 1; next < steps.size() && steps.get(next).round() == step.round() && next - scroll < shown; next++) room += pitch;
                Text label = Text.translatable(KEY + "timeline.round", step.round());
                if (textRenderer.getWidth(label) - 1 > room - 2) label = Text.translatable(KEY + "timeline.round.short", step.round());
                light(context, label, cx + 1, top, past ? INK_DIM : step.round() == highlight ? INK_GOLD : INK_SOFT);
            }
            PartyLiveData.Standing player = step.player() >= 0 && step.player() < data.players().size() ? data.players().get(step.player()) : null;
            Ramp ramp = step.kind() == PartyDashboardData.StepKind.MINI_GAME ? CHIP_GAME
                    : step.kind() == PartyDashboardData.StepKind.EVENT ? CHIP_EVENT
                    : player != null ? chipRamp(player, step.player()) : NEUTRAL;
            ConsolePaint.box(context, cx, chipY, chip, chip, ramp, 1, 1);
            if (player != null && step.kind() == PartyDashboardData.StepKind.TURN) {
                head(context, player, cx + (chip - 8) / 2, chipY + (chip - 8) / 2);
            } else if (step.kind() == PartyDashboardData.StepKind.MINI_GAME) {
                ConsolePaint.gamepad(context, cx + (chip - 10) / 2, chipY + (chip - 8) / 2);
            } else {
                Identifier icon = icon(step.kind());
                if (icon != null) context.drawGuiTexture(RenderLayer::getGuiTextured, icon, cx + (chip - 9) / 2, chipY + (chip - 9) / 2, 9, 9);
            }
            if (past) context.fill(cx, chipY, cx + chip, chipY + chip, 0x8C000000 | (SCREEN & 0xFFFFFF));
            if (hovered && !past) context.fill(cx + 1, chipY + 1, cx + chip - 1, chipY + chip - 1, 0x30FFFFFF);
            if (current) halo(context, cx, chipY, chip);
            if (hovered) hoveredStep = stepText(data, step, current);
        }
        return scroll;
    }

    /** The colour of a player's chip: his token's (the HUDs' ramps). */
    private static Ramp chipRamp(PartyLiveData.Standing player, int index) {
        HudPaint.Ramp ramp = HudPaint.playerRamp(player.color(), index);
        return new Ramp(ramp.outline(), ramp.hi(), ramp.body(), ramp.shadow());
    }

    /** The current step's frame: one pale gold pixel all round the chip (its cut corners filled). */
    private static void halo(DrawContext context, int hx, int hy, int size) {
        int c = 0xFFFFF87E;
        context.fill(hx, hy - 1, hx + size, hy, c);
        context.fill(hx, hy + size, hx + size, hy + size + 1, c);
        context.fill(hx - 1, hy, hx, hy + size, c);
        context.fill(hx + size, hy, hx + size + 1, hy + size, c);
        PartyGui.pixel(context, hx, hy, c);
        PartyGui.pixel(context, hx + size - 1, hy, c);
        PartyGui.pixel(context, hx, hy + size - 1, c);
        PartyGui.pixel(context, hx + size - 1, hy + size - 1, c);
    }

    /** @return true if the mouse is over the timeline of the page shown. */
    private boolean overTimeline(double mouseX, double mouseY) {
        PartyDashboardData data = data();
        if (data == null) return false;
        int mx = (int) mouseX - x, my = (int) mouseY - y;
        if (page() == Page.STATE && data.phase() == Phase.RUNNING)
            return in(mx, my, STEPS_LEFT - 6, STEPS_LABELS_Y, STEPS_WIDTH + 12, TIMELINE_LABELS + STEPS_CHIP);
        return page() == Page.PROGRAM && in(mx, my, PROGRAM_LEFT - 6, PROGRAM_LABELS_Y, PROGRAM_WIDTH + 12, TIMELINE_LABELS + PROGRAM_CHIP);
    }

    private void scrollTimeline(int steps) {
        if (page() == Page.STATE) {
            stepsScroll += steps;
            stepsScrolled = true;
        } else {
            programScroll += steps;
        }
    }

    // ------------------------------------------------------------------ Gains page

    private static final int COLOR_SHORT = 0xFFFFB54A;

    private void drawGains(DrawContext context, PartyDashboardData data) {
        // The bank's line: its cartridge slot, its name (what it holds in its tooltip)
        PartyBank.Status bank = data.bank();
        int tx = CX + 22, room = COIN_X - 1 - 4 - tx;
        switch (bank.state()) {
            case NONE, MISSING -> context.drawText(textRenderer, fit(Text.translatable(KEY + "gains.bank." + (bank.state() == PartyBank.State.NONE ? "none" : "missing")), room),
                    tx, CY + 5, INK_RED, true);
            case OK, SHORT -> context.drawText(textRenderer, fit(Text.translatable(KEY + "gains.bank"), room), tx, CY + 5,
                    bank.state() == PartyBank.State.SHORT ? COLOR_SHORT : WHITE, true);
        }
        for (int row = 0; row < MiniGameGains.ROWS; row++) {
            int top = GAINS_Y + row * GAINS_ROW;
            if (row == MiniGameGains.PARTICIPANTS) {
                context.drawText(textRenderer, fit(Text.translatable(KEY + "gains.participants"), GAINS_COIN_X - CX - 4), CX, top + 4, WHITE, true);
            } else {
                rankDisc(context, row + 1, CX, top + 2);
                context.drawText(textRenderer, fit(Text.translatable(KEY + "gains.place"), GAINS_COIN_X - CX - 20), CX + 16, top + 4, INK_SOFT, true);
            }
            for (PartyCurrency currency : new PartyCurrency[]{PartyCurrency.COIN, PartyCurrency.STAR}) {
                int left = (currency == PartyCurrency.COIN ? GAINS_COIN_X : GAINS_STAR_X) + STEP + 2;
                int amount = data.gains().amount(currency, row);
                ConsolePaint.inset(context, left, top, GAINS_FIELD - 1, 15, SLOT_BODY, SLOT_EDGE, SLOT_LOW);
                String value = Integer.toString(amount);
                light(context, Text.literal(value), left + (GAINS_FIELD - textRenderer.getWidth(value) + 1) / 2, top + 4, amount == 0 ? INK_SOFT : WHITE);
            }
        }
    }

    // ------------------------------------------------------------------ Settings page

    private void drawSettings(DrawContext context, PartyDashboardData data) {
        header(context, Text.translatable(KEY + "tab.settings"), !data.canEdit());
        int room = CW - 72;
        context.drawText(textRenderer, fit(Text.translatable(KEY + "settings.rounds"), room), CX, SETTINGS_Y + 5, WHITE, true);
        String value = Integer.toString(data.roundsSetting());
        int fieldX = CX + CW - 68 + 18;
        ConsolePaint.inset(context, fieldX, SETTINGS_Y + 1, 31, 15, SLOT_BODY, SLOT_EDGE, SLOT_LOW);
        light(context, Text.literal(value), fieldX + (32 - textRenderer.getWidth(value) + 1) / 2, SETTINGS_Y + 5, WHITE);
        context.drawText(textRenderer, fit(Text.translatable(KEY + "settings.practice"), room), CX, SETTINGS_Y + SETTINGS_ROW + 5, WHITE, true);
    }

    /** The practice round: its state, then a switch (green and to the right when on). */
    private final class PracticeSwitch extends PressableWidget {
        private final boolean on;

        PracticeSwitch(int x, int y, int width, int height, Text message, boolean on) {
            super(x, y, width, height, message);
            this.on = on;
        }

        @Override
        public void onPress() {
            click(BUTTON_PRACTICE);
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int sx = getX() + width - 24, sy = getY() + 3;
            ConsolePaint.pill(context, sx, sy, 24, 12, on ? SWITCH_ON : SWITCH_OFF, true);
            ConsolePaint.disc(context, sx + (on ? 13 : 1), sy + 1, 10, KNOB);
            if (active && (isHovered() || isFocused())) context.drawBorder(sx - 1, sy - 1, 26, 14, WHITE);
            TextRenderer font = MinecraftClient.getInstance().textRenderer;
            context.drawText(font, getMessage(), getX(), getY() + 5, !active ? INK_DIM : on ? INK_GREEN : INK_SOFT, true);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }

    // ------------------------------------------------------------------ tooltips and input

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    private void tooltip(DrawContext context, List<Text> lines, int mouseX, int mouseY) {
        // wrapLines also breaks at the line breaks, keeping the styles
        List<OrderedText> wrapped = new ArrayList<>();
        for (Text line : lines) wrapped.addAll(textRenderer.wrapLines(line, TOOLTIP_WIDTH));
        context.drawOrderedTooltip(textRenderer, wrapped, mouseX, mouseY);
    }

    @Override
    protected void drawMouseoverTooltip(DrawContext context, int mouseX, int mouseY) {
        PartyDashboardData data = data();
        int mx = mouseX - x, my = mouseY - y;
        boolean emptyHand = handler.getCursorStack().isEmpty();
        // A step of a timeline
        Text step = hoveredStep;
        hoveredStep = null;
        if (step != null && emptyHand && overTimeline(mouseX, mouseY)) {
            context.drawTooltip(textRenderer, step, mouseX, mouseY);
            return;
        }
        // The « i » of the page: what it is for and how it is used
        if (data != null && in(mx, my, INFO_X, infoY(), ROW_H, ROW_H)) {
            tooltip(context, List.of(Text.translatable(KEY + "tab." + key(page())).formatted(Formatting.GOLD),
                    Text.translatable(KEY + "info." + key(page())).formatted(Formatting.GRAY)), mouseX, mouseY);
            return;
        }
        if (focusedSlot != null && isGhostSlot(focusedSlot.id) && emptyHand && data != null) {
            PartyCurrency currency = currencyOf(focusedSlot.id);
            String name = currency == PartyCurrency.STAR ? "star" : "coin";
            List<Text> lines = new ArrayList<>();
            lines.add(Text.translatable(KEY + "gains." + name, focusedSlot.getStack().getName()).formatted(Formatting.GOLD));
            lines.add(Text.translatable(KEY + "gains." + name + ".hint").formatted(Formatting.GRAY));
            if (!data.canEdit()) lines.add(Text.translatable(KEY + "locked").formatted(Formatting.RED));
            tooltip(context, lines, mouseX, mouseY);
            return;
        }
        // The bank: its line, its empty slot
        if (data != null && page() == Page.GAINS && emptyHand && (in(mx, my, CX + 20, CY, COIN_X - 1 - CX - 24, 18)
                || (focusedSlot != null && focusedSlot.id == SLOT_BANK && !focusedSlot.hasStack()))) {
            tooltip(context, bankTooltip(data.bank()), mouseX, mouseY);
            return;
        }
        // The catalogue: what it is, its mini-games
        if (data != null && page() == Page.PROGRAM && emptyHand
                && ((focusedSlot != null && focusedSlot.id == SLOT_CATALOGUE) || in(mx, my, CX + 20, CY, INFO_X - 4 - CX - 20, 18))) {
            ItemStack catalogue = handler.getSlot(SLOT_CATALOGUE).getStack();
            if (catalogue.isEmpty()) {
                tooltip(context, List.of(Text.translatable(KEY + "program.catalogue").formatted(Formatting.GOLD),
                        Text.translatable(KEY + "program.catalogue.hint").formatted(Formatting.GRAY)), mouseX, mouseY);
                return;
            }
            tooltip(context, catalogueTooltip(data, catalogue), mouseX, mouseY);
            return;
        }
        if (focusedSlot != null && isProgramSlot(focusedSlot.id) && !focusedSlot.hasStack() && emptyHand && data != null) {
            List<ItemStack> ghosts = ghosts(data);
            int index = focusedSlot.id - PROGRAM_FIRST_SLOT;
            List<Text> lines = new ArrayList<>();
            if (index < ghosts.size()) {
                ItemStack ghost = ghosts.get(index);
                Text name = ghost.getCount() > 1 ? Text.translatable(KEY + "program.ghost.count", ghost.getName(), ghost.getCount()) : ghost.getName();
                lines.add(Text.translatable(KEY + "program.ghost", name).formatted(Formatting.GOLD));
                lines.add(Text.translatable(KEY + "program.ghost.hint").formatted(Formatting.GRAY));
            } else {
                lines.add(Text.translatable(KEY + "program.slot").formatted(Formatting.GRAY));
            }
            tooltip(context, lines, mouseX, mouseY);
            return;
        }
        if (data != null && page() == Page.STATE && data.phase() == Phase.SETUP) {
            int index = checkAt(mouseX, mouseY, data);
            if (index >= 0) {
                Check check = checklist(data).get(index);
                List<Text> lines = new ArrayList<>();
                lines.add(check.hint().copy().formatted(Formatting.GRAY));
                if (check.target() != null)
                    lines.add(Text.translatable(KEY + "check.go", Text.translatable(KEY + "tab." + key(check.target()))).formatted(Formatting.YELLOW));
                tooltip(context, lines, mouseX, mouseY);
                return;
            }
        }
        // The label of a setting: what it does
        if (data != null && page() == Page.SETTINGS && emptyHand && mx >= CX && mx < CX + CW - 72) {
            int row = Math.floorDiv(my - SETTINGS_Y, SETTINGS_ROW);
            if (my >= SETTINGS_Y && row >= 0 && row < 2 && my < SETTINGS_Y + row * SETTINGS_ROW + 18) {
                String key = row == 0 ? "settings.rounds" : "settings.practice";
                tooltip(context, List.of(Text.translatable(KEY + key + ".tooltip").formatted(Formatting.GRAY)), mouseX, mouseY);
                return;
            }
        }
        super.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    /** The catalogue's tooltip: its name, its count, then a line per mini-game (played, without its pipes, current). */
    private List<Text> catalogueTooltip(PartyDashboardData data, ItemStack catalogue) {
        List<Text> lines = new ArrayList<>();
        lines.add(catalogue.getName().copy().formatted(Formatting.GOLD));
        long played = data.pages().stream().filter(p -> p.played() > 0).count();
        lines.add(Text.translatable(KEY + "program.catalogue.summary", data.pages().size(), played).formatted(Formatting.GRAY));
        for (PartyDashboardData.Page page : data.pages()) {
            net.minecraft.text.MutableText line = Text.literal("• ").append(page.page().getName());
            if (page.slot() == data.currentPage()) line.append(" · ").append(Text.translatable(KEY + "mini_games.page.current"));
            else if (page.playable() == 0) line.append(" · ").append(Text.translatable(page.pipes() == 0 ? KEY + "mini_games.page.no_pipe" : KEY + "mini_games.page.not_playable"));
            else if (page.played() > 0) line.append(" · ").append(Text.translatable(KEY + "mini_games.page.played", page.played()));
            lines.add(line.formatted(page.slot() == data.currentPage() ? Formatting.GOLD : page.playable() == 0 ? Formatting.RED
                    : page.played() > 0 ? Formatting.GREEN : Formatting.WHITE));
        }
        if (data.catalogueLocked()) lines.add(Text.translatable(KEY + "program.catalogue.locked").formatted(Formatting.RED));
        return lines;
    }

    private int checkAt(double mouseX, double mouseY, PartyDashboardData data) {
        double mx = mouseX - x, my = mouseY - y;
        if (mx < CX || mx >= CX + CW) return -1;
        int index = (int) Math.floor((my - CHECK_Y) / CHECK_ROW);
        int count = checklist(data).size();
        return my >= CHECK_Y && index >= 0 && index < count && my < CHECK_Y + index * CHECK_ROW + ROW_H ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PartyDashboardData data = data();
        if (data != null && button == 0 && page() == Page.STATE && data.phase() == Phase.SETUP) {
            int index = checkAt(mouseX, mouseY, data);
            if (index >= 0) {
                Check check = checklist(data).get(index);
                if (check.target() != null) {
                    playClick();
                    showPage(check.target());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClick() {
        if (client != null)
            client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    protected void onMouseClick(@Nullable Slot slot, int slotId, int button, SlotActionType actionType) {
        if (slot != null && isGhostSlot(slot.id)) {
            // Checked here too, to say why at once (the server checks again)
            PartyDashboardData data = data();
            if (actionType != SlotActionType.PICKUP) return;
            if (data == null || !data.canEdit()) {
                flash(Text.translatable(KEY + "read_only"));
                return;
            }
            ItemStack cursor = handler.getCursorStack();
            PartyCurrency currency = currencyOf(slot.id);
            ItemStack other = currency(currency.other());
            if (!cursor.isEmpty() && ItemStack.areItemsAndComponentsEqual(cursor, other)) {
                flash(Text.translatable(KEY + "settings.same_item"));
                return;
            }
        }
        if (slot != null && slot.id == SLOT_CATALOGUE && slot.hasStack() && handler.isCatalogueLocked()) {
            flash(Text.translatable(KEY + "program.catalogue.locked"));
            return;
        }
        super.onMouseClick(slot, slotId, button, actionType);
    }

    private void flash(Text text) {
        flash = text;
        flashUntil = Util.getMeasuringTimeMs() + 2500;
        if (client != null)
            client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8F));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // A timeline is dragged like a strip: one chip at a time
        if (button == 0 && handler.getCursorStack().isEmpty() && overTimeline(mouseX, mouseY)) {
            timelineDrag += deltaX;
            int pitch = (page() == Page.STATE ? STEPS_CHIP : PROGRAM_CHIP) + TIMELINE_GAP;
            while (Math.abs(timelineDrag) >= pitch) {
                scrollTimeline(timelineDrag > 0 ? -1 : 1);
                timelineDrag -= Math.signum(timelineDrag) * pitch;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0 && overTimeline(mouseX, mouseY)) {
            scrollTimeline(-(int) Math.signum(verticalAmount));
            return true;
        }
        if (verticalAmount != 0 && page() == Page.PLAYERS && mouseY < y + PANEL_HEIGHT) {
            playersScroll -= (int) Math.signum(verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        if (client != null && client.player != null)
            client.player.playSound(CLOSE_TILE_GUI_SOUND_EVENT, 1.0F, 1.0F);
        super.close();
    }
}
