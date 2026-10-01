package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** The Telescope's block entity: it holds nothing, it is only there to be drawn (tripod and tube). */
public class TelescopeBlockEntity extends BlockEntity {
    public TelescopeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TELESCOPE_ENTITY, pos, state);
    }
}
