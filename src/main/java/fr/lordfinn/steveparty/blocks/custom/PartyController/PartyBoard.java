package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.board.BoardGraph;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity.START_TILES_SEARCH_RADIUS;
import static fr.lordfinn.steveparty.components.ModComponents.TB_START_BOUND_ENTITY;

/**
 * The board of a Party Controller. Its start tiles are the ones within {@link PartyControllerEntity#START_TILES_SEARCH_RADIUS}
 * blocks of it; its board spaces are the ones the paths from them join (the links of the cartridges, whichever way:
 * see {@link BoardGraph#boardOf}), however far they go. A board standing beside it, or a lone tile, is not part of it.
 * <p>
 * Never loads a chunk. The controller remembers its board ({@link Memory}): a start tile or a space in a chunk that
 * is not loaded stays known (the board check tells the start tiles it can't see).
 */
public final class PartyBoard {
    /** The graph is collected this far around the spaces the paths reach (the spaces linking into them). */
    private static final int MARGIN = 16;
    /** A board of more spaces than this is cut there (a mistake, or a loop of links far away). */
    static final int MAX_SPACES = 4096;

    private PartyBoard() {}

    /**
     * The board as seen now.
     *
     * @param starts         its start tiles, loaded or remembered, sorted by z, then y, then x
     * @param startTokens    the tokens bound to its loaded start tiles, in their order, each with its start tile
     * @param spaces         its board spaces (start tiles included), loaded or remembered
     * @param unloadedStarts its start tiles remembered in chunks that are not loaded: their tokens can't be seen
     * @param graph          the graph of its loaded spaces (for the board check); with no start tile, the spaces
     *                       around the controller, to tell there is a board but no start
     */
    public record Snapshot(List<BlockPos> starts, Map<UUID, BlockPos> startTokens, Set<BlockPos> spaces,
                           List<BlockPos> unloadedStarts, BoardGraph graph) {
        public boolean contains(BlockPos pos) {
            return spaces.contains(pos);
        }
    }

    /** What a controller remembers of its board, saved with it. */
    public static final class Memory {
        private final Set<BlockPos> starts = new LinkedHashSet<>();
        private final Set<BlockPos> spaces = new LinkedHashSet<>();

        public Set<BlockPos> spaces() {
            return spaces;
        }

        void writeNbt(NbtCompound nbt) {
            if (starts.isEmpty() && spaces.isEmpty()) return;
            NbtCompound board = new NbtCompound();
            board.put("Starts", new NbtLongArray(starts.stream().mapToLong(BlockPos::asLong).toArray()));
            board.put("Spaces", new NbtLongArray(spaces.stream().mapToLong(BlockPos::asLong).toArray()));
            nbt.put("Board", board);
        }

        void readNbt(NbtCompound nbt) {
            starts.clear();
            spaces.clear();
            NbtCompound board = nbt.getCompound("Board");
            for (long pos : board.getLongArray("Starts")) starts.add(BlockPos.fromLong(pos));
            for (long pos : board.getLongArray("Spaces")) spaces.add(BlockPos.fromLong(pos));
        }

        void update(Snapshot snapshot) {
            starts.clear();
            starts.addAll(snapshot.starts());
            spaces.clear();
            spaces.addAll(snapshot.spaces());
        }
    }

    /**
     * The board of the controller at {@code controller}, read in the loaded chunks, completed by {@code memory}.
     *
     * @param partySpaces where the tokens of its party started and stand: on its board too (a party may have been
     *                    started from start tiles that are gone, or without any)
     */
    static Snapshot scan(ServerWorld world, BlockPos controller, Memory memory, Collection<BlockPos> partySpaces) {
        // The start tiles around the controller, and the ones it remembers in chunks that are not loaded
        List<BlockPos> loadedStarts = boardSpaces(world, controller, START_TILES_SEARCH_RADIUS, BoardSpaceType.TILE_START);
        List<BlockPos> unloadedStarts = memory.starts.stream().filter(pos -> !isLoaded(world, pos)).toList();
        List<BlockPos> starts = new ArrayList<>(loadedStarts);
        starts.addAll(unloadedStarts);
        sort(starts);
        List<BlockPos> seeds = new ArrayList<>(loadedStarts);
        for (BlockPos pos : partySpaces) if (isLoaded(world, pos) && !seeds.contains(pos)) seeds.add(pos);
        if (seeds.isEmpty()) {
            BoardGraph around = BoardGraph.collect(world, controller, START_TILES_SEARCH_RADIUS);
            return new Snapshot(starts, Map.of(), new LinkedHashSet<>(unloadedStarts), unloadedStarts, around);
        }

        // The spaces the paths from the start tiles reach, and the graph around them (the spaces linking into them)
        Set<BlockPos> reached = reach(world, seeds);
        if (reached.isEmpty()) reached.addAll(seeds);
        BoardGraph graph = BoardGraph.collect(world, box(reached, MARGIN));
        Set<BlockPos> boards = new HashSet<>();
        for (BlockPos start : seeds) {
            BlockPos board = graph.boardOf(start);
            if (board != null) boards.add(board);
        }
        Set<BlockPos> spaces = new LinkedHashSet<>();
        for (BoardGraph.Node node : graph.nodes()) {
            if (spaces.size() >= MAX_SPACES) break;
            if (boards.contains(graph.boardOf(node.pos()))) spaces.add(node.pos());
        }
        graph = graph.only(spaces);
        // The spaces remembered in chunks that are not loaded stay on the board
        for (BlockPos pos : memory.spaces) if (!isLoaded(world, pos)) spaces.add(pos);
        spaces.addAll(unloadedStarts);
        return new Snapshot(starts, startTokens(world, loadedStarts), spaces, unloadedStarts, graph);
    }

