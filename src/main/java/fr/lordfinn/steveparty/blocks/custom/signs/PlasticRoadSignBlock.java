package fr.lordfinn.steveparty.blocks.custom.signs;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.tick.ScheduledTickView;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.Direction;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.nbt.NbtCompound;
import fr.lordfinn.steveparty.blocks.custom.PlasticBlock;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

/**
 * Plastic road sign: a round, square, diamond, star or heart plate of one of the 16 plastic colours on top of any
 * fence or wall ({@link SignPosts}, plastic fences included), turning in 16 directions. Every plate fills the 16x16
 * pixel grid (the diamond is the square turned by 45°). Stencils paint on the plate. The wrench changes the plate.
 * <p>
 * Made of plastic: unless it is on a post (standing on a fence or a wall, or hung on its side), it floats like the
 * plastic studs ({@link PlasticBlock}). Under water it rises, lies flat on the surface, is pressed flat under a block
 * that stops it, lies on the magma that pulls it down; a chain holds it. Like them it needs nothing to hold it.
 */
public class PlasticRoadSignBlock extends AbstractStencilSignBlock {
    public static final MapCodec<PlasticRoadSignBlock> CODEC = createCodec(PlasticRoadSignBlock::new);
    public static final EnumProperty<Plate> PLATE = EnumProperty.of("plate", Plate.class);

    public enum Plate implements StringIdentifiable {
        ROUND("round"), SQUARE("square"), DIAMOND("diamond"), TRIANGLE("triangle"), STAR("star"), HEART("heart");

        private final String name;
        private byte[] mask;

        Plate(String name) {
            this.name = name;
        }

        /** @return the plate's pixels on the 16x16 grid, as a stencil shape (the diamond is a square, turned when drawn). */
        public byte[] mask() {
            if (mask == null) {
                mask = switch (this) {
                    case ROUND -> StencilShape.fromRows(
                            ".....######.....",
                            "...##########...",
                            "..############..",
                            ".##############.",
                            ".##############.",
                            "################",
                            "################",
                            "################",
                            "################",
                            "################",
                            "################",
                            ".##############.",
                            ".##############.",
                            "..############..",
                            "...##########...",
                            ".....######.....");
                    case SQUARE, DIAMOND -> StencilShape.full();
                    case TRIANGLE -> StencilShape.fromRows(
                            ".......##.......",
                            ".......##.......",
                            "......####......",
                            "......####......",
                            ".....######.....",
                            ".....######.....",
                            "....########....",
                            "....########....",
                            "...##########...",
                            "...##########...",
                            "..############..",
                            "..############..",
                            ".##############.",
                            ".##############.",
                            "################",
                            "################");
                    case STAR -> StencilShape.fromRows(
                            "......####......",
                            "......####......",
                            ".....######.....",
                            "################",
                            "################",
                            "################",
                            ".##############.",
                            ".##############.",
                            "..############..",
                            "..############..",
                            ".##############.",
                            ".##############.",
                            "#######..#######",
                            "######....######",
                            "#####......#####",
                            "####........####");
                    case HEART -> StencilShape.fromRows(
                            "................",
                            ".#####....#####.",
                            "#######..#######",
                            "################",
                            "################",
                            "################",
                            "################",
                            ".##############.",
                            ".##############.",
                            "..############..",
                            "...##########...",
                            "....########....",
                            ".....######.....",
                            "......####......",
                            ".......##.......",
                            "................");
                };
            }
            return mask.clone();
        }

        /** @return true if the plate is drawn turned by 45° (the diamond). */
        public boolean turned() {
            return this == DIAMOND;
        }

        @Override
        public String asString() {
            return name;
        }

        public Plate next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Plate in model space (pixels, front facing north), in front of the post. */
    public static final float PLATE_Z = 3, PLATE_DEPTH = 1;
    private static final SignShapes.Box PLATE_BOX = new SignShapes.Box(0, 0, PLATE_Z, 16, 16, PLATE_Z + PLATE_DEPTH);
    private static final SignShapes.BoardOutline OUTLINE = new SignShapes.BoardOutline(PLATE_BOX);

