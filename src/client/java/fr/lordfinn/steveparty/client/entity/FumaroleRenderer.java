package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.fumarole.FumaroleEntity;
import fr.lordfinn.steveparty.entities.custom.fumarole.FumaroleHead;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.cache.texture.AnimatableTexture;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Draws a Fumarole: its shell and skin (FumaroleModel), then
 * <ul>
 *     <li><b>its veins</b> ({@link VeinsLayer}): the animated glow layer (fumarole_veins.png) drawn emissive, brighter
 *     the fuller its tank, dying out as it dies;</li>
 *     <li><b>its tank's lava</b> and <b>its vents</b> ({@link TankAndVentLayer}): on the hidden {@code tank_lava} and
 *     {@code vent<suffix>} bones, their own planes (as authored: the cubes' faces) drawn again with our own animated
 *     lava (26 x 28 px of each 34 x 34 frame) and with each head's vent state texture (idle, charging, spitting:
 *     FumaroleEntity#getVent), both full bright.</li>
 * </ul>
 */
public class FumaroleRenderer extends GeoEntityRenderer<FumaroleEntity> {
    private static final Identifier VEINS = Steveparty.id("textures/entity/fumarole_veins.png");
    private static final Identifier LAVA = Steveparty.id("textures/entity/fumarole_lava.png");
    private static final Identifier[] VENT = {Steveparty.id("textures/entity/fumarole_vent_idle.png"),
            Steveparty.id("textures/entity/fumarole_vent_charging.png"), Steveparty.id("textures/entity/fumarole_vent_spitting.png")};
    private static final int FULL_BRIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    /** The lava texture's frames are 34 px; the tank's plane shows 26 x 28 of them. */
    private static final float LAVA_U = 26 / 34f, LAVA_V = 28 / 34f;

    public FumaroleRenderer(EntityRendererFactory.Context context) {
        super(context, new FumaroleModel());
        this.shadowRadius = 1.6f;
        addRenderLayer(new VeinsLayer(this));
        addRenderLayer(new TankAndVentLayer(this));
    }

    /** The veins' glow, 0..1: dim when the tank is empty, whole when full, out once dead. */
    static float glow(FumaroleEntity fumarole, float partialTick) {
        float level = fumarole.tankLevel(partialTick) / FumaroleEntity.TANK_MAX;
        float glow = 0.55f + 0.45f * level;
        if (fumarole.deathTime > 0) glow *= Math.max(0, 1 - (fumarole.deathTime + partialTick) / 20f);
        return glow;
    }

    private static final class VeinsLayer extends GeoRenderLayer<FumaroleEntity> {
        VeinsLayer(GeoRenderer<FumaroleEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(MatrixStack poseStack, FumaroleEntity fumarole, BakedGeoModel bakedModel, @Nullable RenderLayer renderType,
                           VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick,
                           int packedLight, int packedOverlay) {
            float glow = glow(fumarole, partialTick);
            if (glow <= 0.01f) return;
            int c = MathHelper.clamp(Math.round(glow * 255), 0, 255);
            AnimatableTexture.setAndUpdate(VEINS);
            RenderLayer layer = RenderLayer.getEyes(VEINS);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, fumarole, layer, bufferSource.getBuffer(layer),
                    partialTick, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, 0xFF000000 | c << 16 | c << 8 | c);
        }
    }

    private static final class TankAndVentLayer extends GeoRenderLayer<FumaroleEntity> {
        TankAndVentLayer(GeoRenderer<FumaroleEntity> renderer) {
            super(renderer);
        }

        /** The head whose vent this bone is, or -1. */
        private static int ventOf(String bone) {
            for (FumaroleHead head : FumaroleEntity.HEADS) if (bone.equals(head.name("vent"))) return head.index();
            return bone.equals("vent") ? 0 : -1; // a one-headed model
        }

        @Override
        public void renderForBone(MatrixStack poseStack, FumaroleEntity fumarole, GeoBone bone, RenderLayer renderType,
                                  VertexConsumerProvider bufferSource, VertexConsumer buffer, float partialTick,
                                  int packedLight, int packedOverlay) {
            String name = bone.getName();
            if (name.equals("tank_lava")) {
                if (fumarole.tankLevel(partialTick) < 0.05f) return;
                AnimatableTexture.setAndUpdate(LAVA);
                drawPlanes(poseStack, bone, bufferSource.getBuffer(RenderLayer.getEntityTranslucent(LAVA)), LAVA_U, LAVA_V);
            } else {
                int head = ventOf(name);
                if (head < 0 || fumarole.deathTime > 0) return;
                Identifier texture = VENT[MathHelper.clamp(fumarole.getVent(head), 0, VENT.length - 1)];
                AnimatableTexture.setAndUpdate(texture);
                drawPlanes(poseStack, bone, bufferSource.getBuffer(RenderLayer.getEntityTranslucent(texture)), 1, 1);
            }
            // Give GeckoLib its buffer back (see GeoRenderLayer#renderForBone)
            bufferSource.getBuffer(renderType);
        }

        /**
         * The bone's cubes' faces again, full bright, their UV stretched over {@code 0..uMax, 0..vMax} of another
         * texture (the authored UV belongs to the main texture). The pose stack is at the bone (GeckoLib's).
         */
        private static void drawPlanes(MatrixStack poseStack, GeoBone bone, VertexConsumer consumer, float uMax, float vMax) {
            for (GeoCube cube : bone.getCubes()) {
                poseStack.push();
                RenderUtil.translateToPivotPoint(poseStack, cube);
                RenderUtil.rotateMatrixAroundCube(poseStack, cube);
                RenderUtil.translateAwayFromPivotPoint(poseStack, cube);
                MatrixStack.Entry entry = poseStack.peek();
                for (GeoQuad quad : cube.quads()) {
                    if (quad == null) continue;
                    GeoVertex[] vertices = quad.vertices();
                    float u0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v0 = Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
                    for (GeoVertex vertex : vertices) {
                        u0 = Math.min(u0, vertex.texU());
                        u1 = Math.max(u1, vertex.texU());
                        v0 = Math.min(v0, vertex.texV());
                        v1 = Math.max(v1, vertex.texV());
                    }
                    if (u1 - u0 < 1.0e-6f || v1 - v0 < 1.0e-6f) continue; // a face with no texture (the plane's edges)
                    Vector3f normal = quad.normal();
                    for (GeoVertex vertex : vertices) {
                        Vector3f at = vertex.position();
                        consumer.vertex(entry, at.x(), at.y(), at.z()).color(0xFFFFFFFF)
                                .texture((vertex.texU() - u0) / (u1 - u0) * uMax, (vertex.texV() - v0) / (v1 - v0) * vMax)
                                .overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT)
                                .normal(entry, normal.x(), normal.y(), normal.z());
                    }
                }
                poseStack.pop();
            }
        }
    }
}
