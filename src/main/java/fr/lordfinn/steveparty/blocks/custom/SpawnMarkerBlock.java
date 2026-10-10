package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeSpawnMarker;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import fr.lordfinn.steveparty.service.MarkerResidents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The Spawn Marker: a little stake on a plate, all but invisible during a party, where the mob of a board space
 * appears, facing its way ({@link #FACING}: toward whoever placed it). Linked to a mob space with the Tile Linker
 * Brush (from the space to the marker, see CartridgeLinks.SpawnPoint); a space without one summons its mob beside it,
 * as always. Walked through, broken by hand; broken, the space forgets it.
 * <p>
 * An empty hand opens its menu (client side, {@link #openMenu}): when its mob shows ({@link SpawnMarkerBlockEntity#isResident}:
 * only when a token lands, or all the party long, see {@link MarkerResidents}) and how high above it the mob appears
 * ({@link SpawnMarkerBlockEntity#getLift}); sneaking, it turns a quarter. Any item in hand acts as that item (the
 * brush links it, a block is placed against it).
 */
public class SpawnMarkerBlock extends BlockWithEntity {
    public static final MapCodec<SpawnMarkerBlock> CODEC = createCodec(SpawnMarkerBlock::new);
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    private static final VoxelShape SHAPE = Block.createCuboidShape(5, 0, 5, 11, 3, 11);
    /** Opens the menu of the marker at a position: set by the client. */
    public static Consumer<BlockPos> openMenu = pos -> {
    };

    public SpawnMarkerBlock(Settings settings) {
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

    /** Its mob will face whoever placed it. */
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

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty();
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SpawnMarkerBlockEntity(pos, state);
    }

    /** The middle of its plate. */
    public static Vec3d standPos(BlockPos pos) {
        return Vec3d.ofBottomCenter(pos);
    }

    /** Where its mob appears: the middle of its plate, lifted as set ({@link SpawnMarkerBlockEntity#getLift}). */
    public static Vec3d spawnPos(BlockView world, BlockPos pos) {
        double lift = world.getBlockEntity(pos) instanceof SpawnMarkerBlockEntity marker ? marker.getLift() : 0;
        return standPos(pos).add(0, lift, 0);
    }

    /** The way its mob faces (a yaw). */
    public static float yaw(BlockState state) {
        return state.contains(FACING) ? state.get(FACING).asRotation() : 0;
    }

    /** Any item in hand: that item's use (the brush links it, a block is placed against it...). */
    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
                                             Hand hand, BlockHitResult hit) {
        return stack.isEmpty() ? ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** An empty hand: its menu; sneaking, turned a quarter. */
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
            BlockState turned = state.with(FACING, state.get(FACING).rotateYClockwise());
            world.setBlockState(pos, turned, Block.NOTIFY_ALL);
            player.sendMessage(Text.translatable("message.steveparty.spawn_marker.turned",
                    Text.translatable("message.steveparty.spawn_marker.facing." + turned.get(FACING).asString())), true);
            world.playSound(null, pos, SoundEvents.BLOCK_WOOD_HIT, SoundCategory.BLOCKS, 0.6f, 1.4f);
            if (world instanceof ServerWorld serverWorld) MarkerResidents.refresh(serverWorld, pos);
            return ActionResult.SUCCESS;
        }
        return ActionResult.SUCCESS;
    }

    /** Broken (or replaced): the space linked to it forgets it, its resident mob goes. */
    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world instanceof ServerWorld serverWorld) {
            if (world.getBlockEntity(pos) instanceof SpawnMarkerBlockEntity marker && marker.getOwner() != null) {
                CartridgeSpawnMarker.forget(serverWorld, marker.getOwner(), pos);
            }
            MarkerResidents.remove(serverWorld, pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
