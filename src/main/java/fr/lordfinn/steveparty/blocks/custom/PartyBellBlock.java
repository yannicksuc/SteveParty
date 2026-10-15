package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Party Antenna (registry id {@code party_bell}, see {@link PartyBellBlockEntity}).
 * <ul>
 *     <li>Right-click (empty hand): next moment listened to; sneaking: previous one.</li>
 *     <li>Right-click with a wrench: simple mode (pulse only) / waiting mode (the party pauses at that moment until
 *     the antenna receives a redstone signal).</li>
 *     <li>Output: a pulse of power 15 on every side; comparator: value of the last moment heard.</li>
 * </ul>
 */
public class PartyBellBlock extends Block implements BlockEntityProvider {
    public static final MapCodec<PartyBellBlock> CODEC = Block.createCodec(PartyBellBlock::new);
    public static final EnumProperty<PartyMoment> MOMENT = EnumProperty.of("moment", PartyMoment.class);
    public static final BooleanProperty WAITING = BooleanProperty.of("waiting");
    public static final BooleanProperty POWERED = Properties.POWERED;
    /** Where its head rests (toward whoever placed it): what it scans around when no Party Controller is near. */
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    /** Synced block event: it caught a moment (its receiver lights, its dish pulses). */
    public static final int RING_EVENT = 1;
    /** Length of the pulse: 2 redstone ticks. */
    public static final int PULSE_TICKS = 4;

    /** The redstone plate of the other redstone blocks, its box and the mast (what you bump into). */
    private static final VoxelShape BODY = VoxelShapes.union(
            Block.createCuboidShape(0, 0, 0, 16, 2, 16),
            Block.createCuboidShape(4, 2, 4, 12, 5, 12),
            Block.createCuboidShape(7, 5, 7, 9, 12, 9)
    );
    /** And the head, whichever way it turns (what you aim at). */
    private static final VoxelShape SHAPE = VoxelShapes.union(BODY, Block.createCuboidShape(2, 7, 2, 14, 16, 14));

    public PartyBellBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(MOMENT, PartyMoment.TURN_START)
                .with(WAITING, false)
                .with(POWERED, false)
                .with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(MOMENT, WAITING, POWERED, FACING);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return BODY;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PartyBellBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return TickableBlockEntity.getTicker(world);
    }

    @Override
    protected boolean onSyncedBlockEvent(BlockState state, World world, BlockPos pos, int type, int data) {
        super.onSyncedBlockEvent(state, world, pos, type, data);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity != null && blockEntity.onSyncedBlockEvent(type, data);
    }

    // ---------------------------------------------------------------- interaction

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof WrenchItem) {
            if (!world.isClient) toggleWaiting(state, world, pos, player);
            return ItemActionResult.SUCCESS;
        }
        // Empty hand: onUse (cycle the moment); any other item keeps its own use (placing redstone next to it...)
        return stack.isEmpty() ? ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            PartyMoment moment = state.get(MOMENT).next(player.isSneaking());
            BlockState newState = state.with(MOMENT, moment);
            world.setBlockState(pos, newState, Block.NOTIFY_ALL);
            world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.BLOCKS, 0.5f,
                    0.7f + 0.1f * moment.ordinal());
            showMode(player, newState);
        }
        return ActionResult.SUCCESS;
    }

    private static void toggleWaiting(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        PartyMoment moment = state.get(MOMENT);
        if (!moment.canWait()) {
            if (player instanceof ServerPlayerEntity serverPlayer)
                MessageUtils.sendToPlayer(serverPlayer, Text.translatableWithFallback("message.steveparty.party_bell.cannot_wait",
                        "The party cannot wait at this moment.").formatted(Formatting.RED), MessageUtils.MessageType.ACTION_BAR);
            return;
        }
        BlockState newState = state.with(WAITING, !state.get(WAITING));
        world.setBlockState(pos, newState, Block.NOTIFY_ALL);
        world.playSound(null, pos, SoundEvents.BLOCK_COPPER_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1.0f, 2.0f);
        showMode(player, newState);
    }

    private static void showMode(PlayerEntity player, BlockState state) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        PartyMoment moment = state.get(MOMENT);
        boolean waiting = state.get(WAITING) && moment.canWait();
        Text mode = waiting
                ? Text.translatableWithFallback("message.steveparty.party_bell.waiting", "party waits").formatted(Formatting.GOLD)
                : Text.translatableWithFallback("message.steveparty.party_bell.simple", "pulse").formatted(Formatting.GREEN);
        MessageUtils.sendToPlayer(serverPlayer, Text.translatableWithFallback("message.steveparty.party_bell.mode",
                        "Party Antenna: %1$s (%2$s) - comparator: %3$s",
                        moment.getText().copy().formatted(Formatting.YELLOW), mode,
                        Text.translatable(moment.valueDescriptionKey())),
                MessageUtils.MessageType.ACTION_BAR);
    }

    // ---------------------------------------------------------------- redstone

    /** Starts (or extends) the output pulse of the antenna at {@code pos}: a soft beep, its receiver lights. */
    static void pulse(ServerWorld world, BlockPos pos, BlockState state) {
        if (!state.get(POWERED))
            world.setBlockState(pos, state.with(POWERED, true), Block.NOTIFY_ALL);
        world.updateComparators(pos, state.getBlock());
        world.scheduleBlockTick(pos, state.getBlock(), PULSE_TICKS);
        world.addSyncedBlockEvent(pos, state.getBlock(), RING_EVENT, 0);
        world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.BLOCKS, 0.4f, 1.6f);
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (state.get(POWERED))
            world.setBlockState(pos, state.with(POWERED, false), Block.NOTIFY_ALL);
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        if (world.isClient || !(world.getBlockEntity(pos) instanceof PartyBellBlockEntity bell)) return;
        // While the antenna pulses, a wire it powers powers it back: that is not a signal sent to the antenna
        bell.onRedstoneInput(world.isReceivingRedstonePower(pos), state.get(POWERED));
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        // Placed next to a powered block: that is not a signal sent to the antenna
        if (!world.isClient && world.getBlockEntity(pos) instanceof PartyBellBlockEntity bell)
            bell.initRedstoneInput(world.isReceivingRedstonePower(pos));
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
    protected boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof PartyBellBlockEntity bell ? bell.getValue() : 0;
    }
}
