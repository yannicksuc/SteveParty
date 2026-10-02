package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.blocks.custom.PartyController.MiniGameGains;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.BasicGameGeneratorStep;
import fr.lordfinn.steveparty.items.custom.PartyCardItem;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Blocker;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Phase;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import fr.lordfinn.steveparty.client.gui.PartyButton;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.Page;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.*;
import static fr.lordfinn.steveparty.sounds.ModSounds.CLOSE_TILE_GUI_SOUND_EVENT;
import static fr.lordfinn.steveparty.sounds.ModSounds.OPEN_TILE_GUI_SOUND_EVENT;

/**
 * The Party Controller's dashboard: sober and compact (with its tabs and the player's inventory it fits a 427 x 240
 * screen, a large GUI scale, leaving the bottom of the screen free). No paragraph on the pages: what a page is for is in the yellow « i » of its top right
 * corner, what a slot or a control does in a short tooltip.
 * <ul>
 *     <li><b>Status</b>: before a party, a checklist of what a party needs (board, tokens on the start tiles, mini-game
 *     catalogue, currencies, rounds), each line leading to the tab where it is fixed, and « Start the party » (active
 *     only when ready, else it says why); during a party, a timeline of its steps (the current one highlighted, the
 *     next ones at a glance, a number where a round begins: wheel or drag to scroll it), what is happening now, the
 *     round, the leader; once over, the podium and « Start a new party ».</li>
 *     <li><b>Players</b>: the tokens in the turn order with their player, whether they are connected, their stars,
 *     coins and rank (before a party: the tokens on the start tiles, who will play). The list scrolls.</li>
 *     <li><b>Program</b>: the catalogue slot and the mini-games it brings (a scrolling grid: how many times each was
 *     played, whether it has somewhere to send the players), then the party's program, its card slots, and under
 *     them the timeline of what that program will play (its loops unrolled). Without any
 *     card, the slots show the default party as see-through ghost cards (the players' turn, a mini-game, repeated as
 *     many times as rounds): exactly what will be played, see {@link BasicGameGeneratorStep#defaultProgram}.</li>
 *     <li><b>Gains</b>: what the party pays at the end of each mini-game, a row per place (1st to 4th, then the
 *     participants), an amount of coins and of stars each; above each column, the item that counts as that currency
 *     (click the slot with an item to pick it, with an empty hand to go back to the default one).</li>
 *     <li><b>Settings</b>: a row per setting (the rounds, the practice round), its control on the right.</li>
 * </ul>
 * The tabs carry a red « ! » when something blocks (no board, no token, no catalogue) and a yellow one for a warning.
 * Everything shown comes from the server ({@link PartyDashboardData}), every action is checked there.
 */
public class PartyControllerScreen extends HandledScreen<PartyControllerScreenHandler> {
    private static final String KEY = "gui.steveparty.party_controller.";
    /** A tab stands on the panel's top edge, the selected one a little taller. */
    private static final int TAB_HEIGHT = TABS_HEIGHT - 2, TAB_RAISE = 2;
    private static final int PAD = 8;
    private static final int LINE = 13;
    /** Room kept free under the dashboard when the screen allows it (a recipe viewer's search field). */
    private static final int ROOM_BELOW = 24;
    /** Wide enough for one idea per line. */
    private static final int TOOLTIP_WIDTH = 236;
    private static final int COLOR_WARN = 0xFFB36200;
    private static final int COLOR_GOLD = 0xFF8A5A00;
    /** The « i » of a page: top right corner. */
    private static final int INFO_SIZE = 9, INFO_X = WIDTH - PAD - INFO_SIZE, INFO_Y = 8;
    /** Players rows. */
    private static final int ROW = 18, PLAYER_ROWS = 5, LIST_Y = 19;
    /** Program page: the grid of the catalogue's mini-games, the timeline of the program under the cards. */
    private static final int GRID_X = CATALOGUE_X + 22, GRID_COLUMNS = 10, GRID_ROWS = 1, GRID_Y = CATALOGUE_Y - 1;
    private static final int PROGRAM_TIMELINE_Y = PROGRAM_Y + 2 * 18 + 3, PROGRAM_CHIP = 15;
    /** Status page: the timeline of the running party. */
    private static final int STEPS_TIMELINE_Y = 19, STEPS_CHIP = 20;
    /** The row of round numbers above the chips of a timeline, the gap between two chips. */
    private static final int TIMELINE_LABELS = 9, TIMELINE_GAP = 2;
    /** The running party's timeline keeps this many past steps on the left of the current one. */
    private static final int TIMELINE_PAST = 2;
    /** Gains page: a row per place, a stepper of coins and one of stars. */
    private static final int GAINS_Y = 25, GAINS_ROW = 17, GAINS_STEP = 16, GAINS_FIELD = GAINS_COLUMN - 2 * GAINS_STEP - 2;
    /** Settings page: a row per setting. */
    private static final int SETTINGS_Y = 21, SETTINGS_ROW = 22;
    private static final int SCROLLBAR = 5;
    private static final PartyGui.Theme TAB_IDLE = new PartyGui.Theme(0xFF000000, 0xFFE9E9E9, 0xFFA9A9A9, 0xFF4A4A4A);
    private static final PartyGui.Theme GOLD = new PartyGui.Theme(0xFF3B2600, 0xFFFFF2A8, 0xFFFFC52E, 0xFFB5761A);
    private static final PartyGui.Theme SILVER = new PartyGui.Theme(0xFF202020, 0xFFFFFFFF, 0xFFC9D3DA, 0xFF7C8A94);
    private static final PartyGui.Theme BRONZE = new PartyGui.Theme(0xFF2A1405, 0xFFF4B98A, 0xFFC9793F, 0xFF7A4118);
    private static final PartyGui.Theme OTHER = new PartyGui.Theme(0xFF1A1030, 0xFFD9C8FF, 0xFF9C7FD6, 0xFF5B438F);
    private static final float MARQUEE_SPEED = 28F;
    private static final long MARQUEE_PAUSE_MS = 700;

