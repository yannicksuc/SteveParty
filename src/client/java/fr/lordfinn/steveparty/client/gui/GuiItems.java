package fr.lordfinn.steveparty.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

/** Items drawn in a GUI other than as they are: tinted, see-through, scaled. */
public final class GuiItems {
    private GuiItems() {
    }

    /** The item at ({@code x}, {@code y}) with its colours multiplied by ({@code r}, {@code g}, {@code b}, {@code a}). */
    public static void tinted(DrawContext context, ItemStack stack, int x, int y, float r, float g, float b, float a) {
        // Items are drawn in batches: flushed before and after, so that the tint applies to this item only
        context.draw();
        RenderSystem.setShaderColor(r, g, b, a);
        context.drawItem(stack, x, y);
        context.draw();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** The item with its top left corner at ({@code x}, {@code y}), {@code scale} times its size (16 px). */
    public static void scaled(DrawContext context, ItemStack stack, int x, int y, float scale) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(scale, scale, 1f);
        context.drawItem(stack, 0, 0);
        matrices.pop();
    }
}
