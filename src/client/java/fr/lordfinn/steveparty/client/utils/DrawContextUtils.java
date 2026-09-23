package fr.lordfinn.steveparty.client.utils;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public class DrawContextUtils {
    private static final Function<Identifier, RenderLayer> GLINT_LAYER = id -> RenderLayer.getGlint();

    /**
     * Draws a texture with the enchantment glint on top of it.
     * <p>
     * DrawContext is deferred since 1.21.2: raw GL/RenderSystem state (stencil, color mask...) set here
     * would not apply to these quads and would leak into the rest of the UI. The masking is done by the
     * glint render layer itself (depth test EQUAL): the glint only shows where the texture quad was
     * drawn at the same depth, which is exactly the previous visual.
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

        // Draw the opaque texture (writes the depth used as mask by the glint)
        context.drawTexture(RenderLayer::getGuiOpaqueTexturedBackground, sprite, x, y, u, v, width, height, textureWidth, textureHeight);

        // Render the glint
        context.drawTexture(GLINT_LAYER, sprite, x, y, u, v, width, height, textureWidth, textureHeight);

        // Pop the matrix stack
        matrices.pop();
    }

}
