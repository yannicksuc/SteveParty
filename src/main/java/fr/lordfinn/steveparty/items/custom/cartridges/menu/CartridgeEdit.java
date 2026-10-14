package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A change being written in a cartridge (server side): the cartridge, and the block holding it (null: in a hand) with
 * its slot, and what the player holds on the cursor of the menu (a page put in the Mini-game Cartridge). The block is
 * saved and sent to the clients after the change (see {@link CartridgeRef#commit}).
 */
public record CartridgeEdit(ItemStack stack, @Nullable BlockEntity holder, int slot, ItemStack cursor) {
    public CartridgeEdit(ItemStack stack, @Nullable BlockEntity holder, int slot) {
        this(stack, holder, slot, ItemStack.EMPTY);
    }

    /** The board space holding the cartridge, if it is one. */
    public @Nullable BoardSpaceBlockEntity boardSpace() {
        return holder instanceof BoardSpaceBlockEntity boardSpace ? boardSpace : null;
    }

    /** Whether the cartridge is the one giving its role to its board space now (its active slot). */
    public boolean isActiveOnBoardSpace() {
        BoardSpaceBlockEntity boardSpace = boardSpace();
        return boardSpace != null && boardSpace.getActiveCartridgeItemStack() == stack;
    }
}
