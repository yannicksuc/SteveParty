package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;

import java.util.HashMap;
import java.util.Map;

public class BoardSpaceBehaviorFactory {
        private static final Map<BoardSpaceType, ABoardSpaceBehavior> BOARD_SPACES_TYPES = new HashMap<>();

        static {
            BOARD_SPACES_TYPES.put(BoardSpaceType.TILE_START, new StartTileBehavior());
            BOARD_SPACES_TYPES.put(BoardSpaceType.DEFAULT, new DefaultBoardSpaceBehavior());
            BOARD_SPACES_TYPES.put(BoardSpaceType.BOARD_SPACE_STOP, new StopBoardSpaceBehavior());
            BOARD_SPACES_TYPES.put(BoardSpaceType.TILE_INVENTORY_INTERACTOR, new InventoryInteractorTileBehavior());
            BOARD_SPACES_TYPES.put(BoardSpaceType.BOARD_SPACE_SHOP, new ShopBoardSpaceBehavior());
            BOARD_SPACES_TYPES.put(BoardSpaceType.TILE_ADVANCE_BACK, new AdvanceBackTileBehavior());
            BOARD_SPACES_TYPES.put(BoardSpaceType.TILE_REPLAY, new ReplayBoardSpaceBehavior());
            BOARD_SPACES_TYPES.put(BoardSpaceType.TILE_TELEPORT, new TeleportTileBehavior());
            BOARD_SPACES_TYPES.put(BoardSpaceType.TILE_STAR, new StarBoardSpaceBehavior());
            BOARD_SPACES_TYPES.put(BoardSpaceType.TILE_GLANDOUILLE, new GlandouilleTileBehavior());
        }

        public static ABoardSpaceBehavior get(BoardSpaceType type) {
            return BOARD_SPACES_TYPES.get(type);
        }
}
