package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class SimpleTileBlockEntity extends BoardSpaceBlockEntity {
    public SimpleTileBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state, ModBlockEntities.SIMPLE_TILE_ENTITY, 1);
    }
}
