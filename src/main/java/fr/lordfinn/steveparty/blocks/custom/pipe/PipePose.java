package fr.lordfinn.steveparty.blocks.custom.pipe;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
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
 *     before and after it.</li>
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
    private static final Quaternionf A = new Quaternionf(), B = new Quaternionf();

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
    static float ease(double t) {
        float x = (float) MathHelper.clamp(t, 0, 1);
        return x * x * (3 - 2 * x);
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

    private static Quaternionf orientation(List<Vec3d> points, double[] lengths, double at, Vector3f facing, Quaternionf out) {
        if (points.size() < 2) return out.identity();
        int seg = segment(lengths, at);
        int last = points.size() - 2;
        double w;
        if (seg < last && (w = turn(lengths, seg + 1)) > 1.0E-3 && at > lengths[seg + 1] - w) {
            frame(points, seg, facing, A);
            frame(points, seg + 1, facing, B);
            return A.slerp(B, ease((at - (lengths[seg + 1] - w)) / (2 * w)), out);
        }
        if (seg > 0 && (w = turn(lengths, seg)) > 1.0E-3 && at < lengths[seg] + w) {
            frame(points, seg - 1, facing, A);
            frame(points, seg, facing, B);
            return A.slerp(B, ease((at - (lengths[seg] - w)) / (2 * w)), out);
        }
        return frame(points, seg, facing, out);
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
