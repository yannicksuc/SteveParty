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
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.StringIdentifiable;
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
import org.joml.Matrix4f;

/**
 * A free-standing sign taking stencils: 16 orientations like a standing vanilla sign, waterloggable, drawn in the
 * chunk mesh (turned by the client model, see {@code StencilSignModels}) with its symbol drawn by the block entity
 * renderer. Everything painted on it, and what it is made of, is kept by its item when broken.
 * <p>
 * Put against the side of a fence or a wall ({@link SignPosts}), a sign faces away from it: signs made to stand on a
 * post ({@link #hangsOnPosts()}) then hang on that post ({@link Mount#HUNG}), drawn around it as if they stood on it;
 * the others stand on the ground in front of it, their back against it. Signs made for posts also go flat against
 * any wall, floor or ceiling ({@link #MOUNT}).
 */
public abstract class AbstractStencilSignBlock extends BlockWithEntity implements Waterloggable, StencilCanvasBlock {
    public static final IntProperty ROTATION = Properties.ROTATION;
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
    /** What holds the sign: see {@link Mount}. */
    public static final EnumProperty<Mount> MOUNT = EnumProperty.of("mount", Mount.class);

    /** What holds a sign, and how its board is drawn (see {@link #modelTransform}). */
    public enum Mount implements StringIdentifiable {
        /** Standing (on the fence or wall below, for the signs made for posts), turned 16 ways. */
        POST("post"),
        /** Hung on the side of the post (fence or wall) right behind it, drawn around that post. */
        HUNG("hung"),
        /** Flat against the wall behind it. */
        WALL("wall"),
        /** Lying flat on the floor, face up, the top of its symbol away from where it was put from. */
        FLOOR("floor"),
        /** Flat against the ceiling, face down. */
        CEILING("ceiling");

        private final String name;

        Mount(String name) {
            this.name = name;
        }

        @Override
        public String asString() {
            return name;
        }
    }

    protected AbstractStencilSignBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(ROTATION, 0).with(WATERLOGGED, false).with(MOUNT, Mount.POST));
    }

    /**
     * @return whether this sign is made to stand on a post, so that it can also hang on the side of one, or go flat
     * against a wall, the floor or the ceiling
     */
    public boolean hangsOnPosts() {
        return false;
    }

    /**
     * @return the side a hung sign faces (the post it hangs on is right behind it, on the opposite side), null for a
     * standing sign
     */
    public static @Nullable Direction hungFacing(BlockState state) {
        if (!state.contains(MOUNT) || state.get(MOUNT) != Mount.HUNG) return null;
        return facing(state.get(ROTATION));
    }

    /** @return the side of the sign's block its support is on (the block holding it). */
    public static Direction supportSide(BlockState state) {
        Mount mount = state.contains(MOUNT) ? state.get(MOUNT) : Mount.POST;
        Direction facing = facing(state.get(ROTATION));
        return switch (mount) {
            case HUNG, WALL -> facing == null ? Direction.DOWN : facing.getOpposite();
            case CEILING -> Direction.UP;
            default -> Direction.DOWN;
        };
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

    /** @return model y (pixels) of the middle of this sign's board, which lies there on a floor or a ceiling. */
    public float boardCenterY() {
        return 8;
    }

    /**
     * @return where the sign's model (block units, front facing north) is drawn in its block: turned, moved onto
     * its post, against its wall or laid on its floor / ceiling. The client model, the symbol and the outline all
     * use it.
     */
    public Matrix4f modelTransform(BlockView world, BlockPos pos, BlockState state) {
        Matrix4f matrix = new Matrix4f();
        Mount mount = state.contains(MOUNT) ? state.get(MOUNT) : Mount.POST;
        Direction hung = hungFacing(state);
        if (hung != null) matrix.translate(-hung.getOffsetX(), 0, -hung.getOffsetZ());
        float angle = (float) Math.toRadians(SignShapes.angleDegrees(state.get(ROTATION)));
        matrix.translate(0.5F, 0, 0.5F).rotateY(angle).translate(-0.5F, 0, -0.5F);
        float back = boardBack() / 16F, middle = boardCenterY() / 16F;
        switch (mount) {
            case POST, HUNG -> {
                double shift = boardShift(world, pos, state);
                if (shift != 0) matrix.translate(0, 0, (float) shift / 16F);
            }
            // The back of the board on the face of the block behind
            case WALL -> matrix.translate(0, 0, 1 - back);
            // Face up, the top of the symbol towards the back of the sign (away from who put it)
            case FLOOR -> matrix.translate(0.5F, 0, 0.5F).rotateX((float) Math.PI / 2).translate(-0.5F, -middle, -back);
            // Face down, the top of the symbol towards the back of the sign too (read looking up from where it was put)
            case CEILING -> matrix.translate(0.5F, 1, 0.5F).rotateX((float) -Math.PI / 2).translate(-0.5F, -middle, -back);
        }
        return matrix;
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
        builder.add(ROTATION, WATERLOGGED, MOUNT);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState state = this.getDefaultState()
                .with(ROTATION, RotationPropertyHelper.fromYaw(ctx.getPlayerYaw() + 180.0F))
                .with(WATERLOGGED, ctx.getWorld().getFluidState(ctx.getBlockPos()).getFluid() == Fluids.WATER);
        if (ctx.canReplaceExisting()) return state;
        Direction side = ctx.getSide();
        BlockPos supportPos = ctx.getBlockPos().offset(side.getOpposite());
        BlockState support = ctx.getWorld().getBlockState(supportPos);
        if (side.getAxis().isHorizontal() && SignPosts.isPost(support)) {
            // Put against a fence or a wall: facing away from it, hung on it when made for posts
            return state.with(ROTATION, RotationPropertyHelper.fromDirection(side)).with(MOUNT, hangsOnPosts() ? Mount.HUNG : Mount.POST);
        }
        if (!hangsOnPosts() || (side == Direction.UP && SignPosts.isPost(support))) return state;
        if (!support.isSideSolid(ctx.getWorld(), supportPos, side, SideShapeType.CENTER)) return state;
        // Flat against the face clicked
        return switch (side) {
            case UP -> state.with(MOUNT, Mount.FLOOR);
            case DOWN -> state.with(MOUNT, Mount.CEILING);
            default -> state.with(ROTATION, RotationPropertyHelper.fromDirection(side)).with(MOUNT, Mount.WALL);
        };
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    protected final boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        if (!needsSupport(state)) return true;
        Mount mount = state.get(MOUNT);
        if (mount == Mount.POST) return canStandAt(world, pos);
        Direction side = supportSide(state);
        BlockPos supportPos = pos.offset(side);
        BlockState support = world.getBlockState(supportPos);
        if (mount == Mount.HUNG) return facing(state.get(ROTATION)) != null && SignPosts.isPost(support);
        if (mount == Mount.WALL && facing(state.get(ROTATION)) == null) return false;
        return support.isSideSolid(world, supportPos, side.getOpposite(), SideShapeType.CENTER);
    }

    /** @return whether this sign falls when what holds it goes (see {@link #supportSide}). */
    protected boolean needsSupport(BlockState state) {
        return true;
    }

    /** @return whether this sign can stand at {@code pos} (on what is below it). */
    protected boolean canStandAt(WorldView world, BlockPos pos) {
        return world.getBlockState(pos.down()).isSolid();
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos,
                                                   Direction direction, BlockPos neighborPos, BlockState neighborState, Random random) {
        if (direction == supportSide(state) && !this.canPlaceAt(state, world, pos)) return Blocks.AIR.getDefaultState();
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
