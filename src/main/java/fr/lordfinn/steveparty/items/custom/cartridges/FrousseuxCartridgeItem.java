package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.InfoModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.NumberModule;
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

    private static final String K = MENU_KEY + "frousseux.";
    private static final List<CartridgeModule> MODULES = List.of(
            description("frousseux_cartridge", 3),
            new ChoiceModule("loot", K + "loot",
                    List.of(new ChoiceModule.Option(K + "coins", -1, K + "coins.tooltip"),
                            new ChoiceModule.Option(K + "stars", -1, K + "stars.tooltip")),
                    stack -> stealsStars(stack) ? 1 : 0,
                    (edit, value) -> edit.stack().set(ModComponents.FROUSSEUX_STARS, value == 1)),
            new NumberModule("coins", K + "coin_count", 1, MAX_COINS, FrousseuxCartridgeItem::coins,
                    (edit, value) -> edit.stack().set(ModComponents.FROUSSEUX_COINS, value), stack -> COLOR,
                    stack -> !stealsStars(stack)),
            new NumberModule("stars", K + "star_count", 1, MAX_STARS, FrousseuxCartridgeItem::stars,
                    (edit, value) -> edit.stack().set(ModComponents.FROUSSEUX_STAR_COUNT, value), stack -> COLOR,
                    FrousseuxCartridgeItem::stealsStars),
            new InfoModule("hint", null, 2, context -> List.of(
                    new InfoModule.Line(Text.translatable(K + "hint"), InfoModule.Tone.SOFT))));

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
        return stack != null && stack.getOrDefault(ModComponents.FROUSSEUX_STARS, false);
    }

    /** Coins it steals: 1 to {@link #MAX_COINS}. */
    public static int coins(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return DEFAULT_COINS;
        return Math.max(1, Math.min(MAX_COINS, stack.getOrDefault(ModComponents.FROUSSEUX_COINS, DEFAULT_COINS)));
    }

    /** Stars it steals: 1 to {@link #MAX_STARS}. */
    public static int stars(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return DEFAULT_STARS;
        return Math.max(1, Math.min(MAX_STARS, stack.getOrDefault(ModComponents.FROUSSEUX_STAR_COUNT, DEFAULT_STARS)));
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
