package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriEntity;
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

/** Draws the Mistigri (MistigriModel) and his yellow-green eye glowing in the dark, but while it is shut. */
public class MistigriRenderer extends GeoEntityRenderer<MistigriEntity> {
    private static final Identifier GLOW = Steveparty.id("textures/entity/mistigri_glow.png");

    public MistigriRenderer(EntityRendererFactory.Context context) {
        super(context, new MistigriModel());
        this.shadowRadius = 0.75f;
        addRenderLayer(new GlowLayer(this));
    }

    private static final class GlowLayer extends GeoRenderLayer<MistigriEntity> {
        GlowLayer(GeoRenderer<MistigriEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(MatrixStack poseStack, MistigriEntity mistigri, BakedGeoModel bakedModel, @Nullable RenderLayer renderType,
                           VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick,
                           int packedLight, int packedOverlay) {
            if (mistigri.deathTime > 0 || mistigri.isInvisible() || MistigriModel.eyesClosed(mistigri)) return;
            RenderLayer layer = RenderLayer.getEyes(GLOW);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, mistigri, layer, bufferSource.getBuffer(layer),
                    partialTick, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
        }
    }
}
