package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.BoardSpaceBehaviorFactory;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.entities.custom.DirectionDisplayEntity;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
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
import net.minecraft.util.math.Vec3d;
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
    private static final String ACTIVE_SLOT_KEY = "ActiveSlot";
    private static final String CYCLE_INDEXES_KEY = "CycleIndexes";

    private int ticks = 0;
    private SoundEvent walkedOnSound = null;
    private final Map<Integer, Integer> cycleIndexes = new HashMap<>();
    public final int INV_SIZE;

    /**
     * Index of the cartridge giving this board space its role: the redstone power received by the board space,
     * or by its router. Event-driven (neighbor update, router push, inventory change), saved and synced to clients:
     * nothing is polled per tick.
     */
    private int activeSlot = 0;
    /** Set when loaded from disk: the power may have changed while unloaded, recheck on first server use. */
    private boolean activeSlotNeedsCheck = true;
    /** Last applied cartridge / type (baseline captured on creation and load), to react only to real changes. */
    private ItemStack appliedCartridge = ItemStack.EMPTY;
    private BoardSpaceType appliedType = BoardSpaceType.DEFAULT;

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

    public DefaultedList<ItemStack> getItems() {
        return this.getHeldStacks();
    }

    // ---------------------------------------------------------------- active slot

    public int getActiveSlot() {
        if (activeSlotNeedsCheck && world instanceof ServerWorld) {
            activeSlotNeedsCheck = false;
            refreshActiveSlot();
        }
        return activeSlot;
    }

    /** Recomputes the active slot from the current redstone power (own or router's). Server side. */
    public void refreshActiveSlot() {
        if (!(world instanceof ServerWorld serverWorld)) return;
        activeSlotNeedsCheck = false;
        BlockPos routerPos = BoardSpaceRoutersPersistentState.get(serverWorld).getRouter(pos);
        BlockPos powerPos = routerPos != null ? routerPos : pos;
        // Never load a chunk for this: an unloaded router keeps the last known slot, it pushes its power when it changes
        if (!serverWorld.isChunkLoaded(powerPos)) return;
        setActiveSlot(serverWorld.getReceivedRedstonePower(powerPos));
    }

    /** Own neighbors changed: only relevant when this board space is not driven by a router. */
    public void onNeighborUpdate() {
        if (!(world instanceof ServerWorld serverWorld)) return;
        if (BoardSpaceRoutersPersistentState.get(serverWorld).getRouter(pos) != null) return;
        activeSlotNeedsCheck = false;
        setActiveSlot(serverWorld.getReceivedRedstonePower(pos));
    }

    /** Pushed by a router whose power changed. */
    public void onRouterPowerChanged(BlockPos routerPos, int power) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        if (!routerPos.equals(BoardSpaceRoutersPersistentState.get(serverWorld).getRouter(pos))) return;
        activeSlotNeedsCheck = false;
        setActiveSlot(power);
    }

    private void setActiveSlot(int slot) {
        if (slot == activeSlot) return;
        activeSlot = slot;
        super.markDirty();
        applyActiveCartridge();
        syncToClients(); // the GUI shows the active slot, even when the cartridge doesn't change
    }

    /**
     * Placed from an item that already holds cartridges: the loaded cartridges count as applied, but the block state
     * still has the default type. Takes the role of the active cartridge right away.
     */
    public void onPlaced() {
        if (!(world instanceof ServerWorld)) return;
        refreshActiveSlot();
        if (getCachedState().get(TILE_TYPE) != determineBoardSpaceType(getStack(activeSlot))) {
            appliedCartridge = null;
            applyActiveCartridge();
        }
    }

    @Override
    protected void onInventoryChanged() {
        applyActiveCartridge();
    }

    public ItemStack getActiveCartridgeItemStack() {
        if (this.world == null) return ItemStack.EMPTY;
        return this.getStack(getActiveSlot());
    }

    public void setActiveCartridgeItemStack(ItemStack stack) {
        if (this.world == null) return;
        this.setStack(getActiveSlot(), stack);
    }

    /**
     * Applies the role of the active cartridge (block state type, colour, token notification) if it changed.
     * Server side; the client receives the result through the block state and the block entity data.
     */
    private void applyActiveCartridge() {
        if (!(world instanceof ServerWorld serverWorld)) return;
        ItemStack stack = getStack(activeSlot);
        BoardSpaceType type = determineBoardSpaceType(stack);
        if (stack == appliedCartridge && type == appliedType) {
            updateBoardSpaceColor();
            return;
        }
        appliedCartridge = stack;
        appliedType = type;

        spawnChangeParticles(serverWorld);
        BlockState state = getCachedState();
        if (state.getBlock() instanceof ABoardSpaceBlock && state.get(TILE_TYPE) != type) {
            // The type drives the model, and whether the block entity ticks (see ABoardSpaceBlock#getTicker)
            serverWorld.setBlockState(pos, state.with(TILE_TYPE, type));
        }
        updateBoardSpaceColor();
        syncToClients();
        getTokensOnMe().forEach(token -> EVENT.invoker().onTileUpdated(token, this));
    }

    private static BoardSpaceType determineBoardSpaceType(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof CartridgeItem cartridge)
            return cartridge.getBoardSpaceType();
        return BoardSpaceType.DEFAULT;
    }

    private void updateBoardSpaceColor() {
        ItemStack stack = getStack(activeSlot);
        if (stack.isEmpty()) return;
        getBoardSpaceBehavior(stack).updateBoardSpaceColor(this, stack);
    }

    private void spawnChangeParticles(ServerWorld serverWorld) {
        Vec3d center = pos.toCenterPos();
        serverWorld.spawnParticles(ParticleTypes.GLOW, center.x, center.y, center.z, 10, 0.05, 0.05, 0.05, 0.2);
    }

    @Override
    public void markDirty() {
        super.markDirty();
        // The active cartridge may have been edited in place (colour, destinations...)
        if (world instanceof ServerWorld) updateBoardSpaceColor();
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

    public ABoardSpaceBehavior getBoardSpaceBehavior() {
        ItemStack stack = getActiveCartridgeItemStack();
        return getBoardSpaceBehavior(stack);
    }

    public ABoardSpaceBehavior getBoardSpaceBehavior(ItemStack stack) {
        return BoardSpaceBehaviorFactory.get(determineBoardSpaceType(stack));
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

    /** Only ticks for board spaces whose role needs it (see ABoardSpaceBlock#getTicker). */
    @Override
    public void tick() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        ticks++;
        ItemStack stack = getActiveCartridgeItemStack();
        getBoardSpaceBehavior(stack).tick(serverWorld, this, stack, ticks);
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.writeNbt(nbt, wrapper);
        nbt.putInt(ACTIVE_SLOT_KEY, activeSlot);
        if (!cycleIndexes.isEmpty()) {
            NbtCompound cycles = new NbtCompound();
            cycleIndexes.forEach((slot, index) -> cycles.putInt(Integer.toString(slot), index));
            nbt.put(CYCLE_INDEXES_KEY, cycles);
        }
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        activeSlot = nbt.getInt(ACTIVE_SLOT_KEY);
        activeSlotNeedsCheck = true;
        appliedCartridge = getStack(activeSlot);
        appliedType = determineBoardSpaceType(appliedCartridge);
        cycleIndexes.clear();
        NbtCompound cycles = nbt.getCompound(CYCLE_INDEXES_KEY);
        for (String key : cycles.getKeys()) {
            try {
                cycleIndexes.put(Integer.parseInt(key), cycles.getInt(key));
            } catch (NumberFormatException ignored) {
            }
        }
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
        // A board space without cartridge acts as a default one: the game must go on
        this.getBoardSpaceBehavior().onDestinationReached(this.world, this.pos, token, this, partyController);
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
            super.markDirty();
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
