package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.components.BoardSpaceBehaviorComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.utils.BoardSpaceRoutersPersistentState;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class BoardSpaceRedstoneRouterBlockEntity extends CartridgeContainerBlockEntity {
    public BoardSpaceRedstoneRouterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOARD_SPACE_REDSTONE_ROUTER_ENTITY, pos, state, 16);  //TODO implement for size of 1
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.steveparty.board_space_redstone_router");
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        BoardSpaceRoutersPersistentState persistentState = BoardSpaceRoutersPersistentState.get(serverWorld.getServer());
        persistentState.clear(this.pos, serverWorld);
        ItemStack stack = this.getStack(0);
        if (stack.isEmpty()) return;
        BoardSpaceBehaviorComponent component = stack.getOrDefault(ModComponents.BOARD_SPACE_BEHAVIOR_COMPONENT, null);
        if (component == null) return;
        List<BlockPos> destinations = component.destinations();
        persistentState.putAll(destinations, this.pos, serverWorld);
    }

    public void updateRoutedDestinations() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        BoardSpaceRoutersPersistentState persistentState = BoardSpaceRoutersPersistentState.get(serverWorld.getServer());
        persistentState.getAll().forEach((pos, routerPos) ->{
            if (!routerPos.equals(this.pos)) return;
            if (serverWorld.getBlockEntity(pos) instanceof BoardSpaceBlockEntity boardSpaceBlockEntity)
                boardSpaceBlockEntity.markDirty();
        });
    }

    @Override
    public void markRemoved() {
        if (!(this.world instanceof ServerWorld serverWorld)) {
            super.markRemoved();
            return;
        }
        BoardSpaceRoutersPersistentState persistentState = BoardSpaceRoutersPersistentState.get(serverWorld.getServer());
        persistentState.clear(this.pos, serverWorld);
        super.markRemoved();
    }
}
