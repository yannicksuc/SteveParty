package fr.lordfinn.steveparty.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity.OutputMode;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity.Players;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity.RedstoneMode;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity.Source;
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
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

import static fr.lordfinn.steveparty.sounds.ModSounds.CLOSE_TILE_GUI_SOUND_EVENT;
import static fr.lordfinn.steveparty.sounds.ModSounds.OPEN_TILE_GUI_SOUND_EVENT;

/**
 * Goal pole base settings, in two columns.
 * <ul>
 * <li>Left, <b>points</b>: where they come from (landings on this base's poles, or a scoreboard criterion with
 * presets), and which players count, in plain words (the party's players, everyone, the players nearby) or with
 * an advanced selector (checked while typing, with what it means underneath).</li>
 * <li>Right, <b>redstone</b>: what the back port does (pause / run / nothing), what a comparator reads (a pulse per
 * point, or the progress), a button to reset now, and a reminder of the redstone (a pulse on any other side
 * resets).</li>
 * </ul>
 * The current total and whether the base counts are shown at the bottom. Enter validates, Escape cancels.
 */
public class GoalPoleBaseScreen extends HandledScreen<GoalPoleBaseScreenHandler> {
    private static final String KEY = "gui.steveparty.goal_pole_base.";
    private static final int WIDTH = 322, HEIGHT = 214;
    private static final int MARGIN = 12, GAP = 12, FIELD_HEIGHT = 18, ROW = 22;
    private static final int COLUMN = (WIDTH - 2 * MARGIN - GAP) / 2;
    private static final int RIGHT_X = MARGIN + COLUMN + GAP;
    private static final int TOP = 30;
    private static final int PRESET_SIZE = 18;
    private static final int BUTTONS_Y = HEIGHT - 30;
    /** The redstone reminder, under the reset button. */
    private static final int LEGEND_TOP = TOP + 3 * ROW + 1;
    private static final ItemStack BASE_ICON = new ItemStack(ModBlocks.GOAL_POLE_BASE);
    /** The back plug as it looks on the back of the base (lit), for the first legend row. */
    private static final net.minecraft.util.Identifier PLUG_ICON = fr.lordfinn.steveparty.Steveparty.id("textures/block/goal_pole_base_plug_on.png");
    /** The other legend rows: redstone for the reset (a pulse on any other side), a comparator for the output. */
    private static final ItemStack[] LEGEND_ITEMS = {ItemStack.EMPTY, new ItemStack(Items.REDSTONE), new ItemStack(Items.COMPARATOR)};
    private static final String[] LEGEND_KEYS = {"legend.power", "legend.reset", "legend.pulse"};

    /** Criterion presets: criterion and the key of its description. */
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

    private Source source;
    private Players players;
    /** Whether a party controller is near the base (for the party choice's meaning). */
    private final boolean partyNear;
    /** Whether the base is linked to a mini-game page: « the party » is then the one playing that page's mini-game. */
    private final boolean pageLinked;
    private RedstoneMode redstoneMode;
    private OutputMode outputMode;
    private boolean resetRequested;

    private TextFieldWidget selectorField;
    private TextFieldWidget radiusField;
    private TextFieldWidget goalField;
    private PartyButton presetsButton;
    private PartyButton doneButton;
    private Check selectorCheck = new Check(true, Text.empty());
    /** The players choice: its meaning, and whether its field (distance, selector) is valid. */
    private Check playersCheck = new Check(true, Text.empty());
    private Check goalCheck = new Check(true, Text.empty());
    private boolean openSoundPlayed = false;
    /** The objectives of the server (name, criterion), for the goal's completion. */
    private final java.util.List<String[]> objectives = new java.util.ArrayList<>();
    /** What the goal typed so far may be completed with, and the one picked (Up / Down; Tab or a click takes it). */
    private java.util.List<String> completions = java.util.List.of();
    private int completion = 0;
    /** Rows of the completion list shown under the goal field. */
    private static final int COMPLETION_ROWS = 5, COMPLETION_ROW = 11;

