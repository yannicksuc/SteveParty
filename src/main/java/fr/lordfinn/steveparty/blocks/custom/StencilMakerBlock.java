package fr.lordfinn.steveparty.blocks.custom;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

public class StencilMakerBlock extends Block {
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
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

}
