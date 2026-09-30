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
    /** The sub-currency: a gold nugget by default, a small gold coin, cheap and stackable. */
    COIN("CoinItem", Items.GOLD_NUGGET);

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
