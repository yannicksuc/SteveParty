package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TeleportNetwork;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.TeleportCartridgeItem;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The board as a graph: the board spaces of the loaded chunks around a point and their links (every cartridge of
 * every slot: an Advanced Tile may take any of them). Read only, the same on both sides: the board view of the Wrench
 * (client) and the board check (server) share it.
 */
public final class BoardGraph {
    /** A link, from the cartridge in {@code slot} of the board space at {@code from}. */
    public record Edge(BlockPos from, BlockPos to, int slot, boolean active, Target target) {
    }

    /** What a link leads to. */
    public enum Target {
        BOARD_SPACE,
        /** A block that is not a board space (any more): a token following it stops. */
        BROKEN,
        /** In a chunk that is not loaded: unknown, assumed fine. */
        UNLOADED
    }

    /**
     * @param step          reaching it takes one step (tiles; check points don't)
     * @param start         a start tile (its active cartridge)
     * @param cartridges    number of cartridges it holds
     * @param inventoryIssue for an inventory tile: its chest is missing (null: fine or not an inventory tile)
     * @param teleportNetwork for a teleport tile (its active cartridge): its network, null for any other board space
     */
    public record Node(BlockPos pos, boolean step, boolean start, int cartridges, boolean hasStartToken,
                       @Nullable InventoryIssue inventoryIssue, List<Edge> edges, @Nullable TeleportNetwork teleportNetwork) {

        public long boardSpaceLinks() {
            return edges.stream().filter(e -> e.target() != Target.BROKEN).count();
        }

        public long activeBoardSpaceLinks() {
            return edges.stream().filter(e -> e.active() && e.target() != Target.BROKEN).count();
        }
    }

    public enum InventoryIssue { NO_CHEST, CHEST_GONE }

    /** A router and the positions it drives that are not board spaces. */
    public record Router(BlockPos pos, List<BlockPos> brokenTargets) {
    }

    private final Map<BlockPos, Node> nodes;
    private final List<Router> routers;
    private final Map<BlockPos, Integer> distances = new HashMap<>();
    private final Set<BlockPos> withIncoming = new HashSet<>();
    /** The teleport tiles of each network on each board (see {@link #teleportPartners}), in the graph's order. */
    private final Map<BlockPos, List<BlockPos>> teleportGroups = new HashMap<>();
    /** Each board space's board: one of its spaces, the same for all the spaces paths join. */
    private final Map<BlockPos, BlockPos> boards = new HashMap<>();

    private BoardGraph(Map<BlockPos, Node> nodes, List<Router> routers) {
        this.nodes = nodes;
        this.routers = routers;
        for (Node node : nodes.values()) {
            for (Edge edge : node.edges()) withIncoming.add(edge.to());
        }
        groupTeleports();
        computeDistances();
    }

    // ---------------------------------------------------------------- collecting

    /**
     * The board spaces (and routers) of the loaded chunks within {@code radius} blocks of {@code center} (a cube).
     * Never loads a chunk.
     */
    public static BoardGraph collect(World world, BlockPos center, int radius) {
        return collect(world, new BlockBox(center.getX() - radius, center.getY() - radius, center.getZ() - radius,
                center.getX() + radius, center.getY() + radius, center.getZ() + radius));
    }

