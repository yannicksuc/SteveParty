package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlockEntity.Comparator;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.PartyButton;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.payloads.custom.GoalPolePayload;
import fr.lordfinn.steveparty.screen_handlers.custom.GoalPoleScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.sounds.ModSounds.CLOSE_TILE_GUI_SOUND_EVENT;
import static fr.lordfinn.steveparty.sounds.ModSounds.OPEN_TILE_GUI_SOUND_EVENT;

/**
 * Goal pole settings: when its comparator output lights up, compared to the total score of the players tracked by
 * the base below. The five comparisons are picked in one row ({@code < ≤ = ≥ >}); the value is typed, or nudged with
 * the ± buttons / the mouse wheel (Shift: 10 at a time). Enter validates, Escape cancels.
 */
public class GoalPoleScreen extends HandledScreen<GoalPoleScreenHandler> {
    private static final String KEY = "gui.steveparty.goal_pole.";
    private static final int WIDTH = 196;
    /** Display order of the comparisons, left to right. */
    private static final Comparator[] ORDER = {Comparator.LESS, Comparator.LESS_OR_EQUAL, Comparator.EQUAL,
            Comparator.GREATER_OR_EQUAL, Comparator.GREATER};
    private static final int CMP_SIZE = 26, CMP_HEIGHT = 22, CMP_GAP = 7;
    private static final int HINT_Y = 16, FIELD_HEIGHT = 20, STEP_SIZE = 20;
    private static final ItemStack FLAG_ICON = new ItemStack(ModItems.FLAG);

    private final List<PartyButton> comparatorButtons = new ArrayList<>();
    private Comparator comparator;
    /** Advanced: each segment of the pole has its own goal (notches show them); otherwise one goal for the pole. */
    private boolean perSegment;
    /** The flags step down one notch per point, instead of sliding down once the goal is met. */
    private boolean flagSteps;
    /**
     * Whose points the goal is about: each side's (a team's added up, or a player's: the default), each team's best
     * player's, or everybody's total. Clicking the button goes through them (Shift: backwards).
     */
    private GoalPoleBlockEntity.Count count;
    private int modeY, flagY, playerY;
    private TextFieldWidget valueField;
    private PartyButton doneButton;
    private boolean openSoundPlayed = false;
    /** Layout, from the number of lines the hint takes in the current language. */
    private int cmpY, valueY, summaryY, buttonsY;

    public GoalPoleScreen(GoalPoleScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = WIDTH;
        this.comparator = handler.getComparator();
        this.perSegment = handler.isPerSegment();
        this.flagSteps = handler.isFlagSteps();
        this.count = handler.getCount();
    }

