package fr.lordfinn.steveparty.blocks.custom.pipe;

import fr.lordfinn.steveparty.utils.Easing;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;

/**
 * How a traveller lies in the pipe, from its carrier's path (the points along the middle of the pipes, and the
 * distance from the start to each): pure maths, drawn by the client.
 * <ul>
 *     <li><b>Orientation</b>: always head first along the pipe. A tall body (player, zombie, villager, item...): its
 *     up (head) along the way, its front belly down in a level pipe (swimming), and in an upright one, the side it
 *     carries over from the level pipe next to it (so that the turn between the two is a plain pitch). A long body
 *     ({@code lengthwise}: pig, cow, spider...): its front (snout) along the way, back up in a level pipe (it walks),
 *     belly toward the level pipe it comes from in an upright one. Eased through a bend, over {@link #TURN} blocks
 *     before and after it; its middle glides round the corner ({@link #position}), and the parts of a long body are
 *     each turned the way of the pipe where they are ({@link #bend}): it folds round the corner.</li>
 *     <li><b>Squash and stretch</b> ({@link #stretch}): along the way; longer the faster it goes, flattened as it goes
 *     in, squashed into each bend then springing back, stretched as it pops out, a jelly wobble once out
 *     ({@link #exitWobble}).</li>
 * </ul>
 * Render thread only for the methods writing into the scratch values: no allocation per frame.
 */
public final class PipePose {
    /** Half length of the eased turn through a bend (blocks), shortened on short segments. */
    public static final double TURN = 0.45;
    /** Most stretch along the way on a straight run (at {@link PipeTravel#MAX_SPEED}). */
    public static final float RUN = 0.3f;
    /** How flat it goes in, how much a bend squashes it, how long it pops out. */
    public static final float ENTRY = 0.45f, BEND = 0.3f, EXIT = 0.35f;

    private static final Vector3f DIR = new Vector3f(), OTHER = new Vector3f(), FRONT = new Vector3f(), RIGHT = new Vector3f();
    private static final Matrix3f FRAME = new Matrix3f();
    private static final Quaternionf A = new Quaternionf(), B = new Quaternionf(), C = new Quaternionf();

    private PipePose() {}

    /** Distance from the start of the path to each point. */
    public static double[] lengths(List<Vec3d> points) {
        double[] lengths = new double[points.size()];
        for (int i = 1; i < points.size(); i++) lengths[i] = lengths[i - 1] + points.get(i).distanceTo(points.get(i - 1));
        return lengths;
    }

    /** The segment (from point i to i + 1) the distance {@code at} is on. */
    public static int segment(double[] lengths, double at) {
        int last = lengths.length - 2;
        for (int i = 0; i < last; i++) if (at < lengths[i + 1]) return i;
        return Math.max(last, 0);
    }

    /**
     * The way of segment {@code seg} (unit vector), the nearest non empty one after it, else before; false if the
     * path has no length.
     */
    public static boolean direction(List<Vec3d> points, int seg, Vector3f out) {
        int n = points.size() - 1;
        for (int k = 0; k < 2 * n; k++) {
            int i = k < n ? seg + k : seg - (k - n) - 1;
            if (i < 0 || i >= n) continue;
            Vec3d a = points.get(i), b = points.get(i + 1);
            float x = (float) (b.x - a.x), y = (float) (b.y - a.y), z = (float) (b.z - a.z);
            float length = MathHelper.sqrt(x * x + y * y + z * z);
            if (length > 1.0E-4f) {
                out.set(x / length, y / length, z / length);
                return true;
            }
        }
        return false;
    }

    private static boolean level(Vector3f dir) {
        return Math.abs(dir.y) < 0.7f;
    }

    /**
     * The orientation of a traveller on segment {@code seg}: turns the body's up (+Y) to the way and its front (+Z)
     * to the side it faces. {@code facing}: its front when nothing else says (an upright path without any level
     * pipe), horizontal.
     */
    public static Quaternionf frame(List<Vec3d> points, int seg, Vector3f facing, Quaternionf out) {
        if (!direction(points, seg, DIR)) return out.identity();
        if (level(DIR)) {
            FRONT.set(0, -1, 0);
        } else {
            // Upright: the level way next to it, before (where it comes from) else after; heading up it faces that
            // way, heading down the other one (both turn into belly down by a plain pitch)
            boolean found = false;
            for (int i = seg - 1; i >= 0 && !found; i--) found = direction(points, i, OTHER) && level(OTHER);
            for (int i = seg + 1; i < points.size() - 1 && !found; i++) found = direction(points, i, OTHER) && level(OTHER);
            if (!found) OTHER.set(facing);
            FRONT.set(OTHER.x, 0, OTHER.z);
            if (FRONT.lengthSquared() < 1.0E-6f) FRONT.set(0, 0, 1);
            if (DIR.y < 0) FRONT.negate();
        }
        // Front square to the way
        FRONT.fma(-FRONT.dot(DIR), DIR).normalize();
        DIR.cross(FRONT, RIGHT);
        FRAME.set(RIGHT, DIR, FRONT);
        return out.setFromNormalized(FRAME);
    }

