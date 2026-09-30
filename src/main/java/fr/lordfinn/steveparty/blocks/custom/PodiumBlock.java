package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainer;
import fr.lordfinn.steveparty.utils.MessageUtils;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.world.block.WireOrientation;
import net.minecraft.world.tick.ScheduledTickView;
import org.jetbrains.annotations.Nullable;

/**
 * Podium (see {@link PodiumBlockEntity}): ends the mini-game being played and names its winners.
 * <ul>
 *     <li>Four kinds: gold (1st place), silver (2nd), bronze (3rd) and the classic one.</li>
 *     <li>A podium is a slab; another podium of the same kind put on it makes it a full block. Podiums stacked on
 *     one another make one podium (a column): only its top counts, redstone goes in and out through any of its
 *     blocks, and its settings (mode, cartridge, banner) are kept by its bottom block.</li>
 *     <li>Wrench (or cartridge) + right-click: the cartridge slot (the reward of the winners).</li>
 *     <li>Right-click (empty hand): mode "first arrived" / "on signal".</li>
 *     <li>The banner hanging on its front can be stamped with a stencil (Stencil Hammer, or a stencil and a dye),
 *     washed with a wet sponge.</li>
 *     <li>Output: a pulse each time a player steps on it; comparator: number of players standing on it.</li>
 * </ul>
 */
public class PodiumBlock extends CartridgeContainer {
    public static final EnumProperty<Direction> FACING = HorizontalFacingBlock.FACING;
    /** False: a slab (the podium's first level); true: a full block. */
    public static final BooleanProperty FULL = BooleanProperty.of("full");
    /** Top block of its column: the one with the plate and the banner, the one players stand on. */
    public static final BooleanProperty TOP = BooleanProperty.of("top");
    public static final BooleanProperty POWERED = Properties.POWERED;
    private static final VoxelShape SLAB = Block.createCuboidShape(0, 0, 0, 16, 8, 16);
    private static final VoxelShape FULL_SHAPE = VoxelShapes.fullCube();

    /** The place a podium gives in the "first arrived" mode (classic: the first one to arrive wins). */
    public enum Place implements StringIdentifiable {
        FIRST("gold", 1), SECOND("silver", 2), THIRD("bronze", 3), CLASSIC("classic", 1);

        private final String name;
        private final int rank;

        Place(String name, int rank) {
            this.name = name;
            this.rank = rank;
        }

        @Override
        public String asString() {
            return name;
        }

        public int rank() {
            return rank;
        }

        /** Arriving on it ends the mini-game (the 1st place, or a classic podium). */
        public boolean endsTheMiniGame() {
            return rank == 1;
        }
    }

    private final Place place;

