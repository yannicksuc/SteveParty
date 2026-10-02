package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.TelescopeRenderState;
import fr.lordfinn.steveparty.client.telescope.TelescopePoses;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player looking through a Telescope, seen from outside: drawn at its eyepiece, his body turned like the tube, his
 * head tilted like it (the bend and the hand on the tube: {@link PlayerEntityModelTelescopeMixin}).
 */
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererTelescopeMixin {
    @Unique
    private static final TelescopePoses.Stance STEVEPARTY$STANCE = new TelescopePoses.Stance();
    /** A fully bent player is drawn this much lower (as a sneaking one is). */
    @Unique
    private static final double STEVEPARTY$BENT_DROP = 0.125;

    @Inject(method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL"))
    private void steveparty$telescopePose(AbstractClientPlayerEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        TelescopeRenderState telescope = (TelescopeRenderState) state;
        TelescopePoses.Pose pose = TelescopePoses.of(player.getUuid());
        float ease = pose == null ? 0f : pose.ease(tickDelta);
        if (pose == null || ease <= 0f) {
            telescope.steveparty$setTelescope(0f, 0f, 0, 0, 0, 0f);
            return;
        }
        float yaw = player.getYaw(tickDelta), pitch = player.getPitch(tickDelta);
        TelescopePoses.stance(pose.pos, yaw, pitch, state.x, state.y, state.z, STEVEPARTY$STANCE);
        telescope.steveparty$setTelescope(ease, STEVEPARTY$STANCE.bend, STEVEPARTY$STANCE.dx * ease,
                (STEVEPARTY$STANCE.dy - STEVEPARTY$BENT_DROP * STEVEPARTY$STANCE.bend) * ease, STEVEPARTY$STANCE.dz * ease,
                player.getStackInArm(Arm.RIGHT).isEmpty() ? pitch : Float.NaN);
        // The body turns to face the way the tube points, the head straight on it
        float turn = MathHelper.wrapDegrees(yaw - state.bodyYaw) * ease;
        state.bodyYaw += turn;
        state.yawDegrees = MathHelper.wrapDegrees(yaw - state.bodyYaw);
        // standing still at the eyepiece
        state.limbAmplitudeMultiplier *= 1f - ease;
    }

    @Inject(method = "getPositionOffset(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)Lnet/minecraft/util/math/Vec3d;",
            at = @At("RETURN"), cancellable = true)
    private void steveparty$telescopePlace(PlayerEntityRenderState state, CallbackInfoReturnable<Vec3d> cir) {
        TelescopeRenderState telescope = (TelescopeRenderState) state;
        if (telescope.steveparty$telescopeEase() <= 0f) return;
        cir.setReturnValue(cir.getReturnValue().add(telescope.steveparty$telescopeDx(), telescope.steveparty$telescopeDy(),
                telescope.steveparty$telescopeDz()));
    }
}
