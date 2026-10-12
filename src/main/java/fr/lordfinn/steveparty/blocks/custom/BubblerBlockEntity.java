package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** A Bubbler's block entity: it holds nothing, it is there for its tiny bubbles to be drawn (BubblerRenderer). */
public class BubblerBlockEntity extends BlockEntity {
    public BubblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BUBBLER_ENTITY, pos, state);
    }
}
