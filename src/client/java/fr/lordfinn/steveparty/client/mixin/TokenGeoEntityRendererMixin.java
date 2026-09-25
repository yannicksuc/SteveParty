package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.token.TokenFootAnchor;
import fr.lordfinn.steveparty.entities.TokenBase;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * GeckoLib tokens (Mula...): once the body rotations are applied ({@code applyRotations}, model space), the model is
 * shifted horizontally by minus its {@link TokenFootAnchor}, so that the base (drawn at the entity origin by
 * {@code TokenBaseRenderer}) is centred between its lowest limbs. Also applies to the re-renders of its layers.
 */
@Mixin(value = GeoEntityRenderer.class, remap = false)
public abstract class TokenGeoEntityRendererMixin {

    @Inject(method = "applyRotations", at = @At("TAIL"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void steveparty$centreTokenOnFeet(Entity animatable, MatrixStack poseStack, float ageInTicks,
                                              float rotationYaw, float partialTick, float nativeScale,
                                              CallbackInfo ci) {
        if (!TokenBase.isToken(animatable) || !(animatable instanceof GeoAnimatable geoAnimatable)) return;
        GeoEntityRenderer renderer = (GeoEntityRenderer) (Object) this;
        GeoModel geoModel = renderer.getGeoModel();
        BakedGeoModel model = geoModel.getBakedModel(geoModel.getModelResource(geoAnimatable, renderer));
        if (model == null) return;
        TokenFootAnchor.Anchor anchor = TokenFootAnchor.cached(model);
        if (anchor == null) anchor = TokenFootAnchor.computeGeckoLib(model);
        if (!anchor.isZero()) poseStack.translate(-anchor.x(), 0.0F, -anchor.z());
    }
}
