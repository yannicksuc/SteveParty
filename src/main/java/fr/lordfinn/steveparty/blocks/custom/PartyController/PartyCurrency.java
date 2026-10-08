package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.function.Supplier;

/**
 * The two currencies of a party, as in party board games: the stars rank the players, the coins break the ties. Each party
 * controller picks the item used for each one (its Settings page); a player's stars / coins are the items of that kind
 * in their inventory (same item and same components: a renamed nugget is not a plain nugget).
 */
public enum PartyCurrency {
    /** The main currency: the mod's Party Star by default. */
    STAR("StarItem", () -> ModItems.PARTY_STAR),
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
}
