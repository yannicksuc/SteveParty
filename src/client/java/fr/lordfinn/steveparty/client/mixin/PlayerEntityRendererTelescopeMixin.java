package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.TelescopeRenderState;
import fr.lordfinn.steveparty.client.telescope.TelescopePoses;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
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
 * head tilted like it (the turn: {@link LivingEntityRendererTelescopeMixin}; the bend and the hand on the tube:
 * {@link PlayerEntityModelTelescopeMixin}).
 */
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererTelescopeMixin {
    @Unique
    private static final TelescopePoses.Stance STEVEPARTY$STANCE = new TelescopePoses.Stance();
    /** A fully bent player is drawn this much lower (as a sneaking one is). */
    @Unique
    private static final double STEVEPARTY$BENT_DROP = 0.125;

    /** Works out his pose at the eyepiece for this frame, kept on him (the position offset is asked for first). */
    @Unique
    private static void steveparty$updateTelescope(AbstractClientPlayerEntity player, float tickDelta) {
        TelescopeRenderState telescope = (TelescopeRenderState) player;
        TelescopePoses.Pose pose = TelescopePoses.of(player.getUuid());
        float ease = pose == null ? 0f : pose.ease(tickDelta);
        if (pose == null || ease <= 0f) {
            telescope.steveparty$setTelescope(0f, 0f, 0, 0, 0, 0f);
            return;
        }
        float yaw = player.getYaw(tickDelta), pitch = player.getPitch(tickDelta);
        TelescopePoses.stance(pose.pos, yaw, pitch, MathHelper.lerp(tickDelta, player.lastRenderX, player.getX()),
                MathHelper.lerp(tickDelta, player.lastRenderY, player.getY()),
                MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ()), STEVEPARTY$STANCE);
        boolean rightHandEmpty = (player.getMainArm() == Arm.RIGHT ? player.getMainHandStack() : player.getOffHandStack()).isEmpty();
        telescope.steveparty$setTelescope(ease, STEVEPARTY$STANCE.bend, STEVEPARTY$STANCE.dx * ease,
                (STEVEPARTY$STANCE.dy - STEVEPARTY$BENT_DROP * STEVEPARTY$STANCE.bend) * ease, STEVEPARTY$STANCE.dz * ease,
                rightHandEmpty ? pitch : Float.NaN);
    }

    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void steveparty$telescopePose(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        steveparty$updateTelescope(player, tickDelta);
    }

    @Inject(method = "getPositionOffset(Lnet/minecraft/client/network/AbstractClientPlayerEntity;F)Lnet/minecraft/util/math/Vec3d;",
            at = @At("RETURN"), cancellable = true)
    private void steveparty$telescopePlace(AbstractClientPlayerEntity player, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        steveparty$updateTelescope(player, tickDelta);
        TelescopeRenderState telescope = (TelescopeRenderState) player;
        if (telescope.steveparty$telescopeEase() <= 0f) return;
        cir.setReturnValue(cir.getReturnValue().add(telescope.steveparty$telescopeDx(), telescope.steveparty$telescopeDy(),
                telescope.steveparty$telescopeDz()));
    }
}