    /** The loaded board spaces the links of the cartridges (every slot) lead to from {@code from}, {@code from} included. */
    private static Set<BlockPos> reach(ServerWorld world, Collection<BlockPos> from) {
        Set<BlockPos> reached = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>(from);
        while (!queue.isEmpty() && reached.size() < MAX_SPACES) {
            BlockPos pos = queue.poll();
            if (reached.contains(pos) || !isLoaded(world, pos)
                    || !(world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity space)) continue;
            reached.add(pos.toImmutable());
            for (int slot = 0; slot < space.size(); slot++) {
                ItemStack cartridge = space.getStack(slot);
                if (cartridge.getItem() instanceof CartridgeItem) queue.addAll(BoardLinks.links(cartridge));
            }
        }
        return reached;
    }

    /** The tokens bound to the start tiles {@code starts}, in their order, each with its start tile (the first one bound to it). */
    private static Map<UUID, BlockPos> startTokens(ServerWorld world, List<BlockPos> starts) {
        Map<UUID, BlockPos> tokens = new LinkedHashMap<>();
        for (BlockPos tilePos : starts) {
            if (!(world.getBlockEntity(tilePos) instanceof BoardSpaceBlockEntity tile)) continue;
            String potentialUuid = tile.getActiveCartridgeItemStack().get(TB_START_BOUND_ENTITY);
            if (potentialUuid == null) continue;
            try {
                tokens.putIfAbsent(UUID.fromString(potentialUuid), tilePos);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return tokens;
    }

    private static BlockBox box(Collection<BlockPos> positions, int margin) {
        BlockBox box = BlockBox.encompassPositions(positions).orElseThrow();
        return box.expand(margin);
    }

    @SuppressWarnings("deprecation") // isChunkLoaded(BlockPos): never loads it
    private static boolean isLoaded(ServerWorld world, BlockPos pos) {
        return world.isChunkLoaded(pos);
    }

    private static void sort(List<BlockPos> positions) {
        positions.sort(Comparator.comparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX));
    }

    /**
     * The board spaces of the type {@code type} within {@code radius} blocks of {@code center}, in the loaded chunks
     * (none is loaded for this), sorted by z, then y, then x.
     */
    static List<BlockPos> boardSpaces(ServerWorld world, BlockPos center, int radius, BoardSpaceType type) {
        List<BlockPos> spaces = new ArrayList<>();
        int minX = center.getX() - radius, maxX = center.getX() + radius;
        int minY = center.getY() - radius, maxY = center.getY() + radius;
        int minZ = center.getZ() - radius, maxZ = center.getZ() + radius;

        for (int chunkX = ChunkSectionPos.getSectionCoord(minX); chunkX <= ChunkSectionPos.getSectionCoord(maxX); chunkX++) {
            for (int chunkZ = ChunkSectionPos.getSectionCoord(minZ); chunkZ <= ChunkSectionPos.getSectionCoord(maxZ); chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof BoardSpaceBlockEntity)) continue;
                    BlockPos pos = blockEntity.getPos();
                    if (pos.getX() < minX || pos.getX() > maxX || pos.getY() < minY || pos.getY() > maxY
                            || pos.getZ() < minZ || pos.getZ() > maxZ) continue;
                    BlockState state = chunk.getBlockState(pos);
                    if (state.getBlock() instanceof ABoardSpaceBlock && state.get(ABoardSpaceBlock.TILE_TYPE) == type) {
                        spaces.add(pos.toImmutable());
                    }
                }
            }
        }
        sort(spaces);
        return spaces;
    }

    /** The loaded board spaces of {@code snapshot} of the type {@code type}, sorted by z, then y, then x. */
    static List<BlockPos> boardSpaces(ServerWorld world, Snapshot snapshot, BoardSpaceType type) {
        List<BlockPos> spaces = new ArrayList<>();
        for (BlockPos pos : snapshot.spaces()) {
            if (!isLoaded(world, pos)) continue;
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof ABoardSpaceBlock && state.get(ABoardSpaceBlock.TILE_TYPE) == type) spaces.add(pos);
        }
        sort(spaces);
        return spaces;
    }
}
