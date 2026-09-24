package fr.lordfinn.steveparty.blocks.custom.signs;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationPropertyHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import org.jetbrains.annotations.Nullable;

/**
 * A free-standing sign taking stencils: 16 orientations like a standing vanilla sign, waterloggable, drawn in the
 * chunk mesh (turned by the client model, see {@code StencilSignModels}) with its symbol drawn by the block entity
 * renderer. Everything painted on it, and what it is made of, is kept by its item when broken.
 */
public abstract class AbstractStencilSignBlock extends BlockWithEntity implements Waterloggable, StencilCanvasBlock {
    public static final IntProperty ROTATION = Properties.ROTATION;
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

    protected AbstractStencilSignBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(ROTATION, 0).with(WATERLOGGED, false));
    }

    /** @return the kind of block this sign is made of, or null for a sign with a fixed look. */
    public @Nullable SignMaterial getMaterialKind() {
        return null;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new StencilCanvasBlockEntity(pos, state);
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ROTATION, WATERLOGGED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState()
                .with(ROTATION, RotationPropertyHelper.fromYaw(ctx.getPlayerYaw() + 180.0F))
                .with(WATERLOGGED, ctx.getWorld().getFluidState(ctx.getBlockPos()).getFluid() == Fluids.WATER);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return world.getBlockState(pos.down()).isSolid();
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos,
                                                   Direction direction, BlockPos neighborPos, BlockState neighborState, Random random) {
        if (direction == Direction.DOWN && !this.canPlaceAt(state, world, pos)) return Blocks.AIR.getDefaultState();
        if (state.get(WATERLOGGED)) tickView.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        return super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public boolean canMobSpawnInside(BlockState state) {
        return false;
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(ROTATION, rotation.rotate(state.get(ROTATION), 16));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.with(ROTATION, mirror.mirror(state.get(ROTATION), 16));
    }

    public static float getRotationDegrees(BlockState state) {
        return RotationPropertyHelper.toDegrees(state.get(ROTATION));
    }

    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
                                         Hand hand, BlockHitResult hit) {
        return StencilInteractions.onUseWithItem(state, world, pos, player, hand);
    }

    /** Middle click gives this sign made of the same material (the symbol itself needs ctrl + middle click). */
    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
        ItemStack stack = super.getPickStack(world, pos, state);
        if (world.getBlockEntity(pos) instanceof StencilCanvasBlockEntity canvas) {
            MaterialSignItems.copyLook(canvas, stack);
        }
        return stack;
    }
}
