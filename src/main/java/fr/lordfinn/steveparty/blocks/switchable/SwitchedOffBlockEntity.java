package fr.lordfinn.steveparty.blocks.switchable;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Remembers the exact state of a switched off block, to restore it when it is switched back on. */
public class SwitchedOffBlockEntity extends BlockEntity {
    private static final String STORED_STATE_KEY = "StoredState";
    private BlockState storedState = Blocks.AIR.getDefaultState();

    public SwitchedOffBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SWITCHED_OFF_BLOCK_ENTITY, pos, state);
    }

    public BlockState getStoredState() {
        return storedState;
    }

    public void setStoredState(BlockState state) {
        this.storedState = state;
        markDirty();
        // The client needs it for pick block (middle click)
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.put(STORED_STATE_KEY, NbtHelper.fromBlockState(storedState));
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        storedState = NbtHelper.toBlockState(Registries.createEntryLookup(Registries.BLOCK), nbt.getCompound(STORED_STATE_KEY));
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
