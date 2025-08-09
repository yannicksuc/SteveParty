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
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
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
                TradeOffer offer = new TradeOffer(
                        new TradedItem(firstBuyItem.getItem(), firstBuyItem.getCount()),
                        secondBuyItem.isEmpty() ? Optional.empty() : Optional.of(new TradedItem(secondBuyItem.getItem(), secondBuyItem.getCount())),
                        sellItem,
                        1, 0, 0
                );
                offers.add(offer);
            }
        }
        return offers;
    }

}

