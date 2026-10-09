package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.render.geo.GeoBones;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import fr.lordfinn.steveparty.utils.Argb;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.texture.AnimatableTexture;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.Color;

/**
 * Draws a Frousseux: its candle wax, a little see-through (FrousseuxModel), then over the same model the pool on its
 * top, tinted a hint of its candle's wax colour ({@code FrousseuxColor.accent}), and its flame ({@link FlameLayer}),
 * tinted its flame colour and glowing, dimmer as its health goes down ({@link FrousseuxEntity.Flame}). Its hands are drawn one-sided;
 * its wax shell (the body's cube, hollow underneath, its overlay and the sleeves) two-sided, so the inside of its
 * walls shows from below and the drips on the far faces show through the gaps. The body is lit by its
 * own flame. The three textures share one UV layout and never overlap.
 */
public class FrousseuxRenderer extends GeoEntityRenderer<FrousseuxEntity> {
    private static final Identifier WAX = Steveparty.id("textures/entity/frousseux_wax.png");
    /** Its flame's animated textures (the same frames): the flame, then its heart and wick; the candle holder's too. */
    public static final Identifier FLAME = Steveparty.id("textures/entity/frousseux_flame.png");
    public static final Identifier CORE = Steveparty.id("textures/entity/frousseux_flame_core.png");
    /** Its flame's bones, one a stage (FrousseuxEntity.Flame order): each its own size, pixel for pixel. */
    private static final String[] STAGE_BONES = {"flame_full", "flame_high", "flame_low", "flame_ember"};

    private final ItemRenderer itemRenderer;

    public FrousseuxRenderer(EntityRendererFactory.Context context) {
        super(context, new FrousseuxModel());
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.2f;
        addRenderLayer(new ShellLayer(this));
        addRenderLayer(new WaxLayer(this));
        addRenderLayer(new FlameLayer(this));
    }

    /** A candle: lit by its own flame (full block light), whatever the light around. */
    @Override
    public void render(FrousseuxEntity frousseux, float entityYaw, float partialTick, MatrixStack poseStack,
                       VertexConsumerProvider bufferSource, int packedLight) {
        int light = frousseux.deathTime > 0 ? packedLight
                : LightmapTextureManager.pack(15, LightmapTextureManager.getSkyLightCoordinates(packedLight));
        if (frousseux.bodyAlpha(partialTick) <= 0.01f && frousseux.flameAlpha(partialTick) <= 0.01f) return; // out of its owner's way
        super.render(frousseux, entityYaw, partialTick, poseStack, bufferSource, light);
        renderShownItem(frousseux, partialTick, poseStack, bufferSource, light);
    }

    /** Its body's opacity: faded out of its owner's way (FrousseuxEntity#bodyAlpha), else whole. */
    @Override
    public Color getRenderColor(FrousseuxEntity frousseux, float partialTick, int packedLight) {
        return Color.ofARGB(Argb.alpha(frousseux.bodyAlpha(partialTick)), 255, 255, 255);
    }

    /**
     * Its two-sided wax: see-through, writing its depth; faded out of its owner's way, none (FrousseuxGhostLayers: its
     * flame, drawn after it, shows through it from every side).
     */
    private static RenderLayer shell(FrousseuxEntity frousseux, Identifier texture) {
        return frousseux.isFaded() ? FrousseuxGhostLayers.noCull(texture) : RenderLayer.getEntityTranslucent(texture);
    }

    /** {@code rgb} opaque, each channel times {@code brightness} (0 to 1): a flame's colour at its stage. */
    public static int shade(int rgb, float brightness) {
        return Argb.opaque(Argb.scale(rgb, brightness));
    }

    /** {@code argb} with the given opacity (0 to 1) over its own. */
    static int withAlpha(int argb, float alpha) {
        return (Argb.alpha((((argb >>> 24) & 0xFF) / 255f) * alpha) << 24) | (argb & 0xFFFFFF);
    }

