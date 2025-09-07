package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;

public class GoalPoleBaseBlock extends HorizontalFacingBlock implements BlockEntityProvider {

    public static final BooleanProperty POWERED = Properties.POWERED; // add powered property
    VoxelShape SHAPE = makeShape();

    public GoalPoleBaseBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(POWERED, false)); // default not powered
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState()
                .with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected MapCodec<GoalPoleBaseBlock> getCodec() {
        return createCodec(GoalPoleBaseBlock::new);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new GoalPoleBaseBlockEntity(pos, state);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        ItemStack mainHandStack = player.getMainHandStack();

        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof GoalPoleBaseBlockEntity goalPoleBaseBlockEntity) {
                if (mainHandStack.getItem() instanceof WrenchItem) {
                    goalPoleBaseBlockEntity.openScreen((ServerPlayerEntity) player);
                } else {
                    // Show action bar message
                    player.sendMessage(
                            Text.translatable("message.steveparty.wrench_required").formatted(Formatting.GOLD),
                            true // action bar
                    );
                }
            }
        }

        return ActionResult.SUCCESS;
    }

    @Override
    protected void neighborUpdate(
            BlockState state,
            World world,
            BlockPos pos,
            Block sourceBlock,
            @Nullable WireOrientation wireOrientation,
            boolean notify
    ) {
        if (world.isClient) return;

        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof GoalPoleBaseBlockEntity goalPoleBaseBlockEntity)) {
            return;
        }

        // Determine powering side (south relative to facing)
        Direction facing = state.get(FACING);
        Direction poweringSide = facing.rotateYClockwise().rotateYClockwise();

        // Check redstone on powering side → pause/resume logic
        boolean isPowered = isReceivingPowerFromSouth(world, pos, state);
        if (state.get(POWERED) != isPowered) {
            world.setBlockState(pos, state.with(POWERED, isPowered), 3);

            if (isPowered) {
                goalPoleBaseBlockEntity.resumeGoal();
            } else {
                goalPoleBaseBlockEntity.pauseGoal();
            }
            return;
        }

        // Otherwise → if neighbor update is not the powering side, treat as reset
        // (safer to check actual redstone power too, so you only reset on impulse)
        for (Direction dir : Direction.values()) {
            if (dir == poweringSide) continue;
            if (world.getEmittedRedstonePower(pos.offset(dir), dir.getOpposite()) > 0) {
                goalPoleBaseBlockEntity.resetGoal();
                break;
            }
        }
    }



    private boolean isReceivingPowerFromSouth(World world, BlockPos pos, BlockState state) {
        Direction facing = state.get(FACING);
        Direction southRelative = facing.rotateYClockwise().rotateYClockwise();
        BlockPos checkPos = pos.offset(southRelative);
        return world.getEmittedRedstonePower(checkPos, southRelative.getOpposite()) > 0;
    }

    public static VoxelShape makeShape() {
        return VoxelShapes.union(
                VoxelShapes.cuboid(0.125, 0.125, 0.125, 0.875, 0.4375, 0.875),
                VoxelShapes.cuboid(0, 0.4375, 0, 1, 0.6875, 1),
                VoxelShapes.cuboid(0, 0.6875, 0.125, 0.125, 0.875, 1),
                VoxelShapes.cuboid(0, 0.6875, 0, 0.875, 0.875, 0.125),
                VoxelShapes.cuboid(0.875, 0.6875, 0, 1, 0.875, 0.875),
                VoxelShapes.cuboid(0.125, 0.6875, 0.875, 1, 0.875, 1),
                VoxelShapes.cuboid(0, 0, 0, 1, 0.125, 1)
        );
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof GoalPoleBaseBlockEntity entity) {
                entity.removeGoal();
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (type == ModBlockEntities.GOAL_POLE_BASE_ENTITY) {
            return (world1, pos, state1, blockEntity) -> ((GoalPoleBaseBlockEntity) blockEntity).tick(world1, pos, state1, blockEntity);
        }
        return null;
    }
    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof GoalPoleBaseBlockEntity entity) {
            return entity.getComparatorOutput();
        }
        return 0;
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof GoalPoleBaseBlockEntity entity) {
            if (entity.getComparatorOutput() != 0) {
                entity.setRedstoneOutput(0);
                world.updateComparators(pos, this);
            }
        }
    }
}
