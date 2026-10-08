package fr.lordfinn.steveparty.client.pawn;

import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnPose;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * The little statue of a player, shared by the player pawns and the podiums: the player model (its layer's parts) with
 * the proportions of a figurine (body, arms and legs as a player's, only the head bigger), standing in a
 * {@linkplain PlayerPawnPose pose}. The overlay layers (hat, sleeves, jacket, pants) follow their part.
 * <p>
 * Drawn like a player model: {@link #pose} on the parts, and, in the model's frame (flipped, y down, its feet
 * {@value #FEET} blocks below, before the renderer's {@code translate(0, -1.501, 0)}), {@link #transform} for the
 * moves of the whole figure.
 */
public final class PlayerStatue {
    public static final float HEAD_SCALE = 1.5F;
    /** Height of the statue, in model pixels: the legs and body (24) under the big head. */
    public static final float HEIGHT_PIXELS = 24.0F + 8.0F * HEAD_SCALE;
    /** The statue drawn as high as a player model of the same scale (32 pixels): the scale to apply. */
    public static final float AS_HIGH_AS_A_PLAYER = 32.0F / HEIGHT_PIXELS;
    /** Where the feet are below the origin of {@link #transform} (vanilla's 1.501). */
    private static final float FEET = 1.501F;

    private PlayerStatue() {
    }

    /** {@code root} (a player model layer's root) stands in {@code pose}. */
    public static void pose(ModelPart root, PlayerPawnPose pose) {
        ModelPart head = root.getChild("head"), body = root.getChild("body");
        ModelPart rightArm = root.getChild("right_arm"), leftArm = root.getChild("left_arm");
        ModelPart rightLeg = root.getChild("right_leg"), leftLeg = root.getChild("left_leg");
        body.setAngles(0, 0, 0);
        head.setAngles(rad(pose.headPitch), rad(pose.headYaw), rad(pose.headRoll));
        rightArm.setAngles(rad(pose.rightArmPitch), rad(pose.rightArmYaw), rad(pose.rightArmRoll));
        leftArm.setAngles(rad(pose.leftArmPitch), rad(pose.leftArmYaw), rad(pose.leftArmRoll));
        rightLeg.setAngles(rad(pose.rightLegPitch), 0, rad(pose.rightLegRoll));
        leftLeg.setAngles(rad(pose.leftLegPitch), 0, rad(pose.leftLegRoll));

        scale(head, HEAD_SCALE, HEAD_SCALE, HEAD_SCALE);
        follow(root.getChild("hat"), head);
        follow(root.getChild("jacket"), body);
        follow(root.getChild("right_sleeve"), rightArm);
        follow(root.getChild("left_sleeve"), leftArm);
        follow(root.getChild("right_pants"), rightLeg);
        follow(root.getChild("left_pants"), leftLeg);
    }

    /** The pose's moves of the whole figure: lifted, sitting, leaning back, upside down on its hands. */
    public static void transform(MatrixStack matrices, PlayerPawnPose pose) {
        if (pose.lift != 0 || pose.forward != 0) matrices.translate(0, -pose.lift / 16.0F, -pose.forward / 16.0F);
        if (pose.tilt != 0) {
            // Around the hips (12 pixels above the feet)
            float hips = 12.0F / 16.0F - FEET;
            matrices.translate(0, hips, 0);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-pose.tilt));
            matrices.translate(0, -hips, 0);
        }
        if (pose.upsideDown) {
            // Turned over, the hands of the raised arms on the ground: shoulders 2 pixels below the neck, arms 12 long
            float arm = 12.0F * MathHelper.cos((180.0F - Math.abs(pose.rightArmRoll)) * MathHelper.RADIANS_PER_DEGREE);
            float hands = (24.0F - 2.0F + arm) / 16.0F;
            matrices.translate(0, -hands, 0);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
        }
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
