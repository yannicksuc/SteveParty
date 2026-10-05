package fr.lordfinn.steveparty.powerups.effects;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads and moves the Star on a party's board. The Star sits on one of the board's Star spaces (the board spaces
 * carrying a Star cartridge that is active); only one carries it at a time. The power-ups that move the Star
 * ({@link StarWhistleEffect}) only know the board through this, so they don't depend on how the Star is stored.
 * <p>
 * An implementation gives {@link #currentStarSpace} (from {@link StarLocator}), {@link #activeStarSpaces} and
 * {@link #moveStarTo}; {@link #moveStarElsewhere} is built on them. The Star cartridge may override
 * {@link #moveStarElsewhere} with the move it already makes when the Star is bought, as long as it keeps the same
 * contract (another active Star space, never the same one).
 */
public interface StarRelocator extends StarLocator {
    /** No Star and no Star space anywhere: nothing ever moves. */
    StarRelocator NONE = new StarRelocator() {
        @Override
        public Optional<BlockPos> currentStarSpace(PartyControllerEntity party) {
            return Optional.empty();
        }

        @Override
        public List<BlockPos> activeStarSpaces(PartyControllerEntity party) {
            return List.of();
        }

        @Override
        public boolean moveStarTo(PartyControllerEntity party, BlockPos space) {
            return false;
        }
    };

    /**
     * The Star spaces of {@code party}'s board that can carry the Star right now (their Star cartridge is active),
     * including the one carrying it. Server side.
     *
     * @param party the party controller of the party being played
     * @return the positions of those board spaces, in any order, without duplicates
     */
    List<BlockPos> activeStarSpaces(PartyControllerEntity party);

    /**
     * Puts the Star on {@code space} (taking it off the space carrying it), with no announcement: the caller tells the
     * players. Server side.
     *
     * @param party the party controller of the party being played
     * @param space one of {@link #activeStarSpaces}
     * @return true if the Star now stands on {@code space}
     */
    boolean moveStarTo(PartyControllerEntity party, BlockPos space);

    /**
     * Sends the Star from the space carrying it to another active Star space, picked at random (see
     * {@link #pickOther}). Never the same space: if there is no other active Star space, or no Star at all, nothing
     * moves.
     *
     * @param party  the party controller of the party being played
     * @param random where the pick comes from
     * @return true if the Star moved to another space
     */
    default boolean moveStarElsewhere(PartyControllerEntity party, Random random) {
        Optional<BlockPos> current = currentStarSpace(party);
        if (current.isEmpty()) return false;
        Optional<BlockPos> next = pickOther(activeStarSpaces(party), current.get(), random);
        return next.isPresent() && moveStarTo(party, next.get());
    }

    /**
     * One of {@code spaces} other than {@code current}, at random (uniform).
     *
     * @param spaces  the candidate Star spaces
     * @param current the space carrying the Star, left out; null leaves nothing out
     * @param random  where the pick comes from
     * @return the picked space, or empty if no space other than {@code current} is given
     */
    static Optional<BlockPos> pickOther(List<BlockPos> spaces, @Nullable BlockPos current, Random random) {
        List<BlockPos> others = new ArrayList<>();
        for (BlockPos pos : spaces) {
            if (!pos.equals(current) && !others.contains(pos)) others.add(pos.toImmutable());
        }
        return others.isEmpty() ? Optional.empty() : Optional.of(others.get(random.nextInt(others.size())));
    }
}
