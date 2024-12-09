package fr.lordfinn.steveparty.blocks.tiles;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.tiles.behaviors.TileBehaviorFactory;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.tilebehaviors.TileBehaviorItem;
import fr.lordfinn.steveparty.screens.TileScreenHandler;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import static fr.lordfinn.steveparty.blocks.tiles.Tile.TILE_TYPE;

public class TileEntity extends BlockEntity implements NamedScreenHandlerFactory, TickableBlockEntity {
    //private final DefaultedList<ItemStack> items = DefaultedList.ofSize(16, ItemStack.EMPTY);
    private int ticks = 0;

    private final SimpleInventory inventory = new SimpleInventory(16) {
        @Override
        public void markDirty() {
            super.markDirty();
            update();
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
            if (!stack.isEmpty() && stack.getItem() instanceof TileBehaviorItem) {
                getItems().set(slot, stack);
                if (stack.getCount() > stack.getMaxCount()) {
                    stack.setCount(stack.getMaxCount());
                }
            }
            if ( TileEntity.this.world != null) {
                updateTileSkin();
            }
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < size(); i++) {
                ItemStack stack = getStack(i);
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }
    };

    private void update() {
        markDirty();
        if(world != null)
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL);
    }


    public TileEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TILE_ENTITY, pos, state);
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        Inventories.writeNbt(nbt, inventory.getHeldStacks(), wrapper);
        super.writeNbt(nbt, wrapper);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        try {
            Inventories.readNbt(nbt, inventory.getHeldStacks(), wrapper);
        } catch (Exception e) {
            Steveparty.LOGGER.error("Failed to read NBT", e);
        }
        if (getCachedState().get(TILE_TYPE) == TileType.START)
            inventory.getHeldStacks().forEach(item -> {
                Steveparty.LOGGER.info("item : {}", item);
            });

    }

    public DefaultedList<ItemStack> getItems() {
        return inventory.getHeldStacks();
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
            ItemStack stack = getActiveTileBehaviorItemStack();
            // Determine the tile type based on the stack
            TileType tileType = determineTileType(stack);

            // Update the block state if necessary
            BlockState state = this.getCachedState();
            if (state.getBlock() instanceof Tile && state.get(TILE_TYPE) != tileType) {
                this.world.setBlockState(this.pos, state.with(TILE_TYPE, tileType));
            }
        }
    }

    public ItemStack getActiveTileBehaviorItemStack() {
        if (this.world == null) {
            return ItemStack.EMPTY;
        }

        int redstonePower = this.world.getReceivedRedstonePower(this.pos); // Get redstone signal strength
        int slot = redstonePower; // Map signal (0–15) to slot (0–15)

        // Get the stack in the corresponding slot
        return inventory.getStack(slot);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        var nbt = super.toInitialChunkDataNbt(registries);
        writeNbt(nbt, registries);
        return nbt;
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (this.world != null &&!this.world.isClient && this.world instanceof ServerWorld) {
            ((ServerWorld) this.world).getServer().submit(this::updateTileSkin);
        }
    }

    @Override
    public Text getDisplayName() {
        return Text.of("Tile");//Text.translatable(getCachedState().getBlock().getTranslationKey());
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new TileScreenHandler(syncId, playerInventory, this.getInventory());
    }

    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void tick() {
        if (this.world == null || this.world.isClient) return;
        ticks++;
        ItemStack stack = getActiveTileBehaviorItemStack();
        TileType tileType = determineTileType(stack);
        TileBehaviorFactory.get(tileType).tick((ServerWorld) this.world, this, stack, ticks);
    }
}