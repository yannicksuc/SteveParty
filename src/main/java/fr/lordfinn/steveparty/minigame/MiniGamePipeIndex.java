package fr.lordfinn.steveparty.minigame;

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
 * Where the mini-game pipes programmed with each page are (saved with the overworld), so that a way out of a mini-game
 * finds the nearest one without its chunk loaded. A mini-game pipe tells it whenever its page changes, when it is
 * loaded, and when it is removed ({@code MiniGamePipeBlockEntity}).
 */
public final class MiniGamePipeIndex extends PersistentState {
    private static final String ID = "steveparty_mini_game_pipes";
    private static final Type<MiniGamePipeIndex> TYPE = new Type<>(MiniGamePipeIndex::new, MiniGamePipeIndex::fromNbt, null);

    private final Map<GlobalPos, UUID> pipes = new LinkedHashMap<>();

    public static MiniGamePipeIndex get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, ID);
    }

    /** The mini-game pipe at {@code pos} is programmed with {@code page} (null: with nothing, or removed). */
    public static void set(MinecraftServer server, GlobalPos pos, @Nullable UUID page) {
        MiniGamePipeIndex index = get(server);
        UUID before = page == null ? index.pipes.remove(pos) : index.pipes.put(pos, page);
        if (!java.util.Objects.equals(before, page)) index.markDirty();
    }

    /** The mini-game pipes programmed with {@code page}. */
    public static List<GlobalPos> of(MinecraftServer server, UUID page) {
        List<GlobalPos> found = new ArrayList<>();
        get(server).pipes.forEach((pos, id) -> {
            if (id.equals(page)) found.add(pos);
        });
        return found;
    }

    /**
     * The mini-game pipe programmed with {@code page} nearest to {@code from}: in the same dimension the closest (straight
     * distance), else none; null when there is none.
     */
    public static @Nullable GlobalPos nearest(MinecraftServer server, UUID page, GlobalPos from) {
        GlobalPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (GlobalPos pos : of(server, page)) {
            if (!pos.dimension().equals(from.dimension())) continue;
            double distance = pos.pos().getSquaredDistance(from.pos());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        return best;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList list = new NbtList();
        pipes.forEach((pos, page) -> {
            NbtCompound pipe = new NbtCompound();
            pipe.putString("Dimension", pos.dimension().getValue().toString());
            pipe.putLong("Pos", pos.pos().asLong());
            pipe.putUuid("Page", page);
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
            index.pipes.put(GlobalPos.create(world, BlockPos.fromLong(pipe.getLong("Pos"))), pipe.getUuid("Page"));
        }
        return index;
    }
}
