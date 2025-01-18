package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class StepControllerBlock extends BlockWithEntity {
    public static final MapCodec<StepControllerBlock> CODEC = Block.createCodec(StepControllerBlock::new);
    public static final VoxelShape SHAPE_STRAIGHT = VoxelShapes.union(
            VoxelShapes.cuboid(0, 0.625, 0, 1, 0.75, 1),
            VoxelShapes.cuboid(0.3125, 0.25, 0.3125, 0.6875, 1.125, 0.6875),
            VoxelShapes.cuboid(0, 0, 0, 1, 0.125, 1)
    );
    public static final VoxelShape SHAPE_SIDE = VoxelShapes.union(
            VoxelShapes.cuboid(0.4375, 0.1875, 0, 0.5625, 1.1875, 1),
            VoxelShapes.cuboid(0.0625, 0.5, 0.3125, 0.9375, 0.875, 0.6875),
            VoxelShapes.cuboid(0, 0, 0, 1, 0.125, 1)
    );
    public StepControllerBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (world.getBlockEntity(pos) instanceof StepControllerBlockEntity blockEntity && blockEntity.mode == 1) {
            return SHAPE_SIDE;
        }
        return SHAPE_STRAIGHT;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return TickableBlockEntity.getTicker(world);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new StepControllerBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.PASS;
        StepControllerBlockEntity blockEntity = (StepControllerBlockEntity) world.getBlockEntity(pos);
        if (blockEntity != null) blockEntity.cycleMode();
        return ActionResult.SUCCESS;
    }
}
