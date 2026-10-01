package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.BoxCostumeRenderState;
import fr.lordfinn.steveparty.client.entity.costume.BoxCostumeAnimatable;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Box Costume worn at the waist: the arms rest on the rim of the box, spread out over it (else they would vanish
 * inside it with whatever they hold), still swinging and using items as usual.
 */
@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelBoxCostumeMixin {
    /** How far the arms spread out (rad): enough for them to come out over the rim. */
    @Unique
    private static final float STEVEPARTY$ARM_SPREAD = 0.75F;

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
    private void steveparty$armsOnTheBox(PlayerEntityRenderState state, CallbackInfo ci) {
        if (!(state instanceof BoxCostumeRenderState costume)) return;
        BoxCostumeAnimatable box = costume.steveparty$getBoxCostume();
        if (box == null || box.isHidden()) return;
        PlayerEntityModel model = (PlayerEntityModel) (Object) this;
        model.rightArm.roll += STEVEPARTY$ARM_SPREAD;
        model.leftArm.roll -= STEVEPARTY$ARM_SPREAD;
    }
}
