package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.StencilHammerRenderState;
import fr.lordfinn.steveparty.client.hammer.StencilHammerStrikes;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Puts the player's Stencil Hammer strike (if any) on its render state, for the arm pose. */
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererStencilHammerMixin {

    @Inject(method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL"))
    private void steveparty$hammerStrike(AbstractClientPlayerEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (!(state instanceof StencilHammerRenderState hammer)) return;
        float ticks = StencilHammerStrikes.strikeTicks(player.getId(), tickDelta);
        boolean mainHand = StencilHammerStrikes.strikesWithMainHand(player.getId());
        hammer.steveparty$setHammerStrike(ticks, mainHand ? player.getMainArm() : player.getMainArm().getOpposite());
    }
}
