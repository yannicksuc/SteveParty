package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
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
        BlockState state = this.getDefaultState()
                .with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
        // Placed against a signal: paused from the start (neighbor updates only see later changes)
        return state.with(POWERED, ctx.getWorld().getReceivedRedstonePower(ctx.getBlockPos()) > 0);
    }

    /** Placed by a player: the base follows the party's players when a party controller is near, else everyone. */
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable net.minecraft.entity.LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient && placer instanceof PlayerEntity && world.getBlockEntity(pos) instanceof GoalPoleBaseBlockEntity base) {
            base.onPlacedByPlayer();
        }
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
        boolean page = mainHandStack.getItem() instanceof fr.lordfinn.steveparty.items.custom.MiniGamePageItem;
        // Same result as the server on the client (arm swing, no item use behind the screen)
        if (world.isClient) return mainHandStack.getItem() instanceof WrenchItem || page ? ActionResult.SUCCESS : ActionResult.PASS;
        // A mini-game page: the base is one of its counters (reset with its podiums, its goals give their places)
        if (page) {
            fr.lordfinn.steveparty.podium.Podiums.clickLink((ServerPlayerEntity) player, net.minecraft.util.Hand.MAIN_HAND, (ServerWorld) world, pos,
                    fr.lordfinn.steveparty.minigame.MiniGamePodiumLink.Kind.COUNTER);
            return ActionResult.SUCCESS;
        }

        if (world.getBlockEntity(pos) instanceof GoalPoleBaseBlockEntity goalPoleBaseBlockEntity) {
            if (mainHandStack.getItem() instanceof WrenchItem) {
                goalPoleBaseBlockEntity.openScreen((ServerPlayerEntity) player);
                return ActionResult.SUCCESS;
            } else if (!(mainHandStack.getItem() instanceof BlockItem)) {
                // Show action bar message (not while building on the base, e.g. placing the pole on it)
                player.sendMessage(
                        Text.translatable("message.steveparty.wrench_required").formatted(Formatting.GOLD),
                        true // action bar
                );
            }
        }

        return ActionResult.PASS;
    }

    @Override
    protected void neighborUpdate(
            BlockState state,
            World world,
            BlockPos pos,
            Block sourceBlock,
            BlockPos sourcePos,
            boolean notify
    ) {
        if (world.isClient) return;

        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof GoalPoleBaseBlockEntity goalPoleBaseBlockEntity)) {
            return;
        }

        // A signal from any side: 1 to 14 pauses the base, 15 pauses it and puts the points back to 0
        int power = world.getReceivedRedstonePower(pos);
        if (state.get(POWERED) != power > 0) world.setBlockState(pos, state.with(POWERED, power > 0), 3);
        goalPoleBaseBlockEntity.onInputPower(power);
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
                entity.onBroken();
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
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
