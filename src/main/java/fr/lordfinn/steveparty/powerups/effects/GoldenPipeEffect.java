package fr.lordfinn.steveparty.powerups.effects;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileTeleport;
import fr.lordfinn.steveparty.board.BoardGraph;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.service.AdvanceBackMoves;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The Golden Pipe power-up (item {@code powerup_golden_pipe}): used at the start of its owner's turn, before the roll,
 * it warps the pawn to the board space just before the one carrying the Star, so the roll that follows can reach the
 * Star and buy it.
 * <ul>
 *     <li>The space before the Star: a board space whose active cartridge leads to the Star's space (through check
 *     points, which take no step). Several: the one closest to a start tile, ties at random. None through an active
 *     cartridge: the links of the other cartridges are tried. Still none: the pawn is put on the Star's space itself.
 *     See {@link #spaceBefore}.</li>
 *     <li>The warp is the Teleport tile's ({@link TileTeleport#teleport}: shrinking swirl, sounds, pop), in gold.</li>
 *     <li>No Star on the board (see {@link StarLocator}): nothing happens, the power-up is not used up and its user is
 *     told why. Same when the pawn already stands just before the Star or is already warping.</li>
 *     <li>The party is told « X takes the Golden Pipe ».</li>
 * </ul>
 * Server side only. The caller (the power-up item) consumes the item when {@link Result#consumed()} is true and lets
 * the turn go on (the roll) once {@code onArrived} has run.
 */
public final class GoldenPipeEffect {
    /** Gold, the colour of the warp. */
    public static final int COLOR = 0xFFC83D;
    /** How far around the Star the board is read (the same reach as the Teleport tiles' networks). */
    public static final int BOARD_RANGE = TileTeleport.NETWORK_RANGE;

    public static final String USED_KEY = "message.steveparty.powerup.golden_pipe.used";
    public static final String NO_STAR_KEY = "message.steveparty.powerup.golden_pipe.no_star";
    public static final String ALREADY_THERE_KEY = "message.steveparty.powerup.golden_pipe.already_there";

    /** What using the power-up did. */
    public enum Outcome {
        /** The pawn is warping to {@link Result#destination()}: the power-up is used up. */
        TELEPORTED(true),
        /** The board has no Star: nothing happened. */
        NO_STAR(false),
        /** The pawn already stands just before the Star (or on it, with no space before): nothing happened. */
        ALREADY_THERE(false),
        /** The pawn is already being warped (or isn't in a world any more): nothing happened. */
        BUSY(false);

        private final boolean consumed;

        Outcome(boolean consumed) {
            this.consumed = consumed;
        }
    }

    /**
     * @param outcome     what happened
     * @param destination where the pawn is sent (TELEPORTED, ALREADY_THERE), else null
     */
    public record Result(Outcome outcome, @Nullable BlockPos destination) {
        /** True when the power-up has done its effect and must be used up. */
        public boolean consumed() {
            return outcome.consumed;
        }
    }

    private final StarLocator starLocator;

    /** @param starLocator where the Star is (the Star cartridge's, or a test one) */
    public GoldenPipeEffect(StarLocator starLocator) {
        this.starLocator = Objects.requireNonNull(starLocator);
    }

    /**
     * Uses the Golden Pipe for {@code token}, in {@code party}.
     *
     * @param party     the party being played (where the Star is looked for, who is told)
     * @param token     the pawn of the player whose turn it is
     * @param user      the player using it (named in the announcement, told when nothing happens), or null
     * @param onArrived runs once the pawn stands on its new space (only when the result is {@link Outcome#TELEPORTED})
     */
    public Result use(PartyControllerEntity party, MobEntity token, @Nullable ServerPlayerEntity user, Runnable onArrived) {
        if (!(token.getWorld() instanceof ServerWorld world) || token.isRemoved() || TileTeleport.isTeleporting(token)) {
            return new Result(Outcome.BUSY, null);
        }
        Optional<BlockPos> star = starLocator.currentStarSpace(party);
        if (star.isEmpty() || !(world.getBlockEntity(BoardSpaces.resolve(world, star.get())) instanceof BoardSpaceBlockEntity)) {
            PowerUpStar.tell(party, user, Text.translatable(NO_STAR_KEY).formatted(Formatting.RED));
            return new Result(Outcome.NO_STAR, null);
        }
        BlockPos starPos = BoardSpaces.resolve(world, star.get()).toImmutable();
        BoardGraph graph = BoardGraph.collect(world, starPos, BOARD_RANGE);
        List<BlockPos> before = spacesBefore(graph, starPos);
        BoardSpaceBlockEntity on = BoardSpaces.boardSpaceOf(token);
        BlockPos from = on != null ? on.getPos().toImmutable() : token.getBlockPos().toImmutable();
        if (on != null && before.contains(from) || before.isEmpty() && from.equals(starPos)) {
            PowerUpStar.tell(party, user, Text.translatable(ALREADY_THERE_KEY).formatted(Formatting.YELLOW));
            return new Result(Outcome.ALREADY_THERE, from);
        }
        BlockPos destination = pick(graph, before, world.getRandom()).orElse(starPos);

        Text who = user != null ? user.getDisplayName() : token.getDisplayName();
        MessageUtils.sendToPlayers(party.getPartyAudience(), Text.translatable(USED_KEY, who).formatted(Formatting.GOLD),
                MessageUtils.MessageType.CHAT);

        // No move during the warp (a die rolled meanwhile would move it from its old space)
        TokenizedEntityInterface tokenized = token instanceof TokenizedEntityInterface t ? t : null;
        boolean couldMove = tokenized != null && TokenStatus.hasStatus(tokenized.steveparty$getStatus(), TokenStatus.CAN_MOVE);
        if (couldMove) tokenized.steveparty$setStatus(TokenStatus.clearStatus(tokenized.steveparty$getStatus(), TokenStatus.CAN_MOVE));
        TileTeleport.teleport(world, token, from, destination, COLOR, () -> {
            if (couldMove) tokenized.steveparty$setStatus(TokenStatus.setStatus(tokenized.steveparty$getStatus(), TokenStatus.CAN_MOVE));
            // Its path starts again there (moving back goes the board's way, not through the pipe)
            AdvanceBackMoves.forgetTrail(token.getUuid());
            if (!token.isRemoved()) AdvanceBackMoves.noteAt(token, destination);
            onArrived.run();
        });
        return new Result(Outcome.TELEPORTED, destination);
    }

    /**
     * Where the Golden Pipe sends a pawn on {@code world}'s board for a Star on {@code star}: the space before it (see
     * {@link #spaceBefore}), else the Star's space itself.
     */
    public static BlockPos destination(World world, BlockPos star, Random random) {
        BlockPos starPos = BoardSpaces.resolve(world, star).toImmutable();
        return spaceBefore(BoardGraph.collect(world, starPos, BOARD_RANGE), starPos, random).orElse(starPos);
    }

    /**
     * The board space just before {@code star} on the board's paths: among {@link #spacesBefore}, the one closest to a
     * start tile (the way most pawns come), ties at random. Empty if no space leads to the Star.
     */
    public static Optional<BlockPos> spaceBefore(BoardGraph graph, BlockPos star, Random random) {
        return pick(graph, spacesBefore(graph, star), random);
    }

    /**
     * The board spaces from which one step leads to {@code star}: those whose active cartridge links to it, directly
     * or through check points (they take no step, so the space before them is the one before the Star). If none, the
     * same through the links of every cartridge (an Advanced Tile's other slots). Never the Star itself.
     */
    public static List<BlockPos> spacesBefore(BoardGraph graph, BlockPos star) {
        List<BlockPos> found = spacesBefore(graph, star, true);
        return found.isEmpty() ? spacesBefore(graph, star, false) : found;
    }

    private static List<BlockPos> spacesBefore(BoardGraph graph, BlockPos star, boolean activeOnly) {
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        visited.add(star);
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(star);
        while (!queue.isEmpty()) {
            BlockPos to = queue.poll();
            for (BoardGraph.Node node : graph.nodes()) {
                if (visited.contains(node.pos())) continue;
                boolean leads = node.edges().stream().anyMatch(edge -> (edge.active() || !activeOnly)
                        && edge.to().equals(to) && edge.target() == BoardGraph.Target.BOARD_SPACE);
                if (!leads) continue;
                visited.add(node.pos());
                if (node.step()) found.add(node.pos());
                else queue.add(node.pos()); // a check point: the space before it
            }
        }
        return found;
    }

    private static Optional<BlockPos> pick(BoardGraph graph, List<BlockPos> candidates, Random random) {
        int best = Integer.MAX_VALUE;
        List<BlockPos> closest = new ArrayList<>();
        for (BlockPos pos : candidates) {
            Integer distance = graph.distance(pos);
            int d = distance == null ? Integer.MAX_VALUE : distance;
            if (d < best) {
                best = d;
                closest.clear();
            }
            if (d == best) closest.add(pos);
        }
        return closest.isEmpty() ? Optional.empty() : Optional.of(closest.get(random.nextInt(closest.size())));
    }
}
