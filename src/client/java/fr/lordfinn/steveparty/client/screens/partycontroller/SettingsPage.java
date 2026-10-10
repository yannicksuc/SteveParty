package fr.lordfinn.steveparty.client.screens.partycontroller;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Phase;
import fr.lordfinn.steveparty.client.gui.ConsoleButton;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.dice.AllowedDice;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.powerups.PowerUpLimit;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.PressableWidget;
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
 * The Settings tab: the rounds, the practice round, the power-ups a player may carry, « Restrict dice » (a switch, a
 * row of ghost slots, its toggle opening a panel of all of them over the other settings; the places after the first
 * free one greyed, all of them while the switch is off).
 */
public final class SettingsPage {
    /** A row per setting. */
    private static final int SETTINGS_Y = CY + 18, SETTINGS_ROW = 22;
    /** The « Restrict dice » row (the 4th): its switch, its slots, the toggle of its panel. */
    private static final int DICE_Y = SETTINGS_Y + 3 * SETTINGS_ROW, DICE_TOGGLE_X = CX + CW - STEP, DICE_SWITCH_X = DICE_ROW_X - 1 - 4 - 24;

    private final Dashboard dashboard;
    private final DashboardPainter paint;

    public SettingsPage(Dashboard dashboard, DashboardPainter paint) {
        this.dashboard = dashboard;
        this.paint = paint;
    }

    private PartyControllerScreenHandler handler() {
        return dashboard.handler();
    }

    // ------------------------------------------------------------------ buttons

