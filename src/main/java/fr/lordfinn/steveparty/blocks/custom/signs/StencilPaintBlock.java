package fr.lordfinn.steveparty.blocks.custom.signs;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.Orientation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.tick.ScheduledTickView;
import org.jetbrains.annotations.Nullable;

/**
 * Paint sprayed through a stencil on the face of a full block (wall, floor or ceiling). It is a thin layer in the
 * space in front of that face: blocks can be put over it, water washes it away, a brush fades it then scrubs it off,
 * and it goes when its block goes. It drops nothing. The symbol can be repainted like on a sign.
 * <p>
 * {@link #ORIENTATION}: facing = side of the block it is sprayed on (pointing out of it), rotation = which way the
 * top of the symbol points.
 */
public class StencilPaintBlock extends BlockWithEntity implements StencilCanvasBlock {
    public static final MapCodec<StencilPaintBlock> CODEC = createCodec(StencilPaintBlock::new);
    public static final EnumProperty<Orientation> ORIENTATION = Properties.ORIENTATION;
    private static final double THICKNESS = 0.5;
    private static final VoxelShape[] SHAPES = new VoxelShape[6];

    static {
        SHAPES[Direction.UP.ordinal()] = Block.createCuboidShape(0, 0, 0, 16, THICKNESS, 16);
        SHAPES[Direction.DOWN.ordinal()] = Block.createCuboidShape(0, 16 - THICKNESS, 0, 16, 16, 16);
        SHAPES[Direction.SOUTH.ordinal()] = Block.createCuboidShape(0, 0, 0, 16, 16, THICKNESS);
        SHAPES[Direction.NORTH.ordinal()] = Block.createCuboidShape(0, 0, 16 - THICKNESS, 16, 16, 16);
        SHAPES[Direction.EAST.ordinal()] = Block.createCuboidShape(0, 0, 0, THICKNESS, 16, 16);
        SHAPES[Direction.WEST.ordinal()] = Block.createCuboidShape(16 - THICKNESS, 0, 0, 16, 16, 16);
    }

    public StencilPaintBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(ORIENTATION, Orientation.NORTH_UP));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new StencilCanvasBlockEntity(pos, state);
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        // Drawn by the block entity renderer only
        return BlockRenderType.INVISIBLE;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ORIENTATION);
    }

    public static Direction getFacing(BlockState state) {
        return state.get(ORIENTATION).getFacing();
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES[getFacing(state).ordinal()];
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        Direction facing = getFacing(state);
        BlockPos support = pos.offset(facing.getOpposite());
        return world.getBlockState(support).isSideSolidFullSquare(world, support, facing);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos,
                                                   Direction direction, BlockPos neighborPos, BlockState neighborState, Random random) {
        if (direction == getFacing(state).getOpposite() && !canPlaceAt(state, world, pos)) return Blocks.AIR.getDefaultState();
        return super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        Orientation orientation = state.get(ORIENTATION);
        return state.with(ORIENTATION, Orientation.byDirections(rotation.rotate(orientation.getFacing()), rotation.rotate(orientation.getRotation())));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        Orientation orientation = state.get(ORIENTATION);
        return state.with(ORIENTATION, Orientation.byDirections(mirror.apply(orientation.getFacing()), mirror.apply(orientation.getRotation())));
    }

    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
                                         Hand hand, BlockHitResult hit) {
        if (stack.isOf(Items.BRUSH)) {
            // Held on the paint, the brush fades it step by step, then scrubs it off the wall
            if (!world.isClient && world.getBlockEntity(pos) instanceof StencilCanvasBlockEntity canvas
                    && StencilInteractions.brush(canvas, player, hand) == StencilInteractions.BrushResult.GONE) {
                world.removeBlock(pos, false);
                Direction facing = getFacing(state);
                BlockPos support = pos.offset(facing.getOpposite());
                if (world instanceof ServerWorld serverWorld) {
                    serverWorld.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, world.getBlockState(support)),
                            pos.getX() + 0.5 - facing.getOffsetX() * 0.45, pos.getY() + 0.5 - facing.getOffsetY() * 0.45,
                            pos.getZ() + 0.5 - facing.getOffsetZ() * 0.45, 12, 0.25, 0.25, 0.25, 0.05);
                }
                world.emitGameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Emitter.of(player, state));
            }
            return ActionResult.SUCCESS;
        }
        if (stack.isOf(Items.WET_SPONGE)) {
            // Washing the paint off leaves nothing behind
            if (!world.isClient) {
                world.removeBlock(pos, false);
                world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.BLOCKS, 1.0F, 1.4F);
                world.emitGameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Emitter.of(player, state));
            }
            return ActionResult.SUCCESS;
        }
        return StencilInteractions.onUseWithItem(state, world, pos, player, hand);
    }

    /**
     * Sprays {@code shape} in {@code color} on the {@code side} face of the block at {@code target}: repaints the
     * paint already there, or adds paint in front of the face if it is a full face with room in front of it.
     *
     * @param up the player's horizontal facing: the symbol is sprayed the right way up for them
     * @return true if something was painted
     */
    public static boolean spray(World world, BlockPos target, Direction side, byte[] shape, DyeColor color, Direction up) {
        BlockState targetState = world.getBlockState(target);
        BlockPos pos;
        if (targetState.isOf(ModBlocks.STENCIL_PAINT)) {
            pos = target;
        } else {
            if (!targetState.isSideSolidFullSquare(world, target, side)) return false;
            pos = target.offset(side);
        }
        BlockState current = world.getBlockState(pos);
        if (current.isOf(ModBlocks.STENCIL_PAINT)) {
            if (world.getBlockEntity(pos) instanceof StencilCanvasBlockEntity canvas) {
                if (!world.isClient) canvas.setSymbol(shape, color);
                return true;
            }
            return false;
        }
        if (!current.isReplaceable() || !current.getFluidState().isEmpty()) return false;
        // Read from where the player stands: on a ceiling, looking up, the top of the view is behind them
        Direction rotation = side == Direction.UP ? up : side == Direction.DOWN ? up.getOpposite() : Direction.UP;
        BlockState paint = ModBlocks.STENCIL_PAINT.getDefaultState().with(ORIENTATION, Orientation.byDirections(side, rotation));
        if (!paint.canPlaceAt(world, pos)) return false;
        if (!world.isClient) {
            world.setBlockState(pos, paint, Block.NOTIFY_ALL);
            if (world.getBlockEntity(pos) instanceof StencilCanvasBlockEntity canvas) canvas.setSymbol(shape, color);
        }
        return true;
    }
}
