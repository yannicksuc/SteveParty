package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

public class TileBlock extends ABoardSpaceBlock {

    public static final MapCodec<TileBlock> CODEC = Block.createCodec(TileBlock::new);

    private static final VoxelShape SHAPE =
            Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 2.0, 16.0);

    public static final IntProperty ROTATION_8 = IntProperty.of("rotation_8", 0, 7);

    public TileBlock(Settings settings) {
        super(settings.nonOpaque(), 16);

        setDefaultState(
                this.stateManager.getDefaultState()
                        .with(ROTATION_8, 0)                // nouvelle rotation
        );
    }

    @Override
    protected MapCodec<? extends TileBlock> getCodec() {
        return CODEC;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(ROTATION_8);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {

        float yaw = ctx.getPlayerYaw();

        // Convert yaw → 8 steps of 45°
        int rot8 = Math.floorMod((int)Math.floor((yaw + 202.5F) / 45.0F), 8);

        return this.getDefaultState()
                .with(ROTATION_8, rot8);
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        int value = state.get(ROTATION_8);
        int rotated = (value + rotation.ordinal() * 2) & 7; // 90° = +2 steps
        return state.with(ROTATION_8, rotated);
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        int value = state.get(ROTATION_8);
        int mirrored = (8 - value) & 7;
        return state.with(ROTATION_8, mirrored);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TileBlockEntity(pos, state);
    }
}
