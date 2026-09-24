package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.entities.custom.HidingTraderEntity;
import fr.lordfinn.steveparty.persistent_state.TraderStallRegistry;
import fr.lordfinn.steveparty.screen_handlers.custom.TradingStallScreenHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.AirBlockItem;
import net.minecraft.item.BlockItem;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public class TradingStallBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, ImplementedInventory {
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(28, ItemStack.EMPTY);

    /** How the stall's offers can be bought, chosen in the stall GUI. */
    public enum SaleMode {
        /** Unlimited purchases while there is stock. */
        FREE(0, "free"),
        /** Locked by default: each redstone rising edge allows exactly one sale (the credit never exceeds 1). */
        ONE_SALE_PER_SIGNAL(1, "one_sale_per_signal");

        private final int id;
        private final String key;

        SaleMode(int id, String key) {
            this.id = id;
            this.key = key;
        }

        public int getId() {
            return id;
        }

        public String getKey() {
            return key;
        }

        public SaleMode next() {
            return fromId((id + 1) % values().length);
        }

        public static SaleMode fromId(int id) {
            for (SaleMode mode : values()) {
                if (mode.id == id) return mode;
            }
            return FREE;
        }
    }

    public static final int PROPERTY_SALE_MODE = 0;
    public static final int PROPERTY_SALE_CREDIT = 1;
    public static final int PROPERTY_COUNT = 2;

    private SaleMode saleMode = SaleMode.FREE;
    /** ONE_SALE_PER_SIGNAL only: one sale is allowed (max 1 credit, extra pulses are ignored). */
    private boolean saleCredit = false;
    /** Last redstone power seen, to only react to rising edges. */
    private boolean powered = false;

    /** Syncs the sale mode and credit to the stall GUI. */
    private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case PROPERTY_SALE_MODE -> saleMode.getId();
                case PROPERTY_SALE_CREDIT -> saleCredit ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == PROPERTY_SALE_MODE) setSaleMode(SaleMode.fromId(value));
        }

        @Override
        public int size() {
            return PROPERTY_COUNT;
        }
    };

    public TradingStallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRADING_STALL, pos, state);
    }

    public PropertyDelegate getPropertyDelegate() {
        return propertyDelegate;
    }

    public SaleMode getSaleMode() {
        return saleMode;
    }

    public void setSaleMode(SaleMode mode) {
        if (mode == saleMode) return;
        saleMode = mode;
        // A new mode always starts locked (FREE ignores the credit)
        saleCredit = false;
        super.markDirty();
    }

    public boolean hasSaleCredit() {
        return saleCredit;
    }

    /** True if the stall's offers can currently be bought (FREE, or a pending credit in ONE_SALE_PER_SIGNAL). */
    public boolean isSaleAllowed() {
        return saleMode == SaleMode.FREE || saleCredit;
    }

    /** Called after a completed purchase of one of this stall's offers: consumes the credit (locks again). */
    public void onSale() {
        if (saleMode == SaleMode.ONE_SALE_PER_SIGNAL && saleCredit) {
            saleCredit = false;
            super.markDirty();
        }
    }

    /** Redstone input of the stall: a rising edge grants one sale credit in ONE_SALE_PER_SIGNAL mode. */
    public void onRedstonePower(boolean isPowered) {
        boolean risingEdge = isPowered && !powered;
        if (isPowered != powered) {
            powered = isPowered;
            super.markDirty();
        }
        if (risingEdge && saleMode == SaleMode.ONE_SALE_PER_SIGNAL && !saleCredit) {
            saleCredit = true;
            super.markDirty();
        }
    }

    /** Initial redstone state (on placement): never counted as a pulse. */
    public void initRedstonePower(boolean isPowered) {
        powered = isPowered;
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
        nbt.putInt("sale_mode", saleMode.getId());
        nbt.putBoolean("sale_credit", saleCredit);
        nbt.putBoolean("powered", powered);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        Inventories.readNbt(nbt, items, registryLookup);
        saleMode = SaleMode.fromId(nbt.getInt("sale_mode"));
        saleCredit = nbt.getBoolean("sale_credit");
        powered = nbt.getBoolean("powered");
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

    @Override
    public void markDirty() {
        super.markDirty();
        if (world != null && !world.isClient) {
            updateLinkedTraders();
        }
    }

    public void updateLinkedTraders() {
        ItemStack lastSlotItem = this.getItems().get(27); // Assuming the last slot is index 27
        if (!lastSlotItem.isEmpty() && lastSlotItem.getItem() instanceof BlockItem) {
            Block block = ((BlockItem) lastSlotItem.getItem()).getBlock();
            World world = this.getWorld();
            if (world != null) {
                Box searchBox = new Box(this.getPos().add(-5, -5, -5).toCenterPos(), this.getPos().add(5, 5, 5).toCenterPos());
                Predicate<HidingTraderEntity> predicate = trader -> trader.getBlockState().getBlock() == block;

                List<HidingTraderEntity> nearbyTraders = world.getEntitiesByClass(HidingTraderEntity.class, searchBox, predicate);

                for (HidingTraderEntity trader : nearbyTraders) {
                    BlockPos stallPos = this.getPos();
                    if (trader.getPos().distanceTo(new Vec3d(stallPos.getX(), stallPos.getY(), stallPos.getZ())) <= 5) {
                        TraderStallRegistry.linkTraderToStall(trader.getUuid(), stallPos);
                    }
                }
            }
        }
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
                offers.add(new ExactTradeOffer(firstBuyItem, secondBuyItem, sellItem.copy(), this));
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
     * Uses are unlimited: the offer is only disabled by the trader when the stock is missing or the stall is locked.
     */
    public static class ExactTradeOffer extends TradeOffer {
        private final ItemStack firstPrice;
        private final ItemStack secondPrice;
        @Nullable
        private final TradingStallBlockEntity stall;
        private final List<ItemStack> lastPayment = new ArrayList<>();

        public ExactTradeOffer(ItemStack firstPrice, ItemStack secondPrice, ItemStack sellItem) {
            this(firstPrice, secondPrice, sellItem, null);
        }

        public ExactTradeOffer(ItemStack firstPrice, ItemStack secondPrice, ItemStack sellItem, @Nullable TradingStallBlockEntity stall) {
            super(createExactTradedItem(firstPrice),
                    secondPrice.isEmpty() ? Optional.empty() : Optional.of(createExactTradedItem(secondPrice)),
                    sellItem, Integer.MAX_VALUE, 0, 0);
            this.firstPrice = firstPrice.copy();
            this.secondPrice = secondPrice.copy();
            this.stall = stall;
        }

        /** The stall this offer comes from (null for an offer built outside a stall). */
        @Nullable
        public TradingStallBlockEntity getStall() {
            return stall;
        }

        /** False while the stall waits for a redstone signal (ONE_SALE_PER_SIGNAL) or has been removed. */
        public boolean isSaleAllowed() {
            return stall == null || (!stall.isRemoved() && stall.isSaleAllowed());
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

