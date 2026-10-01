package fr.lordfinn.steveparty.blocks.custom.pipe;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The pipe networks of a world (pipes joined to each other), worked out when first needed and kept until a pipe of
 * the network (or next to it) changes: only pipe blocks and their block entities (chunk loads) forget them, nothing
 * scans the world. Also the loaded pipes, for the warp search.
 */
public final class PipeNetworks {
    /** A network stops growing there (and at unloaded chunks). */
    public static final int MAX_PIPES = 4096;
    /**
     * A capped end warps for free to the nearest mouth of the same colour of another network within this distance
     * (blocks, loaded chunks); nothing warps farther.
     */
    public static final double WARP_RADIUS = 100;

    private static final Map<ServerWorld, PipeNetworks> WORLDS = new HashMap<>();

    private final ServerWorld world;
    private final Map<BlockPos, Network> networks = new HashMap<>();
    /** Every pipe block in a loaded chunk (from the block entities). */
    private final Set<BlockPos> loaded = new HashSet<>();

    /** An end of a network: a pipe block and one of its {@link PipeShape.End}s. */
    public record End(BlockPos pos, Direction dir, boolean capped) {}

    /** Pipes joined to each other, with their connections (masks) and their ends. */
    public static final class Network {
        private final Map<BlockPos, Integer> pipes;
        private final List<End> ends;

        private Network(Map<BlockPos, Integer> pipes, List<End> ends) {
            this.pipes = pipes;
            this.ends = ends;
        }

        public boolean contains(BlockPos pos) {
            return pipes.containsKey(pos);
        }

        public int size() {
            return pipes.size();
        }

        public List<End> ends() {
            return ends;
        }

        /** The pipe blocks from {@code from} to {@code to} (both included), the shortest way, or empty. */
        public List<BlockPos> path(BlockPos from, BlockPos to) {
            if (!pipes.containsKey(from) || !pipes.containsKey(to)) return List.of();
            Map<BlockPos, BlockPos> parents = new HashMap<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            parents.put(from, from);
            queue.add(from);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                if (pos.equals(to)) break;
                int mask = pipes.get(pos);
                for (Direction dir : Direction.values()) {
                    if ((mask & 1 << dir.ordinal()) == 0) continue;
                    BlockPos next = pos.offset(dir);
                    if (pipes.containsKey(next) && !parents.containsKey(next)) {
                        parents.put(next, pos);
                        queue.add(next);
                    }
                }
            }
            if (!parents.containsKey(to)) return List.of();
            List<BlockPos> path = new ArrayList<>();
            for (BlockPos pos = to; ; pos = parents.get(pos)) {
                path.add(pos);
                if (pos.equals(from)) break;
            }
            Collections.reverse(path);
            return path;
        }
    }

    private PipeNetworks(ServerWorld world) {
        this.world = world;
    }

    public static PipeNetworks of(ServerWorld world) {
        return WORLDS.computeIfAbsent(world, PipeNetworks::new);
    }

    public static void initialize() {
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof PipeBlockEntity) {
                PipeNetworks networks = of(world);
                networks.loaded.add(blockEntity.getPos().toImmutable());
                networks.forget(blockEntity.getPos());
            }
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof PipeBlockEntity) {
                PipeNetworks networks = of(world);
                networks.loaded.remove(blockEntity.getPos());
                networks.forget(blockEntity.getPos());
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WORLDS.clear());
    }

    /** A pipe at {@code pos} changed (or appeared, or went): its network and its neighbours' are worked out again. */
    public static void changed(ServerWorld world, BlockPos pos) {
        of(world).forget(pos);
    }

    private void forget(BlockPos pos) {
        forgetOne(pos);
        for (Direction dir : Direction.values()) forgetOne(pos.offset(dir));
    }

    private void forgetOne(BlockPos pos) {
        Network network = networks.get(pos);
        if (network != null) network.pipes.keySet().forEach(networks::remove);
    }

    /** The network of the pipe at {@code pos}, or null if there is no pipe there. */
    public @Nullable Network network(BlockPos pos) {
        Network network = networks.get(pos);
        if (network != null) return network;
        if (!isLoaded(pos) || !(world.getBlockState(pos).getBlock() instanceof PipeBlock)) return null;
        Map<BlockPos, Integer> pipes = new LinkedHashMap<>();
        List<End> ends = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(pos.toImmutable());
        pipes.put(pos.toImmutable(), 0);
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            BlockState state = world.getBlockState(at);
            int mask = 0;
            for (Direction dir : Direction.values()) {
                if (!state.get(PipeShape.connection(dir))) continue;
                BlockPos next = at.offset(dir);
                if (!isLoaded(next)) continue;
                BlockState other = world.getBlockState(next);
                if (!(other.getBlock() instanceof PipeBlock) || !other.get(PipeShape.connection(dir.getOpposite()))) continue;
                mask |= 1 << dir.ordinal();
                if (!pipes.containsKey(next) && pipes.size() < MAX_PIPES) {
                    pipes.put(next, 0);
                    queue.add(next);
                }
            }
            pipes.put(at, mask);
            for (PipeShape.End end : PipeShape.ends(state)) ends.add(new End(at, end.dir(), end.capped()));
        }
        network = new Network(pipes, List.copyOf(ends));
        for (BlockPos at : pipes.keySet()) networks.put(at, network);
        return network;
    }

    /**
     * The warp from a capped end: the nearest mouth (open end) of a pipe of the colour of the capped end's block
     * {@code from} ({@link PipeBlock#warpColor}: the last block reached, not the mouth gone in) in another network, within {@code radius} blocks of {@code from} (between block centres), among the loaded
     * pipes; ties go to the lowest position.
     */
    public @Nullable End nearestMouth(BlockPos from, Network own, double radius) {
        String color = PipeBlock.warpColor(world.getBlockState(from));
        End best = null;
        double bestDistance = radius >= 1.0E9 ? Double.MAX_VALUE : radius * radius;
        for (BlockPos pos : loaded) {
            double distance = pos.getSquaredDistance(from);
            if (distance > bestDistance || own.contains(pos)) continue;
            BlockState state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof PipeBlock pipe) || !pipe.warpColor().equals(color)) continue;
            for (PipeShape.End end : PipeShape.ends(state)) {
                if (end.capped()) continue;
                if (best == null || distance < bestDistance || (distance == bestDistance && pos.compareTo(best.pos()) < 0)) {
                    best = new End(pos, end.dir(), false);
                    bestDistance = distance;
                }
                break;
            }
        }
        return best;
    }

    private boolean isLoaded(BlockPos pos) {
        return world.getChunkManager().isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()));
    }
}
