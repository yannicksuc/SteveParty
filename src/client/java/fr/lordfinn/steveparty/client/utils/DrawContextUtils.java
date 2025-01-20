package fr.lordfinn.steveparty.client.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import java.util.function.Function;

public class DrawContextUtils {
    /**
     * Utility function to draw a glint texture with stencil masking.
     *
     * @param context the drawing context
     * @param x       the x-coordinate on the screen
     * @param y       the y-coordinate on the screen
     */
    public static void drawTextureWithGlint(DrawContext context, Identifier sprite, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();

        // Translate to ensure the glint is drawn in the foreground
        matrices.translate(0, 0, 200);

        // Enable the stencil buffer
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.clearDepth(1.0D); // Clear depth for stencil masking
        RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT); // Clear stencil buffer

        // Step 1: Write to the stencil buffer
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.depthMask(false);
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 1, 0xFF); // Always write to the stencil buffer
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE); // Replace with 1 on pass

        // Draw your opaque texture
        context.drawTexture(RenderLayer::getGuiOpaqueTexturedBackground, sprite, x, y, u, v, width, height, textureWidth, textureHeight);

        // Step 2: Use the stencil buffer to mask the glint
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF); // Render only where stencil == 1
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP); // Keep stencil buffer unchanged

        // Render the glint
        Function<Identifier, RenderLayer> renderLayerFunction = id -> RenderLayer.getGlint();
        context.drawTexture(renderLayerFunction, sprite, x, y, u, v, width, height, textureWidth, textureHeight);

        // Disable stencil and cleanup
        RenderSystem.clearStencil(1);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();

        // Pop the matrix stack
        matrices.pop();
    }

}
