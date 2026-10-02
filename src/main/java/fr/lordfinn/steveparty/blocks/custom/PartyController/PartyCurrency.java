package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.function.Supplier;

/**
 * The two currencies of a party, like in Mario Party: the stars rank the players, the coins break the ties. Each party
 * controller picks the item used for each one (its Settings page); a player's stars / coins are the items of that kind
 * in their inventory (same item and same components: a renamed nugget is not a plain nugget).
 */
public enum PartyCurrency {
    /** The main currency: the mod's Power Star by default. */
    STAR("StarItem", () -> ModItems.POWER_STAR),
    /** The sub-currency: the mod's coin by default (a gold nugget minted at the crafting table). */
    COIN("CoinItem", () -> ModItems.COIN);

    private final String nbtKey;
    /** Read when asked for, not when the enum loads: the mod's items may not be registered yet. */
    private final Supplier<Item> defaultItem;

    PartyCurrency(String nbtKey, Supplier<Item> defaultItem) {
        this.nbtKey = nbtKey;
        this.defaultItem = defaultItem;
    }

    public String nbtKey() {
        return nbtKey;
    }

    public ItemStack defaultStack() {
        return new ItemStack(defaultItem.get());
    }

    public PartyCurrency other() {
        return this == STAR ? COIN : STAR;
    }

    /** The template stored for a picked item: one of it, or the default one for nothing. */
    public ItemStack template(ItemStack picked) {
        return picked.isEmpty() ? defaultStack() : picked.copyWithCount(1);
    }

    /** Gives {@code count} items of the template to a player (what doesn't fit is dropped at their feet). */
    public static void give(net.minecraft.entity.player.PlayerEntity player, ItemStack template, int count) {
        if (template.isEmpty()) return;
        int left = count;
        while (left > 0) {
            int size = Math.min(left, template.getMaxCount());
            player.getInventory().offerOrDrop(template.copyWithCount(size));
            left -= size;
        }
    }

    /**
     * Takes up to {@code count} items matching the template (same item and components) from the inventory.
     *
     * @return how many were taken: never more than it held
     */
    public static int take(Inventory inventory, ItemStack template, int count) {
        if (template.isEmpty()) return 0;
        int left = count;
        for (int slot = 0; slot < inventory.size() && left > 0; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty() || !ItemStack.areItemsAndComponentsEqual(template, stack)) continue;
            int taken = Math.min(left, stack.getCount());
            stack.decrement(taken);
            if (stack.isEmpty()) inventory.setStack(slot, ItemStack.EMPTY);
            left -= taken;
        }
        if (left != count) inventory.markDirty();
        return count - left;
    }

    /** How many items of the inventory match the template (same item and components). */
    public static int count(Inventory inventory, ItemStack template) {
        if (template.isEmpty()) return 0;
        int count = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && ItemStack.areItemsAndComponentsEqual(template, stack)) count += stack.getCount();
        }
        return count;
    }
}
