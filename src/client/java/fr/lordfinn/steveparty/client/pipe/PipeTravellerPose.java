package fr.lordfinn.steveparty.client.pipe;

import fr.lordfinn.steveparty.blocks.custom.pipe.PipePose;
import fr.lordfinn.steveparty.entities.custom.PipeCarrierEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * How a traveller looks in a pipe (client side, drawing only, worked out each frame from its carrier's path: nothing
 * is sent, nothing changes on the server).
 * <ul>
 *     <li><b>Where</b>: on the line through the middle of the pipes, the corners rounded ({@link PipePose#position}),
 *     at the very distance it has travelled at this frame: it glides, round the bends too.</li>
 *     <li><b>Size</b>: its hitbox is small inside (it fits any pipe); a tall body is drawn as big as the tube allows,
 *     lying along the run ({@link #FIT} across, up to {@link #LONGEST} long).</li>
 *     <li><b>Pose</b>: head (or snout) first along the pipe, eased round the bends; bipeds (players, zombies...) swim
 *     (arms ahead, flutter kick) and fold round the corners: their legs, trunk and head with the arms are each turned
 *     the way of the pipe where they are ({@link PipePose#bend}), see {@link #swimPose}.</li>
 *     <li>Squashed and stretched like a cartoon ({@link PipePose#stretch}), no name tag nor shadow; a jelly wobble
 *     once out.</li>
 * </ul>
 * Applied around the middle of the body before its renderer draws it ({@code EntityRenderDispatcherPipePoseMixin}), so
 * it works for every renderer: vanilla mobs, players (seen by the others, and in third person), items (spinning along
 * the way), GeckoLib mobs and tokens. The lying pose of a living entity is set in its renderer's render
 * ({@code LivingEntityRendererPipePoseMixin}), its name tag hidden by {@code EntityRendererNameTagMixin}.
 * <p>
 * The traveller's own view ({@code CameraPipeViewMixin}): the camera glides along the same line, in the middle of the
 * tube, and turns with the pipe (eased like the body, never rolled, never straight up or down); the player's own look
 * is not touched while it travels (the mouse moves the view freely, added to the pipe's turn), and takes the view's
 * direction when it comes out.
 */
public final class PipeTravellerPose {
    /** Head pitch of a lying mob that is not a biped (looks ahead, not at the bottom of the pipe). */
    private static final float HEAD_PITCH = -35;
    /** Width (hitbox) a tall body is drawn with, lying in the tube, and the longest it gets (blocks). */
    public static final float FIT = 0.45f, LONGEST = 1.6f;
    /** Where a biped's head and legs are from its middle, in body lengths: where their own turn is taken. */
    private static final float HEAD_AT = 0.34f, LEGS_AT = -0.31f;
    /** How long the view takes to follow the pipe's way (ticks; it gets about two thirds of the way in that time). */
    private static final float VIEW_LAG = 2.5f;

    private static final Quaternionf ROTATION = new Quaternionf(), HEAD = new Quaternionf(), LEGS = new Quaternionf(), PART = new Quaternionf();
    private static final Vector3f FACING = new Vector3f(), ANGLES = new Vector3f();
    private static final Vector3d MIDDLE = new Vector3d();
    private static final float[] HEADING = new float[2];
    /** Travellers lately seen inside: when last seen inside, then when seen out (-1: not yet), in ticks of their age. */
    private static final Map<Entity, float[]> SEEN = new WeakHashMap<>();

    /** While a tall living traveller is drawn: its biped models swim (arms ahead). */
    private static boolean swimming;
    /** The traveller drawn in a pipe now (null: none), and the one drawn lying by its living entity renderer. */
    private static @Nullable Entity drawn, lying;
    /** Head pitch of the lying one. */
    private static float lyingPitch;

    /** The path the player's view follows (null: none), its look when that path began, and the turn added to it. */
    private static @Nullable List<Vec3d> viewPath;
    private static float viewFromYaw, viewFromPitch, viewYaw, viewPitch, viewTime;

    private PipeTravellerPose() {}

    /** The carrier taking it through a pipe, or null. */
    public static @Nullable PipeCarrierEntity carrier(Entity entity) {
        return entity.getVehicle() instanceof PipeCarrierEntity carrier && carrier.points().size() >= 2 ? carrier : null;
    }

    /** How far along its path the carrier is at this frame. */
    private static double travelled(PipeCarrierEntity carrier, float tickDelta) {
        return Math.min(carrier.travelled() + carrier.speed() * tickDelta, carrier.length());
    }

    // ---------------------------------------------------------------- the body

    /** Is the biped model being posed now that of a traveller lying in a pipe? */
    public static boolean swimming() {
        return swimming;
    }

    private static void turn(ModelPart part, Quaternionf bend, float pitch, float yaw, float roll) {
        PART.set(bend).rotateZYX(roll, yaw, pitch).getEulerAnglesZYX(ANGLES);
        part.pitch = ANGLES.x;
        part.yaw = ANGLES.y;
        part.roll = ANGLES.z;
    }

    /** Is {@code entity} drawn lying in a pipe by its living entity renderer now (facing the pipe's frame)? */
    public static boolean lying(Entity entity) {
        return entity == lying;
    }

    /** The head pitch of the one {@link #lying}: looking ahead. */
    public static float lyingPitch() {
        return lyingPitch;
    }

    /** No name tag inside a pipe. */
    public static boolean hidesNameTag(Entity entity) {
        return entity == drawn;
    }

    /**
     * A swimmer's pose: arms stretched ahead of the head, legs behind with a little flutter kick, looking ahead; round
     * a bend, the head and arms are turned from the neck and the shoulders the way the pipe goes ahead, the legs from
     * the hips the way it came.
     *
     * @param age the model's animation progress (age and tick delta), for the kicks
     */
    public static void swimPose(BipedEntityModel<?> model, float age) {
        float kick = MathHelper.sin(age * 0.9f) * 0.25f;
        model.body.pitch = 0;
        model.body.yaw = 0;
        turn(model.head, HEAD, -0.8f, 0, 0);
        model.hat.copyTransform(model.head);
        turn(model.rightArm, HEAD, -2.95f, 0, 0.12f);
        turn(model.leftArm, HEAD, -2.95f, 0, -0.12f);
        turn(model.rightLeg, LEGS, kick, 0, 0.04f);
        turn(model.leftLeg, LEGS, -kick, 0, -0.04f);
    }

    /** After the entity is drawn. */
    public static void end() {
        swimming = false;
        drawn = lying = null;
    }

    /** The turn of a part of the body, from the body's frame to its model's (turned half round, upside down). */
    private static void toModel(Quaternionf bend) {
        bend.y = -bend.y;
        bend.z = -bend.z;
    }

    /**
     * Before {@code renderer} draws {@code entity} (the matrices at its feet): pushes its pipe pose, or its wobble
     * once out.
     *
     * @return whether a pose was pushed (to pop after drawing)
     */
    public static boolean push(EntityRenderer<?> renderer, Entity entity, float tickDelta, MatrixStack matrices) {
        swimming = false;
        drawn = lying = null;
        PipeCarrierEntity carrier = carrier(entity);
        if (carrier == null) return !SEEN.isEmpty() && wobble(entity, tickDelta, matrices);
        float now = entity.age + tickDelta;
        float[] seen = SEEN.get(entity);
        if (seen == null) SEEN.put(entity, seen = new float[2]);
        seen[0] = now;
        seen[1] = -1;

        List<Vec3d> points = carrier.points();
        double[] lengths = carrier.lengths();
        double at = travelled(carrier, tickDelta);
        float bodyYaw = entity instanceof LivingEntity living ? MathHelper.lerpAngleDegrees(tickDelta, living.prevBodyYaw, living.bodyYaw)
                : entity.getYaw(tickDelta);
        float yaw = bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        FACING.set(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
        // Tall bodies (and items: spinning round the way) head first, the others snout first
        float width = entity.getWidth(), height = entity.getHeight();
        boolean lengthwise = !(entity instanceof ItemEntity) && PipePose.lengthwise(width, height);
        PipePose.orientation(points, lengths, at, FACING, lengthwise, ROTATION);
        float stretch = PipePose.stretch(points, lengths, at, carrier.speed());
        // A tall body lies along the run: as big as the tube allows
        float size = lengthwise || !(entity instanceof LivingEntity) ? 1 : Math.max(1, Math.min(FIT / width, LONGEST / height));
        float across = size / MathHelper.sqrt(stretch), along = size * stretch;
        float pivot = height / 2;
        // Its middle on the pipe's line at this very frame (not between the places it was at the last two ticks)
        PipePose.position(points, lengths, at, MIDDLE);

        matrices.push();
        matrices.translate(MIDDLE.x - MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()),
                MIDDLE.y - MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()),
                MIDDLE.z - MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()));
        matrices.multiply(ROTATION);
        if (lengthwise) matrices.scale(across, across, along);
        else matrices.scale(across, along, across);
        if (renderer instanceof LivingEntityRenderer<?, ?> && entity instanceof LivingEntity) {
            // Facing the pipe's frame (yaw 0), looking ahead, lying (not sitting, not crouching)
            lying = entity;
            lyingPitch = lengthwise ? 0 : HEAD_PITCH;
            if (!lengthwise) {
                // Bipeds swim, folded round the bends
                swimming = true;
                float length = height * along;
                toModel(PipePose.bend(points, lengths, at, HEAD_AT * length, FACING, HEAD));
                toModel(PipePose.bend(points, lengths, at, LEGS_AT * length, FACING, LEGS));
            }
        } else if (entity instanceof LivingEntity) {
            // Other renderers (GeckoLib) turn it by its body yaw themselves: undone, it faces the pipe's frame too
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(bodyYaw));
        }
        matrices.translate(0, -pivot, 0);
        drawn = entity;
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

    // ---------------------------------------------------------------- the traveller's own view

    /**
     * Works out the view of {@code focused} at this frame, before the camera reads its look. In a pipe (the client's
     * own player): the turn to add to its look ({@link #viewYaw}, {@link #viewPitch}), from the look it had when this
     * path began to the pipe's way, which it follows smoothly ({@link #VIEW_LAG}). Out of the pipe (or on
     * another path): the player's look takes the turn, so that the view does not jump.
     *
     * @return whether the view follows a pipe
     */
    public static boolean updateView(Entity focused, float tickDelta) {
        PipeCarrierEntity carrier = focused == MinecraftClient.getInstance().player ? carrier(focused) : null;
        List<Vec3d> path = carrier == null ? null : carrier.points();
        if (path != viewPath) {
            if (viewPath != null && MinecraftClient.getInstance().player != null) {
                Entity player = MinecraftClient.getInstance().player;
                float pitch = MathHelper.clamp(player.getPitch() + viewPitch, -90, 90);
                player.prevYaw += viewYaw;
                player.prevPitch += pitch - player.getPitch();
                player.setYaw(player.getYaw() + viewYaw);
                player.setPitch(pitch);
            }
            viewPath = path;
            viewYaw = viewPitch = 0;
            if (path != null) {
                viewFromYaw = focused.getYaw(tickDelta);
                viewFromPitch = focused.getPitch(tickDelta);
                viewTime = focused.age + tickDelta;
            }
        }
        if (carrier == null) return false;
        PipePose.heading(path, carrier.lengths(), travelled(carrier, tickDelta), viewFromYaw, HEADING);
        // Toward the pipe's way, a part of what is left each frame (by the time gone by: the same at any frame rate)
        float now = focused.age + tickDelta;
        float part = 1 - (float) Math.exp(-MathHelper.clamp(now - viewTime, 0, 2) / VIEW_LAG);
        viewTime = now;
        viewYaw += MathHelper.wrapDegrees(MathHelper.wrapDegrees(HEADING[0] - viewFromYaw) - viewYaw) * part;
        viewPitch += (HEADING[1] - viewFromPitch - viewPitch) * part;
        return true;
    }

    public static float viewYaw() {
        return viewYaw;
    }

    public static float viewPitch() {
        return viewPitch;
    }

    /** Where the camera is in a pipe: on the pipe's line, where the traveller is at this frame. */
    public static Vector3d viewPos(Entity focused, float tickDelta) {
        PipeCarrierEntity carrier = carrier(focused);
        return PipePose.position(carrier.points(), carrier.lengths(), travelled(carrier, tickDelta), MIDDLE);
    }
}
