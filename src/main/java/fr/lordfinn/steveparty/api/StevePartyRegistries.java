package fr.lordfinn.steveparty.api;

import fr.lordfinn.steveparty.api.registry.IdRegistry;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;

/**
 * The registries of Steve Party's game content, keyed by {@link net.minecraft.util.Identifier}
 * ({@link IdRegistry}). Steve Party fills them with its own content during its initialization; an addon adds its own
 * from its {@link StevePartyAddon} entrypoint, under its own namespace.
 */
public final class StevePartyRegistries {
    /**
     * The roles a cartridge gives its board space, and what a board space with that role does
     * (see {@link fr.lordfinn.steveparty.api.board.BoardSpaceRoles}).
     */
    public static final IdRegistry<ABoardSpaceBehavior> BOARD_SPACE_ROLES = new IdRegistry<>("board space role");

    /** The kinds of party steps and how to rebuild a saved one (see {@link fr.lordfinn.steveparty.api.party.PartySteps}). */
    public static final IdRegistry<fr.lordfinn.steveparty.api.party.PartySteps.Loader> PARTY_STEPS = new IdRegistry<>("party step");

    /** The modules a die may carry (see {@link fr.lordfinn.steveparty.dice.DiceModules}). */
    public static final IdRegistry<fr.lordfinn.steveparty.dice.DiceModule> DICE_MODULES = new IdRegistry<>("dice module");

    /** The power-ups (see {@link fr.lordfinn.steveparty.powerups.PowerUps}). */
    public static final IdRegistry<fr.lordfinn.steveparty.powerups.PowerUp> POWER_UPS = new IdRegistry<>("power-up");

    /** The playing cards of the party program and what they put in the party (see {@link fr.lordfinn.steveparty.api.party.PartyCards}). */
    public static final IdRegistry<fr.lordfinn.steveparty.api.party.PartyCard> PARTY_CARDS = new IdRegistry<>("party card");

    private StevePartyRegistries() {
    }
}
