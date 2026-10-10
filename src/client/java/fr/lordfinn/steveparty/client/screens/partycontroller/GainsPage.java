package fr.lordfinn.steveparty.client.screens.partycontroller;

import fr.lordfinn.steveparty.blocks.custom.PartyController.MiniGameGains;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyBank;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import fr.lordfinn.steveparty.client.gui.ConsoleButton;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.HitArea;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.client.screens.partycontroller.DashboardStyle.*;
import static fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.*;

/**
 * The Gains tab: the bank's Inventory Cartridge, the Coin and Star items above their columns (click with an item to pick
 * it, with an empty hand to go back to the default one), what each place earns.
 */
public final class GainsPage {
    /** A row per place. */
    private static final int GAINS_Y = CY + 21, GAINS_ROW = 17, GAINS_FIELD = GAINS_COLUMN - 2 * STEP - 4;
    private static final int COLOR_SHORT = 0xFFFFB54A;
    /** The button opening the controller's own bank, right of the cartridge slot. */
    private static final int BANK_BUTTON_X = CX + 22, BANK_BUTTON_W = 44;
    private static final PartyCurrency[] CURRENCIES = {PartyCurrency.COIN, PartyCurrency.STAR};

    private final Dashboard dashboard;
    private final DashboardPainter paint;

    public GainsPage(Dashboard dashboard, DashboardPainter paint) {
        this.dashboard = dashboard;
        this.paint = paint;
    }

    /**
     * The containers of the bank left out of its total, « · 1 absent, 1 non chargé », empty when none is: those gone
     * (or no storage container), those whose chunk is not loaded. The others pay.
     */
    public static Text skipped(PartyBank.Status bank) {
        if (bank.absent() == 0 && bank.unloaded() == 0) return Text.empty();
        if (bank.unloaded() == 0) return Text.translatable(KEY + "bank.skipped.absent", bank.absent());
        if (bank.absent() == 0) return Text.translatable(KEY + "bank.skipped.unloaded", bank.unloaded());
        return Text.translatable(KEY + "bank.skipped.both", bank.absent(), bank.unloaded());
    }

    public void addButtons(PartyDashboardData data) {
        ConsoleButton bank = dashboard.add(new ConsoleButton(dashboard.left() + BANK_BUTTON_X, dashboard.top() + CY + 2, BANK_BUTTON_W, 14,
                Text.translatable(KEY + "gains.bank.open"), ConsoleButton.Kind.SCREEN, null, () -> dashboard.click(BUTTON_BANK, 1)));
        bank.active = data.canEdit();
        bank.setTooltip(Tooltip.of(data.canEdit() ? Text.translatable(KEY + "gains.bank.open.hint") : Text.translatable(KEY + "locked")));
        for (int row = 0; row < MiniGameGains.ROWS; row++) {
            for (PartyCurrency currency : CURRENCIES) {
                int left = dashboard.left() + (currency == PartyCurrency.COIN ? GAINS_COIN_X : GAINS_STAR_X), top = dashboard.top() + GAINS_Y + row * GAINS_ROW;
                int amount = data.gains().amount(currency, row);
                int less = gainButton(row, currency, false), more = gainButton(row, currency, true);
                ConsoleButton minus = dashboard.add(new ConsoleButton(left, top, STEP, STEP, Text.literal("-"), ConsoleButton.Kind.SCREEN, null,
                        () -> dashboard.click(less, Screen.hasShiftDown() ? 5 : 1)));
                ConsoleButton plus = dashboard.add(new ConsoleButton(left + GAINS_COLUMN - STEP, top, STEP, STEP, Text.literal("+"), ConsoleButton.Kind.SCREEN, null,
                        () -> dashboard.click(more, Screen.hasShiftDown() ? 5 : 1)));
                minus.active = data.canEdit() && amount > 0;
                plus.active = data.canEdit() && amount < MiniGameGains.MAX;
                Text why = data.canEdit() ? Text.translatable(KEY + "gains.step") : Text.translatable(KEY + "locked");
                minus.setTooltip(Tooltip.of(why));
                plus.setTooltip(Tooltip.of(why));
            }
        }
    }