    public PodiumBlock(Settings settings, Place place) {
        super(settings, 1);
        this.place = place;
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH).with(FULL, false).with(TOP, true).with(POWERED, false));
    }

    public Place getPlace() {
        return place;
    }

    @Override
    protected MapCodec<PodiumBlock> getCodec() {
        return Block.createCodec(settings -> new PodiumBlock(settings, place));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, FULL, TOP, POWERED);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(FULL) ? FULL_SHAPE : SLAB;
    }

    @Override
    protected boolean hasSidedTransparency(BlockState state) {
        return !state.get(FULL);
    }

    // ---------------------------------------------------------------- placement and column

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos pos = ctx.getBlockPos();
        BlockState existing = ctx.getWorld().getBlockState(pos);
        // Doubling a slab: the podium becomes a full block, same facing
        if (existing.isOf(this) && !existing.get(FULL)) return existing.with(FULL, true);
        return getDefaultState()
                .with(FACING, ctx.getHorizontalPlayerFacing().getOpposite())
                .with(TOP, !isPodium(ctx.getWorld().getBlockState(pos.up())));
    }

    @Override
    protected boolean canReplace(BlockState state, ItemPlacementContext context) {
        if (state.get(FULL) || !context.getStack().isOf(this.asItem())) return false;
        // Like a slab: clicking its top doubles it, placing into it from the side too
        return !context.canReplaceExisting() || context.getSide() == Direction.UP;
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos,
                                                   Direction direction, BlockPos neighborPos, BlockState neighborState, Random random) {
        if (direction == Direction.UP) return state.with(TOP, !isPodium(neighborState));
        return state;
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    public static boolean isPodium(BlockState state) {
        return state.getBlock() instanceof PodiumBlock;
    }

    /** Bottom block of the column {@code pos} belongs to (it keeps the podium's settings). */
    public static BlockPos bottomOf(BlockView world, BlockPos pos) {
        BlockPos.Mutable cursor = pos.mutableCopy();
        while (isPodium(world.getBlockState(cursor.down()))) cursor.move(Direction.DOWN);
        return cursor.toImmutable();
    }

    /** Top block of the column {@code pos} belongs to (the one players stand on). */
    public static BlockPos topOf(BlockView world, BlockPos pos) {
        BlockPos.Mutable cursor = pos.mutableCopy();
        while (isPodium(world.getBlockState(cursor.up()))) cursor.move(Direction.UP);
        return cursor.toImmutable();
    }

    /** The block entity keeping the settings of the column {@code pos} belongs to. */
    public static @Nullable PodiumBlockEntity master(BlockView world, BlockPos pos) {
        return world.getBlockEntity(bottomOf(world, pos)) instanceof PodiumBlockEntity podium ? podium : null;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PodiumBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        // Only the top of a column watches the players standing on it
        return world.isClient || !state.get(TOP) ? null : TickableBlockEntity.getTicker(world);
    }

    // ---------------------------------------------------------------- interaction

    @Override
    public NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
        // The cartridge of the whole column is the bottom block's
        return master(world, pos);
    }

    @Override
    protected ActionResult onUseWithoutCartridgeContainerOpener(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        ActionResult stamped = PodiumBanner.onUseWithItem(state, world, pos, player, hand, hit);
        if (stamped != null) return stamped;
        return stack.isEmpty() ? ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION : ActionResult.PASS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && master(world, pos) instanceof PodiumBlockEntity podium) {
            podium.cycleMode();
            world.playSound(null, pos, SoundEvents.BLOCK_COPPER_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1.0f, 1.5f);
            if (player instanceof ServerPlayerEntity serverPlayer)
                MessageUtils.sendToPlayer(serverPlayer, Text.translatableWithFallback("message.steveparty.podium.mode",
                        "Podium: %s", podium.getMode().getText().copy().formatted(Formatting.YELLOW)), MessageUtils.MessageType.ACTION_BAR);
        }
        return ActionResult.SUCCESS;
    }

    // ---------------------------------------------------------------- redstone

    /** A 2 redstone tick pulse out of every block of the column (a new arrival during the pulse extends it). */
    static void pulse(ServerWorld world, BlockPos anyPos) {
        BlockPos top = topOf(world, anyPos);
        for (BlockPos pos = bottomOf(world, anyPos); pos.getY() <= top.getY(); pos = pos.up()) {
            BlockState state = world.getBlockState(pos);
            if (!isPodium(state)) break;
            if (!state.get(POWERED)) world.setBlockState(pos, state.with(POWERED, true), Block.NOTIFY_ALL);
            world.scheduleBlockTick(pos, state.getBlock(), PartyBellBlock.PULSE_TICKS);
        }
        world.playSound(null, top, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.BLOCKS, 0.8f, 1.5f);
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (state.get(POWERED))
            world.setBlockState(pos, state.with(POWERED, false), Block.NOTIFY_ALL);
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, @Nullable WireOrientation wireOrientation, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, wireOrientation, notify);
        // Its own pulse fed back through a wire is not a signal sent to the podium
        if (world.isClient || state.get(POWERED)) return;
        PodiumBlockEntity podium = master(world, pos);
        if (podium != null) podium.onRedstoneInput(columnPower(world, pos));
    }

    /** The strongest power received by a block of the column. */
    static int columnPower(World world, BlockPos anyPos) {
        int power = 0;
        BlockPos top = topOf(world, anyPos);
        for (BlockPos pos = bottomOf(world, anyPos); pos.getY() <= top.getY(); pos = pos.up())
            power = Math.max(power, world.getReceivedRedstonePower(pos));
        return power;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient && master(world, pos) instanceof PodiumBlockEntity podium)
            podium.initRedstoneInput(columnPower(world, pos) > 0);
    }

    @Override
    protected boolean emitsRedstonePower(BlockState state) {
        return true;
    }

    @Override
    protected int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return state.get(POWERED) ? 15 : 0;
    }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return world.getBlockEntity(topOf(world, pos)) instanceof PodiumBlockEntity top ? Math.min(15, top.getPlayersOnCount()) : 0;
    }
}
