package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;
import java.util.List;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class BoardSpaceRedstoneRouterBlock extends CartridgeContainer {
    public static final MapCodec<BoardSpaceRedstoneRouterBlock> CODEC = Block.createCodec(BoardSpaceRedstoneRouterBlock::new);
    public BoardSpaceRedstoneRouterBlock(Settings settings) {
        super(settings, 1);
    }

    @Override
    protected ItemActionResult onUseWithoutCartridgeContainerOpener(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BoardSpaceRedstoneRouterBlockEntity(pos, state);
    }

    @Override
    protected MapCodec<BoardSpaceRedstoneRouterBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        if (world.isClient) return;
        if (world.getBlockEntity(pos) instanceof BoardSpaceRedstoneRouterBlockEntity entity) {
            entity.pushPowerToBoardSpaces();
        }
    }

    /** Not its fill level: the pulse of the last board event (see BoardSpaceRedstoneRouterBlockEntity#pulse). */
    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof BoardSpaceRedstoneRouterBlockEntity router ? router.getComparatorSignal() : 0;
    }

    /** The end of a comparator pulse. */
    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (world.getBlockEntity(pos) instanceof BoardSpaceRedstoneRouterBlockEntity router) router.onPulseTick();
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        super.appendTooltip(stack, context, tooltip, options);
        tooltip.add(Text.translatable("tooltip.steveparty.router.input").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.steveparty.router.output").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.steveparty.router.levels").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.translatable("tooltip.steveparty.router.loop").formatted(Formatting.DARK_GRAY));
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) { // Block is changed or removed
            if (!world.isClient && world.getBlockEntity(pos) instanceof BoardSpaceRedstoneRouterBlockEntity entity) {
                entity.unroute();
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

}
