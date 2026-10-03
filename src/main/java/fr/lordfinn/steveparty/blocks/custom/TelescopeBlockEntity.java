package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.SyncedBlockEntity;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.telescope.TelescopeService;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Telescope's block entity: it is there to be drawn (tripod and tube), and knows who is looking through it, if
 * anyone. That is all the clients are told (never saved): they draw that player at the eyepiece and the tube following
 * his eyes, from the look they already know of him. What he sees in it stays his own.
 */
public class TelescopeBlockEntity extends SyncedBlockEntity {
    /** The watcher is checked this often (ticks). */
    private static final int CHECK_PERIOD = 5;
    /** Client: the watchers of the telescopes ticked since the last time it was taken. */
    private static final Map<UUID, BlockPos> SEEN = new HashMap<>();

    private @Nullable UUID watcher;

    public TelescopeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TELESCOPE_ENTITY, pos, state);
    }

    public @Nullable UUID getWatcher() {
        return watcher;
    }

    /** Server: who looks through it now (null: nobody), told to the clients that see the block. */
    public void setWatcher(@Nullable UUID watcher) {
        if (java.util.Objects.equals(this.watcher, watcher)) return;
        this.watcher = watcher;
        syncToClients();
    }

    public static void tick(World world, BlockPos pos, BlockState state, TelescopeBlockEntity telescope) {
        if (telescope.watcher == null) return;
        if (world.isClient) {
            SEEN.put(telescope.watcher, pos);
        } else if (world.getTime() % CHECK_PERIOD == 0 && world instanceof ServerWorld serverWorld
                && !TelescopeService.stillWatching(serverWorld, pos, telescope.watcher)) {
            TelescopeService.released(telescope.watcher, serverWorld, pos);
            telescope.setWatcher(null);
        }
    }

    /** Client: the watchers seen since the last call (player, telescope), into {@code out}. */
    public static void takeSeen(Map<UUID, BlockPos> out) {
        out.putAll(SEEN);
        SEEN.clear();
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        watcher = nbt.containsUuid("Watcher") ? nbt.getUuid("Watcher") : null;
    }

    /** What the clients get: the watcher (always something, so that "nobody" is read too). Nothing is saved. */
    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean("Watched", watcher != null);
        if (watcher != null) nbt.putUuid("Watcher", watcher);
        return nbt;
    }
}
