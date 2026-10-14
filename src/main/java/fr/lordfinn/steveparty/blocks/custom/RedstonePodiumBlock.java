package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.ItemResults;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Redstone Podium: a podium taken by a redstone pulse (the nearest player, see {@link fr.lordfinn.steveparty.podium.PodiumSignal}),
 * not by a click. Its carpet (the top and the band round its top) takes the colour of a dye clicked on it, the whole
 * column at once ({@link #CARPET}); its banner is stamped as any podium's.
 */
public class RedstonePodiumBlock extends PodiumBlock {
    /** The colour of the carpet. */
    public static final EnumProperty<DyeColor> CARPET = EnumProperty.of("carpet", DyeColor.class);
    /** The carpet's band: the top 3 pixels of the column's top. */
    private static final double BAND = 3 / 16.0;

    /** The carpet's colour for each dye (rgb): the colour of its lightest pixels, the texture's greys darken it. */
    private static final int[] CARPET_RGB = {
            0xFFFFFF, 0xFFA040, 0xE070E0, 0x70D0FF, 0xFFE050, 0xA0F050, 0xFFA0C8, 0x8C8C94,
            0xC8C8CC, 0x40C8C8, 0xA868F0, 0x5078F0, 0xB07848, 0x60A840, 0xFF5E5E, 0x404048};

    /** The colour of a carpet dyed {@code color} (rgb). */
    public static int carpetRgb(DyeColor color) {
        return CARPET_RGB[color.getId()];
    }

    public RedstonePodiumBlock(Settings settings) {
        super(settings, Style.CLASSIC);
        setDefaultState(getDefaultState().with(CARPET, DyeColor.RED));
    }

    @Override
    protected MapCodec<PodiumBlock> getCodec() {
        return Block.createCodec(RedstonePodiumBlock::new);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(CARPET);
    }

    /** Placed on (or into) a Redstone Podium: the colour of its carpet. */
    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState state = super.getPlacementState(ctx);
        if (state == null) return null;
        BlockState existing = ctx.getWorld().getBlockState(ctx.getBlockPos());
        BlockState below = ctx.getWorld().getBlockState(ctx.getBlockPos().down());
        if (existing.getBlock() instanceof RedstonePodiumBlock) return state.with(CARPET, existing.get(CARPET));
        if (below.getBlock() instanceof RedstonePodiumBlock) return state.with(CARPET, below.get(CARPET));
        return state;
    }

    /** Whether {@code hit} is on the carpet of the column's top at {@code pos}: its top face, or its band. */
    public static boolean onCarpet(World world, BlockPos pos, BlockState state, BlockHitResult hit) {
        if (!state.get(TOP)) return false;
        if (hit.getSide() == Direction.UP) return true;
        double surface = pos.getY() + (state.get(FULL) ? 1.0 : 0.5);
        return hit.getPos().y >= surface - BAND - 1.0E-4;
    }

    /** A dye clicked on its carpet (no stencil in the other hand: that is the banner) dyes the column's carpet. */
    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand == Hand.MAIN_HAND && stack.getItem() instanceof DyeItem dye && !(player.getOffHandStack().getItem() instanceof StencilItem)
                && onCarpet(world, pos, state, hit)) {
            return ItemResults.of(dyeCarpet(world, pos, player, stack, dye.getColor()));
        }
        return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
    }

    /** The carpet of the column at {@code pos} takes {@code color}; one dye used (not in creative). */
    public static ActionResult dyeCarpet(World world, BlockPos pos, @Nullable PlayerEntity player, ItemStack dye, DyeColor color) {
        BlockPos top = topOf(world, pos);
        if (!(world.getBlockState(top).getBlock() instanceof RedstonePodiumBlock) || world.getBlockState(top).get(CARPET) == color)
            return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        for (BlockPos block = bottomOf(world, pos); block.getY() <= top.getY(); block = block.up()) {
            BlockState state = world.getBlockState(block);
            if (state.getBlock() instanceof RedstonePodiumBlock) world.setBlockState(block, state.with(CARPET, color), Block.NOTIFY_ALL);
        }
        world.playSound(null, top, SoundEvents.ITEM_DYE_USE, SoundCategory.BLOCKS, 1.0f, 1.0f);
        world.emitGameEvent(GameEvent.BLOCK_CHANGE, top, GameEvent.Emitter.of(player, world.getBlockState(top)));
        if (player == null || !player.isCreative()) dye.decrement(1);
        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        Tooltips.of(tooltip)
                .summary("tooltip.steveparty.redstone_podium.what")
                .more(more -> more
                        .note("tooltip.steveparty.redstone_podium.pulse")
                        .use(Tooltips.Keys.use(), "tooltip.steveparty.redstone_podium.dye")
                        .use(Tooltips.Keys.use(), "tooltip.steveparty.podium.page")
                        .note("tooltip.steveparty.podium.modes")
                        .note("tooltip.steveparty.podium.comparator"));
    }
}
