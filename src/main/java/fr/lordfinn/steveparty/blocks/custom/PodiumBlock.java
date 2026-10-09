package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.ItemResults;
import fr.lordfinn.steveparty.items.custom.MiniGamePageItem;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import fr.lordfinn.steveparty.minigame.MiniGamePodiumLink;
import fr.lordfinn.steveparty.podium.Podiums;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Podium (see {@link PodiumBlockEntity} and {@link Podiums}): it records who came first, second, third... in a
 * mini-game.
 * <ul>
 *     <li>Four looks: classic, gold, silver and bronze. The look says nothing about the place.</li>
 *     <li>A podium is a slab; another podium put on it makes it a full block. Podiums stacked on one another make one
 *     podium (a column): its settings (who is registered, what a signal does, its banner) are kept by its bottom
 *     block. A column looks like one piece: the plate on its top only, the plinth at its foot only, and its banner
 *     hanging from its top over one block of height, across two blocks when its top is a slab (a slab alone only has
 *     a label).</li>
 *     <li>The columns touching each other, and the columns linked to the same mini-game page (page in hand, click),
 *     form a group. <b>The taller the column, the better the place</b>: the tallest is the 1st place, the next height
 *     the 2nd... Columns of the same height share the place.</li>
 *     <li>Sneaking on its top, or right-clicking it, registers the player on it; again, unregisters him. One podium
 *     per player (per team in a team mini-game); a podium held by someone else can be taken.</li>
 *     <li>Redstone into any block of the column: what the Wrench set (right click with it): register the nearest
 *     player on it, give him the highest free place of the group, empty the column, or reset the group. Sneak +
 *     right click with the Wrench resets the group.</li>
 *     <li>The banner hanging on its front can be stamped with a stencil (Stencil Hammer, or a stencil and a dye),
 *     washed with a wet sponge.</li>
 *     <li>Comparator: 15 while someone is registered on the column.</li>
 * </ul>
 */
public class PodiumBlock extends Block implements BlockEntityProvider {
    public static final EnumProperty<Direction> FACING = HorizontalFacingBlock.FACING;
    /** False: a slab (the podium's first level); true: a full block. */
    public static final BooleanProperty FULL = BooleanProperty.of("full");
    /** Top block of its column: the one with the plate and the banner, the one players stand on. */
    public static final BooleanProperty TOP = BooleanProperty.of("top");
    /** Foot of a piece: it doesn't rest on a full podium (a full block shows its plinth, a top slab a label). */
    public static final BooleanProperty BASE = BooleanProperty.of("base");
    /** Full block right under the top slab of its column: the lower half of that slab's banner hangs on it. */
    public static final EnumProperty<BannerTail> BANNER_TAIL = EnumProperty.of("banner_tail", BannerTail.class);
    private static final VoxelShape SLAB = Block.createCuboidShape(0, 0, 0, 16, 8, 16);
    private static final VoxelShape FULL_SHAPE = VoxelShapes.fullCube();

    /** The look of a podium. It gives no place: the height of the column does. */
    public enum Style implements StringIdentifiable {
        CLASSIC("classic"), GOLD("gold"), SILVER("silver"), BRONZE("bronze");

        private final String name;

        Style(String name) {
            this.name = name;
        }

        @Override
        public String asString() {
            return name;
        }
    }

    /** The banner of the slab above, by the look of that slab (podiums of different looks can be stacked). */
    public enum BannerTail implements StringIdentifiable {
        NONE("none"), CLASSIC("classic"), GOLD("gold"), SILVER("silver"), BRONZE("bronze");

        private final String name;

        BannerTail(String name) {
            this.name = name;
        }

        @Override
        public String asString() {
            return name;
        }

        static BannerTail of(Style style) {
            return switch (style) {
                case GOLD -> GOLD;
                case SILVER -> SILVER;
                case BRONZE -> BRONZE;
                case CLASSIC -> CLASSIC;
            };
        }
    }

    private final Style style;

