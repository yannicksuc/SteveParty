package fr.lordfinn.steveparty.podium;

import fr.lordfinn.steveparty.blocks.custom.PodiumBlock;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlockEntity;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePodiumLink;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A group of podiums, as it is now (worked out when something happens, never kept): the podium columns touching each
 * other, plus, when one of them is linked to a mini-game page, every column linked to that page (however far) and the
 * columns touching those.
 * <p>
 * <b>The places come from the heights</b>: the tallest columns are the 1st place, the next height the 2nd place, and
 * so on. Columns of the same height share the place (several winners). The height of a column is the height of its top
 * above its own foot, so that a group spread over uneven ground is ranked like a podium built on flat ground.
 */
public final class PodiumGroup {
    /** The most columns a group can have (the others are left out). */
    public static final int MAX_COLUMNS = 64;

    /**
     * A podium column.
     *
     * @param bottom its bottom block (it keeps the column's settings)
     * @param height of its top above its foot, in half blocks (a slab alone: 1)
     */
    public record Column(ServerWorld world, BlockPos bottom, BlockPos top, int height) {
        public @Nullable PodiumBlockEntity master() {
            return world.getBlockEntity(bottom) instanceof PodiumBlockEntity podium ? podium : null;
        }

        public @Nullable PodiumOccupant occupant() {
            PodiumBlockEntity master = master();
            return master == null ? null : master.getOccupant();
        }

        public GlobalPos id() {
            return GlobalPos.create(world.getRegistryKey(), bottom);
        }

        /** The middle of the surface players stand on. */
        public Vec3d standPos() {
            return new Vec3d(bottom.getX() + 0.5, bottom.getY() + height / 2.0, bottom.getZ() + 0.5);
        }

        /** @return true if {@code pos} is a block of this column. */
        public boolean contains(ServerWorld inWorld, BlockPos pos) {
            return inWorld == world && pos.getX() == bottom.getX() && pos.getZ() == bottom.getZ()
                    && pos.getY() >= bottom.getY() && pos.getY() <= top.getY();
        }
    }

    /** The columns, the best place first (then by position, so that the order never changes by itself). */
    private final List<Column> columns;
    private final Map<Column, Integer> places = new LinkedHashMap<>();
    private final Set<UUID> pages;

    private PodiumGroup(List<Column> found, Set<UUID> pages) {
        List<Column> sorted = new ArrayList<>(found);
        sorted.sort(Comparator.comparingInt((Column column) -> -column.height())
                .thenComparingInt(column -> column.bottom().getX())
                .thenComparingInt(column -> column.bottom().getZ())
                .thenComparingInt(column -> column.bottom().getY()));
        this.columns = Collections.unmodifiableList(sorted);
        int place = 0, height = Integer.MAX_VALUE;
        for (Column column : sorted) {
            if (column.height() != height) {
                place++;
                height = column.height();
            }
            places.put(column, place);
        }
        this.pages = Collections.unmodifiableSet(pages);
    }

    // ------------------------------------------------------------------ finding it