    /** What it stole: under its body, turning slowly, or flying from the player to it (or back). */
    private void renderShownItem(FrousseuxEntity frousseux, float partialTick, MatrixStack poseStack,
                                 VertexConsumerProvider bufferSource, int light) {
        Vec3d at = frousseux.itemOffset(partialTick);
        if (at == null) return;
        float time = frousseux.age + partialTick;
        poseStack.push();
        poseStack.translate(at.x, at.y + 0.03 * MathHelper.sin(time * 0.15f), at.z);
        poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 3f));
        poseStack.scale(0.8f, 0.8f, 0.8f);
        itemRenderer.renderItem(frousseux.getShownItem(), ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV,
                poseStack, bufferSource, frousseux.getWorld(), frousseux.getId());
        poseStack.pop();
    }

    private static final class WaxLayer extends GeoRenderLayer<FrousseuxEntity> {
        WaxLayer(GeoRenderer<FrousseuxEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(MatrixStack poseStack, FrousseuxEntity frousseux, BakedGeoModel bakedModel, @Nullable RenderLayer renderType,
                           VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick,
                           int packedLight, int packedOverlay) {
            // see-through wax as the rest, two-sided: from inside the hollow body it closes the top, which hides the
            // flame (drawn after it) by depth
            RenderLayer layer = shell(frousseux, WAX);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, frousseux, layer, bufferSource.getBuffer(layer),
                    partialTick, packedLight, packedOverlay, withAlpha(0xFF000000 | frousseux.getColor().accent, frousseux.bodyAlpha(partialTick)));
        }
    }

    /**
     * The wax shell alone, without culling: the body's cube (left out of the main pass by FrousseuxModel) and the outer
     * layers; their inner faces show, mirrored. The hands keep their bones (the sleeves hang on them) but not their own
     * cubes for this pass; the lids and the flame are left out. Then every bone is put back as the next passes (the
     * pool, the flame) want it: the body's cube shown, the overlays hidden.
     */
    private static final class ShellLayer extends GeoRenderLayer<FrousseuxEntity> {
        private static final String[] HOLDERS = {"left_hand", "right_hand"};
        private static final String[] OTHERS = {"lids", "flame"};
        private final boolean[] othersHidden = new boolean[OTHERS.length];

        ShellLayer(GeoRenderer<FrousseuxEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(MatrixStack poseStack, FrousseuxEntity frousseux, BakedGeoModel bakedModel, @Nullable RenderLayer renderType,
                           VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick,
                           int packedLight, int packedOverlay) {
            GeoBones.hide(bakedModel, FrousseuxModel.OVERLAY_BONES, false);
            GeoBones.hide(bakedModel, "body", false);
            for (String name : HOLDERS) GeoBones.hideOnlyItself(bakedModel, name);
            GeoBones.hideFor(bakedModel, OTHERS, othersHidden);
            RenderLayer layer = shell(frousseux, getTextureResource(frousseux));
            getRenderer().reRender(bakedModel, poseStack, bufferSource, frousseux, layer, bufferSource.getBuffer(layer),
                    partialTick, packedLight, packedOverlay, withAlpha(0xFFFFFFFF, frousseux.bodyAlpha(partialTick)));
            GeoBones.restore(bakedModel, OTHERS, othersHidden);
            GeoBones.hide(bakedModel, HOLDERS, false);
            GeoBones.hide(bakedModel, FrousseuxModel.OVERLAY_BONES, true);
        }
    }

    /**
     * Its flame, alone (the other bones hidden for these passes; of its stage's bones, its stage's only): its wick and
     * flame on one pair of crossed planes, a pixel of texture for a pixel of model. The animated flame texture tinted
     * its flame colour made more saturated (FrousseuxColor#flameEdge: it still reads under shaders' glow), dimmer as
     * its health goes down; then, on the same faces, its heart and its wick (frousseux_flame_core.png, the same frames),
     * tinted a paler shade. Both unshaded and full bright (a light, not a lit thing), see-through. The flame bone is hidden from every other pass (FrousseuxModel).
     */
    private static final class FlameLayer extends GeoRenderLayer<FrousseuxEntity> {
        private static final String[] OTHERS = {"body_overlay", "left_hand", "right_hand", "lids"};
        private final boolean[] othersHidden = new boolean[OTHERS.length];

        FlameLayer(GeoRenderer<FrousseuxEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(MatrixStack poseStack, FrousseuxEntity frousseux, BakedGeoModel bakedModel, @Nullable RenderLayer renderType,
                           VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick,
                           int packedLight, int packedOverlay) {
            if (frousseux.deathTime > 0) return;
            GeoBone body = bakedModel.getBone("body").orElse(null), flame = bakedModel.getBone("flame").orElse(null);
            if (body == null || flame == null) return;
            boolean bodyHidden = body.isHidden();
            GeoBones.hideFor(bakedModel, OTHERS, othersHidden);
            GeoBones.hideOnlyItself(body);
            flame.setHidden(false);
            int stage = frousseux.getFlame().ordinal();
            for (int i = 0; i < STAGE_BONES.length; i++) GeoBones.hide(bakedModel, STAGE_BONES[i], i != stage);

            float brightness = frousseux.getFlame().brightness;
            AnimatableTexture.setAndUpdate(FLAME); // its frames (frousseux_flame.png.mcmeta): GeckoLib animates it
            RenderLayer layer = RenderLayer.getBeaconBeam(FLAME, true);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, frousseux, layer, bufferSource.getBuffer(layer),
                    partialTick, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV,
                    withAlpha(shade(frousseux.getColor().flameEdge, brightness), frousseux.flameAlpha(partialTick)));
            AnimatableTexture.setAndUpdate(CORE);
            RenderLayer heartLayer = RenderLayer.getBeaconBeam(CORE, true);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, frousseux, heartLayer, bufferSource.getBuffer(heartLayer),
                    partialTick, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV,
                    withAlpha(shade(frousseux.getColor().flameHeart, brightness), frousseux.flameAlpha(partialTick)));

            flame.setHidden(true);
            body.setHidden(bodyHidden);
            body.setChildrenHidden(false);
            GeoBones.restore(bakedModel, OTHERS, othersHidden);
        }
    }
}
