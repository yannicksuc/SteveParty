package fr.lordfinn.steveparty.blocks.tiles.behaviors;

import fr.lordfinn.steveparty.blocks.tiles.TileType;

import java.util.HashMap;
import java.util.Map;

public class TileBehaviorFactory {
        private static final Map<TileType, ATileBehavior> TILES = new HashMap<>();

        static {
            TILES.put(TileType.START, new StartTileBehavior());
            TILES.put(TileType.DEFAULT, new DefaultTileBehavior());
        }

        public static ATileBehavior get(TileType type) {
            return TILES.get(type);
        }
}
