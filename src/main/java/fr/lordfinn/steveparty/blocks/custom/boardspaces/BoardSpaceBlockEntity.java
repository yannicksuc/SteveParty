package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.BoardSpaceBehaviorFactory;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.BoardSpaceBehaviorComponent;
import fr.lordfinn.steveparty.entities.custom.DirectionDisplayEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpace.TILE_TYPE;

public class BoardSpaceBlockEntity extends CartridgeContainerBlockEntity implements TickableBlockEntity {
    private int ticks = 0;
    private SoundEvent walkedOnSound = null;
    private final Map<Integer, Integer> cycleIndexes = new HashMap<>();


    public BoardSpaceBlockEntity(BlockPos pos, BlockState state) {
        super(state.getBlock() instanceof Tile ? ModBlockEntities.TILE_ENTITY : ModBlockEntities.TRIGGER_POINT_ENTITY, pos, state, 16);
    }

/*    private final SimpleInventory inventory = new SimpleInventory(16) {
        @Override
        public void markDirty() {
            super.markDirty();
            update();
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
            if (!stack.isEmpty() && stack.getItem() instanceof CartridgeItem) {
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
    };*/

    private void update() {
        markDirty();
        if(world != null)
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL);
    }

    public DefaultedList<ItemStack> getItems() {
        return this.getHeldStacks();
    }

    private BoardSpaceType determineBoardSpaceType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() == ModItems.TILE_BEHAVIOR_START) {
            return BoardSpaceType.TILE_START;
        } else if (stack.getItem() == ModItems.BOARD_SPACE_BEHAVIOR_STOP) {
            return BoardSpaceType.BOARD_SPACE_STOP;
        } else if (stack.getItem() == ModItems.INVENTORY_CARTRIDGE) {
            return BoardSpaceType.TILE_INVENTORY_INTERACTOR;
        } else if (stack.getItem() == ModItems.BOARD_SPACE_BEHAVIOR) {
            return BoardSpaceType.DEFAULT;
        }
        return null;
    }

    public void updateTileSkin() {
        if (this.world != null) {
            ItemStack stack = getActiveTileBehaviorItemStack();
            BoardSpaceType tileType = determineBoardSpaceType(stack);

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
        BoardSpaceBlockEntity boardSpaceEntity = Tile.getBoardSpaceEntity(world, pos);
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
        BoardSpaceBlockEntity.hideDestinations((ServerWorld) this.world, this.getPos());
    }

    private static List<DirectionDisplayEntity> getSpawnedDestinations(ServerWorld world, BlockPos pos) {
        if (world == null) return new ArrayList<>();
        return world.getEntitiesByClass(DirectionDisplayEntity.class, Box.of(pos.toCenterPos(), 4, 4, 4), entity -> entity instanceof DirectionDisplayEntity);
    }

    public List<BoardSpaceDestination> getStockedDestinations(){
        List<BoardSpaceDestination> tileDestinations = new ArrayList<>();

        ItemStack stack = this.getActiveTileBehaviorItemStack();
        if (stack != null && stack.getItem() instanceof CartridgeItem) {
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
        return this.getStack(slot);
    }

    public void setActiveTileBehaviorItemStack(ItemStack stack) {
        if (this.world == null) return;
        int slot = this.world.getReceivedRedstonePower(this.pos);
        this.setStack(slot, stack);
        this.markDirty();
    }

    public ABoardSpaceBehavior getTileBehavior() {
        ItemStack stack = getActiveTileBehaviorItemStack();
        BoardSpaceType tileType = determineBoardSpaceType(stack);
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
        if (this.world == null) return;
        if (this.world instanceof ServerWorld serverWorld) {
            serverWorld.getServer().submit(this::updateTileSkin);
        }
    }

    @Override
    public void tick() {
        if (this.world == null || this.world.isClient) return;
        ticks++;
        ItemStack stack = getActiveTileBehaviorItemStack();
        BoardSpaceType tileType = determineBoardSpaceType(stack);
        if (tileType != null)
            BoardSpaceBehaviorFactory.get(tileType).tick((ServerWorld) this.world, this, stack, ticks);
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

    public void onDestinationReached(MobEntity token, PartyControllerEntity partyController) {
        this.getTileBehavior().onDestinationReached(this.world, this.pos, token, this, partyController);
        partyController.nextStep();
    }

    protected void setWalkedOnSound(SoundEvent walkedOnSound) {
        this.walkedOnSound = walkedOnSound;
    }

    public void onTileReached(@NotNull MobEntity token, PartyControllerEntity partyControllerEntity) {
        if (this.world == null || this.walkedOnSound == null) return;
        this.world.playSound(null, this.pos, this.walkedOnSound, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    public void setCycleIndex(int i) {
        if (this.world != null) {
            cycleIndexes.put(this.world.getReceivedRedstonePower(this.pos), i);
        }
    }

    public int getCycleIndex() {
        if (this.world != null) {
            return cycleIndexes.getOrDefault(this.world.getReceivedRedstonePower(this.pos), 0);
        }
        return 0;
    }
}