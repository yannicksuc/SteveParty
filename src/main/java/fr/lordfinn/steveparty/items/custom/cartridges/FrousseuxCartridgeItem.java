package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.InfoModule;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The Frousseux Cartridge: a token stopping on its tile sends a Frousseux to steal from another player of the party,
 * picked by the token's player (see FrousseuxTileBehavior and FrousseuxThefts). It steals coins ({@link #coins}, the
 * victim may hit it to get some back) or stars ({@link #stars}, no defence). Its tile is night indigo; a dye on it
 * changes that.
 */
public class FrousseuxCartridgeItem extends CartridgeItem {
    /** Its tile's night indigo (a thief in the dark). */
    public static final int COLOR = 0x3B1FB8;
    public static final int MAX_COINS = 99, DEFAULT_COINS = 15;
    public static final int MAX_STARS = 5, DEFAULT_STARS = 1;

    private static final BoolSetting STARS = new BoolSetting(ModComponents.FROUSSEUX_STARS);
    private static final IntSetting COINS = new IntSetting(ModComponents.FROUSSEUX_COINS, 1, MAX_COINS, DEFAULT_COINS);
    private static final IntSetting STAR_COUNT = new IntSetting(ModComponents.FROUSSEUX_STAR_COUNT, 1, MAX_STARS, DEFAULT_STARS);

    private static final String K = MENU_KEY + "frousseux.";
    private static final List<CartridgeModule> MODULES = List.of(
            description("frousseux_cartridge", 3),
            STARS.choice("loot", K + "loot", ChoiceModule.Option.tipped(K + "coins"), ChoiceModule.Option.tipped(K + "stars")),
            COINS.module("coins", K + "coin_count", stack -> COLOR, stack -> !stealsStars(stack)),
            STAR_COUNT.module("stars", K + "star_count", stack -> COLOR, FrousseuxCartridgeItem::stealsStars),
            InfoModule.hint("hint", K + "hint", 2));

    public FrousseuxCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_FROUSSEUX;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int tileColor() {
        return COLOR;
    }

    /** It steals stars rather than coins. */
    public static boolean stealsStars(ItemStack stack) {
        return STARS.get(stack);
    }

    /** Coins it steals: 1 to {@link #MAX_COINS}. */
    public static int coins(ItemStack stack) {
        return COINS.get(stack);
    }

    /** Stars it steals: 1 to {@link #MAX_STARS}. */
    public static int stars(ItemStack stack) {
        return STAR_COUNT.get(stack);
    }

    /** What it steals, as set: the number of coins or of stars. */
    public static int amount(ItemStack stack) {
        return stealsStars(stack) ? stars(stack) : coins(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        boolean stars = stealsStars(stack);
        tooltip.add(Text.translatable("tooltip.steveparty.frousseux_cartridge." + (stars ? "stars" : "coins"), amount(stack))
                .styled(style -> style.withColor(TextColor.fromRgb(0xFFD27A)).withBold(true)));
        tooltip.add(Text.translatable("tooltip.steveparty.frousseux_cartridge." + (stars ? "no_defence" : "defence"))
                .formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
