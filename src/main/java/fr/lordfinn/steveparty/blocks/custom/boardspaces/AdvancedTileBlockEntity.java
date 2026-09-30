package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

public class AdvancedTileBlockEntity extends BoardSpaceBlockEntity {
    public AdvancedTileBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state, ModBlockEntities.ADVANCED_TILE_ENTITY, 16);
    }
}
