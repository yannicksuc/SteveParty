package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.gui.ConsoleButton;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.FormatChips;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.client.gui.paint.Ramp;
import fr.lordfinn.steveparty.client.gui.party.MiniGamePracticeHud;
import fr.lordfinn.steveparty.client.minigame.MiniGamePageClient;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.screen_handlers.custom.MiniGameControllerScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.MiniGameControllerScreenHandler.State;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static fr.lordfinn.steveparty.screen_handlers.custom.MiniGameControllerScreenHandler.*;

/**
 * The Mini-game Controller's screen, as its approved mock-up (the art sources, « direction
 * C, v2 »): the block's monitor (the page's picture, its title, its way to play and players, what the mini-game is doing
 * or why it can't be played) on a cloud console, the console's two rows (the page slot and, read only, the page's
 * « Mode aventure »; the page's zone, its pictogram filled when the arena is put back after each round, and the
 * button: ▶ Play, ■ Stop, ✔ Ready), the inventory in its own console panel below. The page's options are set in its
 * editor.
 */
public class MiniGameControllerScreen extends HandledScreen<MiniGameControllerScreenHandler> {
    private static final String KEY = "gui.steveparty.mini_game_controller.";
    // The grid of the mock-up (GUI px, from the screen's corner)
    private static final int BEZEL = 4, EDGE = 10, CX = EDGE, CY = EDGE, CW = WIDTH - 2 * EDGE;
    private static final int MONITOR_Y = CY + 12, MONITOR_H = BEZEL + 4 + 47 + 4 + BEZEL;
    private static final int SX = CX + BEZEL + 4, SY = MONITOR_Y + BEZEL + 4, SW = CW - 2 * (BEZEL + 4);
    private static final int TX = SX + 82 + 6, TW = SW - 88;
    private static final int BUTTON_W = 80, BUTTON_H = 18;
    private static final int LABEL_X = CX + 22;

    // The colours of the mock-up
    private static final Ramp CLOUD = Ramp.of(0x5a6f9a, 0xffffff, 0xecf7fe, 0xc6d8f0);
    private static final Ramp MONITOR = Ramp.of(0x354a55, 0xffffff, 0xecf7fe, 0xcad5da);
    private static final Ramp GOLD = Ramp.of(0x5b2e00, 0xffe3a3, 0xffc600, 0xcc8400);
    private static final Ramp SWITCH_ON = Ramp.of(0x08270a, 0xa6ef8a, 0x46ae2e, 0x1f6a14);
    private static final Ramp SWITCH_OFF = Ramp.of(0x5a6f9a, 0xffffff, 0xc6d8f0, 0x9eb4d6);
    private static final Ramp KNOB = Ramp.of(0x2f3a55, 0xffffff, 0xffffff, 0xdfe6ea);
    private static final int SURFACE = 0xFFF4FAFF, SURFACE_EDGE = 0xFFDBE9F8;
    private static final int SCREEN = 0xFF14181B, SCREEN_EDGE = 0xFF0B0D10;
    private static final int SLOT_BODY = 0xFFC6D8F0, SLOT_EDGE = 0xFF5A6F9A, SLOT_LOW = 0xFFFFFFFF;
    private static final int INK = 0xFF2F3A55, INK_GHOST = 0xFF7A8AA8, INK_RED = 0xFFB3202A;
    private static final int SCREEN_TITLE = 0xFFFFD24A, SCREEN_SOFT = 0xFF9E9CC8;
    private static final int WAVE = 0xFFC6D8F0, WAVE_LOW = 0xFFADC2DE;

    /** What the widgets were built for: rebuilt when it changes. */
    private @Nullable Object builtFor;

    public MiniGameControllerScreen(MiniGameControllerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = WIDTH;
        this.backgroundHeight = INVENTORY_Y + INVENTORY_PANEL_HEIGHT;
    }

    private Object signature() {
        return List.of(state(), handler.isVoter(), handler.isReady(), handler.readyCount(), handler.voters(), handler.players(), handler.format(), Arrays.toString(handler.shortfall()),
                handler.isLocked(), handler.forbiddenPos());
    }

    private State state() {
        return handler.state();
    }

    private @Nullable MiniGamePageData page() {
        UUID id = MiniGamePages.idOf(handler.getSlot(SLOT_PAGE).getStack());
        return id == null ? null : MiniGamePageClient.page(id);
    }

