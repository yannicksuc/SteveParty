package fr.lordfinn.steveparty.utils;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Counting, taking and giving items of exactly one kind (same item and components): the party's coins and stars in
 * a player's inventory or in the bank's chest, the dice that pay or cost coins...
 */
public final class InventoryUtils {
    private InventoryUtils() {
    }

    /** How many items of the inventory match the template; 0 for no inventory or an empty template. */
    public static int count(@Nullable Inventory inventory, ItemStack template) {
        if (inventory == null || template.isEmpty()) return 0;
        int count = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && ItemStack.areItemsAndComponentsEqual(template, stack)) count += stack.getCount();
        }
        return count;
    }

    /**
     * Takes up to {@code amount} items matching the template out of the inventory.
     *
     * @return how many were taken: never more than it held, 0 for no inventory
     */
    public static int take(@Nullable Inventory inventory, ItemStack template, int amount) {
        if (inventory == null || template.isEmpty() || amount <= 0) return 0;
        int taken = 0;
        for (int slot = 0; slot < inventory.size() && taken < amount; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty() || !ItemStack.areItemsAndComponentsEqual(template, stack)) continue;
            int count = Math.min(stack.getCount(), amount - taken);
            stack.decrement(count);
            if (stack.isEmpty()) inventory.setStack(slot, ItemStack.EMPTY);
            taken += count;
        }
        if (taken > 0) inventory.markDirty();
        return taken;
    }

    /** Gives {@code count} items of the template to a player, in full stacks; what doesn't fit falls at their feet. */
    public static void giveOrDrop(PlayerEntity player, ItemStack template, int count) {
        if (template.isEmpty()) return;
        int left = count;
        while (left > 0) {
            int size = Math.min(left, template.getMaxCount());
            player.getInventory().offerOrDrop(template.copyWithCount(size));
            left -= size;
        }
    }
}
