package fr.lordfinn.steveparty.client.screens.partycontroller;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyBank;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import fr.lordfinn.steveparty.client.gui.ConsoleButton;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.GuiItems;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static fr.lordfinn.steveparty.client.screens.partycontroller.DashboardStyle.*;
import static fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.*;

/**
 * The Storage tab: the party's storage, where everything it pays comes from and everything it takes goes. At the left
 * the controller's own storage, real slots, {@link PartyControllerScreenHandler#STORAGE_ROWS} rows at a time (the wheel
 * or the thin bar at their right scrolls them); at the right the « Infinite storage » switch (creative mode or operator
 * only), what it holds in the party's currencies (its linked containers too), whether that pays a whole mini-game, the
 * toggle of the list of the containers linked with the Tile Linker Brush (open, it takes the place of the slots: each
 * container, where it is, whether it is there) and how many of its own slots are used.
 */
public final class StoragePage {
    /** The grid of the slots (their insets), the scroll bar at its right. */
    private static final int GRID_X = STORAGE_X - 1, GRID_Y = STORAGE_Y - 1, GRID_W = STORAGE_COLUMNS * 18, GRID_H = STORAGE_ROWS * 18;
    private static final int BAR_X = GRID_X + GRID_W + 3, BAR_W = 3;
    /** The column at the right: the switch (on the « i »'s row), the counts, the state, the list's toggle, the slots used. */
    private static final int SIDE_X = BAR_X + BAR_W + 6, SIDE_W = CX + CW - SIDE_X;
    private static final int SWITCH_Y = CY - 3, SWITCH_W = INFO_X - 4 - SIDE_X;
    private static final int COUNTS_Y = CY + 20, STATE_Y = CY + 32, LINKED_Y = CY + 46, USED_Y = CY + 68;
    /** The list of the linked containers, over the slots and the bar: its title, its rows, the hint at its foot. */
    private static final int LIST_X = GRID_X + 4, LIST_W = BAR_X + BAR_W - GRID_X - 8, LIST_Y = GRID_Y + 18, LIST_ROW = 12, LIST_ROWS = 6;
    private static final int COLOR_SHORT = 0xFFFFB54A;

    private final Dashboard dashboard;
    private final DashboardPainter paint;
    /** The first row of the list shown. */
    private int listScroll;
    /** The scroll bar is being dragged. */
    private boolean dragging;

    public StoragePage(Dashboard dashboard, DashboardPainter paint) {
        this.dashboard = dashboard;
        this.paint = paint;
    }

    private PartyControllerScreenHandler handler() {
        return dashboard.handler();
    }

    /**
     * The containers of the storage left out of its total, « · 1 absent, 1 non chargé », empty when none is: those gone
     * (or no storage container), those whose chunk is not loaded. The others pay.
     */
    public static Text skipped(PartyBank.Status bank) {
        if (bank.absent() == 0 && bank.unloaded() == 0) return Text.empty();
        if (bank.unloaded() == 0) return Text.translatable(KEY + "bank.skipped.absent", bank.absent());
        if (bank.absent() == 0) return Text.translatable(KEY + "bank.skipped.unloaded", bank.unloaded());
        return Text.translatable(KEY + "bank.skipped.both", bank.absent(), bank.unloaded());
    }

    /** The colour the storage is said in: red when it pays nothing, orange when short or some container is skipped. */
    public static int colour(PartyBank.Status bank) {
        return switch (bank.state()) {
            case NONE, MISSING -> INK_RED;
            case OK, SHORT -> bank.state() == PartyBank.State.SHORT || !skipped(bank).getString().isEmpty() ? COLOR_SHORT : WHITE;
            case INFINITE -> WHITE;
        };
    }

    /** Back to the storage's slots, at their top (the tab is opened again). */
    public void reset() {
        listScroll = 0;
        dragging = false;
        handler().setLinkedOpen(false);
        handler().setStorageScroll(0);
    }

    // ------------------------------------------------------------------ buttons

