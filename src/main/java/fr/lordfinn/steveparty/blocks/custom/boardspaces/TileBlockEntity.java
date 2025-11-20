package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

public class TileBlockEntity extends BoardSpaceBlockEntity {
    public TileBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state, ModBlockEntities.TILE_ENTITY, 16);
    }
}
