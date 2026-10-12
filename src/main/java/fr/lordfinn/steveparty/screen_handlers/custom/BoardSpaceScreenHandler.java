package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenuHost;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.Property;
import org.jetbrains.annotations.Nullable;

/**
 * A board space's interface: its cartridge slot(s) (16 in an Advanced Tile), the player's inventory and, on the right,
 * the menu of the selected slot's cartridge (see {@link CartridgeMenuHost}). The selected slot is the active one when
 * the interface opens; a right click on another slot of an Advanced Tile selects it ({@link #onButtonClick}).
 */
public class BoardSpaceScreenHandler extends CartridgeContainerScreenHandler {
    /** The tile part's width; the cartridge's menu starts after it, with a gap. */
    public static final int TILE_W = BODY_W;

    private final Property selected = Property.create();
    private final BoardSpaceBlockEntity boardSpace;

    //Main constructor (Called on the server and the client)
    public BoardSpaceScreenHandler(int syncId, PlayerInventory playerInventory, BoardSpaceBlockEntity blockEntity) {
        super(ModScreensHandlers.TILE_SCREEN_HANDLER, syncId);
        this.inventory = blockEntity;
        this.boardSpace = blockEntity;
        init(playerInventory, 101);
        selected.set(blockEntity == null ? 0 : Math.clamp(blockEntity.getActiveSlot(), 0, Math.max(0, blockEntity.size() - 1)));
        addProperty(selected);
        addCartridgeMenu(playerInventory.player, blockEntity == null ? null : blockEntity.getPos());
    }

    //Client constructor
    public BoardSpaceScreenHandler(int syncId, PlayerInventory playerInventory, BlockPosPayload blockPosPayload) {
        this(syncId, playerInventory, (BoardSpaceBlockEntity) playerInventory.player.getWorld().getBlockEntity(blockPosPayload.pos()));
    }

    @Override
    public void setupScreen() {
        int m, l;
        if (this.inventory.size() == 16) {
            for (m = 0; m < 4; ++m) {
                for (l = 0; l < 4; ++l) {
                    this.addSlot(new CartridgeCustomSlot(this.inventory, l + m * 4, 53 + l * 18, 12 + m * 18));
                }
            }
        }

        if (this.inventory.size() == 1) {
            this.addSlot(new CartridgeCustomSlot(this.inventory, 0, 80, 39));
        }
    }

    public int getActiveSlot() {
        if (inventory == null) return -1;
        return ((BoardSpaceBlockEntity)inventory).getActiveSlot();
    }

    /** The slot whose cartridge's menu is shown. */
    @Override
    public int getSelectedSlot() {
        return inventory == null ? 0 : Math.clamp(selected.get(), 0, Math.max(0, inventory.size() - 1));
    }

    public @Nullable BoardSpaceBlockEntity boardSpace() {
        return boardSpace;
    }

    /** Button {@code id}: selects slot {@code id} (a right click on it). */
    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id < 0 || id >= inventory.size()) return false;
        selected.set(id);
        return true;
    }
}
