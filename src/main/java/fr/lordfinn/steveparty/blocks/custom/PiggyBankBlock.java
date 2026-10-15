package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainer;
import net.minecraft.block.*;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Piggy bank (see {@link PiggyBankBlockEntity}).
 * <ul>
 *     <li>Wrench (or cartridge) + right-click: its menu (inventory cartridge slot, target).</li>
 *     <li>Right-click (empty hand): the same menu, where its target is chosen (player of the turn, winners,
 *     everyone, nearest player).</li>
 *     <li>Redstone pulse: gives / takes the cartridge items.</li>
 * </ul>
 */
public class PiggyBankBlock extends CartridgeContainer {
    public static final MapCodec<PiggyBankBlock> CODEC = Block.createCodec(PiggyBankBlock::new);
    /** The pig for each facing: the metal plate it stands on, its body down to the hooves, and its head with the snout and the ears. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Type.HORIZONTAL)
            SHAPES.put(facing, VoxelShapes.union(Block.createCuboidShape(0, 0, 0, 16, 2, 16), turned(facing, 3, 2, 5, 13, 12, 15),
                    turned(facing, 4, 5, 0, 12, 15, 5)));
    }

    /** A box of the model (which faces north), turned around the middle of the block to face {@code facing}. */
    private static VoxelShape turned(Direction facing, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return switch (facing) {
            case SOUTH -> Block.createCuboidShape(16 - maxX, minY, 16 - maxZ, 16 - minX, maxY, 16 - minZ);
            case EAST -> Block.createCuboidShape(16 - maxZ, minY, minX, 16 - minZ, maxY, maxX);
            case WEST -> Block.createCuboidShape(minZ, minY, 16 - maxX, maxZ, maxY, 16 - minX);
            default -> Block.createCuboidShape(minX, minY, minZ, maxX, maxY, maxZ);
        };
    }

    public PiggyBankBlock(Settings settings) {
        super(settings, 1);
        setDefaultState(getStateManager().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
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
        return SHAPES.get(state.get(HorizontalFacingBlock.FACING));
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PiggyBankBlockEntity(pos, state);
    }

    /** Server side: an old Piggy Bank links itself once to its party ({@link PiggyBankBlockEntity#serverTick}). */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient || type != ModBlockEntities.PIGGY_BANK_ENTITY) return null;
        return (tickWorld, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof PiggyBankBlockEntity bank && tickWorld instanceof ServerWorld serverWorld) bank.serverTick(serverWorld);
        };
    }

    @Override
    protected ItemActionResult onUseWithoutCartridgeContainerOpener(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        // Other items keep their own use; an empty hand goes to onUse (its menu)
        return stack.isEmpty() ? ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** An empty hand opens its menu (its cartridge, its target), as the Wrench does. */
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        return openContainerScreen(state, world, pos, player);
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
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
