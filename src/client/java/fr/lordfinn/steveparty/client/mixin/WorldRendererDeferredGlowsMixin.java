package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.entity.DeferredGlows;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right before the translucent terrain is drawn (the fourth block layer of {@code render}, after solid, cutout mipped
 * and cutout; ordinal 3 with the Fabulous graphics, 5 without): the glows seen through a translucent block are drawn
 * there with a shader pack ({@link DeferredGlows}).
 * Iris has run its deferred passes by then (it does so earlier in the same method), and no Fabric event sits between
 * them and the translucent terrain. Not required: without it those glows are drawn after the translucent terrain,
 * like the others.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererDeferredGlowsMixin {
    @Inject(method = "render", require = 0, at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;renderLayer(Lnet/minecraft/client/render/RenderLayer;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", ordinal = 3),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;renderLayer(Lnet/minecraft/client/render/RenderLayer;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", ordinal = 5)})
    private void steveparty$glowsBeforeTranslucentTerrain(CallbackInfo ci) {
        DeferredGlows.drawBeforeTranslucentTerrain();
    }
}
