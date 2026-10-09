package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.GhostSlotsModule;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;

import java.util.function.Supplier;

import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_COMPONENT;
import static fr.lordfinn.steveparty.components.ModComponents.IS_NEGATIVE;

/**
 * Server side: the ghost items ({@link GhostSlotsModule}) of the cartridge given by {@code source} (the one in hand, or in a block's
 * slot, which may change while the screen is open), read from its {@link InventoryComponent} and written back into
 * it on every change, then {@code onWrite} (saving and sending its block).
 */
public class CartridgeGhostInventory extends SimpleInventory {
    private final Supplier<ItemStack> source;
    private final Runnable onWrite;
    private ItemStack loadedFrom = ItemStack.EMPTY;
    private InventoryComponent loaded;
    private boolean writing;

    public CartridgeGhostInventory(Supplier<ItemStack> source, Runnable onWrite) {
        super(GhostSlotsModule.COUNT);
        this.source = source;
        this.onWrite = onWrite;
    }

    /** Reloads the working copy when the cartridge (or its items) changed elsewhere. */
    private void refresh() {
        if (writing) return;
        ItemStack stack = source.get();
        InventoryComponent component = stack.get(INVENTORY_COMPONENT);
        if (stack == loadedFrom && component == loaded) return;
        loadedFrom = stack;
        loaded = component;
        writing = true;
        try {
            for (int i = 0; i < size(); i++) {
                super.setStack(i, component != null && i < component.size() ? component.getStack(i) : ItemStack.EMPTY);
            }
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
        GhostSlotsModule ghosts = GhostSlotsModule.of(stack);
        if (ghosts == null) return;
        writing = true;
        try {
            for (int i = 0; i < size(); i++) {
                ItemStack item = super.getStack(i);
                // Past its slots: nothing; slots that only give: never a taken item (the wheel stops at 1)
                if (i >= ghosts.count() && !item.isEmpty()) super.setStack(i, ItemStack.EMPTY);
                else if (!ghosts.signed()) item.remove(IS_NEGATIVE);
            }
            InventoryComponent.writeToStack(stack, this);
            loadedFrom = stack;
            loaded = stack.get(INVENTORY_COMPONENT);
            onWrite.run();
        } finally {
            writing = false;
        }
    }
}
