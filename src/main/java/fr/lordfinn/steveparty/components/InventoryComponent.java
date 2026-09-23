package fr.lordfinn.steveparty.components;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_COMPONENT;

/**
 * Immutable data component holding a list of item stacks (cartridge ghost slots, catalogue pages...).
 * <p>
 * The component never exposes its internal stacks: every accessor returns copies, so it can safely be
 * shared between item stack copies. To edit it, work on a {@link SimpleInventory} obtained with
 * {@link #toInventory(int)} / {@link #getInventoryFromStack(ItemStack, int)} and write it back with
 * {@link #writeToStack(ItemStack, Inventory)}.
 */
public final class InventoryComponent {
    /** Max count accepted by {@link ItemStack#CODEC}. Bigger (ghost) counts are stored in the "counts" field. */
    private static final int CODEC_MAX_COUNT = 99;

    private final List<ItemStack> items;

    public InventoryComponent(List<ItemStack> items) {
        List<ItemStack> copy = new ArrayList<>(items.size());
        for (ItemStack stack : items) {
            copy.add(stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        this.items = List.copyOf(copy);
    }

    public static InventoryComponent empty(int size) {
        List<ItemStack> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) list.add(ItemStack.EMPTY);
        return new InventoryComponent(list);
    }

    public static InventoryComponent of(Inventory inventory) {
        List<ItemStack> list = new ArrayList<>(inventory.size());
        for (int i = 0; i < inventory.size(); i++) list.add(inventory.getStack(i));
        return new InventoryComponent(list);
    }

    /** @return a mutable list of copies of the stored stacks (empty slots included). */
    public List<ItemStack> getItems() {
        List<ItemStack> list = new ArrayList<>(items.size());
        for (ItemStack stack : items) list.add(stack.copy());
        return list;
    }

    /** @return a copy of the stack at the given slot, or {@link ItemStack#EMPTY}. */
    public ItemStack getStack(int slot) {
        if (slot < 0 || slot >= items.size()) return ItemStack.EMPTY;
        return items.get(slot).copy();
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    /** @return a fresh, independent inventory of at least {@code size} slots filled with copies of the stored stacks. */
    public SimpleInventory toInventory(int size) {
        SimpleInventory inventory = new SimpleInventory(Math.max(size, items.size()));
        for (int i = 0; i < items.size(); i++) {
            inventory.setStack(i, items.get(i).copy());
        }
        return inventory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InventoryComponent other)) return false;
        return ItemStack.stacksEqual(this.items, other.items);
    }

    @Override
    public int hashCode() {
        return ItemStack.listHashCode(this.items);
    }

    // ========================
    //   CODEC
    // ========================

    /** Kept for backward compatibility with already saved data: empty slots are stored as {@code {}}. */
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
                            .forGetter(InventoryComponent::getEncodedItems),
                    Codec.INT.listOf().optionalFieldOf("counts", List.of())
                            .forGetter(InventoryComponent::getEncodedCounts)
            ).apply(builder, InventoryComponent::decode)
    );

    /** Stacks with a count clamped to what {@link ItemStack#CODEC} accepts. */
    private List<ItemStack> getEncodedItems() {
        List<ItemStack> list = new ArrayList<>(items.size());
        for (ItemStack stack : items) {
            list.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(Math.min(stack.getCount(), CODEC_MAX_COUNT)));
        }
        return list;
    }

    /** Real counts, only written when at least one slot exceeds the codec limit (ghost quantities). */
    private List<Integer> getEncodedCounts() {
        boolean needed = items.stream().anyMatch(stack -> stack.getCount() > CODEC_MAX_COUNT);
        if (!needed) return List.of();
        List<Integer> counts = new ArrayList<>(items.size());
        for (ItemStack stack : items) counts.add(stack.isEmpty() ? 0 : stack.getCount());
        return counts;
    }

    private static InventoryComponent decode(List<ItemStack> stacks, List<Integer> counts) {
        if (counts.size() != stacks.size()) return new InventoryComponent(stacks);
        List<ItemStack> list = new ArrayList<>(stacks.size());
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            int count = counts.get(i);
            list.add(!stack.isEmpty() && count > 0 ? stack.copyWithCount(count) : stack);
        }
        return new InventoryComponent(list);
    }

    // ========================
    //   ITEM STACK HELPERS
    // ========================

    /**
     * @return a new, independent inventory backed by {@code stack}: every change is written back into the
     * stack's {@link ModComponents#INVENTORY_COMPONENT} (see {@link ItemStackBackedInventory}).
     */
    public static ItemStackBackedInventory getInventoryFromStack(ItemStack stack, int size) {
        return new ItemStackBackedInventory(stack, size);
    }

    /** @return a fresh copy of the inventory stored in {@code stack} (not written back automatically). */
    public static SimpleInventory copyInventoryFromStack(ItemStack stack, int size) {
        InventoryComponent component = stack.get(INVENTORY_COMPONENT);
        return component != null ? component.toInventory(size) : new SimpleInventory(size);
    }

    /** Stores a snapshot of {@code inventory} into {@code stack}. */
    public static void writeToStack(ItemStack stack, Inventory inventory) {
        if (stack == null || stack.isEmpty()) return;
        stack.set(INVENTORY_COMPONENT, of(inventory));
    }
}
