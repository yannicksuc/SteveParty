package fr.lordfinn.steveparty.client.screens;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity;
import fr.lordfinn.steveparty.client.gui.PartyButton;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.criteria.ModScoreboardCriteria;
import fr.lordfinn.steveparty.payloads.custom.GoalPoleBasePayload;
import fr.lordfinn.steveparty.screen_handlers.custom.GoalPoleBaseScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import static fr.lordfinn.steveparty.sounds.ModSounds.CLOSE_TILE_GUI_SOUND_EVENT;
import static fr.lordfinn.steveparty.sounds.ModSounds.OPEN_TILE_GUI_SOUND_EVENT;

/**
 * Goal pole base settings: which players it follows (a player name or a selector, relative to the base) and what it
 * counts (a scoreboard criterion). Both fields are checked while typing, with what they mean in words underneath;
 * the goal has a few presets. A reminder of the redstone wiring sits at the bottom. Enter validates, Escape cancels.
 */
public class GoalPoleBaseScreen extends HandledScreen<GoalPoleBaseScreenHandler> {
    private static final String KEY = "gui.steveparty.goal_pole_base.";
    private static final int WIDTH = 224, HEIGHT = 198;
    private static final int MARGIN = 12, FIELD_HEIGHT = 18;
    private static final int SELECTOR_Y = 28, GOAL_Y = 74, LEGEND_Y = 110, LEGEND_ROW = 16;
    private static final int PRESET_SIZE = 18;
    private static final int BUTTONS_Y = HEIGHT - 30;
    private static final ItemStack BASE_ICON = new ItemStack(ModBlocks.GOAL_POLE_BASE);
    private static final ItemStack[] LEGEND_ICONS = {new ItemStack(Items.REDSTONE_TORCH), new ItemStack(Items.STONE_BUTTON),
            new ItemStack(Items.COMPARATOR)};
    private static final String[] LEGEND_KEYS = {"legend.power", "legend.reset", "legend.pulse"};

    /** Goal presets: criterion and the key of its description. */
    private static final String[][] PRESETS = {
            {ModScoreboardCriteria.LANDED_ON_POLE_ID, "goal.landed_on_pole"},
            {"minecraft.custom:minecraft.jump", "goal.jump"},
            {"deathCount", "goal.deaths"},
            {"playerKillCount", "goal.player_kills"},
            {"totalKillCount", "goal.kills"},
            {"dummy", "goal.dummy"},
    };

    /** What a field's content means, and whether it can be saved. */
    private record Check(boolean valid, Text meaning) {}

    private TextFieldWidget selectorField;
    private TextFieldWidget goalField;
    private PartyButton doneButton;
    private Check selectorCheck = new Check(true, Text.empty());
    private Check goalCheck = new Check(true, Text.empty());
    private boolean openSoundPlayed = false;

    public GoalPoleBaseScreen(GoalPoleBaseScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = WIDTH;
        this.backgroundHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        // init() runs again on every resize: keep what the player already typed
        String selectorText = selectorField != null ? selectorField.getText() : handler.getSelector();
        String goalText = goalField != null ? goalField.getText() : handler.getGoal();

        int fieldWidth = WIDTH - 2 * MARGIN;
        selectorField = createField(x + MARGIN, y + SELECTOR_Y, fieldWidth - 12, KEY + "selector", selectorText);
        selectorField.setPlaceholder(Text.literal("@p").formatted(Formatting.DARK_GRAY));

        int goalWidth = fieldWidth - PRESET_SIZE - 4;
        goalField = createField(x + MARGIN, y + GOAL_Y, goalWidth - 12, KEY + "goal", goalText);
        goalField.setPlaceholder(Text.literal(ModScoreboardCriteria.LANDED_ON_POLE_ID).formatted(Formatting.DARK_GRAY));
        addDrawableChild(new PartyButton(x + WIDTH - MARGIN - PRESET_SIZE, y + GOAL_Y, PRESET_SIZE, FIELD_HEIGHT,
                Text.translatable(KEY + "presets"), b -> cyclePreset(Screen.hasShiftDown() ? -1 : 1))
                .content(GoalPoleBaseScreen::drawPresetIcon))
                .setTooltip(Tooltip.of(Text.translatable(KEY + "presets").formatted(Formatting.GOLD)
                        .append("\n").append(Text.translatable(KEY + "presets.hint").formatted(Formatting.GRAY))));

        int buttonWidth = (WIDTH - 2 * MARGIN - 8) / 2;
        addDrawableChild(new PartyButton(x + MARGIN, y + BUTTONS_Y, buttonWidth, 20, Text.translatable("gui.steveparty.cancel"), b -> close()));
        doneButton = addDrawableChild(new PartyButton(x + MARGIN + buttonWidth + 8, y + BUTTONS_Y, buttonWidth, 20,
                Text.translatable("gui.steveparty.validate"), b -> submit()).style(PartyButton.Style.PRIMARY));

        setInitialFocus(selectorField);
        refresh();

        // Play open sound once when screen opens (init() is called again on resize)
        if (!openSoundPlayed && client != null && client.player != null) {
            openSoundPlayed = true;
            client.player.playSound(OPEN_TILE_GUI_SOUND_EVENT, 1.0F, 1.0F);
        }
    }

