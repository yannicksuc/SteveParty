package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.board.BoardText;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.SneakScrollItem;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ContainersModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.GhostSlotsModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.NumberModule;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_COMPONENT;
import static fr.lordfinn.steveparty.components.ModComponents.IS_NEGATIVE;

/**
 * The Shop Cartridge (« Cartouche Boutique », lime green: yellow is the Star Cartridge's): a shop stop, as in party board games (see
 * {@link fr.lordfinn.steveparty.service.ShopStops}).
 * <ul>
 *     <li>in a check point: a token passing through pauses there while its owner shops;</li>
 *     <li>in a tile: the shop opens when a token ends its move there (passing tokens are not stopped).</li>
 * </ul>
 * The space summons its own merchant, a Boxed Trader hologram, on its Spawn Marker ({@link MobSpawnCartridge}). What he
 * sells is set in the cartridge's menu: up to {@link #OFFERS} offers, one per column of its slots like a trading stall's
 * (the item sold, its price, a second price), quantities with the mouse wheel. Each sale is taken from its linked
 * containers ({@link ContainerCartridge}), or without any from the bank of the party running on its board; the payment
 * goes to that bank (or to its containers outside a party). The cartridge also sets how many items may be bought per
 * stop ({@link #purchases}): its menu, or sneak + mouse wheel with it in the main hand.
 */
public class ShopCartridgeItem extends CartridgeItem implements SneakScrollItem, ContainerCartridge, MobSpawnCartridge {
    public static final int DEFAULT_PURCHASES = 1;
    public static final int MAX_PURCHASES = 9;
    /** The shop cartridge's colour: the tile, the check point and the landing burst. */
    public static final int COLOR = 0xA6E22E;
    /** The most offers (the columns of its menu's slots). */
    public static final int OFFERS = GhostSlotsModule.COLUMNS;
    /** The rows of its menu's slots: the item sold, its price, a second price (optional). */
    public static final int ROW_SOLD = 0, ROW_PRICE = 1, ROW_SECOND_PRICE = 2;

    private static final String K = MENU_KEY + "shop.";
    private static final List<CartridgeModule> MODULES = List.of(
            new GhostSlotsModule("offers", K + "offers", GhostSlotsModule.COUNT, K + "offers.help"),
            new NumberModule("purchases", K + "purchases", 1, MAX_PURCHASES, ShopCartridgeItem::purchases,
                    (edit, value) -> setPurchases(edit.stack(), value), stack -> COLOR),
            // No title: its hint row says what it is
            new ContainersModule("chests", null));

    public ShopCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public net.minecraft.entity.EntityType<?> spawnedMob() {
        return fr.lordfinn.steveparty.entities.ModEntities.BOXED_TRADER_ENTITY;
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.BOARD_SPACE_SHOP;
    }

    /** How many items a player may buy during one stop. */
    public static int purchases(ItemStack stack) {
        return stack.getOrDefault(ModComponents.SHOP_PURCHASES, DEFAULT_PURCHASES);
    }

    @Override
    public void onSneakScroll(ServerPlayerEntity player, ItemStack stack, int direction) {
        scroll(player, stack, direction);
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

    /** The slot of its menu holding {@code row} ({@link #ROW_SOLD}, {@link #ROW_PRICE}...) of offer {@code column}. */
    public static int slot(int column, int row) {
        return row * GhostSlotsModule.COLUMNS + column;
    }

    /**
     * Its offers, in their columns' order, as a trading stall makes them ({@link TradingStallBlockEntity#offerOf}): a
     * column without an item sold or without a price is no offer. New offers each time (their uses are their own).
     */
    public static List<TradingStallBlockEntity.ExactTradeOffer> offers(ItemStack stack) {
        List<TradingStallBlockEntity.ExactTradeOffer> offers = new ArrayList<>();
        InventoryComponent slots = stack == null ? null : stack.get(INVENTORY_COMPONENT);
        if (slots == null) return offers;
        for (int column = 0; column < OFFERS; column++) {
            TradingStallBlockEntity.ExactTradeOffer offer = TradingStallBlockEntity.offerOf(ghost(slots, slot(column, ROW_PRICE)),
                    ghost(slots, slot(column, ROW_SECOND_PRICE)), ghost(slots, slot(column, ROW_SOLD)));
            if (offer != null) offers.add(offer);
        }
        return offers;
    }

    private static ItemStack ghost(InventoryComponent slots, int slot) {
        if (slot >= slots.size()) return ItemStack.EMPTY;
        ItemStack stack = slots.getStack(slot).copy();
        stack.remove(IS_NEGATIVE);
        return stack;
    }

    /** Whether it has at least one offer set. */
    public static boolean hasOffers(ItemStack stack) {
        return !offers(stack).isEmpty();
    }

    /**
     * Sets its offers (for the GameTests and the showcases): each a list of the item sold, its price and optionally
     * its second price; at most {@link #OFFERS}, none: no offer.
     */
    public static void setOffers(ItemStack stack, List<List<ItemStack>> offers) {
        SimpleInventory slots = new SimpleInventory(GhostSlotsModule.COUNT);
        for (int column = 0; column < Math.min(OFFERS, offers.size()); column++) {
            List<ItemStack> offer = offers.get(column);
            for (int row = 0; row < Math.min(3, offer.size()); row++) slots.setStack(slot(column, row), offer.get(row).copy());
        }
        if (slots.isEmpty()) stack.remove(INVENTORY_COMPONENT);
        else InventoryComponent.writeToStack(stack, slots);
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
    protected void appendState(ItemStack stack, Tooltips tips) {
        int offers = offers(stack).size();
        if (offers == 0) tips.warn(Text.translatable("tooltip.steveparty.shop_cartridge.no_offer"));
        else tips.state("tooltip.steveparty.shop_cartridge.offers", Tooltips.value(offers));
        tips.state("tooltip.steveparty.shop_cartridge.purchases", Tooltips.value(purchases(stack)));
    }

    @Override
    protected void appendMore(ItemStack stack, Tooltips.More more) {
        Entity viewer = stack.getHolder();
        List<GlobalPos> containers = CartridgeContainers.of(stack, viewer == null ? World.OVERWORLD : viewer.getWorld().getRegistryKey());
        for (int i = 0; i < containers.size(); i++) {
            var pos = containers.get(i).pos();
            more.detail(Text.translatable("tooltip.steveparty.container_entry_indexed", i + 1, pos.getX(), pos.getY(), pos.getZ())
                    .formatted(Tooltips.DIM));
        }
        more.use(Tooltips.Keys.sneakScroll(), "tooltip.steveparty.shop_cartridge.scroll");
        more.use(Tooltips.Keys.use(), "tooltip.steveparty.controls.container_click");
        more.note("tooltip.steveparty.shop_cartridge.rules");
    }

    /** Its name says what it does. */
    @Override
    protected boolean hasSummary() {
        return false;
    }
}
