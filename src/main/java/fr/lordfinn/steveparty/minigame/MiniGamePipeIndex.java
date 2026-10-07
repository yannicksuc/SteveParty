package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.blocks.custom.pipe.MiniGamePipeBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeNetworks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where the mini-game pipes programmed with each page are, and how far each sends (its tier: copper, iron, golden),
 * saved with the overworld: a pipe linked to a page finds the mini-game pipes of that page without their chunks loaded
 * ({@code MiniGamePipes}). A mini-game pipe tells it whenever its page changes, when it is loaded, and when it is
 * removed ({@code MiniGamePipeBlockEntity}, {@code MiniGamePipeBlock#onStateReplaced}).
 */
public final class MiniGamePipeIndex extends PersistentState {
    private static final String ID = "steveparty_mini_game_pipes";
    private static final Type<MiniGamePipeIndex> TYPE = new Type<>(MiniGamePipeIndex::new, MiniGamePipeIndex::fromNbt, null);

    /** A programmed mini-game pipe: its page, how far it sends. */
    public record Entry(UUID page, MiniGamePipeBlock.Reach reach) {
    }

    private final Map<GlobalPos, Entry> pipes = new LinkedHashMap<>();

    public static MiniGamePipeIndex get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, ID);
    }

    /** The mini-game pipe at {@code pos} is programmed with {@code page} (null: with nothing, or removed). */
    public static void set(MinecraftServer server, GlobalPos pos, @Nullable UUID page, MiniGamePipeBlock.Reach reach) {
        MiniGamePipeIndex index = get(server);
        Entry entry = page == null ? null : new Entry(page, reach);
        Entry before = entry == null ? index.pipes.remove(pos) : index.pipes.put(pos, entry);
        if (!java.util.Objects.equals(before, entry)) index.markDirty();
    }

    /** The mini-game pipe at {@code pos} is no more, or holds no page. */
    public static void remove(MinecraftServer server, GlobalPos pos) {
        MiniGamePipeIndex index = get(server);
        if (index.pipes.remove(pos) != null) index.markDirty();
    }

    /** What the index knows of the mini-game pipe at {@code pos}, null if it is not a programmed one. */
    public static @Nullable Entry at(MinecraftServer server, GlobalPos pos) {
        return get(server).pipes.get(pos);
    }

    /** The mini-game pipes programmed with {@code page}. */
    public static List<GlobalPos> of(MinecraftServer server, UUID page) {
        List<GlobalPos> found = new ArrayList<>();
        get(server).pipes.forEach((pos, entry) -> {
            if (entry.page().equals(page)) found.add(pos);
        });
        return found;
    }

    /**
     * @return true if a player at {@code from} is within the range of a mini-game pipe at {@code pipe} sending as far as
     * {@code reach}: copper {@value PipeNetworks#WARP_RADIUS} blocks in its dimension, iron its whole dimension,
     * golden anywhere
     */
    public static boolean inRange(MiniGamePipeBlock.Reach reach, GlobalPos pipe, GlobalPos from) {
        if (reach == MiniGamePipeBlock.Reach.EVERYWHERE) return true;
        if (!pipe.dimension().equals(from.dimension())) return false;
        return reach == MiniGamePipeBlock.Reach.DIMENSION
                || pipe.pos().getSquaredDistance(from.pos()) <= (double) PipeNetworks.WARP_RADIUS * PipeNetworks.WARP_RADIUS;
    }

    /**
     * The mini-game pipe programmed with {@code page} nearest to {@code from} among those in range of it: in its
     * dimension the closest (straight distance), else one of another dimension (a golden pipe); null for none.
     */
    public static @Nullable GlobalPos nearestInRange(MinecraftServer server, UUID page, GlobalPos from) {
        GlobalPos best = null, elsewhere = null;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<GlobalPos, Entry> pipe : get(server).pipes.entrySet()) {
            GlobalPos pos = pipe.getKey();
            if (!pipe.getValue().page().equals(page) || !inRange(pipe.getValue().reach(), pos, from)) continue;
            // Ties go to the lowest dimension then position: never to the order the pipes were placed or loaded in
            if (!pos.dimension().equals(from.dimension())) {
                if (elsewhere == null || before(pos, elsewhere)) elsewhere = pos;
                continue;
            }
            double distance = pos.pos().getSquaredDistance(from.pos());
            if (distance < bestDistance || (distance == bestDistance && best != null && pos.pos().compareTo(best.pos()) < 0)) {
                bestDistance = distance;
                best = pos;
            }
        }
        return best != null ? best : elsewhere;
    }

    private static boolean before(GlobalPos a, GlobalPos b) {
        int dimension = a.dimension().getValue().compareTo(b.dimension().getValue());
        return dimension != 0 ? dimension < 0 : a.pos().compareTo(b.pos()) < 0;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList list = new NbtList();
        pipes.forEach((pos, entry) -> {
            NbtCompound pipe = new NbtCompound();
            pipe.putString("Dimension", pos.dimension().getValue().toString());
            pipe.putLong("Pos", pos.pos().asLong());
            pipe.putUuid("Page", entry.page());
            pipe.putString("Reach", entry.reach().name());
            list.add(pipe);
        });
        nbt.put("Pipes", list);
        return nbt;
    }

    public static MiniGamePipeIndex fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        MiniGamePipeIndex index = new MiniGamePipeIndex();
        for (NbtElement element : nbt.getList("Pipes", NbtElement.COMPOUND_TYPE)) {
            NbtCompound pipe = (NbtCompound) element;
            Identifier dimension = Identifier.tryParse(pipe.getString("Dimension"));
            if (dimension == null || !pipe.containsUuid("Page")) continue;
            RegistryKey<World> world = RegistryKey.of(RegistryKeys.WORLD, dimension);
            MiniGamePipeBlock.Reach reach;
            try {
                // Saved before the tiers were: the shortest reach, until the pipe is loaded again and tells its own
                reach = pipe.contains("Reach") ? MiniGamePipeBlock.Reach.valueOf(pipe.getString("Reach")) : MiniGamePipeBlock.Reach.NEAR;
            } catch (IllegalArgumentException e) {
                reach = MiniGamePipeBlock.Reach.NEAR;
            }
            index.pipes.put(GlobalPos.create(world, BlockPos.fromLong(pipe.getLong("Pos"))), new Entry(pipe.getUuid("Page"), reach));
        }
        return index;
    }
}
