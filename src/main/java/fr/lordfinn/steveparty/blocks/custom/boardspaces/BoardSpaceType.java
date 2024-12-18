package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import net.minecraft.util.StringIdentifiable;

public enum BoardSpaceType implements StringIdentifiable {
    DEFAULT("default"),
    TILE_START("tile_start"),
    BOARD_SPACE_STOP("board_space_stop");

    private final String name;

    BoardSpaceType(String name) {
        this.name = name;
    }

    @Override
    public String asString() {
        return name;
    }
}