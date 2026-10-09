package fr.lordfinn.steveparty.blocks.custom.frousseux;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.SyncedBlockEntity;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxColor;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The Frousseux sleeping in a candle holder: its whole entity data (its identity: UUID, colour, name, owner, health,
 * effects...), without where it was ({@link FrousseuxCandleHolderBlock#keptData}). Sent to the clients
 * and kept by the block's item when broken. Empty: a candle holder that never held one (from the creative tab).
 * Its colour and flame are block states too (FrousseuxCandleHolderBlock): its look and light need no block entity.
 */
public class FrousseuxCandleHolderBlockEntity extends SyncedBlockEntity {
    public static final String KEY = "Frousseux";

    private NbtCompound frousseux = new NbtCompound();

    public FrousseuxCandleHolderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FROUSSEUX_CANDLE_HOLDER, pos, state);
    }

    public NbtCompound getFrousseux() {
        return frousseux;
    }

    public void setFrousseux(NbtCompound frousseux) {
        this.frousseux = frousseux.copy();
        markDirty();
        syncToClients();
    }

    /** Its owner (who may wake it), or null for one that never had one. */
    public @Nullable UUID getOwner() {
        return frousseux.containsUuid("Owner") ? frousseux.getUuid("Owner") : null;
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
    }
}
