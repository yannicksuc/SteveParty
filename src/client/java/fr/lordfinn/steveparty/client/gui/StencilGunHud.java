package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.StencilGunSelection;
import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The Stencil Hammer's HUD: a small pair of boxes above the hotbar, the stencil it stamps and its paint (the tools' HUD
 * look: see {@link ToolHud}). Both are picked on its wheel (left click: see
 * {@link fr.lordfinn.steveparty.client.hammer.HammerWheel}).
 */
public final class StencilGunHud {
    private static final int PIXEL = 1;
    private static final int BOX = ToolHud.BOX;
    /** Where the 16 pixel content starts inside a box. */
    private static final int INSET = (BOX - 16) / 2;

    /** What the HUD shows of a gun, worked out again only when its contents or selection change. */
    private record Shown(InventoryComponent contentsComponent, StencilGunSelection selectionComponent, List<ItemStack> contents,
                         StencilGunSelection selection, StencilGunItem.Load load) {
    }

    private static Shown shown;

    private StencilGunHud() {
    }

    public static void initialize() {
        HudRenderCallback.EVENT.register(StencilGunHud::render);
        fr.lordfinn.steveparty.client.gui.wheel.ToolWheel.register(new fr.lordfinn.steveparty.client.hammer.HammerWheel());
    }

    private static boolean isHoldingGun(MinecraftClient client) {
        return client.player != null && client.player.getMainHandStack().getItem() instanceof StencilGunItem;
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

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.currentScreen != null || !isHoldingGun(client)
                || fr.lordfinn.steveparty.client.gui.wheel.ToolWheel.isOpen()) return;
        Shown shown = shown(client.player.getMainHandStack());
        List<ItemStack> contents = shown.contents();
        StencilGunSelection selection = shown.selection();
        StencilGunItem.Load load = shown.load();

        int width = context.getScaledWindowWidth();
        int y = ToolHud.top(context);
        int stencilX = width / 2 - BOX - 4;
        int colorX = width / 2 + 4;

        // Stencil
        ToolHud.box(context, stencilX, y, false);
        if (load.shape() != null) {
            int paint = load.color() != null ? 0xFF000000 | load.color().getEntityColor() : 0xFF6B6B6B;
            byte[] shape = load.shape();
            for (int px = 0; px < 16; px++) {
                for (int py = 0; py < 16; py++) {
                    if (!StencilShape.get(shape, px, py)) continue;
                    int sx = stencilX + INSET + px * PIXEL, sy = y + INSET + py * PIXEL;
                    context.fill(sx, sy, sx + PIXEL, sy + PIXEL, paint);
                }
            }
        }

        // Colour
        ToolHud.box(context, colorX, y, false);
        ItemStack dye = selection.dye() == StencilGunSelection.ENGRAVE ? ItemStack.EMPTY : contents.get(StencilGunItem.STENCIL_SLOTS + selection.dye());
        if (dye.getItem() instanceof DyeItem) {
            context.getMatrices().push();
            context.getMatrices().translate(colorX + INSET, y + INSET, 0);
            context.drawItem(dye, 0, 0);
            context.getMatrices().pop();
            context.drawItemInSlot(client.textRenderer, dye, colorX + INSET, y + INSET);
        } else {
            // No paint: a netherite pickaxe (engrave), or an axe on a cut-out panel (cut)
            context.drawItem(fr.lordfinn.steveparty.client.hammer.HammerWheel.engraveIcon(client), colorX + INSET, y + INSET);
        }

        // What the mouse buttons do, above the boxes
        Text controls = Text.translatable("hud.steveparty.stencil_gun.controls");
        context.getMatrices().push();
        context.getMatrices().translate(width / 2F, y - 7, 0);
        ToolHud.occupy(y - 8);
        context.getMatrices().scale(0.75F, 0.75F, 1);
        context.drawTextWithShadow(client.textRenderer, controls, -client.textRenderer.getWidth(controls) / 2, 0, ToolHud.TEXT);
        context.getMatrices().pop();
    }
}
