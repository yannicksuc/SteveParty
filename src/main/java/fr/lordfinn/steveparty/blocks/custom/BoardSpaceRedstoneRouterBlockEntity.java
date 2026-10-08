package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.board.BoardPerf;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.screen_handlers.custom.RouterScreenHandler;
import fr.lordfinn.steveparty.persistent_state.BoardSpaceRoutersPersistentState;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Gives its redstone power to the board spaces listed by its cartridge: that power selects their active cartridge.
 * <p>
 * Everything is pushed on change: the routing is updated when the cartridge changes, and the power is pushed to the
 * routed board spaces when the router's own power changes. Board spaces never poll their router.
 * <p>
 * It is also a sensor: a comparator reading it outputs a pulse when a token stops on one of its board spaces (its
 * strength tells the tile's role, see {@link #landingSignal}) and a short weak pulse when a token goes over one
 * ({@link #PASS_SIGNAL}). The pulse ends through a scheduled tick of the block: nothing ticks in between.
 */
public class BoardSpaceRedstoneRouterBlockEntity extends CartridgeContainerBlockEntity {
    /** Comparator level while a token goes over a routed board space. */
    public static final int PASS_SIGNAL = 1;
    /** Pulse lengths, in game ticks. */
    public static final int LANDING_TICKS = 10, PASS_TICKS = 4;

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

    public BoardSpaceRedstoneRouterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOARD_SPACE_REDSTONE_ROUTER_ENTITY, pos, state, 1);
    }

    @Override
    public Text getDisplayName() {
        return Text.empty(); //"block.steveparty.board_space_redstone_router"
    }

    @Override
    public void markDirty() {
        super.markDirty();
        // The cartridge's destinations may have been edited in place: re-route only if they really changed
        updateRouting();
    }

    public int getPower() {
        BoardPerf.routerPowerReads++;
        return world == null ? 0 : world.getReceivedRedstonePower(pos);
    }

    /** Called when the router's neighbors (and so maybe its power) changed. */
    public void pushPowerToBoardSpaces() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        int power = getPower();
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
        List<BlockPos> destinations = List.copyOf(getDestinations(0));
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
        net.minecraft.item.ItemStack stack = boardSpace.getActiveCartridgeItemStack();
        var behavior = boardSpace.getBoardSpaceBehavior(stack);
        int level = behavior == null ? LEVEL_DEFAULT : behavior.comparatorLevel(boardSpace, stack == null ? net.minecraft.item.ItemStack.EMPTY : stack);
        router.pulse(Math.clamp(level, PASS_SIGNAL + 1, 15), LANDING_TICKS);
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
    public void setWorld(net.minecraft.world.World world) {
        super.setWorld(world);
        // Rebuild the in-memory routing list from the cartridge (the persistent state already holds the mapping)
        this.routedBoardSpaces = List.copyOf(getDestinations(0));
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new RouterScreenHandler(syncId, playerInventory, this);
    }
}
