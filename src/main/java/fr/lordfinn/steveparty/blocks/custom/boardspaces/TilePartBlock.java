package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
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

/**
 * The 3 other blocks of a large (2x2) tile: they only hold the place (nothing can be put there), look and collide like
 * the tile, and pass every click to it. The tile itself (the board space: cartridges, destinations, tokens) is the
 * north-west block. Breaking a part breaks the tile; the parts vanish with it.
 */
public class TilePartBlock extends Block {
    public static final MapCodec<TilePartBlock> CODEC = Block.createCodec(TilePartBlock::new);

    /** Where the part is from the tile (its north-west block). */
    public enum Part implements StringIdentifiable {
        EAST("east", 1, 0), SOUTH("south", 0, 1), SOUTH_EAST("south_east", 1, 1);

        private final String name;
        private final int dx, dz;

        Part(String name, int dx, int dz) {
            this.name = name;
            this.dx = dx;
            this.dz = dz;
        }

        @Override
        public String asString() {
            return name;
        }

        public BlockPos fromMaster(BlockPos master) {
            return master.add(dx, 0, dz);
        }

        public BlockPos toMaster(BlockPos part) {
            return part.add(-dx, 0, -dz);
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.of("part", Part.class);

    public TilePartBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(PART, Part.EAST));
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    public static BlockState stateFor(Part part) {
        return ModBlocks.TILE_PART.getDefaultState().with(PART, part);
    }

    public static boolean isPartOf(BlockState state, Part part) {
        return state.isOf(ModBlocks.TILE_PART) && state.get(PART) == part;
    }

    /** The position of the large tile this part belongs to. */
    public static BlockPos master(BlockState state, BlockPos pos) {
        return state.get(PART).toMaster(pos);
    }

    private static boolean hasMaster(BlockView world, BlockState state, BlockPos pos) {
        BlockState master = world.getBlockState(master(state, pos));
        return master.getBlock() instanceof ATileBlock && master.get(ATileBlock.SIZE) == TileSize.LARGE;
    }

    /** Drawn by the tile's renderer. */
    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    /** The same thin slab as the tile, at the tile's height (a large tile only lies level). */
    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        BlockState master = world.getBlockState(master(state, pos));
        if (master.getBlock() instanceof ATileBlock) return master.get(ATileBlock.SUPPORT).shape();
        return VoxelShapes.cuboid(0, 0, 0, 1, TileSupport.THICKNESS, 1);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos,
                                                   Direction direction, BlockPos neighborPos, BlockState neighborState, Random random) {
        if (!hasMaster(world, state, pos)) return Blocks.AIR.getDefaultState();
        // Its own support changed: the tile checks whether it still lies on one level surface
        if (direction == Direction.DOWN) {
            BlockPos master = master(state, pos);
            tickView.scheduleBlockTick(master, world.getBlockState(master).getBlock(), 1);
        }
        return state;
    }

    // ---------------------------------------------------------------- everything goes to the tile

    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        BlockPos master = master(state, pos);
        BlockState masterState = world.getBlockState(master);
        if (!(masterState.getBlock() instanceof ATileBlock)) return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION;
        return masterState.onUseWithItem(stack, world, player, hand, hit.withBlockPos(master));
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        BlockPos master = master(state, pos);
        BlockState masterState = world.getBlockState(master);
        if (!(masterState.getBlock() instanceof ATileBlock)) return ActionResult.PASS;
        return masterState.onUse(world, player, hit.withBlockPos(master));
    }

    /** Breaking a part breaks the tile (which drops itself, like when broken directly). */
    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        BlockPos master = master(state, pos);
        if (!world.isClient && world.getBlockState(master).getBlock() instanceof ATileBlock) {
            world.breakBlock(master, !player.isCreative(), player);
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        BlockPos master = master(state, pos);
        BlockState masterState = world.getBlockState(master);
        if (masterState.getBlock() instanceof ATileBlock) return masterState.calcBlockBreakingDelta(player, world, master);
        return super.calcBlockBreakingDelta(state, player, world, pos);
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
        BlockPos master = master(state, pos);
        BlockState masterState = world.getBlockState(master);
        if (masterState.getBlock() instanceof ATileBlock) return masterState.getBlock().getPickStack(world, master, masterState);
        return ItemStack.EMPTY;
    }

    /** The board space a part stands for: its tile, or null. */
    public static @Nullable BlockPos resolve(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(ModBlocks.TILE_PART)) return null;
        return hasMaster(world, state, pos) ? master(state, pos) : null;
    }
}
