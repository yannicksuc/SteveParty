package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BubbleColumnBlock;
import net.minecraft.block.ChainBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Plastic floats: a plastic block under water rises one block at a time until its top is level with the surface.
 * Bubble columns carry it twice as fast as they carry entities: soul sand pushes it up, magma pulls it down onto the
 * magma block.
 * Nothing made of plastic (blocks and studs, see {@link #isPlastic}) cuts a bubble column: the column goes on above
 * it ({@code BubbleColumnBlockMixin}).
 * Whatever stands on the block (players, mobs, items) rides with it, up or down: over soul sand or magma it makes a
 * fast water elevator. What is in its way is carried along too: lifted onto it going up, pushed down ahead of it going
 * down. It never crushes anyone: with no room to carry them, it waits.
 * <p>
 * It only ever moves into water (still water or a bubble column) and never replaces anything else. {@link #WET} remembers
 * whether the block stands in place of water: only then does it leave water behind when it moves, so no water is
 * created or lost (a block put above the surface and pulled down leaves air). A chain attached to it keeps it where
 * it is.
 */
public class PlasticBlock extends Block {
    public static final MapCodec<PlasticBlock> CODEC = createCodec(PlasticBlock::new);
    /** Ticks between two steps in still water. */
    public static final int RISE_DELAY = 4;
    /**
     * Speed in a bubble column, in blocks per {@link #COLUMN_PERIOD} ticks: twice what the column gives entities
     * (0.7 blocks per tick up over soul sand, 0.3 down over magma), so 1.4 and 0.6 blocks per tick.
     */
    public static final int COLUMN_PERIOD = 5, COLUMN_UP_BLOCKS = 7, COLUMN_DOWN_BLOCKS = 3;
    /** Player teleport flags: relative (unchanged) except the height and the vertical speed, set exactly. */
    private static final Set<PositionFlag> ALL_BUT_HEIGHT = EnumSet.of(PositionFlag.X, PositionFlag.Z,
            PositionFlag.Y_ROT, PositionFlag.X_ROT, PositionFlag.DELTA_X, PositionFlag.DELTA_Z);
    /** Speed of a player riding a piece in a bubble column (blocks per tick): the piece's, 1.4 up and 0.6 down. */
    private static final double RIDE_UP_SPEED = (double) COLUMN_UP_BLOCKS / COLUMN_PERIOD;
    private static final double RIDE_DOWN_SPEED = (double) COLUMN_DOWN_BLOCKS / COLUMN_PERIOD;
    /**
     * A player gliding with a piece is ahead of it or behind it between two of its steps (the piece moves a whole
     * block at a time). Going up, it may be up to RIDE_AHEAD above it or RIDE_INSIDE inside it, and is put RIDE_LEAD
     * above it when put back; going down, up to RIDE_BEHIND above it (up to a block behind it between two steps, plus
     * the server seeing the player about a tick late). Past that, it is put back on it.
     */
    private static final double RIDE_AHEAD = 2.0, RIDE_INSIDE = 0.5, RIDE_LEAD = 0.5, RIDE_BEHIND = 2.5;
    /** How far above a sinking piece something still counts as standing on it (bobbing in the water). */
    private static final double RIDER_MARGIN = 0.5;
    /** True when the block took the place of a water source: it gives it back when it moves away. */
    public static final BooleanProperty WET = BooleanProperty.of("wet");

    /** Which way the water around the block carries it. */
    public enum Current { NONE, UP, DOWN }

    public PlasticBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(WET, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(WET);
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        // Placed (by hand, command or structure) into a water source: it now holds that water
        FluidState replaced = oldState.getFluidState();
        if (!state.get(WET) && replaced.isOf(Fluids.WATER) && replaced.isStill()) {
            world.setBlockState(pos, state.with(WET, true), Block.NOTIFY_LISTENERS);
        }
        world.scheduleBlockTick(pos, this, getDelay(world, pos));
    }

    /** Broken by a player (survival, creative or the wrench): gives back the water source it took. */
    @Override
    public void onBroken(WorldAccess world, BlockPos pos, BlockState state) {
        if (state.get(WET) && !world.getDimension().ultrawarm() && world.getBlockState(pos).isAir()) {
            world.setBlockState(pos, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
        }
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView,
                                                   BlockPos pos, Direction direction, BlockPos neighborPos,
                                                   BlockState neighborState, Random random) {
        // Water arriving, a bubble column forming, or a chain holding it being broken: try again
        tickView.scheduleBlockTick(pos, this, getDelay(world, pos));
        return super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        drift(world, pos, this, PlasticBlock::step);
    }

    @Nullable
    private static BlockPos step(BlockState state, ServerWorld world, BlockPos pos) {
        if (isChained(world, pos)) return null;
        BlockPos target;
        if (getCurrent(world, pos) == Current.DOWN) {
            target = pos.down();
            if (!canSinkInto(world.getBlockState(target))) return null; // resting on the magma
        } else {
            target = pos.up();
            if (!canRiseInto(world.getBlockState(target))) return null;
        }
        return moveWithRiders(world, pos, target, state) ? target : retry(world, pos, state);
    }

    /** One move of a piece, one block up or down. */
    @FunctionalInterface
    public interface Step {
        /** @return where the piece went, or null if it stays where it is */
        @Nullable BlockPos move(BlockState state, ServerWorld world, BlockPos pos);
    }

    /**
     * Moves a piece as far as the water carries it this tick: one block in still water (a step every
     * {@link #RISE_DELAY} ticks), up to two blocks per tick in a bubble column.
     */
    public static void drift(ServerWorld world, BlockPos pos, Block block, Step step) {
        carryColumnAbove(world, pos);
        Current current = getCurrent(world, pos);
        int moves = movesThisTick(world, current);
        if (moves == 0) {
            world.scheduleBlockTick(pos, block, 1); // a column tick without a move: next one
            keepRidersGliding(world, pos, current);
        }
        for (int i = 0; i < moves && pos != null; i++) {
            BlockState state = world.getBlockState(pos);
            if (!state.isOf(block)) return;
            pos = step.move(state, world, pos);
        }
    }

    /**
     * Between two steps of a travelling piece, keeps the players riding it at its speed: the water slows them down
     * every tick, and going down (a step on 3 ticks out of 5) they would lag behind and be put back on it with a jerk.
     */
    private static void keepRidersGliding(ServerWorld world, BlockPos pos, Current current) {
        BlockState state = world.getBlockState(pos);
        boolean up = current == Current.UP;
        if (state.contains(PlotBlock.WATERLOGGED) && !state.get(PlotBlock.WATERLOGGED)) return; // a dry stud stays
        if (isChained(world, pos)) return;
        if (up ? !canRiseInto(world.getBlockState(pos.up())) : !canSinkInto(world.getBlockState(pos.down()))) return;
        double top = pos.getY() + state.getOutlineShape(world, pos).getMax(Direction.Axis.Y);
        for (Entity rider : getRiders(world, pos, top, top + (up ? RIDE_AHEAD : RIDE_BEHIND))) {
            if (rider instanceof ServerPlayerEntity player) glide(player, up ? RIDE_UP_SPEED : -RIDE_DOWN_SPEED);
        }
    }

    /** @return how many blocks the current moves a piece this tick (spread evenly over {@link #COLUMN_PERIOD}) */
    private static int movesThisTick(ServerWorld world, Current current) {
        if (current == Current.NONE) return 1;
        long blocks = current == Current.UP ? COLUMN_UP_BLOCKS : COLUMN_DOWN_BLOCKS;
        long time = world.getTime();
        return (int) ((time + 1) * blocks / COLUMN_PERIOD - time * blocks / COLUMN_PERIOD);
    }

    /** Someone is in the way and cannot be carried: try again later. */
    @Nullable
    public static BlockPos retry(ServerWorld world, BlockPos pos, BlockState state) {
        world.scheduleBlockTick(pos, state.getBlock(), getDelay(world, pos));
        return null;
    }

    /**
     * Swaps a plastic piece with the water of {@code target} (one block up or down), carrying what is on it and what
     * is in its way. Going up, everything in the space the piece moves into (standing on it, or floating just above
     * it) is lifted onto its new top first, so the piece never ends up inside anyone. Going down, what is under it is
     * pushed down ahead of it, and what stands on it follows once it made room. Players glide along at the piece's
     * speed instead (see {@link #ride}). If carrying someone would push them into a block, the piece does not move.
     * The piece schedules its next step itself (onBlockAdded). It leaves water behind only if it held some.
     *
     * @return false if it could not move (someone would be crushed): the caller tries again later
     */
    public static boolean moveWithRiders(ServerWorld world, BlockPos pos, BlockPos target, BlockState moved) {
        BlockState current = world.getBlockState(pos);
        BlockState left = holdsWater(current) ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
        if (moved.contains(WET)) moved = moved.with(WET, true); // it only ever moves into water
        VoxelShape shape = current.getOutlineShape(world, pos), newShape = moved.getOutlineShape(world, target);
        double top = pos.getY() + shape.getMax(Direction.Axis.Y);
        double newTop = target.getY() + newShape.getMax(Direction.Axis.Y);
        double newBottom = target.getY() + newShape.getMin(Direction.Axis.Y);
        boolean up = target.getY() > pos.getY();
        // Its last step: riders are put down on it, not carried on
        boolean last = up ? !canRiseInto(world.getBlockState(target.up())) : !canSinkInto(world.getBlockState(target.down()));
        // Up: all that the piece would sweep through, and the players gliding ahead of it; down: what stands on it,
        // or bobs (players: glides) just above it
        List<Entity> riders = getRiders(world, pos, top, up ? newTop + RIDE_AHEAD : top + RIDE_BEHIND);
        List<Entity> lifted = up ? riders.stream().filter(e -> e.getBoundingBox().minY < newTop).toList() : List.of();
        List<Entity> pushed = up ? List.of()
                : getInTheWay(world, pos, pos.getY() + shape.getMin(Direction.Axis.Y), newBottom);
        ToDoubleFunction<Entity> ontoTop = e -> newTop - e.getBoundingBox().minY;
        ToDoubleFunction<Entity> underBottom = e -> newBottom - e.getBoundingBox().maxY;
        if (!canShift(world, lifted, ontoTop) || !canShift(world, pushed, underBottom)) return false;
        world.setBlockState(target, moved, Block.NOTIFY_ALL);
        world.setBlockState(pos, left, Block.NOTIFY_ALL);
        // The water left behind joins the bubble column at once (vanilla would take 5 ticks, slowing the piece down)
        BubbleColumnBlock.update(world, pos, getCurrentSource(world, pos.down()));
        double speed = last ? 0 : up ? RIDE_UP_SPEED : -RIDE_DOWN_SPEED;
        for (Entity rider : riders) {
            double feet = rider.getBoundingBox().minY;
            if (rider instanceof ServerPlayerEntity player) {
                ride(world, player, feet - newTop, newTop, up, last, speed, pos, target);
            } else if (up ? feet < newTop : feet < top + RIDER_MARGIN) {
                rider.requestTeleport(rider.getX(), newTop, rider.getZ());
            }
        }
        for (Entity entity : pushed) {
            double y = entity.getY() + underBottom.applyAsDouble(entity);
            if (entity instanceof ServerPlayerEntity player) {
                place(world, player, y, speed, pos, target);
            } else {
                entity.requestTeleport(entity.getX(), y, entity.getZ());
            }
        }
        playRiseEffects(world, pos, target);
        return true;
    }

    /**
     * Carries a player riding a piece: while it travels, the player glides at its speed (a teleport per step would
     * be jerky), and is only put back on it when too far off; on its last step, the player is put down on it.
     *
     * @param off height of the player's feet above the piece's new top (negative: inside it)
     */
    private static void ride(ServerWorld world, ServerPlayerEntity player, double off, double newTop, boolean up,
                             boolean last, double speed, BlockPos from, BlockPos to) {
        if (last) {
            if (up && off >= 0) { // ahead of it: lands on it
                sendBlocks(world, player, from, to);
                glide(player, 0);
            } else {
                place(world, player, newTop, 0, from, to);
            }
        } else if (off < -RIDE_INSIDE || off > (up ? RIDE_AHEAD : RIDE_BEHIND)) {
            place(world, player, newTop + (up ? RIDE_LEAD : 0), speed, from, to);
        } else {
            sendBlocks(world, player, from, to);
            glide(player, speed);
        }
    }

    /** Sets the player's vertical speed: its client moves it. */
    private static void glide(ServerPlayerEntity player, double speed) {
        Vec3d velocity = player.getVelocity();
        player.setVelocity(velocity.x, speed, velocity.z);
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
    }

    /**
     * Puts the player at an exact height (a relative teleport would apply to where its client already is, drifting in
     * the water) with the given vertical speed, the two blocks that changed sent first so that it lands on the piece.
     */
    private static void place(ServerWorld world, ServerPlayerEntity player, double y, double speed,
                              BlockPos from, BlockPos to) {
        sendBlocks(world, player, from, to);
        player.networkHandler.requestTeleport(new PlayerPosition(new Vec3d(0, y, 0), new Vec3d(0, speed, 0), 0, 0),
                ALL_BUT_HEIGHT);
    }

    private static void sendBlocks(ServerWorld world, ServerPlayerEntity player, BlockPos from, BlockPos to) {
        player.networkHandler.sendPacket(new BlockUpdateS2CPacket(world, from));
        player.networkHandler.sendPacket(new BlockUpdateS2CPacket(world, to));
    }

    /**
     * @return true if the entity stands on (or glides just above) a plastic piece: a bubble column must not slow it
     * down, it moves at the piece's speed
     */
    public static boolean isRidingPlastic(World world, Entity entity) {
        BlockPos.Mutable cursor = BlockPos.ofFloored(entity.getX(), entity.getBoundingBox().minY - 0.01, entity.getZ()).mutableCopy();
        for (int i = 0; i <= Math.max(RIDE_AHEAD, RIDE_BEHIND); i++, cursor.move(Direction.DOWN)) {
            if (isPlastic(world.getBlockState(cursor))) return true;
        }
        return false;
    }

    private static boolean canShift(ServerWorld world, List<Entity> entities, ToDoubleFunction<Entity> dy) {
        for (Entity entity : entities) {
            if (!world.isSpaceEmpty(entity, entity.getBoundingBox().offset(0, dy.applyAsDouble(entity), 0))) return false;
        }
        return true;
    }

    /** @return true if the piece stands in place of water: a wet block or a waterlogged stud. */
    private static boolean holdsWater(BlockState state) {
        return state.contains(WET) ? state.get(WET) : state.getFluidState().isOf(Fluids.WATER);
    }

    /**
     * @return the bubble column acting on the block, from what feeds it at the bottom: soul sand pushes it up, magma
     * pulls it down. The column cells themselves can lag behind (soul sand and magma take 20 ticks to turn them), so
     * they are not trusted.
     */
    public static Current getCurrent(WorldView world, BlockPos pos) {
        BlockState source = getCurrentSource(world, pos.down());
        if (source.isOf(Blocks.SOUL_SAND)) return Current.UP;
        if (source.isOf(Blocks.MAGMA_BLOCK)) return Current.DOWN;
        return Current.NONE;
    }

    /** @return the block at the bottom of the bubble column at {@code pos}, looking down through it and any plastic */
    public static BlockState getCurrentSource(WorldView world, BlockPos pos) {
        BlockPos.Mutable cursor = pos.mutableCopy();
        BlockState state = world.getBlockState(cursor);
        for (int i = 0; i < 64 && (isPlastic(state) || state.isOf(Blocks.BUBBLE_COLUMN)); i++) {
            state = world.getBlockState(cursor.move(Direction.DOWN));
        }
        return state;
    }

    /** @return true for anything made of plastic (blocks and studs): it floats and never cuts a bubble column. */
    public static boolean isPlastic(BlockState state) {
        return state.getBlock() instanceof PlasticBlock || state.getBlock() instanceof PlotBlock;
    }

    /** @return the state at {@code pos}, looking under any plastic stacked there (bubble columns ignore plastic). */
    public static BlockState getColumnSource(WorldView world, BlockPos pos) {
        BlockPos.Mutable cursor = pos.mutableCopy();
        BlockState state = world.getBlockState(cursor);
        for (int i = 0; i < 64 && isPlastic(state); i++) {
            state = world.getBlockState(cursor.move(Direction.DOWN));
        }
        return state;
    }

    /**
     * @return what is on the piece (top at {@code top}): feet between {@code top} and {@code maxFeet}, above the piece
     * (passengers follow their vehicle, spectators stay)
     */
    private static List<Entity> getRiders(ServerWorld world, BlockPos pos, double top, double maxFeet) {
        Box above = new Box(pos.getX(), top - 0.01, pos.getZ(), pos.getX() + 1, maxFeet, pos.getZ() + 1);
        return world.getOtherEntities(null, above, e -> !e.isSpectator() && !e.hasVehicle()
                && e.getBoundingBox().minY >= top - 0.01 && e.getBoundingBox().minY < maxFeet);
    }

    /** @return what is under a piece (bottom at {@code bottom}) and would be inside it once it sank to {@code newBottom} */
    private static List<Entity> getInTheWay(ServerWorld world, BlockPos pos, double bottom, double newBottom) {
        Box below = new Box(pos.getX(), newBottom, pos.getZ(), pos.getX() + 1, bottom + 0.01, pos.getZ() + 1);
        return world.getOtherEntities(null, below, e -> !e.isSpectator() && !e.hasVehicle()
                && e.getBoundingBox().maxY <= bottom + 0.01);
    }

    /**
     * Standing in a bubble column, a plastic piece lets it go on above itself (vanilla stops at the first block).
     * The water above is rebuilt from what is under the plastic every time, so switching soul sand and magma turns
     * the column above too, and removing them turns it back into still water.
     */
    public static void carryColumnAbove(ServerWorld world, BlockPos pos) {
        BubbleColumnBlock.update(world, pos.up(), getCurrentSource(world, pos.down()));
    }

    public static int getDelay(WorldView world, BlockPos pos) {
        return getCurrent(world, pos) == Current.NONE ? RISE_DELAY : 1;
    }

    public static void playRiseEffects(ServerWorld world, BlockPos from, BlockPos to) {
        world.spawnParticles(ParticleTypes.BUBBLE, from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5,
                6, 0.3, 0.3, 0.3, 0.05);
        world.playSound(null, to, SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.BLOCKS, 0.4f, 1.2f);
    }

    /** @return true if a chain is attached to the block: vertical below it, or pointing at it from a side. */
    public static boolean isChained(WorldView world, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (direction == Direction.UP) continue; // a chain above blocks the way anyway
            BlockState neighbor = world.getBlockState(pos.offset(direction));
            if (neighbor.isOf(Blocks.CHAIN) && neighbor.get(ChainBlock.AXIS) == direction.getAxis()) return true;
        }
        return false;
    }

    /**
     * @return true for still water or an upward bubble column: the block never turns flowing water into a source and
     * never climbs against a whirlpool
     */
    public static boolean canRiseInto(BlockState state) {
        if (state.isOf(Blocks.BUBBLE_COLUMN)) return !state.get(BubbleColumnBlock.DRAG);
        return state.isOf(Blocks.WATER) && state.getFluidState().isStill();
    }

    /** @return true for the water of a bubble column or still water. */
    public static boolean canSinkInto(BlockState state) {
        if (state.isOf(Blocks.BUBBLE_COLUMN)) return true;
        return state.isOf(Blocks.WATER) && state.getFluidState().isStill();
    }
}