    /** The board spaces (and routers) of the loaded chunks inside {@code box}. Never loads a chunk. */
    public static BoardGraph collect(World world, BlockBox box) {
        Map<BlockPos, Node> nodes = new LinkedHashMap<>();
        List<Router> routers = new ArrayList<>();
        int minX = box.getMinX(), maxX = box.getMaxX();
        int minY = box.getMinY(), maxY = box.getMaxY();
        int minZ = box.getMinZ(), maxZ = box.getMaxZ();
        List<BlockEntity> found = new ArrayList<>();
        for (int chunkX = ChunkSectionPos.getSectionCoord(minX); chunkX <= ChunkSectionPos.getSectionCoord(maxX); chunkX++) {
            for (int chunkZ = ChunkSectionPos.getSectionCoord(minZ); chunkZ <= ChunkSectionPos.getSectionCoord(maxZ); chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    BlockPos pos = blockEntity.getPos();
                    if (pos.getX() < minX || pos.getX() > maxX || pos.getY() < minY || pos.getY() > maxY
                            || pos.getZ() < minZ || pos.getZ() > maxZ) continue;
                    found.add(blockEntity);
                }
            }
        }
        // Stable order (x, then z, then y): the same report twice
        found.sort(Comparator.comparingInt((BlockEntity b) -> b.getPos().getX()).thenComparingInt(b -> b.getPos().getZ())
                .thenComparingInt(b -> b.getPos().getY()));
        for (BlockEntity blockEntity : found) {
            if (blockEntity instanceof BoardSpaceBlockEntity boardSpace && boardSpace.getCachedState().getBlock() instanceof ABoardSpaceBlock) {
                Node node = node(world, boardSpace);
                nodes.put(node.pos(), node);
            } else if (blockEntity instanceof BoardSpaceRedstoneRouterBlockEntity router) {
                List<BlockPos> broken = new ArrayList<>();
                for (BlockPos target : BoardLinks.links(router, 0)) {
                    if (target(world, target) == Target.BROKEN) broken.add(target);
                }
                routers.add(new Router(router.getPos().toImmutable(), broken));
            }
        }
        return new BoardGraph(nodes, routers);
    }

    public static BoardGraph of(World world, Collection<BoardSpaceBlockEntity> boardSpaces) {
        Map<BlockPos, Node> nodes = new LinkedHashMap<>();
        for (BoardSpaceBlockEntity boardSpace : boardSpaces) {
            Node node = node(world, boardSpace);
            nodes.put(node.pos(), node);
        }
        return new BoardGraph(nodes, List.of());
    }

    private static Node node(World world, BoardSpaceBlockEntity boardSpace) {
        BlockPos pos = boardSpace.getPos().toImmutable();
        BlockState state = boardSpace.getCachedState();
        boolean start = state.contains(ABoardSpaceBlock.TILE_TYPE) && state.get(ABoardSpaceBlock.TILE_TYPE) == BoardSpaceType.TILE_START;
        int active = boardSpace.getActiveSlot();
        List<Edge> edges = new ArrayList<>();
        int cartridges = 0;
        for (int slot = 0; slot < boardSpace.size(); slot++) {
            ItemStack cartridge = boardSpace.getStack(slot);
            if (!(cartridge.getItem() instanceof CartridgeItem)) continue;
            cartridges++;
            for (BlockPos to : BoardLinks.links(cartridge)) {
                edges.add(new Edge(pos, to.toImmutable(), slot, slot == active, target(world, to)));
            }
        }
        ItemStack activeCartridge = boardSpace.getStack(active);
        boolean hasToken = start && activeCartridge.get(ModComponents.TB_START_BOUND_ENTITY) != null;
        TeleportNetwork network = activeCartridge.getItem() instanceof TeleportCartridgeItem
                ? TeleportCartridgeItem.settings(activeCartridge).network() : null;
        return new Node(pos, ABoardSpaceBlock.countsAsStep(state.getBlock()), start, cartridges, hasToken,
                inventoryIssue(world, activeCartridge), edges, network);
    }

    private static @Nullable InventoryIssue inventoryIssue(World world, ItemStack cartridge) {
        if (!CartridgeContainers.linksContainers(cartridge)) return null;
        List<BlockPos> chests = CartridgeContainers.in(cartridge, world);
        if (CartridgeContainers.isEmpty(cartridge)) return InventoryIssue.NO_CHEST;
        // One of its containers gone (an unloaded one is not looked at)
        for (BlockPos chest : chests) {
            if (world.isChunkLoaded(chest) && !(world.getBlockEntity(chest) instanceof Inventory)) return InventoryIssue.CHEST_GONE;
        }
        return null;
    }

    @SuppressWarnings("deprecation") // isChunkLoaded(BlockPos): the chunk must not be loaded for this
    private static Target target(World world, BlockPos pos) {
        if (!world.isChunkLoaded(pos)) return Target.UNLOADED;
        return BoardLinks.isBoardSpace(world, pos) ? Target.BOARD_SPACE : Target.BROKEN;
    }

    // ---------------------------------------------------------------- teleport networks

    /**
     * Groups the teleport tiles by network and by board: two tiles are on the same board when paths join them (links
     * of any cartridge, whichever way), so two boards side by side never send tokens to each other.
     */
    private void groupTeleports() {
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        for (Node node : nodes.values()) {
            for (Edge edge : node.edges()) {
                if (nodes.containsKey(edge.to())) union(parent, node.pos(), edge.to());
            }
        }
        Map<BlockPos, Map<TeleportNetwork, List<BlockPos>>> networks = new HashMap<>();
        for (Node node : nodes.values()) {
            BlockPos board = find(parent, node.pos());
            boards.put(node.pos(), board);
            if (node.teleportNetwork() == null) continue;
            List<BlockPos> group = networks.computeIfAbsent(board, b -> new EnumMap<>(TeleportNetwork.class))
                    .computeIfAbsent(node.teleportNetwork(), network -> new ArrayList<>());
            group.add(node.pos());
            teleportGroups.put(node.pos(), group);
        }
    }

    private static BlockPos find(Map<BlockPos, BlockPos> parent, BlockPos pos) {
        BlockPos root = pos;
        for (BlockPos up = parent.get(root); up != null; up = parent.get(root)) root = up;
        // Path compression
        for (BlockPos at = pos, up = parent.get(at); up != null && !up.equals(root); at = up, up = parent.get(at)) parent.put(at, root);
        return root;
    }

    private static void union(Map<BlockPos, BlockPos> parent, BlockPos a, BlockPos b) {
        BlockPos rootA = find(parent, a), rootB = find(parent, b);
        if (!rootA.equals(rootB)) parent.put(rootA, rootB);
    }

    /**
     * The other teleport tiles of {@code pos}'s network on its board (loaded, in the graph's stable order): where a
     * token landing on it may be sent. Empty for a tile alone in its network, or no teleport tile.
     */
    public List<BlockPos> teleportPartners(BlockPos pos) {
        List<BlockPos> group = teleportGroups.get(pos);
        if (group == null || group.size() < 2) return List.of();
        List<BlockPos> partners = new ArrayList<>(group);
        partners.remove(pos);
        return partners;
    }

    /** All the teleport tiles of {@code pos}'s network on its board, itself included (empty if it is none). */
    public List<BlockPos> teleportNetworkOf(BlockPos pos) {
        List<BlockPos> group = teleportGroups.get(pos);
        return group == null ? List.of() : group;
    }

    /** A teleport tile no other tile of its network shares its board with: a token landing there stays. */
    public boolean isTeleportAlone(Node node) {
        return node.teleportNetwork() != null && teleportPartners(node.pos()).isEmpty();
    }

    // ---------------------------------------------------------------- distances

    /** Steps from the nearest start tile (0-1 BFS: reaching a check point takes no step). */
    private void computeDistances() {
        Deque<BlockPos> queue = new ArrayDeque<>();
        for (Node node : nodes.values()) {
            if (node.start()) {
                distances.put(node.pos(), 0);
                queue.add(node.pos());
            }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.pollFirst();
            int distance = distances.get(pos);
            Node node = nodes.get(pos);
            if (node == null) continue;
            for (Edge edge : node.edges()) {
                Node next = nodes.get(edge.to());
                if (next == null) continue;
                int d = distance + (next.step() ? 1 : 0);
                Integer known = distances.get(next.pos());
                if (known != null && known <= d) continue;
                distances.put(next.pos(), d);
                if (next.step()) queue.addLast(next.pos());
                else queue.addFirst(next.pos());
            }
            // A token landing on a teleport tile is sent to another one of its network: reached as soon as the tile is
            if (node.teleportNetwork() != null) {
                for (BlockPos to : teleportPartners(node.pos())) {
                    Node next = nodes.get(to);
                    if (next == null) continue;
                    Integer known = distances.get(next.pos());
                    if (known != null && known <= distance) continue;
                    distances.put(next.pos(), distance);
                    queue.addFirst(next.pos());
                }
            }
        }
    }

    // ---------------------------------------------------------------- queries

    public Collection<Node> nodes() {
        return nodes.values();
    }

    public @Nullable Node node(BlockPos pos) {
        return nodes.get(pos);
    }

    public List<Router> routers() {
        return routers;
    }

    public boolean hasStart() {
        return nodes.values().stream().anyMatch(Node::start);
    }

    /** Steps from the nearest start, or null if no start leads there. */
    public @Nullable Integer distance(BlockPos pos) {
        return distances.get(pos);
    }

    /** No link to a board space in any of its cartridges: a token stops there. */
    public boolean isDeadEnd(Node node) {
        return node.boardSpaceLinks() == 0;
    }

    /** Several links in its active cartridge: the player chooses. */
    public boolean isFork(Node node) {
        return node.activeBoardSpaceLinks() >= 2;
    }

    /** A start exists but none leads there. */
    public boolean isUnreachable(Node node) {
        return hasStart() && !distances.containsKey(node.pos());
    }

}
