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
    public static final EnumProperty<TileLayout> SIZE = EnumProperty.of("size", TileLayout.class);

    protected ATileBlock(Settings settings, int numberOfCartridges) {
        super(settings.nonOpaque(), numberOfCartridges);
        setDefaultState(getStateManager().getDefaultState().with(ROTATION_8, 0).with(SUPPORT, TileSupport.FLAT)
                .with(SIZE, TileLayout.STANDARD));
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
        return TileSize.with(super.getPickStack(world, pos, state), state.get(SIZE).size());
    }

    /** A broken tile drops itself in its size. */
    @Override
    protected List<ItemStack> getDroppedStacks(BlockState state, net.minecraft.loot.context.LootWorldContext.Builder builder) {
        List<ItemStack> drops = super.getDroppedStacks(state, builder);
        for (ItemStack drop : drops) if (drop.isOf(asItem())) TileSize.with(drop, state.get(SIZE).size());
        return drops;
    }

    // ---------------------------------------------------------------- placement and support

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        TileLayout layout = layoutFor(ctx);
        return getDefaultState()
                .with(ROTATION_8, rotation8FromYaw(ctx.getPlayerYaw()))
                .with(SIZE, layout)
                .with(SUPPORT, support(ctx.getWorld(), ctx.getBlockPos(), layout));
    }

    /** The layout of the tile placed by {@code ctx}: its item's size, and for a large one the side it spreads to. */
    public static TileLayout layoutFor(ItemPlacementContext ctx) {
        TileSize size = TileSize.of(ctx.getStack());
        if (size != TileSize.LARGE) return TileLayout.of(size);
        return TileLayout.largeFor(TileSupport.compute(ctx.getWorld(), ctx.getBlockPos()), ctx.getBlockPos(), ctx.getHitPos());
    }

    /**
     * The support of a tile laid out as {@code layout} at {@code pos}. A large tile follows the slope under its own
     * block (its highest one) when it spreads down it; on level ground it is lowered only when its 4 blocks lie on the
     * same level surface (a floor of bottom slabs...); otherwise it lies flat.
     */
    public static TileSupport support(BlockView world, BlockPos pos, TileLayout layout) {
        TileSupport support = TileSupport.compute(world, pos);
        if (!layout.isLarge()) return support;
        if (support.isSloped()) return layout.goesDown(support) ? support : TileSupport.FLAT;
        for (BlockPos part : layout.parts(pos)) {
            if (TileSupport.compute(world, part) != support) return TileSupport.FLAT;
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
        if (state.get(SIZE).isLarge() && !claimParts(world, pos, state.get(SIZE))) {
            world.setBlockState(pos, state.with(SIZE, TileLayout.STANDARD), Block.NOTIFY_ALL);
            return;
        }
        TileSupport support = support(world, pos, state.get(SIZE));
        if (state.get(SUPPORT) != support) world.setBlockState(pos, state.with(SUPPORT, support), Block.NOTIFY_ALL);
    }

    /** Whether the other blocks of a large tile at {@code anchor} are free (or already its parts). */
    public static boolean partsFree(BlockView world, BlockPos anchor, TileLayout layout) {
        for (BlockPos part : layout.parts(anchor)) {
            BlockState there = world.getBlockState(part);
            if (!TilePartBlock.isPartOf(there, part, anchor) && !there.isReplaceable()) return false;
        }
        return true;
    }

    /** Fills the 3 other blocks of a large tile with its parts, if they are free (or already its parts). */
    private boolean claimParts(World world, BlockPos anchor, TileLayout layout) {
        if (!partsFree(world, anchor, layout)) return false;
        for (BlockPos part : layout.parts(anchor)) {
            if (!TilePartBlock.isPartOf(world.getBlockState(part), part, anchor)) {
                world.setBlockState(part, TilePartBlock.stateFor(part, anchor), Block.NOTIFY_ALL);
            }
        }
        return true;
    }

    /** A large tile gone (or laid out another way): its parts go with it. */
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        TileLayout was = state.get(SIZE);
        TileLayout now = newState.isOf(this) ? newState.get(SIZE) : null;
        super.onStateReplaced(state, world, pos, newState, moved);
        if (world.isClient || !was.isLarge() || was == now) return;
        for (BlockPos part : was.parts(pos)) {
            if (TilePartBlock.isPartOf(world.getBlockState(part), part, pos)) {
                world.setBlockState(part, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
            }
        }
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, ShapeContext context) {
        return state.get(SUPPORT).outline();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView view, BlockPos pos, ShapeContext context) {
        return collision(state, 0, 0, context);
    }

    /**
     * What collides over the block at ({@code dx}, {@code dz}) from the tile's own: the slope for players and mobs
     * (walked up and down like bare stairs); for tokens, which the board stands upright in the middle of the tile, a
     * level platform at that height.
     */
    public static VoxelShape collision(BlockState tile, int dx, int dz, ShapeContext context) {
        TileSupport support = tile.get(SUPPORT);
        if (support.isSloped() && isToken(context)) {
            TileLayout layout = tile.get(SIZE);
            return TileSupport.tokenPlatform(support.standY(layout.centreX(), layout.centreZ()));
        }
        return support.shape(dx, dz);
    }

    private static boolean isToken(ShapeContext context) {
        return context instanceof net.minecraft.block.EntityShapeContext entityContext
                && entityContext.getEntity() instanceof fr.lordfinn.steveparty.entities.TokenizedEntityInterface token
                && token.steveparty$isTokenized();
    }



    /** The state to draw as a plain, level tile of the standard size (e.g. the small tile shown by the destination arrows). */
    public static BlockState levelState(BlockState state) {
        return state.contains(SUPPORT) ? state.with(SUPPORT, TileSupport.FLAT).with(SIZE, TileLayout.STANDARD) : state;
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