    @Override
    protected void init() {
        int hintLines = textRenderer.wrapLines(Text.translatable(KEY + "hint"), WIDTH - 24).size();
        cmpY = HINT_Y + hintLines * 10 + 4;
        valueY = cmpY + CMP_HEIGHT + 12;
        summaryY = valueY + FIELD_HEIGHT + 7;
        modeY = summaryY + 16;
        flagY = modeY + 20;
        playerY = flagY + 20;
        buttonsY = playerY + 24;
        backgroundHeight = buttonsY + 20 + 12;
        super.init();
        // init() runs again on every resize: keep what the player already chose / typed
        String valueText = valueField != null ? valueField.getText() : Integer.toString(handler.getValue());

        comparatorButtons.clear();
        int rowWidth = ORDER.length * CMP_SIZE + (ORDER.length - 1) * CMP_GAP;
        int cx = x + (WIDTH - rowWidth) / 2;
        for (Comparator c : ORDER) {
            PartyButton button = new PartyButton(cx, y + cmpY, CMP_SIZE, CMP_HEIGHT, symbolName(c), b -> selectComparator(c))
                    .content((context, textRenderer, centerX, centerY, color) -> drawSymbol(context, textRenderer, c, centerX, centerY, color));
            comparatorButtons.add(addDrawableChild(button));
            cx += CMP_SIZE + CMP_GAP;
        }

        int fieldX = x + 16 + STEP_SIZE + 4;
        int fieldWidth = WIDTH - 2 * (16 + STEP_SIZE + 4);
        addDrawableChild(new PartyButton(x + 16, y + valueY, STEP_SIZE, FIELD_HEIGHT, Text.literal("-"), b -> nudge(-1))
                .content(GoalPoleScreen::drawMinus))
                .setTooltip(Tooltip.of(Text.translatable(KEY + "decrease")));
        addDrawableChild(new PartyButton(x + WIDTH - 16 - STEP_SIZE, y + valueY, STEP_SIZE, FIELD_HEIGHT, Text.literal("+"), b -> nudge(1))
                .content(GoalPoleScreen::drawPlus))
                .setTooltip(Tooltip.of(Text.translatable(KEY + "increase")));

        valueField = new TextFieldWidget(textRenderer, fieldX + 5, y + valueY + 6, fieldWidth - 10, 10, Text.translatable(KEY + "value"));
        valueField.setDrawsBackground(false);
        valueField.setMaxLength(11);
        // Digits and a leading minus sign only
        valueField.setTextPredicate(s -> s.matches("-?\\d*"));
        valueField.setText(valueText);
        valueField.setChangedListener(s -> refresh());
        addDrawableChild(valueField);

        int buttonWidth = (WIDTH - 32 - 8) / 2;
        addDrawableChild(new PartyButton(x + 16, y + buttonsY, buttonWidth, 20, Text.translatable("gui.steveparty.cancel"), b -> close()));
        PartyButton mode = addDrawableChild(new PartyButton(x + 16, y + modeY, WIDTH - 32, 18, Text.empty(), b -> {
            perSegment = !perSegment;
            b.setMessage(modeText());
            b.setTooltip(Tooltip.of(modeTooltip()));
        }));
        mode.setMessage(modeText());
        mode.setTooltip(Tooltip.of(modeTooltip()));
        PartyButton flag = addDrawableChild(new PartyButton(x + 16, y + flagY, WIDTH - 32, 18, Text.empty(), b -> {
            flagSteps = !flagSteps;
            b.setMessage(flagText());
            b.setTooltip(Tooltip.of(flagTooltip()));
        }));
        flag.setMessage(flagText());
        flag.setTooltip(Tooltip.of(flagTooltip()));
        PartyButton each = addDrawableChild(new PartyButton(x + 16, y + playerY, WIDTH - 32, 18, Text.empty(), b -> {
            GoalPoleBlockEntity.Count[] counts = GoalPoleBlockEntity.Count.values();
            count = counts[Math.floorMod(count.ordinal() + (Screen.hasShiftDown() ? -1 : 1), counts.length)];
            b.setMessage(playerText());
            b.setTooltip(Tooltip.of(playerTooltip()));
        }));
        each.setMessage(playerText());
        each.setTooltip(Tooltip.of(playerTooltip()));
        doneButton = addDrawableChild(new PartyButton(x + 16 + buttonWidth + 8, y + buttonsY, buttonWidth, 20,
                Text.translatable("gui.steveparty.validate"), b -> submit()).style(PartyButton.Style.PRIMARY));

        setInitialFocus(valueField);
        refresh();

        // Play open sound once (not on every resize)
        if (!openSoundPlayed && client != null && client.player != null) {
            client.player.playSound(OPEN_TILE_GUI_SOUND_EVENT, 1.0F, 1.0F);
        }
        openSoundPlayed = true;
    }

    // ------------------------------------------------------------------ state

    private void selectComparator(Comparator c) {
        comparator = c;
        refresh();
    }

