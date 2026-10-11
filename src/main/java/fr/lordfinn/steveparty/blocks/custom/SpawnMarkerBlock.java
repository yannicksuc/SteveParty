package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeSpawnMarker;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import fr.lordfinn.steveparty.service.MarkerResidents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationPropertyHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The Spawn Bubble (its code names stay « spawn marker », so that the worlds keep theirs): a thin blue plate (its
 * outline and its collision, {@link #SHAPE}) under a floating soap bubble, where the mob of a board space appears,
 * facing its way ({@link #ROTATION}: one of 16, like a sign, toward whoever placed it; the plate marks the nearest of 8,
 * the mob in the bubble the exact one). Linked to a mob space with the Tile Linker Brush (from the space to the bubble,
 * see CartridgeLinks.SpawnPoint); a space without one summons its mob beside it, as always. Broken by hand; broken, its
 * bubble pops and the spaces linking it forget it.
 * <p>
 * Its bubble ({@link #bubbleCenter}) floats in the middle of the block, lifted as set; the mob appears
 * {@link #SPAWN_UNDER_BUBBLE} under its centre ({@link #spawnPos}: on the plate by default). It has no collision and
 * cannot be aimed at: only its plate can.
 * <p>
 * An empty hand opens its menu (client side, {@link #openMenu}): when its mob shows ({@link SpawnMarkerBlockEntity#isResident}:
 * only when a token lands, or all the party long, see {@link MarkerResidents}) and how high above it the mob appears
 * ({@link SpawnMarkerBlockEntity#getLift}); sneaking, it turns a sixteenth of a turn (22.5°) clockwise. Any item in hand acts as that item (the
 * brush links it, a block is placed against it).
 */
public class SpawnMarkerBlock extends BlockWithEntity {
    public static final MapCodec<SpawnMarkerBlock> CODEC = createCodec(SpawnMarkerBlock::new);
    /**
     * The way its mob faces, in 16 steps of 22.5° like a sign's: 0 south, 4 west, 8 north, 12 east (see
     * {@link RotationPropertyHelper}). The plate marks the nearest of 8 (blockstate: an odd step shows the one before it); the mob in the bubble shows the exact one.
     */
    public static final IntProperty ROTATION = Properties.ROTATION;
    /** The compass point of each step, from north clockwise: the end of its lang key. */
    private static final String[] COMPASS = {"n", "nne", "ne", "ene", "e", "ese", "se", "sse", "s", "ssw", "sw", "wsw", "w", "wnw", "nw", "nnw"};
    /** The plate: 14 x 14 pixels, 2 high. */
    private static final VoxelShape SHAPE = Block.createCuboidShape(1, 0, 1, 15, 2, 15);
    /** How high the plate is (blocks). */
    public static final double PLATE_HEIGHT = 2 / 16.0;
    /** How high the centre of the bubble is above the bottom of the block, unlifted (blocks): the block's centre. */
    public static final double BUBBLE_HEIGHT = 8 / 16.0;
    /** How far under the bubble's centre its mob appears (blocks): on the plate when unlifted. */
    public static final double SPAWN_UNDER_BUBBLE = 6 / 16.0;
    /** Opens the menu of the marker at a position: set by the client. */
    public static Consumer<BlockPos> openMenu = pos -> {
    };

    public SpawnMarkerBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(ROTATION, rotation(Direction.NORTH)));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ROTATION);
    }

    /** Its mob will face whoever placed it. */
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(ROTATION, RotationPropertyHelper.fromYaw(ctx.getPlayerYaw() + 180));
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(ROTATION, rotation.rotate(state.get(ROTATION), RotationPropertyHelper.getMax() + 1));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.with(ROTATION, mirror.mirror(state.get(ROTATION), RotationPropertyHelper.getMax() + 1));
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    /** Its plate only: the bubble floats free (a mob appears standing on the plate). */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SpawnMarkerBlockEntity(pos, state);
    }

    /** The centre of its bubble {@code lift} blocks up ({@link SpawnMarkerBlockEntity#getLift}): the block's centre unlifted. */
    public static Vec3d bubbleCenter(BlockPos pos, double lift) {
        return Vec3d.ofBottomCenter(pos).add(0, BUBBLE_HEIGHT + lift, 0);
    }

    /** Where its mob appears unlifted: {@link #SPAWN_UNDER_BUBBLE} under its bubble's centre, standing on the plate. */
    public static Vec3d standPos(BlockPos pos) {
        return bubbleCenter(pos, 0).subtract(0, SPAWN_UNDER_BUBBLE, 0);
    }

    /** Where its mob appears: under its bubble, lifted as set ({@link SpawnMarkerBlockEntity#getLift}). */
    public static Vec3d spawnPos(BlockView world, BlockPos pos) {
        double lift = world.getBlockEntity(pos) instanceof SpawnMarkerBlockEntity marker ? marker.getLift() : 0;
        return standPos(pos).add(0, lift, 0);
    }

    /**
     * The way its mob faces (a yaw from 0 to 360: 0 south, 90 west, 180 north, 270 east, like {@link Direction#asRotation};
     * {@link RotationPropertyHelper#toDegrees} wraps it to ±180).
     */
    public static float yaw(BlockState state) {
        return state.contains(ROTATION) ? (RotationPropertyHelper.toDegrees(state.get(ROTATION)) + 360) % 360 : 0;
    }

    /** The {@link #ROTATION} facing a horizontal direction (north: 8). */
    public static int rotation(Direction direction) {
        return RotationPropertyHelper.fromYaw(direction.asRotation());
    }

    /** The compass point of a {@link #ROTATION} ("n", "nne", "ne"...), the end of its lang key. */
    public static String compass(int rotation) {
        return COMPASS[(rotation + 8) & 15];
    }

    /** Any item in hand: that item's use (the brush links it, a block is placed against it...). */
    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
                                             Hand hand, BlockHitResult hit) {
        return stack.isEmpty() ? ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** An empty hand: its menu; sneaking, turned a sixteenth of a turn clockwise (22.5°). */
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof SpawnMarkerBlockEntity)) return ActionResult.PASS;
        if (world.isClient) {
            if (!player.isSneaking()) openMenu.accept(pos);
            return ActionResult.SUCCESS;
        }
        if (!ScreenHandlerChecks.canBuildAt(player, pos)) {
            player.sendMessage(Text.translatable("message.steveparty.spawn_marker.locked"), true);
            return ActionResult.CONSUME;
        }
        if (player.isSneaking()) {
            BlockState turned = state.with(ROTATION, (state.get(ROTATION) + 1) & RotationPropertyHelper.getMax());
            world.setBlockState(pos, turned, Block.NOTIFY_ALL);
            player.sendMessage(Text.translatable("message.steveparty.spawn_marker.turned",
                    Text.translatable("message.steveparty.spawn_marker.facing." + compass(turned.get(ROTATION)))), true);
            world.playSound(null, pos, SoundEvents.BLOCK_WOOD_HIT, SoundCategory.BLOCKS, 0.6f, 1.4f);
            if (world instanceof ServerWorld serverWorld) MarkerResidents.refresh(serverWorld, pos);
            return ActionResult.SUCCESS;
        }
        return ActionResult.SUCCESS;
    }

    /** Placed (by hand, a command, a paste...): its bubble comes out of the plate, inflating (on the clients around). */
    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (!world.isClient && !oldState.isOf(this)) world.addSyncedBlockEvent(pos, this, SpawnMarkerBlockEntity.INFLATE, 0);
    }

    /** Broken (or replaced): its bubble pops, the spaces linked to it forget it, its resident mob goes. */
    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world instanceof ServerWorld serverWorld) {
            double lift = 0;
            if (world.getBlockEntity(pos) instanceof SpawnMarkerBlockEntity marker) {
                lift = marker.getLift();
                for (BlockPos tile : marker.getLinkers()) CartridgeSpawnMarker.forget(serverWorld, tile, pos);
            }
            pop(serverWorld, bubbleCenter(pos, lift));
            MarkerResidents.remove(serverWorld, pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    /** A bubble popping at {@code center}: a little pop, a few droplets. */
    private static void pop(ServerWorld world, Vec3d center) {
        world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.BLOCKS,
                1.0f, 1.3f + world.getRandom().nextFloat() * 0.3f);
        world.spawnParticles(ParticleTypes.BUBBLE_POP, center.x, center.y, center.z, 6, 0.18, 0.18, 0.18, 0.02);
        world.spawnParticles(ParticleTypes.SPLASH, center.x, center.y, center.z, 10, 0.2, 0.15, 0.2, 0.1);
    }
}
