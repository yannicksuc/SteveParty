package fr.lordfinn.steveparty.blocks;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.items.MiniGamesCatalogue;
import fr.lordfinn.steveparty.utils.VoxelShapeutils;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class PartyController extends HorizontalFacingBlock implements BlockEntityProvider {
    public static final BooleanProperty POWERED = Properties.POWERED;
    public static final BooleanProperty CATALOGUED = BooleanProperty.of("catalogued");

    public static final MapCodec<PartyController> CODEC = Block.createCodec(PartyController::new);

    public static final Map<Direction, VoxelShape> SHAPES = new HashMap<>();
    public static final VoxelShape SHAPE = VoxelShapes.union(
            VoxelShapes.cuboid(0, 0, 0, 1, 0.125, 1),
            VoxelShapes.cuboid(0.25, 0.125, 0.25, 0.75, 0.5, 0.75),
            VoxelShapes.cuboid(0.25, 0.6875, 0.125, 0.875, 1.0625, 0.3125),
            VoxelShapes.cuboid(0.125, 0.8125, 0.25, 0.25, 1.4375, 0.375),
            VoxelShapes.cuboid(0.0625, 0.1875, 0.6875, 0.9375, 0.75, 0.9375),
            VoxelShapes.cuboid(0.0625, 0.3125, 0.4375, 0.9375, 0.875, 0.6875),
            VoxelShapes.cuboid(0.0625, 0.4375, 0.1875, 0.9375, 1, 0.4375),
            VoxelShapes.cuboid(0.1875, 0.6875, 0.375, 0.8125, 0.8359375, 0.9796875)
    );

    public PartyController(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(POWERED, false)
                .with(CATALOGUED, false)
                .with(FACING, Direction.NORTH));
        setupShapes();
    }

    private void setupShapes() {
        Box[] boxes = VoxelShapeutils.shapeToBoxes(SHAPE);
        SHAPES.put(Direction.SOUTH, SHAPE);
        SHAPES.put(Direction.EAST, VoxelShapeutils.shape(VoxelShapeutils.rotate(90, boxes)));
        SHAPES.put(Direction.NORTH, VoxelShapeutils.shape(VoxelShapeutils.rotate(180, boxes)));
        SHAPES.put(Direction.WEST, VoxelShapeutils.shape(VoxelShapeutils.rotate(270, boxes)));
    }

    @Override
    protected MapCodec<PartyController> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, CATALOGUED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        boolean isWaterlogged = context.getWorld().getFluidState(context.getBlockPos()).isStill();
        boolean isCatalogued = false;
        BlockEntity entity = context.getWorld().getBlockEntity(context.getBlockPos());
        if (entity instanceof PartyControllerEntity partyEntity) {
            isCatalogued = !partyEntity.catalogue.isEmpty();
        }
        return this.getDefaultState()
                .with(FACING, context.getHorizontalPlayerFacing().getOpposite())
                .with(CATALOGUED, isCatalogued);
    }

    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        return ActionResult.SUCCESS;
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, @Nullable WireOrientation wireOrientation, boolean notify) {
        if (!world.isClient) {
            updatePoweredState(state, world, pos);
        }
    }

    private void updatePoweredState(BlockState state, World world, BlockPos pos) {
        boolean isPowered = world.isReceivingRedstonePower(pos);
        if (state.get(POWERED) != isPowered) {
            world.setBlockState(pos, state.with(POWERED, isPowered), Block.NOTIFY_ALL);
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() != newState.getBlock()) {
            PartyControllerEntity entity = (PartyControllerEntity) world.getBlockEntity(pos);
            if (entity != null) {
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, entity.catalogue);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    public int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES.get(state.get(FACING));
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PartyControllerEntity(pos, state);
    }

    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient || hand.equals(Hand.OFF_HAND)) return ActionResult.PASS;
        if (stack.getItem() instanceof MiniGamesCatalogue) {
            PartyControllerEntity entity = (PartyControllerEntity) world.getBlockEntity(pos);
            if (entity != null) {
                boolean isCatalogued = entity.setCatalogue(stack.copyAndEmpty());
                world.setBlockState(pos, state.with(PartyController.CATALOGUED, isCatalogued), Block.NOTIFY_ALL);
                return ActionResult.SUCCESS;
            }
        }
        return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
    }
}
