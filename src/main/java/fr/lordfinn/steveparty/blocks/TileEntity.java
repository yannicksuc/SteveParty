package fr.lordfinn.steveparty.blocks;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public class TileEntity extends BlockEntity implements TileInventory {
    private UUID uniqueId;
    private List<BlockPos> ingoingTiles = new ArrayList<>();
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(16, ItemStack.EMPTY);


    public TileEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TILE_ENTITY, pos, state);
        this.uniqueId = UUID.randomUUID(); // Generate a unique ID when the TileEntity is created
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.writeNbt(nbt, wrapper);
        Inventories.readNbt(nbt, items, wrapper);
        nbt.putUuid("UniqueId", uniqueId);

        // Save input and output tiles
        nbt.putIntArray("IngoingTiles", ingoingTiles.stream().flatMapToInt(pos -> IntStream.of(pos.getX(), pos.getY(), pos.getZ())).toArray());
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        uniqueId = nbt.getUuid("UniqueId");

        int[] ingoingTileData = nbt.getIntArray("IngoingTiles");
        for (int i = 0; i < ingoingTileData.length; i += 3) {
            ingoingTiles.add(new BlockPos(ingoingTileData[i], ingoingTileData[i + 1], ingoingTileData[i + 2]));
        }
        Inventories.writeNbt(nbt, items, wrapper);
    }

    @Override
    public DefaultedList<ItemStack> getItems() {
        return items;
    }

    @Override
    public int size() {
        return TileInventory.super.size();
    }

    @Override
    public boolean isEmpty() {
        return TileInventory.super.isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        return TileInventory.super.getStack(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int count) {
        return TileInventory.super.removeStack(slot, count);
    }

    @Override
    public ItemStack removeStack(int slot) {
        return TileInventory.super.removeStack(slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        TileInventory.super.setStack(slot, stack);
    }

    @Override
    public void clear() {
        TileInventory.super.clear();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return TileInventory.super.canPlayerUse(player);
    }
}