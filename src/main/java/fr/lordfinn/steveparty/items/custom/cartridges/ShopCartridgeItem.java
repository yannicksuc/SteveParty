package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.board.BoardText;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.ShopLinkComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The Shop Cartridge (« Cartouche Boutique », yellow): a shop stop, Mario Party style (see
 * {@link fr.lordfinn.steveparty.service.ShopStops}).
 * <ul>
 *     <li>in a check point: a token passing through pauses there while its owner shops;</li>
 *     <li>in a tile: the shop opens when a token ends its move there (passing tokens are not stopped).</li>
 * </ul>
 * The shop is the nearest merchant (a Hiding Trader, or the trader of the nearest trading stall), or the one chosen
 * with the Wrench. The cartridge sets how many items may be bought per stop ({@link #purchases}): sneak + mouse wheel
 * with it in the main hand.
 */
public class ShopCartridgeItem extends CartridgeItem {
    public static final int DEFAULT_PURCHASES = 1;
    public static final int MAX_PURCHASES = 9;
    /** The shop cartridge's colour: the tile, the check point and the landing burst. */
    public static final int COLOR = 0xFFD83D;

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
        if (purchases == DEFAULT_PURCHASES) stack.remove(ModComponents.SHOP_PURCHASES);
        else stack.set(ModComponents.SHOP_PURCHASES, purchases);
        player.sendMessage(Text.translatable("message.steveparty.shop_cartridge.purchases", BoardText.num(purchases)), true);
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS,
                0.4F, 1.0F + purchases * 0.08F);
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
