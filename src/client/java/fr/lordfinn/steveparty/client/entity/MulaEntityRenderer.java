package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.MulaEffects;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.entities.custom.MulaMotion;
import fr.lordfinn.steveparty.client.squish.SquishAnimations;
import fr.lordfinn.steveparty.client.utils.ShaderPacks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.client.option.ParticlesMode;
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
    /** The soft little star of light its meals become inside it. */
    private static final Identifier WISP_TEXTURE = Steveparty.id("textures/entity/mula_wisp.png");
    /** Most inner lights (one per 1/8 of its hunger). */
    private static final int MAX_WISPS = 8;
    /**
     * Height of its inner lights, from the "head" bone's pivot (model pixels). That pivot is the centre of the body
     * cube (geo: pivot y 6.53, cube 2..11), and it follows the bob, the animations and the drawn size: 0 keeps them in
     * the middle of the body at every size and in every pose (they used to sit 2.1 px lower, in the bottom half).
     */
    private static final float LIGHTS_Y = 0f;
    /** How far the inner lights wander from the centre (model pixels), sideways and up / down (the body is 8 wide). */
    private static final float LIGHTS_REACH_X = 2.6f, LIGHTS_REACH_Y = 1.6f;
    /**
     * Farther than this from the camera (blocks) the little stars of its inner lights are not drawn: a pixel or two
     * each there, they were up to 16 quads per Mula. The soft heart glow stays.
     */
    private static final double WISPS_RANGE = 24;

    public MulaEntityRenderer(EntityRendererFactory.Context renderManager) {
        super(renderManager, new MulaModel());
        addRenderLayer(new HalloLayer(this, HALLO_TEXTURE)); // add the custom billboard
    }

    @Override
    public Identifier getTextureLocation(MulaEntity entity) {
        return TEXTURES.getOrDefault(entity.getVariant(), Steveparty.id("textures/entity/mula.png"));
    }

    /**
     * The size the model is drawn at, relative to its hitbox: its springy visual size (it swells with a little boing
     * when fed), and the token spell's transformation while it plays (GeckoLib renderers are not living entity
     * renderers: the squish animation does not reach them by itself). The pose stack of the render layers is scaled
     * by it; the token's own scale (scale attribute) is not in that stack.
     */
    private static float drawnRatio(MulaEntity mula, float partialTick) {
        return mula.getMotion().visualScaleRatio(partialTick, mula.getScaleFactor()) * SquishAnimations.scaleMultiplier(mula, partialTick);
    }

    /**
     * Where the glows (halo, inner lights) are drawn: vanilla's translucent emissive layer, not depth-writing and not
     * outlined by the glowing effect (only the body is). Without a shader pack, drawn with the entity, unchanged. With
     * one, drawn apart ({@link DeferredGlows}): drawn with the entity, the pack's clouds covered them. Then
     * {@code behindTranslucent} tells a Mula seen through glass or water from the others.
     */
    private static VertexConsumer glowBuffer(VertexConsumerProvider bufferSource, Identifier texture, boolean shaderPack,
                                             boolean behindTranslucent) {
        return shaderPack ? DeferredGlows.buffer(texture, behindTranslucent)
                : bufferSource.getBuffer(RenderLayer.getEntityTranslucentEmissive(texture, false));
    }

    /** Draws the springy visual size (it swells with a little boing when fed), the hitbox keeps the real one. */
    @Override
    public void scaleModelForRender(float widthScale, float heightScale, MatrixStack poseStack, MulaEntity mula,
                                    BakedGeoModel model, boolean isReRender, float partialTick, int packedLight,
                                    int packedOverlay) {
        float ratio = drawnRatio(mula, partialTick);
        super.scaleModelForRender(widthScale * ratio, heightScale * ratio, poseStack, mula, model, isReRender,
                partialTick, packedLight, packedOverlay);
    }

    @Override
    public void actuallyRender(MatrixStack poseStack, MulaEntity animatable, BakedGeoModel model, @Nullable RenderLayer renderType, VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int renderColor) {
        if (!isReRender) {
            MulaEffects effects = animatable.getEffects();
            effects.lastRenderAge = animatable.age;
            if (!ShaderPacks.renderingShadows()) {
                effects.cameraDistanceSq = MinecraftClient.getInstance().gameRenderer.getCamera().getPos()
                        .squaredDistanceTo(animatable.getX(), animatable.getY(), animatable.getZ());
            }
            spawnParticles(animatable);
            renderFloatingItem(poseStack, animatable, bufferSource, partialTick);
        }
        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender, partialTick, 0xF000F0, packedOverlay, renderColor);
    }

    /**
     * The food being absorbed (rising from the hand, turning slowly, shrinking into light) or refused (floating up,
     * hesitating, sinking back to the hand): its pose comes from MulaEffects.
     */
    private static void renderFloatingItem(MatrixStack poseStack, MulaEntity mula, VertexConsumerProvider bufferSource,
                                           float partialTick) {
        MulaEffects effects = mula.getEffects();
        ItemStack stack = effects.itemStack();
        if (stack.isEmpty()) return;
        effects.updateItemPose(partialTick);
        float size = effects.itemScale();
        // the pose stack is at the entity's feet, already scaled by its drawn size (scaleModelForRender)
        float ratio = drawnRatio(mula, partialTick);
        if (size <= 0.005f || ratio <= 0.01f) return;
        double mx = MathHelper.lerp(partialTick, mula.prevX, mula.getX());
        double my = MathHelper.lerp(partialTick, mula.prevY, mula.getY());
        double mz = MathHelper.lerp(partialTick, mula.prevZ, mula.getZ());
        poseStack.push();
        poseStack.translate((effects.itemX() - mx) / ratio, (effects.itemY() - my) / ratio, (effects.itemZ() - mz) / ratio);
        poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(effects.itemSpin()));
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
     * Minimal, fewer on Decreased). Far from the camera, they share a small budget per tick with the other far Mulas
     * (MulaEffects#mayAddEverydayParticle): a crowd stays sparkling without thousands of particles.
     */
    private static void spawnParticles(MulaEntity mula) {
        if (mula.isToken()) return; // a board token is a still pawn: no ambient sparkles
        if (mula.age == mula.lastEffectsAge) return;
        mula.lastEffectsAge = mula.age;
        ParticlesMode mode = MinecraftClient.getInstance().options.getParticles().getValue();
        if (mode == ParticlesMode.MINIMAL) return;
        boolean fewer = mode == ParticlesMode.DECREASED;
        World world = mula.getWorld();
        Random random = mula.getRandom();
        // at night they twinkle like little stars: twice as often
        long day = Math.floorMod(world.getTimeOfDay(), 24000L);
        int every = (fewer ? 10 : 5) / (day >= 13000 && day < 23000 ? 2 : 1);
        MulaEffects effects = mula.getEffects();
        if (mula.age % every == 0 && effects.mayAddEverydayParticle()) {
            double offsetX = (random.nextDouble() - 0.5) * 0.9;
            double offsetY = random.nextDouble() * 0.8 + 0.2;
            double offsetZ = (random.nextDouble() - 0.5) * 0.9;
            world.addParticle(ParticleTypes.WAX_OFF, mula.getX() + offsetX, mula.getY() + offsetY, mula.getZ() + offsetZ,
                    0, 0, 0);
        }
        if (mula.getMotion().speed() > TRAIL_SPEED && mula.age % (fewer ? 4 : 2) == 0 && effects.mayAddEverydayParticle()) {
            // behind it: where it was a tick ago, a little below its centre
            world.addParticle(mula.starDust(), mula.prevX + (random.nextDouble() - 0.5) * 0.25,
                    mula.prevY + mula.getHeight() * 0.3 + (random.nextDouble() - 0.5) * 0.2,
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
                           int packedLight, int packedOverlay) {
            // the shader pack's shadow map: glows cast no shadow (their quads were thrown away, after a ray each)
            if (ShaderPacks.renderingShadows()) return;
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
                float warm = motion.absorbGlow(partialTick);
                matrices.push();
                // the bone's position is in world units, while this pose stack is scaled by the Mula's drawn size
                float ratio = drawnRatio(entity, partialTick);
                if (ratio <= 0.01f) {
                    matrices.pop();
                    return;
                }
                matrices.translate(bonePos.x / ratio, bonePos.y / ratio, bonePos.z / ratio);
                boolean shaderPack = ShaderPacks.inUse();
                boolean behindTranslucent = shaderPack && behindTranslucent(entity, camera, partialTick);
                renderInnerLights(matrices, entity, bodyBone.get(), camera, bufferSource, partialTick, full, warm,
                        shaderPack, behindTranslucent);
                matrices.multiply(rotation);
                // (times the token's scale: a small token has a small halo, a big one a big halo)
                float size = (0.95f + 0.1f * glow) * (1f + 0.25f * full + 0.35f * flare + 0.3f * warm) * entity.getScale();
                matrices.scale(size, size, size);
                int tint = entity.getVariant().getHaloColor();
                // bright, clearly in the Mula's colour (the texture keeps a white-hot centre)
                int r = 100 + (((tint >> 16) & 0xFF) * 155 / 255);
                int g = 100 + (((tint >> 8) & 0xFF) * 155 / 255);
                int b = 100 + ((tint & 0xFF) * 155 / 255);
                int alpha = (int) (255 * MathHelper.clamp(0.7f + 0.2f * glow + 0.1f * full + 0.3f * flare + 0.25f * warm,
                        0f, 1f));
                drawQuad(matrices, glowBuffer(bufferSource, texture, shaderPack, behindTranslucent), packedLight, r, g, b, alpha);
                matrices.pop();
            }
        }

        /** Whether its glow is seen through a translucent block: one ray per Mula and per tick, not per frame. */
        private static boolean behindTranslucent(MulaEntity mula, Camera camera, float partialTick) {
            MulaEffects effects = mula.getEffects();
            if (effects.behindTranslucentAge != mula.age) {
                effects.behindTranslucentAge = mula.age;
                effects.behindTranslucent = DeferredGlows.behindTranslucent(mula.getWorld(), camera.getPos(),
                        mula.getLerpedPos(partialTick).add(0, mula.getHeight() * MulaEntity.CENTER, 0));
            }
            return effects.behindTranslucent;
        }

        /**
         * What it has eaten, as light inside it: a soft heart glow and one little tinted star per eighth of its hunger,
         * drifting slowly round the middle of its body, brighter, bigger and quicker the fuller it is, trembling near
         * the burst while it is on edge, flaring warm when a meal's light sinks in. Camera-facing and drawn just in
         * front of the body's surface on the camera's side (the translucent body would hide them inside), within its
         * silhouette, so they read as glowing through it. They shrink with the body (the burst's pop). Nothing allocated per frame.
         */
        private void renderInnerLights(MatrixStack matrices, MulaEntity mula, GeoBone head, Camera camera,
                                       VertexConsumerProvider bufferSource, float partialTick, float full, float warm,
                                       boolean shaderPack, boolean behindTranslucent) {
            float lights = full * MAX_WISPS;
            if (lights < 0.02f && warm < 0.02f) return;
            float headScale = head.getScaleY();
            if (headScale < 0.05f) return;
            // how far the body's surface is towards the camera: the view direction in the body's frame hits the cube
            double dx = camera.getPos().x - MathHelper.lerp(partialTick, mula.prevX, mula.getX());
            double dy = camera.getPos().y - (MathHelper.lerp(partialTick, mula.prevY, mula.getY()) + mula.getHeight() * MulaEntity.CENTER);
            double dz = camera.getPos().z - MathHelper.lerp(partialTick, mula.prevZ, mula.getZ());
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0E-3) return;
            float yaw = MathHelper.lerpAngleDegrees(partialTick, mula.prevBodyYaw, mula.bodyYaw) * MathHelper.RADIANS_PER_DEGREE;
            double lx = (dx * MathHelper.cos(yaw) + dz * MathHelper.sin(yaw)) / length;
            double lz = (-dx * MathHelper.sin(yaw) + dz * MathHelper.cos(yaw)) / length;
            double ly = dy / length;
            double surface = Math.min(7.0, 4.75 / Math.max(Math.abs(lx), Math.max(Math.abs(ly), Math.abs(lz))));
            float px = headScale / 16f * mula.getScale();
            float t = mula.animationAge(partialTick);
            int tint = mula.getVariant().getGlowColor();
            int r = 90 + (((tint >> 16) & 0xFF) * 165 / 255);
            int g = 90 + (((tint >> 8) & 0xFF) * 165 / 255);
            int b = 90 + ((tint & 0xFF) * 165 / 255);
            VertexConsumer vertices = glowBuffer(bufferSource, WISP_TEXTURE, shaderPack, behindTranslucent);
            // the pose stack is world-aligned here: step towards the camera up to the surface, then face the camera
            matrices.push();
            float step = (float) surface * px;
            matrices.translate(dx / length * step, dy / length * step, dz / length * step);
            matrices.multiply(camera.getRotation());
            MatrixStack.Entry entry = matrices.peek();
            float z = 0f;
            // the heart: a soft glow in the middle of its body, warmer and bigger when a meal has just come in
            float heart = (1.2f + 1.5f * full + 2.0f * warm) * (0.9f + 0.1f * MathHelper.sin(t * 0.2f)) * px;
            int heartAlpha = (int) (255 * MathHelper.clamp(0.18f + 0.25f * full + 0.45f * warm, 0f, 0.85f));
            wisp(vertices, entry, 0, LIGHTS_Y * px, z, heart, r, g, b, heartAlpha);
            // only while it is on edge (a player very close, a meal just taken): a calm full Mula's lights just drift
            float tremble = mula.getMotion().tremble(partialTick);
            int count = mula.getEffects().cameraDistanceSq < WISPS_RANGE * WISPS_RANGE ? Math.min(MAX_WISPS, MathHelper.ceil(lights)) : 0;
            for (int i = 0; i < count; i++) {
                float shown = MathHelper.clamp(lights - i, 0f, 1f);
                float a = t * (0.035f + 0.05f * full) + i * 2.39996f;
                float reach = 0.55f + 0.45f * ((i * 0.618f) % 1f);
                float wx = MathHelper.cos(a) * LIGHTS_REACH_X * reach + tremble * 0.3f * MathHelper.sin(t * 2.1f + i);
                float wy = LIGHTS_Y + MathHelper.sin(a * 1.3f + i) * LIGHTS_REACH_Y * reach
                        + tremble * 0.3f * MathHelper.cos(t * 1.9f + i);
                float half = (0.45f + 0.25f * full) * (0.75f + 0.4f * Math.abs(MathHelper.sin(t * 0.25f + i))) * shown * px;
                int alpha = (int) (255 * MathHelper.clamp((0.55f + 0.45f * full + 0.3f * warm) * shown, 0f, 1f));
                wisp(vertices, entry, wx * px, wy * px, z + (i + 1) * 0.002f * px, half, 255, 255, 255, alpha);
                wisp(vertices, entry, wx * px, wy * px, z + (i + 1) * 0.002f * px + 0.001f * px, half * 2.4f, r, g, b,
                        alpha / 3);
            }
            matrices.pop();
        }

        private static void wisp(VertexConsumer vertices, MatrixStack.Entry entry, float cx, float cy, float cz,
                                 float half, int r, int g, int b, int alpha) {
            vertex(vertices, entry, cx - half, cy - half, cz, 0f, 1f, r, g, b, alpha);
            vertex(vertices, entry, cx + half, cy - half, cz, 1f, 1f, r, g, b, alpha);
            vertex(vertices, entry, cx + half, cy + half, cz, 1f, 0f, r, g, b, alpha);
            vertex(vertices, entry, cx - half, cy + half, cz, 0f, 0f, r, g, b, alpha);
        }

        private static void vertex(VertexConsumer vertices, MatrixStack.Entry entry, float x, float y, float z, float u,
                                   float v, int r, int g, int b, int alpha) {
            vertices.vertex(entry.getPositionMatrix(), x, y, z).color(r, g, b, alpha).texture(u, v)
                    .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(entry, 0.0F, 1.0F, 0.0F);
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
