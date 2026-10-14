package fr.lordfinn.steveparty.blocks.custom.pipe;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeIndex;
import net.minecraft.block.BlockState;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * The mini-game pipe's block entity: its slot holds the mini-game page it is programmed with (only pages go
 * in). The page is sent to the clients, which show it in the pipe's notch. The server's index of the programmed pipes
 * ({@link MiniGamePipeIndex}) is told when its page changes and when it is loaded.
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
    public void setWorld(World world) {
        super.setWorld(world);
        index();
    }

    /** Tells the server's index which page it is programmed with. */
    private void index() {
        if (!(world instanceof ServerWorld server) || !server.getServer().isOnThread()) return;
        MiniGamePipeIndex.set(server.getServer(), GlobalPos.create(server.getRegistryKey(), pos),
                MiniGamePages.idOf(getPage()), MiniGamePipeBlock.reachOf(getCachedState()));
    }

    /** The page carried by the item of a mini-game pipe broken with one (empty: none). */
    public static ItemStack pageOf(ItemStack pipeItem) {
        ContainerComponent contents = pipeItem.get(DataComponentTypes.CONTAINER);
        return contents == null ? ItemStack.EMPTY : contents.stream().filter(MiniGamePages::isPage).findFirst().orElse(ItemStack.EMPTY);
    }

    /** Broken, it keeps its page: its item carries it (see its loot table), and gives it back when placed. */
    @Override
    protected void addComponents(ComponentMap.Builder builder) {
        super.addComponents(builder);
        if (!getPage().isEmpty()) builder.add(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(getPage().copy())));
    }

    @Override
    protected void readComponents(ComponentsAccess components) {
        super.readComponents(components);
        ContainerComponent contents = components.get(DataComponentTypes.CONTAINER);
        if (contents != null) contents.stream().filter(MiniGamePages::isPage).findFirst().ifPresent(page -> setStack(CARTRIDGE_SLOT, page.copyWithCount(1)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeFromCopiedStackNbt(NbtCompound nbt) {
        super.removeFromCopiedStackNbt(nbt);
        nbt.remove("Items");
    }

    /** Broken, its page stays with the pipe item: nothing is scattered. */
    @Override
    public boolean scattersContents() {
        return false;
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