    private static boolean isLoaded(ServerWorld world, BlockPos pos) {
        return world.isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()));
    }

    private static @Nullable Column find(ServerWorld world, BlockPos pos) {
        if (!isLoaded(world, pos) || !PodiumBlock.isPodium(world.getBlockState(pos))) return null;
        BlockPos bottom = PodiumBlock.bottomOf(world, pos);
        return new Column(world, bottom, PodiumBlock.topOf(world, pos), PodiumBlock.heightOf(world, pos));
    }

    /** The group of the podium at {@code pos}; an empty group if there is no podium there. */
    public static PodiumGroup of(ServerWorld world, BlockPos pos) {
        MinecraftServer server = world.getServer();
        Map<GlobalPos, Column> found = new LinkedHashMap<>();
        Set<UUID> pages = new LinkedHashSet<>();
        ArrayDeque<Column> queue = new ArrayDeque<>();
        Column first = find(world, pos);
        if (first != null) {
            found.put(first.id(), first);
            queue.add(first);
        }
        while (!queue.isEmpty()) {
            Column column = queue.poll();
            ServerWorld in = column.world();
            for (BlockPos block = column.bottom(); block.getY() <= column.top().getY(); block = block.up()) {
                // The columns touching it
                for (Direction direction : Direction.Type.HORIZONTAL) add(find(in, block.offset(direction)), found, queue);
                // The columns of the pages it is linked to
                for (MiniGamePageData page : MiniGamePages.pagesAt(server, GlobalPos.create(in.getRegistryKey(), block))) {
                    if (!pages.add(page.id())) continue;
                    for (MiniGamePodiumLink link : page.podiumLinks()) {
                        if (link.kind() != MiniGamePodiumLink.Kind.PODIUM) continue;
                        ServerWorld there = server.getWorld(link.pos().dimension());
                        if (there != null) add(find(there, link.pos().pos()), found, queue);
                    }
                }
            }
        }
        return new PodiumGroup(new ArrayList<>(found.values()), pages);
    }

    private static void add(@Nullable Column column, Map<GlobalPos, Column> found, ArrayDeque<Column> queue) {
        if (column == null || found.size() >= MAX_COLUMNS || found.containsKey(column.id())) return;
        found.put(column.id(), column);
        queue.add(column);
    }

    /** The group of the podiums linked to a page, null if it has none (or none is loaded). */
    public static @Nullable PodiumGroup ofPage(MinecraftServer server, MiniGamePageData page) {
        for (MiniGamePodiumLink link : page.podiumLinks()) {
            if (link.kind() != MiniGamePodiumLink.Kind.PODIUM) continue;
            ServerWorld world = server.getWorld(link.pos().dimension());
            if (world == null || find(world, link.pos().pos()) == null) continue;
            return of(world, link.pos().pos());
        }
        return null;
    }

    // ------------------------------------------------------------------ reading it

    public List<Column> columns() {
        return columns;
    }

    public boolean isEmpty() {
        return columns.isEmpty();
    }

    /** The mini-game pages the group is linked to. */
    public Set<UUID> pages() {
        return pages;
    }

    /** The place of a column: 1 for the tallest ones, 2 for the next height... */
    public int placeOf(Column column) {
        return places.getOrDefault(column, columns.size() + 1);
    }

    /** The number of different places (heights). */
    public int placeCount() {
        return columns.isEmpty() ? 0 : places.get(columns.getLast());
    }

    /** What tells this group from another one: its first column. */
    public @Nullable GlobalPos id() {
        return columns.isEmpty() ? null : columns.getFirst().id();
    }

    public @Nullable Column columnAt(ServerWorld world, BlockPos pos) {
        for (Column column : columns) if (column.contains(world, pos)) return column;
        return null;
    }

    /** The column {@code player} (or, in a team mini-game, his team) is registered on, null for none. */
    public @Nullable Column columnOf(UUID player, int team) {
        for (Column column : columns) {
            PodiumOccupant occupant = column.occupant();
            if (occupant != null && occupant.sameSide(player, team)) return column;
        }
        return null;
    }

    /** The free column with the best place, null when every column is taken. */
    public @Nullable Column highestFree() {
        for (Column column : columns) if (column.occupant() == null) return column;
        return null;
    }

    /** @return true if every column is taken. */
    public boolean isFull() {
        return !columns.isEmpty() && highestFree() == null;
    }

    /** @return true if someone is registered on a column of the 1st place. */
    public boolean isFirstPlaceTaken() {
        for (Column column : columns) {
            if (placeOf(column) != 1) break;
            if (column.occupant() != null) return true;
        }
        return false;
    }

    /** Who is registered, with the place of their column, the best place first. */
    public Map<PodiumOccupant, Integer> placements() {
        Map<PodiumOccupant, Integer> placements = new LinkedHashMap<>();
        for (Column column : columns) {
            PodiumOccupant occupant = column.occupant();
            if (occupant != null) placements.put(occupant, placeOf(column));
        }
        return placements;
    }
}
