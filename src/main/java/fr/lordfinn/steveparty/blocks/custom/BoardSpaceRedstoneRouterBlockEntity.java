package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.board.BoardPerf;
import fr.lordfinn.steveparty.board.BoardRevision;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.persistent_state.BoardSpaceRoutersPersistentState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Gives its redstone power to the board spaces it is linked to (painted with the Tile Linker Brush and kept by the
 * block itself, see RouterLinks): that power selects their active cartridge. No cartridge, no screen.
 * <p>
 * Everything is pushed on change: the routing is updated when its links change, and the power is pushed to the
 * routed board spaces when the router's own power changes. Board spaces never poll their router.
 * <p>
 * It is also a sensor: a comparator reading it outputs a pulse when a token stops on one of its board spaces (its
 * strength tells the tile's role, see {@link #landingSignal}) and a short weak pulse when a token goes over one
 * ({@link #PASS_SIGNAL}). The pulse ends through a scheduled tick of the block: nothing ticks in between.
 */
public class BoardSpaceRedstoneRouterBlockEntity extends BlockEntity implements GeoBlockEntity {
    /** Its board spaces, saved and sent to the clients (the brush and the board views draw them). */
    private static final String TILES = "Tiles";
    /** Routers saved with a cartridge (they held one before): its links become theirs, the cartridge is given back. */
    private static final String LEGACY_ITEMS = "Items";
    /** Comparator level while a token goes over a routed board space. */
    public static final int PASS_SIGNAL = 1;
    /** Pulse lengths, in game ticks. */
    public static final int LANDING_TICKS = 10, PASS_TICKS = 4;

    /** The board spaces it drives, in the order they were linked. */
    private List<BlockPos> tiles = List.of();
    /** Server side, a router loaded with a cartridge: given back (dropped on it) once its world is there. */
    private ItemStack legacyCartridge = ItemStack.EMPTY;
    private List<BlockPos> routedBoardSpaces = List.of();
    /**
     * The power last pushed to the routed board spaces (-1: none yet). Neighbour updates come in bursts (redstone dust,
     * blocks placed around) and most don't change the power: only a change is pushed. Board spaces taken since then read
     * the router's power themselves (see BoardSpaceBlockEntity#refreshActiveSlot), and so do those loaded later.
     */
    private int pushedPower = -1;
    /** Comparator output of the current pulse (0: none) and the game time it ends at. Not saved: pulses are short. */
    private int signal;
    private long signalEnd;

    /** Its lever, drawn by GeckoLib (no animation: the renderer sets its angle). */
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Client only: the lever's drawn angle (degrees, NaN until first drawn) and the time it was drawn at (ticks). */
    public float leverAngle = Float.NaN, leverTime;

    public BoardSpaceRedstoneRouterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOARD_SPACE_REDSTONE_ROUTER_ENTITY, pos, state);
    }

    /** The board spaces it drives. */
    public List<BlockPos> getTiles() {
        return tiles;
    }

    /** Links it to {@code tiles} (server side): saved, re-routed and sent to the clients. */
    public void setTiles(List<BlockPos> tiles) {
        this.tiles = tiles.stream().map(BlockPos::toImmutable).toList();
        markDirty();
        if (world != null && !world.isClient) {
            BlockState state = getCachedState();
            world.updateListeners(pos, state, state, Block.NOTIFY_ALL);
        }
    }

    @Override
    public void markDirty() {
        super.markDirty();
        BoardRevision.changed(world);
        // Its links may have been edited (brush, undo, /data): re-route only if they really changed
        updateRouting();
    }

    public int getPower() {
        BoardPerf.routerPowerReads++;
        return world == null ? 0 : world.getReceivedRedstonePower(pos);
    }

    /**
     * Puts its received power (-1: read it) in its state (its lever and torch), if its neighbours are loaded (reading an
     * unloaded one would load its chunk). Listeners only: no block update to its neighbours.
     */
    private void showPower(int power) {
        if (!(world instanceof ServerWorld serverWorld) || isRemoved()) return;
        BlockState state = serverWorld.getBlockState(pos);
        if (!state.contains(BoardSpaceRedstoneRouterBlock.POWER)) return;
        for (Direction side : Direction.Type.HORIZONTAL) {
            if (!serverWorld.isChunkLoaded(pos.offset(side))) return;
        }
        if (power < 0) power = getPower();
        if (state.get(BoardSpaceRedstoneRouterBlock.POWER) != power)
            serverWorld.setBlockState(pos, state.with(BoardSpaceRedstoneRouterBlock.POWER, power), Block.NOTIFY_LISTENERS);
    }

    /** Called when the router's neighbors (and so maybe its power) changed. */
    public void pushPowerToBoardSpaces() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        int power = getPower();
        showPower(power);
        if (power == pushedPower) return;
        pushedPower = power;
        for (BlockPos boardSpacePos : routedBoardSpaces) {
            BoardPerf.routerPushLookups++;
            if (serverWorld.isChunkLoaded(boardSpacePos)
                    && serverWorld.getBlockEntity(boardSpacePos) instanceof BoardSpaceBlockEntity boardSpace) {
                boardSpace.onRouterPowerChanged(this.pos, power);
            }
        }
    }

    private void updateRouting() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        List<BlockPos> destinations = tiles;
        if (destinations.equals(routedBoardSpaces)) return;
        routedBoardSpaces = destinations;
        Set<BlockPos> changed = BoardSpaceRoutersPersistentState.get(serverWorld).setRoutedBoardSpaces(this.pos, destinations);
        refreshBoardSpaces(serverWorld, changed);
    }

    /** Called when the router is removed: its board spaces go back to their own redstone power. */
    public void unroute() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        routedBoardSpaces = List.of();
        Set<BlockPos> changed = BoardSpaceRoutersPersistentState.get(serverWorld).setRoutedBoardSpaces(this.pos, List.of());
        refreshBoardSpaces(serverWorld, changed);
    }

    private static void refreshBoardSpaces(ServerWorld world, Set<BlockPos> boardSpaces) {
        for (BlockPos boardSpacePos : boardSpaces) {
            if (world.isChunkLoaded(boardSpacePos) && world.getBlockEntity(boardSpacePos) instanceof BoardSpaceBlockEntity boardSpace) {
                boardSpace.refreshActiveSlot();
            }
        }
    }

    // ---------------------------------------------------------------- comparator output: board events

    /*
     * Comparator levels of a token stopping on a board space, by role, from 15 down (the wiki has the table). A role
     * gives its level through ABoardSpaceBehavior#comparatorLevel; by default the one of its landing kind. 1 is a token
     * passing.
     */
    public static final int LEVEL_DEFAULT = 15, LEVEL_BONUS = 14, LEVEL_MALUS = 13, LEVEL_ITEM = 12, LEVEL_START = 11,
            LEVEL_STOP = 10;
    /** Reserved for the roles being made: « Rejouer », « Téléportation », « Avancer » and « Reculer ». */
    public static final int LEVEL_REPLAY = 9, LEVEL_TELEPORT = 8, LEVEL_FORWARD = 7, LEVEL_BACKWARD = 6;
    /** A shop stop: a token ending its move on a shop tile, or held by a shop check point while its owner shops. */
    public static final int LEVEL_SHOP = 5;
    /** A star space: a token stopping on it (the star may be there or not). */
    public static final int LEVEL_STAR = 4;
    /** A Glandouille space: a token stopping on it (pushed on by a tower, or not). */
    public static final int LEVEL_GLANDOUILLE = 3;
    /** A Frousseux space: a token stopping on it (a Frousseux sent to steal, or not). */
    public static final int LEVEL_FROUSSEUX = 2;

    /** The level of a landing kind (roles without a level of their own); an unknown kind: the default level. */
    public static int landingSignal(TileFeedback.Landing landing) {
        return switch (landing) {
            case GOOD -> LEVEL_BONUS;
            case BAD -> LEVEL_MALUS;
            case ITEM -> LEVEL_ITEM;
            case START -> LEVEL_START;
            case STOP -> LEVEL_STOP;
            case SHOP -> LEVEL_SHOP;
            case REPLAY, REPLAY_SPENT -> LEVEL_REPLAY;
            case ADVANCE -> LEVEL_FORWARD;
            case BACK -> LEVEL_BACKWARD;
            case TELEPORT -> LEVEL_TELEPORT;
            case STAR -> LEVEL_STAR;
            case GLANDOUILLE -> LEVEL_GLANDOUILLE;
            case FROUSSEUX -> LEVEL_FROUSSEUX;
            // no level left: the Mistigri's space reads as the malus it is
            case MISTIGRI -> LEVEL_MALUS;
            // nor for the Trichaudron's: a prize, a bonus space
            case TRICHAUDRON -> LEVEL_BONUS;
            default -> LEVEL_DEFAULT;
        };
    }

    /** What a comparator reading this router outputs now. */
    public int getComparatorSignal() {
        return signal;
    }

    /** A token stopped on {@code boardSpace}: its router (if any, loaded) pulses for a comparator, by the tile's role. */
    public static void onTokenStopped(ServerWorld world, BoardSpaceBlockEntity boardSpace) {
        BoardSpaceRedstoneRouterBlockEntity router = routerOf(world, boardSpace.getPos());
        if (router == null) return;
        ItemStack stack = boardSpace.getActiveCartridgeItemStack();
        var behavior = boardSpace.getBoardSpaceBehavior(stack);
        int level = behavior == null ? LEVEL_DEFAULT : behavior.comparatorLevel(boardSpace, stack == null ? ItemStack.EMPTY : stack);
        if (level <= 0) return; // the role says nothing happened (a token blocked by a Threshold obstacle)
        router.pulse(Math.clamp(level, PASS_SIGNAL + 1, 15), LANDING_TICKS);
    }

    /** Something happened on {@code boardSpace} (a token got over a Threshold obstacle): its router pulses {@code level}. */
    public static void onBoardSpaceEvent(ServerWorld world, BlockPos boardSpace, int level) {
        BoardSpaceRedstoneRouterBlockEntity router = routerOf(world, boardSpace);
        if (router != null && level > 0) router.pulse(Math.clamp(level, PASS_SIGNAL + 1, 15), LANDING_TICKS);
    }

    /** A token went over the board space at {@code boardSpace} during a move: a short weak pulse. */
    public static void onTokenPassed(ServerWorld world, BlockPos boardSpace) {
        BoardSpaceRedstoneRouterBlockEntity router = routerOf(world, boardSpace);
        if (router != null) router.pulse(PASS_SIGNAL, PASS_TICKS);
    }

    private static @Nullable BoardSpaceRedstoneRouterBlockEntity routerOf(ServerWorld world, BlockPos boardSpace) {
        BlockPos routerPos = BoardSpaceRoutersPersistentState.get(world).getRouter(boardSpace);
        // Never load a chunk for a pulse
        if (routerPos == null || !world.isChunkLoaded(routerPos)) return null;
        return world.getBlockEntity(routerPos) instanceof BoardSpaceRedstoneRouterBlockEntity router ? router : null;
    }

    /**
     * Outputs {@code level} for {@code ticks} game ticks. The latest event wins, except that a token passing never cuts
     * the pulse of a landing short; pulses of the same level following each other closely merge into one.
     */
    public void pulse(int level, int ticks) {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        long now = serverWorld.getTime();
        boolean running = signal > 0 && now < signalEnd;
        if (level == PASS_SIGNAL && running && signal > PASS_SIGNAL) return;
        signalEnd = running && level == signal ? Math.max(signalEnd, now + ticks) : now + ticks;
        if (level != signal) {
            signal = level;
            serverWorld.updateComparators(pos, getCachedState().getBlock());
        }
        // Ignored if a tick is already scheduled (a pulse in progress): that tick reschedules itself until signalEnd
        serverWorld.scheduleBlockTick(pos, getCachedState().getBlock(), (int) (signalEnd - now));
    }

    /** The scheduled tick of the block: ends the pulse, or waits for its (extended) end. */
    public void onPulseTick() {
        if (!(this.world instanceof ServerWorld serverWorld) || signal == 0) return;
        long now = serverWorld.getTime();
        if (now < signalEnd) {
            serverWorld.scheduleBlockTick(pos, getCachedState().getBlock(), (int) (signalEnd - now));
            return;
        }
        signal = 0;
        serverWorld.updateComparators(pos, getCachedState().getBlock());
    }

    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        // Rebuild the in-memory routing list from its links (the persistent state already holds the mapping)
        this.routedBoardSpaces = tiles;
        // Its lever and torch show its power: routers saved before had none, and power may have changed while unloaded.
        // Queued: the chunk is still loading here. A router saved with a cartridge gives it back then.
        if (world instanceof ServerWorld serverWorld) {
            MinecraftServer server = serverWorld.getServer();
            server.send(new ServerTask(server.getTicks(), () -> {
                showPower(-1);
                giveBackLegacyCartridge();
            }));
        }
    }

    /** Drops the cartridge it held before (its links are now its own): a player's item is never lost. */
    private void giveBackLegacyCartridge() {
        if (legacyCartridge.isEmpty() || isRemoved() || world == null) return;
        ItemStack cartridge = legacyCartridge;
        legacyCartridge = ItemStack.EMPTY;
        ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, cartridge);
        markDirty();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putLongArray(TILES, tiles.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        List<BlockPos> read = new ArrayList<>();
        for (long packed : nbt.getLongArray(TILES)) read.add(BlockPos.fromLong(packed));
        if (nbt.contains(LEGACY_ITEMS, NbtElement.LIST_TYPE)) {
            DefaultedList<ItemStack> slots = DefaultedList.ofSize(1, ItemStack.EMPTY);
            Inventories.readNbt(nbt, slots, registries);
            ItemStack cartridge = slots.getFirst();
            if (!cartridge.isEmpty()) {
                DestinationsComponent links = cartridge.get(ModComponents.DESTINATIONS_COMPONENT);
                if (links != null) for (BlockPos tile : links.destinations()) if (!read.contains(tile)) read.add(tile.toImmutable());
                cartridge.remove(ModComponents.DESTINATIONS_COMPONENT);
                legacyCartridge = cartridge;
            }
        }
        tiles = List.copyOf(read);
        BoardRevision.changed(world); // new links from the server
        // Data given to a router already in its world (/data, a structure): its cartridge is given back too
        if (!legacyCartridge.isEmpty() && world instanceof ServerWorld serverWorld) {
            MinecraftServer server = serverWorld.getServer();
            server.send(new ServerTask(server.getTicks(), this::giveBackLegacyCartridge));
        }
    }

    /** Its links are sent to the clients (on load and on each change): the brush and the board views draw them. */
    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
