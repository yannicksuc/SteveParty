package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TrapSetComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.NumberModule;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The Trap Cartridge: a space where traps are set. A token of a party stopping on it may set a Trap there (a Trap item
 * of its player, used up); the next token of another player stopping there springs it, for the one who set it: what
 * it does is the cartridge's (see BoardTraps): steal coins, send back some spaces, lose the next turn, steal an item.
 * One trap per space: setting one on a trapped space is refused, unless the cartridge lets it replace the old one.
 * Its tile is moss green; the trap shows on it as a plate in its setter's colour.
 * <p>
 * Settings ({@link ModComponents#BOARD_CARTRIDGE_SETTINGS}): {@link #EFFECT} (an {@link Effect}, coins by default),
 * {@link #AMOUNT} (coins stolen or spaces back: 5 coins, 3 spaces by default), {@link #REPLACE}. The trap set:
 * {@link ModComponents#TRAP_SET}.
 */
public class TrapCartridgeItem extends BoardRuleCartridgeItem {
    /** Its tile's moss green. */
    public static final int COLOR = 0x407010;
    public static final String EFFECT = "effect", AMOUNT = "amount", REPLACE = "replace";
    public static final int MAX_AMOUNT = 20;

    /** What a sprung trap does to the token's player. */
    public enum Effect {
        COINS(5), BACK(3), SKIP_TURN(0), STEAL_ITEM(0);

        public final int defaultAmount;

        Effect(int defaultAmount) {
            this.defaultAmount = defaultAmount;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public boolean hasAmount() {
            return defaultAmount > 0;
        }
    }

    private static final String K = MENU_KEY + "trap.";
    private static final List<CartridgeModule> MODULES = List.of(
            description("trap_cartridge", 2),
            new ChoiceModule("effect", K + "effect", java.util.Arrays.stream(Effect.values())
                    .map(effect -> new ChoiceModule.Option(K + "effect." + effect.id(), -1, K + "effect." + effect.id() + ".tooltip")).toList(),
                    stack -> effect(stack).ordinal(), (edit, value) -> putSetting(edit.stack(), EFFECT, value)),
            new NumberModule("amount", K + "amount", 1, MAX_AMOUNT, TrapCartridgeItem::amount,
                    (edit, value) -> putSetting(edit.stack(), AMOUNT + "_" + effect(edit.stack()).id(), value), stack -> COLOR,
                    stack -> effect(stack).hasAmount()),
            yesNo("replace", K + "replace", REPLACE, false));

    public TrapCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_TRAP;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int menuColor(ItemStack stack) {
        return stack.getOrDefault(ModComponents.COLOR, COLOR) & 0xFFFFFF;
    }

    public static Effect effect(ItemStack stack) {
        return Effect.values()[setting(stack, EFFECT, 0, 0, Effect.values().length - 1)];
    }

    /** Coins stolen or spaces back, for the current effect (1 for the others). */
    public static int amount(ItemStack stack) {
        Effect effect = effect(stack);
        if (!effect.hasAmount()) return 1;
        return setting(stack, AMOUNT + "_" + effect.id(), effect.defaultAmount, 1, MAX_AMOUNT);
    }

    public static boolean replaces(ItemStack stack) {
        return setting(stack, REPLACE, 0, 0, 1) == 1;
    }

    /** A cartridge with this effect (tests, commands). */
    public static ItemStack with(net.minecraft.item.Item item, Effect effect, int amount) {
        ItemStack stack = new ItemStack(item);
        putSetting(stack, EFFECT, effect.ordinal());
        if (effect.hasAmount()) putSetting(stack, AMOUNT + "_" + effect.id(), amount);
        return stack;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.steveparty.trap_cartridge.effect." + effect(stack).id(), amount(stack))
                .styled(style -> style.withColor(TextColor.fromRgb(0xC9E27A))));
        if (stack.get(ModComponents.TRAP_SET) instanceof TrapSetComponent) {
            tooltip.add(Text.translatable("tooltip.steveparty.trap_cartridge.set").formatted(Formatting.RED));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
