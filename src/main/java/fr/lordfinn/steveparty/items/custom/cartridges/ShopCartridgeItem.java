package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.board.BoardText;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.ShopLinkComponent;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.InfoModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.NumberModule;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The Shop Cartridge (« Cartouche Boutique », lime green: yellow is the Star Cartridge's): a shop stop, as in party board games (see
 * {@link fr.lordfinn.steveparty.service.ShopStops}).
 * <ul>
 *     <li>in a check point: a token passing through pauses there while its owner shops;</li>
 *     <li>in a tile: the shop opens when a token ends its move there (passing tokens are not stopped).</li>
 * </ul>
 * The shop is the nearest merchant (a Boxed Trader, or the trader of the nearest trading stall), or the one chosen
 * with the Tile Linker Brush. The cartridge sets how many items may be bought per stop ({@link #purchases}): its menu, or sneak +
 * mouse wheel with it in the main hand.
 */
public class ShopCartridgeItem extends CartridgeItem {
    public static final int DEFAULT_PURCHASES = 1;
    public static final int MAX_PURCHASES = 9;
    /** The shop cartridge's colour: the tile, the check point and the landing burst. */
    public static final int COLOR = 0xA6E22E;

    private static final String K = MENU_KEY + "shop.";
    private static final List<CartridgeModule> MODULES = List.of(
            new NumberModule("purchases", K + "purchases", 1, MAX_PURCHASES, ShopCartridgeItem::purchases,
                    (edit, value) -> setPurchases(edit.stack(), value), stack -> COLOR),
            new InfoModule("merchant", K + "merchant", 2, ShopCartridgeItem::merchant,
                    stack -> new ItemStack(ModItems.SHOPKEEPER_KEY)));

    public ShopCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.BOARD_SPACE_SHOP;
    }

    /** How many items a player may buy during one stop. */
    public static int purchases(ItemStack stack) {
        return stack.getOrDefault(ModComponents.SHOP_PURCHASES, DEFAULT_PURCHASES);
    }

    /** Sneak + mouse wheel (server side): one more / one less purchase allowed per stop, from 1 to {@value #MAX_PURCHASES}. */
    public static void scroll(PlayerEntity player, ItemStack stack, int direction) {
        if (!(stack.getItem() instanceof ShopCartridgeItem) || direction == 0) return;
        int purchases = Math.clamp(purchases(stack) + Integer.signum(direction), 1, MAX_PURCHASES);
        setPurchases(stack, purchases);
        player.sendMessage(Text.translatable("message.steveparty.shop_cartridge.purchases", BoardText.num(purchases)), true);
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS,
                0.4F, 1.0F + purchases * 0.08F);
    }

    /** How many items a stop sells, from 1 to {@value #MAX_PURCHASES} (the default one is not stored). */
    public static void setPurchases(ItemStack stack, int purchases) {
        purchases = Math.clamp(purchases, 1, MAX_PURCHASES);
        if (purchases == DEFAULT_PURCHASES) stack.remove(ModComponents.SHOP_PURCHASES);
        else stack.set(ModComponents.SHOP_PURCHASES, purchases);
    }

    /** The shop: the nearest merchant, or the one chosen with the Tile Linker Brush; how to choose it. */
    private static List<InfoModule.Line> merchant(InfoModule.Context context) {
        ShopLinkComponent link = context.stack().get(ModComponents.SHOP_LINK);
        return List.of(InfoModule.Line.of(link != null
                        ? Text.translatable(K + "linked", BoardText.pos(link.anchor()))
                        : Text.translatable(K + "nearest")),
                new InfoModule.Line(Text.translatable(K + "hint"), InfoModule.Tone.SOFT));
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int menuColor(ItemStack stack) {
        return COLOR;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatable("tooltip.steveparty.shop_cartridge.purchases", purchases(stack)).formatted(Formatting.GOLD));
        ShopLinkComponent link = stack.get(ModComponents.SHOP_LINK);
        tooltip.add((link != null
                ? Text.translatable("tooltip.steveparty.shop_cartridge.shop", BoardText.pos(link.anchor()))
                : Text.translatable("tooltip.steveparty.shop_cartridge.nearest")).formatted(Formatting.GRAY));
        addWrapped(tooltip, Text.translatable("tooltip.steveparty.shop_cartridge.scroll"), Formatting.DARK_GRAY);
    }
}
