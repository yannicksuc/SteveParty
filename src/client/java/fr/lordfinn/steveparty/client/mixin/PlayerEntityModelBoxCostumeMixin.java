package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.BoxCostumeRenderState;
import fr.lordfinn.steveparty.client.entity.costume.BoxCostumeAnimatable;
import fr.lordfinn.steveparty.client.entity.costume.BoxCostumeRenderer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Box Costume: the wearer's limbs are placed like the merchant's around his box.
 * <ul>
 * <li>Arms: detached from the shoulders, they come out of the box's side arm holes, horizontal, and follow the box
 * (its height, its width, its holes opening and closing). They still swing and use items, with a smaller sweep;
 * whatever they hold stays visible outside the box.</li>
 * <li>Legs: short ones under the box (feet on the ground), so they never stick out of it while walking.</li>
 * </ul>
 */
@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelBoxCostumeMixin {
    /** Model px per world px: the player model is drawn at 0.9375. */
    @Unique
    private static final float STEVEPARTY$MODEL_PX = 1.0F / 0.9375F;
    /** The box model: arm holes centred 8 px above its floor, on its side walls 8 px from its centre. */
    @Unique
    private static final float STEVEPARTY$HOLE_HEIGHT = 8.0F, STEVEPARTY$WALL = 8.0F;
    /** The arm starts this far inside the wall (model px). */
    @Unique
    private static final float STEVEPARTY$ARM_INSIDE = 2.5F;
    /** Share of the vanilla arm swing kept (a full swing would cut through the box). */
    @Unique
    private static final float STEVEPARTY$ARM_SWING = 0.4F;
    /** Legs shortened to fit under the box: 12 px long, feet at 24 px (model px, y down). */
    @Unique
    private static final float STEVEPARTY$LEG_SCALE = 0.4F, STEVEPARTY$LEG_LENGTH = 12.0F, STEVEPARTY$FEET_Y = 24.0F;

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
    private void steveparty$limbsAroundTheBox(PlayerEntityRenderState state, CallbackInfo ci) {
        if (!(state instanceof BoxCostumeRenderState costume)) return;
        BoxCostumeAnimatable box = costume.steveparty$getBoxCostume();
        if (box == null) return;
        PlayerEntityModel model = (PlayerEntityModel) (Object) this;
        float lift = box.getLift();
        // Where the box's arm holes are now, in the player model's space (origin 24 px above the feet, y down)
        float holeY = STEVEPARTY$FEET_Y - (STEVEPARTY$HOLE_HEIGHT + (BoxCostumeRenderer.MERCHANT_LIFT + BoxCostumeRenderer.WAIST_LIFT) * lift) * STEVEPARTY$MODEL_PX;
        float wallX = STEVEPARTY$WALL * (1.0F + BoxCostumeRenderer.WAIST_WIDENING * lift) * STEVEPARTY$MODEL_PX;
        steveparty$armThroughHole(model.rightArm, -(wallX - STEVEPARTY$ARM_INSIDE), holeY, MathHelper.HALF_PI, box.getArmHole());
        steveparty$armThroughHole(model.leftArm, wallX - STEVEPARTY$ARM_INSIDE, holeY, -MathHelper.HALF_PI, box.getArmHole());
        steveparty$shortLeg(model.rightLeg);
        steveparty$shortLeg(model.leftLeg);
    }

    /** The arm lies horizontal through the hole (rolled a quarter turn, the 4 px wide arm centred on the hole). */
    @Unique
    private static void steveparty$armThroughHole(ModelPart arm, float pivotX, float holeY, float roll, float scale) {
        arm.pivotX = pivotX;
        arm.pivotY = holeY + 1.0F;
        arm.pivotZ = 0.0F;
        arm.pitch *= STEVEPARTY$ARM_SWING;
        arm.yaw *= STEVEPARTY$ARM_SWING;
        arm.roll = roll + arm.roll * STEVEPARTY$ARM_SWING;
        // Shrinks into the hole as it closes (with what it holds)
        arm.xScale = arm.yScale = arm.zScale = Math.max(scale, 0.001F);
    }

    @Unique
    private static void steveparty$shortLeg(ModelPart leg) {
        leg.yScale = STEVEPARTY$LEG_SCALE;
        leg.pivotY = STEVEPARTY$FEET_Y - STEVEPARTY$LEG_LENGTH * STEVEPARTY$LEG_SCALE;
    }
}
