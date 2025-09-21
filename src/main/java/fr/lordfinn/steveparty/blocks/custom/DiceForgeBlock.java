package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.mob.PiglinBrain;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import static fr.lordfinn.steveparty.blocks.ModBlocks.GRAVITY_CORE;

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

    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        ItemStack itemStack = player.getStackInHand(Hand.MAIN_HAND);

        if (world.isClient) return ActionResult.PASS;
        DiceForgeBlockEntity blockEntity = (DiceForgeBlockEntity) world.getBlockEntity(pos);
        if (blockEntity != null && itemStack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof GravityCoreBlock) {
            if (!blockEntity.isActivated()) {
                blockEntity.activate();
                return ActionResult.SUCCESS;
            }
        }

        BlockEntity var8 = world.getBlockEntity(pos);
        if (var8 instanceof DiceForgeBlockEntity diceForgeBlockEntity) {
            player.openHandledScreen(diceForgeBlockEntity);
        }

        return ActionResult.SUCCESS;
    }
}
