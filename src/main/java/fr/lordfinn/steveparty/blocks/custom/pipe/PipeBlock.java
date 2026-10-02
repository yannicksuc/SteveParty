package fr.lordfinn.steveparty.blocks.custom.pipe;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A warp pipe (« tuyau »), see {@link PipeShape} for its shape.
 * <ul>
 *     <li>Placed, it joins the pipe it was placed against, and the neighbouring runs whose mouth faces it; placed
 *     against a solid block (and not a pipe), it is fixed to it.</li>
 *     <li>A block placed next to it never changes it; a pipe placed next to it joins it if that pipe joins it.</li>
 *     <li>At most one solid block per pipe: the Wrench cycles which one among the solid neighbours, then none.</li>
 *     <li>What goes in a mouth travels to another end of the network ({@link PipeTravel}).</li>
 *     <li>A click with a mini-game page links the pipe to the page, or unlinks it.</li>
 * </ul>
 */
public class PipeBlock extends Block implements BlockEntityProvider {
    public static final EnumProperty<PipeSolid> SOLID = EnumProperty.of("solid", PipeSolid.class);

    private final PipeKind kind;
    private final int color;

    public PipeBlock(PipeKind kind, int color, Settings settings) {
        super(settings);
        this.kind = kind;
        this.color = color;
        BlockState state = getStateManager().getDefaultState().with(SOLID, PipeSolid.NONE);
        for (Direction dir : Direction.values()) state = state.with(PipeShape.connection(dir), false);
        setDefaultState(state);
    }

    public PipeKind kind() {
        return kind;
    }

    /** Index of its colour in {@code ModBlocks.COLORS} (0 for the plain glass pipe). */
    public int color() {
        return color;
    }

    /**
     * Its colour for the warps: a capped end only warps to a pipe of the same colour. The plastic colour for plastic
     * pipes (opaque and windowed alike), each stained glass colour its own, the plain glass pipe its own.
     */
    public String warpColor() {
        return switch (kind) {
            case GLASS -> "glass";
            case STAINED_GLASS -> "stained_glass/" + color;
            case COPPER, IRON, GOLDEN -> kind.folder;
            default -> "plastic/" + color;
        };
    }

    public static String warpColor(BlockState state) {
        return state.getBlock() instanceof PipeBlock pipe ? pipe.warpColor() : "";
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return createCodec(settings -> new PipeBlock(kind, color, settings));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        for (Direction dir : Direction.values()) builder.add(PipeShape.connection(dir));
        builder.add(SOLID);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PipeBlockEntity(pos, state);
    }

    @Override
    public void appendTooltip(ItemStack stack, net.minecraft.item.Item.TooltipContext context, List<Text> tooltip, net.minecraft.item.tooltip.TooltipType options) {
        for (String line : new String[]{"enter", "way", "wrench"}) {
            tooltip.add(Text.translatable("tooltip.steveparty.pipe." + line).formatted(net.minecraft.util.Formatting.GRAY));
        }
    }

    // ---------------------------------------------------------------- placing and connections

    public static boolean isPipe(BlockState state) {
        return state.getBlock() instanceof PipeBlock;
    }

    /** Can a pipe be fixed to the block at {@code pos}, on its side {@code side}? */
    public static boolean isSolid(BlockView world, BlockPos pos, BlockState state, Direction side) {
        return !isPipe(state) && state.isSideSolidFullSquare(world, pos, side);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        Direction toward = ctx.getSide().getOpposite();
        BlockPos againstPos = pos.offset(toward);
        BlockState against = world.getBlockState(againstPos);
        BlockState state = getDefaultState();
        if (isPipe(against)) {
            state = state.with(PipeShape.connection(toward), true);
        } else if (isSolid(world, againstPos, against, ctx.getSide())) {
            state = state.with(SOLID, PipeSolid.of(toward));
        }
        // Runs ending right here (their mouth facing this block) are joined
        for (Direction dir : Direction.values()) {
            if (state.get(PipeShape.connection(dir))) continue;
            BlockState neighbour = world.getBlockState(pos.offset(dir));
            if (isPipe(neighbour) && PipeShape.mouth(neighbour, dir.getOpposite()) != null) {
                state = state.with(PipeShape.connection(dir), true);
            }
        }
        return state;
    }

