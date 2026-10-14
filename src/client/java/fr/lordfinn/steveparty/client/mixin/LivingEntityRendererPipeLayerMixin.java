package fr.lordfinn.steveparty.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fr.lordfinn.steveparty.client.pipe.PipeTravellerPose;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A traveller in a pipe is drawn solid (cutout, writing its depth) instead of in its model's translucent layer (a
 * player's skin, an allay): a see-through layer waits for the end of the frame, after the pipe's glass, and failed
 * against its depth (under shader packs and batching mods, which flush the entity buffers their own way: the
 * dispatcher's flush, EntityRenderDispatcherPipePoseMixin, never reached them). Only its cape, solid, showed through
 * the glass. Its texture's half-transparent pixels are cut out instead (minecraft-rendering: a translucent thing that
 * must look solid is drawn cutout).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererPipeLayerMixin {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @ModifyReturnValue(method = "getRenderLayer", at = @At("RETURN"))
    private @Nullable RenderLayer steveparty$solidInPipes(@Nullable RenderLayer layer, LivingEntity entity, boolean showBody,
                                                          boolean translucent, boolean showOutline) {
        if (layer == null || !showBody || translucent || PipeTravellerPose.carrier(entity) == null) return layer;
        Identifier texture = ((EntityRenderer) (Object) this).getTexture(entity);
        return RenderLayer.getEntityCutoutNoCull(texture);
    }
}
