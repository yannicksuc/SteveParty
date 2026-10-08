package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.entities.TokenPoses;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A right click with an empty hand on a mob pawn: its next pose (see {@link TokenPoses}), before the mob's own use. */
@Mixin(MobEntity.class)
public abstract class TokenPoseInteractMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void steveparty$nextPose(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        MobEntity mob = (MobEntity) (Object) this;
        if (!TokenPoses.posesOnClick(mob, player, hand)) return;
        TokenPoses.nextPose(mob);
        cir.setReturnValue(ActionResult.success(mob.getWorld().isClient));
    }
}