    /**
     * Only a pipe next to it changes its connections: joined if that pipe joins it, else not. Anything else placed
     * next to it changes nothing (a pipe going away lets go of its neighbours itself, see {@link #onStateReplaced}),
     * so pipes set by commands or structures keep the connections they are given. A solid block going away frees it.
     */
    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos,
                                                   Direction direction, BlockPos neighborPos, BlockState neighborState, Random random) {
        boolean joins = isPipe(neighborState) && neighborState.get(PipeShape.connection(direction.getOpposite()));
        if (isPipe(neighborState) && joins != state.get(PipeShape.connection(direction))) state = state.with(PipeShape.connection(direction), joins);
        if (state.get(SOLID).direction() == direction && (joins || !isSolid(world, neighborPos, neighborState, direction.getOpposite()))) {
            state = state.with(SOLID, PipeSolid.NONE);
        }
        return state;
    }

    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (world instanceof ServerWorld serverWorld) PipeNetworks.changed(serverWorld, pos);
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (world instanceof ServerWorld serverWorld) {
            PipeNetworks.changed(serverWorld, pos);
            if (!newState.isOf(this) && world.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
                ItemScatterer.spawn(world, pos, pipe);
            }
            if (!isPipe(newState)) {
                // Gone: the pipes joined to it let go (their run ends here now)
                for (Direction dir : Direction.values()) {
                    if (!state.get(PipeShape.connection(dir))) continue;
                    BlockPos at = pos.offset(dir);
                    BlockState neighbour = world.getBlockState(at);
                    BooleanProperty back = PipeShape.connection(dir.getOpposite());
                    if (isPipe(neighbour) && neighbour.get(back)) world.setBlockState(at, neighbour.with(back, false), Block.NOTIFY_ALL);
                }
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    /**
     * The Wrench: the pipe lets go of its solid block and takes the next solid neighbour (down, up, north, south,
     * west, east), then none, then the first again.
     *
     * @return the solid block it is fixed to now
     */
    public static PipeSolid cycleSolid(World world, BlockPos pos, BlockState state) {
        List<PipeSolid> choices = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            BlockPos at = pos.offset(dir);
            if (!state.get(PipeShape.connection(dir)) && isSolid(world, at, world.getBlockState(at), dir.getOpposite())) {
                choices.add(PipeSolid.of(dir));
            }
        }
        choices.add(PipeSolid.NONE);
        int current = choices.indexOf(state.get(SOLID));
        PipeSolid next = choices.get((current + 1) % choices.size());
        if (next != state.get(SOLID)) world.setBlockState(pos, state.with(SOLID, next), Block.NOTIFY_ALL);
        return next;
    }

    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof WrenchItem) {
            if (!world.isClient) {
                PipeSolid solid = cycleSolid(world, pos, state);
                world.playSound(null, pos, SoundEvents.BLOCK_BAMBOO_WOOD_HIT, SoundCategory.BLOCKS, 1f, 1.3f);
                player.sendMessage(Text.translatable("message.steveparty.pipe.solid." + solid.asString()), true);
            }
            return ActionResult.SUCCESS;
        }
        // A mini-game page: the pipe is linked to it (or unlinked)
        if (stack.getItem() instanceof fr.lordfinn.steveparty.items.custom.MiniGamePageItem) {
            if (world instanceof ServerWorld serverWorld && player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                fr.lordfinn.steveparty.minigame.MiniGamePipes.click(serverPlayer, hand, serverWorld, pos);
            }
            return ActionResult.SUCCESS;
        }
        // A block in hand is placed against the pipe (to go on with the run); anything else: into the mouth
        return stack.getItem() instanceof BlockItem ? ActionResult.PASS : ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION;
    }

    /** Right click on a mouth: in you go. */
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        Direction mouth = PipeTravel.mouthFacing(state, pos, player.getEyePos());
        if (mouth == null) return ActionResult.PASS;
        if (world instanceof ServerWorld serverWorld && !PipeTravel.enter(serverWorld, pos, mouth, player, 0)) return ActionResult.PASS;
        return ActionResult.SUCCESS;
    }

    // ---------------------------------------------------------------- going in

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world instanceof ServerWorld serverWorld) PipeTravel.touch(serverWorld, pos, state, entity);
    }

    /** Landing in an upward mouth: in, without damage when it goes in. */
    @Override
    public void onLandedUpon(World world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if (world instanceof ServerWorld serverWorld && PipeTravel.land(serverWorld, pos, state, entity, fallDistance)) return;
        super.onLandedUpon(world, state, pos, entity, fallDistance);
    }

    // ---------------------------------------------------------------- shapes

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return PipeShape.shape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return PipeShape.shape(state);
    }

    @Override
    protected boolean isTransparent(BlockState state) {
        return true;
    }

    /**
     * Faces on the block's side hidden by the pipe next to it (two rims side by side): not drawn, when that pipe
     * covers them and can't be seen through (plastic), or is the same glass.
     */
    @Override
    protected boolean isSideInvisible(BlockState state, BlockState stateFrom, Direction direction) {
        if (!(stateFrom.getBlock() instanceof PipeBlock other)) return false;
        if (other.kind.isGlass() && other != this) return false;
        return VoxelShapes.isSideCovered(PipeShape.shape(state), PipeShape.shape(stateFrom), direction);
    }

    // ---------------------------------------------------------------- structures

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return transform(state, rotation::rotate);
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return transform(state, mirror::apply);
    }

    private BlockState transform(BlockState state, java.util.function.UnaryOperator<Direction> turn) {
        BlockState turned = state;
        for (Direction dir : Direction.values()) turned = turned.with(PipeShape.connection(turn.apply(dir)), state.get(PipeShape.connection(dir)));
        Direction solid = state.get(SOLID).direction();
        return turned.with(SOLID, PipeSolid.of(solid == null ? null : turn.apply(solid)));
    }
}