    public PlasticRoadSignBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState().with(PLATE, Plate.ROUND));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(PLATE);
    }

    @Override
    public boolean hangsOnPosts() {
        return true;
    }

    @Override
    public float boardBack() {
        return (float) (PLATE_Z + PLATE_DEPTH);
    }

    @Override
    protected boolean canStandAt(WorldView world, BlockPos pos) {
        return SignPosts.standsOnPost(world, pos);
    }

    /** @return whether the sign is free to float: not standing on a post nor hung on one. */
    public static boolean floats(BlockState state) {
        Mount mount = state.get(MOUNT);
        return mount != Mount.POST && mount != Mount.HUNG;
    }

    @Override
    protected boolean needsSupport(BlockState state) {
        return !floats(state);
    }

    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (floats(state)) world.scheduleBlockTick(pos, this, PlasticBlock.getDelay(world, pos));
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos,
                                                   Direction direction, BlockPos neighborPos, BlockState neighborState, Random random) {
        // Water arriving, a bubble column forming, or a chain holding it being broken: try again
        if (floats(state)) tickView.scheduleBlockTick(pos, this, PlasticBlock.getDelay(world, pos));
        return super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (floats(state)) PlasticBlock.drift(world, pos, this, PlasticRoadSignBlock::step);
    }

    /** One step of a floating sign, like a stud's: it carries its block entity (plate, symbol) along. */
    @Nullable
    private static BlockPos step(BlockState state, ServerWorld world, BlockPos pos) {
        if (!floats(state) || !state.get(WATERLOGGED) || PlasticBlock.isChained(world, pos)) return null;
        BlockPos target;
        BlockState moved;
        if (PlasticBlock.getCurrent(world, pos) == PlasticBlock.Current.DOWN) {
            target = pos.down();
            if (!PlasticBlock.canSinkInto(world.getBlockState(target))) {
                turn(state, world, pos, Mount.FLOOR); // lies on the magma that pulled it down
                return null;
            }
            moved = state;
        } else {
            target = pos.up();
            BlockState above = world.getBlockState(target);
            if (PlasticBlock.canRiseInto(above)) {
                moved = state; // still under water
            } else if (above.isAir()) {
                moved = state.with(WATERLOGGED, false).with(MOUNT, Mount.FLOOR); // floats flat on the surface
            } else {
                turn(state, world, pos, Mount.CEILING); // pressed flat under the block that stops it
                return null;
            }
        }
        NbtCompound data = world.getBlockEntity(pos) instanceof StencilCanvasBlockEntity canvas
                ? canvas.createNbt(world.getRegistryManager()) : null;
        if (!PlasticBlock.moveWithRiders(world, pos, target, moved)) return PlasticBlock.retry(world, pos, state);
        if (data != null && world.getBlockEntity(target) instanceof StencilCanvasBlockEntity canvas) {
            canvas.read(data, world.getRegistryManager());
            canvas.markDirty();
            world.updateListeners(target, moved, moved, Block.NOTIFY_ALL);
        }
        return target;
    }

    /** Lays the sign flat in place (it keeps its water and its block entity). */
    private static void turn(BlockState state, ServerWorld world, BlockPos pos, Mount mount) {
        if (state.get(MOUNT) != mount) world.setBlockState(pos, state.with(MOUNT, mount), Block.NOTIFY_ALL);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return OUTLINE.get(this, world, pos, state);
    }

    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
                                         Hand hand, BlockHitResult hit) {
        if (hand == Hand.MAIN_HAND && stack.isOf(ModItems.WRENCH)) {
            if (!world.isClient) {
                world.setBlockState(pos, state.with(PLATE, state.get(PLATE).next()), Block.NOTIFY_ALL);
                world.playSound(null, pos, SoundEvents.BLOCK_BAMBOO_WOOD_HIT, SoundCategory.BLOCKS, 1.0F, 1.2F);
            }
            return ActionResult.SUCCESS;
        }
        return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
        ItemStack stack = super.getPickStack(world, pos, state);
        stack.set(DataComponentTypes.BLOCK_STATE, BlockStateComponent.DEFAULT.with(PLATE, state.get(PLATE)));
        return stack;
    }
}
