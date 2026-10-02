package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.gui.MiniGamePageTooltipComponent;
import fr.lordfinn.steveparty.client.gui.PartyButton;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.party.MiniGamePracticeHud;
import fr.lordfinn.steveparty.client.minigame.MiniGamePageClient;
import fr.lordfinn.steveparty.minigame.MiniGameMode;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.screen_handlers.custom.MiniGameControllerScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.MiniGameControllerScreenHandler.State;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static fr.lordfinn.steveparty.screen_handlers.custom.MiniGameControllerScreenHandler.*;

/**
 * The Mini-game Controller's screen, in the mod's GUI style ({@link PartyGui}): the card of the page it holds
 * (picture, title, ways to play, players), its two slots (the page, the Zone Cartridge) each with what it means for
 * the mini-game (whether it can be played now and with whom, the zone), and its button: « Play » / « Stop » out of a
 * party, « Ready » during a party's practice round.
 */
public class MiniGameControllerScreen extends HandledScreen<MiniGameControllerScreenHandler> {
    private static final String KEY = "gui.steveparty.mini_game_controller.";
    private static final int INVENTORY_PANEL_HEIGHT = 98;
    private static final int PAD = 10;
    private static final int PICTURE_X = PAD, PICTURE_Y = 20, PICTURE_WIDTH = 80, PICTURE_HEIGHT = 45;
    private static final int TEXT_X = PAGE_X + 23;
    private static final int BUTTON_Y = PANEL_HEIGHT - 24, BUTTON_WIDTH = 80;

    /** What the button was built for: rebuilt when it changes. */
    private @Nullable Object builtFor;

    public MiniGameControllerScreen(MiniGameControllerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = WIDTH;
        this.backgroundHeight = INVENTORY_Y + INVENTORY_PANEL_HEIGHT;
    }

    private Object signature() {
        return List.of(handler.state(), handler.isVoter(), handler.isReady(), handler.readyCount(), handler.voters(), handler.players(), handler.mode(),
                handler.isAdventure(), handler.isLocked());
    }

    private @Nullable MiniGamePageData page() {
        UUID id = MiniGamePages.idOf(handler.getSlot(SLOT_PAGE).getStack());
        return id == null ? null : MiniGamePageClient.page(id);
    }

    @Override
    protected void init() {
        super.init();
        builtFor = signature();
        State state = handler.state();
        int bx = x + WIDTH - PAD - BUTTON_WIDTH, by = y + BUTTON_Y;
        // The option of the zone: its players play in adventure mode
        PartyButton adventure = addDrawableChild(new PartyButton(bx, y + ZONE_Y - 1, BUTTON_WIDTH, 18, Text.translatable(KEY + "adventure"),
                b -> click(BUTTON_ADVENTURE)));
        adventure.setSelected(handler.isAdventure());
        adventure.active = !handler.isLocked() && client != null && client.player != null && MiniGamePages.canEdit(client.player);
        adventure.setTooltip(Tooltip.of(Text.translatable(KEY + "adventure.tooltip")));
        if (state == State.PARTY_PRACTICE) {
            // The vote of the practice round: the same as the key
            PartyButton ready = addDrawableChild(new PartyButton(bx, by, BUTTON_WIDTH, 18,
                    Text.translatable(KEY + "ready", handler.readyCount(), handler.voters()), b -> click(BUTTON_READY)));
            ready.setSelected(handler.isReady());
            ready.active = handler.isVoter();
            ready.setTooltip(Tooltip.of(handler.isVoter()
                    ? Text.translatable(KEY + "ready.tooltip", MiniGamePracticeHud.keyText())
                    : Text.translatable(KEY + "ready.tooltip.watching").formatted(Formatting.RED)));
        } else if (state != State.PARTY_PLAYING) {
            boolean running = state == State.RUNNING;
            PartyButton play = addDrawableChild(new PartyButton(bx, by, BUTTON_WIDTH, 18, Text.translatable(KEY + (running ? "stop" : "play")), b -> {
                click(BUTTON_PLAY);
                if (!running) close();
            }));
            if (!running) play.style(PartyButton.Style.PRIMARY);
            play.active = running || state == State.READY;
            play.setTooltip(Tooltip.of(running ? Text.translatable(KEY + "stop.tooltip")
                    : state == State.READY ? Text.translatable(KEY + "play.tooltip")
                    : statusText(state).copy().formatted(Formatting.RED)));
        }
    }

    private void click(int button) {
        if (client != null && client.interactionManager != null) client.interactionManager.clickButton(handler.syncId, button);
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        if (!signature().equals(builtFor)) clearAndInit();
    }

    /** What the mini-game is doing, or why it can't be played now. */
    private Text statusText(State state) {
        return switch (state) {
            case READY -> {
                MiniGameMode[] ways = MiniGameMode.values();
                int mode = handler.mode();
                yield Text.translatable(KEY + "status.ready", handler.players(), mode >= 0 && mode < ways.length ? ways[mode].text() : Text.empty());
            }
            case PARTY_PRACTICE -> Text.translatable(KEY + "status.party_practice", handler.readyCount(), handler.voters());
            default -> Text.translatable(KEY + "status." + state.name().toLowerCase(Locale.ROOT));
        };
    }