    public void draw(DrawContext context, PartyDashboardData data) {
        // The bank's line: its cartridge slot, its name (what it holds in its tooltip)
        PartyBank.Status bank = data.bank();
        int tx = BANK_BUTTON_X + BANK_BUTTON_W + 4, room = COIN_X - 1 - 4 - tx;
        switch (bank.state()) {
            case NONE, MISSING -> paint.line(context, Text.translatable(KEY + "gains.bank." + (bank.state() == PartyBank.State.NONE ? "none" : "missing")),
                    tx, CY + 5, room, INK_RED);
            // Some containers skipped (gone, not loaded): said on the line, in the colour of « short »
            case OK, SHORT -> paint.line(context, Text.translatable(KEY + "gains.bank").append(skipped(bank)), tx, CY + 5, room,
                    bank.state() == PartyBank.State.SHORT || !skipped(bank).getString().isEmpty() ? COLOR_SHORT : WHITE);
        }
        for (int row = 0; row < MiniGameGains.ROWS; row++) {
            int top = GAINS_Y + row * GAINS_ROW;
            if (row == MiniGameGains.PARTICIPANTS) {
                paint.line(context, Text.translatable(KEY + "gains.participants"), CX, top + 4, GAINS_COIN_X - CX - 4, WHITE);
            } else {
                paint.rankDisc(context, row + 1, CX, top + 2);
                paint.line(context, Text.translatable(KEY + "gains.place"), CX + 16, top + 4, GAINS_COIN_X - CX - 20, INK_SOFT);
            }
            for (PartyCurrency currency : CURRENCIES) {
                int left = (currency == PartyCurrency.COIN ? GAINS_COIN_X : GAINS_STAR_X) + STEP + 2;
                int amount = data.gains().amount(currency, row);
                ConsolePaint.inset(context, left, top, GAINS_FIELD - 1, 15, SLOT_BODY, SLOT_EDGE, SLOT_LOW);
                paint.centred(context, Integer.toString(amount), left, GAINS_FIELD, top + 4, amount == 0 ? INK_SOFT : WHITE);
            }
        }
    }

    // ------------------------------------------------------------------ tooltips

    /**
     * A click on a currency slot, checked here too to say why at once (the server checks again).
     *
     * @return the refusal to flash, {@link Text#empty()} to refuse silently, null to let the click through
     */
    public @Nullable Text refusal(@Nullable PartyDashboardData data, Slot slot, SlotActionType actionType) {
        if (actionType != SlotActionType.PICKUP) return Text.empty();
        if (data == null || !data.canEdit()) return Text.translatable(KEY + "read_only");
        ItemStack cursor = dashboard.handler().getCursorStack();
        ItemStack other = dashboard.currency(currencyOf(slot.id).other());
        if (!cursor.isEmpty() && ItemStack.areItemsAndComponentsEqual(cursor, other)) return Text.translatable(KEY + "settings.same_item");
        return null;
    }

    /** A currency slot: the item it stands for, how to change it. */
    public List<Text> currencyTooltip(PartyDashboardData data, Slot slot) {
        String name = currencyOf(slot.id) == PartyCurrency.STAR ? "star" : "coin";
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable(KEY + "gains." + name, slot.getStack().getName()).formatted(Formatting.GOLD));
        lines.add(Text.translatable(KEY + "gains." + name + ".hint").formatted(Formatting.GRAY));
        if (!data.canEdit()) lines.add(Text.translatable(KEY + "locked").formatted(Formatting.RED));
        return lines;
    }

    /** The bank (its line, its empty slot): what it is, what it holds, why it can't pay; null when the mouse is elsewhere. */
    public @Nullable List<Text> bankTooltip(PartyDashboardData data, @Nullable Slot focused, int mx, int my) {
        if (!(HitArea.contains(mx, my, BANK_BUTTON_X + BANK_BUTTON_W + 2, CY, COIN_X - 1 - BANK_BUTTON_X - BANK_BUTTON_W - 6, 18) || (focused != null && focused.id == SLOT_BANK && !focused.hasStack())))
            return null;
        PartyBank.Status bank = data.bank();
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable(KEY + "gains.bank.title").formatted(Formatting.GOLD));
        switch (bank.state()) {
            case NONE -> lines.add(Text.translatable(KEY + "gains.bank.none.hint").formatted(Formatting.GRAY));
            case MISSING -> lines.add(Text.translatable(KEY + "gains.bank.missing.hint").append(skipped(bank)).formatted(Formatting.RED));
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
}
