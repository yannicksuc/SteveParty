package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;

import java.util.List;

/**
 * What the Tile and the Advanced Tile share: they face one of 8 directions (from where the placing player looks), and
 * lie on the real surface of the block under them ({@link TileSupport}): level on full blocks and top slabs, lowered
 * onto bottom slabs, snow or carpets, sloped on stairs.
 */
public abstract class ATileBlock extends ABoardSpaceBlock {
    public static final IntProperty ROTATION_8 = IntProperty.of("rotation_8", 0, 7);
    public static final EnumProperty<TileSupport> SUPPORT = EnumProperty.of("support", TileSupport.class);
    public static final EnumProperty<TileSize> SIZE = EnumProperty.of("size", TileSize.class);

    protected ATileBlock(Settings settings, int numberOfCartridges) {
        super(settings.nonOpaque(), numberOfCartridges);
        setDefaultState(getStateManager().getDefaultState().with(ROTATION_8, 0).with(SUPPORT, TileSupport.FLAT)
                .with(SIZE, TileSize.STANDARD));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(ROTATION_8, SUPPORT, SIZE);
    }

    /** The translation key suffix of the line describing this tile in its tooltip. */
    protected abstract String tooltipKey();

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        super.appendTooltip(stack, context, tooltip, options);
        tooltip.add(Text.translatable("tooltip.steveparty.tile." + tooltipKey()).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.steveparty.tile.size",
                Text.translatable("tooltip.steveparty.tile.size." + TileSize.of(stack).asString())).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.steveparty.tile.size.hint").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.translatable("tooltip.steveparty.tile.stamp.hint").formatted(Formatting.DARK_GRAY));
    }

    /** The picked tile keeps its size. */
    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
        return TileSize.with(super.getPickStack(world, pos, state), state.get(SIZE));
    }

    // ---------------------------------------------------------------- placement and support

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        TileSize size = TileSize.of(ctx.getStack());
        return getDefaultState()
                .with(ROTATION_8, rotation8FromYaw(ctx.getPlayerYaw()))
                .with(SIZE, size)
                .with(SUPPORT, support(ctx.getWorld(), ctx.getBlockPos(), size));
    }

    /**
     * The support of a tile of {@code size} at {@code pos}. A large tile lies level: lowered only when its 4 blocks
     * lie on the same level surface (a floor of bottom slabs...), otherwise flat.
     */
    public static TileSupport support(BlockView world, BlockPos pos, TileSize size) {
        TileSupport support = TileSupport.compute(world, pos);
        if (size != TileSize.LARGE) return support;
        if (support.isSloped()) return TileSupport.FLAT;
        for (TilePartBlock.Part part : TilePartBlock.Part.values()) {
            if (TileSupport.compute(world, part.fromMaster(pos)) != support) return TileSupport.FLAT;
        }
        return support;
    }

    /** The support under the tile changed (placed, broken, a slab turned double...): follow its surface. */
    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos,
                                                   Direction direction, BlockPos neighborPos, BlockState neighborState, Random random) {
        BlockState updated = super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
        if (direction == Direction.DOWN && updated.isOf(this)) {
            return updated.with(SUPPORT, support(world, pos, updated.get(SIZE)));
        }
        return updated;
    }

    /** A large tile's part saw its support change: the whole tile checks its support again. */
    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        TileSupport support = support(world, pos, state.get(SIZE));
        if (state.get(SUPPORT) != support) world.setBlockState(pos, state.with(SUPPORT, support), Block.NOTIFY_ALL);
    }

    /**
     * Placed or replaced without a player too (commands, structures, the test world): reads the support there, and
     * a large tile takes the 3 other blocks of its 2x2 (it shrinks back to the standard size if they are not free).
     */
    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (world.isClient) return;
        if (state.get(SIZE) == TileSize.LARGE && !claimParts(world, pos)) {
            world.setBlockState(pos, state.with(SIZE, TileSize.STANDARD), Block.NOTIFY_ALL);
            return;
        }
        TileSupport support = support(world, pos, state.get(SIZE));
        if (state.get(SUPPORT) != support) world.setBlockState(pos, state.with(SUPPORT, support), Block.NOTIFY_ALL);
    }

    /** Fills the 3 other blocks of a large tile with its parts, if they are free (or already its parts). */
    private boolean claimParts(World world, BlockPos master) {
        for (TilePartBlock.Part part : TilePartBlock.Part.values()) {
            BlockPos partPos = part.fromMaster(master);
            BlockState there = world.getBlockState(partPos);
            if (!TilePartBlock.isPartOf(there, part) && !there.isReplaceable()) return false;
        }
        for (TilePartBlock.Part part : TilePartBlock.Part.values()) {
            BlockPos partPos = part.fromMaster(master);
            if (!TilePartBlock.isPartOf(world.getBlockState(partPos), part)) {
                world.setBlockState(partPos, TilePartBlock.stateFor(part), Block.NOTIFY_ALL);
            }
        }
        return true;
    }

    /** A large tile gone (or no longer large): its parts go with it. */
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        boolean wasLarge = state.get(SIZE) == TileSize.LARGE;
        boolean stillLarge = newState.isOf(this) && newState.get(SIZE) == TileSize.LARGE;
        super.onStateReplaced(state, world, pos, newState, moved);
        if (world.isClient || !wasLarge || stillLarge) return;
        for (TilePartBlock.Part part : TilePartBlock.Part.values()) {
            BlockPos partPos = part.fromMaster(pos);
            if (TilePartBlock.isPartOf(world.getBlockState(partPos), part)) {
                world.setBlockState(partPos, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
            }
        }
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, ShapeContext context) {
        return state.get(SUPPORT).shape();
    }

    /** The state to draw as a plain, level tile of the standard size (e.g. the small tile shown by the destination arrows). */
    public static BlockState levelState(BlockState state) {
        return state.contains(SUPPORT) ? state.with(SUPPORT, TileSupport.FLAT).with(SIZE, TileSize.STANDARD) : state;
    }

    // ---------------------------------------------------------------- 8 directions

    /** Converts a player yaw into 8 steps of 45° (0 = placed while looking north, same convention as signs). */
    public static int rotation8FromYaw(float yaw) {
        return Math.floorMod((int) Math.floor((yaw + 202.5F) / 45.0F), 8);
    }

    public static BlockState rotate8(BlockState state, BlockRotation rotation) {
        return state.with(ROTATION_8, rotation.rotate(state.get(ROTATION_8), 8)); // 90° = +2 steps
    }

    public static BlockState mirror8(BlockState state, BlockMirror mirror) {
        return state.with(ROTATION_8, mirror.mirror(state.get(ROTATION_8), 8)); // NONE = identity
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return rotate8(state, rotation);
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return mirror8(state, mirror);
    }
}
