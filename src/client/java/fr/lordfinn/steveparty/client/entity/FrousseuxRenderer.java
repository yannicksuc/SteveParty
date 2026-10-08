package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Draws a Frousseux: its see-through sheet (FrousseuxModel), then over the same model the wax on its top, tinted its
 * candle's wax colour, and its flame, tinted its flame colour and glowing, dimmer as its health goes down
 * ({@link FrousseuxEntity.Flame}). The body is lit by its own flame. The three textures share one UV layout and never overlap.
 */
public class FrousseuxRenderer extends GeoEntityRenderer<FrousseuxEntity> {
    private static final Identifier WAX = Steveparty.id("textures/entity/frousseux_wax.png");
    private static final Identifier FLAME = Steveparty.id("textures/entity/frousseux_flame.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    public FrousseuxRenderer(EntityRendererFactory.Context context) {
        super(context, new FrousseuxModel());
        this.shadowRadius = 0.2f;
        addRenderLayer(new WaxLayer(this));
        addRenderLayer(new FlameLayer(this));
    }

    /** A candle: lit by its own flame (full block light), whatever the light around. */
    @Override
    public void render(FrousseuxEntity frousseux, float entityYaw, float partialTick, MatrixStack poseStack,
                       VertexConsumerProvider bufferSource, int packedLight) {
        int light = frousseux.isBlownOut() || frousseux.deathTime > 0 ? packedLight
                : LightmapTextureManager.pack(15, LightmapTextureManager.getSkyLightCoordinates(packedLight));
        super.render(frousseux, entityYaw, partialTick, poseStack, bufferSource, light);
    }

    private static final class WaxLayer extends GeoRenderLayer<FrousseuxEntity> {
        WaxLayer(GeoRenderer<FrousseuxEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(MatrixStack poseStack, FrousseuxEntity frousseux, BakedGeoModel bakedModel, @Nullable RenderLayer renderType,
                           VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick,
                           int packedLight, int packedOverlay) {
            RenderLayer layer = RenderLayer.getEntityCutoutNoCull(WAX);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, frousseux, layer, bufferSource.getBuffer(layer),
                    partialTick, packedLight, packedOverlay, 0xFF000000 | frousseux.getColor().wax);
        }
    }

    private static final class FlameLayer extends GeoRenderLayer<FrousseuxEntity> {
        FlameLayer(GeoRenderer<FrousseuxEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(MatrixStack poseStack, FrousseuxEntity frousseux, BakedGeoModel bakedModel, @Nullable RenderLayer renderType,
                           VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick,
                           int packedLight, int packedOverlay) {
            if (frousseux.isBlownOut() || frousseux.deathTime > 0) return;
            float brightness = frousseux.getFlame().brightness;
            int tint = frousseux.getColor().flame;
            int r = (int) (((tint >> 16) & 0xFF) * brightness), g = (int) (((tint >> 8) & 0xFF) * brightness),
                    b = (int) ((tint & 0xFF) * brightness);
            RenderLayer layer = RenderLayer.getEntityTranslucentEmissive(FLAME);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, frousseux, layer, bufferSource.getBuffer(layer),
                    partialTick, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, 0xFF000000 | r << 16 | g << 8 | b);
        }
    }
}
