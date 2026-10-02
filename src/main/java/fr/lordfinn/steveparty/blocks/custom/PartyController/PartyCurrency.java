package fr.lordfinn.steveparty.blocks.custom.PartyController;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * The two currencies of a party, like in Mario Party: the stars rank the players, the coins break the ties. Each party
 * controller picks the item used for each one (its Settings page); a player's stars / coins are the items of that kind
 * in their inventory (same item and same components: a renamed nugget is not a plain nugget).
 */
public enum PartyCurrency {
    /** The main currency: a nether star by default, the rarest looking star of the game. */
    STAR("StarItem", Items.NETHER_STAR),
    /** The sub-currency: an emerald by default, the money of Minecraft itself. */
    COIN("CoinItem", Items.EMERALD);

    private final String nbtKey;
    private final Item defaultItem;

    PartyCurrency(String nbtKey, Item defaultItem) {
        this.nbtKey = nbtKey;
        this.defaultItem = defaultItem;
    }

    public String nbtKey() {
        return nbtKey;
    }

    public ItemStack defaultStack() {
        return new ItemStack(defaultItem);
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
