package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.board.BoardGraph;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static fr.lordfinn.steveparty.Steveparty.SCHEDULER;

/**
 * The extra moves of the Move Forward / Back tiles, and the recent path of every token (to go back the way it came).
 * <p>
 * Rules:
 * <ul>
 *     <li><b>forward N</b>: an ordinary move of N steps from the tile (destinations followed, the owner chooses at a
 *     fork, stop tiles halt, a dead end ends it);</li>
 *     <li><b>back N</b>: the token retraces the path it came by ({@link #TRAIL_LENGTH} last board spaces); beyond
 *     what it remembers, it walks the links backward (at a board space several spaces lead to, the one closest to a
 *     start tile). It stops on a start tile, or where nothing leads to;</li>
 *     <li>the spaces passed pop, the space reached in the end gives its own landing (bonus, item...);</li>
 *     <li><b>no chain</b>: a token ending an extra move on a Move Forward / Back tile does not move again (a plain
 *     landing): one landing gives at most one extra move, so two tiles sending a token to each other never loop.</li>
 * </ul>
 * The token's steps are set as soon as the move is decided (its turn goes on until it lands), the walk itself starts
 * {@link #START_DELAY_TICKS} later, once the landing jingle is heard. Server thread only, not saved (a restart in the
 * middle of an extra move ends it as an ordinary move).
 */
public final class AdvanceBackMoves {
    /** Board spaces remembered per token, to go back the way it came. */
    public static final int TRAIL_LENGTH = 32;
    /** Ticks between the landing on the tile and the start of the extra move. */
    public static final int START_DELAY_TICKS = 20;
    /** How far around the tile the links are read to walk them backward. */
    private static final int GRAPH_RADIUS = 48;
    private static final int MAX_TRACKED_TOKENS = 512;

    /** An extra move being made. */
    private static final class Move {
        final boolean backward;
        /** Back: the board spaces still to go through, in order (null forward). */
        final @Nullable Deque<BlockPos> route;
        /** Decided, not walking yet (see {@link #START_DELAY_TICKS}). */
        boolean waiting = true;
        /** The extra move of a tile (true), or the move of a roll going backward (the Reversed dice module). */
        final boolean extra;

        Move(boolean backward, @Nullable Deque<BlockPos> route, boolean extra) {
            this.backward = backward;
            this.route = route;
            this.extra = extra;
        }
    }

