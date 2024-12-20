package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.utils.CashRegisterState;
import fr.lordfinn.steveparty.utils.VoxelShapeutils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CashRegister extends HorizontalFacingBlock {
    private static final BooleanProperty POWERED = BooleanProperty.of("powered");
    private static final MapCodec<CashRegister> CODEC = Block.createCodec(CashRegister::new);
    private static final Map<Direction, VoxelShape> SHAPES = new HashMap<>();

    public CashRegister(Settings settings) {
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
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            CashRegisterState cashRegisterState = CashRegisterState.get(world.getServer());
            if (cashRegisterState != null) cashRegisterState.addPosition(pos);
        }
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            CashRegisterState cashRegisterState = CashRegisterState.get(world.getServer());
            if (cashRegisterState != null) cashRegisterState.removePosition(pos);
        }
        return super.onBreak(world, pos, state, player);
    }

    public void setPowered(World world, BlockPos pos, boolean powered) {
        world.setBlockState(pos, world.getBlockState(pos).with(POWERED, powered), Block.NOTIFY_ALL);
        if (powered) {
            world.scheduleBlockTick(pos, this, 4); // 20 ticks = 1 second
        }
    }

    @Override
    protected boolean emitsRedstonePower(BlockState state) {
        return true;
    }

    @Override
    protected int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return state.get(POWERED) ? 15 : 0;
    }

    @Override
    protected int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return state.get(POWERED) ? 15 : 0;
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, net.minecraft.util.math.random.Random random) {
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
        Box[] boxes = VoxelShapeutils.shapeToBoxes(makeShape());
        SHAPES.put(Direction.SOUTH, makeShape());
        SHAPES.put(Direction.EAST, VoxelShapeutils.shape(VoxelShapeutils.rotate(90, boxes)));
        SHAPES.put(Direction.NORTH, VoxelShapeutils.shape(VoxelShapeutils.rotate(180, boxes)));
        SHAPES.put(Direction.WEST, VoxelShapeutils.shape(VoxelShapeutils.rotate(270, boxes)));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES.get(state.get(FACING));
    }
}