    private static int statusColor(State state) {
        return switch (state) {
            case READY -> PartyGui.TEXT_OK;
            case NO_PIPE, NOBODY, NOT_ENOUGH, ZONE_TOO_BIG, ZONE_BUSY, ZONE_NO_WORLD, ZONE_TOO_FULL -> PartyGui.TEXT_ERROR;
            case NO_PAGE -> PartyGui.TEXT_SOFT;
            default -> PartyGui.TEXT_DARK;
        };
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        PartyGui.panel(context, x, y, WIDTH, PANEL_HEIGHT, PartyGui.PANEL);
        PartyGui.panel(context, x, y + INVENTORY_Y, WIDTH, INVENTORY_PANEL_HEIGHT, PartyGui.PANEL);
        context.drawText(textRenderer, playerInventoryTitle, x + (WIDTH - 162) / 2 + 1, y + INVENTORY_Y + 4, PartyGui.TEXT_DARK, false);
        for (Slot slot : handler.slots) PartyGui.inset(context, x + slot.x - 1, y + slot.y - 1, 18, 18, 0xFF8B8B8B, false, false);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title.copy().formatted(Formatting.BOLD), PAD, 9, PartyGui.TEXT_DARK, false);
        drawCard(context);
        // The page slot: what its mini-game is doing
        State state = handler.state();
        wrapped(context, statusText(state), TEXT_X, PAGE_Y + (textRenderer.getWidth(statusText(state)) > WIDTH - TEXT_X - PAD ? -1 : 4),
                WIDTH - TEXT_X - PAD, 2, statusColor(state));
        // The cartridge slot: the zone
        int[] zone = handler.zoneSize();
        boolean hasCartridge = handler.getSlot(SLOT_ZONE).hasStack();
        boolean tooBig = Math.max(zone[0], Math.max(zone[1], zone[2])) > fr.lordfinn.steveparty.minigame.PageZone.MAX_SIDE;
        Text zoneText = zone[0] <= 0 ? Text.translatable(KEY + (hasCartridge ? "zone.empty" : "zone.none"))
                : tooBig ? Text.translatable(KEY + "zone.too_big", zone[0], zone[1], zone[2], fr.lordfinn.steveparty.minigame.PageZone.MAX_SIDE)
                : Text.translatable(KEY + "zone", zone[0], zone[1], zone[2]);
        context.drawText(textRenderer, fit(zoneText, WIDTH - TEXT_X - PAD - BUTTON_WIDTH - 6), TEXT_X, ZONE_Y + 4,
                tooBig ? PartyGui.TEXT_ERROR : zone[0] <= 0 ? PartyGui.TEXT_SOFT : PartyGui.TEXT_DARK, false);
        if (state == State.PARTY_PLAYING || state == State.PARTY_PRACTICE) {
            Text party = Text.translatable(KEY + (state == State.PARTY_PRACTICE ? "party.practice" : "party.playing"));
            context.drawText(textRenderer, fit(party, WIDTH - 2 * PAD - (state == State.PARTY_PRACTICE ? BUTTON_WIDTH + 6 : 0)), PAD, BUTTON_Y + 5, PartyGui.TEXT_SOFT, false);
        } else {
            context.drawText(textRenderer, fit(Text.translatable(KEY + "play.hint"), WIDTH - 2 * PAD - BUTTON_WIDTH - 6), PAD, BUTTON_Y + 5, PartyGui.TEXT_SOFT, false);
        }
    }

    /** The card of the page: its picture, its title, the ways it is played and its players. */
    private void drawCard(DrawContext context) {
        PartyGui.inset(context, PICTURE_X, PICTURE_Y, PICTURE_WIDTH + 1, PICTURE_HEIGHT + 1, 0xFF14181B, false, false);
        ItemStack stack = handler.getSlot(SLOT_PAGE).getStack();
        MiniGamePageData data = page();
        int tx = PICTURE_X + PICTURE_WIDTH + 8, width = WIDTH - tx - PAD;
        if (stack.isEmpty()) {
            wrapped(context, Text.translatable(KEY + "card.empty"), tx, PICTURE_Y + 2, width, 4, PartyGui.TEXT_SOFT);
            return;
        }
        MiniGamePageClient.Picture picture = data == null ? null : MiniGamePageClient.picture(data.image(), PICTURE_WIDTH, PICTURE_HEIGHT);
        if (picture != null) picture.draw(context, PICTURE_X + 1, PICTURE_Y + 1, PICTURE_WIDTH, PICTURE_HEIGHT, 0xFFFFFFFF);
        else context.drawItem(stack, PICTURE_X + 1 + (PICTURE_WIDTH - 16) / 2, PICTURE_Y + 1 + (PICTURE_HEIGHT - 16) / 2);
        Text name = data != null && data.hasTitle() ? Text.literal(data.title()) : stack.getName();
        int ty = PICTURE_Y + 2;
        context.drawText(textRenderer, fit(name.copy().formatted(Formatting.BOLD), width), tx, ty, PartyGui.TEXT_DARK, false);
        ty += 12;
        if (data == null) return;
        ty = wrapped(context, MiniGamePageTooltipComponent.modesText(data), tx, ty, width, 2, 0xFF8A5A00);
        context.drawText(textRenderer, fit(MiniGamePageTooltipComponent.playersText(data), width), tx, ty + 2, PartyGui.TEXT_SOFT, false);
    }

    private OrderedText fit(Text text, int width) {
        return MiniGamePageTooltipComponent.wrap(textRenderer, text, width, 1).stream().findFirst().orElse(OrderedText.EMPTY);
    }

    private int wrapped(DrawContext context, Text text, int tx, int ty, int width, int maxLines, int color) {
        for (OrderedText line : MiniGamePageTooltipComponent.wrap(textRenderer, text, width, maxLines)) {
            context.drawText(textRenderer, line, tx, ty, color, false);
            ty += 10;
        }
        return ty;
    }
}
