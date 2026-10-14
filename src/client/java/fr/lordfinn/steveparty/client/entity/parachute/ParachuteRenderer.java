package fr.lordfinn.steveparty.client.entity.parachute;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.parachute.ParachuteItem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.Color;

/**
 * Draws a parachute's canopy (geo/entity/parachute.geo.json, any model: its bottom centre at the origin, 16 px a block)
 * from the current origin. Cutout, one-sided, opaque: it writes depth like any model, drawn with its entity. Open
 * underneath: the model carries inward planes on its sides (their inside shows from below), nothing under its top.
 * <p>
 * Undyed, as painted; dyed, two passes on the same faces that never cover each other's pixels (cutout): the white cloth
 * as painted, then the stripes in grey tinted by the dye.
 */
public class ParachuteRenderer extends GeoObjectRenderer<ParachuteAnimatable> {
    /** The canopy model's bottom (px): the geo's canopy bone pivots there. */
    private static final float MODEL_BOTTOM = 30;

    private final ParachuteAnimatable canopy = new ParachuteAnimatable();

    public ParachuteRenderer() {
        super(new DefaultedEntityGeoModel<>(Steveparty.id("parachute")));
    }

    /** Draws a canopy with stripes of {@code color} (ARGB), its bottom centre at the origin. */
    public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, int color, float tickDelta) {
        var manager = canopy.getAnimatableInstanceCache().getManagerForId(getInstanceId(canopy));
        if (manager.getFirstTickTime() == -1) manager.startedAt(0);
        this.animatable = canopy;
        if ((color & 0xFFFFFF) == (ParachuteItem.DEFAULT_COLOR & 0xFFFFFF)) {
            pass(matrices, consumers, light, ParachuteAnimatable.TEXTURE, 0xFFFFFFFF, tickDelta);
        } else {
            pass(matrices, consumers, light, ParachuteAnimatable.WHITE, 0xFFFFFFFF, tickDelta);
            pass(matrices, consumers, light, ParachuteAnimatable.STRIPES, color | 0xFF000000, tickDelta);
        }
    }

    private void pass(MatrixStack matrices, VertexConsumerProvider consumers, int light, Identifier texture, int color, float tickDelta) {
        canopy.texture = texture;
        canopy.color = color;
        defaultRender(matrices, canopy, consumers, null, null, 0, tickDelta, light);
    }

    @Override
    public Identifier getTextureLocation(ParachuteAnimatable animatable) {
        return animatable.texture;
    }

    @Override
    public @Nullable RenderLayer getRenderType(ParachuteAnimatable animatable, Identifier texture, @Nullable VertexConsumerProvider bufferSource,
                                               float partialTick) {
        return RenderLayer.getEntityCutout(texture);
    }

    @Override
    public Color getRenderColor(ParachuteAnimatable animatable, float partialTick, int packedLight) {
        return new Color(animatable.color);
    }

    @Override
    public void preRender(MatrixStack poseStack, ParachuteAnimatable animatable, BakedGeoModel model, @Nullable VertexConsumerProvider bufferSource,
                          @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int renderColor) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, renderColor);
        // GeoObjectRenderer centres objects on a block: ours hangs from its bottom centre
        poseStack.translate(-0.5F, -0.51F - MODEL_BOTTOM / 16, -0.5F);
    }
}
