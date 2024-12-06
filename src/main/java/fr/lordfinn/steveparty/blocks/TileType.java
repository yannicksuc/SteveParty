package fr.lordfinn.steveparty.blocks;

import net.minecraft.util.StringIdentifiable;

public enum TileType implements StringIdentifiable {
    DEFAULT("default"),
    START("start");

    private final String name;

    TileType(String name) {
        this.name = name;
    }

    @Override
    public String asString() {
        return name;
    }
}