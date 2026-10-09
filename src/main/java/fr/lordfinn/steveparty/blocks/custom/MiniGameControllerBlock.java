package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Mini-game Controller block (see {@link MiniGameControllerBlockEntity}). A click with a Mini-game Page puts it
 * in when its slot is free; a sneaking click with empty hands takes the page back; any other click opens its screen. Putting in and taking out take the right to
 * build. No light.
 * <p>
 * Redstone: out of a party, a rising edge of its power plays or stops its mini-game, like the « Play » / « Stop » of its
 * screen; holding the power does nothing more, and while a party plays its mini-game the power is ignored. A
 * comparator reads what its mini-game is doing ({@link MiniGameControllerBlockEntity.Activity}): 0 nobody plays it (or
 * no page), 1 a party's practice round, 2 a party's real round, 3 played out of a party (countdown and round), 4 out
 * of a party, its results shown before everyone is brought back.
 * <p>
 * Its look: a referee on a cloud, facing whoever placed it ({@link #FACING}), holding the page it was given
 * ({@link #PAGE}) next to a lamp that tells what the mini-game of that page is doing ({@link #SIGNAL}). The block
 * entity keeps these two up to date ({@code MiniGameControllerBlockEntity#refreshState}).
 */
public class MiniGameControllerBlock extends Block implements BlockEntityProvider {
    /** The side its face looks at. */
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    /** A page is in the controller. */
    public static final BooleanProperty PAGE = BooleanProperty.of("page");
    public static final EnumProperty<Signal> SIGNAL = EnumProperty.of("signal", Signal.class);

    /** The lamp: what the mini-game of the page is doing. */
    public enum Signal implements StringIdentifiable {
        /** Nobody plays it (or no page). */
        RED("red"),
        /** A party plays its practice round. */
        ORANGE("orange"),
        /** It is being played: the real round of a party, or out of a party. */
        GREEN("green");

        private final String name;

        Signal(String name) {
            this.name = name;
        }

        @Override
        public String asString() {
            return name;
        }
    }

    /** The model, facing north: its base, the cloud, the referee's head, his lamp. In sixteenths of a block. */
    private static final double[][] BOXES = {{0, 0, 0, 16, 2, 16}, {1, 2, 2, 15, 9, 14}, {6, 7, 3, 14, 15, 13}, {2, 8, 6, 6, 16, 10}};
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Type.HORIZONTAL) {
            // Quarter turns clockwise (seen from above) from north, like the block state's model rotation
            int turns = switch (facing) {
                case EAST -> 1;
                case SOUTH -> 2;
                case WEST -> 3;
                default -> 0;
            };
            VoxelShape shape = VoxelShapes.empty();
            for (double[] box : BOXES) {
                double x1 = box[0], z1 = box[2], x2 = box[3], z2 = box[5];
                for (int i = 0; i < turns; i++) {
                    double nx1 = 16 - z2, nz1 = x1, nx2 = 16 - z1, nz2 = x2;
                    x1 = nx1;
                    z1 = nz1;
                    x2 = nx2;
                    z2 = nz2;
                }
                shape = VoxelShapes.union(shape, Block.createCuboidShape(x1, box[1], z1, x2, box[4], z2));
            }
            SHAPES.put(facing, shape);
        }
    }

    public MiniGameControllerBlock(Settings settings) {
        super(settings);
        setDefaultState(stateManager.getDefaultState().with(FACING, Direction.NORTH).with(PAGE, false).with(SIGNAL, Signal.RED));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, PAGE, SIGNAL);
    }

    /** Its face looks at whoever places it. */
    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
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
        return SHAPES.get(state.get(FACING));
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof MiniGameControllerBlockEntity controller)) return ActionResult.PASS;
        if (!player.isSneaking()) {
            player.openHandledScreen(controller);
            return ActionResult.SUCCESS;
        }
        ItemStack held = controller.getPage();
        if (held.isEmpty()) return ActionResult.PASS;
        if (!MiniGamePages.canEdit(player)) {
            player.sendMessage(Text.translatable("message.steveparty.mini_game_controller.no_build").formatted(Formatting.RED), true);
            return ActionResult.SUCCESS;
        }
        if (controller.isLockedFor(player)) {
            player.sendMessage(Text.translatable("message.steveparty.mini_game_controller.locked").formatted(Formatting.RED), true);
            return ActionResult.SUCCESS;
        }
        controller.setPage(ItemStack.EMPTY);
        player.getInventory().offerOrDrop(held);
        world.playSound(null, pos, SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 1f, 1f);
        return ActionResult.SUCCESS;
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        // The Wrench is not for this block
        if (stack.getItem() instanceof WrenchItem) return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (!MiniGamePages.isPage(stack) || hand == Hand.OFF_HAND) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (world.isClient) return ItemActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof MiniGameControllerBlockEntity controller)) return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        // Its slot is taken: the screen (where it can be swapped)
        if (!controller.getPage().isEmpty()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!MiniGamePages.canEdit(player)) {
            player.sendMessage(Text.translatable("message.steveparty.mini_game_controller.no_build").formatted(Formatting.RED), true);
            return ItemActionResult.SUCCESS;
        }
        if (controller.isLockedFor(player)) {
            player.sendMessage(Text.translatable("message.steveparty.mini_game_controller.locked").formatted(Formatting.RED), true);
            return ItemActionResult.SUCCESS;
        }
        if (!controller.accepts(stack)) {
            player.sendMessage(Text.translatable("message.steveparty.mini_game_controller.other_home").formatted(Formatting.RED), true);
            return ItemActionResult.SUCCESS;
        }
        controller.setPage(stack.split(1));
        world.playSound(null, pos, SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 1f, 1f);
        return ItemActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip,
                              TooltipType options) {
        super.appendTooltip(stack, context, tooltip, options);
        for (String line : List.of("page", "play", "redstone")) {
            tooltip.add(Text.translatable("tooltip.steveparty.mini_game_controller." + line).formatted(Formatting.GRAY));
        }
    }

    /** It is placed: the power it receives then is not an edge (it acts on the next one). */
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient && world.getBlockEntity(pos) instanceof MiniGameControllerBlockEntity controller) {
            controller.initPower(world.isReceivingRedstonePower(pos));
        }
    }

    /** Redstone: only a change of its power is looked at (no polling); a rising edge acts out of a party. */
    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        if (!world.isClient && world.getBlockEntity(pos) instanceof MiniGameControllerBlockEntity controller) {
            controller.onPower(world.isReceivingRedstonePower(pos));
        }
    }

    @Override
    protected boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    /** What its mini-game is doing ({@link MiniGameControllerBlockEntity.Activity}). */
    @Override
    protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof MiniGameControllerBlockEntity controller ? controller.comparatorOutput() : 0;
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() != newState.getBlock() && world.getBlockEntity(pos) instanceof MiniGameControllerBlockEntity controller) {
            controller.onBroken();
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MiniGameControllerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient || type != ModBlockEntities.MINI_GAME_CONTROLLER_ENTITY) return null;
        return (w, pos, s, blockEntity) -> {
            if (w instanceof ServerWorld serverWorld && blockEntity instanceof MiniGameControllerBlockEntity controller)
                controller.serverTick(serverWorld);
        };
    }
}
