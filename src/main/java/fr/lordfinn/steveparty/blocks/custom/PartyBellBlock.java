package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
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
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;

/**
 * Party bell (see {@link PartyBellBlockEntity}).
 * <ul>
 *     <li>Right-click (empty hand): next moment listened to; sneaking: previous one.</li>
 *     <li>Right-click with a wrench: simple mode (pulse only) / waiting mode (the party pauses at that moment until
 *     the bell receives a redstone signal).</li>
 *     <li>Output: a pulse of power 15 on every side; comparator: value of the last moment heard.</li>
 * </ul>
 */
public class PartyBellBlock extends Block implements BlockEntityProvider {
    public static final MapCodec<PartyBellBlock> CODEC = Block.createCodec(PartyBellBlock::new);
    public static final EnumProperty<PartyMoment> MOMENT = EnumProperty.of("moment", PartyMoment.class);
    public static final BooleanProperty WAITING = BooleanProperty.of("waiting");
    public static final BooleanProperty POWERED = Properties.POWERED;
    /** Length of the pulse: 2 redstone ticks. */
    public static final int PULSE_TICKS = 4;

    /** A reception desk bell (dome and plunger) on the redstone plate of the other redstone blocks. */
    private static final VoxelShape SHAPE = VoxelShapes.union(
            Block.createCuboidShape(0, 0, 0, 16, 2, 16),
            Block.createCuboidShape(4, 2, 4, 12, 5, 12),
            Block.createCuboidShape(6, 5, 6, 10, 7, 10)
    );

    public PartyBellBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(MOMENT, PartyMoment.TURN_START)
                .with(WAITING, false)
                .with(POWERED, false));
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(MOMENT, WAITING, POWERED);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PartyBellBlockEntity(pos, state);
    }

    // ---------------------------------------------------------------- interaction

    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof WrenchItem) {
            if (!world.isClient) toggleWaiting(state, world, pos, player);
            return ActionResult.SUCCESS;
        }
        // Empty hand: onUse (cycle the moment); any other item keeps its own use (placing redstone next to it...)
        return stack.isEmpty() ? ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION : ActionResult.PASS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            PartyMoment moment = state.get(MOMENT).next(player.isSneaking());
            BlockState newState = state.with(MOMENT, moment);
            world.setBlockState(pos, newState, Block.NOTIFY_ALL);
            world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.BLOCKS, 0.6f,
                    0.6f + 0.1f * moment.ordinal());
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
                ? Text.translatableWithFallback("message.steveparty.party_bell.waiting", "waits for a signal").formatted(Formatting.GOLD)
                : Text.translatableWithFallback("message.steveparty.party_bell.simple", "pulse").formatted(Formatting.GREEN);
        MessageUtils.sendToPlayer(serverPlayer, Text.translatableWithFallback("message.steveparty.party_bell.mode",
                        "Party bell: %1$s (%2$s) - comparator: %3$s",
                        moment.getText().copy().formatted(Formatting.YELLOW), mode,
                        Text.translatable(moment.valueDescriptionKey())),
                MessageUtils.MessageType.ACTION_BAR);
    }

    // ---------------------------------------------------------------- redstone

    /** Starts (or extends) the output pulse of the bell at {@code pos}. */
    static void pulse(ServerWorld world, BlockPos pos, BlockState state) {
        if (!state.get(POWERED))
            world.setBlockState(pos, state.with(POWERED, true), Block.NOTIFY_ALL);
        world.updateComparators(pos, state.getBlock());
        world.scheduleBlockTick(pos, state.getBlock(), PULSE_TICKS);
        world.playSound(null, pos, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 0.5f, 1.4f);
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (state.get(POWERED))
            world.setBlockState(pos, state.with(POWERED, false), Block.NOTIFY_ALL);
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, @Nullable WireOrientation wireOrientation, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, wireOrientation, notify);
        if (world.isClient || !(world.getBlockEntity(pos) instanceof PartyBellBlockEntity bell)) return;
        // While the bell pulses, a wire it powers powers it back: that is not a signal sent to the bell
        bell.onRedstoneInput(world.isReceivingRedstonePower(pos), state.get(POWERED));
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        // Placed next to a powered block: that is not a signal sent to the bell
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
