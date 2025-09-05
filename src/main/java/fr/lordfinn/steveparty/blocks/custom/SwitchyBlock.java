package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import static fr.lordfinn.steveparty.sounds.ModSounds.POP_SOUND_EVENT;

public class SwitchyBlock extends Block {
    public static final BooleanProperty SOLID = BooleanProperty.of("solid");
    public static final MapCodec<SwitchyBlock> CODEC = Block.createCodec(SwitchyBlock::new);

    private static final VoxelShape FULL_SHAPE = Block.createCuboidShape(0, 0, 0, 16, 16, 16);
    private static final VoxelShape SMALL_CENTER_SHAPE = Block.createCuboidShape(7.0, 7.0, 7.0, 9.0, 9.0, 9.0);


    public SwitchyBlock(Settings settings) {
        super(settings);
        // default state: solid = true
        this.setDefaultState(this.stateManager.getDefaultState().with(SOLID, true));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(SOLID);
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    // Outline (selection) shape
    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(SOLID) ? FULL_SHAPE : SMALL_CENTER_SHAPE;
    }

    // Collision shape
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, net.minecraft.util.math.BlockPos pos, ShapeContext context) {
        return state.get(SOLID) ? FULL_SHAPE : VoxelShapes.empty();
    }

    public void trigger(BlockState state, World world, BlockPos pos) {
        setSolid(state, world, pos, !state.get(SOLID));
    }

    public void setSolid(BlockState state, World world, BlockPos pos, boolean solid) {
        BlockState newState = state.with(SOLID, solid);
        world.setBlockState(pos, newState, Block.NOTIFY_ALL);
        world.playSound(null, pos, POP_SOUND_EVENT, SoundCategory.BLOCKS, 0.4f, 0.4f);
    }
}
