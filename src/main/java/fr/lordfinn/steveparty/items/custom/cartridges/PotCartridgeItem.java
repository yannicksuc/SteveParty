package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.NumberModule;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * The Common pot Cartridge: every token passing over its space puts a stake of coins into the pot, the token stopping
 * exactly on it wins the whole pot (see CommonPots). The pot is kept in the cartridge, so in the tile holding it (saved
 * with it), with the items the Pie stole. A nest set near the space shows it and the Pie fetches the stakes.
 * <p>
 * Settings ({@link ModComponents#BOARD_CARTRIDGE_SETTINGS}): {@link #STAKE} (coins put in by a passing token, 1 by
 * default, 0 to 100), {@link #LANDER_PAYS} (the winner pays a stake too before taking the pot), {@link #START} (the pot after a
 * win, 0 by default), {@link #CAP} (the most it holds, 0 for no cap), {@link #THIEF} (the thieving Pie: the chance, in
 * percent, that it also steals an item from a passing token's player, 0: never). Remembered
 * ({@link ModComponents#BOARD_CARTRIDGE_STATE}): {@link #COINS}, the coins in the pot; the stolen items in
 * {@link ModComponents#POT_ITEMS}.
 */
public class PotCartridgeItem extends BoardRuleCartridgeItem {
    /** Its tile's straw (the nest's). */
    public static final int COLOR = 0xC0A060;
    public static final String STAKE = "stake", LANDER_PAYS = "lander_pays", START = "start", CAP = "cap", THIEF = "thief",
            COINS = "coins";
    /** Every number of the menu goes from 0 to 100 (a stake of 0: the pot only grows by what the Pie steals). */
    public static final int MAX = 100;

    private static final String K = MENU_KEY + "pot.";
    /** The numbers of the menu, edited one at a time (picked in a row of buttons): stake, start, cap, thief. */
    private static final String[] NUMBERS = {STAKE, START, CAP, THIEF};
        /** The settings key of the number being edited in the menu. */
    private static final String EDITED = "edited";
    private static final List<CartridgeModule> MODULES = List.of(
            description("pot_cartridge", 2),
            new ChoiceModule("number", K + "number", List.of(new ChoiceModule.Option(K + "stake", -1, K + "stake.tooltip"),
                    new ChoiceModule.Option(K + "start", -1, K + "start.tooltip"), new ChoiceModule.Option(K + "cap", -1, K + "cap.tooltip"),
                    new ChoiceModule.Option(K + "thief", -1, K + "thief.tooltip")),
                    PotCartridgeItem::edited, (edit, value) -> putSetting(edit.stack(), EDITED, value)),
            new NumberModule("amount", K + "amount", 0, MAX, stack -> number(stack, edited(stack)),
                    (edit, value) -> {
                        int i = edited(edit.stack());
                        putSetting(edit.stack(), NUMBERS[i], Math.clamp(value, 0, MAX));
                    }, stack -> COLOR),
            yesNo("lander_pays", K + "lander_pays", LANDER_PAYS, false));

    private static int edited(ItemStack stack) {
        return setting(stack, EDITED, 0, 0, NUMBERS.length - 1);
    }

    private static int number(ItemStack stack, int i) {
        return switch (i) {
            case 0 -> stake(stack);
            case 1 -> start(stack);
            case 2 -> cap(stack);
            default -> thief(stack);
        };
    }

    public PotCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_POT;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int tileColor() {
        return COLOR;
    }

    public static int stake(ItemStack stack) {
        return setting(stack, STAKE, 1, 0, MAX);
    }

    public static boolean landerPays(ItemStack stack) {
        return setting(stack, LANDER_PAYS, 0, 0, 1) == 1;
    }

    public static int start(ItemStack stack) {
        return setting(stack, START, 0, 0, MAX);
    }

    /** The most coins the pot holds, 0: no cap. */
    public static int cap(ItemStack stack) {
        return setting(stack, CAP, 0, 0, MAX);
    }

    /** The thieving Pie's chance to steal an item from a passing token's player, in percent (0: never). */
    public static int thief(ItemStack stack) {
        return setting(stack, THIEF, 0, 0, MAX);
    }

    /** The coins in the pot now (its start until a token passed). */
    public static int coins(ItemStack stack) {
        return Math.max(0, state(stack, COINS, start(stack)));
    }

    public static void setCoins(ItemStack stack, int coins) {
        putState(stack, COINS, Math.max(0, coins));
    }

    /** The items the Pie stole for the pot (copies). */
    public static List<ItemStack> items(ItemStack stack) {
        List<ItemStack> items = new ArrayList<>();
        List<ItemStack> stored = stack.getOrDefault(ModComponents.POT_ITEMS, List.<ItemStack>of());
        for (ItemStack item : stored) items.add(item.copy());
        return items;
    }

    public static void setItems(ItemStack stack, List<ItemStack> items) {
        List<ItemStack> kept = items.stream().filter(item -> !item.isEmpty()).map(ItemStack::copy).toList();
        if (kept.isEmpty()) stack.remove(ModComponents.POT_ITEMS);
        else stack.set(ModComponents.POT_ITEMS, kept);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.steveparty.pot_cartridge.pot", coins(stack), stake(stack))
                .styled(style -> style.withColor(TextColor.fromRgb(0xF2C230))));
        int stolen = items(stack).stream().mapToInt(ItemStack::getCount).sum();
        if (stolen > 0) tooltip.add(Text.translatable("tooltip.steveparty.pot_cartridge.items", stolen).formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
