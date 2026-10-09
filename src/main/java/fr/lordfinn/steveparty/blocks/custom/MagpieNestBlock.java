package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * The Pie's nest, a decorative block of woven twigs. Set near a Common pot space (within
 * {@link fr.lordfinn.steveparty.service.CommonPots#NEST_RADIUS} blocks), it becomes that pot's nest: the Pie lives on
 * it and the coins of the pot show in it ({@link #COINS}: empty, a few, a pile, a heap). On its own it is only a nest.
 */
public class MagpieNestBlock extends Block {
    public static final MapCodec<MagpieNestBlock> CODEC = createCodec(MagpieNestBlock::new);
    /** How full of coins it looks: 0 empty, 1 a few, 2 a pile, 3 a heap (see CommonPots#nestLevel). */
    public static final IntProperty COINS = IntProperty.of("coins", 0, 3);
    /** The height of its rim, in pixels: where the Pie stands. */
    public static final int HEIGHT = 5;
    private static final VoxelShape SHAPE = Block.createCuboidShape(1, 0, 1, 15, HEIGHT, 15);

    public MagpieNestBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(COINS, 0));
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(COINS);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }
}
