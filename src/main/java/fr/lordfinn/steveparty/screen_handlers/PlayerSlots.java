package fr.lordfinn.steveparty.screen_handlers;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;

import java.util.function.Consumer;

/** The player's inventory slots of a screen handler, laid out like the vanilla containers. */
public final class PlayerSlots {
    private PlayerSlots() {
    }

    /**
     * Adds (through {@code addSlot}, the handler's own) the three rows of the inventory from ({@code left}, {@code top}),
     * then the hotbar 4 pixels below them.
     */
    public static void add(Consumer<Slot> addSlot, PlayerInventory inventory, int left, int top) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot.accept(new Slot(inventory, col + row * 9 + 9, left + col * 18, top + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot.accept(new Slot(inventory, col, left + col * 18, top + 58));
        }
    }
}
