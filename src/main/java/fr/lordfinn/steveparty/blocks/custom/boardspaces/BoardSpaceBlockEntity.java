package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.BoardSpaceBehaviorFactory;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.entities.custom.DirectionDisplayEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.persistent_state.ClientBoardSpaceRouters;
import fr.lordfinn.steveparty.screen_handlers.custom.BoardSpaceScreenHandler;
import fr.lordfinn.steveparty.persistent_state.BoardSpaceRoutersPersistentState;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
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
import java.util.UUID;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock.TILE_TYPE;
import static fr.lordfinn.steveparty.events.TileUpdatedEvent.EVENT;

public class BoardSpaceBlockEntity extends CartridgeContainerBlockEntity implements TickableBlockEntity, ExtendedScreenHandlerFactory<BlockPosPayload> {

    private int ticks = 0;
    private SoundEvent walkedOnSound = null;
    private final Map<Integer, Integer> cycleIndexes = new HashMap<>();
    private ItemStack currentlyActiveCartridge = null;
    public final int INV_SIZE;
    /** Server-side cache of the active slot (-1 = must be recomputed). Invalidated on neighbor/router/inventory changes. */
    private int cachedActiveSlot = -1;
    /** Safety net: the cached active slot is recomputed at least this often (ticks). */
    private static final int ACTIVE_SLOT_REFRESH_INTERVAL = 20;
    /** Last state for which a TileUpdatedEvent was fired, to fire it only on real changes. */
    private ItemStack lastNotifiedCartridge = null;
    private BoardSpaceType lastNotifiedType = null;

    public BoardSpaceBlockEntity(BlockPos pos, BlockState state, BlockEntityType<? extends BoardSpaceBlockEntity> type, int size) {
        super(type, pos, state, size);
        INV_SIZE = size;
    }

    /** Marks the block entity dirty and sends its data (cartridges and their components) to the tracking clients. */
    public void update() {
        markDirty();
        syncToClients();
    }

