package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;

import java.util.List;

/** Rejouer / Roll Again: the tile gives the token's owner another turn (see ReplayBoardSpaceBehavior). */
public class ReplayCartridgeItem extends CartridgeItem {
    /** The face of a Replay tile (cyan), unless its cartridge is dyed. */
    public static final int COLOR = 0x1CC6D6;

    private static final List<CartridgeModule> MODULES = List.of(
            description("replay_cartridge", 3), colorModule(COLOR));

    public ReplayCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_REPLAY;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int tileColor() {
        return COLOR;
    }

    /** Its name says what it does. */
    @Override
    protected boolean hasSummary() {
        return false;
    }
}
