package fr.lordfinn.steveparty.client.utils;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import net.minecraft.item.ItemStack;

public final class BoardSpaceClientUtils {
    private BoardSpaceClientUtils() {}

    /**
     * Side-effect free read of the active cartridge, for rendering code (renderers, color providers
     * running on chunk-builder threads). Unlike {@link BoardSpaceBlockEntity#getActiveCartridgeItemStack()}
     * it never marks the block entity dirty nor updates any state.
     */
    public static ItemStack getDisplayedCartridge(BoardSpaceBlockEntity boardSpace) {
        if (boardSpace == null || boardSpace.getWorld() == null) return ItemStack.EMPTY;
        try {
            ItemStack stack = boardSpace.getStack(boardSpace.getActiveSlot());
            return stack == null ? ItemStack.EMPTY : stack;
        } catch (RuntimeException e) {
            // Concurrent modification from the main thread while meshing / bad slot: render as empty
            return ItemStack.EMPTY;
        }
    }
}