    private void syncToClients() {
        if (world != null && !world.isClient)
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL);
    }

    public void invalidateActiveSlot() {
        this.cachedActiveSlot = -1;
    }

    @Override
    protected void onInventoryChanged() {
        invalidateActiveSlot();
        syncToClients();
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

    public void updateBoardSpaceType() {
        if (this.world != null) {
            invalidateActiveSlot();
            ItemStack stack = getActiveCartridgeItemStack();
            BoardSpaceType tileType = determineBoardSpaceType(stack);
            BlockState state = this.getCachedState();
            if (state.getBlock() instanceof ABoardSpaceBlock) {
                if (tileType == null)
                    tileType = BoardSpaceType.DEFAULT;
                if (!state.get(TILE_TYPE).equals(tileType)) {
                    this.world.setBlockState(this.pos, state.with(TILE_TYPE, tileType));
                }
                // Only notify the tokens when the active cartridge or the tile type really changed
                if (stack != this.lastNotifiedCartridge || tileType != this.lastNotifiedType) {
                    this.lastNotifiedCartridge = stack;
                    this.lastNotifiedType = tileType;
                    this.getTokensOnMe().forEach(token -> EVENT.invoker().onTileUpdated(token, this));
                }
            }
        }
    }

    private void updateBoardSpaceColor() {
        ItemStack stack = currentlyActiveCartridge;
        if (stack == null || stack.isEmpty()) {
            ABoardSpaceBehavior.setColor(this, 0xFFFFFF);
            return;
        }
        ABoardSpaceBehavior behavior = getBoardSpaceBehavior(stack);
        if (behavior == null) {
            ABoardSpaceBehavior.setColor(this, 0xFFFFFF);
            return;
        }
        behavior.updateBoardSpaceColor(this, stack);
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
        displayDestinations(world, pos, holder, destinations, null);
    }

    public static void displayDestinations(ServerWorld world, BlockPos pos, ServerPlayerEntity holder, List<BoardSpaceDestination> destinations, @Nullable UUID token) {
        if (destinations.isEmpty()) return;
        for (BoardSpaceDestination destination : destinations) {
            new DirectionDisplayEntity(world, destination, pos, holder, token);
        }
    }

    public static void searchAndDisplayDestinations(ServerWorld world, BlockPos pos, ServerPlayerEntity holder) {
        BoardSpaceBlockEntity boardSpaceEntity = TileBlock.getBoardSpaceEntity(world, pos);
        if (boardSpaceEntity == null) return;
        List<BoardSpaceDestination> destinations = boardSpaceEntity.getStockedDestinations();
        displayDestinations(world, pos, holder, destinations);
    }

    public void displayDestinations(ServerPlayerEntity player, List<BoardSpaceDestination> destinations) {
        displayDestinations((ServerWorld) this.getWorld(), this.getPos(), player, destinations);
    }

    public void displayDestinations(ServerPlayerEntity player, List<BoardSpaceDestination> destinations, @Nullable UUID token) {
        displayDestinations((ServerWorld) this.getWorld(), this.getPos(), player, destinations, token);
    }

    public static void hideDestinations(ServerWorld world, BlockPos pos) {
        if (world == null) return;
        List<DirectionDisplayEntity> spawnedDestinations = getSpawnedDestinations(world, pos);
        if (spawnedDestinations.isEmpty()) return;
        hideDestinations(spawnedDestinations, pos);
    }

    private static void hideDestinations(List<DirectionDisplayEntity> e, BlockPos pos) {
        e.forEach(entity -> {
            if (pos.equals(entity.getTileOrigin())) entity.remove(Entity.RemovalReason.DISCARDED);
        });
    }

    public void hideDestinations() {
        BoardSpaceBlockEntity.hideDestinations((ServerWorld) this.world, this.getPos());
    }

    private static List<DirectionDisplayEntity> getSpawnedDestinations(ServerWorld world, BlockPos pos) {
        if (world == null) return new ArrayList<>();
        return world.getEntitiesByClass(DirectionDisplayEntity.class, Box.of(pos.toCenterPos(), 4, 4, 4), entity -> entity instanceof DirectionDisplayEntity);
    }

    public List<BoardSpaceDestination> getStockedDestinations() {
        List<BoardSpaceDestination> tileDestinations = new ArrayList<>();
        ItemStack stack = this.getActiveCartridgeItemStack();
        if (stack != null && stack.getItem() instanceof CartridgeItem) {
            DestinationsComponent component = stack.getOrDefault(ModComponents.DESTINATIONS_COMPONENT, DestinationsComponent.DEFAULT);
            List<BlockPos> destinations = new ArrayList<>(component.destinations());
            tileDestinations = getDestinationsStatus(destinations, this.getWorld());
        }
        return tileDestinations;
    }

    public int getActiveSlot() {
        if (world == null) return 0;
        if (world.isClient) return computeActiveSlot(getPowerPos());
        if (cachedActiveSlot < 0) {
            BlockPos powerPos = getPowerPos();
            int slot = computeActiveSlot(powerPos);
            // Only cache when everything that can power this position is loaded: a chunk loading later
            // does not trigger any neighbor update, so the cached value could otherwise stay stale.
            if (!isPowerAreaLoaded(powerPos)) return slot;
            cachedActiveSlot = slot;
        }
        return cachedActiveSlot;
    }

    private BlockPos getPowerPos() {
        BlockPos routerPos = getRouterPos();
        return routerPos != null ? routerPos : pos;
    }

    private int computeActiveSlot(BlockPos powerPos) {
        return world != null ? world.getReceivedRedstonePower(powerPos) : 0;
    }

    private boolean isPowerAreaLoaded(BlockPos powerPos) {
        if (world == null) return false;
        return world.isChunkLoaded(powerPos.add(-2, 0, -2))
                && world.isChunkLoaded(powerPos.add(2, 0, -2))
                && world.isChunkLoaded(powerPos.add(-2, 0, 2))
                && world.isChunkLoaded(powerPos.add(2, 0, 2));
    }

    private BlockPos getRouterPos() {
        if (world instanceof ServerWorld sw) {
            BoardSpaceRoutersPersistentState routers = BoardSpaceRoutersPersistentState.get(sw.getServer());
            return routers == null ? null : routers.get(pos);
        }
        return ClientBoardSpaceRouters.getRouter(pos);
    }

    public ItemStack getActiveCartridgeItemStack() {
        if (this.world == null) {
            return ItemStack.EMPTY;
        }
        int slot = getActiveSlot();
        ItemStack newActiveCartridgeItemStack = this.getStack(slot);
        if (newActiveCartridgeItemStack != this.currentlyActiveCartridge) {
            ItemStack previous = this.currentlyActiveCartridge;
            this.currentlyActiveCartridge = newActiveCartridgeItemStack;
            if (!this.world.isClient()) {
                if (previous != null)
                    spawnChangementParticles(previous, newActiveCartridgeItemStack);
                markDirty();
                if (previous != null)
                    syncToClients();
            }
        }
        return newActiveCartridgeItemStack;
    }

    private void spawnChangementParticles(ItemStack currentlyActiveCartridge, ItemStack newActiveCartridgeItemStack) {
        double x = pos.toCenterPos().getX();
        double y = pos.toCenterPos().getY();
        double z = pos.toCenterPos().getZ();
        if (world != null) {
            ((ServerWorld)world).spawnParticles(ParticleTypes.GLOW, x, y ,z, 10, 0.05, 0.05, 0.05, 0.2);
        }
    }

    public void setActiveCartridgeItemStack(ItemStack stack) {
        if (this.world == null) return;
        int slot = getActiveSlot();
        this.setStack(slot, stack);
        this.markDirty();
    }

    public ABoardSpaceBehavior getBoardSpaceBehavior() {
        ItemStack stack = getActiveCartridgeItemStack();
        return getBoardSpaceBehavior(stack);
    }

    public ABoardSpaceBehavior getBoardSpaceBehavior(ItemStack stack) {
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
            invalidateActiveSlot();
            updateBoardSpaceColor();
            serverWorld.getServer().submit(this::updateBoardSpaceType);
        }
    }

    @Override
    public void tick() {
        if (this.world == null || this.world.isClient) return;
        ticks++;
        if (ticks % ACTIVE_SLOT_REFRESH_INTERVAL == 0)
            invalidateActiveSlot();
        ItemStack stack = getActiveCartridgeItemStack();
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
        return blockState.getBlock() instanceof ABoardSpaceBlock;
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
        ABoardSpaceBehavior behavior = this.getBoardSpaceBehavior();
        // A board space without (known) cartridge acts as a default one: the game must go on
        if (behavior == null) behavior = BoardSpaceBehaviorFactory.get(BoardSpaceType.DEFAULT);
        behavior.onDestinationReached(this.world, this.pos, token, this, partyController);
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
            cycleIndexes.put(getActiveSlot(), i);
        }
    }

    public int getCycleIndex() {
        if (this.world != null) {
            return cycleIndexes.getOrDefault(getActiveSlot(), 0);
        }
        return 0;
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new BoardSpaceScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public BlockPosPayload getScreenOpeningData(ServerPlayerEntity serverPlayerEntity) {
        return new BlockPosPayload(this.pos);
    }
}
