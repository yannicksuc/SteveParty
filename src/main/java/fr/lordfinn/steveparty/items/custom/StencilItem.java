package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.blocks.custom.signs.StencilCanvasBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.StencilInteractions;
import fr.lordfinn.steveparty.blocks.custom.signs.StencilPaintBlock;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.stencil.StencilPatterns;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

import java.util.List;

import static fr.lordfinn.steveparty.components.ModComponents.STENCIL_PIXELS;

/**
 * A 16x16 stencil, drawn at the stencil maker. With a dye in the other hand it paints its shape on a stencil
 * sign ({@link StencilInteractions}) or sprays it on the face of any
 * full block ({@link StencilPaintBlock}).
 */
public class StencilItem extends Item {
    public StencilItem(Settings settings) {
        super(settings);
    }

    /** @return a copy of the stencil's shape (blank if it has none). */
    public static byte[] getShape(ItemStack stack) {
        byte[] shape = StencilShape.fromList(stack.get(STENCIL_PIXELS));
        return StencilShape.isValid(shape) ? StencilShape.sanitize(shape) : StencilShape.blank();
    }

    public static void setShape(byte[] shape, ItemStack stack) {
        // Immutable value: stencils stack, and stacks sharing a component instance must never see it change
        stack.set(STENCIL_PIXELS, StencilShape.toList(StencilShape.sanitize(shape)));
    }

    public static ItemStack of(StencilPatterns.Pattern pattern) {
        ItemStack stack = new ItemStack(ModItems.STENCIL);
        setShape(pattern.shape(), stack);
        return stack;
    }

    /** Stencil + dye on the face of a full block (not a sign, those handle it themselves): sprays paint on it. */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;
        ItemStack other = player.getStackInHand(context.getHand() == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND);
        World world = context.getWorld();
        if (!(other.getItem() instanceof DyeItem dyeItem)) {
            // Only signs are engraved: a block face takes paint
            if (canTakePaint(world, context)) StencilInteractions.hint(world, player, "message.steveparty.stencil.block_needs_dye");
            return ActionResult.PASS;
        }
        DyeColor color = dyeItem.getColor();
        boolean sprayed = StencilPaintBlock.spray(world, context.getBlockPos(), context.getSide(), getShape(context.getStack()),
                color, player.getHorizontalFacing());
        if (!sprayed) return ActionResult.PASS;
        if (!world.isClient) {
            if (!player.isCreative()) other.decrement(1);
            StencilGunItem.playSpray(world, context.getBlockPos().offset(context.getSide()), color);
            world.emitGameEvent(GameEvent.BLOCK_CHANGE, context.getBlockPos(), GameEvent.Emitter.of(player));
        }
        return ActionResult.SUCCESS;
    }

    /** Whether the clicked face is one sprayed paint could go on (a full face, not a sign). */
    static boolean canTakePaint(World world, ItemUsageContext context) {
        BlockState target = world.getBlockState(context.getBlockPos());
        return !(target.getBlock() instanceof StencilCanvasBlock)
                && target.isSideSolidFullSquare(world, context.getBlockPos(), context.getSide());
    }

    @Environment(EnvType.CLIENT)
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        byte[] shape = getShape(stack);
        StencilPatterns.Pattern pattern = StencilPatterns.byShape(shape);
        // The name of the pattern only: the stencil's icon already shows its shape
        if (pattern != null) tooltip.add(pattern.name().copy().formatted(Formatting.GOLD));
        else if (!StencilShape.isBlank(shape)) tooltip.add(Text.translatable("tooltip.steveparty.stencil.custom").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tooltip.steveparty.stencil.usage").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.steveparty.stencil.usage_tools").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.steveparty.stencil.usage_engrave").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.translatable("tooltip.steveparty.stencil.remove").formatted(Formatting.DARK_GRAY));
    }
}
