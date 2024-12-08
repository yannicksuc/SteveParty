package fr.lordfinn.steveparty.blocks.tiles;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.tilebehaviors.TileBehavior;
import fr.lordfinn.steveparty.screens.TileScreenHandler;
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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class TileEntity extends BlockEntity implements NamedScreenHandlerFactory, TileInventory {
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(16, ItemStack.EMPTY);


    public TileEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TILE_ENTITY, pos, state);
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        Inventories.writeNbt(nbt, items, wrapper);
        super.writeNbt(nbt, wrapper);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        try {
            Inventories.readNbt(nbt, items, wrapper);
        } catch (Exception e) {
            Steveparty.LOGGER.error("Failed to read NBT", e);
        }
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
        if (!stack.isEmpty() && stack.getItem() instanceof TileBehavior) {
            TileInventory.super.setStack(slot, stack);
        }
        if (this.world != null) {
            updateTileSkin();
        }
    }
    private TileType determineTileType(ItemStack stack) {
        if (stack.isEmpty()) {
            return TileType.DEFAULT;
        }
        if (stack.getItem() == ModItems.TILE_BEHAVIOR_START) {
            return TileType.START;
        }
        return TileType.DEFAULT;
    }

    public void updateTileSkin() {
        if (this.world != null) {
            int redstonePower = this.world.getReceivedRedstonePower(this.pos); // Get redstone signal strength
            int slot = Math.min(redstonePower, items.size() - 1); // Map signal (0–15) to slot (0–15)

            // Get the stack in the corresponding slot
            ItemStack stack = items.get(slot);

            // Determine the tile type based on the stack
            TileType tileType = determineTileType(stack);

            // Update the block state if necessary
            BlockState state = this.getCachedState();
            if (state.getBlock() instanceof Tile && state.get(Tile.TILE_TYPE) != tileType) {
                this.world.setBlockState(this.pos, state.with(Tile.TILE_TYPE, tileType));
            }
        }
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (this.world != null &&!this.world.isClient && this.world instanceof ServerWorld) {
            ((ServerWorld) this.world).getServer().submit(this::updateTileSkin);
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