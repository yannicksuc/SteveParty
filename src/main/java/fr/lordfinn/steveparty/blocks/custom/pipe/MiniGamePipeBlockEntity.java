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
 * The mini-game pipe's block entity: its slot holds the mini-game page it is programmed with (only pages go
 * in). The page is sent to the clients, which show it in the pipe's notch. The server's index of the programmed pipes
 * ({@link fr.lordfinn.steveparty.minigame.MiniGamePipeIndex}) is told when its page changes and when it is loaded.
 */
public class MiniGamePipeBlockEntity extends PipeBlockEntity {
    public MiniGamePipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINIGAME_PIPE_ENTITY, pos, state);
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
    public void markDirty() {
        super.markDirty();
        index();
    }

    @Override
    public void setWorld(net.minecraft.world.World world) {
        super.setWorld(world);
        index();
    }

    /** Tells the server's index which page it is programmed with. */
    private void index() {
        if (!(world instanceof net.minecraft.server.world.ServerWorld server) || !server.getServer().isOnThread()) return;
        fr.lordfinn.steveparty.minigame.MiniGamePipeIndex.set(server.getServer(), net.minecraft.util.math.GlobalPos.create(server.getRegistryKey(), pos),
                MiniGamePages.idOf(getPage()), MiniGamePipeBlock.reachOf(getCachedState()));
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
