package fr.lordfinn.steveparty.telescope;

import net.minecraft.util.math.MathHelper;

/**
 * The numbers of the Telescope and its game "Replay the night", shared by both sides and free of any game state: when
 * the sky can be watched, the way the replayed shooting stars cross the sky, the tracking gauge, and how a guide star
 * tells its distance.
 */
public final class TelescopeMath {
    private TelescopeMath() {
    }

    // ---------------------------------------------------------------- the night

    /** The sky can be watched between these times of day (the stars are out). */
    public static final long NIGHT_START = 13000, NIGHT_END = 23000;

    public static boolean isNightTime(long timeOfDay) {
        long t = Math.floorMod(timeOfDay, 24000L);
        return t >= NIGHT_START && t < NIGHT_END;
    }

    // ---------------------------------------------------------------- the replayed stars

    /** Stars of a replayed night, the ticks one takes to cross the sky, and the ticks after which the night loops. */
    public static final int STARS = 5, FLIGHT_TICKS = 420, LOOP_TICKS = 480;
    /** Their straight way over the watcher: this long (blocks), centred over him. */
    public static final double PATH_LENGTH = 480;

    /** A number in [0, 1) from a site, a star and a salt: the same on every client, every time. */
    public static double roll(int siteId, int star, int salt) {
        long h = siteId * 0x9E3779B97F4A7C15L + star * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 11) / (double) (1L << 53);
    }

    /** How far to the side of the watcher this star passes (blocks, either side). */
    public static double side(int siteId, int star) {
        double r = roll(siteId, star, 1) * 2 - 1;
        // never right overhead: the zenith is where a star goes fastest
        return Math.signum(r == 0 ? 1 : r) * (25 + Math.abs(r) * 60);
    }

    /** How high this star passes (blocks). */
    public static double height(int siteId, int star) {
        return 80 + roll(siteId, star, 2) * 30;
    }

    /** 0..1 along its flight at this tick of the replay, or -1 while it is not in the sky. */
    public static double progress(int star, double ticks) {
        double t = (ticks - star * (double) LOOP_TICKS / STARS) % LOOP_TICKS;
        if (t < 0) t += LOOP_TICKS;
        return t <= FLIGHT_TICKS ? t / FLIGHT_TICKS : -1;
    }

    /**
     * Where a replayed star is in the sky, seen from the watcher, as a unit vector into {@code out} (x, y, z): it
     * flies level towards the site ({@code dirX}, {@code dirZ}: unit), so it rises behind the watcher, passes over
     * him and sinks towards where the Mulas came down.
     */
    public static void direction(double dirX, double dirZ, double side, double height, double progress, double[] out) {
        double along = (progress - 0.5) * PATH_LENGTH;
        double x = dirX * along - dirZ * side, z = dirZ * along + dirX * side;
        double length = Math.sqrt(x * x + height * height + z * z);
        out[0] = x / length;
        out[1] = height / length;
        out[2] = z / length;
    }

    /** How bright it is along its flight: lighting up at the start, fading out before it vanishes. */
    public static float fade(double progress) {
        if (progress < 0) return 0;
        return (float) (Math.min(1, progress * 10) * Math.min(1, (1 - progress) * 8));
    }

    /** Angle (degrees) between two unit vectors. */
    public static double angle(double ax, double ay, double az, double bx, double by, double bz) {
        double dot = MathHelper.clamp(ax * bx + ay * by + az * bz, -1, 1);
        return Math.toDegrees(Math.acos(dot));
    }

    // ---------------------------------------------------------------- the watcher at the eyepiece

    /** The tube turns about a point this high over the block's floor; its eyepiece ends this far behind it (blocks). */
    public static final double PIVOT_HEIGHT = 31 / 16.0, EYEPIECE_BACK = 0.6;
    /**
     * The player model: its neck this high when standing, this much lower fully bent (as when sneaking), the head half
     * this size, and the eye this far from the eyepiece.
     */
    public static final double NECK_HEIGHT = 1.406, BEND_DROP = 0.371, HEAD = 0.234, EYE_GAP = 0.03;
    /** A watcher stays within this distance of the telescope (blocks, horizontally from its centre). */
    public static final double WATCH_DISTANCE = 4.5;

    /**
     * Where the neck of a player looking through the tube is, relative to the tube's pivot, into {@code out}
     * (x, y, z): his eye against the eyepiece, his head tilted like the tube.
     *
     * @param yaw   where the tube points (degrees, as a player's yaw)
     * @param pitch its tilt (degrees, as a player's pitch: negative is up)
     */
    public static void neck(double yaw, double pitch, double[] out) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        double fx = -Math.sin(y), fz = Math.cos(y);
        double level = Math.cos(p), rise = -Math.sin(p);
        // along the tube (behind the eyepiece, then half a head), then half a head down the tilted head
        double along = -(EYEPIECE_BACK + EYE_GAP + HEAD);
        double flat = along * level + HEAD * rise;
        out[0] = fx * flat;
        out[1] = along * rise - HEAD * level;
        out[2] = fz * flat;
    }

    /** How much a player bends (0 standing .. 1 as low as sneaking) for his neck to be this high over his feet. */
    public static double bend(double neckHeight) {
        return MathHelper.clamp((NECK_HEIGHT - neckHeight) / BEND_DROP, 0, 1);
    }

    // ---------------------------------------------------------------- the tracking gauge

    /** The reticle tracks a star within this angle (degrees); within the smaller one it is well centred. */
    public static final double TRACK_DEGREES = 7, CENTRE_DEGREES = 2.5;
    /** Ticks to fill the gauge well centred, at the edge of the tracking angle, and to empty it off any star. */
    public static final int FILL_CENTRED_TICKS = 80, FILL_EDGE_TICKS = 200, DRAIN_TICKS = 400;

    /**
     * The gauge (0..1) one tick later.
     *
     * @param error angle between the reticle and the nearest star in the sky (degrees); negative: no star
     */
    public static float step(float gauge, double error) {
        float next;
        if (error < 0 || Double.isNaN(error) || error > TRACK_DEGREES) {
            next = gauge - 1f / DRAIN_TICKS;
        } else {
            double off = Math.max(0, (error - CENTRE_DEGREES) / (TRACK_DEGREES - CENTRE_DEGREES));
            double ticks = FILL_CENTRED_TICKS + (FILL_EDGE_TICKS - FILL_CENTRED_TICKS) * off;
            next = gauge + (float) (1 / ticks);
        }
        return MathHelper.clamp(next, 0f, 1f);
    }

    public static boolean tracking(double error) {
        return error >= 0 && error <= TRACK_DEGREES;
    }

    // ---------------------------------------------------------------- the guide star

    /** Up to this distance (blocks) the Mulas are "near", then "far", then "very far". */
    public static final double NEAR = 150, FAR = 450;

    /** 0 near, 1 far, 2 very far. */
    public static int band(double distance) {
        return distance < NEAR ? 0 : distance < FAR ? 1 : 2;
    }

    /** How high the guide star stands (degrees over the horizon): low when far away, nearly overhead when there. */
    public static double guideElevation(double distance) {
        return 16 + 64 * Math.exp(-Math.max(0, distance) / 200);
    }

    /** How big and bright it is (0.5 far away .. 1 there). */
    public static float guideBrightness(double distance) {
        return (float) (0.5 + 0.5 * Math.exp(-Math.max(0, distance) / 300));
    }

    /** The compass point of a horizontal offset: 0 north (-z), 1 north-east, 2 east (+x) ... 7 north-west. */
    public static int cardinal(double dx, double dz) {
        double turns = Math.atan2(dx, -dz) / (Math.PI / 4);
        return Math.floorMod((int) Math.round(turns), 8);
    }

    public static final String[] CARDINAL_KEYS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
    public static final String[] BAND_KEYS = {"near", "far", "very_far"};
}
