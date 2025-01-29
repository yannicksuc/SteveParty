package fr.lordfinn.steveparty.components;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;

import java.util.List;

import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_COMPONENT;

public class InventoryComponent extends SimpleInventory {
    private static ItemStack holder = ItemStack.EMPTY;

    public InventoryComponent(int size) {
        super(size);
    }

    public InventoryComponent(int size, ItemStack holder) {
        super(size);
        InventoryComponent.holder = holder;
    }

    public InventoryComponent(List<ItemStack> itemStacks) {
        super(itemStacks.toArray(new ItemStack[0]));
    }

    public void setHolder(ItemStack holder) {
        InventoryComponent.holder = holder;
    }

    public ItemStack getHolder() {
        return InventoryComponent.holder;
    }

    public List<ItemStack> getItems() {
        return this.heldStacks;
    }

    @Override
    public void markDirty() {
        if (InventoryComponent.holder.getItem() instanceof InventoryCartridgeItem) {
            ItemStack stack = this.getHolder();
            if (stack == null || stack.isEmpty()) return;
            stack.set(INVENTORY_COMPONENT, this);
        }
    }

    public static final Codec<ItemStack> CUSTOM_ITEMSTACK_CODEC = Codec.either(
            ItemStack.CODEC,
            Codec.unit(ItemStack.EMPTY)
    ).xmap(
            either -> either.map(stack -> stack, stack -> stack),
            stack -> stack.isEmpty() ? Either.right(stack) : Either.left(stack)
    );

    public static final Codec<InventoryComponent> CODEC = RecordCodecBuilder.create(builder ->
            builder.group(
                    Codec.list(CUSTOM_ITEMSTACK_CODEC).fieldOf("items")
                            .forGetter(InventoryComponent::getItems)
            ).apply(builder, InventoryComponent::new)
    );

    public static InventoryComponent getInventoryFromStack(ItemStack stack, int size) {
        if (stack.contains(INVENTORY_COMPONENT) &&
                stack.get(INVENTORY_COMPONENT) instanceof InventoryComponent inventory) {
            inventory.setHolder(stack);
            return inventory;
        } else {
            InventoryComponent inventory = new InventoryComponent(size, stack);
            stack.set(INVENTORY_COMPONENT, inventory);
            return inventory;
        }
    }
}
