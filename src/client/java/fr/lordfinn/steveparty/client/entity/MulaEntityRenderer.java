package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.entities.custom.MulaMotion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.BakedGeoModel;
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
    /** Smoothed speed (blocks/tick) above which the Mula leaves a trail of star dust. */
    private static final float TRAIL_SPEED = 0.05f;

    public MulaEntityRenderer(EntityRendererFactory.Context renderManager) {
        super(renderManager, new MulaModel());
        addRenderLayer(new HalloLayer(this, HALLO_TEXTURE)); // add the custom billboard
    }

    @Override
    public Identifier getTextureLocation(MulaEntity entity) {
        return TEXTURES.getOrDefault(entity.getVariant(), Steveparty.id("textures/entity/mula.png"));
    }

    /** Draws the springy visual size (it swells with a little boing when fed), the hitbox keeps the real one. */
    @Override
    public void scaleModelForRender(float widthScale, float heightScale, MatrixStack poseStack, MulaEntity mula,
                                    BakedGeoModel model, boolean isReRender, float partialTick, int packedLight,
                                    int packedOverlay) {
        float ratio = mula.getMotion().visualScaleRatio(partialTick, mula.getScaleFactor());
        super.scaleModelForRender(widthScale * ratio, heightScale * ratio, poseStack, mula, model, isReRender,
                partialTick, packedLight, packedOverlay);
    }

    @Override
    public void actuallyRender(MatrixStack poseStack, MulaEntity animatable, BakedGeoModel model, @Nullable RenderLayer renderType, VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int renderColor) {
        if (!isReRender) {
            spawnParticles(animatable);
        }
        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender, partialTick, 0xF000F0, packedOverlay, renderColor);
    }

    @Override
    public @Nullable RenderLayer getRenderType(MulaEntity animatable, Identifier texture,
                                               @Nullable VertexConsumerProvider bufferSource,
                                               float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    /**
     * Sparkles, only for a Mula that is drawn (off screen or too far: nothing) and at most once per tick: the old
     * ambient twinkle, plus a trail of star dust in its colour while it flies. Follows the particle setting (none on
     * Minimal, fewer on Decreased).
     */
    private static void spawnParticles(MulaEntity mula) {
        if (mula.age == mula.lastEffectsAge) return;
        mula.lastEffectsAge = mula.age;
        ParticlesMode mode = MinecraftClient.getInstance().options.getParticles().getValue();
        if (mode == ParticlesMode.MINIMAL) return;
        boolean fewer = mode == ParticlesMode.DECREASED;
        World world = mula.getWorld();
        Random random = mula.getRandom();
        if (mula.age % (fewer ? 10 : 5) == 0) {
            double offsetX = (random.nextDouble() - 0.5) * 0.9;
            double offsetY = random.nextDouble() * 0.8 + 0.2;
            double offsetZ = (random.nextDouble() - 0.5) * 0.9;
            world.addParticle(ParticleTypes.WAX_OFF, mula.getX() + offsetX, mula.getY() + offsetY, mula.getZ() + offsetZ,
                    0, 0, 0);
        }
        if (mula.getMotion().speed() > TRAIL_SPEED && mula.age % (fewer ? 4 : 2) == 0) {
            // behind it: where it was a tick ago, a little below its centre
            world.addParticle(mula.starDust(), mula.prevX + (random.nextDouble() - 0.5) * 0.25,
                    mula.prevY + mula.getHeight() * 0.4 + (random.nextDouble() - 0.5) * 0.2,
                    mula.prevZ + (random.nextDouble() - 0.5) * 0.25, 0, -0.01, 0);
        }
    }

    // ----------------------
    // Billboard render layer
    // ----------------------
    /** The soft halo: a camera-facing glow on the Mula's centre, breathing with its bob (brighter and a bit bigger at
     * the top). */
    private static class HalloLayer extends GeoRenderLayer<MulaEntity> {
        private final Identifier texture;

        public HalloLayer(GeoRenderer<MulaEntity> renderer, Identifier texture) {
            super(renderer);
            this.texture = texture;
        }


        @Override
        public void render(MatrixStack matrices, MulaEntity entity, BakedGeoModel bakedModel,
                           @Nullable RenderLayer renderType, VertexConsumerProvider bufferSource,
                           @Nullable net.minecraft.client.render.VertexConsumer buffer, float partialTick,
                           int packedLight, int packedOverlay, int renderColor) {
            var bodyBone = bakedModel.getBone("head");
            if (bodyBone.isPresent()) {
                var client = MinecraftClient.getInstance();
                var camera = client.gameRenderer.getCamera();
                var rotation = camera.getRotation();
                Vector3d bonePos = bodyBone.get().getLocalPosition();
                MulaMotion motion = entity.getMotion();
                float glow = motion.glow(partialTick);
                matrices.push();
                matrices.translate(bonePos.x, bonePos.y, bonePos.z);
                matrices.multiply(rotation);
                float size = 0.95f + 0.1f * glow;
                matrices.scale(size, size, size);
                drawQuad(matrices, bufferSource.getBuffer(RenderLayer.getEntityTranslucentEmissive(texture)), packedLight,
                        (int) (255 * (0.75f + 0.25f * glow)));
                matrices.pop();
            }
        }

        private void drawQuad(MatrixStack matrices, net.minecraft.client.render.VertexConsumer vertices, int light, int alpha) {
            MatrixStack.Entry entry = matrices.peek();
            float minU = 0f, maxU = 1f;
            float minV = 0f, maxV = 1f;
            float halfSize = 1.0f;

            vertices.vertex(entry.getPositionMatrix(), -halfSize, -halfSize, 0.0F)
                    .color(255, 255, 255, alpha)
                    .texture(minU, maxV)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(entry, 0.0F, 1.0F, 0.0F);

            vertices.vertex(entry.getPositionMatrix(), halfSize, -halfSize, 0.0F)
                    .color(255, 255, 255, alpha)
                    .texture(maxU, maxV)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(entry, 0.0F, 1.0F, 0.0F);

            vertices.vertex(entry.getPositionMatrix(), halfSize, halfSize, 0.0F)
                    .color(255, 255, 255, alpha)
                    .texture(maxU, minV)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(entry, 0.0F, 1.0F, 0.0F);

            vertices.vertex(entry.getPositionMatrix(), -halfSize, halfSize, 0.0F)
                    .color(255, 255, 255, alpha)
                    .texture(minU, minV)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(entry, 0.0F, 1.0F, 0.0F);

        }
    }

}
