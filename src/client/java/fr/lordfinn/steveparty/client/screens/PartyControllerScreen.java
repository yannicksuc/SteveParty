package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.BasicGameGeneratorStep;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.client.screens.partycontroller.Dashboard;
import fr.lordfinn.steveparty.client.screens.partycontroller.DashboardPainter;
import fr.lordfinn.steveparty.client.screens.partycontroller.DashboardTabs;
import fr.lordfinn.steveparty.client.screens.partycontroller.DashboardTimeline;
import fr.lordfinn.steveparty.client.screens.partycontroller.GainsPage;
import fr.lordfinn.steveparty.client.screens.partycontroller.PlayersPage;
import fr.lordfinn.steveparty.client.screens.partycontroller.ProgramPage;
import fr.lordfinn.steveparty.client.screens.partycontroller.SettingsPage;
import fr.lordfinn.steveparty.client.screens.partycontroller.StatusPage;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.Page;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static fr.lordfinn.steveparty.client.screens.partycontroller.DashboardStyle.*;
import static fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.*;
import static fr.lordfinn.steveparty.sounds.ModSounds.CLOSE_TILE_GUI_SOUND_EVENT;
import static fr.lordfinn.steveparty.sounds.ModSounds.OPEN_TILE_GUI_SOUND_EVENT;

/**
 * The Party Controller's dashboard: the block's little computer, as its approved mock-up (the art sources
 * build_party_controller_screen_v3.py, « direction 1, v3 »). Its screen in the gold bezel, the selected tab and the
 * bezel one shape opening into it; the inventory in its own bezel below. What a tab is for is in the « i » of its top
 * right corner, what a slot or a control does in a short tooltip; a tab that blocks the party says it with its red label.
 * <ul>
 *     <li><b>Status</b> ({@link StatusPage}): before a party, a checklist of what a party needs (board, tokens on the
 *     start tiles, mini-game catalogue, bank, rounds), each line leading to the tab where it is fixed, and « Start the
 *     party » (active only when ready, else it says why); during a party, the round, a timeline of its steps (the current
 *     one framed, the past ones dimmed: wheel or drag to scroll it), what is happening now, the step and the leader; once
 *     over, the standings and « Start a new party ». « Follow » shows the party's HUDs.</li>
 *     <li><b>Players</b> ({@link PlayersPage}): the tokens in the turn order with their rank, player, stars and coins
 *     (it scrolls).</li>
 *     <li><b>Program</b> ({@link ProgramPage}): the catalogue slot (its mini-games in its tooltip), what the party will
 *     be made of, the party's program (2 rows of 12 card slots, the default party as ghost cards while it is empty, see
 *     {@link BasicGameGeneratorStep#defaultProgram}) and, under them, the timeline of what it will play.</li>
 *     <li><b>Gains</b> ({@link GainsPage}): the bank's Inventory Cartridge, the Coin and Star items above their columns
 *     (click with an item to pick it, with an empty hand to go back to the default one), what each place earns.</li>
 *     <li><b>Settings</b> ({@link SettingsPage}): the rounds, the practice round, the power-ups a player may carry,
 *     « Restrict dice » (a switch, a row of ghost slots, its toggle opening a panel of all of them over the other
 *     settings; the places after the first free one greyed, all of them while the switch is off).</li>
 * </ul>
 * Everything shown comes from the server ({@link PartyDashboardData}), every action is checked there. This screen holds
 * the frame, the tabs, the slots and the tooltips' order; each tab draws its own page.
 */
public class PartyControllerScreen extends HandledScreen<PartyControllerScreenHandler> implements Dashboard {
    private boolean openSoundPlayed;
    /** A local refusal shown on the page for a few seconds (currency slots). */
    private @Nullable Text flash;
    private long flashUntil;
    /** The data the widgets were built for: rebuilt when a new one arrives. */
    private @Nullable PartyDashboardData builtFor;

    private final DashboardPainter paint = new DashboardPainter(this);
    private final DashboardTabs tabs = new DashboardTabs(this, paint);
    private final DashboardTimeline timeline = new DashboardTimeline(this, paint);
    private final StatusPage status = new StatusPage(this, paint, timeline);
    private final PlayersPage players = new PlayersPage(paint);
    private final ProgramPage program = new ProgramPage(this, paint, timeline);
    private final GainsPage gains = new GainsPage(this, paint);
    private final SettingsPage settings = new SettingsPage(this, paint);

    public PartyControllerScreen(PartyControllerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = WIDTH;
        this.backgroundHeight = INVENTORY_Y + INVENTORY_PANEL_HEIGHT;
    }

    // ------------------------------------------------------------------ the dashboard, as its pages see it

