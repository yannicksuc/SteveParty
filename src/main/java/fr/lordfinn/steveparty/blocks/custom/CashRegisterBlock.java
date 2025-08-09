package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.utils.VoxelShapeUtils;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CashRegisterBlock extends HorizontalFacingBlock implements BlockEntityProvider {

    private static final BooleanProperty POWERED = BooleanProperty.of("powered");
    private static final MapCodec<CashRegisterBlock> CODEC = Block.createCodec(CashRegisterBlock::new);
    private static final Map<Direction, VoxelShape> SHAPES = new HashMap<>();

    public CashRegisterBlock(Settings settings) {
        super(settings.nonOpaque().strength(1.0f));
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(POWERED, false)
                .with(Properties.HORIZONTAL_FACING, Direction.NORTH));
        setupShapes();
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(Properties.HORIZONTAL_FACING);
        builder.add(POWERED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return Objects.requireNonNull(super.getPlacementState(ctx))
                .with(Properties.HORIZONTAL_FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (player instanceof ServerPlayerEntity serverPlayer && blockEntity instanceof CashRegisterBlockEntity cashRegisterBlockEntity) {
                serverPlayer.openHandledScreen(cashRegisterBlockEntity);
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }

    public void setPowered(World world, BlockPos pos, boolean powered) {
        BlockState state = world.getBlockState(pos);
        if (state.get(POWERED) != powered) {
            world.setBlockState(pos, state.with(POWERED, powered), Block.NOTIFY_ALL);
            if (powered) {
                world.scheduleBlockTick(pos, this, 4); // 20 ticks = 1 second
            }
        }
    }

    @Override
    public boolean emitsRedstonePower(BlockState state) {
        return true;
    }

    @Override
    public int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return state.get(POWERED) ? 15 : 0;
    }

    @Override
    public int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return state.get(POWERED) ? 15 : 0;
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, net.minecraft.util.math.random.Random random) {
        super.scheduledTick(state, world, pos, random);
        if (state.get(POWERED)) {
            setPowered(world, pos, false);
        }
    }

    public static VoxelShape makeShape() {
        return VoxelShapes.union(
                VoxelShapes.cuboid(0.0625, 0, 0.0625, 0.9375, 0.3125, 0.9375),
                VoxelShapes.cuboid(0.0625, 0.3125, 0.0625, 0.9375, 0.625, 0.375),
                VoxelShapes.cuboid(0.625, 0.334375, 0.25, 0.8125, 0.584375, 0.5)
        );
    }

    private void setupShapes() {
        Box[] boxes = VoxelShapeUtils.shapeToBoxes(makeShape());
        SHAPES.put(Direction.SOUTH, makeShape());
        SHAPES.put(Direction.EAST, VoxelShapeUtils.shape(VoxelShapeUtils.rotate(90, boxes)));
        SHAPES.put(Direction.NORTH, VoxelShapeUtils.shape(VoxelShapeUtils.rotate(180, boxes)));
        SHAPES.put(Direction.WEST, VoxelShapeUtils.shape(VoxelShapeUtils.rotate(270, boxes)));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES.get(state.get(FACING));
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CashRegisterBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
