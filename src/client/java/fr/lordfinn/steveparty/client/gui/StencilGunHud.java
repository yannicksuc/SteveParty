package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.client.gui.wheel.ToolWheel;
import fr.lordfinn.steveparty.client.hammer.HammerWheel;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.StencilGunSelection;
import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import fr.lordfinn.steveparty.stencil.StencilShape;
import fr.lordfinn.steveparty.utils.Argb;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The Stencil Hammer's HUD: a small pair of boxes above the hotbar, the stencil it stamps and its paint, under its
 * controls (see {@link ToolHudPanel}). Both are picked on its wheel (left click: see
 * {@link HammerWheel}).
 */
public final class StencilGunHud {
    /** What the HUD shows of a gun, worked out again only when its contents or selection change. */
    private record Shown(InventoryComponent contentsComponent, StencilGunSelection selectionComponent, List<ItemStack> contents,
                         StencilGunSelection selection, StencilGunItem.Load load) {
    }

    private static Shown shown;

    private StencilGunHud() {
    }

    public static void initialize() {
        ToolHudPanel.register(stack -> stack.getItem() instanceof StencilGunItem, StencilGunHud::describe);
        ToolWheel.register(new HammerWheel());
    }

    /** Item components are immutable: the same component instances mean the same contents and selection. */
    private static Shown shown(ItemStack gun) {
        InventoryComponent contentsComponent = gun.get(ModComponents.STENCIL_GUN_CONTENTS);
        StencilGunSelection selectionComponent = gun.get(ModComponents.STENCIL_GUN_SELECTION);
        Shown last = shown;
        if (last != null && last.contentsComponent() == contentsComponent && last.selectionComponent() == selectionComponent) return last;
        List<ItemStack> contents = StencilGunItem.contents(gun);
        StencilGunSelection selection = StencilGunItem.validSelection(contents, StencilGunItem.selection(gun));
        StencilGunItem.Load load = StencilGunItem.selectedLoad(gun);
        shown = new Shown(contentsComponent, selectionComponent, contents, selection, load);
        return shown;
    }

    /** The stencil it stamps (in its paint), the paint (or the engrave tool), and the controls. */
    private static void describe(ToolHudPanel panel, ItemStack gun, MinecraftClient client) {
        Shown shown = shown(gun);
        StencilGunItem.Load load = shown.load();
        panel.box((context, x, y) -> {
            if (load.shape() == null) return;
            int paint = load.color() != null ? Argb.opaque(load.color().getEntityColor()) : 0xFF6B6B6B;
            byte[] shape = load.shape();
            for (int px = 0; px < 16; px++) {
                for (int py = 0; py < 16; py++) {
                    if (StencilShape.get(shape, px, py)) context.fill(x + px, y + py, x + px + 1, y + py + 1, paint);
                }
            }
        });
        StencilGunSelection selection = shown.selection();
        ItemStack dye = selection.dye() == StencilGunSelection.ENGRAVE ? ItemStack.EMPTY
                : shown.contents().get(StencilGunItem.STENCIL_SLOTS + selection.dye());
        // No paint: a netherite pickaxe (engrave), or an axe on a cut-out panel (cut)
        panel.item(dye.getItem() instanceof DyeItem ? dye : HammerWheel.engraveIcon(client))
                .hint(Text.translatable("hud.steveparty.stencil_gun.controls"));
    }
}