    public void addButtons(PartyDashboardData data) {
        int x = dashboard.left(), y = dashboard.top();
        // The « Infinite storage »: greyed out for a player who is neither in creative mode nor an operator (the server checks again)
        boolean infinite = data.bank().state() == PartyBank.State.INFINITE;
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        boolean allowed = viewer != null && PartyControllerEntity.canSwitchInfiniteBank(viewer);
        DashboardSwitch unlimited = dashboard.add(new DashboardSwitch(dashboard, x + SIDE_X, y + SWITCH_Y, SWITCH_W, BTN_H,
                Text.translatable(KEY + "storage.infinite"), infinite, BUTTON_INFINITE_BANK));
        unlimited.active = data.canEdit() && allowed;
        Text state = Text.translatable(KEY + "gains.bank.infinite." + (infinite ? "on" : "off"));
        Text hint = !data.canEdit() ? Text.translatable(KEY + "locked")
                : !allowed ? Text.translatable(KEY + "gains.bank.infinite.not_allowed") : Text.translatable(KEY + "gains.bank.infinite.switch");
        unlimited.setTooltip(Tooltip.of(Text.empty().append(state).append(ScreenTexts.LINE_BREAK).append(hint)));
        // The list of the linked containers: open, it takes the place of the slots
        boolean open = handler().isLinkedOpen();
        ConsoleButton linked = dashboard.add(new ConsoleButton(x + SIDE_X, y + LINKED_Y, SIDE_W, 16,
                open ? Text.translatable(KEY + "storage.linked.close") : Text.translatable(KEY + "storage.linked.open", data.storages().size()),
                ConsoleButton.Kind.SCREEN, null, () -> {
                    handler().setLinkedOpen(!handler().isLinkedOpen());
                    listScroll = 0;
                    dashboard.rebuild();
                }));
        linked.setTooltip(Tooltip.of(Text.translatable(KEY + (open ? "storage.linked.close.hint" : "storage.linked.open.hint"))));
    }

    // ------------------------------------------------------------------ drawing

    /** Under the slots (screen coordinates): the open list, sunk into the screen where the slots were. */
    public void drawPanel(DrawContext context) {
        if (handler().isLinkedOpen())
            ConsolePaint.inset(context, dashboard.left() + GRID_X, dashboard.top() + GRID_Y, LIST_W + 8, GRID_H, SCREEN_EDGE, SLOT_EDGE, SLOT_BODY);
    }

    public void draw(DrawContext context, PartyDashboardData data, int mx, int my) {
        if (handler().isLinkedOpen()) drawList(context, data, mx, my);
        else DashboardPainter.scrollbar(context, BAR_X, GRID_Y, GRID_H, handler().getStorageScroll(), STORAGE_MAX_SCROLL, STORAGE_ROWS,
                STORAGE_MAX_SCROLL + STORAGE_ROWS);
        // What it holds in the party's currencies, over its own slots and its linked containers that are there
        PartyBank.Status bank = data.bank();
        boolean infinite = bank.state() == PartyBank.State.INFINITE;
        int half = SIDE_W / 2;
        GuiItems.scaled(context, dashboard.currency(PartyCurrency.STAR), SIDE_X, COUNTS_Y, 0.5F);
        paint.line(context, Text.literal(infinite ? "∞" : Integer.toString(bank.stars())), SIDE_X + 10, COUNTS_Y, half - 12, WHITE);
        GuiItems.scaled(context, dashboard.currency(PartyCurrency.COIN), SIDE_X + half, COUNTS_Y, 0.5F);
        paint.line(context, Text.literal(infinite ? "∞" : Integer.toString(bank.coins())), SIDE_X + half + 10, COUNTS_Y, SIDE_W - half - 10, WHITE);
        paint.line(context, Text.translatable(KEY + "storage.state." + bank.state().name().toLowerCase(Locale.ROOT)).append(skipped(bank)),
                SIDE_X, STATE_Y, SIDE_W, colour(bank));
        // Its own slots used
        int used = 0;
        for (int i = 0; i < PartyControllerEntity.BANK_SIZE; i++) if (handler().getSlot(STORAGE_FIRST_SLOT + i).hasStack()) used++;
        paint.line(context, Text.translatable(KEY + "storage.used", used, PartyControllerEntity.BANK_SIZE), SIDE_X, USED_Y, SIDE_W, INK_SOFT);
        if (!data.canEdit()) paint.line(context, Text.translatable(KEY + "read_only"), SIDE_X, USED_Y + 12, SIDE_W, INK_RED);
    }

