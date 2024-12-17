package fr.lordfinn.steveparty.blocks.tiles;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.tiles.behaviors.ATileBehavior;
import fr.lordfinn.steveparty.blocks.tiles.behaviors.TileBehaviorFactory;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TileBehaviorComponent;
import fr.lordfinn.steveparty.entities.custom.DirectionDisplayEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.tilebehaviors.TileBehaviorItem;
import fr.lordfinn.steveparty.screens.TileScreenHandler;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.blocks.tiles.Tile.TILE_TYPE;

public class TileEntity extends BlockEntity implements NamedScreenHandlerFactory, TickableBlockEntity {
    //private final DefaultedList<ItemStack> items = DefaultedList.ofSize(16, ItemStack.EMPTY);
    private int ticks = 0;



    public TileEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TILE_ENTITY, pos, state);
    }

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
    }

    public DefaultedList<ItemStack> getItems() {
        return inventory.getHeldStacks();
    }

    private TileType determineTileType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() == ModItems.TILE_BEHAVIOR_START) {
            return TileType.START;
        }
        return null;
    }

    public void updateTileSkin() {
        if (this.world != null) {
            ItemStack stack = getActiveTileBehaviorItemStack();
            // Determine the tile type based on the stack
            TileType tileType = determineTileType(stack);

            // Update the block state if necessary
            BlockState state = this.getCachedState();
            if (state.getBlock() instanceof Tile && tileType != null && state.get(TILE_TYPE) != tileType) {
                this.world.setBlockState(this.pos, state.with(TILE_TYPE, tileType));
            }
        }
    }

    public Boolean toggleDestinations(ServerPlayerEntity holder) {
        if (world == null) return null;
        List<DirectionDisplayEntity> e = getCurrentDestinations();
        if (e.isEmpty()) {
            displayDestinations(holder);
            return true;
        }
        hideDestinations(e);
        return false;
    }


    public void displayDestinations(ServerPlayerEntity holder, List<TileDestination> destinations) {
        if (destinations.isEmpty()) return;
        for (TileDestination destination : destinations) {
            new DirectionDisplayEntity(world, destination, this.getPos(), holder);
        }
    }

    public void displayDestinations(ServerPlayerEntity holder) {
        List<TileDestination> destinations = getCurrentDestinations(this);
        displayDestinations(holder, destinations);
    }

    public void hideDestinations() {
        if (world == null) return;
        List<DirectionDisplayEntity> e = getCurrentDestinations();
        if (e.isEmpty()) return;
        hideDestinations(e);
    }

    private void hideDestinations(List<DirectionDisplayEntity> e) {
        e.forEach(entity -> {if (entity.getTileOrigin().equals(this.getPos())) entity.remove(Entity.RemovalReason.DISCARDED);});
    }

    private List<DirectionDisplayEntity> getCurrentDestinations() {
        if (world == null) return new ArrayList<>();
        return world.getEntitiesByClass(DirectionDisplayEntity.class, Box.of(this.getPos().toCenterPos(), 4, 4, 4), entity -> entity instanceof DirectionDisplayEntity);
    }

    public ItemStack getActiveTileBehaviorItemStack() {
        if (this.world == null) {
            return ItemStack.EMPTY;
        }
        int slot = this.world.getReceivedRedstonePower(this.pos);
        return inventory.getStack(slot);
    }

    public ATileBehavior getTileBehavior() {
        ItemStack stack = getActiveTileBehaviorItemStack();
        TileType tileType = determineTileType(stack);
        if (tileType == null)
            return null;

        return TileBehaviorFactory.get(tileType);
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
        if (this.world.isClient) {
            world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
        if (this.world != null && !this.world.isClient && this.world instanceof ServerWorld) {
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
        if (tileType != null)
            TileBehaviorFactory.get(tileType).tick((ServerWorld) this.world, this, stack, ticks);
    }
    @Override
    public @Nullable Object getRenderData() {
        // this is the method from `RenderDataBlockEntity` class.
        return this;
    }

    public void getNextTiles() {
       ItemStack stack = this.getActiveTileBehaviorItemStack();

    }

    public static List<TileDestination> getCurrentDestinations(TileEntity tileEntity) {
        List<TileDestination> tileDestinations = new ArrayList<>();

        ItemStack stack = tileEntity.getActiveTileBehaviorItemStack();
        if (stack != null && stack.getItem() instanceof TileBehaviorItem) {
            TileBehaviorComponent component = stack.getOrDefault(ModComponents.TILE_BEHAVIOR_COMPONENT, TileBehaviorComponent.DEFAULT_TILE_BEHAVIOR);
            List<BlockPos> destinations = new ArrayList<>(component.destinations()); // Copy destinations to a new list.
            tileDestinations = getDestinationsStatus(destinations, tileEntity.getWorld());
        }
        return tileDestinations;
    }

    public static List<TileDestination> getDestinationsStatus(List<BlockPos> blockPosList, World world) {
        List<TileDestination> tileDestinations = new ArrayList<>();
        for (BlockPos pos : blockPosList) {
            boolean isTile = isTileBlock(pos, world);
            tileDestinations.add(new TileDestination(pos, isTile));
        }
        return tileDestinations;
    }

    /**
     * Checks if the given position is a valid tile block in the world.
     *
     * @param pos The position to check.
     * @param world The world instance to check the block in.
     * @return true if the position is a valid tile block, false otherwise.
     */
    private static boolean isTileBlock(BlockPos pos, World world) {
        if (world == null) return false;
        BlockState blockState = world.getBlockState(pos);
        if (blockState == null) return false;
        return blockState.getBlock() instanceof Tile;
    }

    public List<MobEntity> getTokensOnMe() {
        List<MobEntity> tokens = new ArrayList<>();
        if (this.world != null) {
            for (MobEntity entity : ((ServerWorld) this.world).getEntitiesByClass(MobEntity.class, Box.of(this.getPos().toCenterPos(), 1, 1, 1), entity -> entity instanceof MobEntity)) {
                if (entity instanceof TokenizedEntityInterface && ((TokenizedEntityInterface) entity).steveparty$isTokenized()) {
                    tokens.add(entity);
                }
            }
        }
        return tokens;
    }
}