package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Pie's nest, woven twigs, turned to face whoever places it. It holds coins and shiny things
 * ({@link MagpieNestBlockEntity}): a click with coins or a shiny thing puts the stack in (sneaking: one), an empty
 * hand takes back (on its own) the last shiny thing, then its coins; hoppers fill it too. Each thing in it is one more
 * coin on its pile
 * ({@link MagpieNestPile}, drawn by its block entity renderer, as high as they go: the block stays a nest).
 * <p>
 * Set near a Common pot space (within {@link fr.lordfinn.steveparty.service.CommonPots#NEST_RADIUS} blocks), it
 * becomes that pot's nest: all it holds is the pot, the Pie lives on it. Wild Pies sleep in a free one at night and
 * bring it the shiny things they find.
 */
public class MagpieNestBlock extends BlockWithEntity {
    public static final MapCodec<MagpieNestBlock> CODEC = createCodec(MagpieNestBlock::new);
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    /** The height of its rim, in pixels: where the Pie stands in an empty nest. */
    public static final int HEIGHT = 5;
    private static final VoxelShape SHAPE = Block.createCuboidShape(1, 0, 1, 15, HEIGHT, 15);

    public MagpieNestBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    /** The twigs are a block model; the coins and the shiny things, its block entity renderer. */
    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MagpieNestBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, ModBlockEntities.MAGPIE_NEST_ENTITY, MagpieNestBlockEntity::tick);
    }

    /** Coins or a shiny thing in hand: in it goes (the stack, sneaking: one). */
    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
                                             Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof MagpieNestBlockEntity nest)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!nest.isCoin(stack) && !nest.isTreasure(stack)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (world.isClient) return ItemActionResult.SUCCESS;
        if (!nest.accepts(stack)) {
            player.sendMessage(Text.translatable("message.steveparty.magpie_nest.full").formatted(Formatting.GRAY), true);
            return ItemActionResult.CONSUME;
        }
        ItemStack offered = stack.copyWithCount(player.isSneaking() ? 1 : stack.getCount());
        ItemStack left = nest.insert(offered);
        int put = offered.getCount() - left.getCount();
        stack.decrementUnlessCreative(put, player);
        clink(world, pos, nest.isCoin(offered));
        return ItemActionResult.SUCCESS;
    }

    /** An empty hand: the last shiny thing back, then its own coins (a pot's coins stay: the Pie guards them). */
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof MagpieNestBlockEntity nest)) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        ItemStack treasure = nest.takeTreasure();
        if (!treasure.isEmpty()) {
            player.getInventory().offerOrDrop(treasure);
            clink(world, pos, false);
            return ActionResult.SUCCESS;
        }
        if (nest.isLinked()) {
            if (nest.getPileCount() > 0) {
                player.sendMessage(Text.translatable("message.steveparty.magpie_nest.guarded").formatted(Formatting.GRAY), true);
            }
            return ActionResult.CONSUME;
        }
        int coins = nest.takeCoins(64);
        if (coins <= 0) return ActionResult.PASS;
        player.getInventory().offerOrDrop(new ItemStack(fr.lordfinn.steveparty.items.ModItems.COIN, coins));
        clink(world, pos, true);
        return ActionResult.SUCCESS;
    }

    private static void clink(World world, BlockPos pos, boolean coins) {
        world.playSound(null, pos, coins ? SoundEvents.BLOCK_CHAIN_PLACE : SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS,
                0.6F, coins ? 1.8F : 1.4F);
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.WAX_OFF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.2, 0.15, 0.2, 0);
        }
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof MagpieNestBlockEntity nest) {
            nest.dropContents(world, pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    protected boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof MagpieNestBlockEntity nest ? nest.comparatorOutput() : 0;
    }
}
