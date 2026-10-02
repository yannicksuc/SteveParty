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
 * The Party Controller's dashboard, built around the player's journey:
 * <ul>
 *     <li><b>Status</b>: before a party, a checklist of what a party needs (board, tokens on the start tiles, mini-game
 *     catalogue, currencies, rounds), each line leading to the tab where it is fixed, and « Start the party » (active
 *     only when ready, else it says why); during a party, what is happening now, the round, the leader; once over, the
 *     podium and « Start a new party ».</li>
 *     <li><b>Players</b>: the tokens in the turn order with their player, whether they are connected, their stars,
 *     coins and rank (before a party: the tokens on the start tiles, who will play).</li>
 *     <li><b>Mini-games</b>: the catalogue slot (insert / take it out), its pages with how many times each was played
 *     and whether it has somewhere to send the players.</li>
 *     <li><b>Gains</b>: what the party pays at the end of each mini-game, a row per place (1st to 4th, then the
 *     participants), an amount of coins and of stars each.</li>
 *     <li><b>Settings</b>: the Star and Coin items (click a slot with an item to pick it, with an empty hand to go back
 *     to the default one) and the number of rounds.</li>
 * </ul>
 * The tabs carry a red « ! » when something blocks (no board, no token, no catalogue) and a yellow one for a warning.
 * Everything shown comes from the server ({@link PartyDashboardData}), every action is checked there.
 */
public class PartyControllerScreen extends HandledScreen<PartyControllerScreenHandler> {
    private static final String KEY = "gui.steveparty.party_controller.";
    private static final int TAB_HEIGHT = 20, TAB_OVERLAP = 3;
    private static final int INVENTORY_PANEL_HEIGHT = 98;
    private static final int PAD = 10;
    private static final int LINE = 15;
    private static final int COLOR_WARN = 0xFFB36200;
    private static final int COLOR_GOLD = 0xFF8A5A00;
    /** Players rows and pages grid. */
    private static final int ROW = 19, PLAYER_ROWS = 5;
    private static final int GRID_COLUMNS = 12, GRID_ROWS = 3, GRID_Y = 60;
    private static final PartyGui.Theme TAB_IDLE = new PartyGui.Theme(0xFF000000, 0xFFE9E9E9, 0xFFA9A9A9, 0xFF4A4A4A);
    private static final PartyGui.Theme GOLD = new PartyGui.Theme(0xFF3B2600, 0xFFFFF2A8, 0xFFFFC52E, 0xFFB5761A);
    private static final PartyGui.Theme SILVER = new PartyGui.Theme(0xFF202020, 0xFFFFFFFF, 0xFFC9D3DA, 0xFF7C8A94);
    private static final PartyGui.Theme BRONZE = new PartyGui.Theme(0xFF2A1405, 0xFFF4B98A, 0xFFC9793F, 0xFF7A4118);
    private static final PartyGui.Theme OTHER = new PartyGui.Theme(0xFF1A1030, 0xFFD9C8FF, 0xFF9C7FD6, 0xFF5B438F);

    private boolean openSoundPlayed;
    private int playersScroll, pagesScroll;
    /** A local refusal shown on the page for a few seconds (setting slots). */
    private @Nullable Text flash;
    private long flashUntil;
    /** The data the widgets were built for: rebuilt when a new one arrives. */
    private @Nullable PartyDashboardData builtFor;

    public PartyControllerScreen(PartyControllerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = WIDTH;
    }

    private Page page() {
        return handler.getPage();
    }

    private boolean showsInventory() {
        return page() == Page.MINI_GAMES || page() == Page.PROGRAM || page() == Page.SETTINGS;
    }