    /** The containers linked with the Tile Linker Brush: their block, where they are (red: absent, orange: not loaded). */
    private void drawList(DrawContext context, PartyDashboardData data, int mx, int my) {
        List<PartyBank.Linked> storages = data.storages();
        paint.line(context, Text.translatable(KEY + "storage.linked.title", storages.size()), LIST_X, GRID_Y + 4, LIST_W, WHITE);
        if (storages.isEmpty()) {
            paint.line(context, Text.translatable(KEY + "storage.linked.none"), LIST_X, LIST_Y + 2, LIST_W, INK_SOFT);
        } else {
            int maxScroll = Math.max(0, storages.size() - LIST_ROWS);
            listScroll = Math.clamp(listScroll, 0, maxScroll);
            int rowWidth = LIST_W - (maxScroll > 0 ? 5 : 0);
            for (int i = listScroll; i < Math.min(storages.size(), listScroll + LIST_ROWS); i++) {
                PartyBank.Linked storage = storages.get(i);
                int ry = LIST_Y + (i - listScroll) * LIST_ROW;
                if (HitArea.contains(mx, my, LIST_X - 1, ry - 1, rowWidth + 2, LIST_ROW)) context.fill(LIST_X - 1, ry - 1, LIST_X + rowWidth + 1, ry + LIST_ROW - 1, SLOT_LOW & 0x60FFFFFF);
                ItemStack icon = new ItemStack(storage.block().asItem());
                if (!icon.isEmpty()) GuiItems.scaled(context, icon, LIST_X, ry, 0.5F);
                int colour = stateColour(storage.state());
                Text where = position(storage.pos().pos());
                int posWidth = Math.min(dashboard.font().getWidth(where), rowWidth / 2);
                paint.line(context, name(storage), LIST_X + 11, ry, rowWidth - 11 - posWidth - 4, colour);
                paint.line(context, where, LIST_X + rowWidth - posWidth, ry, posWidth, storage.state() == PartyBank.LinkState.PRESENT ? INK_SOFT : colour);
            }
            if (maxScroll > 0)
                DashboardPainter.scrollbar(context, LIST_X + LIST_W - 3, LIST_Y - 1, LIST_ROWS * LIST_ROW, listScroll, maxScroll, LIST_ROWS, storages.size());
        }
        paint.line(context, Text.translatable(KEY + "storage.linked.hint"), LIST_X, GRID_Y + GRID_H - 12, LIST_W, INK_SOFT);
    }

    private static int stateColour(PartyBank.LinkState state) {
        return switch (state) {
            case PRESENT -> WHITE;
            case ABSENT -> INK_RED;
            case UNLOADED -> INK_WARN;
        };
    }

    /** The block of a linked container; « not loaded », « gone » when there is none to name. */
    private static Text name(PartyBank.Linked storage) {
        if (storage.state() == PartyBank.LinkState.UNLOADED) return Text.translatable(KEY + "storage.linked.unloaded");
        if (storage.block() == Blocks.AIR) return Text.translatable(KEY + "storage.linked.absent");
        return storage.block().getName();
    }

    private static Text position(BlockPos pos) {
        return Text.translatable(KEY + "storage.linked.pos", pos.getX(), pos.getY(), pos.getZ());
    }

    // ------------------------------------------------------------------ tooltips

