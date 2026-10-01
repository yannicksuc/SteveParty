package fr.lordfinn.steveparty.client.pipe;

import fr.lordfinn.steveparty.blocks.custom.pipe.PipePose;
import fr.lordfinn.steveparty.entities.custom.PipeCarrierEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * How a traveller looks in a pipe (client side, drawing only): head (or snout) first along the pipe, eased round the bends,
 * squashed and stretched like a cartoon ({@link PipePose}), no name tag nor shadow; a jelly wobble once out. Applied
 * around the middle of its body before its renderer draws it ({@code EntityRenderDispatcherPipePoseMixin}), so it works
 * for every renderer: vanilla mobs and players (lying as if swimming, arms ahead: {@code BipedEntityModelPipeSwimMixin}),
 * items (spinning along the way), GeckoLib mobs and tokens.
 * <p>
 * First person: the camera stays in the middle of the pipe, the player's look is drawn gently toward the way it goes
 * (level, it looks ahead; up or down a pipe, half up or half down), never rolled; the mouse still moves it.
 */
public final class PipeTravellerPose {
    /** Head pitch of a lying mob (looks ahead, not at the bottom of the pipe). */
    private static final float HEAD_PITCH = -35;
    /** How much of the way to the pipe's direction the player's look goes each tick. */
    private static final float FOLLOW = 0.22f;
    /** Pitch the look goes to up or down a pipe. */
    private static final float UPRIGHT_PITCH = 45;
    /** How far ahead the look follows the way (blocks). */
    private static final double LOOK_AHEAD = 0.7;

    private static final Quaternionf ROTATION = new Quaternionf();
    private static final Vector3f FACING = new Vector3f(), WAY = new Vector3f();
    /** Travellers lately seen inside: when last seen inside, then when seen out (-1: not yet), in ticks of their age. */
    private static final Map<Entity, float[]> SEEN = new WeakHashMap<>();

    /** While a tall living traveller is drawn: its biped models swim (arms ahead), with this age for the kicks. */
    private static boolean swimming;
    private static float swimAge;

    private PipeTravellerPose() {}

    /** Is the biped model being posed now that of a traveller lying in a pipe? */
    public static boolean swimming() {
        return swimming;
    }

    public static float swimAge() {
        return swimAge;
    }

    /** A swimmer's pose: arms stretched ahead of the head, legs behind with a little flutter kick, looking ahead. */
    public static void swimPose(BipedEntityModel<?> model) {
        float kick = MathHelper.sin(swimAge * 0.9f) * 0.25f;
        model.head.pitch = -0.8f;
        model.head.yaw = 0;
        model.head.roll = 0;
        model.body.pitch = 0;
        model.body.yaw = 0;
        model.rightArm.pitch = model.leftArm.pitch = -2.95f;
        model.rightArm.yaw = model.leftArm.yaw = 0;
        model.rightArm.roll = 0.12f;
        model.leftArm.roll = -0.12f;
        model.rightLeg.pitch = kick;
        model.leftLeg.pitch = -kick;
        model.rightLeg.yaw = model.leftLeg.yaw = 0;
        model.rightLeg.roll = 0.04f;
        model.leftLeg.roll = -0.04f;
    }

