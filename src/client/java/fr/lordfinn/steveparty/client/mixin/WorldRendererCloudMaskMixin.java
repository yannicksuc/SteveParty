package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.renderer.HaloBeam;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right before the clouds are drawn (with the Fabulous graphics, their target already bound): the light beams leave
 * their silhouette in the depth buffer, so that the clouds do not cover them ({@link HaloBeam#maskClouds}). No Fabric
 * event sits right there. Not required: without it the clouds cover the beams' far side, as before.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererCloudMaskMixin {
    @Inject(method = "renderClouds", require = 0, at = @At("HEAD"))
    private void steveparty$maskBeamsFromClouds(CallbackInfo ci) {
        HaloBeam.maskClouds();
    }
}
