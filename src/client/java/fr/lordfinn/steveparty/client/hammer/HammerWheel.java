package fr.lordfinn.steveparty.client.hammer;

import fr.lordfinn.steveparty.blocks.custom.signs.StencilCanvasBlock;
import fr.lordfinn.steveparty.client.gui.wheel.ToolWheel;
import fr.lordfinn.steveparty.components.StencilGunSelection;
import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.payloads.custom.ToolWheelPayload;
import fr.lordfinn.steveparty.stencil.StencilPatterns;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * The Stencil Hammer's wheel: what it holds and nothing else. At the top one button, its refill (its slots in a
 * wheel of their own, see StencilGunScreen), its loaded stencils on the right, its loaded dyes on the left. No paint
 * (engrave) is the selected dye clicked again, or no dye at all: the HUD then shows a netherite pickaxe (an axe at a
 * cut-out panel). A pick applies at once.
 */
public final class HammerWheel implements ToolWheel.Provider {
    private static final List<ToolWheel.Ring> RINGS = List.of(new ToolWheel.Ring(30, 92));
    /** The hammer's own wood for the panel, the plates and the stencils, the dyes in their colour. */
    private static final ToolWheel.Theme THEME = ToolWheel.WOOD;
    private static final int PAPER = THEME.body() & 0xFFFFFF, PLATE = THEME.body() & 0xFFFFFF;
    /** Degrees the refill button takes either side of the top. */
    private static final float PLUS = 24;

    @Override
    public boolean handles(ItemStack stack) {
        return stack.getItem() instanceof StencilGunItem;
    }

    @Override
    public ToolWheel.Layout layout(MinecraftClient client, ItemStack hammer, int page) {
        List<ItemStack> contents = StencilGunItem.contents(hammer);
        StencilGunSelection selection = StencilGunItem.validSelection(contents, StencilGunItem.selection(hammer));
        StencilGunItem.Load load = StencilGunItem.selectedLoad(hammer);
        int paint = load.color() != null ? 0xFF000000 | load.color().getEntityColor() : 0xFF6A6A6A;

        // Right: the loaded stencils
        List<ToolWheel.Sector> stencils = new ArrayList<>();
        for (int slot = 0; slot < StencilGunItem.STENCIL_SLOTS; slot++) {
            ItemStack stencil = contents.get(slot);
            if (stencil.isEmpty()) continue;
            byte[] shape = stencil.getItem() instanceof StencilItem ? StencilItem.getShape(stencil) : null;
            StencilPatterns.Pattern pattern = shape == null ? null : StencilPatterns.byShape(shape);
            Text name = pattern != null ? pattern.name() : Text.translatable("tooltip.steveparty.stencil.custom");
            int value = slot;
            stencils.add(new ToolWheel.Sector(name, null, PAPER, shape(shape, paint), selection.stencil() == slot,
                    !StencilShape.isBlank(shape), () -> send(ToolWheelPayload.Action.HAMMER_STENCIL, value)));
        }

        // Left: the loaded dyes; the selected one clicked again is let go of (no paint: engraved)
        List<ToolWheel.Sector> dyes = new ArrayList<>();
        for (int slot = 0; slot < StencilGunItem.DYE_SLOTS; slot++) {
            ItemStack dye = contents.get(StencilGunItem.STENCIL_SLOTS + slot);
            if (!(dye.getItem() instanceof DyeItem dyeItem)) continue;
            DyeColor color = dyeItem.getColor();
            boolean selected = selection.dye() == slot;
            int value = selected ? StencilGunSelection.ENGRAVE : slot;
            dyes.add(new ToolWheel.Sector(Text.translatable("wheel.steveparty.hammer.color",
                    Text.translatable("color.minecraft." + color.getName()), dye.getCount()), null, color.getEntityColor(), stack(dye),
                    selected, true, () -> send(ToolWheelPayload.Action.HAMMER_DYE, value)));
        }

        // Top centre: one button, its refill
        ToolWheel.Sector refill = new ToolWheel.Sector(Text.translatable("wheel.steveparty.hammer.refill"), null, PLATE,
                big(new ItemStack(Items.BUNDLE)), false, true, () -> send(ToolWheelPayload.Action.HAMMER_OPEN, 0));
        List<ToolWheel.Arc> arcs = new ArrayList<>();
        arcs.add(new ToolWheel.Arc(0, -PLUS, PLUS, List.of(refill)));
        if (!stencils.isEmpty()) arcs.add(new ToolWheel.Arc(0, PLUS, 180, stencils));
        if (!dyes.isEmpty()) arcs.add(new ToolWheel.Arc(0, 180, 360 - PLUS, dyes));
        return new ToolWheel.Layout(RINGS, arcs, item(hammer.copy()), null, stencils.isEmpty() && dyes.isEmpty() ? refill : null, null, THEME);
    }

    private static ToolWheel.Icon big(ItemStack stack) {
        return (context, x, y) -> {
            context.getMatrices().push();
            context.getMatrices().translate(x, y, 0);
            context.getMatrices().scale(2, 2, 1);
            context.drawItem(stack, -8, -8);
            context.getMatrices().pop();
        };
    }

    /** Whether the player looks at a cut-out panel: the hammer cuts it rather than engraves. */
    public static boolean looksAtCutOutPanel(MinecraftClient client) {
        return client.world != null && client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && client.world.getBlockState(hit.getBlockPos()).getBlock() instanceof StencilCanvasBlock canvas && canvas.usesSilhouette();
    }

    /** The no-paint mode's icon: a netherite pickaxe (engrave), or an axe on a cut-out panel (cut). */
    public static ItemStack engraveIcon(MinecraftClient client) {
        return new ItemStack(looksAtCutOutPanel(client) ? Items.NETHERITE_AXE : Items.NETHERITE_PICKAXE);
    }

    private static ToolWheel.Icon item(ItemStack stack) {
        return (context, x, y) -> context.drawItem(stack, x - 8, y - 8);
    }

    /** A dye stack, with its count. */
    private static ToolWheel.Icon stack(ItemStack stack) {
        return (context, x, y) -> {
            context.drawItem(stack, x - 8, y - 8);
            context.drawItemInSlot(MinecraftClient.getInstance().textRenderer, stack, x - 8, y - 8);
        };
    }

    /** The stencil's pattern, 16 x 16 pixels, in the current paint. */
    private static ToolWheel.Icon shape(byte[] shape, int paint) {
        return (context, x, y) -> {
            context.fill(x - 9, y - 9, x + 9, y + 9, 0xFFEDE3CC);
            if (shape == null) return;
            for (int px = 0; px < StencilShape.SIDE; px++) {
                for (int py = 0; py < StencilShape.SIDE; py++) {
                    if (StencilShape.get(shape, px, py)) context.fill(x - 8 + px, y - 8 + py, x - 7 + px, y - 7 + py, paint);
                }
            }
        };
    }

    private static void send(ToolWheelPayload.Action action, int value) {
        ClientPlayNetworking.send(new ToolWheelPayload(action, value));
    }
}
