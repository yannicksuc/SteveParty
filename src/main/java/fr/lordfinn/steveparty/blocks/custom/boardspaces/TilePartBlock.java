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
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/**
 * The 3 other blocks of a large (2x2) tile: they only hold the place (nothing can be put there), are aimed at and
 * collide like the tile (its surface over them: level, lowered, or sloped down from the tile's own block), and pass
 * every click to it. The tile itself (the board space: cartridges, destinations, tokens) is its own block, the anchor;
 * a part knows where it is from its {@code to_tile} direction. Breaking a part breaks the tile; the parts go with it.
 */
public class TilePartBlock extends Block {
    public static final MapCodec<TilePartBlock> CODEC = Block.createCodec(TilePartBlock::new);

    /** Where the tile is from the part: one of the 8 blocks around it. */
    public enum ToTile implements StringIdentifiable {
        NORTH("north", 0, -1), NORTH_EAST("north_east", 1, -1), EAST("east", 1, 0), SOUTH_EAST("south_east", 1, 1),
        SOUTH("south", 0, 1), SOUTH_WEST("south_west", -1, 1), WEST("west", -1, 0), NORTH_WEST("north_west", -1, -1);

        private final String name;
        private final int dx, dz;

        ToTile(String name, int dx, int dz) {
            this.name = name;
            this.dx = dx;
            this.dz = dz;
        }

        @Override
        public String asString() {
            return name;
        }

        public static ToTile of(int dx, int dz) {
            for (ToTile value : values()) if (value.dx == dx && value.dz == dz) return value;
            throw new IllegalArgumentException("Not next to the tile: " + dx + ", " + dz);
        }
    }

    public static final EnumProperty<ToTile> TO_TILE = EnumProperty.of("to_tile", ToTile.class);

    public TilePartBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(TO_TILE, ToTile.WEST));
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(TO_TILE);
    }

    /** The part at {@code part} of the tile at {@code tile}. */
    public static BlockState stateFor(BlockPos part, BlockPos tile) {
        return ModBlocks.TILE_PART.getDefaultState().with(TO_TILE, ToTile.of(tile.getX() - part.getX(), tile.getZ() - part.getZ()));
    }

    /** Whether {@code state} at {@code part} is a part of the tile at {@code tile}. */
    public static boolean isPartOf(BlockState state, BlockPos part, BlockPos tile) {
        return state.isOf(ModBlocks.TILE_PART) && master(state, part).equals(tile);
    }

    /** The position of the large tile this part belongs to. */
    public static BlockPos master(BlockState state, BlockPos pos) {
        ToTile toTile = state.get(TO_TILE);
        return pos.add(toTile.dx, 0, toTile.dz);
    }

    private static @Nullable BlockState tileOf(BlockView world, BlockState state, BlockPos pos) {
        BlockPos master = master(state, pos);
        BlockState tile = world.getBlockState(master);
        if (!(tile.getBlock() instanceof ATileBlock) || !tile.get(ATileBlock.SIZE).isLarge()) return null;
        // Really one of its blocks (the tile spreads toward this part)
        for (BlockPos part : tile.get(ATileBlock.SIZE).parts(master)) if (part.equals(pos)) return tile;
        return null;
    }

    /** Drawn by the tile's renderer. */
    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    /** The tile's surface over this block (a large tile on a slope goes down over its parts). */
    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        BlockState tile = tileOf(world, state, pos);
        if (tile == null) return VoxelShapes.cuboid(0, 0, 0, 1, TileSupport.THICKNESS, 1);
        BlockPos master = master(state, pos);
        return tile.get(ATileBlock.SUPPORT).outline(pos.getX() - master.getX(), pos.getZ() - master.getZ());
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        BlockState tile = tileOf(world, state, pos);
        if (tile == null) return VoxelShapes.cuboid(0, 0, 0, 1, TileSupport.THICKNESS, 1);
        BlockPos master = master(state, pos);
        return ATileBlock.collision(tile, pos.getX() - master.getX(), pos.getZ() - master.getZ(), context);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                   WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (tileOf(world, state, pos) == null) return Blocks.AIR.getDefaultState();
        // Its own support changed: the tile checks whether it still lies on one level surface
        if (direction == Direction.DOWN) {
            BlockPos master = master(state, pos);
            world.scheduleBlockTick(master, world.getBlockState(master).getBlock(), 1);
        }
        return state;
    }

    // ---------------------------------------------------------------- everything goes to the tile

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        BlockState tile = tileOf(world, state, pos);
        if (tile == null) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return tile.onUseWithItem(stack, world, player, hand, hit.withBlockPos(master(state, pos)));
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        BlockState tile = tileOf(world, state, pos);
        if (tile == null) return ActionResult.PASS;
        return tile.onUse(world, player, hit.withBlockPos(master(state, pos)));
    }

    /**
     * Breaking a part breaks the tile (which drops itself, like when broken directly: one item holding its cartridges,
     * their links kept with Silk Touch).
     */
    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        BlockState tile = world.isClient ? null : tileOf(world, state, pos);
        if (tile != null) {
            BlockPos master = master(state, pos);
            if (!player.isCreative() && world.getBlockEntity(master) instanceof BoardSpaceBlockEntity boardSpace) {
                // Dropped with the player's tool (Silk Touch keeps the links), then removed without a second drop
                boardSpace.keepContents();
                Block.dropStacks(tile, world, master, boardSpace, player, player.getMainHandStack());
                world.breakBlock(master, false, player);
            } else {
                if (world.getBlockEntity(master) instanceof BoardSpaceBlockEntity boardSpace) boardSpace.keepContents();
                world.breakBlock(master, false, player);
            }
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        BlockState tile = tileOf(world, state, pos);
        if (tile != null) return tile.calcBlockBreakingDelta(player, world, master(state, pos));
        return super.calcBlockBreakingDelta(state, player, world, pos);
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
        BlockState tile = tileOf(world, state, pos);
        if (tile != null) return tile.getBlock().getPickStack(world, master(state, pos), tile);
        return ItemStack.EMPTY;
    }

    /** The board space a part stands for: its tile, or null. */
    public static @Nullable BlockPos resolve(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(ModBlocks.TILE_PART)) return null;
        return tileOf(world, state, pos) != null ? master(state, pos) : null;
    }
}
