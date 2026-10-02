package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.screen_handlers.custom.TradingStallScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.AirBlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.predicate.ComponentPredicate;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TradingStallBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, ImplementedInventory {
    /** 9 columns of 3: two price rows and the sold item (an old stall's 28th slot is ignored when it loads). */
    public static final int SIZE = 27;
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);

    public TradingStallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRADING_STALL, pos, state);
    }

    @Override
    public DefaultedList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        Inventories.writeNbt(nbt, items, registryLookup);
        BlockState state = getCachedState();
        nbt.putInt("color1", state.get(TradingStallBlock.COLOR1));
        nbt.putInt("color2", state.get(TradingStallBlock.COLOR2));
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        Inventories.readNbt(nbt, items, registryLookup);
    }



    @Override
    public Text getDisplayName() {
        return Text.translatable("block.steveparty.trading_stall");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new TradingStallScreenHandler(syncId, playerInventory, this);
    }

    public Inventory getInventory() {
        return ImplementedInventory.of(items);
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        this.writeNbt(nbt, registries);
        return nbt;
    }

    public List<TradeOffer> getTradeOffers() {
        List<TradeOffer> offers = new ArrayList<>();

        for (int column = 0; column < 9; column++) {
            ItemStack firstBuyItem = getStack(column);
            ItemStack secondBuyItem = getStack(column + 9);
            ItemStack sellItem = getStack(column + 18);

            if (firstBuyItem.isEmpty() && !secondBuyItem.isEmpty()) {
                firstBuyItem = secondBuyItem;
                secondBuyItem = ItemStack.EMPTY;
            }

            if (!firstBuyItem.isEmpty() && !sellItem.isEmpty() &&
                    !(firstBuyItem.getItem() instanceof AirBlockItem) &&
                    !(sellItem.getItem() instanceof AirBlockItem)) {
                offers.add(new ExactTradeOffer(firstBuyItem, secondBuyItem, sellItem.copy()));
            }
        }
        return offers;
    }

    /**
     * Builds a {@link TradedItem} requiring the price's exact components (enchantments, custom name, damage...):
     * the merchant screen then displays the real price, and vanilla matching already rejects a stack missing them.
     */
    public static TradedItem createExactTradedItem(ItemStack price) {
        ComponentPredicate components = ComponentPredicate.of(price.getComponentChanges().toAddedRemovedPair().added());
        return new TradedItem(price.getRegistryEntry(), price.getCount(), components);
    }

    /** True if the stack is the exact price item: same item and exactly the same components (count excluded). */
    public static boolean isExactPayment(ItemStack stack, ItemStack price) {
        return ItemStack.areItemsAndComponentsEqual(stack, price);
    }

    /**
     * Trade offer of a trading stall: the payment must match the configured price stacks EXACTLY, components
     * included (an enchanted sword is not a plain sword, a named item is not an unnamed one). Vanilla
     * {@link TradedItem} matching only checks the components listed in its predicate and accepts extra ones.
     * <p>
     * The stacks actually taken from the player by the last trade are kept, so that the cash registers receive
     * the real payment.
     * <p>
     * Uses are unlimited: the offer is only disabled by the trader when the stock is missing (how many items a
     * player may buy is up to the shop stops, see {@link fr.lordfinn.steveparty.service.ShopStops}).
     */
    public static class ExactTradeOffer extends TradeOffer {
        private final ItemStack firstPrice;
        private final ItemStack secondPrice;
        private final List<ItemStack> lastPayment = new ArrayList<>();

        public ExactTradeOffer(ItemStack firstPrice, ItemStack secondPrice, ItemStack sellItem) {
            super(createExactTradedItem(firstPrice),
                    secondPrice.isEmpty() ? Optional.empty() : Optional.of(createExactTradedItem(secondPrice)),
                    sellItem, Integer.MAX_VALUE, 0, 0);
            this.firstPrice = firstPrice.copy();
            this.secondPrice = secondPrice.copy();
        }

        public ItemStack getFirstPrice() {
            return firstPrice;
        }

        public ItemStack getSecondPrice() {
            return secondPrice;
        }

        @Override
        public boolean matchesBuyItems(ItemStack first, ItemStack second) {
            if (!super.matchesBuyItems(first, second)) return false;
            if (!isExactPayment(first, firstPrice)) return false;
            return secondPrice.isEmpty() || isExactPayment(second, secondPrice);
        }

        @Override
        public boolean depleteBuyItems(ItemStack first, ItemStack second) {
            if (!matchesBuyItems(first, second)) return false;
            lastPayment.clear();
            lastPayment.add(first.copyWithCount(getDisplayedFirstBuyItem().getCount()));
            if (!getDisplayedSecondBuyItem().isEmpty()) {
                lastPayment.add(second.copyWithCount(getDisplayedSecondBuyItem().getCount()));
            }
            return super.depleteBuyItems(first, second);
        }

        /** The stacks paid by the player during the last trade (then forgotten), empty if unknown. */
        public List<ItemStack> takeLastPayment() {
            List<ItemStack> payment = new ArrayList<>(lastPayment);
            lastPayment.clear();
            return payment;
        }
    }
}

