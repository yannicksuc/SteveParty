package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;

import java.util.List;
import java.util.Optional;

public class PersistentInventoryComponent implements Inventory {
    private final List<ItemStack> items;
    private ItemStack holder;

    public PersistentInventoryComponent(int size) {
        this.items = DefaultedList.ofSize(size, ItemStack.EMPTY);
        this.holder = ItemStack.EMPTY;
    }
    public PersistentInventoryComponent(int size, ItemStack holder) {
        this.items = DefaultedList.ofSize(size, ItemStack.EMPTY);
        this.holder = holder;
    }

    public PersistentInventoryComponent(List<ItemStack> itemStacks) {
        this.items = itemStacks;
        this.holder = ItemStack.EMPTY;
    }

    public void setHolder(ItemStack holder) {
        this.holder = holder;
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemStack : items) {
            if (!itemStack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        if (slot >= items.size()) return ItemStack.EMPTY;
        return items.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack stack = items.get(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (stack.getCount() <= amount) {
            items.set(slot, ItemStack.EMPTY);
        } else {
            stack.decrement(amount);
        }
        markDirty();
        return stack;
    }

    @Override
    public ItemStack removeStack(int slot) {
        markDirty();
        return items.remove(slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot >= items.size())
            return;
        items.set(slot, stack);
        markDirty();
    }

    @Override
    public void markDirty() {
        if (this.holder.getItem() instanceof InventoryCartridgeItem) {
            InventoryCartridgeItem.saveInventory(this);
        }
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return true;
    }

    // Serialize inventory to NBT
    public NbtCompound toNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        NbtList list = new NbtList();
        for (ItemStack stack : items) {
            NbtElement itemTag = stack.toNbt(registries);
            list.add(itemTag);
        }
        nbt.put("Items", list);
        return nbt;
    }

    // Deserialize inventory from NBT
    public static PersistentInventoryComponent fromNbt(RegistryWrapper.WrapperLookup registries, NbtCompound nbt) {
        PersistentInventoryComponent inventory = new PersistentInventoryComponent(6); // 6 slots as an example
        NbtList list = nbt.getList("Items", 10);  // List of NBT tags for items
        for (int i = 0; i < list.size(); i++) {
            NbtCompound itemTag = list.getCompound(i);
            Optional<ItemStack> stack = ItemStack.fromNbt(registries, itemTag);
            if (stack.isPresent())
                inventory.setStack(i, stack.get());
        }
        return inventory;
    }

    @Override
    public void clear() {
        items.clear();
    }

    public static final Codec<PersistentInventoryComponent> CODEC = RecordCodecBuilder.create(builder -> // Return a new PersistentInventoryComponent with the items added
            builder.group(
            Codec.list(ItemStack.CODEC).fieldOf("items").forGetter(component -> component.items)
    ).apply(builder, PersistentInventoryComponent::new));

    public ItemStack getHolder() {
        return this.holder;
    }
}
