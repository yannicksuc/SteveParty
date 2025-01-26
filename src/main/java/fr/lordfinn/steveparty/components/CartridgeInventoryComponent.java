package fr.lordfinn.steveparty.components;

import com.mojang.datafixers.util.Either;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CartridgeInventoryComponent implements Inventory {
    private final List<ItemStack> items;
    private static ItemStack holder = ItemStack.EMPTY;

    public CartridgeInventoryComponent(int size) {
        this.items = DefaultedList.ofSize(size, ItemStack.EMPTY);
    }
    public CartridgeInventoryComponent(int size, ItemStack holder) {
        this.items = DefaultedList.ofSize(size, ItemStack.EMPTY);
        CartridgeInventoryComponent.holder = holder;
    }

    public CartridgeInventoryComponent(List<ItemStack> itemStacks) {
        this.items = new ArrayList<>(itemStacks);
        while (this.items.size() < 9) {
            this.items.add(ItemStack.EMPTY);
        }
    }

    public void setHolder(ItemStack holder) {
        CartridgeInventoryComponent.holder = holder;
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
        if (slot >= items.size()) {
            return;
            }
        items.set(slot, stack);
        markDirty();
    }

    @Override
    public void markDirty() {
        if (CartridgeInventoryComponent.holder.getItem() instanceof InventoryCartridgeItem) {
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
    public static CartridgeInventoryComponent fromNbt(RegistryWrapper.WrapperLookup registries, NbtCompound nbt) {
        NbtList list = nbt.getList("Items", 10);  // List of NBT tags for items
        CartridgeInventoryComponent inventory = new CartridgeInventoryComponent(list.size());
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

    public static final Codec<ItemStack> CUSTOM_ITEMSTACK_CODEC = Codec.either(
            ItemStack.CODEC,
            Codec.unit(ItemStack.EMPTY)
    ).xmap(
            either -> either.map(stack -> stack, stack -> stack),
            stack -> stack.isEmpty() ? Either.right(stack) : Either.left(stack)
    );

    public static final Codec<CartridgeInventoryComponent> CODEC = RecordCodecBuilder.create(builder ->
            builder.group(
                    Codec.list(CUSTOM_ITEMSTACK_CODEC).fieldOf("items")
                            .forGetter(component -> component.items)
            ).apply(builder, CartridgeInventoryComponent::new)
    );

    public ItemStack getHolder() {
        return CartridgeInventoryComponent.holder;
    }

    public static InventoryCartridgeItem getHolderItem() {
        if (CartridgeInventoryComponent.holder == null) return null;
        if (CartridgeInventoryComponent.holder.isEmpty()) return null;
        return (InventoryCartridgeItem) CartridgeInventoryComponent.holder.getItem();
    }

    public List<ItemStack> getItems() {
        return items;
    }
}
