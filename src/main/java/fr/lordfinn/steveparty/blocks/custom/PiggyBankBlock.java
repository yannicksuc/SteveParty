package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainer;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;

/**
 * Piggy bank (see {@link PiggyBankBlockEntity}).
 * <ul>
 *     <li>Wrench (or cartridge) + right-click: its inventory cartridge slot.</li>
 *     <li>Right-click (empty hand): next target (player of the turn, winners, everyone, nearest player).</li>
 *     <li>Redstone pulse: gives / takes the cartridge items.</li>
 * </ul>
 */
public class PiggyBankBlock extends CartridgeContainer {
    public static final MapCodec<PiggyBankBlock> CODEC = Block.createCodec(PiggyBankBlock::new);
    private static final VoxelShape SHAPE = VoxelShapes.union(
            Block.createCuboidShape(3, 0, 4, 13, 9, 12),
            Block.createCuboidShape(5, 9, 7, 11, 10, 9)
    );

    public PiggyBankBlock(Settings settings) {
        super(settings, 1);
        setDefaultState(getStateManager().getDefaultState().with(HorizontalFacingBlock.FACING, net.minecraft.util.math.Direction.NORTH));
    }

    @Override
    protected MapCodec<PiggyBankBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HorizontalFacingBlock.FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(HorizontalFacingBlock.FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(HorizontalFacingBlock.FACING, rotation.rotate(state.get(HorizontalFacingBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(HorizontalFacingBlock.FACING)));
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PiggyBankBlockEntity(pos, state);
    }

    @Override
    protected ActionResult onUseWithoutCartridgeContainerOpener(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        // Other items keep their own use; an empty hand goes to onUse (target)
        return stack.isEmpty() ? ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION : ActionResult.PASS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof PiggyBankBlockEntity bank) {
            bank.cycleTarget();
            world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 0.6f,
                    0.8f + 0.2f * bank.getTarget().ordinal());
            if (player instanceof ServerPlayerEntity serverPlayer)
                MessageUtils.sendToPlayer(serverPlayer, Text.translatableWithFallback("message.steveparty.piggy_bank.target",
                        "Piggy bank: gives to %s", bank.getTarget().getText().copy().formatted(Formatting.YELLOW)),
                        MessageUtils.MessageType.ACTION_BAR);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, @Nullable WireOrientation wireOrientation, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, wireOrientation, notify);
        if (!world.isClient && world.getBlockEntity(pos) instanceof PiggyBankBlockEntity bank)
            bank.onRedstoneInput(world.isReceivingRedstonePower(pos));
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient && world.getBlockEntity(pos) instanceof PiggyBankBlockEntity bank)
            bank.initRedstoneInput(world.isReceivingRedstonePower(pos));
    }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof PiggyBankBlockEntity bank && bank.hasLastSucceeded() ? 15 : 0;
    }
}
