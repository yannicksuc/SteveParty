package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.telescope.TelescopeService;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationPropertyHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Telescope: a spyglass on a tall tripod, pointing the way its placer looked (16 ways). A click, at night under
 * the open sky: the player comes to its eyepiece, looks through it and can replay the past nights of shooting stars
 * (TelescopeService; the view and the game are drawn by his client alone). One player at a time: the others see him
 * bent at the eyepiece, the tube following his eyes.
 * <p>
 * Two blocks high: the lower half holds it (block entity, drop), the upper one, invisible, is only there to be aimed
 * at, so that the tripod's head and the tube can be clicked and broken. Both halves have the whole telescope as their
 * outline. Sneaking with an empty hand picks it up.
 */
public class TelescopeBlock extends BlockWithEntity {
    public static final MapCodec<TelescopeBlock> CODEC = createCodec(TelescopeBlock::new);
    public static final IntProperty ROTATION = Properties.ROTATION;
    public static final EnumProperty<DoubleBlockHalf> HALF = Properties.DOUBLE_BLOCK_HALF;
    /** Only its middle stops a player (he stands close to the eyepiece). */
    private static final VoxelShape COLLISION = Block.createCuboidShape(6, 0, 6, 10, 24, 10);
    /** The tripod: its splayed legs and its head, up to the tube's pivot. */
    private static final VoxelShape TRIPOD = Block.createCuboidShape(1, 0, 1, 15, 33, 15);
    /** Its outline seen from the lower half, by rotation: the tripod and the tube at rest, the way it was placed. */
    private static final VoxelShape[] SHAPES = new VoxelShape[16];
    public static final String[] TOOLTIP_LINES = {"what", "use", "wheel", "track", "guide", "take"};

    static {
        // The tube (TelescopeModel): 0.7 ahead of its pivot and 0.6 behind, tilted up by its rest pitch
        double pitch = Math.toRadians(35), radius = 0.1;
        double up = Math.sin(pitch), flat = Math.cos(pitch), pivot = 31 / 16.0;
        for (int rotation = 0; rotation < 16; rotation++) {
            double yaw = Math.toRadians(RotationPropertyHelper.toDegrees(rotation));
            double dx = -Math.sin(yaw) * flat, dz = Math.cos(yaw) * flat;
            double fx = 0.5 + dx * 0.7, fz = 0.5 + dz * 0.7, fy = pivot + up * 0.7;
            double bx = 0.5 - dx * 0.6, bz = 0.5 - dz * 0.6, by = pivot - up * 0.6;
            VoxelShape tube = VoxelShapes.cuboid(Math.min(fx, bx) - radius, by - radius, Math.min(fz, bz) - radius,
                    Math.max(fx, bx) + radius, fy + radius, Math.max(fz, bz) + radius);
            SHAPES[rotation] = VoxelShapes.union(TRIPOD, tube);
        }
    }

    public TelescopeBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(ROTATION, 0).with(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ROTATION, HALF);
    }

    /** Where the telescope stands (its lower half), from either half. */
    public static BlockPos base(BlockState state, BlockPos pos) {
        return state.contains(HALF) && state.get(HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos;
    }

    /** It is taller than a block: it needs room over it, for its upper half. */
    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos pos = ctx.getBlockPos();
        World world = ctx.getWorld();
        if (pos.getY() >= world.getTopY() - 1 || !world.getBlockState(pos.up()).canReplace(ctx)) return null;
        return getDefaultState().with(ROTATION, RotationPropertyHelper.fromYaw(ctx.getPlayerYaw()));
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        world.setBlockState(pos.up(), state.with(HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
    }

    /** One half gone: the other goes too (the upper half drops nothing, the lower one the telescope). */
    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                   WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.get(HALF);
        if (direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN)) {
            if (!neighborState.isOf(this) || neighborState.get(HALF) == half) return Blocks.AIR.getDefaultState();
            return state.with(ROTATION, neighborState.get(ROTATION));
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        if (state.get(HALF) == DoubleBlockHalf.LOWER) return true;
        BlockState below = world.getBlockState(pos.down());
        return below.isOf(this) && below.get(HALF) == DoubleBlockHalf.LOWER;
    }

    /** Broken by its upper half in creative: the lower one goes without dropping anything. */
    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient && player.isCreative() && state.get(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.down();
            BlockState lower = world.getBlockState(below);
            if (lower.isOf(this) && lower.get(HALF) == DoubleBlockHalf.LOWER) {
                world.setBlockState(below, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
                world.syncWorldEvent(player, 2001, below, Block.getRawIdFromState(lower));
            }
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(ROTATION, rotation.rotate(state.get(ROTATION), 16));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.with(ROTATION, mirror.mirror(state.get(ROTATION), 16));
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        VoxelShape shape = SHAPES[state.get(ROTATION)];
        return state.get(HALF) == DoubleBlockHalf.LOWER ? shape : shape.offset(0, -1, 0);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(HALF) == DoubleBlockHalf.LOWER ? COLLISION : VoxelShapes.empty();
    }

    /** Nothing rests on it: its outline is the whole telescope, not sides to put things on. */
    @Override
    protected VoxelShape getSidesShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.empty();
    }

    /** It lets the sky through: the night sky is seen over it. */
    @Override
    protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, ModBlockEntities.TELESCOPE_ENTITY, TelescopeBlockEntity::tick);
    }

    /** Drawn by its block entity renderer (the tube turns freely). */
    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    protected boolean canPathfindThrough(BlockState state, NavigationType type) {
        return false;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return state.get(HALF) == DoubleBlockHalf.LOWER ? new TelescopeBlockEntity(pos, state) : null;
    }

    /** A click: look through it; sneaking with an empty hand: pick it up. */
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        BlockPos base = base(state, pos);
        if (player.isSneaking() && player.getMainHandStack().isEmpty()) {
            if (world instanceof ServerWorld serverWorld) pickUp(serverWorld, base, player);
            return ActionResult.SUCCESS;
        }
        if (player instanceof ServerPlayerEntity serverPlayer) TelescopeService.use(serverPlayer, base);
        return ActionResult.SUCCESS;
    }

    /**
     * Back into the player's hand: both halves gone without breaking (no tool, nothing dropped on the ground), unless
     * someone else looks through it.
     *
     * @return true if it was picked up
     */
    public static boolean pickUp(ServerWorld world, BlockPos base, PlayerEntity player) {
        BlockState lower = world.getBlockState(base);
        if (!(lower.getBlock() instanceof TelescopeBlock) || !player.canModifyBlocks()) return false;
        if (world.getBlockEntity(base) instanceof TelescopeBlockEntity telescope && telescope.getWatcher() != null
                && !telescope.getWatcher().equals(player.getUuid())
                && TelescopeService.isAt(world, base, telescope.getWatcher())) {
            player.sendMessage(Text.translatable("message.steveparty.telescope.busy"), true);
            return false;
        }
        // the lower half first: the upper one then goes by itself, dropping nothing
        world.setBlockState(base, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
        world.playSound(null, base, lower.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 0.8f, 1.1f);
        if (!player.getAbilities().creativeMode) {
            ItemStack stack = new ItemStack(lower.getBlock());
            if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
        }
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        for (String line : TOOLTIP_LINES) {
            tooltip.add(Text.translatable("tooltip.steveparty.telescope." + line).formatted(Formatting.GRAY));
        }
    }
}
