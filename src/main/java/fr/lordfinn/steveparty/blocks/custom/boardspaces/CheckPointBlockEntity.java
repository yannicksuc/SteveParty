package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class CheckPointBlockEntity extends BoardSpaceBlockEntity {
    public CheckPointBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state, ModBlockEntities.CHECK_POINT_ENTITY, 16);
    }
}