    /** The typed value, or null if it is not a whole number that fits. */
    private Integer parsedValue() {
        try {
            return Integer.parseInt(valueField.getText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void nudge(int direction) {
        Integer value = parsedValue();
        int step = Screen.hasShiftDown() ? 10 : 1;
        long next = (value != null ? value : 0) + (long) direction * step;
        valueField.setText(Long.toString(Math.clamp(next, Integer.MIN_VALUE, Integer.MAX_VALUE)));
    }

    /** Selected comparison, tooltips with the current value, Done only with a valid number. */
    private void refresh() {
        Integer value = parsedValue();
        String shown = value != null ? value.toString() : "?";
        for (int i = 0; i < ORDER.length; i++) {
            PartyButton button = comparatorButtons.get(i);
            button.setSelected(ORDER[i] == comparator);
            button.setTooltip(Tooltip.of(Text.empty()
                    .append(Text.translatable(KEY + "summary", Text.translatable(KEY + "comparator." + key(ORDER[i]), shown))
                            .formatted(Formatting.GOLD))
                    .append("\n")
                    .append(Text.translatable(KEY + "comparator.description").formatted(Formatting.GRAY))));
        }
        if (doneButton != null) {
            doneButton.active = value != null;
            doneButton.setTooltip(value != null ? null : Tooltip.of(Text.translatable(KEY + "invalid_value")));
        }
    }

    private void submit() {
        Integer value = parsedValue();
        if (value == null) return;
        ClientPlayNetworking.send(new GoalPolePayload(handler.getPos(), comparator, value, perSegment, flagSteps, count));
        close();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        PartyGui.panel(context, x, y, WIDTH, backgroundHeight, PartyGui.PANEL);
        PartyGui.titlePlate(context, textRenderer, x + WIDTH / 2, y - 11, 18, title, PartyGui.FLAG_RED);
        context.drawItem(FLAG_ICON, PartyGui.titlePlateIconX(textRenderer, x + WIDTH / 2, 18, title), y - 8);

        int fieldX = x + 16 + STEP_SIZE + 4;
        int fieldWidth = WIDTH - 2 * (16 + STEP_SIZE + 4);
        PartyGui.inset(context, fieldX, y + valueY, fieldWidth, FIELD_HEIGHT, 0xFF3B4247,
                valueField != null && valueField.isFocused(), parsedValue() == null);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawTextWrapped(textRenderer, Text.translatable(KEY + "hint"), 12, HINT_Y, WIDTH - 24, PartyGui.TEXT_DARK);
        // What the pole does with the current settings, in words
        Integer value = parsedValue();
        Text summary = value == null
                ? Text.translatable(KEY + "invalid_value")
                : Text.translatable(KEY + "summary", Text.translatable(KEY + "comparator." + key(comparator), value));
        int summaryWidth = textRenderer.getWidth(summary);
        context.drawText(textRenderer, summary, (WIDTH - summaryWidth) / 2, summaryY,
                value == null ? PartyGui.TEXT_ERROR : PartyGui.TEXT_SOFT, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    private Text playerText() {
        return Text.translatable(KEY + "who." + count.name().toLowerCase(java.util.Locale.ROOT));
    }

    private Text playerTooltip() {
        return Text.empty().append(Text.translatable(KEY + "who").formatted(Formatting.GOLD)).append("\n")
                .append(Text.translatable(KEY + "who." + count.name().toLowerCase(java.util.Locale.ROOT) + ".details").formatted(Formatting.GRAY))
                .append("\n").append(Text.translatable("gui.steveparty.goal_pole_base.cycle_hint").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }

    private Text flagText() {
        return Text.translatable(KEY + (flagSteps ? "flag.steps" : "flag.slide"));
    }

    private Text flagTooltip() {
        return Text.empty().append(Text.translatable(KEY + "flag").formatted(Formatting.GOLD)).append("\n")
                .append(Text.translatable(KEY + (flagSteps ? "flag.steps.details" : "flag.slide.details")).formatted(Formatting.GRAY));
    }

    private Text modeText() {
        return Text.translatable(KEY + (perSegment ? "mode.per_segment" : "mode.column"));
    }

    private Text modeTooltip() {
        return Text.empty().append(Text.translatable(KEY + "mode").formatted(Formatting.GOLD)).append("\n")
                .append(Text.translatable(KEY + (perSegment ? "mode.per_segment.details" : "mode.column.details")).formatted(Formatting.GRAY));
    }

    private static String key(Comparator c) {
        return c.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static Text symbolName(Comparator c) {
        return Text.translatable(KEY + "comparator." + key(c), "");
    }

    /**
     * Comparison symbols at twice the font size: {@code <}, {@code =}, {@code >} are font glyphs, {@code ≤} and
     * {@code ≥} the same glyphs raised a little with a bar underneath (the pixel font has no ≤ ≥).
     */
    private static void drawSymbol(DrawContext context, TextRenderer textRenderer, Comparator c, int centerX, int centerY, int color) {
        String glyph = switch (c) {
            case LESS, LESS_OR_EQUAL -> "<";
            case GREATER, GREATER_OR_EQUAL -> ">";
            case EQUAL -> "=";
        };
        boolean orEqual = c == Comparator.LESS_OR_EQUAL || c == Comparator.GREATER_OR_EQUAL;
        int glyphWidth = textRenderer.getWidth(glyph) - 1; // without the spacing column
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(centerX - glyphWidth, centerY - 7 - (orEqual ? 2 : 0), 0);
        matrices.scale(2f, 2f, 1f);
        context.drawText(textRenderer, glyph, 0, 0, color, false);
        matrices.pop();
        if (orEqual) {
            context.fill(centerX - glyphWidth, centerY + 6, centerX + glyphWidth, centerY + 8, color);
        }
    }

    private static void drawMinus(DrawContext context, TextRenderer textRenderer, int centerX, int centerY, int color) {
        context.fill(centerX - 4, centerY - 1, centerX + 4, centerY + 1, color);
    }

    private static void drawPlus(DrawContext context, TextRenderer textRenderer, int centerX, int centerY, int color) {
        drawMinus(context, textRenderer, centerX, centerY, color);
        context.fill(centerX - 1, centerY - 4, centerX + 1, centerY + 4, color);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int fieldX = x + 16;
        if (verticalAmount != 0 && HitArea.contains(mouseX, mouseY, fieldX, y + valueY, x + WIDTH - 16 - fieldX, FIELD_HEIGHT)) {
            nudge(verticalAmount > 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        if (valueField != null && valueField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
                nudge(keyCode == GLFW.GLFW_KEY_UP ? 1 : -1);
                return true;
            }
            // Escape closes and Tab moves the focus; any other key stays in the field (the inventory key must not close)
            if (keyCode != GLFW.GLFW_KEY_ESCAPE && keyCode != GLFW.GLFW_KEY_TAB) {
                valueField.keyPressed(keyCode, scanCode, modifiers);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (client != null && client.player != null) {
            client.player.playSound(CLOSE_TILE_GUI_SOUND_EVENT, 1.0F, 1.0F);
        }
        super.close();
    }
}
