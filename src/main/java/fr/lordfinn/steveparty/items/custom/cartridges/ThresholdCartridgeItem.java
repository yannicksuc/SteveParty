package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.InfoModule;
import fr.lordfinn.steveparty.service.TurnMoves;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Threshold obstacle Cartridge: a wall on the path. A token reaching its board space during a move goes on only if
 * the roll of that move meets its condition ({@link Operator} and value: « ≥ 7 », « Double »...); otherwise its move
 * ends there (see ThresholdTileBehavior). The roll read is the one of the move: the dice's total, modules and
 * power-ups included. Its tile is steel blue; a dye on it changes that.
 * <p>
 * Two modes ({@link Mode}): tested at each passage (a wall, by default), or a barrier cleared once: a token stopped by
 * it stays blocked there, each of its next turns is a new try with its throw, until it clears it. A barrier opens
 * for every token at its first success ({@link Opening#SHARED}), or each token has to clear it once
 * ({@link Opening#EACH}): see ThresholdGates.
 * <p>
 * Settings ({@link ModComponents#BOARD_CARTRIDGE_SETTINGS}): {@link #KIND} (0 the total, 1 a double, 2 a triple),
 * {@link #SIGN} (how the total compares: an {@link Operator}'s index, ≥ by default), {@link #VALUE} (1 to
 * {@link #MAX_VALUE}, {@value #DEFAULT_VALUE} by default), {@link #MODE} (a {@link Mode}'s index, each passage by
 * default) and {@link #OPENING} (an {@link Opening}'s index, for everyone by default).
 */
public class ThresholdCartridgeItem extends BoardRuleCartridgeItem {
    /** Its tile's steel blue. */
    public static final int COLOR = 0x6F8FAF;
    public static final int MAX_VALUE = 30, DEFAULT_VALUE = 7;
    public static final String KIND = "kind", SIGN = "sign", VALUE = "value", MODE = "mode", OPENING = "opening";

    /** When the condition is tested. */
    public enum Mode {
        /** At each passage: a token stopped there leaves it freely on its next move. */
        EACH_PASSAGE,
        /** Cleared once: until then a token stopped there stays blocked, each of its turns is a new try. */
        BARRIER;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Who a barrier lets through once cleared. */
    public enum Opening {
        /** Its first success opens it for every token, for the rest of the party. */
        SHARED,
        /** Each token has to clear it once. */
        EACH;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** How the roll is compared. */
    public enum Operator {
        AT_LEAST(true), AT_MOST(true), MORE(true), LESS(true), EXACTLY(true), DOUBLE(false), TRIPLE(false);

        private final boolean value;

        Operator(boolean value) {
            this.value = value;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Needs a value (not Double / Triple). */
        public boolean hasValue() {
            return value;
        }

        /** True if a roll of {@code total} walking steps, its dice showing {@code roll}'s faces, gets over. */
        public boolean passes(TurnMoves.Roll roll, int value) {
            int total = roll.total();
            return switch (this) {
                case AT_LEAST -> total >= value;
                case AT_MOST -> total <= value;
                case MORE -> total > value;
                case LESS -> total < value;
                case EXACTLY -> total == value;
                case DOUBLE -> roll.hasSame(2);
                case TRIPLE -> roll.hasSame(3);
            };
        }

        /** What the obstacle shows: « 7 or more », « DOUBLE » (translated, in words: every font has them). */
        public Text label(int value) {
            return this.value ? Text.translatable("gui.steveparty.threshold." + id(), value)
                    : Text.translatable("gui.steveparty.threshold." + id());
        }
    }

    private static final String K = MENU_KEY + "threshold.";

    /** What the roll is tested on: its total (compared with a sign), a pair, three of a kind. */
    private static final String[] KINDS = {"total", "double", "triple"};
    /** The signs of the total, in the order of {@link Operator}. */
    private static final int SIGNS = 5;
    private static final Setting KIND_SETTING = new Setting(KIND, 0, 0, KINDS.length - 1);
    private static final Setting SIGN_SETTING = new Setting(SIGN, 0, 0, SIGNS - 1);
    private static final Setting VALUE_SETTING = new Setting(VALUE, DEFAULT_VALUE, 1, MAX_VALUE);
    private static final Setting MODE_SETTING = new Setting(MODE, 0, 0, Mode.values().length - 1);
    private static final Setting OPENING_SETTING = new Setting(OPENING, 0, 0, Opening.values().length - 1);
    private static final List<CartridgeModule> MODULES = modules0();

    private static List<CartridgeModule> modules0() {
        List<ChoiceModule.Option> kinds = new ArrayList<>();
        for (String kind : KINDS) kinds.add(ChoiceModule.Option.tipped(K + "kind." + kind));
        List<ChoiceModule.Option> signs = new ArrayList<>();
        for (int i = 0; i < SIGNS; i++) {
            signs.add(ChoiceModule.Option.tipped(K + "operator." + Operator.values()[i].id()));
        }
        List<ChoiceModule.Option> modes = new ArrayList<>();
        for (Mode mode : Mode.values()) modes.add(ChoiceModule.Option.tipped(K + "mode." + mode.id()));
        List<ChoiceModule.Option> openings = new ArrayList<>();
        for (Opening opening : Opening.values()) openings.add(ChoiceModule.Option.tipped(K + "opening." + opening.id()));
        // What it does in the chosen mode; two sections, each under one title: « Condition » (what, then the sign and the
        // value, only for a total) and « Mode » (each passage or barrier, then who a barrier lets through)
        return List.of(
                new InfoModule("description", null, 3, context -> List.of(InfoModule.Line.of(
                        Text.translatable(MENU_KEY + "desc.threshold_cartridge" + hint(context.stack()))))),
                KIND_SETTING.choice("kind", K + "kind", kinds),
                SIGN_SETTING.choice("operator", null, signs, stack -> operator(stack).hasValue()),
                VALUE_SETTING.number("value", null, stack -> COLOR, stack -> operator(stack).hasValue()),
                MODE_SETTING.choice("mode", K + "mode", modes),
                OPENING_SETTING.choice("opening", null, openings, ThresholdCartridgeItem::isBarrier));
    }

    /** The description's suffix: none at each passage, the barrier and who it lets through. */
    private static String hint(ItemStack stack) {
        return isBarrier(stack) ? ".barrier." + opening(stack).id() : "";
    }

    public ThresholdCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_THRESHOLD;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int tileColor() {
        return COLOR;
    }

    public static Operator operator(ItemStack stack) {
        int kind = KIND_SETTING.get(stack);
        return kind == 0 ? Operator.values()[SIGN_SETTING.get(stack)] : kind == 1 ? Operator.DOUBLE : Operator.TRIPLE;
    }

    public static int value(ItemStack stack) {
        return VALUE_SETTING.get(stack);
    }

    public static Mode mode(ItemStack stack) {
        return Mode.values()[MODE_SETTING.get(stack)];
    }

    /** Cleared once (not tested at each passage). */
    public static boolean isBarrier(ItemStack stack) {
        return mode(stack) == Mode.BARRIER;
    }

    public static Opening opening(ItemStack stack) {
        return Opening.values()[OPENING_SETTING.get(stack)];
    }

    /** A barrier whose first success opens it for every token. */
    public static boolean opensForAll(ItemStack stack) {
        return isBarrier(stack) && opening(stack) == Opening.SHARED;
    }

    /** True if {@code roll} gets over the obstacle set by {@code stack}. */
    public static boolean passes(ItemStack stack, TurnMoves.Roll roll) {
        return operator(stack).passes(roll, value(stack));
    }

    /** What the obstacle shows over its board space. */
    public static Text label(ItemStack stack) {
        return operator(stack).label(value(stack));
    }

    /** A cartridge with this condition (tests, commands). */
    public static ItemStack with(Item item, Operator operator, int value) {
        ItemStack stack = new ItemStack(item);
        putSetting(stack, KIND, operator == Operator.DOUBLE ? 1 : operator == Operator.TRIPLE ? 2 : 0);
        if (operator.hasValue()) putSetting(stack, SIGN, operator.ordinal());
        putSetting(stack, VALUE, value);
        return stack;
    }

    /** A barrier with this condition, opened for all or for each (tests, commands). */
    public static ItemStack barrier(Item item, Operator operator, int value, Opening opening) {
        ItemStack stack = with(item, operator, value);
        putSetting(stack, MODE, Mode.BARRIER.ordinal());
        putSetting(stack, OPENING, opening.ordinal());
        return stack;
    }

    @Override
    protected void appendState(ItemStack stack, Tooltips tips) {
        tips.state("tooltip.steveparty.threshold_cartridge.condition", Tooltips.rgb(label(stack), 0xA9C6E3));
        if (isBarrier(stack)) tips.state("tooltip.steveparty.threshold_cartridge.mode",
                Tooltips.setting(Text.translatable(K + "opening." + opening(stack).id() + ".short")));
    }
}