    private static double segmentLength(double[] lengths, int seg) {
        return seg < 0 || seg >= lengths.length - 1 ? 0 : lengths[seg + 1] - lengths[seg];
    }

    /** Half length of the eased turn at point {@code corner}. */
    private static double turn(double[] lengths, int corner) {
        return Math.min(TURN, 0.5 * Math.min(segmentLength(lengths, corner - 1), segmentLength(lengths, corner)));
    }

    /** Smoothstep. */
    public static float ease(double t) {
        float x = (float) MathHelper.clamp(t, 0, 1);
        return Easing.smoothstep(x);
    }

    /** Is a body of this size long (on four legs: snout first) rather than tall (head first)? */
    public static boolean lengthwise(float width, float height) {
        return height < 1.6f * width;
    }

    /**
     * The orientation {@code at} blocks along the path: the segment's one, eased into the next one round a bend; turned
     * to put the front (+Z) along the way for a {@code lengthwise} body.
     */
    public static Quaternionf orientation(List<Vec3d> points, double[] lengths, double at, Vector3f facing, boolean lengthwise, Quaternionf out) {
        orientation(points, lengths, at, facing, out);
        return lengthwise ? out.rotateX(-MathHelper.HALF_PI) : out;
    }

    /** The turn {@code at} is in: from segment {@link #turnFrom} to the next one, {@link #turnPart} done (0 to 1). */
    private static int turnFrom;
    private static double turnPart;

    /** Is {@code at} in the turn round a corner? Fills {@link #turnFrom} and {@link #turnPart}, else the segment in {@link #turnFrom}. */
    private static boolean turning(double[] lengths, double at) {
        int seg = segment(lengths, at);
        int last = lengths.length - 2;
        double w;
        turnFrom = seg;
        if (seg < last && (w = turn(lengths, seg + 1)) > 1.0E-3 && at > lengths[seg + 1] - w) {
            turnPart = MathHelper.clamp((at - (lengths[seg + 1] - w)) / (2 * w), 0, 1);
            return true;
        }
        if (seg > 0 && (w = turn(lengths, seg)) > 1.0E-3 && at < lengths[seg] + w) {
            turnFrom = seg - 1;
            turnPart = MathHelper.clamp((at - (lengths[seg] - w)) / (2 * w), 0, 1);
            return true;
        }
        return false;
    }

    private static Quaternionf orientation(List<Vec3d> points, double[] lengths, double at, Vector3f facing, Quaternionf out) {
        if (points.size() < 2) return out.identity();
        if (!turning(lengths, at)) return frame(points, turnFrom, facing, out);
        int from = turnFrom;
        float part = ease(turnPart);
        frame(points, from, facing, A);
        frame(points, from + 1, facing, B);
        return A.slerp(B, part, out);
    }

    /**
     * How a part of the body {@code offset} blocks ahead of its middle (behind: negative) is turned from the middle's
     * orientation, in the body's own frame: nothing on a straight run; round a bend, the head is already turned the
     * new way while the legs still lie the old one, so the body folds round the corner.
     */
    public static Quaternionf bend(List<Vec3d> points, double[] lengths, double at, double offset, Vector3f facing, Quaternionf out) {
        orientation(points, lengths, at, facing, C);
        orientation(points, lengths, at + offset, facing, out);
        return C.conjugate().mul(out, out);
    }

