package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import fr.lordfinn.steveparty.service.MarkerResidents;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.board.BoardText;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.SneakScrollItem;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.BlockSlotModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeEdit;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ContainersModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.StallOffersModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.NumberModule;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

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
 * sells is set on a Trading Stall of its own: its menu shows the stall in small ({@link StallOffersModule}), a click opens
 * the stall's screen, up to {@link #OFFERS} offers, one per column (the item sold over the awning, its price and a second
 * price under it), ghost copies with their quantities set with the mouse wheel. Each sale is taken from its linked
 * containers ({@link ContainerCartridge}), or without any from the bank of the party running on its board; the payment
 * goes to that bank (or to its containers outside a party). The cartridge also sets how many items may be bought per
 * stop ({@link #purchases}): its menu, or sneak + mouse wheel with it in the main hand. Its menu also sets the merchant's
 * disguise ({@link #disguise}), in a slot beside its small stall: the block his box looks like, a beehive by default.
 */
public class ShopCartridgeItem extends CartridgeItem implements SneakScrollItem, ContainerCartridge, MobSpawnCartridge {
    public static final int DEFAULT_PURCHASES = 1;
    public static final int MAX_PURCHASES = 9;
    /** The shop cartridge's colour: the tile, the check point and the landing burst. */
    public static final int COLOR = 0x287844;
    /** The most offers: the columns of a Trading Stall. */
    public static final int OFFERS = 9;
    /** The rows of an offer: the item sold, its price, a second price (optional). */
    public static final int ROW_SOLD = 0, ROW_PRICE = 1, ROW_SECOND_PRICE = 2;
    /** Its slots, in a Trading Stall's order ({@link TradingStallBlockEntity#SIZE}): the prices, the second prices, the items sold. */
    public static final int SLOTS = TradingStallBlockEntity.SIZE;
    /** The merchant's box when no disguise is set: a beehive. */
    public static final BlockState DEFAULT_DISGUISE = Blocks.BEEHIVE.getDefaultState();
    /** The slots of a cartridge made before it had a stall's 9 offers: 3 columns of 3 (row by row: sold, price, 2nd price). */
    private static final int OLD_SLOTS = 9, OLD_COLUMNS = 3;

    private static final String K = MENU_KEY + "shop.";
    private static final List<CartridgeModule> MODULES = List.of(
            new StallOffersModule("offers", K + "offers"),
            // Beside the small stall: the block the merchant hides in (the menu fits beside a tile without one more row)
            new BlockSlotModule("disguise", K + "disguise", ShopCartridgeItem::storedDisguise, ShopCartridgeItem::setDisguise,
                    BoxedTraderEntity::isValidBoxBlock, DEFAULT_DISGUISE, K + "disguise.hint", true),
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

    /** The block set as the merchant's disguise, null if none (or one that can't be a box). */
    public static @Nullable BlockState storedDisguise(ItemStack stack) {
        BlockState state = stack.get(ModComponents.BOX_BLOCK);
        return BoxedTraderEntity.isValidBoxBlock(state) ? state : null;
    }

    /** The block the merchant's box looks like: the one set, else {@link #DEFAULT_DISGUISE}. */
    public static BlockState disguise(ItemStack stack) {
        BlockState state = storedDisguise(stack);
        return state == null ? DEFAULT_DISGUISE : state;
    }

    /**
     * Sets the merchant's disguise: {@code block}'s default state, or none (null: the default box). A block that can't
     * be a box ({@link BoxedTraderEntity#isValidBoxBlock}) is refused.
     *
     * @return true if written
     */
    public static boolean setDisguise(ItemStack stack, @Nullable Block block) {
        if (block == null) {
            stack.remove(ModComponents.BOX_BLOCK);
            return true;
        }
        BlockState state = block.getDefaultState();
        if (!BoxedTraderEntity.isValidBoxBlock(state)) return false;
        stack.set(ModComponents.BOX_BLOCK, state);
        return true;
    }

    /**
     * From its menu: the disguise written, and the merchant living on its Spawn Bubble (« always visible ») dressed at
     * once if the cartridge is the active one of its space.
     */
    private static void setDisguise(CartridgeEdit edit, @Nullable BlockState state) {
        setDisguise(edit.stack(), state == null ? null : state.getBlock());
        if (edit.isActiveOnBoardSpace() && edit.holder().getWorld() instanceof ServerWorld world) {
            BlockPos marker = CartridgeSpawnMarker.linked(edit.stack(), world);
            if (marker != null) MarkerResidents.check(world, marker);
        }
    }

    /** Its merchant wears its disguise (his box looks like {@link #disguise}). */
    @Override
    public void dress(MobEntity mob, ItemStack cartridge) {
        if (!(mob instanceof BoxedTraderEntity trader)) return;
        BlockState state = disguise(cartridge);
        if (trader.getBlockState() != state) trader.setBlockState(state);
    }

    /**
     * The slot holding {@code row} ({@link #ROW_SOLD}, {@link #ROW_PRICE}...) of offer {@code column}: a Trading Stall's
     * (0-8 the prices, 9-17 the second prices, 18-26 the items sold), so that its editor is the stall's screen.
     */
    public static int slot(int column, int row) {
        return switch (row) {
            case ROW_PRICE -> column;
            case ROW_SECOND_PRICE -> OFFERS + column;
            default -> 2 * OFFERS + column;
        };
    }

    /**
     * Its {@value #SLOTS} slots in a Trading Stall's order (copies, never taken items). A cartridge made when it had 3
     * offers (3 columns of 3: sold, price, 2nd price) reads as the stall's first 3 columns: its offers are kept, and
     * written in the new order at its next change.
     */
    public static List<ItemStack> stallSlots(ItemStack stack) {
        List<ItemStack> slots = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) slots.add(ItemStack.EMPTY);
        InventoryComponent stored = stack == null ? null : stack.get(INVENTORY_COMPONENT);
        if (stored == null) return slots;
        if (isOldLayout(stored)) {
            for (int i = 0; i < Math.min(OLD_SLOTS, stored.size()); i++) {
                slots.set(slot(i % OLD_COLUMNS, i / OLD_COLUMNS), ghost(stored, i));
            }
        } else {
            for (int i = 0; i < Math.min(SLOTS, stored.size()); i++) slots.set(i, ghost(stored, i));
        }
        return slots;
    }

    /** Whether its slots were written before the stall's 9 offers (9 slots or fewer: 3 columns of 3). */
    public static boolean isOldLayout(InventoryComponent stored) {
        return stored.size() <= OLD_SLOTS;
    }

    /** Writes its {@value #SLOTS} slots (a Trading Stall's order); all empty: none stored. */
    public static void setStallSlots(ItemStack stack, List<ItemStack> stall) {
        SimpleInventory slots = new SimpleInventory(SLOTS);
        for (int i = 0; i < Math.min(SLOTS, stall.size()); i++) {
            ItemStack item = stall.get(i).copy();
            item.remove(IS_NEGATIVE);
            slots.setStack(i, item);
        }
        if (slots.isEmpty()) stack.remove(INVENTORY_COMPONENT);
        else InventoryComponent.writeToStack(stack, slots);
    }

    /** Offer {@code column}, as a Trading Stall makes it ({@link TradingStallBlockEntity#offerOf}), or null (none there). */
    public static TradingStallBlockEntity.@Nullable ExactTradeOffer offer(List<ItemStack> slots, int column) {
        return TradingStallBlockEntity.offerOf(slots.get(slot(column, ROW_PRICE)), slots.get(slot(column, ROW_SECOND_PRICE)),
                slots.get(slot(column, ROW_SOLD)));
    }

    /**
     * Its offers, in their columns' order, as a trading stall makes them ({@link TradingStallBlockEntity#offerOf}): a
     * column without an item sold or without a price is no offer. New offers each time (their uses are their own).
     */
    public static List<TradingStallBlockEntity.ExactTradeOffer> offers(ItemStack stack) {
        List<TradingStallBlockEntity.ExactTradeOffer> offers = new ArrayList<>();
        if (stack == null || !stack.contains(INVENTORY_COMPONENT)) return offers;
        List<ItemStack> slots = stallSlots(stack);
        for (int column = 0; column < OFFERS; column++) {
            TradingStallBlockEntity.ExactTradeOffer offer = offer(slots, column);
            if (offer != null) offers.add(offer);
        }
        return offers;
    }

    private static ItemStack ghost(InventoryComponent slots, int slot) {
        ItemStack stack = slots.getStack(slot);
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
        List<ItemStack> slots = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) slots.add(ItemStack.EMPTY);
        for (int column = 0; column < Math.min(OFFERS, offers.size()); column++) {
            List<ItemStack> offer = offers.get(column);
            for (int row = 0; row < Math.min(3, offer.size()); row++) slots.set(slot(column, row), offer.get(row).copy());
        }
        setStallSlots(stack, slots);
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
        BlockState disguise = storedDisguise(stack);
        if (disguise != null) tips.state("tooltip.steveparty.shop_cartridge.disguise", Tooltips.value(disguise.getBlock().getName()));
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
