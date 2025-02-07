package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class StencilMakerBlock extends BlockWithEntity {
     private static final VoxelShape SHAPE = VoxelShapes.union(
             VoxelShapes.cuboid(0, 0.5, 0, 1, 0.875, 1),
             VoxelShapes.cuboid(-0.125, 0.8125, -0.125, 1.125, 0.9375, 0),
             VoxelShapes.cuboid(-0.125, 0.8125, 1, 1.125, 0.9375, 1.125),
             VoxelShapes.cuboid(-0.125, 0.8125, 0, 0, 0.9375, 1),
             VoxelShapes.cuboid(1, 0.8125, 0, 1.125, 0.9375, 1),
             VoxelShapes.cuboid(0.3125, 0.125, 0.3125, 0.6875, 0.5, 0.6875),
             VoxelShapes.cuboid(0.1875, 0, 0.1875, 0.8125, 0.125, 0.8125)
             );

    public StencilMakerBlock(Settings settings) {
        super(settings);
    }


    @Override
    protected MapCodec<? extends StencilMakerBlock> getCodec() {
        return createCodec(StencilMakerBlock::new);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new StencilMakerBlockEntity(pos, state);
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient || hand.equals(Hand.OFF_HAND)) return ActionResult.PASS;

        StencilMakerBlockEntity blockEntity = (StencilMakerBlockEntity) world.getBlockEntity(pos);
        if (blockEntity == null) return ActionResult.PASS;

        // Check if the stencil is empty
        if (blockEntity.getStencil().isEmpty()) {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.openHandledScreen(blockEntity);
            }
            return ActionResult.SUCCESS;
        }

        // If the player is holding a stencil item, set it
        if (stack.isEmpty() || stack.getItem() instanceof StencilItem) {
            blockEntity.swapStencil(stack.copyAndEmpty(), player);
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }
}
