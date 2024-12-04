package fr.lordfinn.steveparty.blocks;

import fr.lordfinn.steveparty.screens.TileScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static fr.lordfinn.steveparty.items.ModItems.TILE_BEHAVIOR;

public class TileEntity extends BlockEntity implements NamedScreenHandlerFactory, TileInventory {
    private UUID uniqueId;
    private List<BlockPos> ingoingTiles = new ArrayList<>();
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(16, ItemStack.EMPTY);


    public TileEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TILE_ENTITY, pos, state);
        this.uniqueId = UUID.randomUUID(); // Generate a unique ID when the TileEntity is created
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        Inventories.writeNbt(nbt, items, wrapper);
        super.writeNbt(nbt, wrapper);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        Inventories.readNbt(nbt, items, wrapper);
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
        if (!stack.isEmpty() && stack.getItem() == TILE_BEHAVIOR) {
            TileInventory.super.setStack(slot, stack);
        }
    }

    @Override
    public void clear() {
        TileInventory.super.clear();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return TileInventory.super.canPlayerUse(player);
    }

    @Override
    public Text getDisplayName() {
        return Text.of("Tile");//Text.translatable(getCachedState().getBlock().getTranslationKey());
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new TileScreenHandler(syncId, playerInventory, this);
    }
}