    public void addButtons(PartyDashboardData data) {
        int x = dashboard.left(), y = dashboard.top();
        // Restrict dice: its switch, the toggle of its panel (open, it takes the place of the other settings)
        Switch restrict = dashboard.add(new Switch(x + DICE_SWITCH_X, y + DICE_Y, 24, BTN_H, Text.empty(), data.restrictDice(),
                BUTTON_RESTRICT_DICE));
        restrict.active = data.canEdit();
        restrict.setTooltip(Tooltip.of(data.canEdit() ? Text.translatable(KEY + "settings.allowed_dice.switch." + (data.restrictDice() ? "on" : "off"))
                : Text.translatable(KEY + "locked")));
        boolean open = handler().isDiceOpen();
        ConsoleButton toggle = dashboard.add(new ConsoleButton(x + DICE_TOGGLE_X, y + DICE_Y + 1, STEP, STEP, Text.empty(),
                ConsoleButton.Kind.SCREEN, null, () -> {
                    handler().setDiceOpen(!handler().isDiceOpen());
                    dashboard.rebuild();
                }).decoration((context, button) -> chevron(context, button, !open)));
        toggle.setTooltip(Tooltip.of(open ? Text.translatable(KEY + "settings.allowed_dice.close")
                : Text.translatable(KEY + "settings.allowed_dice.open", handler().allowedDiceCount(), PartyControllerEntity.MAX_ALLOWED_DICE)));
        if (open) return;
        int rowY = y + SETTINGS_Y + 1, sx = x + CX + CW - 68;
        boolean editable = data.canEdit() && data.phase() != Phase.RUNNING;
        Text why = !data.canEdit() ? Text.translatable(KEY + "locked") : Text.translatable(KEY + "settings.rounds.running");
        ConsoleButton minus = dashboard.add(new ConsoleButton(sx, rowY, STEP, STEP, Text.literal("-"), ConsoleButton.Kind.SCREEN, null,
                () -> dashboard.click(BUTTON_ROUNDS_DOWN, Screen.hasShiftDown() ? 5 : 1)));
        ConsoleButton plus = dashboard.add(new ConsoleButton(sx + 52, rowY, STEP, STEP, Text.literal("+"), ConsoleButton.Kind.SCREEN, null,
                () -> dashboard.click(BUTTON_ROUNDS_UP, Screen.hasShiftDown() ? 5 : 1)));
        minus.active = editable && data.roundsSetting() > PartyControllerEntity.MIN_ROUNDS;
        plus.active = editable && data.roundsSetting() < PartyControllerEntity.MAX_ROUNDS;
        minus.setTooltip(Tooltip.of(editable ? Text.translatable(KEY + "settings.rounds.less") : why));
        plus.setTooltip(Tooltip.of(editable ? Text.translatable(KEY + "settings.rounds.more") : why));
        // A practice round before each mini-game: a switch, its state on its left (at most in the 72 px its label leaves)
        Text state = Text.translatable(KEY + (data.practiceRound() ? "settings.practice.on" : "settings.practice.off"));
        int width = Math.min(dashboard.font().getWidth(state) - 1 + 4 + 24, 68);
        Switch practice = dashboard.add(new Switch(x + CX + CW - width, y + SETTINGS_Y + SETTINGS_ROW, width, BTN_H, state, data.practiceRound(),
                BUTTON_PRACTICE));
        practice.active = data.canEdit();
        practice.setTooltip(Tooltip.of(data.canEdit() ? Text.translatable(KEY + "settings.practice.tooltip") : Text.translatable(KEY + "locked")));
        // The power-ups a player may carry (0: no limit)
        int limitY = y + SETTINGS_Y + 2 * SETTINGS_ROW + 1;
        ConsoleButton fewer = dashboard.add(new ConsoleButton(sx, limitY, STEP, STEP, Text.literal("-"), ConsoleButton.Kind.SCREEN, null,
                () -> dashboard.click(BUTTON_MAX_POWER_UPS_DOWN, 1)));
        ConsoleButton more = dashboard.add(new ConsoleButton(sx + 52, limitY, STEP, STEP, Text.literal("+"), ConsoleButton.Kind.SCREEN, null,
                () -> dashboard.click(BUTTON_MAX_POWER_UPS_UP, 1)));
        fewer.active = data.canEdit() && data.maxPowerUps() > 0;
        more.active = data.canEdit() && data.maxPowerUps() < PowerUpLimit.MAX;
        fewer.setTooltip(Tooltip.of(data.canEdit() ? Text.translatable(KEY + "settings.max_powerups.less") : Text.translatable(KEY + "locked")));
        more.setTooltip(Tooltip.of(data.canEdit() ? Text.translatable(KEY + "settings.max_powerups.more") : Text.translatable(KEY + "locked")));
    }

    /** The toggle's chevron: down to open the panel, up to close it. */
    private static void chevron(DrawContext context, ConsoleButton button, boolean down) {
        int cx = button.getX() + 4, cy = button.getY() + 6;
        int colour = button.isHovered() ? WHITE : INK;
        for (int row = 0; row < 4; row++) {
            int r = down ? row : 3 - row;
            context.fill(cx + r, cy + row, cx + 7 - r, cy + row + 1, colour);
        }
    }

    // ------------------------------------------------------------------ drawing

    /** Under the slots (screen coordinates): the open « Allowed dice » panel, sunk into the screen around its slots. */
    public void drawPanel(DrawContext context) {
        if (handler().isDiceOpen())
            ConsolePaint.inset(context, dashboard.left() + DICE_PANEL_X - 5, dashboard.top() + DICE_PANEL_Y - 5, DICE_COLUMNS * 18 + 8, 3 * 18 + 8,
                    SCREEN_EDGE, SLOT_EDGE, SLOT_BODY);
    }

    /** Over the slots (screen coordinates): the first free dice place shows, faded, a die. */
    public void drawFreeDie(DrawContext context) {
        int free = handler().allowedDiceCount();
        int first = handler().isDiceOpen() ? DICE_PANEL_FIRST_SLOT : DICE_FIRST_SLOT;
        if (handler().isRestrictDice() && free < (handler().isDiceOpen() ? PartyControllerEntity.MAX_ALLOWED_DICE : DICE_ROW)) {
            Slot slot = handler().getSlot(first + free);
            if (slot.isEnabled() && !slot.hasStack())
                paint.ghost(context, new ItemStack(ModItems.DEFAULT_DICE), dashboard.left() + slot.x, dashboard.top() + slot.y, false);
        }
    }

