package fr.lordfinn.steveparty.client.pawn;

import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnEntity;
import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnPose;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * The statue of a player pawn: the player model with the proportions of a little figurine (a normal body, a big head
 * and big arms), standing in its {@linkplain PlayerPawnPose pose}. The overlay layers (hat, sleeves, jacket, pants)
 * follow their part.
 */
public class PlayerPawnModel extends PlayerEntityModel<PlayerPawnEntity> {
    public static final float HEAD_SCALE = 1.5F;
    /** Arms: thicker, a little longer. */
    public static final float ARM_THICKNESS = 1.35F;
    public static final float ARM_LENGTH = 1.12F;
    /** The thicker arms move out as much, not into the body (pixels). */
    private static final float ARM_OUTSET = 0.6F;

    private final float rightArmPivotX;
    private final float leftArmPivotX;

    public PlayerPawnModel(ModelPart root, boolean thinArms) {
        super(root, thinArms);
        this.rightArmPivotX = this.rightArm.pivotX;
        this.leftArmPivotX = this.leftArm.pivotX;
    }

    @Override
    public void setAngles(PlayerPawnEntity pawn, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        super.setAngles(pawn, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        PlayerPawnPose pose = pawn.getStatuePose();
        this.body.setAngles(0, 0, 0);
        this.head.setAngles(rad(pose.headPitch), rad(pose.headYaw), rad(pose.headRoll));
        this.rightArm.setAngles(rad(pose.rightArmPitch), rad(pose.rightArmYaw), rad(pose.rightArmRoll));
        this.leftArm.setAngles(rad(pose.leftArmPitch), rad(pose.leftArmYaw), rad(pose.leftArmRoll));
        this.rightLeg.setAngles(rad(pose.rightLegPitch), 0, rad(pose.rightLegRoll));
        this.leftLeg.setAngles(rad(pose.leftLegPitch), 0, rad(pose.leftLegRoll));
        this.rightArm.pivotX = this.rightArmPivotX - ARM_OUTSET;
        this.leftArm.pivotX = this.leftArmPivotX + ARM_OUTSET;

        scale(this.head, HEAD_SCALE, HEAD_SCALE, HEAD_SCALE);
        scale(this.rightArm, ARM_THICKNESS, ARM_LENGTH, ARM_THICKNESS);
        scale(this.leftArm, ARM_THICKNESS, ARM_LENGTH, ARM_THICKNESS);
        follow(this.hat, this.head);
        follow(this.jacket, this.body);
        follow(this.rightSleeve, this.rightArm);
        follow(this.leftSleeve, this.leftArm);
        follow(this.rightPants, this.rightLeg);
        follow(this.leftPants, this.leftLeg);
    }

    private static float rad(float degrees) {
        return degrees * MathHelper.RADIANS_PER_DEGREE;
    }

    private static void scale(ModelPart part, float x, float y, float z) {
        part.xScale = x;
        part.yScale = y;
        part.zScale = z;
    }

    /** {@code layer} takes the place, angles and size of {@code part}. */
    private static void follow(ModelPart layer, ModelPart part) {
        layer.copyTransform(part);
        scale(layer, part.xScale, part.yScale, part.zScale);
    }
}
