package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import net.minecraft.entity.player.PlayerInventory;

public class TileScreenHandler extends CartridgeContainerScreenHandler {

    //Main constructor (Called on the server and the client)
    public TileScreenHandler(int syncId, PlayerInventory playerInventory, BoardSpaceBlockEntity blockEntity) {
        super(ModScreensHandlers.TILE_SCREEN_HANDLER, syncId);
        this.inventory = blockEntity;
        init(playerInventory, 101);
    }

    //Client constructor
    public TileScreenHandler(int syncId, PlayerInventory playerInventory, BlockPosPayload blockPosPayload) {
        this(syncId, playerInventory, (BoardSpaceBlockEntity) playerInventory.player.getWorld().getBlockEntity(blockPosPayload.pos()));
    }

    @Override
    public void setupScreen() {
        int m, l;
        if (this.inventory.size() == 16) {
            for (m = 0; m < 4; ++m) {
                for (l = 0; l < 4; ++l) {
                    this.addSlot(new CustomSlot(this.inventory, l + m * 4, 53 + l * 18, 12 + m * 18));
                }
            }
        }
    }

    public int getActiveSlot() {
        if (inventory == null) return -1;
        return ((BoardSpaceBlockEntity)inventory).getActiveSlot(); // Access the method from the block entity
    }
}
