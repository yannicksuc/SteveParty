package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.entities.custom.bubble.BubbleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * How a rider floats in a Bubbler's bubble, as in water with no weight: calm, slow, looping, each rider out of step
 * with the others ({@link #phase}, from his entity id).
 * <ul>
 *     <li>{@link #pose}: a biped's (player, zombie...) legs dangle, swinging gently out of phase, a little apart; his
 *     empty arms (or holding an item) rise a little from his sides and drift. Arms busy (a bow drawn, a punch, a
 *     spyglass) and the arms a model poses after the biped one (a zombie's) keep their pose.</li>
 *     <li>{@link #sway}: any rider's whole body (a villager's, a pig's) bobs and leans slowly around its middle.</li>
 * </ul>
 * Never for the player's own first-person hand: only a vignette there ({@link BubbleRiderClient}).
 */
public final class BubbleFloatPose {
    /** The legs' swing (radians, about 7 s a swing), how far apart they hang (radians, breathing a little). */
    private static final float LEG_SWING = 0.2f, LEG_SPEED = 0.045f, LEG_FORWARD = 0.08f, LEG_SPREAD = 0.07f, LEG_SPREAD_DRIFT = 0.03f;
    /** The arms: how far they rise from the sides and drift (radians), how far forward, how fast. */
    private static final float ARM_RISE = 0.45f, ARM_DRIFT = 0.12f, ARM_FORWARD = 0.2f, ARM_PITCH_DRIFT = 0.1f, ARM_SPEED = 0.05f;
    /** An arm holding an item rises only this share as much. */
    private static final float HOLDING = 0.5f;
    /** The body's lean (degrees) and bob (blocks), and their pace (radians a tick: 8 to 10 s a sway). */
    private static final float LEAN_PITCH = 4f, LEAN_ROLL = 3f, BOB = 0.04f, LEAN_SPEED = 0.03f, ROLL_SPEED = 0.024f, BOB_SPEED = 0.06f;

    private BubbleFloatPose() {
    }

    /** Whether {@code entity} floats in a bubble, seen from outside (not his own first-person view). */
    public static boolean floats(Entity entity) {
        if (!(entity.getVehicle() instanceof BubbleEntity)) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        return entity != client.getCameraEntity() || !client.options.getPerspective().isFirstPerson();
    }

    /** Each rider's own place in the loops (radians), so that riders never float in step. */
    private static float phase(Entity entity) {
        return (entity.getId() * 2.399963f) % MathHelper.TAU;
    }

    /** A biped's floating legs and arms, after its model's own posing ({@code time}: its age in ticks, smooth). */
    public static void pose(BipedEntityModel<?> model, LivingEntity entity, float time) {
        if (!floats(entity)) return;
        float phase = phase(entity);
        float legs = time * LEG_SPEED + phase;
        model.rightLeg.pitch = LEG_FORWARD + LEG_SWING * MathHelper.sin(legs);
        model.leftLeg.pitch = LEG_FORWARD + LEG_SWING * MathHelper.sin(legs + 2.2f);
        model.rightLeg.yaw = 0;
        model.leftLeg.yaw = 0;
        model.rightLeg.roll = LEG_SPREAD + LEG_SPREAD_DRIFT * MathHelper.sin(time * 0.03f + phase);
        model.leftLeg.roll = -LEG_SPREAD - LEG_SPREAD_DRIFT * MathHelper.sin(time * 0.033f + phase + 1);
        if (model.handSwingProgress > 0) return;
        float arms = time * ARM_SPEED + phase;
        float right = lift(model.rightArmPose), left = lift(model.leftArmPose);
        if (right > 0) {
            model.rightArm.roll = right * (ARM_RISE + ARM_DRIFT * MathHelper.sin(arms));
            if (model.rightArmPose == BipedEntityModel.ArmPose.EMPTY) model.rightArm.pitch = -ARM_FORWARD + ARM_PITCH_DRIFT * MathHelper.sin(arms * 0.8f + 0.5f);
        }
        if (left > 0) {
            model.leftArm.roll = -left * (ARM_RISE + ARM_DRIFT * MathHelper.sin(arms + 1.3f));
            if (model.leftArmPose == BipedEntityModel.ArmPose.EMPTY) model.leftArm.pitch = -ARM_FORWARD + ARM_PITCH_DRIFT * MathHelper.sin(arms * 0.8f + 2.1f);
        }
    }

    /** How far an arm in this pose rises: fully empty, half holding an item, not at all busy. */
    private static float lift(BipedEntityModel.ArmPose pose) {
        return switch (pose) {
            case EMPTY -> 1;
            case ITEM -> HOLDING;
            default -> 0;
        };
    }

    /** A rider's whole body bobbing and leaning slowly around its middle (the matrices at its feet, turned its way). */
    public static void sway(LivingEntity entity, MatrixStack matrices, float time) {
        if (!floats(entity)) return;
        float phase = phase(entity);
        float middle = entity.getHeight() / 2;
        matrices.translate(0, middle + BOB * MathHelper.sin(time * BOB_SPEED + phase), 0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(LEAN_PITCH * MathHelper.sin(time * LEAN_SPEED + phase)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(LEAN_ROLL * MathHelper.sin(time * ROLL_SPEED + phase * 1.3f)));
        matrices.translate(0, -middle, 0);
    }
}
