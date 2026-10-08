package fr.lordfinn.steveparty.client.screens;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity;

import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity.Players;

import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity.Source;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleSearch;
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
import net.minecraft.block.Block;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.stat.StatType;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;
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
 * <li>Right, <b>redstone</b>: a button to reset now, and a reminder of the redstone (a signal of 1-14 pauses, 15
 * also resets; a comparator reads a pulse per point on the base, the progress on the pole).</li>
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
    private static final int LEGEND_TOP = TOP + ROW + 1;
    private static final ItemStack BASE_ICON = new ItemStack(ModBlocks.GOAL_POLE_BASE);
    /** The legend rows: a weak signal (pause), a full one (pause and reset), the comparators on the base and on the pole. */
    private static final ItemStack[] LEGEND_ITEMS = {new ItemStack(Items.REDSTONE), new ItemStack(Items.REDSTONE_BLOCK),
            new ItemStack(Items.COMPARATOR), new ItemStack(ModBlocks.GOAL_POLE)};
    private static final String[] LEGEND_KEYS = {"legend.pause", "legend.reset", "legend.pulse", "legend.progress"};

    /**
     * The common goals (offered when nothing is typed, and cycled by the presets button): value, icon, key of its
     * label (null: the statistic's own name), key of its meaning (null: a point per increase), keywords' key.
     */
    private static final Object[][] PRESETS = {
            {ModScoreboardCriteria.LANDED_ON_POLE_ID, ModBlocks.GOAL_POLE.asItem(), "goal.landed_on_pole.label", "goal.landed_on_pole", "landed_on_pole"},
            {"minecraft.custom:minecraft.jump", Items.RABBIT_FOOT, null, null, "jump"},
            {"deathCount", Items.SKELETON_SKULL, "goal.deaths", null, "deaths"},
            {"minecraft.custom:minecraft.mob_kills", Items.ZOMBIE_HEAD, null, null, "mob_kills"},
            {"playerKillCount", Items.PLAYER_HEAD, "goal.player_kills", null, "player_kills"},
            {"totalKillCount", Items.IRON_SWORD, "goal.kills", null, "kills"},
            {"minecraft.custom:minecraft.walk_one_cm", Items.LEATHER_BOOTS, null, null, "walk"},
            {"dummy", Items.COMMAND_BLOCK, "goal.dummy.label", "goal.dummy", "dummy"},
    };
    /** The other simple criteria of the scoreboard: name and icon (label: {@code goal.criterion.<name>}). */
    private static final Object[][] CRITERIA = {
            {"trigger", Items.LEVER}, {"health", Items.GLISTERING_MELON_SLICE}, {"xp", Items.EXPERIENCE_BOTTLE},
            {"level", Items.EXPERIENCE_BOTTLE}, {"food", Items.COOKED_BEEF}, {"air", Items.GLASS_BOTTLE},
            {"armor", Items.IRON_CHESTPLATE},
    };

    /** A goal of the search: its icon and what it means under the field. */
    private record Goal(Item icon, Text meaning) {}

    /** What a field's content means, and whether it can be saved. */
    private record Check(boolean valid, Text meaning) {}

    private Source source;
    private Players players;
    /** Whether a party controller is near the base (for the party choice's meaning). */
    private final boolean partyNear;
    /** Whether the base is linked to a mini-game page: « the party » is then the one playing that page's mini-game. */
    private final boolean pageLinked;
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
    /** The icon of the goal in the field (instead of a check mark), null if none. */
    private Item goalIcon;
    private boolean openSoundPlayed = false;
    /** The objectives of the server (name, criterion, display name), for the goal's completion. */
    private final java.util.List<String[]> objectives = new java.util.ArrayList<>();
    /** Every goal that can be searched: objectives, common goals, criteria, statistics (in that order). */
    private final java.util.List<GoalPoleSearch.Entry<Goal>> goals = new java.util.ArrayList<>();
    /** The goal last taken from the list: the field shows its label, its value is saved. */
    private GoalPoleSearch.Entry<Goal> picked;
    /** What the goal typed so far may be completed with, and the one picked (Up / Down; Tab, Enter or a click takes it). */
    private java.util.List<GoalPoleSearch.Entry<Goal>> completions = java.util.List.of();
    private int completion = 0;
    /** Whether the goal field was focused when the completions were last computed. */
    private boolean completionsFocused;
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

        for (net.minecraft.nbt.NbtElement element : settings.getList("Objectives", net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) {
            NbtCompound objective = (NbtCompound) element;
            objectives.add(new String[]{objective.getString("Name"), objective.getString("Criterion"), objective.getString("Display")});
        }
        buildGoals();
        picked = GoalPoleSearch.byValue(handler.getGoal(), goals);
    }

    // ------------------------------------------------------------------ the goals to search

    private void buildGoals() {
        java.util.List<GoalPoleSearch.Entry<Goal>> others = new java.util.ArrayList<>();
        java.util.Set<String> added = new java.util.HashSet<>();
        Text perIncrease = Text.translatable(KEY + "goal.stat.meaning");
        for (Object[] preset : PRESETS) {
            String value = (String) preset[0];
            String label = preset[2] != null ? Text.translatable(KEY + preset[2]).getString()
                    : customStatLabel(value.substring(value.indexOf(':') + 1));
            others.add(new GoalPoleSearch.Entry<>(value, label, keywords("goal.keywords." + preset[4]), GoalPoleSearch.GROUP_COMMON,
                    new Goal((Item) preset[1], preset[3] != null ? Text.translatable(KEY + preset[3]) : perIncrease)));
            added.add(value);
        }
        for (Object[] criterion : CRITERIA) {
            String value = (String) criterion[0];
            others.add(new GoalPoleSearch.Entry<>(value, Text.translatable(KEY + "goal.criterion." + value).getString(), "",
                    GoalPoleSearch.GROUP_CRITERION, new Goal((Item) criterion[1], perIncrease)));
        }
        for (StatType<?> type : Registries.STAT_TYPE) addStats(type, others, added, perIncrease);
        for (String[] objective : objectives) {
            GoalPoleSearch.Entry<Goal> criterion = GoalPoleSearch.byValue(objective[1], others);
            String criterionLabel = criterion != null ? criterion.label() : objective[1];
            String label = objective[2].isEmpty() ? objective[0] : objective[2];
            goals.add(new GoalPoleSearch.Entry<>(objective[0], label, objective[0] + " " + criterionLabel + " " + objective[1],
                    GoalPoleSearch.GROUP_OBJECTIVE, new Goal(Items.NAME_TAG, Text.translatable(KEY + "goal.objective", criterionLabel))));
        }
        goals.addAll(others);
    }

    /** The name of a custom statistic ({@code minecraft.jump}), as in the statistics screen. */
    private static String customStatLabel(String id) {
        return Text.translatable("stat." + id).getString();
    }

    /** Extra words to find a goal with, in the client's language (none if the key is missing). */
    private static String keywords(String key) {
        return I18n.hasTranslation(KEY + key) ? I18n.translate(KEY + key) : "";
    }

    /** The statistics of a type, named as {@code Stat.getName} does ({@code minecraft.mined:minecraft.stone}). */
    private static <T> void addStats(StatType<T> type, java.util.List<GoalPoleSearch.Entry<Goal>> into,
                                     java.util.Set<String> added, Text perIncrease) {
        Identifier typeId = Registries.STAT_TYPE.getId(type);
        if (typeId == null) return;
        String typeName = typeId.toString().replace(':', '.');
        boolean known = I18n.hasTranslation(KEY + "stat." + typeId.getPath());
        String words = known ? keywords("stat." + typeId.getPath() + ".keywords") : "";
        for (T value : type.getRegistry()) {
            Identifier valueId = type.getRegistry().getId(value);
            if (valueId == null) continue;
            String id = typeName + ":" + valueId.toString().replace(':', '.');
            if (added.contains(id)) continue;
            Text name;
            Item icon;
            if (type == Stats.CUSTOM) {
                into.add(new GoalPoleSearch.Entry<>(id, customStatLabel(valueId.toString().replace(':', '.')), "",
                        GoalPoleSearch.GROUP_CRITERION, new Goal(Items.PAPER, perIncrease)));
                continue;
            } else if (value instanceof Block block) {
                if (block.getDefaultState().isAir()) continue;
                name = block.getName();
                icon = block.asItem() == Items.AIR ? Items.PAPER : block.asItem();
            } else if (value instanceof Item item) {
                if (item == Items.AIR || (type == Stats.BROKEN && !item.getDefaultStack().isDamageable())) continue;
                name = item.getName();
                icon = item;
            } else if (value instanceof EntityType<?> entity) {
                name = entity.getName();
                // the classic Glandouille's egg, not the last one registered for its type
                SpawnEggItem egg = entity == fr.lordfinn.steveparty.entities.ModEntities.GLANDOUILLE
                        ? (SpawnEggItem) fr.lordfinn.steveparty.items.ModItems.GLANDOUILLE_SPAWN_EGG : SpawnEggItem.forEntity(entity);
                icon = egg != null ? egg : Items.PAPER;
            } else {
                continue;
            }
            String label = known ? Text.translatable(KEY + "stat." + typeId.getPath(), name).getString()
                    : type.getName().getString() + ": " + name.getString();
            into.add(new GoalPoleSearch.Entry<>(id, label, words, GoalPoleSearch.GROUP_STAT, new Goal(icon, perIncrease)));
        }
    }

    /** The goal the field stands for: the one taken from the list, else the one with that label or value; null if none. */
    private GoalPoleSearch.Entry<Goal> currentGoal() {
        String text = goalField.getText();
        if (picked != null && text.equals(picked.label())) return picked;
        return GoalPoleSearch.resolve(text, goals);
    }

    /** The value saved for the goal: the goal's, else what is typed (a criterion of another mod, for instance). */
    private String goalValue() {
        GoalPoleSearch.Entry<Goal> goal = currentGoal();
        return goal != null ? goal.value() : goalField.getText().strip();
    }

    @Override
    protected void init() {
        super.init();
        cycleButtons.clear();
        // init() runs again on every resize: keep what the player already typed / chose
        String selectorText = selectorField != null ? selectorField.getText() : handler.getSelector();
        String goalText = goalField != null ? goalField.getText() : picked != null ? picked.label() : handler.getGoal();
        String radiusText = radiusField != null ? radiusField.getText() : String.valueOf(handler.getSettings().getInt("Radius"));

        // ---- Left: points
        int lx = x + MARGIN;
        addDrawableChild(cycle(lx, y + TOP, COLUMN, "source", Source.values(), () -> source, v -> source = v));
        goalField = createField(lx, y + TOP + ROW, COLUMN - PRESET_SIZE - 4 - 12, KEY + "goal", goalText);
        goalField.setPlaceholder(Text.translatable(KEY + "goal.placeholder").formatted(Formatting.DARK_GRAY));
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
        PartyButton resetButton = addDrawableChild(new PartyButton(rx, y + TOP, COLUMN, FIELD_HEIGHT,
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
        goalIcon = null;
        if (goal.isBlank()) return new Check(false, Text.translatable(KEY + "goal.empty"));
        GoalPoleSearch.Entry<Goal> found = currentGoal();
        if (found != null) {
            goalIcon = found.data().icon();
            return new Check(true, found.data().meaning());
        }
        // Typed as is: a criterion the list does not know (another mod's, a team's...)
        return GoalPoleBaseBlockEntity.parseGoal(goal.strip()).isPresent()
                ? new Check(true, Text.translatable(KEY + "goal.custom"))
                : new Check(false, Text.translatable(KEY + "goal.invalid"));
    }

    // ------------------------------------------------------------------ completion of the goal

    /**
     * What the goal typed so far may be completed with ({@link GoalPoleSearch}: objectives first, then the common
     * goals, criteria and statistics); with nothing typed, the objectives and the common goals; nothing once the
     * field is one of them.
     */
    private void updateCompletions() {
        completionsFocused = goalField.isFocused();
        String typed = goalField.getText();
        GoalPoleSearch.Entry<Goal> current = currentGoal();
        java.util.List<GoalPoleSearch.Entry<Goal>> found = source == Source.CRITERION && completionsFocused
                && (current == null || !GoalPoleSearch.normalize(typed).equals(current.normalizedLabel()))
                ? GoalPoleSearch.search(typed, goals) : java.util.List.of();
        if (!found.equals(completions)) {
            completions = found;
            completion = 0;
        }
        showSuggestion();
    }

    /** The rest of the picked completion, in grey after the cursor, when it starts with what is typed. */
    private void showSuggestion() {
        String typed = goalField.getText();
        String label = completions.isEmpty() ? null : completions.get(completion).label();
        goalField.setSuggestion(label != null && !typed.isEmpty() && goalField.getCursor() == typed.length()
                && label.toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))
                ? label.substring(typed.length()) : null);
    }

    private void complete(GoalPoleSearch.Entry<Goal> with) {
        picked = with;
        goalField.setText(with.label());
        goalField.setCursorToEnd(false);
        completions = java.util.List.of();
        refresh();
    }

    /** The technical id of a goal, discreetly (dark grey), for tooltips. */
    private static Text idLine(GoalPoleSearch.Entry<Goal> goal) {
        return Text.literal(goal.value()).formatted(Formatting.DARK_GRAY);
    }

    /** An item drawn small (scale of 16 px), e.g. a goal's icon in its field or in the list. */
    private static void drawSmallItem(DrawContext context, Item item, int x, int y, float scale) {
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(scale, scale, 1f);
        context.drawItem(new ItemStack(item), 0, 0);
        matrices.pop();
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

    /** The completion list under the goal field, over what is there: each goal's icon and readable name. */
    private void drawCompletions(DrawContext context, int mouseX, int mouseY) {
        int first = firstCompletionRow(), rows = Math.min(COMPLETION_ROWS, completions.size());
        int width = COLUMN - PRESET_SIZE - 4, left = completionsX(), top = completionsY();
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0, 0, 300);
        context.fill(left, top, left + width, top + rows * COMPLETION_ROW + 2, 0xF0202428);
        for (int i = 0; i < rows; i++) {
            int index = first + i, rowY = top + 1 + i * COMPLETION_ROW;
            GoalPoleSearch.Entry<Goal> goal = completions.get(index);
            boolean hovered = mouseX >= left && mouseX < left + width && mouseY >= rowY && mouseY < rowY + COMPLETION_ROW;
            if (index == completion || hovered) context.fill(left + 1, rowY, left + width - 1, rowY + COMPLETION_ROW, 0x50FFFFFF);
            drawSmallItem(context, goal.data().icon(), left + 2, rowY, 0.625f);
            String shown = fit(textRenderer, goal.label(), width - 17, false);
            context.drawText(textRenderer, shown, left + 14, rowY + 2,
                    index == completion ? 0xFFFFE36A : goal.group() == GoalPoleSearch.GROUP_OBJECTIVE ? 0xFFB8E0FF : 0xFFE0E0E0, false);
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
        GoalPoleSearch.Entry<Goal> goal = currentGoal();
        int current = -1;
        for (int i = 0; i < PRESETS.length; i++) {
            if (goal != null && PRESETS[i][0].equals(goal.value())) current = i;
        }
        int next = current < 0 ? (direction > 0 ? 0 : PRESETS.length - 1)
                : Math.floorMod(current + direction, PRESETS.length);
        GoalPoleSearch.Entry<Goal> preset = GoalPoleSearch.byValue((String) PRESETS[next][0], goals);
        if (preset != null) complete(preset);
    }

    private void submit() {
        refresh();
        if (!playersCheck.valid() || !goalCheck.valid()) return;
        NbtCompound settings = new NbtCompound();
        settings.putString("Source", source.name());
        settings.putString("Criterion", source == Source.CRITERION ? goalValue() : handler.getGoal());
        settings.putString("Selector", selectorField.getText());
        settings.putString("Players", players.name());
        if (players == Players.RADIUS) settings.putInt("Radius", Integer.parseInt(radiusField.getText()));
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
            int width = COLUMN - PRESET_SIZE - 4;
            if (goalCheck.valid() && goalIcon != null) {
                // The goal's icon instead of a check mark
                PartyGui.inset(context, x + MARGIN, y + TOP + ROW, width, FIELD_HEIGHT, 0xFF3B4247, goalField.isFocused(), false);
                drawSmallItem(context, goalIcon, x + MARGIN + width - 12, y + TOP + ROW + 3, 0.75f);
            } else {
                drawField(context, x + MARGIN, y + TOP + ROW, width, goalField, goalCheck);
            }
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
            // Each icon framed like a slot, the items scaled down
            int iconX = x + RIGHT_X + 1, iconY = rowY + 1;
            context.fill(iconX - 1, iconY - 1, iconX + 13, iconY + 11, 0xFF3A3A3A);
            var matrices = context.getMatrices();
            matrices.push();
            matrices.translate(iconX, iconY - 1, 0);
            matrices.scale(0.75f, 0.75f, 1f);
            context.drawItem(LEGEND_ITEMS[i], 0, 0);
            matrices.pop();
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
        // Focusing the goal field (click, Tab) shows its completions, even with nothing typed
        if (goalField != null && goalField.isFocused() != completionsFocused) updateCompletions();
        super.render(context, mouseX, mouseY, delta);
        if (showsCompletions()) {
            drawCompletions(context, mouseX, mouseY);
            int row = completionAt(mouseX, mouseY);
            if (row >= 0) {
                context.drawTooltip(textRenderer, java.util.List.of(
                        Text.translatable(KEY + "goal.completion.hint").formatted(Formatting.GRAY), idLine(completions.get(row))), mouseX, mouseY);
            }
            return;
        }
        if (source == Source.CRITERION && isOverStatus(mouseX, mouseY, x + MARGIN + COLUMN - PRESET_SIZE - 4 - 11, y + TOP + ROW + 6)) {
            GoalPoleSearch.Entry<Goal> goal = currentGoal();
            context.drawTooltip(textRenderer, goal != null ? java.util.List.of(goalCheck.meaning(), idLine(goal))
                    : java.util.List.of(goalCheck.meaning()), mouseX, mouseY);
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
        boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        if (enter && !showsCompletions()) {
            submit();
            return true;
        }
        // The goal's completion: Tab or Enter takes the picked one, Up and Down pick another
        if (showsCompletions()) {
            if (keyCode == GLFW.GLFW_KEY_TAB || enter) {
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
