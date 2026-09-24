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
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
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
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;

/**
 * Podium (see {@link PodiumBlockEntity}).
 * <ul>
 *     <li>Wrench (or cartridge) + right-click: its inventory cartridge slot (the reward of the winners).</li>
 *     <li>Right-click (empty hand): mode "first arrived" / "on signal".</li>
 *     <li>Output: a pulse each time a player steps on it; comparator: number of players standing on it.</li>
 * </ul>
 */
public class PodiumBlock extends CartridgeContainer {
    public static final MapCodec<PodiumBlock> CODEC = Block.createCodec(PodiumBlock::new);
    public static final BooleanProperty POWERED = Properties.POWERED;
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 12, 16);

    public PodiumBlock(Settings settings) {
        super(settings, 1);
        setDefaultState(getStateManager().getDefaultState().with(POWERED, false));
    }

    @Override
    protected MapCodec<PodiumBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PodiumBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : TickableBlockEntity.getTicker(world);
    }

    @Override
    protected ActionResult onUseWithoutCartridgeContainerOpener(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        return stack.isEmpty() ? ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION : ActionResult.PASS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof PodiumBlockEntity podium) {
            podium.cycleMode();
            world.playSound(null, pos, SoundEvents.BLOCK_COPPER_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1.0f, 1.5f);
            if (player instanceof ServerPlayerEntity serverPlayer)
                MessageUtils.sendToPlayer(serverPlayer, Text.translatableWithFallback("message.steveparty.podium.mode",
                        "Podium: %s", podium.getMode().getText().copy().formatted(Formatting.YELLOW)), MessageUtils.MessageType.ACTION_BAR);
        }
        return ActionResult.SUCCESS;
    }

    /** A 2 redstone tick pulse (a new arrival during the pulse extends it). */
    static void pulse(ServerWorld world, BlockPos pos, BlockState state) {
        if (!state.get(POWERED))
            world.setBlockState(pos, state.with(POWERED, true), Block.NOTIFY_ALL);
        world.scheduleBlockTick(pos, state.getBlock(), PartyBellBlock.PULSE_TICKS);
        world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.BLOCKS, 0.8f, 1.5f);
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
        if (!world.isClient && !state.get(POWERED) && world.getBlockEntity(pos) instanceof PodiumBlockEntity podium)
            podium.onRedstoneInput(world.isReceivingRedstonePower(pos));
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient && world.getBlockEntity(pos) instanceof PodiumBlockEntity podium)
            podium.initRedstoneInput(world.isReceivingRedstonePower(pos));
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
        return world.getBlockEntity(pos) instanceof PodiumBlockEntity podium ? Math.min(15, podium.getPlayersOnCount()) : 0;
    }
}
