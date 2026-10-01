package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.BoxCostumeRenderState;
import fr.lordfinn.steveparty.client.entity.costume.BoxCostumeAnimatable;
import fr.lordfinn.steveparty.client.entity.costume.BoxCostumeClient;
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
 * <li>Arms: detached from the shoulders, they come out of the box's side arm holes and hang down and outward along
 * it at the merchant's relaxed angle, a little forward, with his slow sway, and follow the box (its height, its
 * width, its holes opening and closing). They swing softly while walking and still rise to use an item; whatever
 * they hold stays visible outside the box.</li>
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
    /** Fully worn, the arms come out 1 px below the centre of the (6 px high) holes. */
    @Unique
    private static final float STEVEPARTY$ARM_BELOW_HOLE = 1.0F;
    /** The arm starts this far inside the wall (model px). */
    @Unique
    private static final float STEVEPARTY$ARM_INSIDE = 0.5F;
    /**
     * Rest pose of the arms, like the merchant's (his idle: 20 to 27.5 degrees out from hanging straight down, a
     * 4 s sway), a bit more open for a player's longer arm to clear the box, and slightly forward.
     */
    @Unique
    private static final float STEVEPARTY$ARM_OUT = 0.61F, STEVEPARTY$ARM_SWAY = 0.065F, STEVEPARTY$ARM_SWAY_SPEED = 0.0785F,
            STEVEPARTY$ARM_FORWARD = 0.12F;
    /**
     * Share of the vanilla arm pitch kept up to {@link #STEVEPARTY$ARM_FREE_PITCH} (the walking swing: the merchant's
     * is soft); beyond it (aiming, blocking, eating...) the rest is kept whole, so the arm still rises.
     */
    @Unique
    private static final float STEVEPARTY$ARM_SWING = 0.35F, STEVEPARTY$ARM_FREE_PITCH = 1.0F;
    /** Legs a little shortened, reaching from the ground to the box: 12 px long, feet at 24 px (model px, y down). */
    @Unique
    private static final float STEVEPARTY$LEG_SCALE = 0.86F, STEVEPARTY$LEG_LENGTH = 12.0F, STEVEPARTY$FEET_Y = 24.0F;

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
    private void steveparty$limbsAroundTheBox(PlayerEntityRenderState state, CallbackInfo ci) {
        if (!(state instanceof BoxCostumeRenderState costume)) return;
        BoxCostumeAnimatable box = costume.steveparty$getBoxCostume();
        if (box == null) return;
        PlayerEntityModel model = (PlayerEntityModel) (Object) this;
        float lift = box.getLift();
        // Where the box's arm holes are now, in the player model's space (origin 24 px above the feet, y down)
        // (the body is squashed from the feet while the box drops or rises: heights are divided by that squash)
        float squash = BoxCostumeClient.bodySquash(lift);
        float holeY = STEVEPARTY$FEET_Y - (STEVEPARTY$HOLE_HEIGHT + (BoxCostumeRenderer.MERCHANT_LIFT + BoxCostumeRenderer.WAIST_LIFT - STEVEPARTY$ARM_BELOW_HOLE) * lift)
                * STEVEPARTY$MODEL_PX / squash;
        float wallX = STEVEPARTY$WALL * (1.0F + BoxCostumeRenderer.WAIST_WIDENING * lift) * STEVEPARTY$MODEL_PX;
        // The two arms sway out of step, like his
        float out = STEVEPARTY$ARM_OUT + MathHelper.sin(state.age * STEVEPARTY$ARM_SWAY_SPEED) * STEVEPARTY$ARM_SWAY;
        float otherOut = STEVEPARTY$ARM_OUT + MathHelper.sin(state.age * STEVEPARTY$ARM_SWAY_SPEED + 0.6F) * STEVEPARTY$ARM_SWAY;
        steveparty$armThroughHole(model.rightArm, -(wallX - STEVEPARTY$ARM_INSIDE), holeY, out, box.getArmHole());
        steveparty$armThroughHole(model.leftArm, wallX - STEVEPARTY$ARM_INSIDE, holeY, -otherOut, box.getArmHole());
        steveparty$shortLeg(model.rightLeg);
        steveparty$shortLeg(model.leftLeg);
    }

    /** The arm comes out of the hole (its shoulder end in the hole) and hangs outward by {@code roll}. */
    @Unique
    private static void steveparty$armThroughHole(ModelPart arm, float pivotX, float holeY, float roll, float scale) {
        arm.pivotX = pivotX;
        arm.pivotY = holeY;
        arm.pivotZ = 0.0F;
        float pitch = Math.abs(arm.pitch);
        arm.pitch = Math.signum(arm.pitch) * (Math.min(pitch, STEVEPARTY$ARM_FREE_PITCH) * STEVEPARTY$ARM_SWING
                + Math.max(pitch - STEVEPARTY$ARM_FREE_PITCH, 0.0F)) - STEVEPARTY$ARM_FORWARD;
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
