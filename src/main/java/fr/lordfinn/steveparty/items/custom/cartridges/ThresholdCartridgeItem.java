package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.service.TurnMoves;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * The Threshold obstacle Cartridge: a wall on the path. A token reaching its board space during a move goes on only if
 * the roll of that move meets its condition ({@link Operator} and value: « ≥ 7 », « Double »...); otherwise its move
 * ends there (see ThresholdTileBehavior). The roll read is the one of the move: the dice's total, modules and
 * power-ups included. Its tile is steel blue; a dye on it changes that.
 * <p>
 * Settings ({@link ModComponents#BOARD_CARTRIDGE_SETTINGS}): {@link #KIND} (0 the total, 1 a double, 2 a triple),
 * {@link #SIGN} (how the total compares: an {@link Operator}'s index, ≥ by default) and {@link #VALUE} (1 to
 * {@link #MAX_VALUE}, {@value #DEFAULT_VALUE} by default).
 */
public class ThresholdCartridgeItem extends BoardRuleCartridgeItem {
    /** Its tile's steel blue. */
    public static final int COLOR = 0x6F8FAF;
    public static final int MAX_VALUE = 30, DEFAULT_VALUE = 7;
    public static final String KIND = "kind", SIGN = "sign", VALUE = "value";

    /** How the roll is compared. */
    public enum Operator {
        AT_LEAST(true), AT_MOST(true), MORE(true), LESS(true), EXACTLY(true), DOUBLE(false), TRIPLE(false);

        private final boolean value;

        Operator(boolean value) {
            this.value = value;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
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
    private static final List<CartridgeModule> MODULES = modules0();

    private static List<CartridgeModule> modules0() {
        List<ChoiceModule.Option> kinds = new ArrayList<>();
        for (String kind : KINDS) kinds.add(ChoiceModule.Option.tipped(K + "kind." + kind));
        List<ChoiceModule.Option> signs = new ArrayList<>();
        for (int i = 0; i < SIGNS; i++) {
            signs.add(ChoiceModule.Option.tipped(K + "operator." + Operator.values()[i].id()));
        }
        return List.of(
                description("threshold_cartridge", 3),
                KIND_SETTING.choice("kind", K + "kind", kinds),
                SIGN_SETTING.choice("operator", K + "operator", signs, stack -> operator(stack).hasValue()),
                VALUE_SETTING.number("value", K + "value", stack -> COLOR, stack -> operator(stack).hasValue()));
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

    /** True if {@code roll} gets over the obstacle set by {@code stack}. */
    public static boolean passes(ItemStack stack, TurnMoves.Roll roll) {
        return operator(stack).passes(roll, value(stack));
    }

    /** What the obstacle shows over its board space. */
    public static Text label(ItemStack stack) {
        return operator(stack).label(value(stack));
    }

    /** A cartridge with this condition (tests, commands). */
    public static ItemStack with(net.minecraft.item.Item item, Operator operator, int value) {
        ItemStack stack = new ItemStack(item);
        putSetting(stack, KIND, operator == Operator.DOUBLE ? 1 : operator == Operator.TRIPLE ? 2 : 0);
        if (operator.hasValue()) putSetting(stack, SIGN, operator.ordinal());
        putSetting(stack, VALUE, value);
        return stack;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.steveparty.threshold_cartridge.condition", label(stack))
                .styled(tint(0xA9C6E3)));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
