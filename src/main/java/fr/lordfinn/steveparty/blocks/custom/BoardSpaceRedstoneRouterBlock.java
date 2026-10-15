package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Aiguilleur: a small board space on the redstone plate with a railway switch lever. It drives the board spaces it
 * is linked to with the Tile Linker Brush (kept by the block, no cartridge, no screen). The power it receives is in its
 * state ({@link #POWER}): the lever leans with it (+90 degrees at 0 to -90 at 15, drawn by RouterLeverRenderer) and
 * its redstone torch is lit while it receives any.
 */
public class BoardSpaceRedstoneRouterBlock extends Block implements BlockEntityProvider {
    public static final MapCodec<BoardSpaceRedstoneRouterBlock> CODEC = Block.createCodec(BoardSpaceRedstoneRouterBlock::new);
    /** The redstone power it receives, as shown by its lever and torch. */
    public static final IntProperty POWER = Properties.POWER;
    /** The plate, the gold slab and the space, the lever's housing and the torch (the lever itself is left out). */
    private static final VoxelShape SHAPE = VoxelShapes.union(
            Block.createCuboidShape(0, 0, 0, 16, 2, 16),
            Block.createCuboidShape(1, 2, 1, 15, 3, 15),
            Block.createCuboidShape(2, 3, 2, 14, 4, 14),
            Block.createCuboidShape(5, 4, 6, 11, 10, 10),
            Block.createCuboidShape(7, 4, 10, 9, 14, 12));

    public BoardSpaceRedstoneRouterBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(POWER, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(POWER, ctx.getWorld().getReceivedRedstonePower(ctx.getBlockPos()));
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    /** The Wrench: nothing to open, said so. Any other item acts as on any block (the brush links from it). */
    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(stack.getItem() instanceof WrenchItem)) return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        linkedWithTheBrush(world, player);
        return ItemActionResult.SUCCESS;
    }

    /** An empty hand: nothing to open either. */
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        linkedWithTheBrush(world, player);
        return ActionResult.SUCCESS;
    }

    /** « Its spaces are linked with the Tile Linker Brush », in the action bar, to a player who may build. */
    private static void linkedWithTheBrush(World world, PlayerEntity player) {
        if (world.isClient || !player.canModifyBlocks() || player.isSpectator()) return;
        // The key colour of the tooltips, readable over a white tile
        player.sendMessage(Text.translatable("message.steveparty.router.brush").withColor(0xFFD24A), true);
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

    @Override
    protected boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    /** The pulse of the last board event (see BoardSpaceRedstoneRouterBlockEntity#pulse). */
    @Override
    protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
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
        Tooltips.of(tooltip)
                .summary("tooltip.steveparty.router.summary")
                .more(more -> more
                        .detail("tooltip.steveparty.router.input")
                        .detail("tooltip.steveparty.router.output")
                        .note("tooltip.steveparty.router.brush")
                        .note("tooltip.steveparty.router.levels")
                        .note("tooltip.steveparty.router.loop"));
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) { // Block is changed or removed
            if (!world.isClient && world.getBlockEntity(pos) instanceof BoardSpaceRedstoneRouterBlockEntity entity) {
                entity.unroute();
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
