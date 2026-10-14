package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.custom.PartyController.StepPulse;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.items.custom.MiniGamePageItem;
import fr.lordfinn.steveparty.minigame.MiniGamePodiumLink;
import fr.lordfinn.steveparty.podium.Podiums;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The Party Rewinder (« Magnéto de Party »), a tape deck that drives a party (see {@link StepControllerBlockEntity}).
 * Its front (the cassette door) faces {@link #FACING}, towards whoever placed it. Its three buttons on top choose its
 * mode: right click on one of them; anywhere else, the next mode.
 */
public class StepControllerBlock extends BlockWithEntity {
    public static final MapCodec<StepControllerBlock> CODEC = Block.createCodec(StepControllerBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    /** The deck's top (px): above it, the buttons. */
    private static final double TOP = 12 / 16.0;
    /** The model facing north, in px: the plate, the deck, its buttons, its door leaning out (two steps). */
    private static final double[][] BOXES = {
            {0, 0, 0, 16, 2, 16},
            {1, 2, 7, 15, 12, 12},
            {2.5, 12, 8, 13.5, 13, 11},
            {1, 2, 5, 15, 7, 7},
            {1, 7, 3, 15, 11.5, 7},
    };
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Type.HORIZONTAL) {
            VoxelShape shape = VoxelShapes.empty();
            for (double[] box : BOXES) {
                double[] a = toWorld(facing, box[0] / 16, box[2] / 16), b = toWorld(facing, box[3] / 16, box[5] / 16);
                shape = VoxelShapes.union(shape, VoxelShapes.cuboid(Math.min(a[0], b[0]), box[1] / 16, Math.min(a[1], b[1]),
                        Math.max(a[0], b[0]), box[4] / 16, Math.max(a[1], b[1])));
            }
            SHAPES.put(facing, shape.simplify());
        }
    }

    public StepControllerBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES.get(state.get(FACING));
    }

    /** A point (x, z in the block, 0 to 1) of the model facing north, where it is once the deck faces {@code facing}. */
    private static double[] toWorld(Direction facing, double x, double z) {
        return switch (facing) {
            case SOUTH -> new double[]{1 - x, 1 - z};
            case WEST -> new double[]{z, 1 - x};
            case EAST -> new double[]{1 - z, x};
            default -> new double[]{x, z};
        };
    }

    /** The x (0 to 1) in the model facing north of the point (x, z in the block) of a deck facing {@code facing}. */
    private static double modelX(Direction facing, double x, double z) {
        return switch (facing) {
            case SOUTH -> 1 - x;
            case WEST -> 1 - z;
            case EAST -> z;
            default -> x;
        };
    }

    /**
     * The button under a click at {@code local} (in the block, 0 to 1) of a deck facing {@code facing}: the top is
     * shared by its three buttons, in thirds, « previous » on the left seen from the front, « next » on the right. Null
     * below the top (the door, the sides, the plate).
     */
    public static @Nullable StepPulse.Mode buttonAt(Direction facing, Vec3d local) {
        if (local.y < TOP - 1.0E-4) return null;
        double x = modelX(facing, local.x, local.z) * 16;
        if (x >= 10) return StepPulse.Mode.PREVIOUS;
        return x >= 6 ? StepPulse.Mode.REPLAY : StepPulse.Mode.NEXT;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return TickableBlockEntity.getTicker(world);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new StepControllerBlockEntity(pos, state);
    }

    /** The plate is a plain block model (its own texture); the deck is drawn by its renderer. */
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    /** A comparator reads the phase of the party it drives (see {@link StepControllerBlockEntity}). */
    @Override
    protected boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof StepControllerBlockEntity blockEntity ? blockEntity.getComparatorOutput() : 0;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        // The Tile Linker Brush paints its link (see StepControllerLinks): the use goes on to the brush
        if (TileLinkerBrush.isBrush(player.getMainHandStack())) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        StepControllerBlockEntity blockEntity = (StepControllerBlockEntity) world.getBlockEntity(pos);
        // A mini-game page: the controller is linked to it (or unlinked), like a podium
        if (player.getMainHandStack().getItem() instanceof MiniGamePageItem) {
            Podiums.clickLink((ServerPlayerEntity) player, Hand.MAIN_HAND,
                    (ServerWorld) world, pos, MiniGamePodiumLink.Kind.STEP_CONTROLLER);
            if (blockEntity != null) blockEntity.refreshLinkedPages();
            return ActionResult.SUCCESS;
        }
        if (blockEntity == null) return ActionResult.SUCCESS;
        StepPulse.Mode button = player.isSneaking() ? null : buttonAt(state.get(FACING), hit.getPos().subtract(Vec3d.of(pos)));
        if (button != null) blockEntity.selectMode(button, player);
        else blockEntity.cycleMode(player);
        return ActionResult.SUCCESS;
    }
}