    public void draw(DrawContext context, PartyDashboardData data) {
        paint.header(context, Text.translatable(KEY + "tab.settings"), !data.canEdit());
        // Restrict dice: its label (its switch, its row: widgets and slots) or, the panel open, how many it lists
        paint.line(context, Text.translatable(KEY + "settings.allowed_dice"), CX, DICE_Y + 5, DICE_SWITCH_X - 4 - CX, WHITE);
        if (handler().isDiceOpen()) {
            int count = handler().allowedDiceCount();
            Text value = count == 0 ? Text.translatable(KEY + "settings.allowed_dice.all")
                    : Text.translatable(KEY + "settings.allowed_dice.count", count, PartyControllerEntity.MAX_ALLOWED_DICE);
            // At the right, before the toggle; its room is between the switch and the toggle
            int w = dashboard.font().getWidth(value), room = DICE_TOGGLE_X - 4 - (DICE_SWITCH_X + 24 + 4);
            paint.light(context, value, w <= room ? DICE_TOGGLE_X - 4 - w : DICE_SWITCH_X + 24 + 4, DICE_Y + 5, Math.min(w, room),
                    !data.restrictDice() ? INK_DIM : count == 0 ? INK_SOFT : WHITE);
        } else {
            int room = CW - 72;
            paint.line(context, Text.translatable(KEY + "settings.rounds"), CX, SETTINGS_Y + 5, room, WHITE);
            int fieldX = CX + CW - 68 + 18;
            ConsolePaint.inset(context, fieldX, SETTINGS_Y + 1, 31, 15, SLOT_BODY, SLOT_EDGE, SLOT_LOW);
            paint.centred(context, Integer.toString(data.roundsSetting()), fieldX, 32, SETTINGS_Y + 5, WHITE);
            paint.line(context, Text.translatable(KEY + "settings.practice"), CX, SETTINGS_Y + SETTINGS_ROW + 5, room, WHITE);
            int limitY = SETTINGS_Y + 2 * SETTINGS_ROW;
            paint.line(context, Text.translatable(KEY + "settings.max_powerups"), CX, limitY + 5, room, WHITE);
            String limit = data.maxPowerUps() <= 0 ? "∞" : Integer.toString(data.maxPowerUps());
            ConsolePaint.inset(context, fieldX, limitY + 1, 31, 15, SLOT_BODY, SLOT_EDGE, SLOT_LOW);
            paint.centred(context, limit, fieldX, 32, limitY + 5, WHITE);
        }
        // Dice not restricted: the listed dice dimmed with their greyed slots
        if (!data.restrictDice()) {
            for (Slot slot : handler().slots) {
                if (isDiceSlot(slot.id) && slot.isEnabled() && slot.hasStack()) PartyGui.veil(context, slot.x, slot.y, DICE_OFF_VEIL);
            }
        }
    }

    // ------------------------------------------------------------------ tooltips and clicks

    /** An allowed die (its own tooltip, how to take it off), a free place, a greyed one. */
    public List<Text> diceTooltip(PartyDashboardData data, Slot slot, List<Text> itemTooltip) {
        List<Text> lines = new ArrayList<>();
        if (slot.hasStack()) {
            lines.addAll(itemTooltip);
            lines.add(Text.translatable(KEY + (data.restrictDice() ? "settings.allowed_dice.remove" : "settings.allowed_dice.off")).formatted(Formatting.GRAY));
        } else if (!data.restrictDice()) {
            lines.add(Text.translatable(KEY + "settings.allowed_dice.off").formatted(Formatting.GRAY));
        } else if (handler().isDiceSlotUsable(slot.id)) {
            lines.add(Text.translatable(KEY + "settings.allowed_dice").formatted(Formatting.GOLD));
            lines.add(Text.translatable(KEY + "settings.allowed_dice.slot").formatted(Formatting.GRAY));
        } else {
            lines.add(Text.translatable(KEY + "settings.allowed_dice.locked").formatted(Formatting.GRAY));
        }
        if (!data.canEdit()) lines.add(Text.translatable(KEY + "locked").formatted(Formatting.RED));
        return lines;
    }

