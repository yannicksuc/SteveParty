package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.BoxCostumeRenderState;
import fr.lordfinn.steveparty.client.entity.costume.BoxCostumeAnimatable;
import fr.lordfinn.steveparty.client.entity.costume.BoxCostumeClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Puts the player's Box Costume (if worn) on his render state; drawn by {@link LivingEntityRendererBoxCostumeMixin}. */
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererBoxCostumeMixin {

    @Inject(method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL"))
    private void steveparty$boxCostume(AbstractClientPlayerEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        BoxCostumeAnimatable box = BoxCostumeClient.boxOf(player);
        ((BoxCostumeRenderState) state).steveparty$setBoxCostume(box, BoxCostumeClient.isInsideBox(box));
    }
}