    private boolean openSoundPlayed;
    private int playersScroll, pagesScroll;
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
    }

    private Page page() {
        return handler.getPage();
    }

    private boolean showsInventory() {
        return page() == Page.PROGRAM || page() == Page.GAINS;
    }

    @Override
    protected void init() {
        backgroundHeight = showsInventory() ? INVENTORY_Y + INVENTORY_PANEL_HEIGHT : PANEL_HEIGHT;
        super.init();
        // Room for the tabs above the panel
        y = Math.max(TABS_HEIGHT + 2, Math.min((height - backgroundHeight + TABS_HEIGHT) / 2, height - ROOM_BELOW - backgroundHeight));
        builtFor = handler.getData();
        addTabs();
        PartyDashboardData data = handler.getData();
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
        pagesScroll = 0;
        clearAndInit();
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        // New state from the server: the buttons follow (enabled, labels, tooltips)
        if (handler.getData() != builtFor) clearAndInit();
    }

    // ------------------------------------------------------------------ tabs

    /** Left edge (from the panel's) and width of each tab: as wide as its name needs, the room left shared out. */
    private final int[] tabLeft = new int[Page.values().length], tabWide = new int[Page.values().length];

    private void layoutTabs() {
        Page[] tabs = Page.values();
        int room = WIDTH - 8 - 2 * (tabs.length - 1), names = 0;
        for (Page tab : tabs) names += textRenderer.getWidth(Text.translatable(KEY + "tab." + key(tab)));
        int pad = Math.max(0, room - names) / tabs.length, left = 4;
        for (Page tab : tabs) {
            int index = tab.ordinal();
            // Names too long for the row (a wordy language): tabs of the same width, their names cut
            tabWide[index] = names > room - 4 * tabs.length ? room / tabs.length
                    : textRenderer.getWidth(Text.translatable(KEY + "tab." + key(tab))) + pad;
            tabLeft[index] = left;
            left += tabWide[index] + 2;
        }
    }

    private int tabWidth(int index) {
        return tabWide[index];
    }

    private int tabX(int index) {
        return x + tabLeft[index];
    }

    private void addTabs() {
        layoutTabs();
        for (Page tab : Page.values()) {
            int index = tab.ordinal();
            PartyButton button = new PartyButton(tabX(index), y - TAB_HEIGHT, tabWidth(index), TAB_HEIGHT,
                    Text.translatable(KEY + "tab." + key(tab)), b -> showPage(tab)) {
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

    /** What needs the player's attention on a tab, null for nothing. */
    private @Nullable Badge badge(Page tab) {
        PartyDashboardData data = handler.getData();
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

    private void drawTabs(DrawContext context, int mouseX, int mouseY, boolean selectedPass) {
        for (Page tab : Page.values()) {
            boolean selected = tab == page();
            if (selected != selectedPass) continue;
            int tx = tabX(tab.ordinal()), ty = y - TAB_HEIGHT - (selected ? TAB_RAISE : 0), tw = tabWidth(tab.ordinal());
            boolean hovered = mouseX >= tx && mouseX < tx + tw && mouseY >= y - TAB_HEIGHT && mouseY < y;
            PartyGui.Theme theme = selected ? PartyGui.PANEL : hovered ? TAB_IDLE.brighter() : TAB_IDLE;
            // Like the creative inventory's: a tab goes down behind the panel's top edge (the panel, drawn after the
            // tabs at rest, cuts them there); the selected one, drawn after the panel, opens into it
            PartyGui.panel(context, tx, ty, tw, y + 4 - ty, theme);
            if (selected) {
                PartyGui.Theme panel = PartyGui.PANEL;
                // No border between the tab and the page: the tab's bottom edge and the panel's top edge go
                context.fill(tx, y + 3, tx + tw, y + 4, panel.body());
                context.fill(tx + 3, y, tx + tw - 3, y + 3, panel.body());
                // The panel's light top edge goes on, on each side, from the foot of the tab
                context.fill(tx, y + 1, tx + 3, y + 3, panel.highlight());
                context.fill(tx + tw - 3, y + 1, tx + tw, y + 3, panel.highlight());
            }
            OrderedText label = fit(Text.translatable(KEY + "tab." + key(tab)), tw - 4);
            int cx = tx + (tw - textRenderer.getWidth(label)) / 2;
            int cy = ty + 6;
            context.drawText(textRenderer, label, cx, cy, selected ? PartyGui.TEXT_DARK : 0xFF2E2E2E, false);
            Badge badge = badge(tab);
            if (badge != null) drawBadge(context, tx + tw - 8, ty - 1, badge.error ? 0xFFD8323F : 0xFFF0A020, badge.error ? 0xFF5E0A12 : 0xFF6B4300, false);
        }
    }

    /** A round mark, 9 px: « ! » in the corner of a tab (red when it blocks, orange for a warning), or the « i » of a page. */
    private static void drawBadge(DrawContext context, int bx, int by, int body, int outline, boolean info) {
        context.fill(bx + 1, by, bx + 8, by + 9, outline);
        context.fill(bx, by + 1, bx + 9, by + 8, outline);
        context.fill(bx + 1, by + 1, bx + 8, by + 8, body);
        int mark = info ? 0xFF4A2C00 : 0xFFFFFFFF;
        if (info) {
            context.fill(bx + 4, by + 2, bx + 5, by + 3, mark);
            context.fill(bx + 4, by + 4, bx + 5, by + 7, mark);
        } else {
            context.fill(bx + 4, by + 2, bx + 5, by + 5, mark);
            context.fill(bx + 4, by + 6, bx + 5, by + 7, mark);
        }
    }

    // ------------------------------------------------------------------ buttons of the pages

    private void addStateButtons(PartyDashboardData data) {
        int buttonY = y + PANEL_HEIGHT - 6 - 18;
        // Follow the party: its HUDs on screen
        PartyButton follow = addDrawableChild(new PartyButton(x + PAD, buttonY, 70, 18,
                Text.translatable(KEY + (data.following() ? "follow.on" : "follow.off")), b -> click(BUTTON_FOLLOW)));
        follow.setSelected(data.following());
        follow.setTooltip(Tooltip.of(Text.translatable(KEY + "follow.tooltip")));

        if (data.phase() == Phase.RUNNING) return;
        // The main action (the board is checked again every two seconds while the dashboard is open, and at the launch)
        Blocker blocker = data.launchBlocker();
        int launchWidth = WIDTH - 2 * PAD - 73;
        PartyButton launch = addDrawableChild(new PartyButton(x + WIDTH - PAD - launchWidth, buttonY, launchWidth, 18,
                Text.translatable(KEY + (data.phase() == Phase.ENDED ? "launch.again" : "launch")), b -> {
                    click(BUTTON_LAUNCH);
                    close();
                }).style(PartyButton.Style.PRIMARY));
        launch.active = blocker == Blocker.NONE;
        launch.setTooltip(Tooltip.of(blocker == Blocker.NONE
                ? Text.translatable(KEY + "launch.tooltip")
                : Text.empty().append(Text.translatable(KEY + "launch.blocked").formatted(Formatting.RED)).append("\n")
                        .append(blockerText(blocker).copy().formatted(Formatting.GRAY))));
    }

    private void addSettingsButtons(PartyDashboardData data) {
        int rowY = y + SETTINGS_Y;
        boolean editable = data.canEdit() && data.phase() != Phase.RUNNING;
        Text why = !data.canEdit() ? Text.translatable(KEY + "locked") : Text.translatable(KEY + "settings.rounds.running");
        PartyButton minus = addDrawableChild(new PartyButton(x + WIDTH - PAD - 72, rowY, 18, 18, Text.literal("-"),
                b -> click(BUTTON_ROUNDS_DOWN, Screen.hasShiftDown() ? 5 : 1))
                .content((context, font, cx, cy, color) -> context.fill(cx - 4, cy - 1, cx + 4, cy + 1, color)));
        PartyButton plus = addDrawableChild(new PartyButton(x + WIDTH - PAD - 18, rowY, 18, 18, Text.literal("+"),
                b -> click(BUTTON_ROUNDS_UP, Screen.hasShiftDown() ? 5 : 1))
                .content((context, font, cx, cy, color) -> {
                    context.fill(cx - 4, cy - 1, cx + 4, cy + 1, color);
                    context.fill(cx - 1, cy - 4, cx + 1, cy + 4, color);
                }));
        minus.active = editable && data.roundsSetting() > PartyControllerEntity.MIN_ROUNDS;
        plus.active = editable && data.roundsSetting() < PartyControllerEntity.MAX_ROUNDS;
        minus.setTooltip(Tooltip.of(editable ? Text.translatable(KEY + "settings.rounds.less") : why));
        plus.setTooltip(Tooltip.of(editable ? Text.translatable(KEY + "settings.rounds.more") : why));
        // A practice round before each mini-game: on / off
        PartyButton practice = addDrawableChild(new PartyButton(x + WIDTH - PAD - 72, rowY + SETTINGS_ROW, 72, 18,
                Text.translatable(KEY + (data.practiceRound() ? "settings.practice.on" : "settings.practice.off")), b -> click(BUTTON_PRACTICE)));
        practice.setSelected(data.practiceRound());
        practice.active = data.canEdit();
        practice.setTooltip(Tooltip.of(data.canEdit() ? Text.translatable(KEY + "settings.practice.tooltip") : Text.translatable(KEY + "locked")));
    }

    private void addGainsButtons(PartyDashboardData data) {
        for (int row = 0; row < MiniGameGains.ROWS; row++) {
            for (PartyCurrency currency : new PartyCurrency[]{PartyCurrency.COIN, PartyCurrency.STAR}) {
                int left = x + (currency == PartyCurrency.COIN ? GAINS_COIN_X : GAINS_STAR_X), top = y + GAINS_Y + row * GAINS_ROW;
                int amount = data.gains().amount(currency, row);
                int less = gainButton(row, currency, false), more = gainButton(row, currency, true);
                PartyButton minus = addDrawableChild(new PartyButton(left, top, GAINS_STEP, GAINS_STEP, Text.literal("-"),
                        b -> click(less, Screen.hasShiftDown() ? 5 : 1))
                        .content((context, font, cx, cy, color) -> context.fill(cx - 3, cy - 1, cx + 3, cy + 1, color)));
                PartyButton plus = addDrawableChild(new PartyButton(left + GAINS_STEP + GAINS_FIELD + 2, top, GAINS_STEP, GAINS_STEP, Text.literal("+"),
                        b -> click(more, Screen.hasShiftDown() ? 5 : 1))
                        .content((context, font, cx, cy, color) -> {
                            context.fill(cx - 3, cy - 1, cx + 3, cy + 1, color);
                            context.fill(cx - 1, cy - 3, cx + 1, cy + 3, color);
                        }));
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
        drawTabs(context, mouseX, mouseY, false);
        PartyGui.panel(context, x, y, WIDTH, PANEL_HEIGHT, PartyGui.PANEL);
        drawTabs(context, mouseX, mouseY, true);
        if (showsInventory()) PartyGui.panel(context, x, y + INVENTORY_Y, WIDTH, INVENTORY_PANEL_HEIGHT, PartyGui.PANEL);
        // Slot boxes of the page
        for (Slot slot : handler.slots) {
            if (slot.isEnabled()) slotBox(context, x + slot.x - 1, y + slot.y - 1);
        }
        // The empty catalogue slot shows, faded, the item it takes (the currency slots are never empty)
        Slot catalogue = handler.getSlot(SLOT_CATALOGUE);
        if (catalogue.isEnabled() && !catalogue.hasStack()) {
            context.drawItem(new ItemStack(ModItems.MINI_GAMES_CATALOGUE), x + catalogue.x, y + catalogue.y);
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 250);
            context.fill(x + catalogue.x, y + catalogue.y, x + catalogue.x + 16, y + catalogue.y + 16, 0x998B8B8B);
            context.getMatrices().pop();
        }
    }

    private static void slotBox(DrawContext context, int sx, int sy) {
        PartyGui.inset(context, sx, sy, 18, 18, 0xFF8B8B8B, false, false);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        PartyDashboardData data = handler.getData();
        if (data == null) {
            centered(context, Text.translatable(KEY + "loading"), PANEL_HEIGHT / 2 - 4, PartyGui.TEXT_SOFT);
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
        drawBadge(context, INFO_X, INFO_Y, 0xFFFFC52E, 0xFF6B4300, true);
        if (flash != null && Util.getMeasuringTimeMs() < flashUntil) {
            int w = textRenderer.getWidth(flash) + 8;
            int fx = (WIDTH - w) / 2, fy = PANEL_HEIGHT - 16;
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 300);
            PartyGui.button(context, fx, fy, w, 13, new PartyGui.Theme(0xFF33030A, 0xFFFF8F8F, 0xFFD9283B, 0xFF8E1022), false);
            context.drawTextWithShadow(textRenderer, flash, fx + 4, fy + 3, 0xFFFFFFFF);
            context.getMatrices().pop();
        }
    }

    private static final int HEADING_Y = 8;

    /**
     * The title of the page, and « Read only » next to the « i » for who may not change what the page sets.
     *
     * @return where the room left on the title's line starts
     */
    private int heading(DrawContext context, Text text, boolean readOnly) {
        Text title = text.copy().formatted(Formatting.BOLD);
        Text locked = Text.translatable(KEY + "read_only");
        int room = INFO_X - PAD - 4 - (readOnly ? textRenderer.getWidth(locked) + 6 : 0);
        OrderedText line = fit(title, room);
        context.drawText(textRenderer, line, PAD, HEADING_Y, PartyGui.TEXT_DARK, false);
        if (readOnly) context.drawText(textRenderer, locked, INFO_X - 4 - textRenderer.getWidth(locked), HEADING_Y, PartyGui.TEXT_ERROR, false);
        return PAD + textRenderer.getWidth(line) + 8;
    }

    private void centered(DrawContext context, Text text, int ty, int color) {
        OrderedText line = fit(text, WIDTH - 2 * PAD);
        context.drawText(textRenderer, line, (WIDTH - textRenderer.getWidth(line)) / 2, ty, color, false);
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

    /** « [star] 3  [coin] 12 », returns the width. */
    private int drawCounts(DrawContext context, int stars, int coins, int cx, int cy, int color) {
        int start = cx;
        drawSmallItem(context, currency(PartyCurrency.STAR), cx, cy - 1, 10);
        cx += 11;
        context.drawText(textRenderer, Integer.toString(stars), cx, cy, color, false);
        cx += textRenderer.getWidth(Integer.toString(stars)) + 5;
        drawSmallItem(context, currency(PartyCurrency.COIN), cx, cy - 1, 10);
        cx += 11;
        context.drawText(textRenderer, Integer.toString(coins), cx, cy, color, false);
        cx += textRenderer.getWidth(Integer.toString(coins));
        return cx - start;
    }

    private static void warnIcon(DrawContext context, int ix, int iy) {
        context.fill(ix + 1, iy, ix + 6, iy + 7, 0xFF6B4300);
        context.fill(ix, iy + 1, ix + 7, iy + 6, 0xFF6B4300);
        context.fill(ix + 1, iy + 1, ix + 6, iy + 6, 0xFFF0A020);
        context.fill(ix + 3, iy + 1, ix + 4, iy + 4, 0xFFFFFFFF);
        context.fill(ix + 3, iy + 5, ix + 4, iy + 6, 0xFFFFFFFF);
    }

    private Text blockerText(Blocker blocker) {
        return Text.translatable(KEY + "blocker." + blocker.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** A scrollbar in the mod's look: a sunken track, a raised thumb. */
    private static void scrollbar(DrawContext context, int sx, int sy, int height, int scroll, int maxScroll, int shown, int total) {
        PartyGui.inset(context, sx, sy, SCROLLBAR, height, 0xFF6F6F6F, false, false);
        int thumb = Math.max(8, (height - 1) * shown / Math.max(shown, total));
        int travel = height - 1 - thumb;
        int ty = sy + 1 + (maxScroll == 0 ? 0 : travel * scroll / maxScroll);
        PartyGui.button(context, sx + 1, ty, SCROLLBAR - 1, thumb, PartyGui.BUTTON, false);
    }

    // ------------------------------------------------------------------ one-line texts that may not fit

    private OrderedText fit(Text text, int width) {
        if (textRenderer.getWidth(text) <= width) return text.asOrderedText();
        String ellipsis = "…";
        String cut = textRenderer.trimToWidth(text.getString(), Math.max(0, width - textRenderer.getWidth(ellipsis)));
        return Text.literal(cut.stripTrailing() + ellipsis).setStyle(text.getStyle()).asOrderedText();
    }

    /**
     * Draws {@code text} in a box {@code maxWidth} wide (panel coordinates): as is when it fits; else cut with « … »,
     * and scrolling back and forth, clipped to the box, while {@code hovered} (like the cartridge menu's lines).
     */
    private void drawFitted(DrawContext context, Text text, int tx, int ty, int maxWidth, int color, boolean hovered) {
        int width = textRenderer.getWidth(text);
        if (width <= maxWidth || !hovered) {
            context.drawText(textRenderer, fit(text, maxWidth), tx, ty, color, false);
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
        context.enableScissor(x + tx, y + ty - 1, x + tx + maxWidth, y + ty + 10);
        context.getMatrices().push();
        context.getMatrices().translate(-offset, 0, 0);
        context.drawText(textRenderer, text, tx, ty, color, false);
        context.getMatrices().pop();
        context.disableScissor();
    }

    private static boolean in(int mx, int my, int left, int top, int width, int height) {
        return mx >= left && mx < left + width && my >= top && my < top + height;
    }

    // ------------------------------------------------------------------ Status page

    /** A line of the checklist: its state, its text, its hint (tooltip) and the tab that fixes it. */
    private record Check(int state, Text text, Text hint, @Nullable Page target) {
        static final int OK = 0, WARN = 1, ERROR = 2;
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
        // Currencies and rounds
        checks.add(new Check(Check.OK, Text.translatable(KEY + "check.currencies", currency(PartyCurrency.STAR).getName(), currency(PartyCurrency.COIN).getName()),
                Text.translatable(KEY + "check.currencies.hint"), Page.GAINS));
        checks.add(new Check(Check.OK, Text.translatable(KEY + "check.rounds", data.roundsSetting()), Text.translatable(KEY + "check.rounds.hint"), Page.SETTINGS));
        return checks;
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

    private static final int CHECK_Y = 21;

    private void drawState(DrawContext context, PartyDashboardData data, int mx, int my) {
        switch (data.phase()) {
            case SETUP -> {
                heading(context, Text.translatable(KEY + "state.setup"), false);
                List<Check> checks = checklist(data);
                for (int i = 0; i < checks.size(); i++) {
                    Check check = checks.get(i);
                    int ly = CHECK_Y + i * LINE;
                    boolean hovered = in(mx, my, PAD - 2, ly - 3, WIDTH - 2 * PAD + 4, LINE);
                    if (hovered) context.fill(PAD - 2, ly - 3, WIDTH - PAD + 2, ly + LINE - 4, 0x30FFFFFF);
                    if (check.state() == Check.WARN) warnIcon(context, PAD, ly);
                    else PartyGui.statusIcon(context, PAD, ly, check.state() == Check.OK);
                    int color = check.state() == Check.ERROR ? PartyGui.TEXT_ERROR : check.state() == Check.WARN ? COLOR_WARN : PartyGui.TEXT_DARK;
                    drawFitted(context, check.text(), PAD + 11, ly, WIDTH - 2 * PAD - 20, color, hovered);
                    if (check.target() != null)
                        context.drawText(textRenderer, "›", WIDTH - PAD - 5, ly, PartyGui.TEXT_SOFT, false);
                }
            }
            case RUNNING -> {
                heading(context, Text.translatable(KEY + "state.running"), false);
                Text round = data.round() == 0 ? Text.translatable("hud.steveparty.party.round.start")
                        : Text.translatable("hud.steveparty.party.round", data.round(), data.rounds());
                int rw = textRenderer.getWidth(round) + 10, rx = INFO_X - 4 - rw;
                PartyGui.button(context, rx, HEADING_Y - 3, rw, 13, GOLD, false);
                context.drawText(textRenderer, round, rx + 5, HEADING_Y, 0xFF4A2C00, false);
                // The steps of the party: the current one stays near the left until the timeline is scrolled
                PartyDashboardData.Timeline steps = data.steps();
                int current = steps.offset() + steps.current();
                if (current != stepsFollowed) {
                    stepsFollowed = current;
                    stepsScrolled = false;
                }
                if (!stepsScrolled) stepsScroll = Math.max(0, steps.current() - TIMELINE_PAST);
                stepsScroll = drawTimeline(context, data, steps, PAD, STEPS_TIMELINE_Y, WIDTH - 2 * PAD, STEPS_CHIP, stepsScroll, mx, my);
                // What is happening now
                int ay = STEPS_TIMELINE_Y + TIMELINE_LABELS + STEPS_CHIP + 4;
                // « Step 3/20 », at the end of that line
                Text stepText = Text.translatable(KEY + "state.step", data.stepIndex() + 1, data.stepCount());
                int stepWidth = textRenderer.getWidth(stepText);
                context.drawText(textRenderer, stepText, WIDTH - PAD - stepWidth, ay, PartyGui.TEXT_SOFT, false);
                int actionRoom = WIDTH - 2 * PAD - stepWidth - 6;
                drawFitted(context, data.action(), PAD, ay, actionRoom, COLOR_GOLD, in(mx, my, PAD, ay - 1, actionRoom, 10));
                if (!data.actionDetail().getString().isEmpty())
                    drawFitted(context, data.actionDetail(), PAD, ay + 11, WIDTH - 2 * PAD, PartyGui.TEXT_DARK, in(mx, my, PAD, ay + 10, WIDTH - 2 * PAD, 10));
                // The leader
                PartyLiveData.Standing leader = leader(data);
                if (leader != null) {
                    int ly = 76;
                    OrderedText label = fit(Text.translatable(KEY + "state.leader", leader.tokenName()), WIDTH - 2 * PAD - 64);
                    context.drawText(textRenderer, label, PAD, ly, PartyGui.TEXT_DARK, false);
                    drawCounts(context, leader.stars(), leader.coins(), PAD + textRenderer.getWidth(label) + 5, ly, PartyGui.TEXT_DARK);
                }
            }
            case ENDED -> {
                List<PartyLiveData.Standing> players = data.players();
                int[] ranks = PartyLiveData.ranks(players);
                List<Integer> order = byRank(ranks);
                // The winner in the title
                heading(context, players.isEmpty() ? Text.translatable(KEY + "state.ended")
                        : Text.translatable(KEY + "state.winner", players.get(order.getFirst()).tokenName()), false);
                int py = 22;
                for (int i = 0; i < Math.min(order.size(), 5); i++) {
                    int index = order.get(i);
                    PartyLiveData.Standing player = players.get(index);
                    drawRankPlate(context, ranks[index], PAD, py - 3);
                    context.drawText(textRenderer, fit(Text.literal(player.tokenName()), 76), PAD + 26, py, PartyGui.TEXT_DARK, false);
                    context.drawText(textRenderer, fit(owner(player), 46), PAD + 106, py, PartyGui.TEXT_SOFT, false);
                    drawCounts(context, player.stars(), player.coins(), WIDTH - PAD - 58, py, PartyGui.TEXT_DARK);
                    py += 13;
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

    private void drawRankPlate(DrawContext context, int rank, int px, int py) {
        PartyGui.Theme theme = switch (rank) {
            case 1 -> GOLD;
            case 2 -> SILVER;
            case 3 -> BRONZE;
            default -> OTHER;
        };
        PartyGui.button(context, px, py, 22, 14, theme, false);
        Text text = Text.translatable("hud.steveparty.party.rank." + Math.min(rank, 9));
        context.drawText(textRenderer, text, px + (22 - textRenderer.getWidth(text)) / 2, py + 3, 0xFF2E2E2E, false);
    }

    private Text owner(PartyLiveData.Standing player) {
        if (player.owner().isEmpty()) return Text.translatable("hud.steveparty.party.anyone");
        if (!player.online()) return Text.translatable("hud.steveparty.party.offline", player.ownerName());
        return Text.literal(player.ownerName());
    }

    // ------------------------------------------------------------------ Players page

    private void drawPlayers(DrawContext context, PartyDashboardData data, int mx, int my) {
        List<PartyLiveData.Standing> players = data.players();
        heading(context, Text.translatable(data.phase() == Phase.SETUP ? KEY + "players.setup" : KEY + "players.party", players.size()), false);
        if (players.isEmpty()) {
            context.drawText(textRenderer, fit(Text.translatable(KEY + "check.tokens.none"), WIDTH - 2 * PAD), PAD, LIST_Y + 4, PartyGui.TEXT_ERROR, false);
            return;
        }
        boolean ranked = data.phase() != Phase.SETUP && players.stream().anyMatch(p -> p.stars() != 0 || p.coins() != 0);
        int[] ranks = PartyLiveData.ranks(players);
        int maxScroll = Math.max(0, players.size() - PLAYER_ROWS);
        playersScroll = Math.clamp(playersScroll, 0, maxScroll);
        int rowWidth = WIDTH - 2 * PAD - (maxScroll > 0 ? SCROLLBAR + 3 : 0);
        int ry = LIST_Y;
        for (int i = playersScroll; i < Math.min(players.size(), playersScroll + PLAYER_ROWS); i++) {
            PartyLiveData.Standing player = players.get(i);
            boolean current = i == data.currentPlayer();
            boolean hovered = in(mx, my, PAD, ry, rowWidth, ROW - 1);
            PartyGui.inset(context, PAD, ry, rowWidth, ROW - 2, current ? 0xFFFFE08A : 0xFFB9B9B9, false, false);
            // Turn order, rank
            context.drawText(textRenderer, Integer.toString(i + 1), PAD + 4, ry + 5, PartyGui.TEXT_SOFT, false);
            if (ranked) drawRankPlate(context, ranks[i], PAD + 14, ry + 2);
            // Colour and names
            int nameX = PAD + (ranked ? 40 : 16);
            int color = player.color() < 0 ? 0xFF8B8B8B : 0xFF000000 | player.color();
            context.fill(nameX, ry + 4, nameX + 8, ry + 12, 0xFF202020);
            context.fill(nameX + 1, ry + 5, nameX + 7, ry + 11, color);
            int countsX = PAD + rowWidth - 56;
            int namesRoom = countsX - nameX - 16, tokenRoom = namesRoom * 3 / 5, ownerRoom = namesRoom - tokenRoom - 4;
            drawFitted(context, Text.literal(player.tokenName()), nameX + 12, ry + 5, tokenRoom, PartyGui.TEXT_DARK, hovered);
            int ownerColor = player.owner().isEmpty() ? PartyGui.TEXT_SOFT : player.online() ? 0xFF2E5E8E : COLOR_WARN;
            context.drawText(textRenderer, fit(owner(player), ownerRoom), nameX + 12 + tokenRoom + 4, ry + 5, ownerColor, false);
            // Stars and coins
            drawCounts(context, player.stars(), player.coins(), countsX, ry + 5, PartyGui.TEXT_DARK);
            ry += ROW;
        }
        if (maxScroll > 0)
            scrollbar(context, WIDTH - PAD - SCROLLBAR, LIST_Y, PLAYER_ROWS * ROW - 2, playersScroll, maxScroll, PLAYER_ROWS, players.size());
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
     * The mini-games of the catalogue in a scrolling grid next to its slot, a line saying what the party will be
     * made of (the same expansion as the party generator), then the card slots, with the default party as ghost cards
     * while no card is placed.
     */
    private void drawProgram(DrawContext context, PartyDashboardData data, int mx, int my) {
        int summaryX = heading(context, Text.translatable(KEY + "tab.program"), !data.canEdit());
        // The mini-games
        int gridWidth = GRID_COLUMNS * 18;
        Badge problem = catalogueBadge(data);
        if (data.pages().isEmpty()) {
            Text text = Text.translatable(KEY + (data.hasCatalogue() ? "program.catalogue.empty" : "program.catalogue.none"));
            drawFitted(context, text, GRID_X + 2, CATALOGUE_Y + 4, gridWidth, PartyGui.TEXT_ERROR, in(mx, my, GRID_X, CATALOGUE_Y, gridWidth, 16));
        } else {
            int total = data.pages().size();
            int maxScroll = Math.max(0, (total + GRID_COLUMNS - 1) / GRID_COLUMNS - GRID_ROWS);
            pagesScroll = Math.clamp(pagesScroll, 0, maxScroll);
            for (int cell = 0; cell < GRID_COLUMNS * GRID_ROWS; cell++) {
                int index = pagesScroll * GRID_COLUMNS + cell;
                int cx = GRID_X + (cell % GRID_COLUMNS) * 18, cy = GRID_Y + (cell / GRID_COLUMNS) * 18;
                slotBox(context, cx, cy);
                if (index >= total) continue;
                PartyDashboardData.Page page = data.pages().get(index);
                if (page.slot() == data.currentPage()) context.fill(cx + 1, cy + 1, cx + 18, cy + 18, 0xFFFFC52E);
                else if (page.playable() == 0) context.fill(cx + 1, cy + 1, cx + 18, cy + 18, 0xFFE0707A);
                else if (page.played() > 0) context.fill(cx + 1, cy + 1, cx + 18, cy + 18, 0xFF8FCF7A);
                context.drawItem(page.page(), cx + 1, cy + 1);
                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 200);
                if (page.played() > 0) PartyGui.statusIcon(context, cx + 10, cy + 1, true);
                if (page.playable() == 0) PartyGui.statusIcon(context, cx + 10, cy + 10, false);
                context.getMatrices().pop();
            }
            if (maxScroll > 0)
                scrollbar(context, GRID_X + gridWidth + 3, GRID_Y, GRID_ROWS * 18, pagesScroll, maxScroll, GRID_ROWS, (total + GRID_COLUMNS - 1) / GRID_COLUMNS);
        }
        // What the party will be made of
        List<ItemStack> cards = cards();
        BasicGameGeneratorStep.Summary made = BasicGameGeneratorStep.summary(cards, data.roundsSetting());
        Text summary = Text.translatable(cards.isEmpty() ? KEY + "program.default" : KEY + "program.summary",
                made.turns(), made.miniGames(), made.events());
        // On the title's line: mini-games without their pipes, whom the program is for during a party, else the summary
        int summaryWidth = INFO_X - 4 - summaryX - (data.canEdit() ? 0 : textRenderer.getWidth(Text.translatable(KEY + "read_only")) + 6);
        boolean overTitle = in(mx, my, summaryX, HEADING_Y - 1, summaryWidth, 10);
        if (problem != null && !data.pages().isEmpty()) {
            warnIcon(context, summaryX, HEADING_Y);
            drawFitted(context, problem.reason(), summaryX + 10, HEADING_Y, summaryWidth - 10, COLOR_WARN, overTitle);
        } else {
            drawFitted(context, data.phase() == Phase.RUNNING ? Text.translatable(KEY + "program.running") : summary,
                    summaryX, HEADING_Y, summaryWidth, PartyGui.TEXT_SOFT, overTitle);
        }
        // The default party, as ghost cards
        List<ItemStack> ghosts = ghosts(data);
        for (int i = 0; i < ghosts.size(); i++) {
            int gx = PROGRAM_X + (i % 9) * 18, gy = PROGRAM_Y + (i / 9) * 18;
            context.drawItem(ghosts.get(i), gx, gy);
            context.drawStackOverlay(textRenderer, ghosts.get(i), gx, gy);
            // See-through: the slot's grey over the card
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 250);
            context.fill(gx, gy, gx + 16, gy + 16, 0x998B8B8B);
            context.getMatrices().pop();
        }
        // Under the cards: what the program will play, its loops unrolled
        programScroll = drawTimeline(context, data, data.program(), PAD, PROGRAM_TIMELINE_Y, WIDTH - 2 * PAD, PROGRAM_CHIP, programScroll, mx, my);
    }

    // ------------------------------------------------------------------ timelines

    private static final net.minecraft.util.Identifier ICON_DICE = fr.lordfinn.steveparty.Steveparty.id("party_hud/dice"),
            ICON_GEAR = fr.lordfinn.steveparty.Steveparty.id("party_hud/gear"), ICON_STEPS = fr.lordfinn.steveparty.Steveparty.id("party_hud/steps"),
            ICON_GAMEPAD = fr.lordfinn.steveparty.Steveparty.id("party_hud/gamepad"), ICON_CLOCK = fr.lordfinn.steveparty.Steveparty.id("party_hud/clock"),
            ICON_CROWN = fr.lordfinn.steveparty.Steveparty.id("party_hud/crown"), ICON_MARKER = fr.lordfinn.steveparty.Steveparty.id("party_hud/marker");
    private static final PartyGui.Theme CHIP_GAME = new PartyGui.Theme(0xFF0B2A12, 0xFFC9F2C0, 0xFF8FD67F, 0xFF4E9A45);
    private static final PartyGui.Theme CHIP_EVENT = new PartyGui.Theme(0xFF3A2400, 0xFFFFE2A8, 0xFFF4B24A, 0xFFB5761A);

    private static net.minecraft.util.Identifier icon(PartyDashboardData.StepKind kind) {
        return switch (kind) {
            case START_ROLLS -> ICON_DICE;
            case PREPARING -> ICON_GEAR;
            case TURN, TURNS -> ICON_STEPS;
            case MINI_GAME -> ICON_GAMEPAD;
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
     * A timeline: a row of chips, one per step (its icon: the head of the player whose turn it is, else what the step
     * is), the current one gold and framed, the past ones dimmed; above the first chip of each round, its number.
     * Shown from {@code scroll}; « +N » after the last step sent when the party has more.
     *
     * @return the scroll, kept within the timeline
     */
    private int drawTimeline(DrawContext context, PartyDashboardData data, PartyDashboardData.Timeline timeline, int left, int top, int width,
                             int chip, int scroll, int mx, int my) {
        List<PartyDashboardData.TimelineStep> steps = timeline.steps();
        int pitch = chip + TIMELINE_GAP, shown = (width + TIMELINE_GAP) / pitch;
        int total = steps.size() + (timeline.more() > 0 ? 2 : 0);
        scroll = Math.clamp(scroll, 0, Math.max(0, total - shown));
        int chipY = top + TIMELINE_LABELS;
        // More on the left
        if (scroll > 0 || timeline.offset() > 0) context.drawText(textRenderer, "‹", left - 5, chipY + (chip - 8) / 2, PartyGui.TEXT_SOFT, false);
        for (int slot = 0; slot < shown; slot++) {
            int index = scroll + slot, cx = left + slot * pitch;
            if (index >= steps.size()) {
                // « +N »: the steps the party has after those sent
                if (index == steps.size() && timeline.more() > 0)
                    context.drawText(textRenderer, "+" + timeline.more(), cx + 2, chipY + (chip - 8) / 2, PartyGui.TEXT_SOFT, false);
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
                if (textRenderer.getWidth(label) > room) label = Text.literal(Integer.toString(step.round()));
                context.drawText(textRenderer, label, cx + 1, top, past ? 0xFF8A8A8A : PartyGui.TEXT_SOFT, false);
                if (begins && slot > 0) context.fill(cx - TIMELINE_GAP, top + 1, cx - TIMELINE_GAP + 1, chipY + chip, 0xFF8A8A8A);
            }
            PartyGui.Theme theme = current ? GOLD : step.kind() == PartyDashboardData.StepKind.MINI_GAME ? CHIP_GAME
                    : step.kind() == PartyDashboardData.StepKind.EVENT ? CHIP_EVENT : PartyGui.BUTTON;
            PartyGui.button(context, cx, chipY, chip, chip, hovered ? theme.brighter() : theme, false);
            // The player whose turn it is: his head, on the colour of his token
            PartyLiveData.Standing player = step.player() >= 0 && step.player() < data.players().size() ? data.players().get(step.player()) : null;
            int head = chip >= 20 ? 16 : 8, inside = (chip - head) / 2;
            if (player != null && player.color() >= 0) context.fill(cx + 1, chipY + 1, cx + chip - 1, chipY + chip - 1, 0xFF000000 | player.color());
            if (player != null && player.owner().isPresent()) {
                net.minecraft.util.Identifier skin = fr.lordfinn.steveparty.client.utils.SkinUtils.getPlayerSkin(player.owner().get());
                context.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, skin, cx + inside, chipY + inside, 8, 8, head, head, 8, 8, 64, 64);
                context.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, skin, cx + inside, chipY + inside, 40, 8, head, head, 8, 8, 64, 64);
            } else {
                context.drawGuiTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, icon(step.kind()), cx + (chip - 9) / 2, chipY + (chip - 9) / 2, 9, 9);
            }
            if (past) context.fill(cx + 1, chipY + 1, cx + chip - 1, chipY + chip - 1, 0x9CC6C6C6);
            if (current) context.drawBorder(cx - 1, chipY - 1, chip + 2, chip + 2, 0xFFFFFFFF);
            if (hovered) hoveredStep = stepText(data, step, current);
        }
        return scroll;
    }

    /** @return true if the mouse is over the timeline of the page shown. */
    private boolean overTimeline(double mouseX, double mouseY) {
        PartyDashboardData data = handler.getData();
        if (data == null) return false;
        int mx = (int) mouseX - x, my = (int) mouseY - y;
        if (page() == Page.STATE && data.phase() == Phase.RUNNING) return in(mx, my, PAD - 6, STEPS_TIMELINE_Y, WIDTH - 2 * PAD + 12, TIMELINE_LABELS + STEPS_CHIP);
        return page() == Page.PROGRAM && in(mx, my, PAD - 6, PROGRAM_TIMELINE_Y, WIDTH - 2 * PAD + 12, TIMELINE_LABELS + PROGRAM_CHIP);
    }

    private void scrollTimeline(int steps) {
        if (page() == Page.STATE) {
            stepsScroll += steps;
            stepsScrolled = true;
        } else {
            programScroll += steps;
        }
    }

    private @Nullable PartyDashboardData.Page pageAt(double mouseX, double mouseY) {
        PartyDashboardData data = handler.getData();
        if (data == null || page() != Page.PROGRAM || data.pages().isEmpty()) return null;
        int col = (int) Math.floor((mouseX - x - GRID_X) / 18), row = (int) Math.floor((mouseY - y - GRID_Y) / 18);
        if (col < 0 || col >= GRID_COLUMNS || row < 0 || row >= GRID_ROWS) return null;
        int index = (pagesScroll + row) * GRID_COLUMNS + col;
        return index < data.pages().size() ? data.pages().get(index) : null;
    }

    // ------------------------------------------------------------------ Gains page

    private void drawGains(DrawContext context, PartyDashboardData data) {
        // No « Read only » here: the currency slots share the title's line (the greyed steppers and their tooltip say it)
        heading(context, Text.translatable(KEY + "tab.gains"), false);
        for (int row = 0; row < MiniGameGains.ROWS; row++) {
            int top = GAINS_Y + row * GAINS_ROW;
            if (row == MiniGameGains.PARTICIPANTS) {
                context.drawText(textRenderer, fit(Text.translatable(KEY + "gains.participants"), GAINS_COIN_X - PAD - 4), PAD, top + 4, PartyGui.TEXT_DARK, false);
            } else {
                drawRankPlate(context, row + 1, PAD, top + 1);
                context.drawText(textRenderer, fit(Text.translatable(KEY + "gains.place"), GAINS_COIN_X - PAD - 30), PAD + 26, top + 4, PartyGui.TEXT_SOFT, false);
            }
            for (PartyCurrency currency : new PartyCurrency[]{PartyCurrency.COIN, PartyCurrency.STAR}) {
                int left = (currency == PartyCurrency.COIN ? GAINS_COIN_X : GAINS_STAR_X) + GAINS_STEP + 1;
                int amount = data.gains().amount(currency, row);
                PartyGui.inset(context, left, top, GAINS_FIELD, GAINS_STEP, 0xFF3B4247, false, false);
                String value = Integer.toString(amount);
                context.drawText(textRenderer, value, left + (GAINS_FIELD - textRenderer.getWidth(value)) / 2 + 1, top + 4,
                        amount == 0 ? 0xFF8E979D : 0xFFFFFFFF, false);
            }
        }
    }

    // ------------------------------------------------------------------ Settings page

    private void drawSettings(DrawContext context, PartyDashboardData data) {
        heading(context, Text.translatable(KEY + "tab.settings"), !data.canEdit());
        int room = WIDTH - 2 * PAD - 78;
        context.drawText(textRenderer, fit(Text.translatable(KEY + "settings.rounds"), room), PAD, SETTINGS_Y + 5, PartyGui.TEXT_DARK, false);
        String value = Integer.toString(data.roundsSetting());
        int fieldX = WIDTH - PAD - 52, fieldW = 32;
        PartyGui.inset(context, fieldX, SETTINGS_Y, fieldW, 18, 0xFF3B4247, false, false);
        context.drawText(textRenderer, value, fieldX + (fieldW - textRenderer.getWidth(value)) / 2 + 1, SETTINGS_Y + 5, 0xFFFFFFFF, false);
        context.drawText(textRenderer, fit(Text.translatable(KEY + "settings.practice"), room), PAD, SETTINGS_Y + SETTINGS_ROW + 5, PartyGui.TEXT_DARK, false);
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
        PartyDashboardData data = handler.getData();
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
        if (data != null && in(mx, my, INFO_X - 1, INFO_Y - 1, INFO_SIZE + 2, INFO_SIZE + 2)) {
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
        if (focusedSlot != null && focusedSlot.id == SLOT_CATALOGUE && emptyHand && data != null) {
            if (!focusedSlot.hasStack()) {
                tooltip(context, List.of(Text.translatable(KEY + "program.catalogue").formatted(Formatting.GOLD),
                        Text.translatable(KEY + "program.catalogue.hint").formatted(Formatting.GRAY)), mouseX, mouseY);
                return;
            }
            List<Text> lines = new ArrayList<>();
            lines.add(focusedSlot.getStack().getName().copy().formatted(Formatting.GOLD));
            long played = data.pages().stream().filter(p -> p.played() > 0).count();
            lines.add(Text.translatable(KEY + "program.catalogue.summary", data.pages().size(), played).formatted(Formatting.GRAY));
            if (data.catalogueLocked()) lines.add(Text.translatable(KEY + "program.catalogue.locked").formatted(Formatting.RED));
            tooltip(context, lines, mouseX, mouseY);
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
        PartyDashboardData.Page hoveredPage = pageAt(mouseX, mouseY);
        if (hoveredPage != null && emptyHand) {
            List<Text> lines = new ArrayList<>(Screen.getTooltipFromItem(client, hoveredPage.page()));
            lines.add(hoveredPage.played() == 0 ? Text.translatable(KEY + "mini_games.page.not_played").formatted(Formatting.GRAY)
                    : Text.translatable(KEY + "mini_games.page.played", hoveredPage.played()).formatted(Formatting.GREEN));
            if (hoveredPage.playable() == 0) {
                lines.add(Text.translatable(hoveredPage.pipes() == 0 ? KEY + "mini_games.page.no_pipe" : KEY + "mini_games.page.not_playable").formatted(Formatting.RED));
            } else {
                lines.add(Text.translatable(KEY + "mini_games.page.pipes", hoveredPage.pipes()).formatted(Formatting.GRAY));
            }
            lines.add(hoveredPage.podiums() == 0 ? Text.translatable(KEY + "mini_games.page.no_podium").formatted(Formatting.YELLOW)
                    : Text.translatable(KEY + "mini_games.page.podiums", hoveredPage.podiums()).formatted(Formatting.GRAY));
            if (data != null && hoveredPage.slot() == data.currentPage())
                lines.add(Text.translatable(KEY + "mini_games.page.current").formatted(Formatting.GOLD));
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
        if (data != null && page() == Page.SETTINGS && emptyHand && mx >= PAD && mx < WIDTH - PAD - 76) {
            int row = Math.floorDiv(my - SETTINGS_Y, SETTINGS_ROW);
            if (my >= SETTINGS_Y && row >= 0 && row < 2 && my < SETTINGS_Y + row * SETTINGS_ROW + 18) {
                String key = row == 0 ? "settings.rounds" : "settings.practice";
                tooltip(context, List.of(Text.translatable(KEY + key + ".tooltip").formatted(Formatting.GRAY)), mouseX, mouseY);
                return;
            }
        }
        super.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    private int checkAt(double mouseX, double mouseY, PartyDashboardData data) {
        double mx = mouseX - x, my = mouseY - y;
        if (mx < PAD - 2 || mx >= WIDTH - PAD + 2) return -1;
        int index = (int) Math.floor((my - CHECK_Y + 3) / LINE);
        int count = checklist(data).size();
        return my >= CHECK_Y - 3 && index >= 0 && index < count ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PartyDashboardData data = handler.getData();
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
            PartyDashboardData data = handler.getData();
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
        if (verticalAmount != 0 && page() == Page.PLAYERS) {
            playersScroll -= (int) Math.signum(verticalAmount);
            return true;
        }
        if (verticalAmount != 0 && page() == Page.PROGRAM && mouseY < y + PROGRAM_Y - 2) {
            pagesScroll -= (int) Math.signum(verticalAmount);
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