    @Override
    protected void init() {
        super.init();
        builtFor = signature();
        State state = state();
        // Row 2: the button, its colour and icon saying the state
        int bx = x + CX + CW - BUTTON_W, by = y + ROW2_Y;
        if (state == State.PARTY_PRACTICE) {
            // The vote of the practice round: the same as the key
            boolean ready = handler.isReady();
            ConsoleButton button = addDrawableChild(new ConsoleButton(bx, by, BUTTON_W, BUTTON_H,
                    Text.translatable(KEY + "ready", handler.readyCount(), handler.voters()),
                    ready ? ConsoleButton.Kind.GOLD : ConsoleButton.Kind.NEUTRAL, ready ? ConsoleButton.Icon.CHECK : null, () -> click(BUTTON_READY)));
            button.active = handler.isVoter();
            button.setTooltip(Tooltip.of(handler.isVoter()
                    ? Text.translatable(KEY + "ready.tooltip", MiniGamePracticeHud.keyText())
                    : Text.translatable(KEY + "ready.tooltip.watching").formatted(Formatting.RED)));
        } else if (state != State.PARTY_PLAYING) {
            boolean running = state == State.RUNNING;
            ConsoleButton button = addDrawableChild(new ConsoleButton(bx, by, BUTTON_W, BUTTON_H, Text.translatable(KEY + (running ? "stop" : "play")),
                    running ? ConsoleButton.Kind.RED : ConsoleButton.Kind.GREEN, running ? ConsoleButton.Icon.STOP : ConsoleButton.Icon.PLAY, () -> {
                click(BUTTON_PLAY);
                if (!running) close();
            }));
            button.active = running || state == State.READY;
            button.setTooltip(Tooltip.of(running ? Text.translatable(KEY + "stop.tooltip")
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
            case READY -> Text.translatable(KEY + "status.ready", handler.players(), formatName(handler.format()));
            case NOT_ENOUGH -> {
                MiniGamePageData data = page();
                int[] shortfall = handler.shortfall();
                yield data == null || shortfall == null ? Text.translatable(KEY + "status.not_enough")
                        : FormatChips.shortfallText(data, shortfall);
            }
            case PARTY_PRACTICE -> Text.translatable(KEY + "status.party_practice", handler.readyCount(), handler.voters());
            case ZONE_FORBIDDEN -> Text.translatable(KEY + "status.zone_forbidden", handler.forbiddenBlock().getName(),
                    handler.forbiddenPos().getX(), handler.forbiddenPos().getY(), handler.forbiddenPos().getZ());
            default -> Text.translatable(KEY + "status." + state.name().toLowerCase(Locale.ROOT));
        };
    }

    private Text formatName(int index) {
        MiniGamePageData data = page();
        MiniGameFormat format = data == null ? null : data.format(index);
        return format == null ? Text.empty() : format.name();
    }

    /** The chips shown on the monitor this frame: {x, y, w, index}, for their tooltips. */
    private final List<int[]> chipsShown = new ArrayList<>();

    /** The status line's colour, on the monitor's dark screen. */
    private static int statusColor(State state) {
        return switch (state) {
            case READY -> 0xFFFFFFFF;
            case RUNNING -> 0xFF8FE07A;
            case PARTY_PRACTICE, PARTY_PLAYING -> 0xFFFFB54A;
            case NO_PAGE -> SCREEN_SOFT;
            default -> 0xFFFF8F8F;
        };
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
        // An empty slot says what it takes
        if (focusedSlot != null && !focusedSlot.hasStack() && handler.getCursorStack().isEmpty()
                && focusedSlot.id == SLOT_PAGE) {
            context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(Text.translatable(KEY + "slot.page"), 180), mouseX, mouseY);
        } else if (chipAt(mouseX, mouseY) >= 0 && page() != null) {
            // A format chip: its name, what it means
            MiniGameFormat format = page().format(chipAt(mouseX, mouseY));
            if (format != null) context.drawTooltip(textRenderer, List.of(format.name(), format.meaning().formatted(Formatting.GRAY)), mouseX, mouseY);
        } else {
            // The zone's line: whole, and where it is drawn
            if (HitArea.contains(mouseX, mouseY, x + CX, y + ROW2_Y, CW - BUTTON_W - 4, 18)) {
                MiniGamePageData data = page();
                boolean restore = data != null && data.restores(), adventure = data != null && data.adventure();
                UiText.cancelTooltip();
                context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(Text.empty().append(zoneText()).append("\n")
                        .append(Text.translatable(KEY + "zone.tooltip").formatted(Formatting.GRAY)).append("\n")
                        .append(Text.translatable(KEY + (restore ? "restore.on" : "restore.off")).formatted(restore ? Formatting.GREEN : Formatting.GRAY)).append("\n")
                        .append(Text.translatable(KEY + (adventure ? "adventure.on" : "adventure.off")).formatted(adventure ? Formatting.GREEN : Formatting.GRAY)),
                        200), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // The cloud console, then the inventory in its own console panel
        ConsolePaint.bezel(context, x, y, WIDTH, PANEL_HEIGHT, BEZEL, CLOUD, SURFACE, SURFACE_EDGE);
        ConsolePaint.bezel(context, x, y + INVENTORY_Y, WIDTH, INVENTORY_PANEL_HEIGHT, BEZEL, CLOUD, SURFACE, SURFACE_EDGE);
        // Wavelets only in the band under the last row: nothing else lives there
        int bandY = y + PANEL_HEIGHT - BEZEL - 4;
        int i = 0;
        for (int wx = x + EDGE + 4; wx < x + WIDTH - EDGE - 8; wx += 14, i++) {
            int left = wx + (i % 2) * 3;
            context.fill(left, bandY, left + 1, bandY + 1, WAVE);
            context.fill(left + 5, bandY, left + 6, bandY + 1, WAVE);
            context.fill(left + 1, bandY + 1, left + 5, bandY + 2, WAVE_LOW);
        }
        // The monitor: a white frame with gold corners round its dark screen
        ConsolePaint.bezel(context, x + CX, y + MONITOR_Y, CW, MONITOR_H, BEZEL, MONITOR, SCREEN, SCREEN_EDGE);
        for (int[] corner : new int[][]{{CX, MONITOR_Y}, {CX + CW - 6, MONITOR_Y}, {CX, MONITOR_Y + MONITOR_H - 6}, {CX + CW - 6, MONITOR_Y + MONITOR_H - 6}}) {
            ConsolePaint.box(context, x + corner[0], y + corner[1], 6, 6, GOLD, 1, 1);
        }
        ConsolePaint.inset(context, x + SX, y + SY, 81, 46, SCREEN, SCREEN_EDGE, SLOT_EDGE);
        // Every slot: the console's
        for (Slot slot : handler.slots) ConsolePaint.inset(context, x + slot.x - 1, y + slot.y - 1, 17, 17, SLOT_BODY, SLOT_EDGE, SLOT_LOW);
        // Its two own slots show, faded, the item they take while they are empty
        ghost(context, SLOT_PAGE, ModItems.MINI_GAME_PAGE);
        // Row 2: the zone's pictogram where row 1 has its slot, so that both labels line up
        MiniGamePageData shown = page();
        zonePictogram(context, x + PAGE_X - 1, y + ROW2_Y, handler.zoneSize()[0] > 0, shown != null && shown.restores());
        drawMonitor(context);
    }

    /** A box drawn by its corners (17 x 17, like a slot): ink when the page has a zone, faded when it has none. */
    private static void zonePictogram(DrawContext context, int left, int top, boolean hasZone, boolean restored) {
        int ink = hasZone ? INK : INK_GHOST, size = 17, arm = 5;
        for (int[] corner : new int[][]{{0, 0, 1, 1}, {size - 1, 0, -1, 1}, {0, size - 1, 1, -1}, {size - 1, size - 1, -1, -1}}) {
            int cx = left + corner[0], cy = top + corner[1];
            int x0 = Math.min(cx, cx + corner[2] * (arm - 1)), y0 = Math.min(cy, cy + corner[3] * (arm - 1));
            context.fill(x0, cy, x0 + arm, cy + 1, ink);
            context.fill(cx, y0, cx + 1, y0 + arm, ink);
        }
        if (hasZone && restored) context.fill(left + 4, top + 4, left + size - 4, top + size - 4, 0x5546AE2E);
    }

    private void ghost(DrawContext context, int index, Item item) {
        Slot slot = handler.getSlot(index);
        if (slot.hasStack()) return;
        PartyGui.ghostItem(context, new ItemStack(item), x + slot.x, y + slot.y, null, 0xA6000000 | (SLOT_BODY & 0xFFFFFF));
    }

    /** The monitor's screen: the page's picture and what it says, or how to give the controller a page. */
    private void drawMonitor(DrawContext context) {
        ItemStack stack = handler.getSlot(SLOT_PAGE).getStack();
        int px = x + SX + 1, py = y + SY + 1, tx = x + TX, ty = y + SY;
        chipsShown.clear();
        if (stack.isEmpty()) {
            context.drawItem(new ItemStack(ModItems.MINI_GAME_PAGE), px + 32, py + 15);
            PartyGui.veil(context, px + 32, py + 15, 0x80000000 | (SCREEN & 0xFFFFFF));
            List<OrderedText> lines = monitorLines(Text.translatable(KEY + "card.empty"), 5);
            for (int i = 0; i < lines.size(); i++) UiText.line(context, textRenderer, lines.get(i), tx, ty + i * 9, TW + 1, SCREEN_SOFT, true);
            return;
        }
        MiniGamePageData data = page();
        MiniGamePageClient.Picture picture = data == null ? null : MiniGamePageClient.picture(data.image(), 80, 45);
        if (picture != null) picture.draw(context, px, py, 80, 45, 0xFFFFFFFF);
        else context.drawItem(stack, px + 32, py + 15);
        // The page's title
        Text name = data != null && data.hasTitle() ? Text.literal(data.title()) : stack.getName();
        UiText.line(context, textRenderer, name, tx, ty, TW + 1, SCREEN_TITLE, true);
        // What the mini-game is doing, or why it can't be played: never cut (over the mode's line when it needs it)
        State state = state();
        Text status = statusText(state);
        List<OrderedText> lines = monitorLines(status, 4);
        boolean modeRow = data != null && lines.size() <= 2;
        if (modeRow) {
            // Its formats (pawn chips, the one played gold rimmed, a « ! » on those without their pipes), on one row
            int left = tx;
            for (int i = 0; i < data.formats().size(); i++) {
                MiniGameFormat format = data.formats().get(i);
                FormatChips.Look look =
                        new FormatChips.Look(false, i == handler.format(), !data.hasPipesFor(format), false, 13);
                int w = FormatChips.width(textRenderer, format, look);
                if (left + w > tx + TW) break;
                FormatChips.draw(context, textRenderer, format, look, left, ty + 10);
                chipsShown.add(new int[]{left, ty + 10, w, i});
                left += w + 3;
            }
            for (int i = 0; i < lines.size(); i++) UiText.line(context, textRenderer, lines.get(i), tx, ty + 27 + i * 10, TW + 1, statusColor(state), true);
        } else {
            // A long reason: under the title, on up to four lines
            int step = lines.size() > 3 ? 9 : 10;
            for (int i = 0; i < lines.size(); i++) UiText.line(context, textRenderer, lines.get(i), tx, ty + 10 + i * step, TW + 1, statusColor(state), true);
        }
    }

    /**
     * {@code text} on the monitor's lines ({@code TW} pixels, its last pixel column), at most {@code max}: what does not
     * fit goes on the last one, which then scrolls.
     */
    private List<OrderedText> monitorLines(Text text, int max) {
        List<OrderedText> lines = UiText.wrap(textRenderer, text, TW + 1);
        if (lines.size() <= max) return lines;
        List<OrderedText> shown = new ArrayList<>(lines.subList(0, max - 1)), rest = new ArrayList<>();
        for (OrderedText line : lines.subList(max - 1, lines.size())) {
            if (!rest.isEmpty()) rest.add(OrderedText.styledForwardsVisitedString(" ", Style.EMPTY));
            rest.add(line);
        }
        shown.add(OrderedText.concat(rest));
        return shown;
    }

    private int chipAt(int mouseX, int mouseY) {
        for (int[] chip : chipsShown) {
            if (HitArea.contains(mouseX, mouseY, chip[0], chip[1], chip[2], 13)) return chip[3];
        }
        return -1;
    }

    private Text zoneText() {
        int[] zone = handler.zoneSize();
        return zone[0] <= 0 ? Text.translatable(KEY + "zone.none") : Text.translatable(KEY + "zone", zone[0], zone[1], zone[2]);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // The title, dark with a light shadow
        dark(context, title, CX, CY, CW + 1, INK);
        // Row 1: the page's « Mode aventure », read only (set in its editor), right-aligned on at most half the row
        MiniGamePageData data = page();
        int labelEnd = CX + CW + 1;
        if (data != null && data.adventure()) {
            Text adventure = Text.translatable(KEY + "adventure");
            int w = Math.min(textRenderer.getWidth(adventure), (CX + CW + 1 - LABEL_X) / 2);
            dark(context, adventure, CX + CW + 1 - w, ROW1_Y + 5, w, INK);
            labelEnd = CX + CW + 1 - w - 4;
        }
        // The page slot's label, before it
        boolean hasPage = handler.getSlot(SLOT_PAGE).hasStack();
        dark(context, Text.translatable(KEY + "label.page"), LABEL_X, ROW1_Y + 5, labelEnd - LABEL_X, hasPage ? INK : INK_GHOST);
        // Row 2: the zone, before the button
        int room = CX + CW - BUTTON_W - 4 - LABEL_X;
        dark(context, zoneText(), LABEL_X, ROW2_Y + 5, room + 1, handler.zoneSize()[0] <= 0 ? INK_GHOST : INK);
        if (state() == State.PARTY_PLAYING) {
            // No button while a party plays it: what it is doing, where the button would be
            Text playing = Text.translatable(KEY + "party.playing");
            int w = Math.min(textRenderer.getWidth(playing), BUTTON_W + 1);
            dark(context, playing, CX + CW + 1 - w, ROW2_Y + 5, w, INK_GHOST);
        }
    }

    /** Dark text with a light shadow (the mock-ups' {@code dark}), in a box {@code width} wide. */
    private void dark(DrawContext context, Text text, int tx, int ty, int width, int colour) {
        ConsolePaint.darkText(context, textRenderer, text.asOrderedText(), tx, ty, width, colour, 0xFFFFFFFF);
    }
}
