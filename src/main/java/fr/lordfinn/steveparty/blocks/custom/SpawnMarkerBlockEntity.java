package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.SyncedBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * A Spawn Marker's settings (its menu): when its mob shows ({@link #isResident}), how high above the marker it appears
 * ({@link #getLift}: the marker may be hidden under the floor or in a wall), and the board space linked to it last
 * ({@link #getOwner}, so that breaking it unlinks it). Seen by the clients (its menu, the preview, the tile's info).
 */
public class SpawnMarkerBlockEntity extends SyncedBlockEntity {
    /** The lift, in half blocks: from -4 to +8 blocks. */
    public static final int MIN_LIFT = -8, MAX_LIFT = 16;
    private boolean resident;
    /** In half blocks. */
    private int lift;
    private @Nullable BlockPos owner;

    public SpawnMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPAWN_MARKER_ENTITY, pos, state);
    }

    /**
     * Its mob shows all the party long (« always visible »: it appears on the marker when the party starts, waits
     * there, plays its show when a token lands on its space, comes back, goes when the party ends); false (the
     * default): only when a token lands on its space.
     */
    public boolean isResident() {
        return resident;
    }

    public void setResident(boolean resident) {
        if (this.resident == resident) return;
        this.resident = resident;
        changed();
    }

    /** How many blocks above the marker its mob appears (-4 to +8, by half blocks); it floats there (a hologram). */
    public double getLift() {
        return lift / 2.0;
    }

    /** The lift in half blocks. */
    public int getLiftSteps() {
        return lift;
    }

    /** Sets the lift, in half blocks (clamped to {@link #MIN_LIFT}..{@link #MAX_LIFT}). */
    public void setLiftSteps(int steps) {
        int clamped = MathHelper.clamp(steps, MIN_LIFT, MAX_LIFT);
        if (clamped == lift) return;
        lift = clamped;
        changed();
    }

    /** The board space whose cartridge links it (the last one linked to it), null for none known. */
    public @Nullable BlockPos getOwner() {
        return owner;
    }

    public void setOwner(@Nullable BlockPos owner) {
        this.owner = owner == null ? null : owner.toImmutable();
        changed();
    }

    private void changed() {
        markDirty();
        syncToClients();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (resident) nbt.putBoolean("Resident", true);
        if (lift != 0) nbt.putInt("Lift", lift);
        if (owner != null) nbt.putLong("Owner", owner.asLong());
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        resident = nbt.getBoolean("Resident");
        lift = MathHelper.clamp(nbt.getInt("Lift"), MIN_LIFT, MAX_LIFT);
        owner = nbt.contains("Owner") ? BlockPos.fromLong(nbt.getLong("Owner")) : null;
    }
}
