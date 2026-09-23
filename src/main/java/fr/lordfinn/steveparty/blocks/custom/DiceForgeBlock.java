package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;


public class DiceForgeBlock extends BlockWithEntity {
    public static final MapCodec<DiceForgeBlock> CODEC = Block.createCodec(DiceForgeBlock::new);
    public static final VoxelShape SHAPE = VoxelShapes.union(
            VoxelShapes.cuboid(0.4375, 0.25, 0.4375, 0.5625, 0.4375, 0.5625),
            VoxelShapes.cuboid(0.3125, 0.4375, 0.3125, 0.6875, 0.625, 0.6875),
            VoxelShapes.cuboid(0, 0.625, 0, 1, 0.75, 1),
            VoxelShapes.cuboid(0, 0.75, 0, 0.25, 0.875, 1),
            VoxelShapes.cuboid(0.75, 0.75, 0, 1, 0.875, 1),
            VoxelShapes.cuboid(0.25, 0.75, 0, 0.75, 0.875, 0.25),
            VoxelShapes.cuboid(0.25, 0.75, 0.75, 0.75, 0.875, 1),
            VoxelShapes.cuboid(0.46875, 0.0625, 0.46875, 0.53125, 0.25, 0.53125)
            );
    public static final BooleanProperty ACTIVATED = BooleanProperty.of("activated");

    public DiceForgeBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(ACTIVATED, false));
    }

    @Override
    protected MapCodec<DiceForgeBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ACTIVATED);
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
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return TickableBlockEntity.getTicker(world);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new DiceForgeBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        // Right-clicking with the gravity (heavy) core inserts it in the forge hole and activates the forge
        if (!DiceForgeBlockEntity.isGravityCore(stack) || state.get(ACTIVATED)) {
            return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION;
        }
        if (world.isClient) return ActionResult.SUCCESS;
        if (world.getBlockEntity(pos) instanceof DiceForgeBlockEntity blockEntity) {
            blockEntity.activate();
            stack.decrementUnlessCreative(1, player);
            return ActionResult.SUCCESS_SERVER;
        }
        return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (world.getBlockEntity(pos) instanceof DiceForgeBlockEntity diceForgeBlockEntity) {
            player.openHandledScreen(diceForgeBlockEntity);
        }
        return ActionResult.SUCCESS_SERVER;
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, @Nullable WireOrientation wireOrientation, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, wireOrientation, notify);
        updatePower(world, pos);
    }

    /** Redstone: powered = production enabled (rising edge starts, falling edge stops). */
    private static void updatePower(World world, BlockPos pos) {
        if (world.isClient) return;
        if (world.getBlockEntity(pos) instanceof DiceForgeBlockEntity blockEntity) {
            blockEntity.onRedstoneChanged(world.isReceivingRedstonePower(pos));
        }
    }

    @Override
    protected boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    /** Comparator: fill level of the die output slot. */
    @Override
    protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof DiceForgeBlockEntity blockEntity ? blockEntity.getComparatorOutput() : 0;
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        // Drop the forge contents when the block is broken (ACTIVATED changes keep the same block):
        // inventory + the inserted gravity core + items waiting to be given back (legacy power star)
        if (!state.isOf(newState.getBlock()) && !world.isClient
                && world.getBlockEntity(pos) instanceof DiceForgeBlockEntity blockEntity) {
            for (ItemStack drop : blockEntity.getExtraDrops()) {
                ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), drop);
            }
        }
        ItemScatterer.onStateReplaced(state, newState, world, pos);
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
