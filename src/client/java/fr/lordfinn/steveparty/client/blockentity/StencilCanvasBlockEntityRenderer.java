package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.TrafficSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.AbstractStencilSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.PlasticRoadSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.RockSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.SignShapes;
import fr.lordfinn.steveparty.blocks.custom.signs.StencilCanvasBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.signs.StencilPaintBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.WoodenPanelBlock;
import fr.lordfinn.steveparty.client.utils.StencilResourceManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * Draws the symbol of every stencil block: on its sign faces (see {@link SymbolLayouts}), turned with the sign, or
 * on the face a paint was sprayed on. The block itself is in the chunk mesh (see the stencil sign models), and so is
 * the engraving of the rock signs, dug in the stone.
 * <p>
 * Painted symbols take the dye's sign colour; engraved ones are a dark, see-through print of the shape. Brushing
 * fades them. Sprayed paint lets a little of its wall show through; a plastic plate's symbol stops at its outline.
 */
public class StencilCanvasBlockEntityRenderer<T extends StencilCanvasBlockEntity> implements BlockEntityRenderer<T> {
    /** Symbols stay visible as far as signs are usually seen (the default is 64 blocks). */
    private static final int RENDER_DISTANCE = 256;
    private static final int ENGRAVED_COLOR = 0x6A1E1A16;
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Opacity of the paint for each brush step (StencilCanvasBlockEntity#MAX_FADE + 1 steps). */
    private static final float[] FADE_ALPHA = {1.0F, 0.78F, 0.58F, 0.4F, 0.24F};
    /** Sprayed paint is a little see-through: the texture of the block it is on shows under it. */
    private static final float SPRAY_ALPHA = 0.86F;

    public StencilCanvasBlockEntityRenderer(BlockEntityRendererFactory.Context ignoredCtx) {
    }

    @Override
    public void render(T entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        byte[] shape = entity.getShape();
        if (shape == null) return;
        BlockState state = entity.getCachedState();
        if (state.getBlock() instanceof RockSignBlock) return;
        if (state.getBlock() instanceof PlasticRoadSignBlock) shape = masked(shape, state.get(PlasticRoadSignBlock.PLATE).mask());
        DyeColor color = entity.getColor();
        // Wooden signs keep their painted wood grain; everything else is a plain print
        boolean woodGrain = color != null && (state.getBlock() instanceof TrafficSignBlock || state.getBlock() instanceof WoodenPanelBlock);
        Identifier texture = StencilResourceManager.getTexture(shape,
                woodGrain ? StencilResourceManager.Kind.WOOD : StencilResourceManager.Kind.FLAT);
        if (texture == null) return;

        int argb = color == null ? ENGRAVED_COLOR : 0xFF000000 | color.getSignColor();
        float alpha = FADE_ALPHA[Math.clamp(entity.getFade(), 0, FADE_ALPHA.length - 1)]
                * (state.getBlock() instanceof StencilPaintBlock ? SPRAY_ALPHA : 1.0F);
        argb = ColorHelper.withAlpha(Math.round((argb >>> 24) * alpha), argb);
        int symbolLight = entity.isGlowing() && color != null ? FULL_BRIGHT : light;
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(texture));

        matrices.push();
        if (state.getBlock() instanceof StencilPaintBlock) {
            drawQuad(matrices.peek(), consumer, SymbolLayouts.forPaint(state), argb, symbolLight);
        } else if (state.getBlock() instanceof AbstractStencilSignBlock) {
            List<SymbolLayouts.SymbolQuad> quads = SymbolLayouts.forSign(state);
            if (!quads.isEmpty()) {
                matrices.translate(0.5, 0, 0.5);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(SignShapes.angleDegrees(state.get(AbstractStencilSignBlock.ROTATION))));
                matrices.translate(-0.5, 0, -0.5);
                for (SymbolLayouts.SymbolQuad quad : quads) drawQuad(matrices.peek(), consumer, quad, argb, symbolLight);
            }
        }
        matrices.pop();
    }

    private static byte[] masked(byte[] shape, byte[] mask) {
        for (int i = 0; i < shape.length; i++) shape[i] = (byte) (shape[i] & mask[i]);
        return shape;
    }

    /** Draws a quad given in pixels. */
    private static void drawQuad(MatrixStack.Entry entry, VertexConsumer consumer, SymbolLayouts.SymbolQuad quad, int argb, int light) {
        Matrix4f matrix = entry.getPositionMatrix();
        Vector3f n = quad.normal();
        vertex(consumer, matrix, entry, quad.topLeft(), 0, 0, argb, light, n);
        vertex(consumer, matrix, entry, quad.bottomLeft(), 0, 1, argb, light, n);
        vertex(consumer, matrix, entry, quad.bottomRight(), 1, 1, argb, light, n);
        vertex(consumer, matrix, entry, quad.topRight(), 1, 0, argb, light, n);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, MatrixStack.Entry entry, Vector3f pixel, float u, float v,
                               int argb, int light, Vector3f normal) {
        consumer.vertex(matrix, pixel.x / 16F, pixel.y / 16F, pixel.z / 16F)
                .color(argb)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, normal.x, normal.y, normal.z);
    }

    @Override
    public int getRenderDistance() {
        return RENDER_DISTANCE;
    }
}