    /** The label of a setting under the mouse: what it does; null when the mouse is elsewhere. */
    public @Nullable List<Text> labelTooltip(int mx, int my) {
        if (mx < CX || mx >= CX + CW - 72) return null;
        int row = Math.floorDiv(my - SETTINGS_Y, SETTINGS_ROW);
        if (my >= SETTINGS_Y && row >= (handler().isDiceOpen() ? 3 : 0) && row < 4 && my < SETTINGS_Y + row * SETTINGS_ROW + 18
                && (row < 3 || mx < DICE_SWITCH_X - 2)) {
            String key = row == 0 ? "settings.rounds" : row == 1 ? "settings.practice" : row == 2 ? "settings.max_powerups" : "settings.allowed_dice";
            return List.of(Text.translatable(KEY + key + ".tooltip").formatted(Formatting.GRAY));
        }
        return null;
    }

    /**
     * A click on a dice place, checked here too to say why at once (the server checks again).
     *
     * @return the refusal to flash, {@link Text#empty()} to refuse silently, null to let the click through
     */
    public @Nullable Text refusal(@Nullable PartyDashboardData data, Slot slot, SlotActionType actionType) {
        if (actionType != SlotActionType.PICKUP) return Text.empty();
        if (data == null || !data.canEdit()) return Text.translatable(KEY + "read_only");
        ItemStack cursor = handler().getCursorStack();
        if (!data.restrictDice()) return Text.translatable(KEY + "settings.allowed_dice.off");
        if (!handler().isDiceSlotUsable(slot.id)) return cursor.isEmpty() ? Text.empty() : Text.translatable(KEY + "settings.allowed_dice.locked");
        if (!cursor.isEmpty() && !AllowedDice.isDie(cursor)) return Text.translatable(KEY + "settings.allowed_dice.not_a_die");
        for (int i = 0; i < handler().allowedDiceCount() && !cursor.isEmpty(); i++) {
            if (AllowedDice.sameDie(handler().getSlot(DICE_PANEL_FIRST_SLOT + i).getStack(), cursor))
                return Text.translatable(KEY + "settings.allowed_dice.listed");
        }
        return null;
    }

    /** A switch (the practice round, Restrict dice): its state, then the switch (green and to the right when on). */
    private final class Switch extends PressableWidget {
        private final boolean on;
        private final int button;

        Switch(int x, int y, int width, int height, Text message, boolean on, int button) {
            super(x, y, width, height, message);
            this.on = on;
            this.button = button;
        }

        @Override
        public void onPress() {
            dashboard.click(button);
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int sx = getX() + width - 24, sy = getY() + 3;
            ConsolePaint.pill(context, sx, sy, 24, 12, on ? SWITCH_ON : SWITCH_OFF, true);
            ConsolePaint.disc(context, sx + (on ? 13 : 1), sy + 1, 10, KNOB);
            if (active && (isHovered() || isFocused())) ConsolePaint.highlight(context, sx - 1, sy - 1, 26, 14, -1, WHITE, 0);
            TextRenderer font = MinecraftClient.getInstance().textRenderer;
            // Its state, up to 4 px before the switch
            UiText.line(context, font, getMessage(), getX(), getY() + 5, Math.max(0, width - 27), !active ? INK_DIM : on ? INK_GREEN : INK_SOFT, true);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }
}
