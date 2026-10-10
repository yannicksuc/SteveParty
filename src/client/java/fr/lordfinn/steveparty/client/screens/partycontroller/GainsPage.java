package fr.lordfinn.steveparty.client.screens.partycontroller;

import fr.lordfinn.steveparty.blocks.custom.PartyController.MiniGameGains;
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
 * The Gains tab: where the gains are taken from (a line leading to the Storage tab, what the storage holds in its
 * tooltip), the Coin and Star items above their columns (click with an item to pick it, with an empty hand to go back
 * to the default one), what each place earns.
 */
public final class GainsPage {
    /** A row per place. */
    private static final int GAINS_Y = CY + 21, GAINS_ROW = 17, GAINS_FIELD = GAINS_COLUMN - 2 * STEP - 4;
    /** The line leading to the Storage tab, left of the Coin item. */
    private static final int STORAGE_LINE_W = COIN_X - 1 - 4 - CX;
    private static final PartyCurrency[] CURRENCIES = {PartyCurrency.COIN, PartyCurrency.STAR};

    private final Dashboard dashboard;
    private final DashboardPainter paint;

    public GainsPage(Dashboard dashboard, DashboardPainter paint) {
        this.dashboard = dashboard;
        this.paint = paint;
    }

    public void addButtons(PartyDashboardData data) {
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
        // Where the gains are taken from: the storage, in its state's colour (what it holds in its tooltip, a click opens its tab)
        paint.line(context, Text.translatable(KEY + "gains.storage"), CX, CY + 5, STORAGE_LINE_W, StoragePage.colour(data.bank()));
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

    /** Whether the mouse is over the line leading to the Storage tab. */
    public static boolean overStorageLine(double mx, double my) {
        return HitArea.contains(mx, my, CX - 2, CY, STORAGE_LINE_W + 4, 18);
    }

    /** The line leading to the Storage tab: what the storage holds, why it can't pay; null when the mouse is elsewhere. */
    public @Nullable List<Text> storageTooltip(PartyDashboardData data, int mx, int my) {
        if (!overStorageLine(mx, my)) return null;
        List<Text> lines = new ArrayList<>(StoragePage.storageTooltip(data, dashboard));
        lines.add(Text.translatable(KEY + "gains.storage.go").formatted(Formatting.YELLOW));
        return lines;
    }
}