    /** After the entity is drawn. */
    public static void end() {
        swimming = false;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PipeTravellerPose::followThePipe);
    }

    /** The carrier taking it through a pipe, or null. */
    public static @Nullable PipeCarrierEntity carrier(Entity entity) {
        return entity.getVehicle() instanceof PipeCarrierEntity carrier && carrier.points().size() >= 2 ? carrier : null;
    }

    /**
     * Before {@code renderer} draws {@code entity} (the matrices at its feet): pushes its pipe pose, or its wobble
     * once out.
     *
     * @return whether a pose was pushed (to pop after drawing)
     */
    public static boolean push(EntityRenderer<?, ?> renderer, Entity entity, EntityRenderState state, float tickDelta, MatrixStack matrices) {
        swimming = false;
        PipeCarrierEntity carrier = carrier(entity);
        if (carrier == null) return !SEEN.isEmpty() && wobble(entity, tickDelta, matrices);
        float now = entity.age + tickDelta;
        float[] seen = SEEN.get(entity);
        if (seen == null) SEEN.put(entity, seen = new float[2]);
        seen[0] = now;
        seen[1] = -1;

        double at = Math.min(carrier.travelled() + carrier.speed() * tickDelta, carrier.length());
        float bodyYaw = entity instanceof LivingEntity living ? MathHelper.lerpAngleDegrees(tickDelta, living.prevBodyYaw, living.bodyYaw)
                : entity.getYaw(tickDelta);
        float yaw = bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        FACING.set(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
        // Tall bodies (and items: spinning round the way) head first, the others snout first
        boolean lengthwise = !(entity instanceof ItemEntity) && PipePose.lengthwise(entity.getWidth(), entity.getHeight());
        PipePose.orientation(carrier.points(), carrier.lengths(), at, FACING, lengthwise, ROTATION);
        float stretch = PipePose.stretch(carrier.points(), carrier.lengths(), at, carrier.speed());
        float across = 1 / MathHelper.sqrt(stretch);
        float pivot = entity.getHeight() / 2;

        matrices.push();
        matrices.translate(0, pivot, 0);
        matrices.multiply(ROTATION);
        if (lengthwise) matrices.scale(across, across, stretch);
        else matrices.scale(across, stretch, across);
        if (renderer instanceof LivingEntityRenderer<?, ?, ?> && state instanceof LivingEntityRenderState living) {
            // Facing the pipe's frame (yaw 0), looking ahead, lying (not sitting, not crouching)
            living.bodyYaw = 0;
            living.yawDegrees = 0;
            living.pitch = lengthwise ? 0 : HEAD_PITCH;
            living.sneaking = false;
            if (living instanceof BipedEntityRenderState biped) biped.isInSneakingPose = false;
            swimming = !lengthwise;
            swimAge = living.age;
        } else if (entity instanceof LivingEntity) {
            // Other renderers (GeckoLib) turn it by its body yaw themselves: undone, it faces the pipe's frame too
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(bodyYaw));
        }
        matrices.translate(0, -pivot, 0);
        state.displayName = null;
        return true;
    }

    /** Just out of a pipe: taller, squashed, settling, from its feet. */
    private static boolean wobble(Entity entity, float tickDelta, MatrixStack matrices) {
        float[] seen = SEEN.get(entity);
        if (seen == null) return false;
        float now = entity.age + tickDelta;
        if (seen[1] < 0) {
            // Out since it was last seen inside: wobbles if that was just now (not when seen again much later)
            if (now - seen[0] > 3) {
                SEEN.remove(entity);
                return false;
            }
            seen[1] = now;
        }
        float ticks = now - seen[1];
        if (ticks > 14 || ticks < 0) {
            SEEN.remove(entity);
            return false;
        }
        float height = PipePose.exitWobble(ticks);
        float across = 1 / MathHelper.sqrt(height);
        matrices.push();
        matrices.scale(across, height, across);
        return true;
    }

    /** Should its shadow be drawn? Not inside a pipe. */
    public static boolean castsShadow(Entity entity) {
        return carrier(entity) == null;
    }

    /** The player in a pipe looks the way it goes, gently. */
    private static void followThePipe(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        if (client.world == null || client.world != player.getWorld()) SEEN.clear();
        PipeCarrierEntity carrier = carrier(player);
        if (carrier == null) return;
        double ahead = Math.min(carrier.travelled() + LOOK_AHEAD, carrier.length());
        if (!PipePose.direction(carrier.points(), PipePose.segment(carrier.lengths(), ahead), WAY)) return;
        float yaw = player.getYaw(), pitch = player.getPitch();
        float targetYaw = yaw, targetPitch;
        if (Math.abs(WAY.y) < 0.7f) {
            targetYaw = (float) (MathHelper.atan2(-WAY.x, WAY.z) * MathHelper.DEGREES_PER_RADIAN);
            targetPitch = 0;
        } else {
            targetPitch = WAY.y > 0 ? -UPRIGHT_PITCH : UPRIGHT_PITCH;
        }
        player.setYaw(yaw + MathHelper.wrapDegrees(targetYaw - yaw) * FOLLOW);
        player.setPitch(pitch + (targetPitch - pitch) * FOLLOW);
    }
}
