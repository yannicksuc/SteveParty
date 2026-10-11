package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_COMPONENT;

/**
 * Server side: the {@value ShopCartridgeItem#SLOTS} offer slots of the Shop Cartridge given by {@code source} (in hand,
 * or in a block's slot), in a Trading Stall's order ({@link ShopCartridgeItem#stallSlots}, an old cartridge's 3 offers
 * included), written back into it on every change (in the new order), then {@code onWrite} (saving and sending its
 * block). Like {@link CartridgeGhostInventory}, it only holds ghost copies.
 */
public class ShopOffersInventory extends SimpleInventory {
    private final Supplier<ItemStack> source;
    private final Runnable onWrite;
    private ItemStack loadedFrom = ItemStack.EMPTY;
    private InventoryComponent loaded;
    private boolean writing;

    public ShopOffersInventory(Supplier<ItemStack> source, Runnable onWrite) {
        super(ShopCartridgeItem.SLOTS);
        this.source = source;
        this.onWrite = onWrite;
    }

    /** Reloads the working copy when the cartridge (or its offers) changed elsewhere. */
    private void refresh() {
        if (writing) return;
        ItemStack stack = source.get();
        InventoryComponent component = stack.get(INVENTORY_COMPONENT);
        if (stack == loadedFrom && component == loaded) return;
        loadedFrom = stack;
        loaded = component;
        List<ItemStack> slots = ShopCartridgeItem.stallSlots(stack);
        writing = true;
        try {
            for (int i = 0; i < size(); i++) super.setStack(i, slots.get(i));
        } finally {
            writing = false;
        }
    }

    @Override
    public ItemStack getStack(int slot) {
        refresh();
        return super.getStack(slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        refresh();
        super.setStack(slot, stack);
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (writing) return;
        ItemStack stack = source.get();
        if (!(stack.getItem() instanceof ShopCartridgeItem)) return;
        writing = true;
        try {
            List<ItemStack> slots = new ArrayList<>(size());
            for (int i = 0; i < size(); i++) slots.add(super.getStack(i));
            ShopCartridgeItem.setStallSlots(stack, slots);
            loadedFrom = stack;
            loaded = stack.get(INVENTORY_COMPONENT);
            onWrite.run();
        } finally {
            writing = false;
        }
    }
}
