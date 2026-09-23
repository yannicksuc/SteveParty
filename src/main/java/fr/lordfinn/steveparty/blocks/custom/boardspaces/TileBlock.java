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
        return this.getDefaultState()
                .with(ROTATION_8, rotation8FromYaw(ctx.getPlayerYaw()));
    }

    /** Converts a player yaw into 8 steps of 45° (0 = placed while looking north, same convention as signs). */
    public static int rotation8FromYaw(float yaw) {
        return Math.floorMod((int)Math.floor((yaw + 202.5F) / 45.0F), 8);
    }

    public static BlockState rotate8(BlockState state, BlockRotation rotation) {
        return state.with(ROTATION_8, rotation.rotate(state.get(ROTATION_8), 8)); // 90° = +2 steps
    }

    public static BlockState mirror8(BlockState state, BlockMirror mirror) {
        return state.with(ROTATION_8, mirror.mirror(state.get(ROTATION_8), 8)); // NONE = identity
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return rotate8(state, rotation);
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return mirror8(state, mirror);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TileBlockEntity(pos, state);
    }
}