    @Override
    protected void init() {
        backgroundHeight = showsInventory() ? INVENTORY_Y + INVENTORY_PANEL_HEIGHT : PANEL_HEIGHT;
        super.init();
        // Room for the tabs above the panel
        y = Math.max(TAB_HEIGHT, (height - backgroundHeight + TAB_HEIGHT - TAB_OVERLAP) / 2);
        builtFor = handler.getData();
        addTabs();
        PartyDashboardData data = handler.getData();
        if (data != null) {
            switch (page()) {
                case STATE -> addStateButtons(data);
                case PLAYERS -> {}
                case MINI_GAMES, PROGRAM -> {}
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
            PartyButton button = new PartyButton(tabX(index), y - TAB_HEIGHT + TAB_OVERLAP, tabWidth(index), TAB_HEIGHT,
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
        Text name = Text.translatable(KEY + "tab." + key(tab)).formatted(Formatting.GOLD);
        Text description = Text.translatable(KEY + "tab." + key(tab) + ".tooltip").formatted(Formatting.GRAY);
        Badge badge = badge(tab);
        Text reason = badge == null ? null : badge.reason.copy().formatted(badge.error ? Formatting.RED : Formatting.YELLOW);
        return reason == null ? Text.empty().append(name).append("\n").append(description)
                : Text.empty().append(name).append("\n").append(reason).append("\n").append(description);
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
            case MINI_GAMES -> {
                if (!data.hasCatalogue()) return new Badge(true, Text.translatable(KEY + "badge.no_catalogue"));
                if (data.pages().isEmpty()) return new Badge(true, Text.translatable(KEY + "badge.empty_catalogue"));
                long notPlayable = data.pages().stream().filter(p -> p.playable() == 0).count();
                if (notPlayable > 0) return new Badge(false, Text.translatable(KEY + "badge.no_pipes", notPlayable));
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    private static String key(Page page) {
        return page.name().toLowerCase(java.util.Locale.ROOT);
    }

    private void drawTabs(DrawContext context, int mouseX, int mouseY, boolean selectedPass) {
        for (Page tab : Page.values()) {
            boolean selected = tab == page();
            if (selected != selectedPass) continue;
            int tx = tabX(tab.ordinal()), ty = y - TAB_HEIGHT + TAB_OVERLAP, tw = tabWidth(tab.ordinal());
            boolean hovered = mouseX >= tx && mouseX < tx + tw && mouseY >= ty && mouseY < ty + TAB_HEIGHT;
            PartyGui.Theme theme = selected ? PartyGui.PANEL : hovered ? TAB_IDLE.brighter() : TAB_IDLE;
            PartyGui.panel(context, tx, ty, tw, TAB_HEIGHT + (selected ? TAB_OVERLAP + 2 : 0), theme);
            if (selected) {
                // Merge into the panel: no border between the tab and the page
                context.fill(tx + 3, y, tx + tw - 3, y + 3, PartyGui.PANEL.body());
            }
            OrderedText label = fit(Text.translatable(KEY + "tab." + key(tab)), tw - 4);
            int cx = tx + (tw - textRenderer.getWidth(label)) / 2;
            int cy = ty + (selected ? 6 : 7);
            context.drawText(textRenderer, label, cx, cy, selected ? PartyGui.TEXT_DARK : 0xFF2E2E2E, false);
            Badge badge = badge(tab);
            if (badge != null) drawBadge(context, tx + tw - 7, ty - 3, badge.error);
        }
    }

    /** A round « ! » in the corner of a tab: red when it blocks, orange for a warning. */
    private void drawBadge(DrawContext context, int bx, int by, boolean error) {
        int body = error ? 0xFFD8323F : 0xFFF0A020, outline = error ? 0xFF5E0A12 : 0xFF6B4300;
        context.fill(bx + 1, by, bx + 8, by + 9, outline);
        context.fill(bx, by + 1, bx + 9, by + 8, outline);
        context.fill(bx + 1, by + 1, bx + 8, by + 8, body);
        context.fill(bx + 4, by + 2, bx + 5, by + 5, 0xFFFFFFFF);
        context.fill(bx + 4, by + 6, bx + 5, by + 7, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ buttons of the pages

    private void addStateButtons(PartyDashboardData data) {
        int buttonY = y + PANEL_HEIGHT - PAD - 18;
        // Follow the party: its HUDs on screen
        PartyButton follow = addDrawableChild(new PartyButton(x + PAD, buttonY, 78, 18,
                Text.translatable(KEY + (data.following() ? "follow.on" : "follow.off")), b -> click(BUTTON_FOLLOW)));
        follow.setSelected(data.following());
        follow.setTooltip(Tooltip.of(Text.translatable(KEY + "follow.tooltip")));

        if (data.phase() == Phase.RUNNING) return;
        // Check the board again (it is checked every few seconds anyway)
        PartyButton check = addDrawableChild(new PartyButton(x + PAD + 82, buttonY, 18, 18, Text.translatable(KEY + "check_board"),
                b -> click(BUTTON_CHECK_BOARD)).content((context, font, cx, cy, color) -> drawSmallItem(context, new ItemStack(ModItems.WRENCH), cx - 5, cy - 5, 10)));
        check.setTooltip(Tooltip.of(Text.translatable(KEY + "check_board.tooltip")));

        // The main action
        Blocker blocker = data.launchBlocker();
        int launchWidth = WIDTH - 2 * PAD - 104;
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
        int rowY = y + ROUNDS_Y;
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
        minus.active = editable && data.roundsSetting() > fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity.MIN_ROUNDS;
        plus.active = editable && data.roundsSetting() < fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity.MAX_ROUNDS;
        minus.setTooltip(Tooltip.of(editable ? Text.translatable(KEY + "settings.rounds.less") : why));
        plus.setTooltip(Tooltip.of(editable ? Text.translatable(KEY + "settings.rounds.more") : why));
    }

    // Gains page: a row per place, a stepper of coins and one of stars
    private static final int GAINS_Y = 48, GAINS_ROW = 20, GAINS_COIN_X = 104, GAINS_STAR_X = 188, GAINS_STEP = 16, GAINS_FIELD = 28;

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

    private void drawGains(DrawContext context, PartyDashboardData data) {
        heading(context, Text.translatable(KEY + "gains.title"));
        if (!data.canEdit())
            context.drawText(textRenderer, fit(Text.translatable(KEY + "settings.read_only"), WIDTH - 2 * PAD - 110), PAD + 110, 9, PartyGui.TEXT_ERROR, false);
        context.drawText(textRenderer, fit(Text.translatable(KEY + "gains.hint"), WIDTH - 2 * PAD), PAD, 21, PartyGui.TEXT_SOFT, false);
        // The two currencies, above their steppers
        int stepper = 2 * GAINS_STEP + GAINS_FIELD + 2;
        for (PartyCurrency currency : new PartyCurrency[]{PartyCurrency.COIN, PartyCurrency.STAR}) {
            int left = currency == PartyCurrency.COIN ? GAINS_COIN_X : GAINS_STAR_X;
            OrderedText name = fit(Text.translatable(KEY + (currency == PartyCurrency.COIN ? "gains.coins" : "gains.stars")).formatted(Formatting.BOLD), stepper - 12);
            int width = 12 + textRenderer.getWidth(name);
            drawSmallItem(context, currency(currency), left + (stepper - width) / 2, GAINS_Y - 13, 10);
            context.drawText(textRenderer, name, left + (stepper - width) / 2 + 12, GAINS_Y - 12,
                    currency == PartyCurrency.STAR ? COLOR_GOLD : PartyGui.TEXT_DARK, false);
        }
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

    private void click(int button) {
        click(button, 1);
    }

    private void click(int button, int times) {
        if (client == null || client.interactionManager == null) return;
        for (int i = 0; i < times; i++) client.interactionManager.clickButton(handler.syncId, button);
    }

    private static final int ROUNDS_Y = 124;

    // ------------------------------------------------------------------ drawing

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        drawTabs(context, mouseX, mouseY, false);
        PartyGui.panel(context, x, y, WIDTH, PANEL_HEIGHT, PartyGui.PANEL);
        drawTabs(context, mouseX, mouseY, true);
        if (showsInventory()) {
            PartyGui.panel(context, x, y + INVENTORY_Y, WIDTH, INVENTORY_PANEL_HEIGHT, PartyGui.PANEL);
            context.drawText(textRenderer, playerInventoryTitle, x + (WIDTH - 162) / 2 + 1, y + INVENTORY_Y + 4, PartyGui.TEXT_DARK, false);
        }
        // Slot boxes of the page
        for (Slot slot : handler.slots) {
            if (slot.isEnabled()) slotBox(context, x + slot.x - 1, y + slot.y - 1);
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
        switch (page()) {
            case STATE -> drawState(context, data, mouseX - x, mouseY - y);
            case PLAYERS -> drawPlayers(context, data);
            case MINI_GAMES -> drawMiniGames(context, data);
            case PROGRAM -> drawProgram(context, data);
            case GAINS -> drawGains(context, data);
            case SETTINGS -> drawSettings(context, data);
        }
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

    private void heading(DrawContext context, Text text) {
        context.drawText(textRenderer, text.copy().formatted(Formatting.BOLD), PAD, 9, PartyGui.TEXT_DARK, false);
    }

    private void centered(DrawContext context, Text text, int ty, int color) {
        context.drawText(textRenderer, text, (WIDTH - textRenderer.getWidth(text)) / 2, ty, color, false);
    }

    private int wrapped(DrawContext context, Text text, int tx, int ty, int width, int color) {
        List<OrderedText> lines = textRenderer.wrapLines(text, width);
        for (OrderedText line : lines) {
            context.drawText(textRenderer, line, tx, ty, color, false);
            ty += 10;
        }
        return ty;
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
            checks.add(new Check(Check.WARN, Text.translatable(KEY + "check.catalogue.none"), Text.translatable(KEY + "check.catalogue.none.hint"), Page.MINI_GAMES));
        } else if (data.pages().isEmpty()) {
            checks.add(new Check(Check.WARN, Text.translatable(KEY + "check.catalogue.empty"), Text.translatable(KEY + "check.catalogue.empty.hint"), Page.MINI_GAMES));
        } else if (notPlayable > 0) {
            checks.add(new Check(Check.WARN, Text.translatable(KEY + "check.catalogue.no_pipes", data.pages().size(), notPlayable), Text.translatable(KEY + "check.catalogue.no_pipes.hint"), Page.MINI_GAMES));
        } else {
            checks.add(new Check(Check.OK, Text.translatable(KEY + "check.catalogue.ok", data.pages().size()), Text.translatable(KEY + "check.catalogue.ok.hint"), Page.MINI_GAMES));
        }
        // Currencies and rounds
        checks.add(new Check(Check.OK, Text.translatable(KEY + "check.currencies", currency(PartyCurrency.STAR).getName(), currency(PartyCurrency.COIN).getName()),
                Text.translatable(KEY + "check.currencies.hint"), Page.SETTINGS));
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

    private static final int CHECK_Y = 26;

    private void drawState(DrawContext context, PartyDashboardData data, int mx, int my) {
        switch (data.phase()) {
            case SETUP -> {
                heading(context, Text.translatable(KEY + "state.setup"));
                List<Check> checks = checklist(data);
                for (int i = 0; i < checks.size(); i++) {
                    Check check = checks.get(i);
                    int ly = CHECK_Y + i * LINE;
                    boolean hovered = mx >= PAD - 2 && mx < WIDTH - PAD + 2 && my >= ly - 3 && my < ly + LINE - 3;
                    if (hovered) context.fill(PAD - 2, ly - 3, WIDTH - PAD + 2, ly + LINE - 4, 0x30FFFFFF);
                    if (check.state() == Check.WARN) warnIcon(context, PAD, ly);
                    else PartyGui.statusIcon(context, PAD, ly, check.state() == Check.OK);
                    int color = check.state() == Check.ERROR ? PartyGui.TEXT_ERROR : check.state() == Check.WARN ? COLOR_WARN : PartyGui.TEXT_DARK;
                    context.drawText(textRenderer, fit(check.text(), WIDTH - 2 * PAD - 22), PAD + 11, ly, color, false);
                    if (check.target() != null)
                        context.drawText(textRenderer, "›", WIDTH - PAD - 5, ly, PartyGui.TEXT_SOFT, false);
                }
                Text redstone = Text.translatable(KEY + "state.redstone");
                wrapped(context, redstone, PAD, CHECK_Y + checks.size() * LINE + 2, WIDTH - 2 * PAD, PartyGui.TEXT_SOFT);
            }
            case RUNNING -> {
                heading(context, Text.translatable(KEY + "state.running"));
                Text round = data.round() == 0 ? Text.translatable("hud.steveparty.party.round.start")
                        : Text.translatable("hud.steveparty.party.round", data.round(), data.rounds());
                int rw = textRenderer.getWidth(round) + 10;
                PartyGui.button(context, WIDTH - PAD - rw, 6, rw, 14, GOLD, false);
                context.drawText(textRenderer, round, WIDTH - PAD - rw + 5, 9, 0xFF4A2C00, false);
                // Progress through the steps
                int barY = 26, barW = WIDTH - 2 * PAD;
                PartyGui.inset(context, PAD, barY, barW, 6, 0xFF3B4247, false, false);
                int done = data.stepCount() == 0 ? 0 : (int) ((barW - 2) * (double) Math.max(0, data.stepIndex()) / data.stepCount());
                context.fill(PAD + 1, barY + 1, PAD + 1 + done, barY + 6, 0xFF46AE2E);
                context.drawText(textRenderer, Text.translatable(KEY + "state.step", data.stepIndex() + 1, data.stepCount()),
                        PAD, barY + 9, PartyGui.TEXT_SOFT, false);
                // What is happening now
                int ay = barY + 24;
                context.drawText(textRenderer, Text.translatable(KEY + "state.now").formatted(Formatting.BOLD), PAD, ay, PartyGui.TEXT_DARK, false);
                ay = wrapped(context, data.action(), PAD, ay + 12, WIDTH - 2 * PAD, COLOR_GOLD);
                if (!data.actionDetail().getString().isEmpty())
                    ay = wrapped(context, data.actionDetail(), PAD, ay, WIDTH - 2 * PAD, PartyGui.TEXT_DARK);
                // The leader
                PartyLiveData.Standing leader = leader(data);
                if (leader != null) {
                    int ly = Math.max(ay + 6, 100);
                    Text label = Text.translatable(KEY + "state.leader", leader.tokenName());
                    context.drawText(textRenderer, label, PAD, ly, PartyGui.TEXT_DARK, false);
                    drawCounts(context, leader.stars(), leader.coins(), PAD + textRenderer.getWidth(label) + 5, ly, PartyGui.TEXT_DARK);
                }
                wrapped(context, Text.translatable(KEY + "state.step_controller"), PAD + 82, PANEL_HEIGHT - PAD - 18, WIDTH - 2 * PAD - 82, PartyGui.TEXT_SOFT);
            }
            case ENDED -> {
                heading(context, Text.translatable(KEY + "state.ended"));
                List<PartyLiveData.Standing> players = data.players();
                int[] ranks = PartyLiveData.ranks(players);
                List<Integer> order = byRank(ranks);
                int py = 28;
                for (int i = 0; i < Math.min(order.size(), 5); i++) {
                    int index = order.get(i);
                    PartyLiveData.Standing player = players.get(index);
                    drawRankPlate(context, ranks[index], PAD, py - 3);
                    context.drawText(textRenderer, fit(Text.literal(player.tokenName()), 90), PAD + 26, py, PartyGui.TEXT_DARK, false);
                    context.drawText(textRenderer, fit(owner(player), 48), PAD + 130, py, PartyGui.TEXT_SOFT, false);
                    drawCounts(context, player.stars(), player.coins(), WIDTH - PAD - 58, py, PartyGui.TEXT_DARK);
                    py += 17;
                }
                if (!players.isEmpty()) {
                    PartyLiveData.Standing winner = players.get(order.getFirst());
                    centered(context, Text.translatable(KEY + "state.winner", winner.tokenName()).formatted(Formatting.BOLD), py + 4, COLOR_GOLD);
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

    private OrderedText fit(Text text, int width) {
        if (textRenderer.getWidth(text) <= width) return text.asOrderedText();
        String ellipsis = "…";
        String cut = textRenderer.trimToWidth(text.getString(), width - textRenderer.getWidth(ellipsis));
        return Text.literal(cut + ellipsis).setStyle(text.getStyle()).asOrderedText();
    }

    // ------------------------------------------------------------------ Players page

    private void drawPlayers(DrawContext context, PartyDashboardData data) {
        List<PartyLiveData.Standing> players = data.players();
        heading(context, Text.translatable(data.phase() == Phase.SETUP ? KEY + "players.setup" : KEY + "players.party", players.size()));
        if (players.isEmpty()) {
            wrapped(context, Text.translatable(KEY + "players.none"), PAD, 30, WIDTH - 2 * PAD, PartyGui.TEXT_ERROR);
            wrapped(context, Text.translatable(KEY + "check.tokens.hint"), PAD, 54, WIDTH - 2 * PAD, PartyGui.TEXT_SOFT);
            return;
        }
        boolean ranked = data.phase() != Phase.SETUP && players.stream().anyMatch(p -> p.stars() != 0 || p.coins() != 0);
        int[] ranks = PartyLiveData.ranks(players);
        playersScroll = Math.clamp(playersScroll, 0, Math.max(0, players.size() - PLAYER_ROWS));
        int ry = 24;
        for (int i = playersScroll; i < Math.min(players.size(), playersScroll + PLAYER_ROWS); i++) {
            PartyLiveData.Standing player = players.get(i);
            boolean current = i == data.currentPlayer();
            PartyGui.inset(context, PAD, ry, WIDTH - 2 * PAD, ROW - 2, current ? 0xFFFFE08A : 0xFFB9B9B9, false, false);
            // Turn order, rank
            context.drawText(textRenderer, Integer.toString(i + 1), PAD + 4, ry + 5, PartyGui.TEXT_SOFT, false);
            if (ranked) drawRankPlate(context, ranks[i], PAD + 14, ry + 2);
            // Colour and names
            int nameX = PAD + 40;
            int color = player.color() < 0 ? 0xFF8B8B8B : 0xFF000000 | player.color();
            context.fill(nameX, ry + 4, nameX + 8, ry + 12, 0xFF202020);
            context.fill(nameX + 1, ry + 5, nameX + 7, ry + 11, color);
            context.drawText(textRenderer, fit(Text.literal(player.tokenName()), 70), nameX + 12, ry + 5, PartyGui.TEXT_DARK, false);
            int ownerColor = player.owner().isEmpty() ? PartyGui.TEXT_SOFT : player.online() ? 0xFF2E5E8E : COLOR_WARN;
            context.drawText(textRenderer, fit(owner(player), 44), nameX + 86, ry + 5, ownerColor, false);
            // Stars and coins
            drawCounts(context, player.stars(), player.coins(), WIDTH - PAD - 56, ry + 5, PartyGui.TEXT_DARK);
            ry += ROW;
        }
        if (players.size() > PLAYER_ROWS)
            context.drawText(textRenderer, Text.translatable(KEY + "scroll", playersScroll + 1, players.size() - PLAYER_ROWS + 1),
                    WIDTH - PAD - 30, 9, PartyGui.TEXT_SOFT, false);
        wrapped(context, Text.translatable(data.phase() == Phase.SETUP ? KEY + "players.setup.footer" : KEY + "players.footer"),
                PAD, 24 + PLAYER_ROWS * ROW + 1, WIDTH - 2 * PAD, PartyGui.TEXT_SOFT);
    }

    // ------------------------------------------------------------------ Mini-games page

    private int gridX() {
        return (WIDTH - GRID_COLUMNS * 18) / 2;
    }

    private void drawMiniGames(DrawContext context, PartyDashboardData data) {
        heading(context, Text.translatable(KEY + "mini_games.title"));
        int tx = CATALOGUE_X + 24;
        int textWidth = WIDTH - tx - PAD;
        if (!data.hasCatalogue()) {
            int ty = wrapped(context, Text.translatable(KEY + "mini_games.none"), tx, CATALOGUE_Y - 4, textWidth, PartyGui.TEXT_ERROR);
            wrapped(context, Text.translatable(KEY + "mini_games.none.hint"), PAD, Math.max(ty + 2, CATALOGUE_Y + 22), WIDTH - 2 * PAD, PartyGui.TEXT_DARK);
            return;
        }
        long played = data.pages().stream().filter(p -> p.played() > 0).count();
        context.drawText(textRenderer, Text.translatable(KEY + "mini_games.summary", data.pages().size(), played), tx, CATALOGUE_Y, PartyGui.TEXT_DARK, false);
        Text second = data.catalogueLocked() ? Text.translatable(KEY + "mini_games.locked").formatted(Formatting.RED)
                : Text.translatable(KEY + "mini_games.edit_hint");
        context.drawText(textRenderer, fit(second, textWidth), tx, CATALOGUE_Y + 10, PartyGui.TEXT_SOFT, false);
        if (data.pages().isEmpty()) {
            wrapped(context, Text.translatable(KEY + "check.catalogue.empty.hint"), PAD, GRID_Y, WIDTH - 2 * PAD, PartyGui.TEXT_ERROR);
            return;
        }
        int total = data.pages().size();
        int maxScroll = Math.max(0, (total + GRID_COLUMNS - 1) / GRID_COLUMNS - GRID_ROWS);
        pagesScroll = Math.clamp(pagesScroll, 0, maxScroll);
        int gx = gridX();
        for (int cell = 0; cell < GRID_COLUMNS * GRID_ROWS; cell++) {
            int index = pagesScroll * GRID_COLUMNS + cell;
            int cx = gx + (cell % GRID_COLUMNS) * 18, cy = GRID_Y + (cell / GRID_COLUMNS) * 18;
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
            context.drawText(textRenderer, Text.translatable(KEY + "scroll", pagesScroll + 1, maxScroll + 1), WIDTH - PAD - 30, 9, PartyGui.TEXT_SOFT, false);
        // Legend
        int ly = GRID_Y + GRID_ROWS * 18 + 6;
        PartyGui.statusIcon(context, PAD, ly, true);
        context.drawText(textRenderer, Text.translatable(KEY + "mini_games.legend.played"), PAD + 10, ly, PartyGui.TEXT_SOFT, false);
        int lx = PAD + 10 + textRenderer.getWidth(Text.translatable(KEY + "mini_games.legend.played")) + 10;
        PartyGui.statusIcon(context, lx, ly, false);
        context.drawText(textRenderer, Text.translatable(KEY + "mini_games.legend.no_pipes"), lx + 10, ly, PartyGui.TEXT_SOFT, false);
        if (data.currentPage() >= 0) {
            int lx2 = lx + 10 + textRenderer.getWidth(Text.translatable(KEY + "mini_games.legend.no_pipes")) + 10;
            context.fill(lx2, ly, lx2 + 7, ly + 7, 0xFFFFC52E);
            context.drawText(textRenderer, Text.translatable(KEY + "mini_games.legend.current"), lx2 + 10, ly, PartyGui.TEXT_SOFT, false);
        }
    }

    private @Nullable PartyDashboardData.Page pageAt(double mouseX, double mouseY) {
        PartyDashboardData data = handler.getData();
        if (data == null || page() != Page.MINI_GAMES || !data.hasCatalogue()) return null;
        int col = (int) Math.floor((mouseX - x - gridX()) / 18), row = (int) Math.floor((mouseY - y - GRID_Y) / 18);
        if (col < 0 || col >= GRID_COLUMNS || row < 0 || row >= GRID_ROWS) return null;
        int index = (pagesScroll + row) * GRID_COLUMNS + col;
        return index < data.pages().size() ? data.pages().get(index) : null;
    }

    // ------------------------------------------------------------------ Program page

    /**
     * The party program: the cards read left to right, top to bottom, then what the party will be made of (the same
     * expansion as the party generator: an empty program is the default party of the Settings page's rounds).
     */
    private void drawProgram(DrawContext context, PartyDashboardData data) {
        heading(context, Text.translatable(KEY + "program.title"));
        if (!data.canEdit())
            context.drawText(textRenderer, fit(Text.translatable(KEY + "settings.read_only"), WIDTH - 2 * PAD - 90), PAD + 90, 9, PartyGui.TEXT_ERROR, false);
        wrapped(context, Text.translatable(KEY + "program.hint"), PAD, 21, WIDTH - 2 * PAD, PartyGui.TEXT_SOFT);
        List<ItemStack> cards = new ArrayList<>();
        for (int i = 0; i < PartyControllerEntity.PROGRAM_SLOTS; i++) {
            ItemStack stack = handler.getSlot(PROGRAM_FIRST_SLOT + i).getStack();
            if (!stack.isEmpty()) cards.add(stack);
        }
        int turns = 0, miniGames = 0, events = 0;
        PartyCardItem.CardType previous = null;
        for (BasicGameGeneratorStep.ExpandedCard card : BasicGameGeneratorStep.expand(cards, data.roundsSetting())) {
            switch (card.type()) {
                case TURNS -> { if (previous != PartyCardItem.CardType.TURNS) turns++; }
                case MINIGAME -> miniGames++;
                case EVENT -> events++;
                default -> {}
            }
            previous = card.type();
        }
        int ty = PROGRAM_Y + 2 * 18 + 8;
        Text summary = Text.translatable(cards.isEmpty() ? KEY + "program.default" : KEY + "program.summary",
                turns, miniGames, events).formatted(Formatting.BOLD);
        ty = wrapped(context, summary, PAD, ty, WIDTH - 2 * PAD, PartyGui.TEXT_DARK);
        wrapped(context, Text.translatable(data.phase() == Phase.RUNNING ? KEY + "program.running" : KEY + "program.cards"),
                PAD, ty + 2, WIDTH - 2 * PAD, PartyGui.TEXT_SOFT);
    }

    // ------------------------------------------------------------------ Settings page

    private void drawSettings(DrawContext context, PartyDashboardData data) {
        heading(context, Text.translatable(KEY + "settings.title"));
        if (!data.canEdit())
            context.drawText(textRenderer, fit(Text.translatable(KEY + "settings.read_only"), WIDTH - 2 * PAD - 70), PAD + 70, 9, PartyGui.TEXT_ERROR, false);
        drawCurrency(context, PartyCurrency.STAR, STAR_X, STAR_Y, KEY + "settings.star");
        drawCurrency(context, PartyCurrency.COIN, COIN_X, COIN_Y, KEY + "settings.coin");
        // Rounds
        int ry = ROUNDS_Y;
        context.drawText(textRenderer, Text.translatable(KEY + "settings.rounds").formatted(Formatting.BOLD), PAD, ry + 1, PartyGui.TEXT_DARK, false);
        context.drawText(textRenderer, fit(Text.translatable(data.phase() == Phase.RUNNING ? KEY + "settings.rounds.running" : KEY + "settings.rounds.hint"),
                WIDTH - 2 * PAD - 80), PAD, ry + 11, PartyGui.TEXT_SOFT, false);
        String value = Integer.toString(data.roundsSetting());
        int fieldX = WIDTH - PAD - 52, fieldW = 32;
        PartyGui.inset(context, fieldX, ry, fieldW, 18, 0xFF3B4247, false, false);
        context.drawText(textRenderer, value, fieldX + (fieldW - textRenderer.getWidth(value)) / 2 + 1, ry + 5, 0xFFFFFFFF, false);
    }

    private void drawCurrency(DrawContext context, PartyCurrency currency, int sx, int sy, String key) {
        int tx = sx + 24;
        ItemStack stack = currency(currency);
        boolean isDefault = ItemStack.areItemsAndComponentsEqual(stack, currency.defaultStack());
        Text title = Text.translatable(key).formatted(Formatting.BOLD);
        context.drawText(textRenderer, title, tx, sy - 4, currency == PartyCurrency.STAR ? COLOR_GOLD : PartyGui.TEXT_DARK, false);
        Text name = Text.empty().append(stack.getName()).append(isDefault ? Text.translatable(KEY + "settings.default") : Text.empty());
        context.drawText(textRenderer, fit(name, WIDTH - tx - PAD - textRenderer.getWidth(title) - 6), tx + textRenderer.getWidth(title) + 6, sy - 4, PartyGui.TEXT_SOFT, false);
        wrapped(context, Text.translatable(key + ".description"), tx, sy + 7, WIDTH - tx - PAD, PartyGui.TEXT_DARK);
    }

    // ------------------------------------------------------------------ tooltips and input

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawMouseoverTooltip(DrawContext context, int mouseX, int mouseY) {
        PartyDashboardData data = handler.getData();
        if (focusedSlot != null && isGhostSlot(focusedSlot.id) && handler.getCursorStack().isEmpty() && data != null) {
            PartyCurrency currency = currencyOf(focusedSlot.id);
            String key = KEY + "settings." + (currency == PartyCurrency.STAR ? "star" : "coin");
            List<Text> lines = new ArrayList<>();
            lines.add(Text.translatable(key).formatted(Formatting.GOLD));
            lines.add(focusedSlot.getStack().getName().copy().formatted(Formatting.WHITE));
            lines.add(Text.translatable(key + ".description").formatted(Formatting.GRAY));
            lines.add(Text.translatable(KEY + "settings.slot.hint").formatted(Formatting.DARK_GRAY));
            if (!data.canEdit()) lines.add(Text.translatable(KEY + "locked").formatted(Formatting.RED));
            context.drawOrderedTooltip(textRenderer, wrapTooltip(lines), mouseX, mouseY);
            return;
        }
        if (focusedSlot != null && focusedSlot.id == SLOT_CATALOGUE && !focusedSlot.hasStack() && handler.getCursorStack().isEmpty()) {
            List<Text> lines = List.of(Text.translatable(KEY + "mini_games.slot").formatted(Formatting.GOLD),
                    Text.translatable(KEY + "mini_games.none.hint").formatted(Formatting.GRAY));
            context.drawOrderedTooltip(textRenderer, wrapTooltip(lines), mouseX, mouseY);
            return;
        }
        PartyDashboardData.Page hoveredPage = pageAt(mouseX, mouseY);
        if (hoveredPage != null && handler.getCursorStack().isEmpty()) {
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
            context.drawOrderedTooltip(textRenderer, wrapTooltip(lines), mouseX, mouseY);
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
                context.drawOrderedTooltip(textRenderer, wrapTooltip(lines), mouseX, mouseY);
                return;
            }
        }
        super.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    private List<OrderedText> wrapTooltip(List<Text> lines) {
        // wrapLines also breaks at the line breaks, keeping the styles
        List<OrderedText> wrapped = new ArrayList<>();
        for (Text line : lines) wrapped.addAll(textRenderer.wrapLines(line, 220));
        return wrapped;
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
                flash(Text.translatable(KEY + "locked"));
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
            flash(Text.translatable(KEY + "mini_games.locked"));
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0 && page() == Page.PLAYERS) {
            playersScroll -= (int) Math.signum(verticalAmount);
            return true;
        }
        if (verticalAmount != 0 && page() == Page.MINI_GAMES && mouseY < y + PANEL_HEIGHT) {
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
