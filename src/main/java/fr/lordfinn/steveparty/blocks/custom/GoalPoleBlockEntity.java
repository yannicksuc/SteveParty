package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class GoalPoleBlockEntity extends BlockEntity {

    public GoalPoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GOAL_POLE_ENTITY, pos, state); // Replace with your BlockEntityType
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
    }

    @Nullable
    @Override
    public BlockEntityType<?> getType() {
        return ModBlockEntities.GOAL_POLE_ENTITY; // your BlockEntityType reference
    }
}