    /** A text field drawn without its own background, inside an inset drawn by {@link #drawBackground}. */
    private TextFieldWidget createField(int insetX, int insetY, int width, String key, String text) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, insetX + 5, insetY + 5, width - 8, 10, Text.translatable(key));
        field.setDrawsBackground(false);
        field.setMaxLength(256);
        field.setText(text);
        field.setChangedListener(s -> refresh());
        return addDrawableChild(field);
    }

    // ------------------------------------------------------------------ validation

    private void refresh() {
        if (selectorField == null || goalField == null) return;
        selectorCheck = checkSelector(selectorField.getText());
        goalCheck = checkGoal(goalField.getText());
        if (doneButton != null) {
            boolean valid = selectorCheck.valid() && goalCheck.valid();
            doneButton.active = valid;
            doneButton.setTooltip(valid ? null : Tooltip.of(Text.translatable(KEY + "invalid")));
        }
    }

    /** Same parsing as the base on the server: a selector (players only) or a player name, nothing left over. */
    static Check checkSelector(String selector) {
        if (selector.isEmpty()) return new Check(true, Text.translatable(KEY + "selector.empty"));
        StringReader reader = new StringReader(selector);
        try {
            EntityArgumentType.players().parse(reader);
            if (reader.canRead()) {
                return new Check(false, Text.translatable(KEY + "selector.invalid", reader.getRemaining()));
            }
        } catch (CommandSyntaxException e) {
            return new Check(false, Text.translatable(KEY + "selector.invalid", e.getRawMessage()));
        }
        if (!selector.startsWith("@")) return new Check(true, Text.translatable(KEY + "selector.name", selector));
        if (selector.length() > 2) return new Check(true, Text.translatable(KEY + "selector.filtered"));
        return switch (selector.charAt(1)) {
            case 'p' -> new Check(true, Text.translatable(KEY + "selector.nearest"));
            case 'a' -> new Check(true, Text.translatable(KEY + "selector.all"));
            case 'r' -> new Check(true, Text.translatable(KEY + "selector.random"));
            // The base is not a player: @s never matches anyone
            case 's' -> new Check(false, Text.translatable(KEY + "selector.self"));
            default -> new Check(true, Text.translatable(KEY + "selector.filtered"));
        };
    }

    static Check checkGoal(String goal) {
        if (goal.isEmpty()) return new Check(true, Text.translatable(KEY + "goal.empty"));
        for (String[] preset : PRESETS) {
            if (preset[0].equals(goal)) return new Check(true, Text.translatable(KEY + preset[1]));
        }
        return GoalPoleBaseBlockEntity.parseGoal(goal).isPresent()
                ? new Check(true, Text.translatable(KEY + "goal.custom"))
                : new Check(false, Text.translatable(KEY + "goal.invalid"));
    }

    private void cyclePreset(int direction) {
        int current = -1;
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i][0].equals(goalField.getText())) current = i;
        }
        int next = current < 0 ? (direction > 0 ? 0 : PRESETS.length - 1)
                : Math.floorMod(current + direction, PRESETS.length);
        goalField.setText(PRESETS[next][0]);
        goalField.setCursorToStart(false);
    }

    private void submit() {
        refresh();
        if (!selectorCheck.valid() || !goalCheck.valid()) return;
        ClientPlayNetworking.send(new GoalPoleBasePayload(handler.getPos(), selectorField.getText(), goalField.getText()));
        close();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        PartyGui.panel(context, x, y, WIDTH, HEIGHT, PartyGui.PANEL);
        PartyGui.titlePlate(context, textRenderer, x + WIDTH / 2, y - 11, 18, title, PartyGui.BRICK);
        context.drawItem(BASE_ICON, PartyGui.titlePlateIconX(textRenderer, x + WIDTH / 2, 18, title), y - 8);

        int fieldWidth = WIDTH - 2 * MARGIN;
        drawField(context, x + MARGIN, y + SELECTOR_Y, fieldWidth, selectorField, selectorCheck);
        drawField(context, x + MARGIN, y + GOAL_Y, fieldWidth - PRESET_SIZE - 4, goalField, goalCheck);

        // Redstone reminder: an icon and a short line per input/output, the details in the row's tooltip
        PartyGui.inset(context, x + MARGIN, y + LEGEND_Y, fieldWidth, legendHeight(), 0xFFB8B8B8, false, false);
        int textX = x + MARGIN + 20;
        int maxWidth = fieldWidth - 24;
        for (int i = 0; i < LEGEND_ICONS.length; i++) {
            int rowY = y + LEGEND_Y + 3 + i * LEGEND_ROW;
            if (legendRowAt(mouseX, mouseY) == i) {
                context.fill(x + MARGIN + 2, rowY - 1, x + MARGIN + fieldWidth - 1, rowY + LEGEND_ROW - 1, 0x40FFFFFF);
            }
            var matrices = context.getMatrices();
            matrices.push();
            matrices.translate(x + MARGIN + 4, rowY + 1, 0);
            matrices.scale(0.75f, 0.75f, 1f);
            context.drawItem(LEGEND_ICONS[i], 0, 0);
            matrices.pop();
            String line = textRenderer.trimToWidth(Text.translatable(KEY + LEGEND_KEYS[i]).getString(), maxWidth);
            context.drawText(textRenderer, line, textX, rowY + 3, PartyGui.TEXT_DARK, false);
        }
    }

    private static int legendHeight() {
        return LEGEND_KEYS.length * LEGEND_ROW + 4;
    }

    /** @return the legend row under the mouse, or -1. */
    private int legendRowAt(double mouseX, double mouseY) {
        if (mouseX < x + MARGIN || mouseX >= x + WIDTH - MARGIN) return -1;
        int row = (int) Math.floor((mouseY - (y + LEGEND_Y + 2)) / LEGEND_ROW);
        return row >= 0 && row < LEGEND_KEYS.length ? row : -1;
    }

    private static void drawField(DrawContext context, int x, int y, int width, TextFieldWidget field, Check check) {
        PartyGui.inset(context, x, y, width, FIELD_HEIGHT, 0xFF3B4247, field != null && field.isFocused(), !check.valid());
        PartyGui.statusIcon(context, x + width - 11, y + 6, check.valid());
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, Text.translatable(KEY + "selector"), MARGIN, SELECTOR_Y - 11, PartyGui.TEXT_DARK, false);
        drawMeaning(context, selectorCheck, SELECTOR_Y + FIELD_HEIGHT + 4);
        context.drawText(textRenderer, Text.translatable(KEY + "goal"), MARGIN, GOAL_Y - 11, PartyGui.TEXT_DARK, false);
        drawMeaning(context, goalCheck, GOAL_Y + FIELD_HEIGHT + 4);
    }

    /** What the field means, under it (trimmed to the panel; the full text is in the tooltip of the status icon). */
    private void drawMeaning(DrawContext context, Check check, int y) {
        int maxWidth = WIDTH - 2 * MARGIN;
        Text meaning = check.meaning();
        String shown = textRenderer.trimToWidth(meaning.getString(), maxWidth);
        if (!shown.equals(meaning.getString())) {
            shown = textRenderer.trimToWidth(meaning.getString(), maxWidth - textRenderer.getWidth("...")) + "...";
        }
        context.drawText(textRenderer, shown, MARGIN, y, check.valid() ? PartyGui.TEXT_SOFT : PartyGui.TEXT_ERROR, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int fieldWidth = WIDTH - 2 * MARGIN;
        if (isOverStatus(mouseX, mouseY, x + MARGIN + fieldWidth - 11, y + SELECTOR_Y + 6)) {
            context.drawTooltip(textRenderer, selectorCheck.meaning(), mouseX, mouseY);
        } else if (isOverStatus(mouseX, mouseY, x + MARGIN + fieldWidth - PRESET_SIZE - 4 - 11, y + GOAL_Y + 6)) {
            context.drawTooltip(textRenderer, goalCheck.meaning(), mouseX, mouseY);
        } else if (legendRowAt(mouseX, mouseY) >= 0) {
            String key = KEY + LEGEND_KEYS[legendRowAt(mouseX, mouseY)];
            context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(Text.empty()
                    .append(Text.translatable(key).formatted(Formatting.GOLD)).append("\n")
                    .append(Text.translatable(key + ".details").formatted(Formatting.GRAY)), 220), mouseX, mouseY);
        }
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    private static boolean isOverStatus(int mouseX, int mouseY, int iconX, int iconY) {
        return mouseX >= iconX - 1 && mouseX < iconX + 9 && mouseY >= iconY - 1 && mouseY < iconY + 9;
    }

    /** Three lines of a list, for the presets button. */
    private static void drawPresetIcon(DrawContext context, TextRenderer textRenderer, int centerX, int centerY, int color) {
        for (int i = -1; i <= 1; i++) {
            context.fill(centerX - 4, centerY + i * 3 - 1, centerX - 2, centerY + i * 3 + 1, color);
            context.fill(centerX - 1, centerY + i * 3 - 1, centerX + 5, centerY + i * 3 + 1, color);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        // Escape closes and Tab moves the focus; while a field is focused, any other key stays in it
        // (e.g. the inventory key must not close the screen)
        if (keyCode != GLFW.GLFW_KEY_ESCAPE && keyCode != GLFW.GLFW_KEY_TAB) {
            for (TextFieldWidget field : new TextFieldWidget[]{selectorField, goalField}) {
                if (field != null && field.isFocused()) {
                    field.keyPressed(keyCode, scanCode, modifiers);
                    return true;
                }
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
