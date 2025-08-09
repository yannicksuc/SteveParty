package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.BoardSpaceBehaviorFactory;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.entities.custom.DirectionDisplayEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.persistent_state.ClientBoardSpaceRouters;
import fr.lordfinn.steveparty.screen_handlers.custom.TileScreenHandler;
import fr.lordfinn.steveparty.persistent_state.BoardSpaceRoutersPersistentState;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
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

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock.TILE_TYPE;
import static fr.lordfinn.steveparty.events.TileUpdatedEvent.EVENT;

public class BoardSpaceBlockEntity extends CartridgeContainerBlockEntity implements TickableBlockEntity, ExtendedScreenHandlerFactory<BlockPosPayload> {

    private int ticks = 0;
    private SoundEvent walkedOnSound = null;
    private final Map<Integer, Integer> cycleIndexes = new HashMap<>();
    private ItemStack currentlyActiveCartridge = null;

    public BoardSpaceBlockEntity(BlockPos pos, BlockState state) {
        super(state.getBlock() instanceof TileBlock ? ModBlockEntities.TILE_ENTITY : ModBlockEntities.CHECK_POINT_ENTITY, pos, state, 16);
    }

    private void update() {
        markDirty();
        if (world != null)
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

    public void updateBoardSpaceType() {
        if (this.world != null) {
            ItemStack stack = getActiveCartridgeItemStack();
            BoardSpaceType tileType = determineBoardSpaceType(stack);
            BlockState state = this.getCachedState();
            if (state.getBlock() instanceof ABoardSpaceBlock) {
                if (tileType == null)
                    tileType = BoardSpaceType.DEFAULT;
                if (!state.get(TILE_TYPE).equals(tileType)) {
                    this.world.setBlockState(this.pos, state.with(TILE_TYPE, tileType));
                }
                this.getTokensOnMe().forEach(token -> EVENT.invoker().onTileUpdated(token, this));
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
        if (destinations.isEmpty()) return;
        for (BoardSpaceDestination destination : destinations) {
            new DirectionDisplayEntity(world, destination, pos, holder);
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

    public static void hideDestinations(ServerWorld world, BlockPos pos) {
        if (world == null) return;
        List<DirectionDisplayEntity> spawnedDestinations = getSpawnedDestinations(world, pos);
        if (spawnedDestinations.isEmpty()) return;
        hideDestinations(spawnedDestinations, pos);
    }

    private static void hideDestinations(List<DirectionDisplayEntity> e, BlockPos pos) {
        e.forEach(entity -> {
            if (entity.getTileOrigin().equals(pos)) entity.remove(Entity.RemovalReason.DISCARDED);
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
        BlockPos routerPos = getRouterPos();
        return world != null ? world.getReceivedRedstonePower(routerPos != null ? routerPos : pos) : 0;
    }

    private BlockPos getRouterPos() {
        if (world instanceof ServerWorld sw) return BoardSpaceRoutersPersistentState.get(sw.getServer()).get(pos);
        return ClientBoardSpaceRouters.getRouter(pos);
    }

    public ItemStack getActiveCartridgeItemStack() {
        if (this.world == null) {
            return ItemStack.EMPTY;
        }
        int slot = getActiveSlot();
        ItemStack newActiveCartridgeItemStack = this.getStack(slot);
        if (!newActiveCartridgeItemStack.equals(this.currentlyActiveCartridge)) {
            if (!this.world.isClient() && this.currentlyActiveCartridge != null)
                spawnChangementParticles(this.currentlyActiveCartridge, newActiveCartridgeItemStack);
            this.currentlyActiveCartridge = newActiveCartridgeItemStack;
            markDirty();
        }
        return this.getStack(slot);
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
            updateBoardSpaceColor();
            serverWorld.getServer().submit(this::updateBoardSpaceType);
        }
    }

    @Override
    public void tick() {
        if (this.world == null || this.world.isClient) return;
        ticks++;
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
        if (behavior == null) return;
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
        return new TileScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public BlockPosPayload getScreenOpeningData(ServerPlayerEntity serverPlayerEntity) {
        return new BlockPosPayload(this.pos);
    }
}
