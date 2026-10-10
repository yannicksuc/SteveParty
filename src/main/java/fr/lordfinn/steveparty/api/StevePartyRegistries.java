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

    private StevePartyRegistries() {
    }
}
