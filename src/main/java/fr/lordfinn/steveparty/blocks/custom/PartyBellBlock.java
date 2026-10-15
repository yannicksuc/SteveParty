package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import fr.lordfinn.steveparty.payloads.custom.OpenAntennaScreenPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
 *     <li>Right-click with the Wrench: its screen (moment, pulse or waiting mode, filter, test). During a party, only
 *     for who placed it, creative players and operators.</li>
 *     <li>Right-click with an empty hand: nothing changes, it says it is set with the Wrench.</li>
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
    /** Synced block events: it caught a moment (its receiver lights, its dish pulses); a click without the Wrench (it blinks). */
    public static final int RING_EVENT = 1, BLINK_EVENT = 2;
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
            if (player instanceof ServerPlayerEntity serverPlayer && world.getBlockEntity(pos) instanceof PartyBellBlockEntity antenna) {
                if (antenna.maySet(serverPlayer)) ServerPlayNetworking.send(serverPlayer, new OpenAntennaScreenPayload(pos));
                else MessageUtils.sendToPlayer(serverPlayer, Text.translatable("message.steveparty.party_bell.not_yours")
                        .formatted(Formatting.RED), MessageUtils.MessageType.ACTION_BAR);
            }
            return ItemActionResult.SUCCESS;
        }
        // Empty hand: onUse (says it is set with the Wrench); any other item keeps its own use (redstone next to it...)
        return stack.isEmpty() ? ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** Empty hand: nothing changes (reading it never sets it); it says it is set with the Wrench, and blinks once. */
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            player.sendMessage(Text.translatable("message.steveparty.wrench_required").withColor(0xFFD24A), true);
            world.addSyncedBlockEvent(pos, state.getBlock(), BLINK_EVENT, 0);
        }
        return ActionResult.SUCCESS;
    }

    /**
     * Sets the antenna as its screen says: the moment it listens to, its waiting mode (off for a moment that never
     * waits), its filter.
     */
    public static void apply(World world, BlockPos pos, PartyMoment moment, boolean waiting, PartyBellBlockEntity.Filter filter, int filterValue) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof PartyBellBlock) || !(world.getBlockEntity(pos) instanceof PartyBellBlockEntity antenna)) return;
        BlockState newState = state.with(MOMENT, moment).with(WAITING, waiting && moment.canWait());
        if (newState != state) {
            world.setBlockState(pos, newState, Block.NOTIFY_ALL);
            world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.BLOCKS, 0.4f, 0.8f + 0.04f * moment.ordinal());
        }
        if (filter != antenna.getFilter() || filterValue != antenna.getFilterValue()) antenna.setFilter(filter, filterValue);
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
        if (world.getBlockEntity(pos) instanceof PartyBellBlockEntity antenna) antenna.onPulseEnd();
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
        if (!world.isClient && world.getBlockEntity(pos) instanceof PartyBellBlockEntity bell) {
            bell.initRedstoneInput(world.isReceivingRedstonePower(pos));
            if (placer != null) bell.setPlacer(placer.getUuid());
        }
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
