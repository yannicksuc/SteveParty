package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import net.minecraft.util.StringIdentifiable;

public enum BoardSpaceType implements StringIdentifiable {
    DEFAULT("default"),
    TILE_START("tile_start"),
    BOARD_SPACE_STOP("board_space_stop"),
    TILE_INVENTORY_INTERACTOR("tile_inventory_interactor"),
    /** Shop Cartridge: a shop stop (see ShopStops). */
    BOARD_SPACE_SHOP("board_space_shop"),
    /** Move Forward / Back: a token landing here moves on some spaces forward or back (see AdvanceBackTileBehavior). */
    TILE_ADVANCE_BACK("tile_advance_back"),
    /** Rejouer / Roll Again: the token's owner plays again right away (see ReplayBoardSpaceBehavior). */
    TILE_REPLAY("tile_replay"),
    /** « Téléportation »: a token landing here is sent to one of the tile's teleport targets (see TileTeleport). */
    TILE_TELEPORT("tile_teleport"),
    /** « Étoile »: a star space, where the party's star may stand and be bought (see fr.lordfinn.steveparty.service.PartyStars). */
    TILE_STAR("tile_star"),
    /** « Glandouille »: a tower of Glandouilles pushes the tokens some spaces on (see GlandouilleTileBehavior). */
    TILE_GLANDOUILLE("tile_glandouille"),
    /** « Frousseux »: a Frousseux steals coins or stars from another player (see FrousseuxTileBehavior). */
    TILE_FROUSSEUX("tile_frousseux"),
    /** « Mistigri »: the black cat of bad luck rolls his loaded die and passes a sentence (see MistigriTileBehavior). */
    TILE_MISTIGRI("tile_mistigri"),
    /** « Obstacle à seuil »: a token reaching it goes on only if its roll meets a condition (see ThresholdTileBehavior). */
    TILE_THRESHOLD("tile_threshold"),
    /** « Pot commun »: passing tokens feed a pot, the token stopping on it wins it (see PotTileBehavior). */
    TILE_POT("tile_pot"),
    /** « Portail à clé »: gates on some exits, opened by a Gate Key (see KeyGateTileBehavior). */
    TILE_KEY_GATE("tile_key_gate");

    private final String name;

    BoardSpaceType(String name) {
        this.name = name;
    }

    @Override
    public String asString() {
        return name;
    }
}