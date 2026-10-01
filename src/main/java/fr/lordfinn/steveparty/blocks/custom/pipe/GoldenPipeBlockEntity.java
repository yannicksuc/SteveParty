package fr.lordfinn.steveparty.blocks.custom.pipe;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import net.minecraft.block.BlockState;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/**
 * The Golden Mini-game Pipe's block entity: its slot holds the mini-game page it is programmed with (only pages go
 * in). The page is sent to the clients, which show it in the pipe's notch.
 */
public class GoldenPipeBlockEntity extends PipeBlockEntity {
    public GoldenPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GOLDEN_PIPE_ENTITY, pos, state);
    }

    /** The page it is programmed with, empty for none. */
    public ItemStack getPage() {
        return getCartridge();
    }

    /** Programs it with {@code page} (empty: with nothing), and tells the clients. */
    public void setPage(ItemStack page) {
        setStack(CARTRIDGE_SLOT, page);
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return slot == CARTRIDGE_SLOT && MiniGamePages.isPage(stack);
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = createNbt(registries);
        // An emptied slot is told too: nothing to read would leave the clients with the page they had
        if (!nbt.contains("Items")) Inventories.writeNbt(nbt, getItems(), true, registries);
        return nbt;
    }
}