    /**
     * The storage's counts and state: what it is, what it holds, why it can't pay; a row of the list: that container;
     * null when the mouse is elsewhere.
     */
    public @Nullable List<Text> tooltip(PartyDashboardData data, int mx, int my) {
        if (HitArea.contains(mx, my, SIDE_X - 2, COUNTS_Y - 2, SIDE_W + 2, STATE_Y + 10 - COUNTS_Y + 2)) return storageTooltip(data, dashboard);
        if (!handler().isLinkedOpen()) return null;
        List<PartyBank.Linked> storages = data.storages();
        int index = Math.floorDiv(my - (LIST_Y - 1), LIST_ROW);
        if (mx < LIST_X - 1 || mx >= LIST_X + LIST_W || my < LIST_Y - 1 || index >= LIST_ROWS || listScroll + index >= storages.size()) return null;
        PartyBank.Linked storage = storages.get(listScroll + index);
        List<Text> lines = new ArrayList<>();
        lines.add(name(storage).copy().formatted(Formatting.GOLD));
        lines.add(Text.translatable(KEY + "storage.linked.where", position(storage.pos().pos()), storage.pos().dimension().getValue().toString())
                .formatted(Formatting.GRAY));
        lines.add(switch (storage.state()) {
            case PRESENT -> Text.translatable(KEY + "storage.linked.present.hint").formatted(Formatting.GREEN);
            case ABSENT -> Text.translatable(KEY + "storage.linked.absent.hint").formatted(Formatting.RED);
            case UNLOADED -> Text.translatable(KEY + "storage.linked.unloaded.hint").formatted(Formatting.GOLD);
        });
        return lines;
    }

    /** The storage: what it is, what it holds, why it can't pay (the Storage tab's counts, the Gains tab's pointer). */
    public static List<Text> storageTooltip(PartyDashboardData data, Dashboard dashboard) {
        PartyBank.Status bank = data.bank();
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable(KEY + "gains.bank.title").formatted(Formatting.GOLD));
        switch (bank.state()) {
            case NONE -> lines.add(Text.translatable(KEY + "gains.bank.none.hint").formatted(Formatting.GRAY));
            case MISSING -> lines.add(Text.translatable(KEY + "gains.bank.missing.hint").append(skipped(bank)).formatted(Formatting.RED));
            case INFINITE -> lines.add(Text.translatable(KEY + "gains.bank.infinite.hint").formatted(Formatting.GRAY));
            case OK, SHORT -> {
                lines.add(Text.translatable(KEY + "gains.bank.holds", bank.coins(), dashboard.currency(PartyCurrency.COIN).getName(),
                        bank.stars(), dashboard.currency(PartyCurrency.STAR).getName()).formatted(Formatting.GRAY));
                if (!skipped(bank).getString().isEmpty()) lines.add(Text.translatable(KEY + "gains.bank.skipped").append(skipped(bank)).formatted(Formatting.RED));
                if (bank.state() == PartyBank.State.SHORT) lines.add(Text.translatable(KEY + "gains.bank.short").formatted(Formatting.GOLD));
                lines.add(Text.translatable(KEY + "gains.bank.hint").formatted(Formatting.DARK_GRAY));
            }
        }
        return lines;
    }

    // ------------------------------------------------------------------ scrolling

    /** The wheel over the page: the list when open, else the storage's rows, one at a time. */
    public void scroll(double verticalAmount) {
        int step = -(int) Math.signum(verticalAmount);
        if (handler().isLinkedOpen()) listScroll += step;
        else handler().setStorageScroll(handler().getStorageScroll() + step);
    }

    /** A press on the scroll bar (screen coordinates): the rows follow the mouse until it is released. */
    public boolean pressed(double mouseX, double mouseY) {
        dragging = !handler().isLinkedOpen()
                && HitArea.contains(mouseX - dashboard.left(), mouseY - dashboard.top(), BAR_X - 2, GRID_Y, BAR_W + 4, GRID_H);
        if (dragging) dragged(mouseY);
        return dragging;
    }

    /** The mouse dragged on (screen coordinates): false when the scroll bar is not being dragged. */
    public boolean dragged(double mouseY) {
        if (!dragging) return false;
        double ratio = (mouseY - dashboard.top() - GRID_Y) / GRID_H;
        handler().setStorageScroll((int) Math.round(ratio * (STORAGE_MAX_SCROLL + STORAGE_ROWS) - STORAGE_ROWS / 2.0));
        return true;
    }

    public void released() {
        dragging = false;
    }
}
