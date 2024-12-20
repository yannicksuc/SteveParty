package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.BoardSpaceBehaviorFactory;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.BoardSpaceBehaviorComponent;
import fr.lordfinn.steveparty.entities.custom.DirectionDisplayEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.tilebehaviors.BoardSpaceBehaviorItem;
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

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpace.TILE_TYPE;

public class BoardSpaceEntity extends BlockEntity implements NamedScreenHandlerFactory, TickableBlockEntity {
    //private final DefaultedList<ItemStack> items = DefaultedList.ofSize(16, ItemStack.EMPTY);
    private int ticks = 0;



    public BoardSpaceEntity(BlockPos pos, BlockState state) {
        super(state.getBlock() instanceof Tile ? ModBlockEntities.TILE_ENTITY : ModBlockEntities.TRIGGER_POINT_ENTITY, pos, state);
    }

    private final SimpleInventory inventory = new SimpleInventory(16) {
        @Override
        public void markDirty() {
            super.markDirty();
            update();
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
            if (!stack.isEmpty() && stack.getItem() instanceof BoardSpaceBehaviorItem) {
                getItems().set(slot, stack);
                if (stack.getCount() > stack.getMaxCount()) {
                    stack.setCount(stack.getMaxCount());
                }
            }
            if ( BoardSpaceEntity.this.world != null) {
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

    private BoardSpaceType determineTileType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() == ModItems.TILE_BEHAVIOR_START) {
            return BoardSpaceType.TILE_START;
        } else if (stack.getItem() == ModItems.BOARD_SPACE_BEHAVIOR_STOP) {
            return BoardSpaceType.BOARD_SPACE_STOP;
        } else if (stack.getItem() == ModItems.BOARD_SPACE_BEHAVIOR) {
            return BoardSpaceType.DEFAULT;
        }
        return null;
    }

    public void updateTileSkin() {
        if (this.world != null) {
            ItemStack stack = getActiveTileBehaviorItemStack();
            // Determine the tile type based on the stack
            BoardSpaceType tileType = determineTileType(stack);

            // Update the block state if necessary
            BlockState state = this.getCachedState();
            if (state.getBlock() instanceof BoardSpace && tileType == null) {
                this.world.setBlockState(this.pos, state.with(TILE_TYPE, BoardSpaceType.DEFAULT));
            } else if (state.getBlock() instanceof BoardSpace && tileType != null && !state.get(TILE_TYPE).equals(tileType)) {
                this.world.setBlockState(this.pos, state.with(TILE_TYPE, tileType));
            }
        }
    }

    public static Boolean toggleDestinations(ServerWorld world, BlockPos pos, ServerPlayerEntity holder) {
        if (world == null) return null;
        List<DirectionDisplayEntity> e = getSpawnedDestinations(world, pos);
        if (e.isEmpty()) {
            searchAndDisplayDestinations(world, pos, holder);
            return true;
        }
        hideDestinations(world, pos);
        return false;
    }


    public static void displayDestinations(ServerWorld world, BlockPos pos, ServerPlayerEntity holder, List<BoardSpaceDestination> destinations) {
        if (destinations.isEmpty()) return;
        for (BoardSpaceDestination destination : destinations) {
            new DirectionDisplayEntity(world, destination, pos, holder);
        }
    }

    public static void searchAndDisplayDestinations(ServerWorld world, BlockPos pos, ServerPlayerEntity holder) {
        BoardSpaceEntity boardSpaceEntity = Tile.getBoardSpaceEntity(world, pos);
        if (boardSpaceEntity == null) return;
        List<BoardSpaceDestination> destinations = boardSpaceEntity.getStockedDestinations();
        displayDestinations(world, pos, holder, destinations);
    }

    public void displayDestinations(ServerPlayerEntity player, List<BoardSpaceDestination> destinations) {
        displayDestinations((ServerWorld) this.getWorld(), this.getPos(), player, destinations);
    }

    public static void hideDestinations(ServerWorld world, BlockPos pos) {
        if (world == null) return;
        List<DirectionDisplayEntity> spawnedDestinations = getSpawnedDestinations(world, pos);
        if (spawnedDestinations.isEmpty()) return;
        hideDestinations(spawnedDestinations, pos);
    }

    private static void hideDestinations(List<DirectionDisplayEntity> e, BlockPos pos) {
        e.forEach(entity -> {if (entity.getTileOrigin().equals(pos)) entity.remove(Entity.RemovalReason.DISCARDED);});
    }

    public void hideDestinations() {
        BoardSpaceEntity.hideDestinations((ServerWorld) this.world, this.getPos());
    }

    private static List<DirectionDisplayEntity> getSpawnedDestinations(ServerWorld world, BlockPos pos) {
        if (world == null) return new ArrayList<>();
        return world.getEntitiesByClass(DirectionDisplayEntity.class, Box.of(pos.toCenterPos(), 4, 4, 4), entity -> entity instanceof DirectionDisplayEntity);
    }

    public List<BoardSpaceDestination> getStockedDestinations(){
        List<BoardSpaceDestination> tileDestinations = new ArrayList<>();

        ItemStack stack = this.getActiveTileBehaviorItemStack();
        if (stack != null && stack.getItem() instanceof BoardSpaceBehaviorItem) {
            BoardSpaceBehaviorComponent component = stack.getOrDefault(ModComponents.BOARD_SPACE_BEHAVIOR_COMPONENT, BoardSpaceBehaviorComponent.DEFAULT_BOARD_SPACE_BEHAVIOR);
            List<BlockPos> destinations = new ArrayList<>(component.destinations()); // Copy destinations to a new list.
            tileDestinations = getDestinationsStatus(destinations, this.getWorld());
        }
        return tileDestinations;
    }

    public ItemStack getActiveTileBehaviorItemStack() {
        if (this.world == null) {
            return ItemStack.EMPTY;
        }
        int slot = this.world.getReceivedRedstonePower(this.pos);
        return inventory.getStack(slot);
    }

    public void setActiveTileBehaviorItemStack(ItemStack stack) {
        if (this.world == null) return;
        int slot = this.world.getReceivedRedstonePower(this.pos);
        inventory.setStack(slot, stack);
        inventory.markDirty();
    }

    public ABoardSpaceBehavior getTileBehavior() {
        ItemStack stack = getActiveTileBehaviorItemStack();
        BoardSpaceType tileType = determineTileType(stack);
        if (tileType == null)
            return null;

        return BoardSpaceBehaviorFactory.get(tileType);
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
        assert this.world != null;
        if (this.world.isClient) {
            world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
        if (this.world != null && !this.world.isClient && this.world instanceof ServerWorld) {
            ((ServerWorld) this.world).getServer().submit(this::updateTileSkin);
        }
    }

    @Override
    public Text getDisplayName() {
        return Text.of("Board Space");//Text.translatable(getCachedState().getBlock().getTranslationKey());
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
        BoardSpaceType tileType = determineTileType(stack);
        if (tileType != null)
            BoardSpaceBehaviorFactory.get(tileType).tick((ServerWorld) this.world, this, stack, ticks);
    }
    @Override
    public @Nullable Object getRenderData() {
        // this is the method from `RenderDataBlockEntity` class.
        return this;
    }

    public static List<BoardSpaceDestination> getDestinationsStatus(List<BlockPos> blockPosList, World world) {
        List<BoardSpaceDestination> tileDestinations = new ArrayList<>();
        for (BlockPos pos : blockPosList) {
            boolean isTile = isBoardSpaceBlock(pos, world);
            tileDestinations.add(new BoardSpaceDestination(pos, isTile));
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
    private static boolean isBoardSpaceBlock(BlockPos pos, World world) {
        if (world == null) return false;
        BlockState blockState = world.getBlockState(pos);
        if (blockState == null) return false;
        return blockState.getBlock() instanceof BoardSpace;
    }

    public List<MobEntity> getTokensOnMe() {
        List<MobEntity> tokens = new ArrayList<>();
        if (this.world != null) {
            for (MobEntity entity : this.world.getEntitiesByClass(MobEntity.class, Box.of(this.getPos().toCenterPos(), 1, 1, 1), entity -> entity instanceof MobEntity)) {
                if (entity instanceof TokenizedEntityInterface && ((TokenizedEntityInterface) entity).steveparty$isTokenized()) {
                    tokens.add(entity);
                }
            }
        }
        return tokens;
    }
}