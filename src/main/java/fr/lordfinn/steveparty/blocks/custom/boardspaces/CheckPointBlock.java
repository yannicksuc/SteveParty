package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

public class CheckPointBlock extends ABoardSpaceBlock {
    public static final MapCodec<CheckPointBlock> CODEC = Block.createCodec(CheckPointBlock::new);
    public CheckPointBlock(Settings settings) {
        super(settings.sounds(BlockSoundGroup.GLASS).nonOpaque().luminance(state -> 7), 16);
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.cuboid(0.34375, 0.15625, 0.34375, 0.65625, 0.46875, 0.65625);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty(); // No collision shape
    }

    @Override
    protected boolean isTransparent(BlockState state) {
        return true;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CheckPointBlockEntity(pos, state);
    }
}
