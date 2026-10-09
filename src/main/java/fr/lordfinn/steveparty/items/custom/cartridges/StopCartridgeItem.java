package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;

import java.util.List;

/** Stop: a forced landing (see StopBoardSpaceBehavior). Its menu: what it does, and its tile's colour. */
public class StopCartridgeItem extends CartridgeItem {
    /** The face of a Stop tile (anthracite, the only dark role colour), unless its cartridge is dyed. */
    public static final int COLOR = 0x454B5A;

    private static final List<CartridgeModule> MODULES = List.of(
            description("board_space_behavior_stop", 3), colorModule(COLOR));

    public StopCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.BOARD_SPACE_STOP;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int tileColor() {
        return COLOR;
    }
}
