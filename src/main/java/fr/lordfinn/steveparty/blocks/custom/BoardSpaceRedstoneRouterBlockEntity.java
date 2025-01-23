package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class BoardSpaceRedstoneRouterBlockEntity extends CartridgeContainerBlockEntity {
    public BoardSpaceRedstoneRouterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOARD_SPACE_REDSTONE_ROUTER_ENTITY, pos, state, 16);  //TODO implement for size of 1
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.steveparty.board_space_redstone_router");
    }
}