    public GoalPoleBaseScreen(GoalPoleBaseScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = WIDTH;
        this.backgroundHeight = HEIGHT;
        NbtCompound settings = handler.getSettings();
        this.source = GoalPoleBaseBlockEntity.readEnum(settings, "Source", Source.values(), Source.LANDINGS_HERE);
        this.players = GoalPoleBaseBlockEntity.readEnum(settings, "Players", Players.values(), Players.ALL);
        this.partyNear = settings.getBoolean("PartyNear");
        this.pageLinked = settings.getBoolean("PageLinked");
        this.redstoneMode = RedstoneMode.read(settings, "RedstoneMode", RedstoneMode.PAUSE_WHEN_POWERED);
        this.outputMode = GoalPoleBaseBlockEntity.readEnum(settings, "OutputMode", OutputMode.values(), OutputMode.PULSE);
        for (net.minecraft.nbt.NbtElement element : settings.getList("Objectives", net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) {
            NbtCompound objective = (NbtCompound) element;
            objectives.add(new String[]{objective.getString("Name"), objective.getString("Criterion")});
        }
    }

    @Override
    protected void init() {
        super.init();
        cycleButtons.clear();
        // init() runs again on every resize: keep what the player already typed / chose
        String selectorText = selectorField != null ? selectorField.getText() : handler.getSelector();
        String goalText = goalField != null ? goalField.getText() : handler.getGoal();
        String radiusText = radiusField != null ? radiusField.getText() : String.valueOf(handler.getSettings().getInt("Radius"));

        // ---- Left: points
        int lx = x + MARGIN;
        addDrawableChild(cycle(lx, y + TOP, COLUMN, "source", Source.values(), () -> source, v -> source = v));
        goalField = createField(lx, y + TOP + ROW, COLUMN - PRESET_SIZE - 4 - 12, KEY + "goal", goalText);
        goalField.setPlaceholder(Text.literal(ModScoreboardCriteria.LANDED_ON_POLE_ID).formatted(Formatting.DARK_GRAY));
        presetsButton = addDrawableChild(new PartyButton(lx + COLUMN - PRESET_SIZE, y + TOP + ROW, PRESET_SIZE, FIELD_HEIGHT,
                Text.translatable(KEY + "presets"), b -> cyclePreset(Screen.hasShiftDown() ? -1 : 1))
                .content(GoalPoleBaseScreen::drawPresetIcon));
        presetsButton.setTooltip(Tooltip.of(Text.translatable(KEY + "presets").formatted(Formatting.GOLD)
                .append("\n").append(Text.translatable(KEY + "presets.hint").formatted(Formatting.GRAY))));
        addDrawableChild(cycle(lx, y + playersY() + 12, COLUMN, "players", Players.values(), () -> players, v -> players = v));
        selectorField = createField(lx, y + playersFieldY(), COLUMN - 12, KEY + "selector", selectorText);
        selectorField.setPlaceholder(Text.literal("@a").formatted(Formatting.DARK_GRAY));
        radiusField = createField(lx, y + playersFieldY(), COLUMN - 12, KEY + "players.radius", radiusText);
        radiusField.setMaxLength(3);
        radiusField.setTextPredicate(text -> text.chars().allMatch(Character::isDigit));

        // ---- Right: redstone
        int rx = x + RIGHT_X;
        addDrawableChild(cycle(rx, y + TOP, COLUMN, "redstone_mode", RedstoneMode.values(), () -> redstoneMode, v -> redstoneMode = v));
        addDrawableChild(cycle(rx, y + TOP + ROW, COLUMN, "output_mode", OutputMode.values(), () -> outputMode, v -> outputMode = v));
        PartyButton resetButton = addDrawableChild(new PartyButton(rx, y + TOP + 2 * ROW, COLUMN, FIELD_HEIGHT,
                Text.translatable(KEY + "reset_now"), b -> {
                    resetRequested = !resetRequested;
                    b.setSelected(resetRequested);
                }));
        resetButton.setSelected(resetRequested);
        resetButton.setTooltip(Tooltip.of(Text.translatable(KEY + "reset_now.hint")));

        int buttonWidth = (WIDTH - 2 * MARGIN - 8) / 2;
        addDrawableChild(new PartyButton(x + MARGIN, y + BUTTONS_Y, buttonWidth, 20, Text.translatable("gui.steveparty.cancel"), b -> close()));
        doneButton = addDrawableChild(new PartyButton(x + MARGIN + buttonWidth + 8, y + BUTTONS_Y, buttonWidth, 20,
                Text.translatable("gui.steveparty.validate"), b -> submit()).style(PartyButton.Style.PRIMARY));

        if (source == Source.CRITERION) setInitialFocus(goalField);
        else if (players == Players.SELECTOR) setInitialFocus(selectorField);
        else if (players == Players.RADIUS) setInitialFocus(radiusField);
        refresh();

        // Play open sound once when screen opens (init() is called again on resize)
        if (!openSoundPlayed && client != null && client.player != null) {
            openSoundPlayed = true;
            client.player.playSound(OPEN_TILE_GUI_SOUND_EVENT, 1.0F, 1.0F);
        }
    }

    private int playersY() {
        return TOP + 2 * ROW + 18;
    }

    /** The field under the players choice (distance or selector). */
    private int playersFieldY() {
        return playersY() + 12 + ROW;
    }

    private boolean playersHasField() {
        return players == Players.SELECTOR || players == Players.RADIUS;
    }

    /** A button that cycles through the values of a setting (Shift: backwards), its tooltip explaining the value. */
    private <E extends Enum<E>> PartyButton cycle(int x, int y, int width, String name, E[] values,
                                                  java.util.function.Supplier<E> getter, java.util.function.Consumer<E> setter) {
        PartyButton button = new PartyButton(x, y, width, FIELD_HEIGHT, Text.empty(), b -> {
            int step = Screen.hasShiftDown() ? values.length - 1 : 1;
            setter.accept(values[(getter.get().ordinal() + step) % values.length]);
            refresh();
        });
        button.content((context, textRenderer, centerX, centerY, color) -> {
            String shown = fit(textRenderer, valueText(name, getter.get()).getString(), width - 8, true);
            context.drawText(textRenderer, shown, centerX - textRenderer.getWidth(shown) / 2, centerY - 4, color, false);
        });
        button.setTooltip(Tooltip.of(tooltipText(name, getter.get())));
        cycleButtons.add(new CycleButton(button, name, () -> getter.get()));
        return button;
    }

    /**
     * The text as it fits in {@code width}: when it is too long, only the value after "Setting: " if allowed (the
     * setting's name is in the tooltip), else cut with an ellipsis (the full text is in the tooltip too).
     */
    static String fit(TextRenderer textRenderer, String text, int width, boolean dropPrefix) {
        if (textRenderer.getWidth(text) <= width) return text;
        int colon = text.indexOf(':');
        if (dropPrefix && colon > 0 && colon < text.length() - 1) {
            String value = text.substring(colon + 1).strip();
            if (textRenderer.getWidth(value) <= width) return value;
            text = value;
        }
        return textRenderer.trimToWidth(text, width - textRenderer.getWidth("…")).stripTrailing() + "…";
    }

    private record CycleButton(PartyButton button, String name, java.util.function.Supplier<Enum<?>> value) {}

    private final java.util.List<CycleButton> cycleButtons = new java.util.ArrayList<>();

    private static String key(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static Text valueText(String name, Enum<?> value) {
        return Text.translatable(KEY + name + "." + key(value));
    }

    private static Text tooltipText(String name, Enum<?> value) {
        return Text.empty().append(Text.translatable(KEY + name).formatted(Formatting.GOLD)).append("\n")
                .append(Text.translatable(KEY + name + "." + key(value) + ".details").formatted(Formatting.GRAY))
                .append("\n").append(Text.translatable(KEY + "cycle_hint").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }

    /** A text field drawn without its own background, inside an inset drawn by {@link #drawBackground}. */
    private TextFieldWidget createField(int insetX, int insetY, int width, String key, String text) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, insetX + 5, insetY + 5, width - 8, 10, Text.translatable(key));
        field.setDrawsBackground(false);
        field.setMaxLength(GoalPoleBaseBlockEntity.MAX_STRING_LENGTH);
        field.setText(text);
        field.setChangedListener(s -> refresh());
        return addDrawableChild(field);
    }

    // ------------------------------------------------------------------ validation

    private void refresh() {
        if (selectorField == null || goalField == null) return;
        boolean criterion = source == Source.CRITERION;
        goalField.visible = criterion;
        goalField.active = criterion;
        presetsButton.visible = criterion;
        selectorField.visible = selectorField.active = players == Players.SELECTOR;
        radiusField.visible = radiusField.active = players == Players.RADIUS;
        selectorCheck = checkSelector(selectorField.getText());
        playersCheck = switch (players) {
            case SELECTOR -> selectorCheck;
            case RADIUS -> checkRadius(radiusField.getText());
            case PARTY -> new Check(true, Text.translatable(KEY + (pageLinked ? "players.party.meaning.page"
                    : partyNear ? "players.party.meaning" : "players.party.none")));
            case ALL -> new Check(true, Text.translatable(KEY + "players.all.meaning"));
        };
        goalCheck = criterion ? checkGoal(goalField.getText()) : new Check(true, Text.translatable(KEY + "source.landings_here.meaning"));
        updateCompletions();
        for (CycleButton cycle : cycleButtons) cycle.button().setTooltip(Tooltip.of(tooltipText(cycle.name(), cycle.value().get())));
        if (doneButton != null) {
            boolean valid = playersCheck.valid() && goalCheck.valid();
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

    /** A distance in blocks, 1 to {@link GoalPoleBaseBlockEntity#MAX_RADIUS}. */
    static Check checkRadius(String text) {
        int radius;
        try {
            radius = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            radius = -1;
        }
        return radius >= 1 && radius <= GoalPoleBaseBlockEntity.MAX_RADIUS
                ? new Check(true, Text.translatable(KEY + "players.radius.meaning", radius))
                : new Check(false, Text.translatable(KEY + "players.radius.invalid", GoalPoleBaseBlockEntity.MAX_RADIUS));
    }

    private Check checkGoal(String goal) {
        if (goal.isEmpty()) return new Check(false, Text.translatable(KEY + "goal.empty"));
        // An objective of the server: its scores' increases are the points
        for (String[] objective : objectives) {
            if (objective[0].equals(goal)) return new Check(true, Text.translatable(KEY + "goal.objective", objective[1]));
        }
        for (String[] preset : PRESETS) {
            if (preset[0].equals(goal)) return new Check(true, Text.translatable(KEY + preset[1]));
        }
        return GoalPoleBaseBlockEntity.parseGoal(goal).isPresent()
                ? new Check(true, Text.translatable(KEY + "goal.custom"))
                : new Check(false, Text.translatable(KEY + "goal.invalid"));
    }

    // ------------------------------------------------------------------ completion of the goal

    /**
     * What the goal typed so far may be completed with: the objectives of the server, then the presets' criteria,
     * those starting with it first, then those containing it (case ignored); nothing once it is one of them.
     */
    private void updateCompletions() {
        String typed = goalField.getText();
        String lower = typed.toLowerCase(Locale.ROOT);
        java.util.List<String> starts = new java.util.ArrayList<>(), contains = new java.util.ArrayList<>();
        java.util.List<String> all = new java.util.ArrayList<>();
        for (String[] objective : objectives) all.add(objective[0]);
        for (String[] preset : PRESETS) if (!all.contains(preset[0])) all.add(preset[0]);
        if (source == Source.CRITERION && goalField.isFocused() && !all.contains(typed)) {
            for (String candidate : all) {
                String name = candidate.toLowerCase(Locale.ROOT);
                if (name.startsWith(lower)) starts.add(candidate);
                else if (!lower.isEmpty() && name.contains(lower)) contains.add(candidate);
            }
        }
        starts.addAll(contains);
        if (!starts.equals(completions)) {
            completions = java.util.List.copyOf(starts);
            completion = 0;
        }
        showSuggestion();
    }

    /** The rest of the picked completion, in grey after the cursor, when it starts with what is typed. */
    private void showSuggestion() {
        String typed = goalField.getText();
        String picked = completions.isEmpty() ? null : completions.get(completion);
        goalField.setSuggestion(picked != null && picked.startsWith(typed) && goalField.getCursor() == typed.length()
                ? picked.substring(typed.length()) : null);
    }

    private void complete(String with) {
        goalField.setText(with);
        goalField.setCursorToEnd(false);
        completions = java.util.List.of();
        refresh();
    }

    private boolean showsCompletions() {
        return source == Source.CRITERION && goalField != null && goalField.isFocused() && !completions.isEmpty();
    }

    /** The first completion row shown (the picked one always is). */
    private int firstCompletionRow() {
        return Math.max(0, Math.min(completion - COMPLETION_ROWS + 1, completions.size() - COMPLETION_ROWS));
    }

    private int completionsX() {
        return x + MARGIN;
    }

    private int completionsY() {
        return y + TOP + ROW + FIELD_HEIGHT;
    }

    /** The completion list under the goal field, over what is there: an objective's criterion after its name. */
    private void drawCompletions(DrawContext context, int mouseX, int mouseY) {
        int first = firstCompletionRow(), rows = Math.min(COMPLETION_ROWS, completions.size());
        int width = COLUMN - PRESET_SIZE - 4, left = completionsX(), top = completionsY();
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0, 0, 300);
        context.fill(left, top, left + width, top + rows * COMPLETION_ROW + 2, 0xF0202428);
        for (int i = 0; i < rows; i++) {
            int index = first + i, rowY = top + 1 + i * COMPLETION_ROW;
            String name = completions.get(index);
            boolean hovered = mouseX >= left && mouseX < left + width && mouseY >= rowY && mouseY < rowY + COMPLETION_ROW;
            if (index == completion || hovered) context.fill(left + 1, rowY, left + width - 1, rowY + COMPLETION_ROW, 0x50FFFFFF);
            String criterion = null;
            for (String[] objective : objectives) if (objective[0].equals(name)) criterion = objective[1];
            String shown = fit(textRenderer, name, width - 6, false);
            context.drawText(textRenderer, shown, left + 3, rowY + 2, index == completion ? 0xFFFFE36A : 0xFFE0E0E0, false);
            int nameWidth = textRenderer.getWidth(shown) + 6;
            if (criterion != null && width - 6 - nameWidth > 12) {
                context.drawText(textRenderer, fit(textRenderer, criterion, width - 6 - nameWidth, false), left + 3 + nameWidth, rowY + 2, 0xFF8A949A, false);
            }
        }
        if (completions.size() > COMPLETION_ROWS) {
            String more = (first + rows) + "/" + completions.size();
            context.drawText(textRenderer, more, left + width - 3 - textRenderer.getWidth(more), top + rows * COMPLETION_ROW - 8, 0xFF8A949A, false);
        }
        matrices.pop();
    }

    /** @return the completion under the mouse, or -1. */
    private int completionAt(double mouseX, double mouseY) {
        if (!showsCompletions()) return -1;
        int rows = Math.min(COMPLETION_ROWS, completions.size()), width = COLUMN - PRESET_SIZE - 4;
        if (mouseX < completionsX() || mouseX >= completionsX() + width || mouseY < completionsY() + 1) return -1;
        int row = (int) Math.floor((mouseY - completionsY() - 1) / COMPLETION_ROW);
        return row >= 0 && row < rows ? firstCompletionRow() + row : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = completionAt(mouseX, mouseY);
        if (row >= 0) {
            complete(completions.get(row));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        if (!playersCheck.valid() || !goalCheck.valid()) return;
        NbtCompound settings = new NbtCompound();
        settings.putString("Source", source.name());
        settings.putString("Criterion", source == Source.CRITERION ? goalField.getText() : handler.getGoal());
        settings.putString("Selector", selectorField.getText());
        settings.putString("Players", players.name());
        if (players == Players.RADIUS) settings.putInt("Radius", Integer.parseInt(radiusField.getText()));
        settings.putString("RedstoneMode", redstoneMode.name());
        settings.putString("OutputMode", outputMode.name());
        settings.putBoolean("Reset", resetRequested);
        ClientPlayNetworking.send(new GoalPoleBasePayload(handler.getPos(), settings));
        close();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        PartyGui.panel(context, x, y, WIDTH, HEIGHT, PartyGui.PANEL);
        PartyGui.titlePlate(context, textRenderer, x + WIDTH / 2, y - 11, 18, title, PartyGui.BRICK);
        context.drawItem(BASE_ICON, PartyGui.titlePlateIconX(textRenderer, x + WIDTH / 2, 18, title), y - 8);
        // Column separator
        context.fill(x + RIGHT_X - GAP / 2, y + TOP - 12, x + RIGHT_X - GAP / 2 + 1, y + BUTTONS_Y - 20, 0xFF9A9A9A);
        context.fill(x + RIGHT_X - GAP / 2 + 1, y + TOP - 12, x + RIGHT_X - GAP / 2 + 2, y + BUTTONS_Y - 20, 0xFFFFFFFF);

        if (source == Source.CRITERION) {
            drawField(context, x + MARGIN, y + TOP + ROW, COLUMN - PRESET_SIZE - 4, goalField, goalCheck);
        }
        if (playersHasField()) {
            drawField(context, x + MARGIN, y + playersFieldY(), COLUMN,
                    players == Players.SELECTOR ? selectorField : radiusField, playersCheck);
        }

        // Redstone reminder: an icon per port, the details in the row's tooltip
        int legendY = y + LEGEND_TOP + 1;
        for (int i = 0; i < LEGEND_KEYS.length; i++) {
            int rowY = legendY + i * 14;
            if (legendRowAt(mouseX, mouseY) == i) {
                context.fill(x + RIGHT_X, rowY - 1, x + RIGHT_X + COLUMN, rowY + 13, 0x40FFFFFF);
            }
            // Each icon framed like a slot: the plug at twice its pixel size, the items scaled down
            int iconX = x + RIGHT_X + 1, iconY = rowY + 1;
            context.fill(iconX - 1, iconY - 1, iconX + 13, iconY + 11, 0xFF3A3A3A);
            if (LEGEND_ITEMS[i].isEmpty()) {
                RenderSystem.enableBlend();
                context.drawTexture(PLUG_ICON, iconX + 2, iconY + 2, 8, 6, 4, 6, 4, 3, 16, 16);
                RenderSystem.disableBlend();
            } else {
                var matrices = context.getMatrices();
                matrices.push();
                matrices.translate(iconX, iconY - 1, 0);
                matrices.scale(0.75f, 0.75f, 1f);
                context.drawItem(LEGEND_ITEMS[i], 0, 0);
                matrices.pop();
            }
            String line = fit(textRenderer, Text.translatable(KEY + LEGEND_KEYS[i]).getString(), COLUMN - 16, false);
            context.drawText(textRenderer, line, x + RIGHT_X + 16, rowY + 3, PartyGui.TEXT_DARK, false);
        }
    }

    /** @return the legend row under the mouse, or -1. */
    private int legendRowAt(double mouseX, double mouseY) {
        if (mouseX < x + RIGHT_X || mouseX >= x + RIGHT_X + COLUMN) return -1;
        int row = (int) Math.floor((mouseY - (y + LEGEND_TOP)) / 14);
        return row >= 0 && row < LEGEND_KEYS.length ? row : -1;
    }

    private static void drawField(DrawContext context, int x, int y, int width, TextFieldWidget field, Check check) {
        PartyGui.inset(context, x, y, width, FIELD_HEIGHT, 0xFF3B4247, field != null && field.isFocused(), !check.valid());
        PartyGui.statusIcon(context, x + width - 11, y + 6, check.valid());
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, Text.translatable(KEY + "points"), MARGIN, TOP - 11, PartyGui.TEXT_DARK, false);
        context.drawText(textRenderer, Text.translatable(KEY + "redstone"), RIGHT_X, TOP - 11, PartyGui.TEXT_DARK, false);
        if (source == Source.CRITERION) {
            drawMeaning(context, goalCheck, MARGIN, TOP + ROW + FIELD_HEIGHT + 3);
        } else {
            context.drawTextWrapped(textRenderer, Text.translatable(KEY + "source.landings_here.meaning"), MARGIN, TOP + ROW + 3,
                    COLUMN, PartyGui.TEXT_SOFT);
        }
        context.drawText(textRenderer, Text.translatable(KEY + "players"), MARGIN, playersY(), PartyGui.TEXT_DARK, false);
        if (playersHasField()) {
            drawMeaning(context, playersCheck, MARGIN, playersFieldY() + FIELD_HEIGHT + 3);
        } else {
            // No field under the choice: room for its meaning on two lines
            context.drawTextWrapped(textRenderer, playersCheck.meaning(), MARGIN, playersFieldY() + 3, COLUMN, PartyGui.TEXT_SOFT);
        }

        // Status line: total and whether the base counts
        NbtCompound settings = handler.getSettings();
        boolean active = settings.getBoolean("Active");
        MutableText status = Text.translatable(KEY + "status", settings.getLong("Total"))
                .append("  ").append(Text.translatable(KEY + (active ? "status.active" : "status.paused"))
                        .formatted(active ? Formatting.DARK_GREEN : Formatting.DARK_RED));
        context.drawText(textRenderer, status, MARGIN, BUTTONS_Y - 14, PartyGui.TEXT_DARK, false);
    }

    /** What the field means, under it (trimmed to the column; the full text is in the tooltip of the status icon). */
    private void drawMeaning(DrawContext context, Check check, int x, int y) {
        String shown = fit(textRenderer, check.meaning().getString(), COLUMN, false);
        context.drawText(textRenderer, shown, x, y, check.valid() ? PartyGui.TEXT_SOFT : PartyGui.TEXT_ERROR, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (showsCompletions()) {
            drawCompletions(context, mouseX, mouseY);
            if (completionAt(mouseX, mouseY) >= 0) {
                context.drawTooltip(textRenderer, Text.translatable(KEY + "goal.completion.hint").formatted(Formatting.GRAY), mouseX, mouseY);
            }
            return;
        }
        if (source == Source.CRITERION && isOverStatus(mouseX, mouseY, x + MARGIN + COLUMN - PRESET_SIZE - 4 - 11, y + TOP + ROW + 6)) {
            context.drawTooltip(textRenderer, goalCheck.meaning(), mouseX, mouseY);
        } else if (playersHasField() && isOverStatus(mouseX, mouseY, x + MARGIN + COLUMN - 11, y + playersFieldY() + 6)) {
            context.drawTooltip(textRenderer, playersCheck.meaning(), mouseX, mouseY);
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
        // The goal's completion: Tab takes the picked one, Up and Down pick another
        if (showsCompletions()) {
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                complete(completions.get(completion));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
                completion = Math.floorMod(completion + (keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1), completions.size());
                showSuggestion();
                return true;
            }
        }
        // Escape closes and Tab moves the focus; while a field is focused, any other key stays in it
        // (e.g. the inventory key must not close the screen)
        if (keyCode != GLFW.GLFW_KEY_ESCAPE && keyCode != GLFW.GLFW_KEY_TAB) {
            for (TextFieldWidget field : new TextFieldWidget[]{selectorField, radiusField, goalField}) {
                if (field != null && field.isFocused() && field.visible) {
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