    @Override
    public PartyControllerScreenHandler handler() {
        return handler;
    }

    @Override
    public @Nullable PartyDashboardData data() {
        return handler.getData();
    }

    @Override
    public Page page() {
        return handler.getPage();
    }

    @Override
    public TextRenderer font() {
        return textRenderer;
    }

    @Override
    public int left() {
        return x;
    }

    @Override
    public int top() {
        return y;
    }

    @Override
    public <T extends Element & Drawable & Selectable> T add(T widget) {
        return addDrawableChild(widget);
    }

    @Override
    public void click(int button, int times) {
        if (client == null || client.interactionManager == null) return;
        for (int i = 0; i < times; i++) client.interactionManager.clickButton(handler.syncId, button);
    }

    @Override
    public void rebuild() {
        clearAndInit();
    }

    @Override
    public void showPage(Page page) {
        if (page == page()) return;
        handler.setPage(page);
        handler.setDiceOpen(false);
        players.resetScroll();
        clearAndInit();
    }

    // ------------------------------------------------------------------ widgets

    @Override
    protected void init() {
        super.init();
        // The tabs above the panel: the whole dashboard centred
        y = Math.max(TABS_HEIGHT, (height - backgroundHeight - TABS_HEIGHT) / 2 + TABS_HEIGHT);
        builtFor = data();
        tabs.addButtons();
        PartyDashboardData data = data();
        if (data != null) {
            switch (page()) {
                case STATE -> status.addButtons(data);
                case PLAYERS, PROGRAM -> {}
                case GAINS -> gains.addButtons(data);
                case SETTINGS -> settings.addButtons(data);
            }
        }
        if (!openSoundPlayed && client != null && client.player != null)
            client.player.playSound(OPEN_TILE_GUI_SOUND_EVENT, 1.0F, 1.0F);
        openSoundPlayed = true;
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        // New state from the server: the buttons follow (enabled, labels, tooltips); the stop confirmation runs out
        boolean confirmOver = status.tick();
        if (data() != builtFor || confirmOver) clearAndInit();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // The tabs at rest, then the panel and the selected tab as one shape, the inventory in its own bezel
        tabs.drawIdle(context, mouseX, mouseY);
        ConsolePaint.tabbedBezel(context, x, y - TABS_HEIGHT, WIDTH, TABS_HEIGHT, PANEL_HEIGHT, tabs.left(page()), tabs.width(page()), BEZEL, FRAME,
                SCREEN, SCREEN_EDGE);
        ConsolePaint.bezel(context, x, y + INVENTORY_Y, WIDTH, INVENTORY_PANEL_HEIGHT, BEZEL, FRAME, SCREEN, SCREEN_EDGE);
        tabs.drawLabels(context);
        if (page() == Page.SETTINGS) settings.drawPanel(context);
        for (Slot slot : handler.slots) {
            if (!slot.isEnabled()) continue;
            // The dice places after the first free one are greyed (not usable yet)
            boolean off = isDiceSlot(slot.id) && !handler.isDiceSlotUsable(slot.id);
            ConsolePaint.inset(context, x + slot.x - 1, y + slot.y - 1, 17, 17, off ? SLOT_OFF_BODY : SLOT_BODY, SLOT_EDGE, off ? SLOT_OFF_LOW : SLOT_LOW);
        }
        if (page() == Page.SETTINGS) settings.drawFreeDie(context);
        // The empty catalogue slot shows, faded, the item it takes (the currency slots are never empty)
        ghostItem(context, handler.getSlot(SLOT_CATALOGUE), ModItems.MINI_GAMES_CATALOGUE);
    }

