package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;

/** Rejouer / Roll Again: the tile gives the token's owner another turn (see ReplayBoardSpaceBehavior). */
public class ReplayCartridgeItem extends CartridgeItem {
    public ReplayCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_REPLAY;
    }
}
