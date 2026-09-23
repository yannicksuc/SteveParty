package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
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
 */
public class BoardSpaceRedstoneRouterBlockEntity extends CartridgeContainerBlockEntity {
    private List<BlockPos> routedBoardSpaces = List.of();

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
        return world == null ? 0 : world.getReceivedRedstonePower(pos);
    }

    /** Called when the router's neighbors (and so maybe its power) changed. */
    public void pushPowerToBoardSpaces() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        int power = getPower();
        for (BlockPos boardSpacePos : routedBoardSpaces) {
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