    private void ghostItem(DrawContext context, Slot slot, Item item) {
        if (!slot.isEnabled() || slot.hasStack()) return;
        paint.ghost(context, new ItemStack(item), x + slot.x, y + slot.y, false);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        PartyDashboardData data = data();
        if (data == null) {
            UiText.centered(context, textRenderer, Text.translatable(KEY + "loading"), CX, PANEL_HEIGHT / 2 - 4, CW, INK_SOFT, true);
            return;
        }
        int mx = mouseX - x, my = mouseY - y;
        switch (page()) {
            case STATE -> status.draw(context, data, mx, my);
            case PLAYERS -> players.draw(context, data, mx, my);
            case PROGRAM -> program.draw(context, data, mx, my);
            case GAINS -> gains.draw(context, data);
            case SETTINGS -> settings.draw(context, data);
        }
        ConsolePaint.infoButton(context, INFO_X, infoY());
        if (flash != null && Util.getMeasuringTimeMs() < flashUntil) {
            // As wide as its text, at most the content's width (longer, it scrolls in it)
            int w = Math.min(textRenderer.getWidth(flash) + 8, CW);
            int fx = (WIDTH - w) / 2, fy = CONTENT_BOTTOM - 14;
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 300);
            ConsolePaint.box(context, fx, fy, w, 14, FLASH, 1, 1);
            UiText.line(context, textRenderer, flash, fx + 4, fy + 3, w - 8, WHITE, true);
            context.getMatrices().pop();
        }
    }

    /** The « i » of the tab: on its header row, or centred on its slot's row (Program, Gains). */
    private int infoY() {
        return page() == Page.PROGRAM || page() == Page.GAINS ? CY + 3 : CY;
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawMouseoverTooltip(DrawContext context, int mouseX, int mouseY) {
        // A step of a timeline
        Text step = timeline.takeHoveredStep();
        if (step != null && handler.getCursorStack().isEmpty() && timeline.isOver(mouseX, mouseY)) {
            context.drawTooltip(textRenderer, step, mouseX, mouseY);
            return;
        }
        List<Text> lines = tooltip(mouseX, mouseY);
        if (lines != null) paint.tooltip(context, lines, mouseX, mouseY);
        else super.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    /** What the mouse is over says (a timeline's step aside), the first that applies; null for the slots' own tooltips. */
    private @Nullable List<Text> tooltip(int mouseX, int mouseY) {
        PartyDashboardData data = data();
        int mx = mouseX - x, my = mouseY - y;
        boolean emptyHand = handler.getCursorStack().isEmpty();
        if (data == null) return null;
        // The « i » of the page: what it is for and how it is used
        if (HitArea.contains(mx, my, INFO_X, infoY(), ROW_H, ROW_H)) {
            return List.of(DashboardTabs.name(page()).copy().formatted(Formatting.GOLD),
                    Text.translatable(KEY + "info." + DashboardTabs.key(page())).formatted(Formatting.GRAY));
        }
        Slot slot = focusedSlot;
        if (slot != null && isGhostSlot(slot.id) && emptyHand) return gains.currencyTooltip(data, slot);
        List<Text> lines = null;
        if (page() == Page.GAINS && emptyHand) lines = gains.bankTooltip(data, slot, mx, my);
        if (lines == null && page() == Page.PROGRAM && emptyHand) lines = program.catalogueTooltip(data, slot, mx, my);
        if (lines != null) return lines;
        if (slot != null && isDiceSlot(slot.id) && emptyHand)
            return settings.diceTooltip(data, slot, slot.hasStack() ? getTooltipFromItem(slot.getStack()) : List.of());
        if (slot != null && isProgramSlot(slot.id) && !slot.hasStack() && emptyHand) return program.slotTooltip(data, slot);
        if (page() == Page.STATE && (lines = status.tooltip(data, mouseX, mouseY)) != null) return lines;
        // The label of a setting: what it does
        if (page() == Page.SETTINGS && emptyHand) return settings.labelTooltip(mx, my);
        return null;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PartyDashboardData data = data();
        if (data != null && button == 0 && page() == Page.STATE) {
            Page target = status.clicked(data, mouseX, mouseY);
            if (target != null) {
                if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                showPage(target);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void onMouseClick(@Nullable Slot slot, int slotId, int button, SlotActionType actionType) {
        Text refusal = slot == null ? null
                : isGhostSlot(slot.id) ? gains.refusal(data(), slot, actionType)
                : isDiceSlot(slot.id) ? settings.refusal(data(), slot, actionType)
                : slot.id == SLOT_CATALOGUE && slot.hasStack() && handler.isCatalogueLocked() ? Text.translatable(KEY + "program.catalogue.locked")
                : null;
        if (refusal != null) {
            // Empty: refused without a word (not a plain click)
            if (!refusal.getString().isEmpty()) flash(refusal);
            return;
        }
        super.onMouseClick(slot, slotId, button, actionType);
    }

    /** A local refusal, said at once on the page (the server checks again). */
    private void flash(Text text) {
        flash = text;
        flashUntil = Util.getMeasuringTimeMs() + 2500;
        if (client != null)
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8F));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // A timeline is dragged like a strip: one chip at a time
        if (button == 0 && handler.getCursorStack().isEmpty() && timeline.isOver(mouseX, mouseY)) {
            timeline.drag(deltaX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0 && timeline.isOver(mouseX, mouseY)) {
            timeline.scroll(-(int) Math.signum(verticalAmount));
            return true;
        }
        if (verticalAmount != 0 && page() == Page.PLAYERS && mouseY < y + PANEL_HEIGHT) {
            players.scroll(verticalAmount);
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
