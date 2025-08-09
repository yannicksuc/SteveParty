package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.screen_handlers.custom.RouterScreenHandler;
import fr.lordfinn.steveparty.persistent_state.BoardSpaceRoutersPersistentState;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BoardSpaceRedstoneRouterBlockEntity extends CartridgeContainerBlockEntity {
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
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        BoardSpaceRoutersPersistentState persistentState = BoardSpaceRoutersPersistentState.get(serverWorld.getServer());
        persistentState.clear(this.pos, serverWorld);
        ItemStack stack = this.getStack(0);
        if (stack.isEmpty()) return;
        DestinationsComponent component = stack.getOrDefault(ModComponents.DESTINATIONS_COMPONENT, null);
        if (component == null) return;
        List<BlockPos> destinations = component.destinations();
        persistentState.putAll(destinations, this.pos, serverWorld);
        BoardSpaceRoutersPersistentState.sendToOnlinePlayers(serverWorld.getServer());
    }

    public void updateRoutedDestinations() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        BoardSpaceRoutersPersistentState persistentState = BoardSpaceRoutersPersistentState.get(serverWorld.getServer());
        persistentState.getAll().forEach((pos, routerPos) ->{
            if (routerPos.equals(this.pos) && serverWorld.getBlockEntity(pos) instanceof BoardSpaceBlockEntity boardSpaceBlockEntity)
                boardSpaceBlockEntity.markDirty();
        });
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new RouterScreenHandler(syncId, playerInventory, this);
    }
}
