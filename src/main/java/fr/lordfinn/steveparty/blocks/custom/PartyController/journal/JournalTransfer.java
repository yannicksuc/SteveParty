package fr.lordfinn.steveparty.blocks.custom.PartyController.journal;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * {@code count} items like {@code item} (item and components; its own count is 1) the game moved from {@code from} to
 * {@code to} during a party: undone by taking them back from {@code to} first, then giving {@code from} what came back.
 */
public record JournalTransfer(JournalPlace from, JournalPlace to, ItemStack item, int count) {
    public NbtCompound toNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        nbt.put("From", from.toNbt());
        nbt.put("To", to.toNbt());
        nbt.put("Item", item.encode(registries));
        nbt.putInt("Count", count);
        return nbt;
    }

    /** Null when its item is no longer known (a removed mod's): nothing can be undone of it. */
    public static @Nullable JournalTransfer fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        ItemStack item = ItemStack.fromNbt(registries, nbt.get("Item")).orElse(ItemStack.EMPTY);
        int count = nbt.getInt("Count");
        if (item.isEmpty() || count <= 0) return null;
        return new JournalTransfer(JournalPlace.fromNbt(nbt.getCompound("From")), JournalPlace.fromNbt(nbt.getCompound("To")),
                item.copyWithCount(1), count);
    }
}
