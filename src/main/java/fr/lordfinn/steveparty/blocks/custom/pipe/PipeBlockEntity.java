package fr.lordfinn.steveparty.blocks.custom.pipe;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.ImplementedInventory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * A pipe's block entity: the slot for a cartridge in a mouth or a capped end (none can be put in yet). The slot is an
 * {@link net.minecraft.inventory.Inventory} slot so that {@code CartridgeMenus.openForContainer(player, pos,
 * CARTRIDGE_SLOT)} opens the menu of the cartridge sitting there; hoppers never reach it. The pipe block also registers
 * its position for the warp search ({@link PipeNetworks}) while its chunk is loaded.
 */
public class PipeBlockEntity extends BlockEntity implements ImplementedInventory, SidedInventory {
    public static final int CARTRIDGE_SLOT = 0;
    private static final int[] NO_SLOTS = new int[0];

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(1, ItemStack.EMPTY);

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIPE_ENTITY, pos, state);
    }

    @Override
    public DefaultedList<ItemStack> getItems() {
        return items;
    }

    public ItemStack getCartridge() {
        return items.get(CARTRIDGE_SLOT);
    }

    /** How the cartridge here picks where this pipe warps to, or null (the default: the nearest other mouth of the same colour). */
    public @Nullable PipeDestinationProvider destinationProvider() {
        return PipeDestinationProvider.of(getCartridge());
    }

    // Hoppers and droppers never put anything in or take it out
    @Override
    public int[] getAvailableSlots(Direction side) {
        return NO_SLOTS;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        return false;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return false;
    }

    /** No cartridge goes in a pipe yet. */
    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return false;
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        items.clear();
        Inventories.readNbt(nbt, items, registries);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (!getCartridge().isEmpty()) Inventories.writeNbt(nbt, items, registries);
    }
}
