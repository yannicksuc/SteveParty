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

    protected ATileBlock(Settings settings, int numberOfCartridges) {
        super(settings.nonOpaque(), numberOfCartridges);
        setDefaultState(getStateManager().getDefaultState().with(ROTATION_8, 0).with(SUPPORT, TileSupport.FLAT));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(ROTATION_8, SUPPORT);
    }

    /** The translation key suffix of the line describing this tile in its tooltip. */
    protected abstract String tooltipKey();

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        super.appendTooltip(stack, context, tooltip, options);
        tooltip.add(Text.translatable("tooltip.steveparty.tile." + tooltipKey()).formatted(Formatting.GRAY));
    }

    // ---------------------------------------------------------------- placement and support

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState()
                .with(ROTATION_8, rotation8FromYaw(ctx.getPlayerYaw()))
                .with(SUPPORT, TileSupport.compute(ctx.getWorld(), ctx.getBlockPos()));
    }

    /** The support under the tile changed (placed, broken, a slab turned double...): follow its surface. */
    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos,
                                                   Direction direction, BlockPos neighborPos, BlockState neighborState, Random random) {
        BlockState updated = super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
        if (direction == Direction.DOWN && updated.isOf(this)) {
            return updated.with(SUPPORT, TileSupport.compute(world, pos));
        }
        return updated;
    }

    /** Placed or replaced without a player (commands, structures, the test world): read the support there. */
    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (world.isClient) return;
        TileSupport support = TileSupport.compute(world, pos);
        if (state.get(SUPPORT) != support) world.setBlockState(pos, state.with(SUPPORT, support), Block.NOTIFY_ALL);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, ShapeContext context) {
        return state.get(SUPPORT).shape();
    }

    /** The state to draw as a plain, level tile (e.g. the small tile shown by the destination arrows). */
    public static BlockState levelState(BlockState state) {
        return state.contains(SUPPORT) ? state.with(SUPPORT, TileSupport.FLAT) : state;
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
