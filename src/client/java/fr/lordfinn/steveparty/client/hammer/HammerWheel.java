package fr.lordfinn.steveparty.client.hammer;

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

import java.util.ArrayList;
import java.util.List;

/**
 * The Stencil Hammer's wheel: the loaded colours on the left (and "Engrave", no paint), the loaded stencils on the
 * right, and on top the hammer's inventory (load / unload its stencils and dyes). A pick applies at once.
 */
public final class HammerWheel implements ToolWheel.Provider {
    private static final ToolWheel.Theme THEME = new ToolWheel.Theme(0xD0261A10, 0xF03A2A1A, 0xFF6B4A2B,
            0xFFFFE08A, 0xFFF3E6D0, 0xE0281C10);
    private static final List<ToolWheel.Ring> RINGS = List.of(new ToolWheel.Ring(34, 108));
    private static final float TOP = 24;
    private static final ItemStack ENGRAVE_ICON = new ItemStack(Items.IRON_NUGGET);

    @Override
    public boolean handles(ItemStack stack) {
        return stack.getItem() instanceof StencilGunItem;
    }

    @Override
    public ToolWheel.Layout layout(MinecraftClient client, ItemStack hammer) {
        List<ItemStack> contents = StencilGunItem.contents(hammer);
        StencilGunSelection selection = StencilGunItem.validSelection(contents, StencilGunItem.selection(hammer));
        StencilGunItem.Load load = StencilGunItem.selectedLoad(hammer);
        int paint = load.color() != null ? 0xFF000000 | load.color().getEntityColor() : 0xFF8A8A8A;

        // Top: the inventory
        ToolWheel.Sector inventory = new ToolWheel.Sector(Text.translatable("wheel.steveparty.hammer.inventory"),
                Text.translatable("wheel.steveparty.hammer.inventory.description"), 0xE07A5634,
                item(new ItemStack(Items.CHEST)), false, true, () -> send(ToolWheelPayload.Action.HAMMER_OPEN, 0));

        // Right: the stencils
        List<ToolWheel.Sector> stencils = new ArrayList<>();
        for (int slot = 0; slot < StencilGunItem.STENCIL_SLOTS; slot++) {
            ItemStack stencil = contents.get(slot);
            if (stencil.isEmpty()) continue;
            byte[] shape = stencil.getItem() instanceof StencilItem ? StencilItem.getShape(stencil) : null;
            StencilPatterns.Pattern pattern = shape == null ? null : StencilPatterns.byShape(shape);
            Text name = pattern != null ? pattern.name() : Text.translatable("tooltip.steveparty.stencil.custom");
            int value = slot;
            stencils.add(new ToolWheel.Sector(name, Text.translatable("wheel.steveparty.hammer.stencil.description", name),
                    0xE0C9B48C, shape(shape, paint), selection.stencil() == slot, !StencilShape.isBlank(shape),
                    () -> send(ToolWheelPayload.Action.HAMMER_STENCIL, value)));
        }
        if (stencils.isEmpty()) {
            stencils.add(new ToolWheel.Sector(Text.translatable("wheel.steveparty.hammer.no_stencil"),
                    Text.translatable("wheel.steveparty.hammer.no_stencil.description"), 0xE0C9B48C, null, false, false, () -> {
            }));
        }

        // Left: Engrave (bottom), then the colours going up
        List<ToolWheel.Sector> colors = new ArrayList<>();
        colors.add(new ToolWheel.Sector(Text.translatable("hud.steveparty.stencil_gun.engrave"),
                Text.translatable("wheel.steveparty.hammer.engrave.description"), 0xE05E5E5E, item(ENGRAVE_ICON),
                selection.dye() == StencilGunSelection.ENGRAVE, true,
                () -> send(ToolWheelPayload.Action.HAMMER_DYE, StencilGunSelection.ENGRAVE)));
        for (int slot = 0; slot < StencilGunItem.DYE_SLOTS; slot++) {
            ItemStack dye = contents.get(StencilGunItem.STENCIL_SLOTS + slot);
            if (!(dye.getItem() instanceof DyeItem dyeItem)) continue;
            DyeColor color = dyeItem.getColor();
            int value = slot;
            Text name = Text.translatable("color.minecraft." + color.getName());
            colors.add(new ToolWheel.Sector(name, Text.translatable("wheel.steveparty.hammer.color.description", name, dye.getCount()),
                    0xE0000000 | color.getEntityColor(), stack(dye), selection.dye() == slot, true,
                    () -> send(ToolWheelPayload.Action.HAMMER_DYE, value)));
        }

        List<ToolWheel.Arc> arcs = List.of(
                new ToolWheel.Arc(0, -TOP, TOP, List.of(inventory)),
                new ToolWheel.Arc(0, TOP, 180, stencils),
                new ToolWheel.Arc(0, 180, 360 - TOP, colors));
        return new ToolWheel.Layout(THEME, RINGS, arcs, Text.translatable("item.steveparty.stencil_gun"), item(hammer.copy()));
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
