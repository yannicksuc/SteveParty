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
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import org.jetbrains.annotations.Nullable;

/**
 * A free-standing sign taking stencils: 16 orientations like a standing vanilla sign, waterloggable, drawn in the
 * chunk mesh (turned by the client model, see {@code StencilSignModels}) with its symbol drawn by the block entity
 * renderer. Everything painted on it, and what it is made of, is kept by its item when broken.
 * <p>
 * Put against the side of a fence or a wall ({@link SignPosts}), a sign faces away from it: signs made to stand on a
 * post ({@link #hangsOnPosts()}) are then {@link #HUNG} on that post, drawn around it as if they stood on it; the
 * others stand on the ground in front of it, their back against it.
 */
public abstract class AbstractStencilSignBlock extends BlockWithEntity implements Waterloggable, StencilCanvasBlock {
    public static final IntProperty ROTATION = Properties.ROTATION;
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
    /** Hung on the post (fence or wall) right behind it instead of standing: see {@link #hungFacing(BlockState)}. */
    public static final BooleanProperty HUNG = BooleanProperty.of("hung");

    protected AbstractStencilSignBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(ROTATION, 0).with(WATERLOGGED, false).with(HUNG, false));
    }

    /** @return whether this sign is made to stand on a post, so that it can also hang on the side of one. */
    public boolean hangsOnPosts() {
        return false;
    }

    /**
     * @return the side a hung sign faces (the post it hangs on is right behind it, on the opposite side), null for a
     * standing sign
     */
    public static @Nullable Direction hungFacing(BlockState state) {
        if (!state.contains(HUNG) || !state.get(HUNG)) return null;
        return facing(state.get(ROTATION));
    }

    /**
     * @return the side a sign with this rotation faces, null between two sides. The inverse of
     * {@link RotationPropertyHelper#fromDirection}, which {@link RotationPropertyHelper#toDirection} is not
     */
    public static @Nullable Direction facing(int rotation) {
        return rotation % 4 == 0 ? Direction.fromHorizontal(rotation / 4) : null;
    }

    /**
     * @return model z (pixels, front facing north) of the back of this sign's board, which is glued against the post
     * it stands or hangs on; NaN for a sign without a board on a post
     */
    public float boardBack() {
        return Float.NaN;
    }

    /**
     * @return how far (model pixels, towards the back) the board of the sign at {@code pos} is moved to rest right
     * against its post, whatever post it is (a thin fence, a thick wall, seen square or at an angle)
     */
    public double boardShift(BlockView world, BlockPos pos, BlockState state) {
        float back = boardBack();
        if (Float.isNaN(back)) return 0;
        double reach = SignPosts.reach(world, pos, state);
        return Double.isNaN(reach) ? 0 : 8 - reach - back;
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
        builder.add(ROTATION, WATERLOGGED, HUNG);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState state = this.getDefaultState()
                .with(ROTATION, RotationPropertyHelper.fromYaw(ctx.getPlayerYaw() + 180.0F))
                .with(WATERLOGGED, ctx.getWorld().getFluidState(ctx.getBlockPos()).getFluid() == Fluids.WATER);
        Direction side = ctx.getSide();
        if (side.getAxis().isHorizontal() && !ctx.canReplaceExisting()
                && SignPosts.isPost(ctx.getWorld().getBlockState(ctx.getBlockPos().offset(side.getOpposite())))) {
            // Put against a fence or a wall: facing away from it, hung on it when made for posts
            state = state.with(ROTATION, RotationPropertyHelper.fromDirection(side)).with(HUNG, hangsOnPosts());
        }
        return state;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    protected final boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        Direction hung = hungFacing(state);
        if (hung != null) return SignPosts.isPost(world.getBlockState(pos.offset(hung.getOpposite())));
        return canStandAt(world, pos);
    }

    /** @return whether this sign can stand at {@code pos} (on what is below it). */
    protected boolean canStandAt(WorldView world, BlockPos pos) {
        return world.getBlockState(pos.down()).isSolid();
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos,
                                                   Direction direction, BlockPos neighborPos, BlockState neighborState, Random random) {
        Direction hung = hungFacing(state);
        Direction support = hung != null ? hung.getOpposite() : Direction.DOWN;
        if (direction == support && !this.canPlaceAt(state, world, pos)) return Blocks.AIR.getDefaultState();
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
