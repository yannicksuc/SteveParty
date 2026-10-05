package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.BoxCostumeRenderState;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player inside his Box Costume casts no round entity shadow: a block doesn't. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererBoxCostumeShadowMixin {
    @Inject(method = "getShadowRadius(Lnet/minecraft/entity/Entity;)F", at = @At("HEAD"), cancellable = true)
    private void steveparty$noShadowInTheBox(Entity entity, CallbackInfoReturnable<Float> cir) {
        if (entity instanceof BoxCostumeRenderState costume && costume.steveparty$isInBox()) cir.setReturnValue(0.0F);
    }
}