    public PodiumBlock(Settings settings, Style style) {
        super(settings);
        this.style = style;
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH).with(FULL, false).with(TOP, true)
                .with(BASE, true).with(BANNER_TAIL, BannerTail.NONE));
    }

    public Style getStyle() {
        return style;
    }

    @Override
    protected MapCodec<PodiumBlock> getCodec() {
        return Block.createCodec(settings -> new PodiumBlock(settings, style));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, FULL, TOP, BASE, BANNER_TAIL);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(FULL) ? FULL_SHAPE : SLAB;
    }

    @Override
    protected boolean hasSidedTransparency(BlockState state) {
        return !state.get(FULL);
    }

    // ---------------------------------------------------------------- placement and column

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos pos = ctx.getBlockPos();
        BlockState existing = ctx.getWorld().getBlockState(pos);
        // Doubling a slab: the podium becomes a full block, same facing
        BlockState state = existing.isOf(this) && !existing.get(FULL)
                ? existing.with(FULL, true)
                : getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
        return below(above(state, ctx.getWorld().getBlockState(pos.up())), ctx.getWorld().getBlockState(pos.down()));
    }

    @Override
    protected boolean canReplace(BlockState state, ItemPlacementContext context) {
        if (state.get(FULL) || !context.getStack().isOf(this.asItem())) return false;
        // Like a slab: clicking its top doubles it, placing into it from the side too
        return !context.canReplaceExisting() || context.getSide() == Direction.UP;
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                   WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP) return above(state, neighborState);
        if (direction == Direction.DOWN) return below(state, neighborState);
        return state;
    }

    /** {@code state} under {@code above}: the top of its column or not, and the banner of a top slab hanging on it. */
    private static BlockState above(BlockState state, BlockState above) {
        boolean podium = isPodium(above);
        boolean tail = podium && state.get(FULL) && !above.get(FULL) && above.get(TOP);
        return state.with(TOP, !podium)
                .with(BANNER_TAIL, tail ? BannerTail.of(((PodiumBlock) above.getBlock()).getStyle()) : BannerTail.NONE);
    }

    /** {@code state} on {@code below}: only a full podium carries it (a slab leaves a gap under the next block). */
    private static BlockState below(BlockState state, BlockState below) {
        return state.with(BASE, !(isPodium(below) && below.get(FULL)));
    }

    /**
     * The banner of a column, seen on its top block: true when it hangs over one block of height (a full block, or a
     * slab on a full podium: over that slab and the upper half of the block below), false for the label of a slab.
     */
    public static boolean bannerHangs(BlockState top) {
        return top.get(FULL) || !top.get(BASE);
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    public static boolean isPodium(BlockState state) {
        return state.getBlock() instanceof PodiumBlock;
    }

    /** Bottom block of the column {@code pos} belongs to (it keeps the podium's settings). */
    public static BlockPos bottomOf(BlockView world, BlockPos pos) {
        BlockPos.Mutable cursor = pos.mutableCopy();
        while (isPodium(world.getBlockState(cursor.down()))) cursor.move(Direction.DOWN);
        return cursor.toImmutable();
    }

    /** Top block of the column {@code pos} belongs to (the one players stand on). */
    public static BlockPos topOf(BlockView world, BlockPos pos) {
        BlockPos.Mutable cursor = pos.mutableCopy();
        while (isPodium(world.getBlockState(cursor.up()))) cursor.move(Direction.UP);
        return cursor.toImmutable();
    }

    /** The block entity keeping the settings of the column {@code pos} belongs to. */
    public static @Nullable PodiumBlockEntity master(BlockView world, BlockPos pos) {
        return world.getBlockEntity(bottomOf(world, pos)) instanceof PodiumBlockEntity podium ? podium : null;
    }

    /** The height of the top of the column {@code pos} belongs to above its foot, in half blocks (a slab alone: 1). */
    public static int heightOf(BlockView world, BlockPos pos) {
        BlockPos top = topOf(world, pos);
        BlockState state = world.getBlockState(top);
        return (top.getY() - bottomOf(world, pos).getY()) * 2 + (isPodium(state) && state.get(FULL) ? 2 : 1);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PodiumBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        // Only the top of a column watches the players sneaking on it
        return world.isClient || !state.get(TOP) ? null : TickableBlockEntity.getTicker(world);
    }

    // ---------------------------------------------------------------- interaction

    /**
     * Decided the same way on both sides. The banner first (stencils, sponge), then the page (linking), the Wrench
     * (what a signal does); a block in hand is placed; anything else, or nothing: the player registers ({@link #onUse}).
     */
    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        ActionResult stamped = PodiumBanner.onUseWithItem(state, world, pos, player, hand, hit);
        if (stamped != null) return ItemResults.of(stamped);
        if (stack.getItem() instanceof MiniGamePageItem) {
            if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer)
                Podiums.clickLink(serverPlayer, hand, serverWorld, pos, MiniGamePodiumLink.Kind.PODIUM);
            return ItemActionResult.SUCCESS;
        }
        if (hand != Hand.MAIN_HAND) return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (stack.getItem() instanceof WrenchItem) {
            if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer)
                Podiums.cycleSignal(serverPlayer, serverWorld, pos);
            return ItemActionResult.SUCCESS;
        }
        return stack.getItem() instanceof BlockItem ? ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION : ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Right click: the player registers on the podium, or leaves it if it already shows him. */
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer)
            Podiums.toggle(serverPlayer, serverWorld, pos);
        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        super.appendTooltip(stack, context, tooltip, options);
        for (String line : List.of("places", "register", "signal", "page", "reset")) {
            tooltip.add(Text.translatable("tooltip.steveparty.podium." + line).formatted(Formatting.GRAY));
        }
    }

    // ---------------------------------------------------------------- redstone

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        if (world.isClient) return;
        PodiumBlockEntity podium = master(world, pos);
        if (podium != null) podium.onRedstoneInput(columnPower(world, pos) > 0);
    }

    /** The strongest power received by a block of the column from what is not a podium. */
    public static int columnPower(World world, BlockPos anyPos) {
        int power = 0;
        BlockPos top = topOf(world, anyPos);
        for (BlockPos pos = bottomOf(world, anyPos); pos.getY() <= top.getY(); pos = pos.up()) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.offset(direction);
                if (isPodium(world.getBlockState(neighbor))) continue;
                // Same convention as World#getReceivedRedstonePower: (neighbor pos, direction towards the neighbor)
                power = Math.max(power, world.getEmittedRedstonePower(neighbor, direction));
            }
        }
        return power;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient && master(world, pos) instanceof PodiumBlockEntity podium)
            podium.initRedstoneInput(columnPower(world, pos) > 0);
    }

    @Override
    protected boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        PodiumBlockEntity podium = master(world, pos);
        return podium != null && podium.getOccupant() != null ? 15 : 0;
    }
}
