package fr.lordfinn.steveparty.blocks.custom.frousseux;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.SyncedBlockEntity;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxColor;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The Frousseux sleeping in a candle holder: its whole entity data (its identity: UUID, colour, name, owner, health,
 * effects...), without where it was ({@link FrousseuxCandleHolderBlock#keptData}). Seen by the clients (they draw it)
 * and kept by the block's item when broken. Empty: a candle holder that never held one (from the creative tab).
 */
public class FrousseuxCandleHolderBlockEntity extends SyncedBlockEntity {
    public static final String KEY = "Frousseux";

    private NbtCompound frousseux = new NbtCompound();
    /** The client's copy of it, drawn by the block entity renderer (kept as an Object: client-only type there). */
    public @Nullable Object clientModel;

    public FrousseuxCandleHolderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FROUSSEUX_CANDLE_HOLDER, pos, state);
    }

    public NbtCompound getFrousseux() {
        return frousseux;
    }

    public void setFrousseux(NbtCompound frousseux) {
        this.frousseux = frousseux.copy();
        this.clientModel = null;
        markDirty();
        syncToClients();
    }

    public FrousseuxColor getColor() {
        return colorOf(frousseux);
    }

    public static FrousseuxColor colorOf(NbtCompound frousseux) {
        return frousseux.contains("Color") ? FrousseuxColor.byName(frousseux.getString("Color")) : FrousseuxColor.PLAIN;
    }

    /** Its flame as it was when it fell asleep: full for an empty one. */
    public static FrousseuxEntity.Flame flameOf(NbtCompound frousseux) {
        return frousseux.contains("Health")
                ? FrousseuxEntity.Flame.of(frousseux.getFloat("Health"), (float) FrousseuxEntity.MAX_HEALTH)
                : FrousseuxEntity.Flame.FULL;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (!frousseux.isEmpty()) nbt.put(KEY, frousseux.copy());
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        frousseux = nbt.getCompound(KEY).copy();
        clientModel = null;
    }
}
