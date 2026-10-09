package fr.lordfinn.steveparty.blocks.custom.PartyController;

import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * The party program of a Party Controller: party cards read in order (dashboard, Program page). Empty: the default
 * party. Any change marks the controller dirty.
 */
public final class PartyProgram {
    private final SimpleInventory inventory;

    PartyProgram(Runnable onChanged) {
        this.inventory = new SimpleInventory(PartyControllerEntity.PROGRAM_SLOTS) {
            @Override
            public void markDirty() {
                super.markDirty();
                onChanged.run();
            }
        };
    }

    public SimpleInventory inventory() {
        return inventory;
    }

    /** The cards in reading order (copies). Empty: the default party. */
    public List<ItemStack> cards() {
        List<ItemStack> cards = new ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) cards.add(stack.copy());
        }
        return cards;
    }

    void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        if (inventory.isEmpty()) return;
        NbtCompound programNbt = new NbtCompound();
        Inventories.writeNbt(programNbt, inventory.getHeldStacks(), wrapper);
        nbt.put("PartyProgram", programNbt);
    }

    void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        inventory.getHeldStacks().clear();
        Inventories.readNbt(nbt.getCompound("PartyProgram"), inventory.getHeldStacks(), wrapper);
    }
}
