package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Map;

public class MulaEntityRenderer extends GeoEntityRenderer<MulaEntity> {
    // Your billboard texture
    private static final Identifier HALLO_TEXTURE = Steveparty.id("textures/entity/mula_hallo.png");
    private static final Map<MulaEntity.MulaVariant, Identifier> TEXTURES = Map.of(
            MulaEntity.MulaVariant.BLUE, Steveparty.id("textures/entity/mula.png"),
            MulaEntity.MulaVariant.RED, Steveparty.id("textures/entity/mula_red.png"),
            MulaEntity.MulaVariant.GREEN, Steveparty.id("textures/entity/mula_green.png"),
            MulaEntity.MulaVariant.YELLOW, Steveparty.id("textures/entity/mula_yellow.png"),
            MulaEntity.MulaVariant.PURPLE, Steveparty.id("textures/entity/mula_purple.png"),
            MulaEntity.MulaVariant.BLACK, Steveparty.id("textures/entity/mula_black.png")
    );

    public MulaEntityRenderer(EntityRendererFactory.Context renderManager) {
        super(renderManager, new DefaultedEntityGeoModel<>(Steveparty.id("mula")));
        addRenderLayer(new HalloLayer<>(this, HALLO_TEXTURE)); // add the custom billboard
    }

    @Override
    public Identifier getTextureLocation(MulaEntity entity) {
        return TEXTURES.getOrDefault(entity.getVariant(), Steveparty.id("textures/entity/mula.png"));
    }

    @Override
    public void actuallyRender(MatrixStack poseStack, MulaEntity animatable, BakedGeoModel model, @Nullable RenderLayer renderType, VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int renderColor) {

        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender, partialTick, 0xF000F0, packedOverlay, renderColor);
    }

    @Override
    public @Nullable RenderLayer getRenderType(MulaEntity animatable, Identifier texture,
                                               @Nullable VertexConsumerProvider bufferSource,
                                               float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    // ----------------------
    // Billboard render layer
    // ----------------------
    private static class HalloLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
        private final Identifier texture;

        public HalloLayer(GeoRenderer<T> renderer, Identifier texture) {
            super(renderer);
            this.texture = texture;
        }


        @Override
        public void render(MatrixStack matrices, T animatable, BakedGeoModel bakedModel,
                           @Nullable RenderLayer renderType, VertexConsumerProvider bufferSource,
                           @Nullable net.minecraft.client.render.VertexConsumer buffer, float partialTick,
                           int packedLight, int packedOverlay, int renderColor) {
            if (animatable instanceof MulaEntity entity) {
                var bodyBone = bakedModel.getBone("head");
                if (bodyBone.isPresent()) {
                    var client = MinecraftClient.getInstance();
                    var camera = client.gameRenderer.getCamera();
                    var rotation = camera.getRotation();
                    Vector3d bonePos = bodyBone.get().getLocalPosition();
                    //matrices.push();
                    matrices.translate(bonePos.x, bonePos.y, bonePos.z);
                    matrices.multiply(rotation);
                    matrices.scale(1f, 1f, 1f);
                    drawQuad(matrices, bufferSource.getBuffer(RenderLayer.getEntityTranslucentEmissive(texture)), packedLight);
//                    matrices.pop();
                }
            }
        }

        private void drawQuad(MatrixStack matrices, net.minecraft.client.render.VertexConsumer vertices, int light) {
            MatrixStack.Entry entry = matrices.peek();
            float minU = 0f, maxU = 1f;
            float minV = 0f, maxV = 1f;
            float halfSize = 1.0f;

            vertices.vertex(entry.getPositionMatrix(), -halfSize, -halfSize, 0.0F)
                    .color(255, 255, 255, 255)
                    .texture(minU, maxV)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(entry, 0.0F, 1.0F, 0.0F);
                    
            vertices.vertex(entry.getPositionMatrix(), halfSize, -halfSize, 0.0F)
                    .color(255, 255, 255, 255)
                    .texture(maxU, maxV)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(entry, 0.0F, 1.0F, 0.0F);
                    
            vertices.vertex(entry.getPositionMatrix(), halfSize, halfSize, 0.0F)
                    .color(255, 255, 255, 255)
                    .texture(maxU, minV)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(entry, 0.0F, 1.0F, 0.0F);
                    
            vertices.vertex(entry.getPositionMatrix(), -halfSize, halfSize, 0.0F)
                    .color(255, 255, 255, 255)
                    .texture(minU, minV)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(entry, 0.0F, 1.0F, 0.0F);
                    
        }
    }

}
