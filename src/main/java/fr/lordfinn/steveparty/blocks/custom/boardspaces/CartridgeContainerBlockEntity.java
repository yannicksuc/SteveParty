package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public abstract class CartridgeContainerBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {
    private final int size;
    public final DefaultedList<ItemStack> heldStacks;

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

    @Override
    public ItemStack getStack(int slot) {
        return this.heldStacks.get(slot % size);
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
        super.markDirty();
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

    protected List<BlockPos> getDestinations(int slot) {
        ItemStack stack = this.getStack(slot);
        if (stack.isEmpty()) return List.of();
        DestinationsComponent component = stack.getOrDefault(ModComponents.DESTINATIONS_COMPONENT, null);
        if (component == null) return List.of();
        return component.destinations();
    }

    @Override
    public Text getDisplayName() {
        return Text.empty();
    }
}
