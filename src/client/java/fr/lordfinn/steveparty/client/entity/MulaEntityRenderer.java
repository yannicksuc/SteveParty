package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.MulaEffects;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.entities.custom.MulaMotion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

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
    /**
     * Where the food it ate floats, in the head bone (model pixels): in the belly, below the eyes, in the thin glassy
     * gap between its inner body (z -4) and its translucent shell (z -4.5), so the shell tints it and nothing hides it.
     */
    private static final float BELLY_Y = 4.1f, BELLY_Z = -4.26f, BELLY_SIZE = 4.2f;

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
            animatable.getEffects().lastRenderAge = animatable.age;
            spawnParticles(animatable);
            renderFlyingItem(poseStack, animatable, bufferSource, partialTick);
        }
        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender, partialTick, 0xF000F0, packedOverlay, renderColor);
    }

    /** The food it ate floats in its belly: drawn first, in the head's space, so its glassy shell covers it. */
    @Override
    public void renderRecursively(MatrixStack poseStack, MulaEntity mula, GeoBone bone, RenderLayer renderType,
                                  VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender,
                                  float partialTick, int packedLight, int packedOverlay, int renderColor) {
        if (!isReRender && "head".equals(bone.getName())) {
            renderBellyItem(poseStack, mula, bone, bufferSource, partialTick);
        }
        super.renderRecursively(poseStack, mula, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, renderColor);
    }

    private static void renderBellyItem(MatrixStack poseStack, MulaEntity mula, GeoBone head,
                                        VertexConsumerProvider bufferSource, float partialTick) {
        ItemStack food = mula.getLastFood();
        if (food.isEmpty()) return;
        float plop = mula.getEffects().bellyItemScale(partialTick);
        if (plop <= 0.01f) return;
        poseStack.push();
        // the head's own transform, as GeckoLib applies it
        RenderUtil.translateMatrixToBone(poseStack, head);
        RenderUtil.translateToPivotPoint(poseStack, head);
        RenderUtil.rotateMatrixAroundBone(poseStack, head);
        RenderUtil.scaleMatrixForBone(poseStack, head);
        RenderUtil.translateAwayFromPivotPoint(poseStack, head);
        // it bobs and rocks a little in there, like in jelly
        float t = mula.age + partialTick;
        poseStack.translate(0, (BELLY_Y + 0.25f * MathHelper.sin(t * 0.09f)) / 16f, BELLY_Z / 16f);
        poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(8f * MathHelper.sin(t * 0.06f)));
        float size = BELLY_SIZE / 16f * plop;
        poseStack.scale(size, size, size);
        MinecraftClient.getInstance().getItemRenderer().renderItem(food, ModelTransformationMode.FIXED, 0xF000F0,
                OverlayTexture.DEFAULT_UV, poseStack, bufferSource, mula.getWorld(), mula.getId());
        poseStack.pop();
    }

    /**
     * The food flying from the feeder's hand into its mouth (accelerating, spinning, shrinking: sucked in), or a
     * refused item spat back at the player in an arc.
     */
    private static void renderFlyingItem(MatrixStack poseStack, MulaEntity mula, VertexConsumerProvider bufferSource,
                                         float partialTick) {
        MulaEffects effects = mula.getEffects();
        ItemStack stack = effects.flyingStack();
        if (stack.isEmpty()) return;
        float p = effects.flyingProgress(partialTick);
        double mx = MathHelper.lerp(partialTick, mula.prevX, mula.getX());
        double my = MathHelper.lerp(partialTick, mula.prevY, mula.getY()) + mula.getHeight() * 0.5;
        double mz = MathHelper.lerp(partialTick, mula.prevZ, mula.getZ());
        double x, y, z;
        float size, spin;
        if (effects.isFlyingOut()) {
            // out of the mouth, up and back to the player, shrinking away
            double e = p;
            x = MathHelper.lerp(e, mx, effects.flyX());
            z = MathHelper.lerp(e, mz, effects.flyZ());
            y = MathHelper.lerp(e, my, effects.flyY()) + Math.sin(e * Math.PI) * 0.6;
            size = 0.4f * (1f - 0.6f * p);
            spin = p * 540f;
        } else {
            // sucked in: slow start, fast end, a little arc
            double e = p * p;
            x = MathHelper.lerp(e, effects.flyX(), mx);
            z = MathHelper.lerp(e, effects.flyZ(), mz);
            y = MathHelper.lerp(e, effects.flyY(), my) + Math.sin(p * Math.PI) * 0.35;
            size = 0.4f * (1f - 0.7f * p);
            spin = p * 720f;
        }
        // the pose stack is at the entity, already scaled by the springy size ratio (scaleModelForRender)
        float ratio = mula.getMotion().visualScaleRatio(partialTick, mula.getScaleFactor());
        if (ratio <= 0.01f) return;
        poseStack.push();
        poseStack.translate((x - mx) / ratio, (y - my + mula.getHeight() * 0.5) / ratio, (z - mz) / ratio);
        poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
        poseStack.scale(size / ratio, size / ratio, size / ratio);
        MinecraftClient.getInstance().getItemRenderer().renderItem(stack, ModelTransformationMode.GROUND, 0xF000F0,
                OverlayTexture.DEFAULT_UV, poseStack, bufferSource, mula.getWorld(), mula.getId() + 1);
        poseStack.pop();
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
    /**
     * The soft halo: a camera-facing glow on the Mula's centre in its own colour, breathing with its heartbeat, bigger
     * and brighter the fuller it is, flaring when it breathes out rings of light.
     */
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
                float full = motion.fullness(partialTick);
                float flare = motion.flareLevel(partialTick);
                matrices.push();
                matrices.translate(bonePos.x, bonePos.y, bonePos.z);
                matrices.multiply(rotation);
                float size = (0.95f + 0.1f * glow) * (1f + 0.25f * full + 0.35f * flare);
                matrices.scale(size, size, size);
                int tint = entity.getVariant().getGlowColor();
                // mostly white near the centre of the texture, tinted towards the Mula's colour
                int r = 170 + (((tint >> 16) & 0xFF) * 85 / 255);
                int g = 170 + (((tint >> 8) & 0xFF) * 85 / 255);
                int b = 170 + ((tint & 0xFF) * 85 / 255);
                int alpha = (int) (255 * MathHelper.clamp(0.7f + 0.2f * glow + 0.1f * full + 0.3f * flare, 0f, 1f));
                drawQuad(matrices, bufferSource.getBuffer(RenderLayer.getEntityTranslucentEmissive(texture)), packedLight,
                        r, g, b, alpha);
                matrices.pop();
            }
        }

        private void drawQuad(MatrixStack matrices, net.minecraft.client.render.VertexConsumer vertices, int light,
                              int r, int g, int b, int alpha) {
            MatrixStack.Entry entry = matrices.peek();
            float minU = 0f, maxU = 1f;
            float minV = 0f, maxV = 1f;
            float halfSize = 1.0f;

            vertices.vertex(entry.getPositionMatrix(), -halfSize, -halfSize, 0.0F)
                    .color(r, g, b, alpha)
                    .texture(minU, maxV)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(entry, 0.0F, 1.0F, 0.0F);

            vertices.vertex(entry.getPositionMatrix(), halfSize, -halfSize, 0.0F)
                    .color(r, g, b, alpha)
                    .texture(maxU, maxV)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(entry, 0.0F, 1.0F, 0.0F);

            vertices.vertex(entry.getPositionMatrix(), halfSize, halfSize, 0.0F)
                    .color(r, g, b, alpha)
                    .texture(maxU, minV)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(entry, 0.0F, 1.0F, 0.0F);

            vertices.vertex(entry.getPositionMatrix(), -halfSize, halfSize, 0.0F)
                    .color(r, g, b, alpha)
                    .texture(minU, minV)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(entry, 0.0F, 1.0F, 0.0F);
        }
    }

}
