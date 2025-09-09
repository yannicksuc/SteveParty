package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class GoalPoleBlockEntity extends BlockEntity {
    private GoalPoleBaseBlockEntity cachedBase;

    public GoalPoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GOAL_POLE_ENTITY, pos, state);
    }

    @Nullable
    @Override
    public BlockEntityType<?> getType() {
        return ModBlockEntities.GOAL_POLE_ENTITY;
    }

    public GoalPoleBaseBlockEntity getCachedBase() {
        if (cachedBase == null) {
            updateCachedBase();
        }
        return cachedBase;
    }

    public void updateCachedBase() {
        World world = getWorld();
        if (world == null || world.isClient) return;

        BlockPos currentPos = getPos();
        cachedBase = null;

        while (currentPos.getY() > world.getBottomY()) {
            currentPos = currentPos.down();
            BlockState state = world.getBlockState(currentPos);

            if (state.getBlock() instanceof GoalPoleBaseBlock) {
                BlockEntity be = world.getBlockEntity(currentPos);
                if (be instanceof GoalPoleBaseBlockEntity goalBase) {
                    cachedBase = goalBase;
                }
                break;
            } else if (!(state.getBlock() instanceof GoalPoleBlock)) {
                break;
            }
        }
    }

    public void propagateCachedBaseUpwards() {
        World world = getWorld();
        if (world == null || world.isClient) return;

        BlockPos currentPos = getPos().up();
        while (currentPos.getY() < world.getHeight()) {
            BlockEntity be = world.getBlockEntity(currentPos);
            if (!(be instanceof GoalPoleBlockEntity pole)) break;

            pole.updateCachedBase();
            currentPos = currentPos.up();
        }
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        // Invalidate the cache of the pole above when removed
        World world = getWorld();
        if (world == null || world.isClient) return;

        BlockEntity above = world.getBlockEntity(getPos().up());
        if (above instanceof GoalPoleBlockEntity poleAbove) {
            poleAbove.updateCachedBase();
        }
    }
}
