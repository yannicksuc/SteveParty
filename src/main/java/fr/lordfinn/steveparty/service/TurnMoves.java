package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.utils.ServerMemory;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The roll that moves each token, kept until its next roll: what the board spaces it reaches during that move read (a
 * Threshold obstacle tests it, a Key gate replays it from where the move started). Also the board spaces that ended a
 * move early ({@link #halt}): a check point that halts a token is a landing there, like a Stop space.
 * Server thread only, not saved (a restart forgets the roll: the obstacles let the token go).
 */
public final class TurnMoves {
    /**
     * A roll.
     *
     * @param total  the steps it gives, modules and power-ups included (what the token walks)
     * @param faces  the values of the number faces of the dice thrown together (one per die)
     * @param origin the board space the move started from, null if unknown
     */
    public record Roll(int total, List<Integer> faces, @Nullable BlockPos origin) {
        /** At least {@code count} dice of the throw show the same number (a pair: 2, three of a kind: 3). */
        public boolean hasSame(int count) {
            Map<Integer, Integer> seen = new HashMap<>();
            for (int face : faces) {
                if (seen.merge(face, 1, Integer::sum) >= count) return true;
            }
            return false;
        }
    }

    private static final Map<UUID, Roll> ROLLS = ServerMemory.forgetOnStop(new HashMap<>());
    /** Tokens whose move was ended early on the space they stand on, until they move again. */
    private static final Map<UUID, BlockPos> HALTED = ServerMemory.forgetOnStop(new HashMap<>());
    /** Tokens whose move was already replayed once (Key gate: « Cancel the move »), until their next roll. */
    private static final Set<UUID> REPLAYED = ServerMemory.forgetOnStop(new HashSet<>());

    private TurnMoves() {
    }

    /** The token rolled: its move starts from {@code origin}. */
    public static void record(MobEntity token, int total, List<Integer> faces, @Nullable BlockPos origin) {
        ROLLS.put(token.getUuid(), new Roll(total, List.copyOf(faces), origin == null ? null : origin.toImmutable()));
        REPLAYED.remove(token.getUuid());
        HALTED.remove(token.getUuid());
    }

    /** The roll moving the token (its last one), null if it has not rolled since the server started. */
    public static @Nullable Roll rollOf(MobEntity token) {
        return ROLLS.get(token.getUuid());
    }

    public static void forget(UUID token) {
        ROLLS.remove(token);
        HALTED.remove(token);
        REPLAYED.remove(token);
    }

    // ---------------------------------------------------------------- halts

    /** The move of the token ends on {@code space}: it lands there, steps left or not, check point or not. */
    public static void halt(MobEntity token, BlockPos space) {
        HALTED.put(token.getUuid(), space.toImmutable());
    }

    /** True if the token's move was ended early on {@code space}. */
    public static boolean isHaltedOn(MobEntity token, BlockPos space) {
        return space.equals(HALTED.get(token.getUuid()));
    }

    /** The token moves again: no halt holds it any more. */
    public static void release(MobEntity token) {
        HALTED.remove(token.getUuid());
    }

    // ---------------------------------------------------------------- replays

    public static boolean wasReplayed(MobEntity token) {
        return REPLAYED.contains(token.getUuid());
    }

    public static void markReplayed(MobEntity token) {
        REPLAYED.add(token.getUuid());
    }
}