    private static final Map<UUID, Move> MOVES = new HashMap<>();
    /** Last board spaces of each token, the latest last. */
    private static final Map<UUID, Deque<BlockPos>> TRAILS = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Deque<BlockPos>> eldest) {
            return size() > MAX_TRACKED_TOKENS;
        }
    };

    private AdvanceBackMoves() {
    }

    // ---------------------------------------------------------------- the path of the tokens

    /** The token stands on {@code pos} (a move starts from there): remembered if it is not its last board space. */
    public static void noteAt(MobEntity token, BlockPos pos) {
        Deque<BlockPos> trail = TRAILS.computeIfAbsent(token.getUuid(), uuid -> new ArrayDeque<>());
        if (pos.equals(trail.peekLast())) return;
        trail.addLast(pos.toImmutable());
        while (trail.size() > TRAIL_LENGTH) trail.removeFirst();
    }

    /**
     * The token arrived on {@code pos}: {@code pos} is added to its path; going back the way it came, the space it
     * leaves is forgotten instead (beyond its memory, walking the links backward, where it came from is unknown: its
     * path starts again there).
     */
    static void onArrived(MobEntity token, BlockPos pos) {
        Move move = MOVES.get(token.getUuid());
        Deque<BlockPos> trail = TRAILS.get(token.getUuid());
        if (move != null && move.backward && trail != null && !trail.isEmpty()) {
            BlockPos left = trail.removeLast();
            if (pos.equals(trail.peekLast())) return;
            if (!pos.equals(left)) trail.clear();
        }
        noteAt(token, pos);
    }

    /** The board spaces the token remembers, oldest first. */
    public static List<BlockPos> trail(MobEntity token) {
        Deque<BlockPos> trail = TRAILS.get(token.getUuid());
        return trail == null ? List.of() : List.copyOf(trail);
    }

    public static void forgetTrail(UUID token) {
        TRAILS.remove(token);
    }

    // ---------------------------------------------------------------- the extra moves

    /** True while the token makes (or is about to make) the extra move of a Move Forward / Back tile. */
    public static boolean isExtraMove(MobEntity token) {
        Move move = MOVES.get(token.getUuid());
        return move != null && move.extra;
    }

    /** The extra move is decided but has not started yet: nothing else may move the token meanwhile. */
    public static boolean isWaiting(MobEntity token) {
        Move move = MOVES.get(token.getUuid());
        return move != null && move.waiting;
    }

    /** Going back: the token follows its route, not the destinations of the board spaces. */
    public static boolean isRouted(MobEntity token) {
        Move move = MOVES.get(token.getUuid());
        return move != null && move.route != null;
    }

    /** The next board space of the route going back, or null at its end. */
    static @Nullable BlockPos nextRouted(MobEntity token) {
        Move move = MOVES.get(token.getUuid());
        return move == null || move.route == null ? null : move.route.pollFirst();
    }

    /** A new move of the token (a dice roll) that is not an extra move: whatever was left of one is dropped. */
    public static void cancel(MobEntity token) {
        MOVES.remove(token.getUuid());
    }

    /** The arrival was handled (landing included): the extra move ends when the token has no step left. */
    static void afterArrival(MobEntity token) {
        Move move = MOVES.get(token.getUuid());
        if (move != null && !move.waiting && ((TokenizedEntityInterface) token).steveparty$getNbSteps() == 0)
            MOVES.remove(token.getUuid());
    }

    /**
     * Sends the token that landed on {@code from} {@code steps} spaces on (negative: back).
     *
     * @return the steps it will really walk: {@code steps} forward (a dead end or a stop tile may cut it on the way),
     * back only as far as the path goes (0: nowhere to go back, nothing happens)
     */
    public static int launch(ServerWorld world, MobEntity token, BlockPos from, int steps) {
        return launch(world, token, from, steps, true);
    }

    /**
     * Same, for a move that is not the extra move of a tile when {@code extra} is false: the move of a roll going
     * backward (the Reversed dice module). It walks back by the same rules, but it is an ordinary move: a Move
     * Forward / Back tile it ends on plays as usual.
     */
    public static int launch(ServerWorld world, MobEntity token, BlockPos from, int steps, boolean extra) {
        if (steps == 0) return 0;
        Move move;
        int walked;
        if (steps > 0) {
            move = new Move(false, null, extra);
            walked = steps;
        } else {
            Route route = planBack(world, token, from, -steps);
            if (route.steps() == 0) return 0;
            move = new Move(true, new ArrayDeque<>(route.spaces()), extra);
            walked = route.steps();
        }
        UUID uuid = token.getUuid();
        MOVES.put(uuid, move);
        noteAt(token, from);
        TokenizedEntityInterface tokenized = (TokenizedEntityInterface) token;
        tokenized.steveparty$setNbSteps(walked); // the turn goes on until it lands (see BoardSpaceBlockEntity)
        // Under the token's UUID: the turn sees the token as moving (TokenTurnPartyStep#isTokenMoving)
        SCHEDULER.schedule(uuid, START_DELAY_TICKS, () -> {
            if (MOVES.get(uuid) != move) return;
            move.waiting = false;
            if (token.isRemoved() || !tokenized.steveparty$isTokenized()) {
                MOVES.remove(uuid);
                tokenized.steveparty$setNbSteps(0);
                return;
            }
            TokenMovementService.moveEntityOnBoard(token, walked);
        });
        return walked;
    }

    // ---------------------------------------------------------------- going back

    /** The board spaces to go through going back (the last one is where it stops), and how many steps they take. */
    public record Route(List<BlockPos> spaces, int steps) {
    }

    /** The way back from {@code from}, at most {@code steps} steps (see the rules above). */
    public static Route planBack(ServerWorld world, MobEntity token, BlockPos from, int steps) {
        List<BlockPos> spaces = new ArrayList<>();
        int counted = 0;
        BlockPos at = from;
        boolean done = false;
        Set<BlockPos> visited = new HashSet<>();
        visited.add(from);

        // 1. The way it came
        List<BlockPos> trail = trail(token);
        int i = trail.size() - 1;
        if (i >= 0 && trail.get(i).equals(from)) {
            for (i--; i >= 0 && counted < steps; i--) {
                BlockPos previous = trail.get(i);
                if (!isBoardSpace(world, previous)) break; // gone: walk the links from there
                spaces.add(previous);
                visited.add(previous);
                at = previous;
                if (countsAsStep(world, previous)) counted++;
                if (isStart(world, previous)) {
                    done = true;
                    break;
                }
            }
        }

        // 2. Beyond its memory: the links, backward
        if (!done && counted < steps) {
            BoardGraph graph = BoardGraph.collect(world, at, GRAPH_RADIUS);
            for (int guard = 0; counted < steps && guard < 64; guard++) {
                BlockPos previous = predecessor(graph, at, visited);
                if (previous == null) break;
                spaces.add(previous);
                visited.add(previous);
                at = previous;
                if (countsAsStep(world, previous)) counted++;
                if (isStart(world, previous)) break;
            }
        }

        // It must stop on a space that takes a step (a check point is only gone through)
        while (!spaces.isEmpty() && !countsAsStep(world, spaces.getLast())) spaces.removeLast();
        return new Route(List.copyOf(spaces), counted);
    }

    /**
     * A board space whose active cartridge leads to {@code pos}, not visited yet: the one closest to a start tile
     * (going back leads toward the start), else the first found.
     */
    private static @Nullable BlockPos predecessor(BoardGraph graph, BlockPos pos, Set<BlockPos> visited) {
        BlockPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (BoardGraph.Node node : graph.nodes()) {
            if (visited.contains(node.pos())) continue;
            boolean leads = node.edges().stream().anyMatch(edge -> edge.active() && edge.to().equals(pos)
                    && edge.target() == BoardGraph.Target.BOARD_SPACE);
            if (!leads) continue;
            Integer distance = graph.distance(node.pos());
            int d = distance == null ? Integer.MAX_VALUE - 1 : distance;
            if (best == null || d < bestDistance) {
                best = node.pos();
                bestDistance = d;
            }
        }
        return best;
    }

    private static boolean isBoardSpace(World world, BlockPos pos) {
        return world.isChunkLoaded(pos) && world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity;
    }

    private static boolean countsAsStep(World world, BlockPos pos) {
        return ABoardSpaceBlock.countsAsStep(world.getBlockState(pos).getBlock());
    }

    private static boolean isStart(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof ABoardSpaceBlock && state.get(ABoardSpaceBlock.TILE_TYPE) == BoardSpaceType.TILE_START;
    }
}
