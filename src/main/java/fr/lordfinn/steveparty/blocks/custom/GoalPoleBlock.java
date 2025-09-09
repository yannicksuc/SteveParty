package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.items.custom.FlagItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;

public class GoalPoleBlock extends HorizontalFacingBlock {

    public static final BooleanProperty FLAG = BooleanProperty.of("flag");
    public static final BooleanProperty ON_BASE = BooleanProperty.of("on_base");
    public static final BooleanProperty TOP = BooleanProperty.of("top");

    private static final VoxelShape POLE_SHAPE = Block.createCuboidShape(
            6.5, 0.0, 6.5,   // minX, minY, minZ
            9.5, 16.0, 9.5   // maxX, maxY, maxZ
    );
    private static final VoxelShape POLE_SHAPE_TOP =
    VoxelShapes.union(
            VoxelShapes.cuboid(0.40625, 0, 0.40625, 0.59375, 1, 0.59375),
            VoxelShapes.cuboid(0.3125, 0.84375, 0.3125, 0.6875, 1, 0.6875)
            );

    public GoalPoleBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(FLAG, false)
                .with(ON_BASE, false)
                .with(TOP, true));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, FLAG, ON_BASE, TOP);
    }

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        boolean isOnBase = isOnBase(ctx.getWorld(), ctx.getBlockPos());
        boolean isTop = isTop(ctx.getWorld(), ctx.getBlockPos());
        return this.getDefaultState()
                .with(FACING, ctx.getHorizontalPlayerFacing().getOpposite())
                .with(FLAG, false)
                .with(ON_BASE, isOnBase)
                .with(TOP, isTop);
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock,
                                  @Nullable WireOrientation wireOrientation, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, wireOrientation, notify);
        updateOnBaseProperty(state, world, pos);
        updateTopProperty(state, world, pos);
    }

    private boolean isOnBase(World world, BlockPos pos) {
        BlockState belowState = world.getBlockState(pos.down());
        return belowState.getBlock() instanceof GoalPoleBaseBlock;
    }

    private boolean isTop(World world, BlockPos pos) {
        BlockState aboveState = world.getBlockState(pos.up());
        return !(aboveState.getBlock() instanceof GoalPoleBlock);
    }

    private void updateOnBaseProperty(BlockState state, World world, BlockPos pos) {
        boolean isOnBase = isOnBase(world, pos);
        if (state.get(ON_BASE) != isOnBase) {
            world.setBlockState(pos, state.with(ON_BASE, isOnBase));
        }
    }

    private void updateTopProperty(BlockState state, World world, BlockPos pos) {
        boolean isTop = isTop(world, pos);
        if (state.get(TOP) != isTop) {
            world.setBlockState(pos, state.with(TOP, isTop));
        }
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(TOP) ? POLE_SHAPE_TOP : POLE_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return POLE_SHAPE;
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return createCodec(GoalPoleBlock::new);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        ItemStack stack = player.getStackInHand(Hand.MAIN_HAND);
        if (!world.isClient) {
            if (stack.getItem() instanceof FlagItem) {
                if (!state.get(FLAG)) {
                    Direction hitSide = hit.getSide();
                    if (hitSide != Direction.UP && hitSide != Direction.DOWN)
                        world.setBlockState(pos, state.with(FLAG, true).with(FACING, hit.getSide().rotateYClockwise()), 3);
                    else
                        world.setBlockState(pos, state.with(FLAG, true));
                    if (!player.isCreative()) {
                        stack.decrement(1);
                    }
                } else {
                    world.setBlockState(pos, state.with(FACING, hit.getSide().rotateYClockwise()), 3);
                }
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }


    @Override
    public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
        return 1.0f;
    }

    @Override
    protected boolean isTransparent(BlockState state) {
        return true;
    }
}
