package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import com.google.common.base.Suppliers;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.AdvanceBackTileBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.DefaultBoardSpaceBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.FrousseuxTileBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.GlandouilleTileBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.InventoryInteractorTileBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.KeyGateTileBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.MistigriTileBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.PotTileBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ReplayBoardSpaceBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ShopBoardSpaceBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.StarBoardSpaceBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.StartTileBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.StopBoardSpaceBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.TeleportTileBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ThresholdTileBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.TrichaudronTileBehavior;
import net.minecraft.util.StringIdentifiable;

import java.util.function.Supplier;

/** The role a cartridge gives its board space, and the behaviour of that role ({@link #behavior}). */
public enum BoardSpaceType implements StringIdentifiable {
    DEFAULT("default", DefaultBoardSpaceBehavior::new),
    TILE_START("tile_start", StartTileBehavior::new),
    BOARD_SPACE_STOP("board_space_stop", StopBoardSpaceBehavior::new),
    TILE_INVENTORY_INTERACTOR("tile_inventory_interactor", InventoryInteractorTileBehavior::new),
    /** Shop Cartridge: a shop stop (see ShopStops). */
    BOARD_SPACE_SHOP("board_space_shop", ShopBoardSpaceBehavior::new),
    /** Move Forward / Back: a token landing here moves on some spaces forward or back (see AdvanceBackTileBehavior). */
    TILE_ADVANCE_BACK("tile_advance_back", AdvanceBackTileBehavior::new),
    /** Rejouer / Roll Again: the token's owner plays again right away (see ReplayBoardSpaceBehavior). */
    TILE_REPLAY("tile_replay", ReplayBoardSpaceBehavior::new),
    /** « Téléportation »: a token landing here is sent to one of the tile's teleport targets (see TileTeleport). */
    TILE_TELEPORT("tile_teleport", TeleportTileBehavior::new),
    /** « Étoile »: a star space, where the party's star may stand and be bought (see fr.lordfinn.steveparty.service.PartyStars). */
    TILE_STAR("tile_star", StarBoardSpaceBehavior::new),
    /** « Glandouille »: a tower of Glandouilles pushes the tokens some spaces on (see GlandouilleTileBehavior). */
    TILE_GLANDOUILLE("tile_glandouille", GlandouilleTileBehavior::new),
    /** « Frousseux »: a Frousseux steals coins or stars from another player (see FrousseuxTileBehavior). */
    TILE_FROUSSEUX("tile_frousseux", FrousseuxTileBehavior::new),
    /** « Mistigri »: the black cat of bad luck rolls his loaded die and passes a sentence (see MistigriTileBehavior). */
    TILE_MISTIGRI("tile_mistigri", MistigriTileBehavior::new),
    /** « Obstacle à seuil »: a token reaching it goes on only if its roll meets a condition (see ThresholdTileBehavior). */
    TILE_THRESHOLD("tile_threshold", ThresholdTileBehavior::new),
    /** « Pot commun »: passing tokens feed a pot, the token stopping on it wins it (see PotTileBehavior). */
    TILE_POT("tile_pot", PotTileBehavior::new),
    /** « Portail à clé »: gates on some exits, opened by a Gate Key (see KeyGateTileBehavior). */
    TILE_KEY_GATE("tile_key_gate", KeyGateTileBehavior::new),
    /** « Trichaudron »: the Trichaudron's three heads each hold a hidden prize, a slow die picks one (see TrichaudronTileBehavior). */
    TILE_TRICHAUDRON("tile_trichaudron", TrichaudronTileBehavior::new);

    private final String name;
    private final Supplier<ABoardSpaceBehavior> behavior;

    BoardSpaceType(String name, Supplier<ABoardSpaceBehavior> behavior) {
        this.name = name;
        // One behaviour per role, made when first asked (a behaviour names its role: not while the roles are made)
        this.behavior = Suppliers.memoize(behavior::get);
    }

    /** What a board space with this role does. */
    public ABoardSpaceBehavior behavior() {
        return behavior.get();
    }

    @Override
    public String asString() {
        return name;
    }
}
