package fr.lordfinn.steveparty.powerups.effects;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Where the Star is on a party's board: the board space that carries it right now (the Star cartridge moves it when
 * it is bought). The power-ups that aim at the Star ({@link GoldenPipeEffect}) only know the board through this, so
 * they don't depend on how the Star is stored.
 */
@FunctionalInterface
public interface StarLocator {
    /** No Star anywhere: the effects aiming at it do nothing. */
    StarLocator NONE = party -> Optional.empty();

    /**
     * The board space carrying the Star on {@code party}'s board, server side.
     *
     * @param party the party controller of the party being played
     * @return the position of that board space (any of its blocks for a large tile), or empty if the board has no Star
     */
    Optional<BlockPos> currentStarSpace(PartyControllerEntity party);
}
