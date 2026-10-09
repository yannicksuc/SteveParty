package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.BlockView;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TileStampComponent;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.minecraft.text.MutableText;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Tile and the Advanced Tile share: they face one of 8 directions (from where the placing player looks), and
 * lie on the real surface of the block under them ({@link TileSupport}): level on full blocks and top slabs, lowered
 * onto bottom slabs, snow or carpets, sloped on stairs.
 */
public abstract class ATileBlock extends ABoardSpaceBlock {
    public static final IntProperty ROTATION_8 = IntProperty.of("rotation_8", 0, 7);
    public static final EnumProperty<TileSupport> SUPPORT = EnumProperty.of("support", TileSupport.class);
    public static final EnumProperty<TileLayout> SIZE = EnumProperty.of("size", TileLayout.class);

    protected ATileBlock(Settings settings, int numberOfCartridges) {
        super(settings.nonOpaque(), numberOfCartridges);
        setDefaultState(getStateManager().getDefaultState().with(ROTATION_8, 0).with(SUPPORT, TileSupport.FLAT)
                .with(SIZE, TileLayout.STANDARD));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(ROTATION_8, SUPPORT, SIZE);
    }

    /** The translation key suffix of the line describing this tile in its tooltip. */
    protected abstract String tooltipKey();

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        super.appendTooltip(stack, context, tooltip, options);
        Tooltips tips = Tooltips.of(tooltip).tags(Tooltips.Tag.BOARD_SPACE);
        TileStampComponent stamp = TileContents.ownStamp(stack);
        if (stamp != null) tips.tags(Tooltips.Tag.STAMPED);
        appendContentsTooltip(stack, tips);
        if (stamp != null) tips.state("tooltip.steveparty.look", Tooltips.look(stamp.describe()));
        tips.summary("tooltip.steveparty.tile." + tooltipKey());
        tips.more(more -> more
                .detail(Text.translatable("tooltip.steveparty.tile.size",
                        Tooltips.value(Text.translatable("tooltip.steveparty.tile.size." + TileSize.of(stack).asString()))))
                .use(Tooltips.Keys.use(), "tooltip.steveparty.tile.use.cartridge")
                .use(Tooltips.Keys.of("tooltip.steveparty.key.stencil_dye"), "tooltip.steveparty.tile.stamp.hint")
                .craft("tooltip.steveparty.tile." + tooltipKey() + ".crafting")
                .craft("tooltip.steveparty.tile.size.hint")
                .note("tooltip.steveparty.tile.contents.hint"));
    }

    /**
     * What the tile item holds (see {@link TileContents}): its cartridge, or its cartridges (slot, name, main
     * setting), the one its preview shows now highlighted.
     */
    private static void appendContentsTooltip(ItemStack stack, Tooltips tips) {
        List<TileContents.Slot> cartridges = TileContents.cartridges(stack);
        if (cartridges.size() == 1) {
            tips.state("tooltip.steveparty.tile.contents.one", describe(cartridges.get(0).cartridge()).formatted(Tooltips.VALUE));
            return;
        }
        if (cartridges.isEmpty()) return;
        tips.state("tooltip.steveparty.tile.contents", Tooltips.value(Text.translatable("tooltip.steveparty.tile.contents.count", cartridges.size())));
        int shown = TileContents.previewedIndex(cartridges.size());
        for (int i = 0; i < cartridges.size(); i++) {
            TileContents.Slot slot = cartridges.get(i);
            boolean previewed = i == shown;
            tips.state(Text.literal(previewed ? "▶ " : "  ")
                    .append(Text.translatable("tooltip.steveparty.tile.contents.slot", slot.slot() + 1, describe(slot.cartridge())))
                    .formatted(previewed ? Formatting.YELLOW : Tooltips.TEXT));
        }
    }

    /** A cartridge's name, then the setting that tells it apart. */
    private static MutableText describe(ItemStack cartridge) {
        MutableText text = cartridge.getName().copy();
        Text setting = mainSetting(cartridge);
        if (setting != null) text.append(Text.literal(" · ")).append(setting);
        return text;
    }

    /** The setting that tells a cartridge apart: its number of spaces, its links, its stamped look. */
    private static @Nullable Text mainSetting(ItemStack cartridge) {
        List<Text> parts = new ArrayList<>();
        if (cartridge.getItem() instanceof AdvanceBackCartridgeItem) {
            int steps = AdvanceBackCartridgeItem.steps(cartridge);
            parts.add(Text.translatable("tooltip.steveparty.tile.contents.steps", (steps > 0 ? "+" : "") + steps));
        }
        int links = BoardLinks.links(cartridge).size();
        if (links > 0) parts.add(Text.translatable("tooltip.steveparty.tile.contents.links", links));
        if (cartridge.contains(ModComponents.TILE_STAMP)) parts.add(Text.literal("\u270E"));
        if (parts.isEmpty()) return null;
        MutableText text = Text.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) text.append(Text.literal(", "));
            text.append(parts.get(i));
        }
        return text;
    }

    /**
     * The picked tile keeps its size; in creative with Ctrl or Shift held, it is a copy of the tile with its contents
     * (see {@link TileContents}; the vanilla pick block with Ctrl adds the same).
     */
    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
        ItemStack stack = TileSize.with(super.getPickStack(world, pos, state), state.get(SIZE).size());
        if (world.isClient() && TileContents.pickWithContents.getAsBoolean()
                && world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity tile) {
            TileContents.copyOf(stack, tile, world);
            TileSize.with(stack, state.get(SIZE).size());
        }
        return stack;
    }

    /**
     * A broken tile drops itself in its size, with its cartridges (their settings, colour...) and its look, so that it
     * is placed again ready to link. Its cartridges lose their links (they led to the neighbours of the old place)
     * unless it is broken with Silk Touch, which moves the tile with its links.
     */
    @Override
    protected List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        List<ItemStack> drops = super.getDroppedStacks(state, builder);
        BlockEntity blockEntity = builder.getOptional(LootContextParameters.BLOCK_ENTITY);
        ItemStack tool = builder.getOptional(LootContextParameters.TOOL);
        boolean keepLinks = tool != null && TileContents.hasSilkTouch(builder.getWorld(), tool);
        for (ItemStack drop : drops) {
            if (!drop.isOf(asItem())) continue;
            if (blockEntity instanceof BoardSpaceBlockEntity tile) {
                // Its cartridges go with the item instead of spilling out
                tile.keepContents();
                drop.applyComponentsFrom(blockEntity.createComponentMap());
                if (!keepLinks) TileContents.dropLinks(drop);
            }
            TileSize.with(drop, state.get(SIZE).size());
        }
        return drops;
    }

    /** Broken by a player who gets its item (or in creative): the cartridges stay in the tile, not spilled out. */
    @Override
    protected boolean dropsContentsOnBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!keepsContents(world, state, player) || !(world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity tile)) return true;
        tile.keepContents();
        return false;
    }

    /**
     * Whether {@code player} breaking the tile {@code state} keeps its cartridges in it: it drops for them (in
     * creative, nothing drops and nothing spills).
     */
    public static boolean keepsContents(World world, BlockState state, PlayerEntity player) {
        return !world.isClient && (player.isCreative() || player.canHarvest(state));
    }

    // ---------------------------------------------------------------- placement and support

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        TileLayout layout = layoutFor(ctx);
        return getDefaultState()
                .with(ROTATION_8, rotation8FromYaw(ctx.getPlayerYaw()))
                .with(SIZE, layout)
                .with(SUPPORT, support(ctx.getWorld(), ctx.getBlockPos(), layout));
    }

    /** The layout of the tile placed by {@code ctx}: its item's size, and for a large one the side it spreads to. */
    public static TileLayout layoutFor(ItemPlacementContext ctx) {
        TileSize size = TileSize.of(ctx.getStack());
        if (size != TileSize.LARGE) return TileLayout.of(size);
        return TileLayout.largeFor(TileSupport.compute(ctx.getWorld(), ctx.getBlockPos()), ctx.getBlockPos(), ctx.getHitPos());
    }

    /**
     * The support of a tile laid out as {@code layout} at {@code pos}. A large tile follows the slope under its own
     * block (its highest one) when it spreads down it; on level ground it is lowered only when its 4 blocks lie on the
     * same level surface (a floor of bottom slabs...); otherwise it lies flat.
     */
    public static TileSupport support(BlockView world, BlockPos pos, TileLayout layout) {
        TileSupport support = TileSupport.compute(world, pos);
        if (!layout.isLarge()) return support;
        if (support.isSloped()) return layout.goesDown(support) ? support : TileSupport.FLAT;
        for (BlockPos part : layout.parts(pos)) {
            if (TileSupport.compute(world, part) != support) return TileSupport.FLAT;
        }
        return support;
    }

    /** The support under the tile changed (placed, broken, a slab turned double...): follow its surface. */
    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                   WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        BlockState updated = super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
        if (direction == Direction.DOWN && updated.isOf(this)) {
            return updated.with(SUPPORT, support(world, pos, updated.get(SIZE)));
        }
        return updated;
    }

    /** A large tile's part saw its support change: the whole tile checks its support again. */
    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        TileSupport support = support(world, pos, state.get(SIZE));
        if (state.get(SUPPORT) != support) world.setBlockState(pos, state.with(SUPPORT, support), Block.NOTIFY_ALL);
    }

    /**
     * Placed or replaced without a player too (commands, structures, the test world): reads the support there, and
     * a large tile takes the 3 other blocks of its 2x2 (it shrinks back to the standard size if they are not free).
     */
    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (world.isClient) return;
        if (state.get(SIZE).isLarge() && !claimParts(world, pos, state.get(SIZE))) {
            world.setBlockState(pos, state.with(SIZE, TileLayout.STANDARD), Block.NOTIFY_ALL);
            return;
        }
        TileSupport support = support(world, pos, state.get(SIZE));
        if (state.get(SUPPORT) != support) world.setBlockState(pos, state.with(SUPPORT, support), Block.NOTIFY_ALL);
    }

    /** Whether the other blocks of a large tile at {@code anchor} are free (or already its parts). */
    public static boolean partsFree(BlockView world, BlockPos anchor, TileLayout layout) {
        for (BlockPos part : layout.parts(anchor)) {
            BlockState there = world.getBlockState(part);
            if (!TilePartBlock.isPartOf(there, part, anchor) && !there.isReplaceable()) return false;
        }
        return true;
    }

    /** Fills the 3 other blocks of a large tile with its parts, if they are free (or already its parts). */
    private boolean claimParts(World world, BlockPos anchor, TileLayout layout) {
        if (!partsFree(world, anchor, layout)) return false;
        for (BlockPos part : layout.parts(anchor)) {
            if (!TilePartBlock.isPartOf(world.getBlockState(part), part, anchor)) {
                world.setBlockState(part, TilePartBlock.stateFor(part, anchor), Block.NOTIFY_ALL);
            }
        }
        return true;
    }

    /** A large tile gone (or laid out another way): its parts go with it. */
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        TileLayout was = state.get(SIZE);
        TileLayout now = newState.isOf(this) ? newState.get(SIZE) : null;
        super.onStateReplaced(state, world, pos, newState, moved);
        if (world.isClient || !was.isLarge() || was == now) return;
        for (BlockPos part : was.parts(pos)) {
            if (TilePartBlock.isPartOf(world.getBlockState(part), part, pos)) {
                world.setBlockState(part, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
            }
        }
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, ShapeContext context) {
        return state.get(SUPPORT).outline();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView view, BlockPos pos, ShapeContext context) {
        return collision(state, 0, 0, context);
    }

    /**
     * What collides over the block at ({@code dx}, {@code dz}) from the tile's own: the slope for players and mobs
     * (walked up and down like bare stairs); for tokens, which the board stands upright in the middle of the tile, a
     * level platform at that height.
     */
    public static VoxelShape collision(BlockState tile, int dx, int dz, ShapeContext context) {
        TileSupport support = tile.get(SUPPORT);
        if (support.isSloped() && isToken(context)) {
            TileLayout layout = tile.get(SIZE);
            return TileSupport.tokenPlatform(support.standY(layout.centreX(), layout.centreZ()));
        }
        return support.shape(dx, dz);
    }

    private static boolean isToken(ShapeContext context) {
        return context instanceof EntityShapeContext entityContext
                && entityContext.getEntity() instanceof TokenizedEntityInterface token
                && token.steveparty$isTokenized();
    }



    /** The state to draw as a plain, level tile of the standard size (e.g. the small tile shown by the destination arrows). */
    public static BlockState levelState(BlockState state) {
        return state.contains(SUPPORT) ? state.with(SUPPORT, TileSupport.FLAT).with(SIZE, TileLayout.STANDARD) : state;
    }

    // ---------------------------------------------------------------- 8 directions

    /** Converts a player yaw into 8 steps of 45° (0 = placed while looking north, same convention as signs). */
    public static int rotation8FromYaw(float yaw) {
        return Math.floorMod((int) Math.floor((yaw + 202.5F) / 45.0F), 8);
    }

    public static BlockState rotate8(BlockState state, BlockRotation rotation) {
        return state.with(ROTATION_8, rotation.rotate(state.get(ROTATION_8), 8)); // 90° = +2 steps
    }

    public static BlockState mirror8(BlockState state, BlockMirror mirror) {
        return state.with(ROTATION_8, mirror.mirror(state.get(ROTATION_8), 8)); // NONE = identity
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return rotate8(state, rotation);
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return mirror8(state, mirror);
    }
}
