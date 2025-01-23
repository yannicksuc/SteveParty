package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import com.google.common.collect.Lists;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.screens.TileScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.InventoryChangedListener;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class CartridgeContainerBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {
    private final int size;
    public final DefaultedList<ItemStack> heldStacks;
    @Nullable
    private List<InventoryChangedListener> listeners;

    public CartridgeContainerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int size, DefaultedList<ItemStack> heldStacks) {
        super(type, pos, state);
        this.size = size;
        this.heldStacks = heldStacks;
    }

    public CartridgeContainerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int size) {
        super(type, pos, state);
        this.size = size;
        this.heldStacks = DefaultedList.ofSize(size, ItemStack.EMPTY);
    }

    public void addListener(InventoryChangedListener listener) {
        if (this.listeners == null) {
            this.listeners = Lists.newArrayList();
        }

        this.listeners.add(listener);
    }

    public void removeListener(InventoryChangedListener listener) {
        if (this.listeners != null) {
            this.listeners.remove(listener);
        }

    }

    @Override
    public ItemStack getStack(int slot) {
        return slot >= 0 && slot < this.heldStacks.size() ? this.heldStacks.get(slot) : ItemStack.EMPTY;
    }

    public List<ItemStack> clearToList() {
        List<ItemStack> list = this.heldStacks.stream().filter((stack) -> !stack.isEmpty()).collect(Collectors.toList());
        this.clear();
        return list;
    }
    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack itemStack = Inventories.splitStack(this.heldStacks, slot, amount);
        if (!itemStack.isEmpty()) {
            this.markDirty();
        }

        return itemStack;
    }

    public ItemStack removeItem(Item item, int count) {
        ItemStack itemStack = new ItemStack(item, 0);

        for(int i = this.size - 1; i >= 0; --i) {
            ItemStack itemStack2 = this.getStack(i);
            if (itemStack2.getItem().equals(item)) {
                int j = count - itemStack.getCount();
                ItemStack itemStack3 = itemStack2.split(j);
                itemStack.increment(itemStack3.getCount());
                if (itemStack.getCount() == count) {
                    break;
                }
            }
        }

        if (!itemStack.isEmpty()) {
            this.markDirty();
        }

        return itemStack;
    }

    public ItemStack addStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemStack = stack.copy();
            this.addToExistingSlot(itemStack);
            if (itemStack.isEmpty()) {
                return ItemStack.EMPTY;
            } else {
                this.addToNewSlot(itemStack);
                return itemStack.isEmpty() ? ItemStack.EMPTY : itemStack;
            }
        }
    }

    private void addToNewSlot(ItemStack stack) {
        for(int i = 0; i < this.size; ++i) {
            ItemStack itemStack = this.getStack(i);
            if (itemStack.isEmpty()) {
                this.setStack(i, stack.copyAndEmpty());
                return;
            }
        }

    }

    private void addToExistingSlot(ItemStack stack) {
        for(int i = 0; i < this.size; ++i) {
            ItemStack itemStack = this.getStack(i);
            if (ItemStack.areItemsAndComponentsEqual(itemStack, stack)) {
                this.transfer(stack, itemStack);
                if (stack.isEmpty()) {
                    return;
                }
            }
        }

    }

    private void transfer(ItemStack source, ItemStack target) {
        int i = this.getMaxCount(target);
        int j = Math.min(source.getCount(), i - target.getCount());
        if (j > 0) {
            target.increment(j);
            source.decrement(j);
            this.markDirty();
        }

    }

    public boolean canInsert(ItemStack stack) {
        boolean bl = false;

        for (ItemStack itemStack : this.heldStacks) {
            if (itemStack.isEmpty() || ItemStack.areItemsAndComponentsEqual(itemStack, stack) && itemStack.getCount() < itemStack.getMaxCount()) {
                bl = true;
                break;
            }
        }

        return bl;
    }
    @Override
    public ItemStack removeStack(int slot) {
        ItemStack itemStack = (ItemStack)this.heldStacks.get(slot);
        if (itemStack.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            this.heldStacks.set(slot, ItemStack.EMPTY);
            return itemStack;
        }
    }
    @Override
    public void setStack(int slot, ItemStack stack) {
        this.heldStacks.set(slot, stack);
        stack.capCount(this.getMaxCount(stack));
        this.markDirty();
    }
    @Override
    public int size() {
        return this.size;
    }
    @Override
    public boolean isEmpty() {
        Iterator<ItemStack> iterator = this.heldStacks.iterator();

        ItemStack itemStack;
        do {
            if (!iterator.hasNext()) {
                return true;
            }

            itemStack = (ItemStack)iterator.next();
        } while(itemStack.isEmpty());

        return false;
    }
    @Override
    public void markDirty() {
        if (this.listeners != null) {
            for (InventoryChangedListener inventoryChangedListener : this.listeners) {
                inventoryChangedListener.onInventoryChanged(this);
            }
        }

    }
    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return true;
    }
    @Override
    public void clear() {
        this.heldStacks.clear();
        this.markDirty();
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        Inventories.writeNbt(nbt, this.getHeldStacks(), wrapper);
        super.writeNbt(nbt, wrapper);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        try {
            Inventories.readNbt(nbt, this.getHeldStacks(), wrapper);
        } catch (Exception e) {
            Steveparty.LOGGER.error("Failed to read NBT", e);
        }
    }
    public DefaultedList<ItemStack> getHeldStacks() {
        return this.heldStacks;
    }


    @Override
    public Text getDisplayName() {
        return Text.translatable("block.steveparty.cartridge_container");
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new TileScreenHandler(syncId, playerInventory, this);
    }
}