    /**
     * Where the middle of the traveller is {@code at} blocks along the path: on the line through the middle of the
     * pipes, the corners rounded (a curve from where the turn starts to where it ends), so that it glides round the
     * bends without a kink.
     */
    public static Vector3d position(List<Vec3d> points, double[] lengths, double at, Vector3d out) {
        if (points.isEmpty()) return out.zero();
        if (points.size() < 2) return out.set(points.getFirst().x, points.getFirst().y, points.getFirst().z);
        at = MathHelper.clamp(at, 0, lengths[lengths.length - 1]);
        if (turning(lengths, at)) {
            int corner = turnFrom + 1;
            double w = turn(lengths, corner), t = turnPart;
            Vec3d c = points.get(corner), a = points.get(corner - 1), b = points.get(corner + 1);
            double ka = w / segmentLength(lengths, corner - 1), kb = w / segmentLength(lengths, corner);
            double u = (1 - t) * (1 - t), v = t * t, m = 1 - u - v;
            // From w before the corner (weight u) through the corner (m) to w after it (v)
            return out.set(
                    u * (c.x + (a.x - c.x) * ka) + m * c.x + v * (c.x + (b.x - c.x) * kb),
                    u * (c.y + (a.y - c.y) * ka) + m * c.y + v * (c.y + (b.y - c.y) * kb),
                    u * (c.z + (a.z - c.z) * ka) + m * c.z + v * (c.z + (b.z - c.z) * kb));
        }
        int seg = turnFrom;
        double length = segmentLength(lengths, seg);
        double t = length <= 1.0E-6 ? 1 : (at - lengths[seg]) / length;
        Vec3d a = points.get(seg), b = points.get(seg + 1);
        return out.set(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }

    /** Pitch of the view going up or down a pipe (degrees): mostly along it, never straight up or down. */
    public static final float VIEW_PITCH = 50;

    private static float segmentYaw(List<Vec3d> points, int seg, float fallbackYaw) {
        boolean found = direction(points, seg, DIR) && level(DIR);
        for (int i = seg - 1; i >= 0 && !found; i--) found = direction(points, i, DIR) && level(DIR);
        for (int i = seg + 1; i < points.size() - 1 && !found; i++) found = direction(points, i, DIR) && level(DIR);
        return found ? (float) (MathHelper.atan2(-DIR.x, DIR.z) * MathHelper.DEGREES_PER_RADIAN) : fallbackYaw;
    }

    private static float segmentPitch(List<Vec3d> points, int seg) {
        if (!direction(points, seg, DIR) || level(DIR)) return 0;
        return DIR.y > 0 ? -VIEW_PITCH : VIEW_PITCH;
    }

    /**
     * Where a traveller looks {@code at} blocks along the path, following the pipe: {@code out[0]} its yaw (the way
     * of a level pipe; up or down a pipe, that of the level pipe before it, else after it, else {@code fallbackYaw}),
     * {@code out[1]} its pitch (level, or {@link #VIEW_PITCH} up or down); eased round the bends like the body.
     */
    public static float[] heading(List<Vec3d> points, double[] lengths, double at, float fallbackYaw, float[] out) {
        out[0] = fallbackYaw;
        out[1] = 0;
        if (points.size() < 2) return out;
        boolean turning = turning(lengths, at);
        int from = turnFrom;
        float part = ease(turnPart);
        out[0] = segmentYaw(points, from, fallbackYaw);
        out[1] = segmentPitch(points, from);
        if (turning) {
            out[0] = MathHelper.lerpAngleDegrees(part, out[0], segmentYaw(points, from + 1, fallbackYaw));
            out[1] = MathHelper.lerp(part, out[1], segmentPitch(points, from + 1));
        }
        return out;
    }

    /** A squash then a damped spring back ({@code ticks} after the hit), from -1. */
    static float spring(float ticks, float frequency, float damping) {
        return (float) (-Math.cos(ticks * frequency) * Math.exp(-ticks * damping));
    }

    /**
     * How long the traveller is along the way, {@code at} blocks along the path at {@code speed} blocks per tick
     * (1: its size; across, it gets {@code 1 / sqrt} of it, keeping its volume).
     */
    public static float stretch(List<Vec3d> points, double[] lengths, double at, double speed) {
        if (points.size() < 2 || speed <= 1.0E-4) return 1;
        float since = (float) (at / speed), left = (float) ((lengths[lengths.length - 1] - at) / speed);
        // The faster, the longer; it builds up once in
        float stretch = (float) (RUN * MathHelper.clamp(speed / PipeTravel.MAX_SPEED * 1.6, 0.25, 1)) * (1 - (float) Math.exp(-since * 0.5f));
        // Flattened into the mouth, springing back
        if (since < 14) stretch += ENTRY * spring(since, 0.8f, 0.32f);
        // Squashed into each bend, springing back
        for (int corner = 1; corner < points.size() - 1; corner++) {
            float ticks = (float) ((at - lengths[corner]) / speed);
            if (ticks < -2.5f || ticks > 12 || !bend(points, corner)) continue;
            stretch += ticks < 0 ? -BEND * (1 + ticks / 2.5f) * (1 + ticks / 2.5f) : BEND * spring(ticks, 0.9f, 0.3f);
        }
        // Popping out
        if (left < 3) stretch += EXIT * (1 - left / 3) * (1 - left / 3);
        return MathHelper.clamp(1 + stretch, 0.5f, 1.7f);
    }

    /** Does the way turn at point {@code corner}? */
    public static boolean bend(List<Vec3d> points, int corner) {
        return direction(points, corner - 1, DIR) && direction(points, corner, OTHER) && DIR.dot(OTHER) < 0.9f;
    }

    /** How tall it is {@code ticks} after popping out (1: its size): still stretched, a squash, settling. */
    public static float exitWobble(float ticks) {
        if (ticks < 0 || ticks > 14) return 1;
        return 1 + 0.25f * (float) (Math.cos(ticks * 0.9f) * Math.exp(-ticks * 0.3f));
    }
}
