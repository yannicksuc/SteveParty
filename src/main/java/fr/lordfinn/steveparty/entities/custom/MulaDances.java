package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.utils.Easing;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * The ten dances of the Mulas around a Dice Forge: pure formulas of the time, the dancer's slot and the number of
 * dancers, so the server and every client compute exactly the same positions from a few synced numbers (which dance,
 * slot, count) and the world time: no pathfinding, no position packets while dancing.
 * <p>
 * A forge plays its dances in turn, one every {@value #DANCE_TICKS} ticks, starting from one given by its position
 * (neighbouring forges don't dance in sync). A change of dance (or of slot / count, someone joining) is blended over
 * {@value #BLEND_TICKS} ticks from the old figure to the new one.
 * <p>
 * The figures grow with the number of dancers so neighbours stay about a block apart. Up to {@value #SINGLE} dancers
 * share one figure; more form rings round it (up to {@value #MAX_RINGS}, see {@link #ringSize}), each clear of the one
 * inside it by {@value #RING_GAP} block, interleaved with it and turning the other way (each dance its own way, see
 * {@link #offset}). Up to {@value #CAP} Mulas dance at once; beyond that they take turns at each new dance
 * ({@link #turnStart}) and the others watch from {@link #spectatorSpot}.
 */
public final class MulaDances {
    private MulaDances() {
    }

    public static final int COUNT = 10;
    public static final int DANCE_TICKS = 520;
    public static final int BLEND_TICKS = 60;
    /** Within this distance of the core's axis (blocks) a dancer leaps up, to about this height above the core. */
    public static final double HOP_RADIUS = 1.4, HOP_HEIGHT = 1.6;
    /** Most dancers on one figure; more make rings, at most this many. */
    public static final int SINGLE = 12, MAX_RINGS = 3;
    /** Most Mulas dancing round a forge at once: three rings of 8, 10 and 14. The others take turns. */
    public static final int CAP = 32;
    /** Blocks between neighbours on a circle, and clear between two rings. */
    public static final double SPACING = 1.0, RING_GAP = 1.0;
    /** Share of the dancers on each ring when there are several (the outer rings are longer). */
    private static final int[] RING_WEIGHT = {10, 13, 16};
    /** Half the width of the band each outer ring weaves in, per dance (Sunflower Bloom has no rings). */
    private static final double[] BAND = {0.25, 0.7, 0, 0.55, 0.5, 0.75, 0, 0.55, 0.5, 0.65};

    /** Id of each dance; its name is the lang key {@code mula.dance.<id>} (see {@link #name}). */
    public static final String[] IDS = {
            "carousel_of_stars", "rose_window", "infinity", "lissajous_lace", "starlit_staircase",
            "planets_and_moons", "sunflower_bloom", "braided_comet", "moonlight_waltz", "heartbeat_nova"};

    /** The translated name of a dance, e.g. "Carousel of Stars". */
    public static Text name(int dance) {
        return Text.translatable("mula.dance." + IDS[Math.floorMod(dance, COUNT)]);
    }

    /**
     * Expression of each dance (the looping animation the dancers play): 0 twirl (arms up, happy squint, spins),
     * 1 sway (arms waving, head tilting, blissful closed eyes), 2 hold (arms stretched out to hold hands, bright eyes).
     */
    public static final int[] STYLE = {0, 1, 1, 0, 2, 0, 1, 2, 2, 0};

    /** The dance a forge plays at this time. */
    public static int danceAt(long worldTime, BlockPos forge) {
        long period = Math.floorDiv(worldTime, DANCE_TICKS);
        int seed = Math.floorMod(forge.getX() * 31 + forge.getZ() * 17 + forge.getY(), COUNT);
        return (int) Math.floorMod(period + seed, COUNT);
    }

    /** When the dance playing at this time started. */
    public static long danceStart(long worldTime) {
        return Math.floorDiv(worldTime, DANCE_TICKS) * DANCE_TICKS;
    }

    // ------------------------------------------------------------------------------------------ turns and rings

    /**
     * More than {@link #CAP} Mulas: the ones dancing this time are {@link #CAP} in a row (in the order of their ids,
     * going round) from this one; the window moves on by {@link #CAP} at each new dance, so the ones resting now dance
     * next time and everyone dances as often over {@code eligible / gcd(eligible, CAP)} dances.
     */
    public static int turnStart(long worldTime, int eligible) {
        if (eligible <= CAP) return 0;
        return (int) Math.floorMod(Math.floorDiv(worldTime, DANCE_TICKS) * CAP, (long) eligible);
    }

    /** How many rings {@code count} dancers make. */
    public static int rings(int count) {
        return MathHelper.clamp((count + SINGLE - 1) / SINGLE, 1, MAX_RINGS);
    }

    /** How many of {@code count} dancers are on ring {@code ring} (0 = the figure itself), shared by ring length. */
    public static int ringSize(int count, int ring) {
        int k = rings(count);
        if (ring < 0 || ring >= k) return 0;
        int total = 0, sum = 0;
        for (int i = 0; i < k; i++) total += RING_WEIGHT[i];
        for (int i = 0; i < k; i++) sum += count * RING_WEIGHT[i] / total;
        // the few left over go to the outer rings first
        return count * RING_WEIGHT[ring] / total + (k - 1 - ring < count - sum ? 1 : 0);
    }

    /** The ring of dancer {@code slot} of {@code count}. */
    public static int ringOf(int slot, int count) {
        int k = rings(count);
        for (int ring = 0; ring < k - 1; ring++) {
            int size = ringSize(count, ring);
            if (slot < size) return ring;
            slot -= size;
        }
        return k - 1;
    }

    /** Radius of a circle on which {@code m} dancers are {@value #SPACING} block apart. */
    private static double fit(int m) {
        return SPACING * m / MathHelper.TAU;
    }

    private static double lemniscate(int m) {
        return Math.max(3.0, 0.4 * m);
    }

    private static double lissajous(int m) {
        return Math.max(2.4, 0.3 * m);
    }

    private static double planets(int m) {
        return Math.max(2.5, 0.75 + 0.95 / (2 * Math.sin(Math.PI / Math.max(2, m))));
    }

    private static double waltz(int m) {
        return Math.max(2.6, 1.95 / (2 * Math.sin(Math.PI / Math.max(2, (m + 1) / 2))));
    }

    /** How far the inner figure reaches from the core's axis with {@code m} dancers on it. */
    private static double innerExtent(int dance, int m) {
        return switch (dance) {
            case 0 -> Math.max(2.6, fit(m) + 0.25) + 0.25;
            case 1 -> Math.max(0.9, fit(m)) + 2.1;
            case 2 -> lemniscate(m);
            case 3 -> lissajous(m) * Math.sqrt(2);
            case 4 -> Math.max(2.0, fit(m) + 0.5) + 0.5;
            case 5 -> planets(m) + 0.75;
            case 7 -> Math.max(2.4, fit(m) + 0.55) + 0.55;
            case 8 -> waltz(m) + 0.5;
            default -> Math.max(1.6, fit(m)) + 1.3;
        };
    }

    /** Centre radius of ring {@code ring} >= 1 of {@code count} dancers: its band clears the ring inside it. */
    private static double ringCentre(int dance, int count, int ring) {
        double reach = innerExtent(dance, ringSize(count, 0)), half = BAND[dance], centre = 0;
        for (int j = 1; j <= ring; j++) {
            centre = Math.max(reach + RING_GAP + half, fit(ringSize(count, j)) + half);
            reach = centre + half;
        }
        return centre;
    }

    /**
     * Infinity's rings are Cassini ovals round the lemniscate (half-width a, same foci): b squared of ring
     * {@code ring} >= 1, clear of the one inside at its tips and its waist.
     */
    private static double ovalB2(double a, int ring) {
        double c2 = a * a / 2, tip = a, waist = 0, b2 = 0;
        for (int j = 1; j <= ring; j++) {
            b2 = Math.max((tip + RING_GAP) * (tip + RING_GAP) - c2, (waist + RING_GAP) * (waist + RING_GAP) + c2);
            tip = Math.sqrt(c2 + b2);
            waist = Math.sqrt(b2 - c2);
        }
        return b2;
    }

    /** How far from the core's axis {@code count} dancers reach in this dance (blocks, horizontally). */
    public static double extent(int dance, int count) {
        dance = Math.floorMod(dance, COUNT);
        int n = Math.max(1, count);
        if (dance == 6) return 0.8 + 0.95 * Math.sqrt(n);
        int outer = rings(n) - 1;
        if (outer == 0) return innerExtent(dance, ringSize(n, 0));
        if (dance == 2) {
            double a = lemniscate(ringSize(n, 0));
            return Math.sqrt(a * a / 2 + ovalB2(a, outer));
        }
        return ringCentre(dance, n, outer) + BAND[dance];
    }

    // ------------------------------------------------------------------------------------------ figures

    /**
     * Offset from the forge's centre (blocks) of dancer {@code slot} of {@code count} in dance {@code dance}, at
     * {@code t} seconds into it; written into out[0..2] (x, y, z), out[3] = yaw the dancer faces (radians, or NaN to
     * face where it goes).
     * <p>
     * The first {@link #ringSize ringSize(count, 0)} slots dance the figure itself, the next ones the rings round it:
     * <ul>
     * <li>Carousel of Stars: wider carousels turning the other way, their waves in counter-time.</li>
     * <li>Rose Window: crowns of 5 then 7 petals, turning the other way.</li>
     * <li>Infinity: Cassini ovals round the lemniscate (the same foci), turning the other way.</li>
     * <li>Lissajous Lace: lace crowns of 5 loops, rising and dipping in counter-time.</li>
     * <li>Starlit Staircase: a double helix, the outer one coming down while the inner one climbs.</li>
     * <li>Planets and Moons: wider, slower orbits the other way, their moons looping the other way too.</li>
     * <li>Sunflower Bloom: no rings, the flower just has more seeds (golden angle).</li>
     * <li>Braided Comet: a second braid round the first, turning the other way, strands crossing in counter-time.</li>
     * <li>Moonlight Waltz: an outer circle of couples waltzing the other way, like a ballroom.</li>
     * <li>Heartbeat Nova: rings breathing a little after the inner one, a ripple running outwards.</li>
     * </ul>
     */
    public static void offset(int dance, int slot, int count, double t, double[] out) {
        offset(dance, slot, count, t, out, 0);
    }

    private static void offset(int dance, int slot, int count, double t, double[] out, int o) {
        dance = Math.floorMod(dance, COUNT);
        int n = Math.max(1, count);
        double x, y, z, face = Double.NaN;
        if (dance == 6) { // Sunflower Bloom: phyllotaxis (golden angle), the flower breathing and turning
            double golden = 2.39996323;
            double a = golden * (slot + 1) + 0.35 * t * 3.0 / (0.8 + 0.95 * Math.sqrt(n));
            double r = (0.8 + 0.95 * Math.sqrt(slot + 1)) * (0.88 + 0.12 * Math.sin(1.2 * t));
            hop(r * Math.cos(a), 0.25 * slot / n + 0.3 * Math.sin(1.2 * t + slot), r * Math.sin(a), face, out, o);
            return;
        }
        int ring = ringOf(slot, n), i = slot;
        for (int j = 0; j < ring; j++) i -= ringSize(n, j);
        int m = ringSize(n, ring);
        boolean outer = ring > 0;
        double phase = MathHelper.TAU * i / m + (outer ? Math.PI / m : 0); // interleaved with the ring inside
        double sgn = (ring & 1) == 1 ? -1 : 1; // every other ring turns the other way
        double counter = Math.PI * ring; // ...and moves in counter-time
        switch (dance) {
            case 0 -> { // Carousel of Stars: a synchronized orbit, riding waves like a merry-go-round
                double rc = outer ? ringCentre(dance, n, ring) : Math.max(2.6, fit(m) + 0.25);
                double a = sgn * 2.34 / rc * t + phase;
                double r = rc + 0.25 * Math.sin(3 * a);
                x = r * Math.cos(a);
                z = r * Math.sin(a);
                y = 0.55 * Math.sin(2 * a + 1.8 * t + counter);
            }
            case 1 -> { // Rose Window: each draws the petals of a rose, r = cos(3 theta), never through the core
                double a, r;
                if (!outer) {
                    double rmin = Math.max(0.9, fit(m));
                    a = 1.65 / (rmin + 2.1) * t + phase;
                    r = rmin + 2.1 * (0.5 + 0.5 * Math.cos(3 * a));
                    y = 0.35 * Math.sin(3 * a) + 0.2;
                } else {
                    double rc = ringCentre(dance, n, ring);
                    int petals = 3 + 2 * ring;
                    a = sgn * 1.65 / (rc + 0.7) * t + phase;
                    r = rc + 0.7 * Math.cos(petals * a);
                    y = 0.35 * Math.sin(petals * a) + 0.2;
                }
                x = r * Math.cos(a);
                z = r * Math.sin(a);
            }
            case 2 -> { // Infinity: a lemniscate of Bernoulli, slowly turning, dancers chasing along it
                double a = lemniscate(ringSize(n, 0));
                double lx, lz;
                if (!outer) {
                    double s = 1.1 * 3.0 / a * t + phase;
                    double d = 1 + Math.sin(s) * Math.sin(s);
                    lx = a * Math.cos(s) / d;
                    lz = a * Math.sin(s) * Math.cos(s) / d;
                    y = Math.sin(s); // twisted: the two passes through the middle cross a block apart
                } else {
                    double b2 = ovalB2(a, ring), c2 = a * a / 2;
                    double th = sgn * 2.75 / Math.sqrt(c2 + b2) * t + phase;
                    double s2 = Math.sin(2 * th);
                    double r = Math.sqrt(c2 * Math.cos(2 * th) + Math.sqrt(b2 * b2 - c2 * c2 * s2 * s2));
                    lx = r * Math.cos(th);
                    lz = r * Math.sin(th);
                    y = 0.45 * Math.sin(2 * th + counter);
                }
                double turn = 0.45 / a * t;
                x = lx * Math.cos(turn) - lz * Math.sin(turn);
                z = lx * Math.sin(turn) + lz * Math.cos(turn);
            }
            case 3 -> { // Lissajous Lace: 3:2 lace in the air, each dancer a phase along it
                if (!outer) {
                    double a = lissajous(m);
                    double u = 0.84 / a * t + phase;
                    x = a * Math.sin(3 * u);
                    z = a * Math.sin(2 * u + 0.2);
                    y = Math.cos(u); // where the lace crosses itself, the two passes are a block apart
                } else {
                    double rc = ringCentre(dance, n, ring);
                    double a = sgn * 2.2 / rc * t + phase;
                    double r = rc + 0.55 * Math.sin(5 * a);
                    x = r * Math.cos(a);
                    z = r * Math.sin(a);
                    y = 0.5 * Math.sin(3 * a + counter);
                }
            }
            case 4 -> { // Starlit Staircase: a helix climbing and coming down again, dancers as its steps
                double rc = outer ? ringCentre(dance, n, ring) : Math.max(2.0, fit(m) + 0.5);
                double a = sgn * 2.4 / rc * t + phase;
                double climb = 0.5 - sgn * 0.5 * Math.cos(0.5 * t);
                double r = rc + 0.5 * Math.cos(2 * a);
                x = r * Math.cos(a);
                z = r * Math.sin(a);
                y = -0.6 + 2.6 * climb + 0.25 * i / (double) m;
            }
            case 5 -> { // Planets and Moons: epicycles, a big slow circle and a quick small loop on it
                double rc = outer ? ringCentre(dance, n, ring) : planets(m);
                double a = sgn * 1.25 / rc * t + phase;
                double b = -sgn * 3.5 * t + phase;
                x = rc * Math.cos(a) + 0.75 * Math.cos(b);
                z = rc * Math.sin(a) + 0.75 * Math.sin(b);
                y = 0.3 * Math.sin(b);
            }
            case 7 -> { // Braided Comet: three strands weaving over and under each other
                double rc = outer ? ringCentre(dance, n, ring) : Math.max(2.4, fit(m) + 0.55);
                double a = sgn * 2.4 / rc * t + phase;
                double r = rc + 0.55 * Math.sin(3 * a + phase + counter);
                x = r * Math.cos(a);
                z = r * Math.sin(a);
                y = 0.8 * Math.sin(2 * a + 2 * phase + counter);
            }
            case 8 -> { // Moonlight Waltz: pairs holding hands, turning round each other while they circle the core
                int pair = i / 2, pairs = (m + 1) / 2;
                boolean second = (i & 1) == 1;
                double rc = outer ? ringCentre(dance, n, ring) : waltz(m);
                double around = sgn * 1.17 / rc * t + MathHelper.TAU * pair / pairs + (outer ? Math.PI / pairs : 0);
                double cx = rc * Math.cos(around), cz = rc * Math.sin(around);
                double spin = sgn * 2.4 * t + (second ? Math.PI : 0);
                double hold = (i == m - 1 && !second && m % 2 == 1) ? 0 : 0.5; // alone: waltzes by itself
                x = cx + hold * Math.cos(spin);
                z = cz + hold * Math.sin(spin);
                y = 0.3 * Math.sin(2 * around) + 0.15 * Math.sin(2 * spin);
                face = hold == 0 ? spin : Math.atan2(cz - z, cx - x); // facing its partner
            }
            default -> { // Heartbeat Nova: the ring breathes out and in on the beat, everyone hopping together
                double beat = t * 1.7 - 0.6 * ring; // the outer rings a little later: a ripple
                double pulse = Math.pow(Math.abs(Math.sin(beat)), 0.8);
                double r, a;
                if (!outer) {
                    double rmin = Math.max(1.6, fit(m));
                    r = rmin + 1.3 * pulse;
                    a = 1.015 / (rmin + 1.3) * t + phase + 0.15 * Math.sin(beat);
                } else {
                    double rc = ringCentre(dance, n, ring);
                    r = rc - 0.65 + 1.3 * pulse;
                    a = sgn / rc * t + phase + 0.15 * Math.sin(beat);
                }
                x = r * Math.cos(a);
                z = r * Math.sin(a);
                y = pulse - 0.2;
            }
        }
        hop(x, y, z, face, out, o);
    }

    /**
     * Figures crossing the middle (Infinity, Lissajous...) leap over the core instead of going through it; two dancers
     * passing the middle at different heights keep half their gap.
     */
    private static void hop(double x, double y, double z, double face, double[] out, int o) {
        double k = Math.max(0, 1 - (x * x + z * z) / (HOP_RADIUS * HOP_RADIUS));
        k *= k;
        out[o] = x;
        out[o + 1] = y * (1 - k / 2) + k * HOP_HEIGHT;
        out[o + 2] = z;
        out[o + 3] = face;
    }

    /** The figure of one slot / count at this time, blended from the previous dance just after a change of dance. */
    private static void figure(BlockPos forge, int slot, int count, long worldTime, float partialTick,
                               double[] out, int o, double[] tmp) {
        int dance = danceAt(worldTime, forge);
        long start = danceStart(worldTime);
        double sinceStart = worldTime - start + partialTick;
        offset(dance, slot, count, sinceStart / 20.0, out, o);
        if (sinceStart >= BLEND_TICKS) return;
        offset(danceAt(start - 1, forge), slot, count, (worldTime - (start - DANCE_TICKS) + partialTick) / 20.0, tmp, 0);
        double k = Easing.smoothstep(MathHelper.clamp(sinceStart / BLEND_TICKS, 0, 1));
        for (int i = 0; i < 3; i++) out[o + i] = tmp[i] + (out[o + i] - tmp[i]) * k;
        if (k < 0.5) out[o + 3] = tmp[3];
    }

    /**
     * Offset of a dancer around the forge at this time (out as in {@link #offset}; tmp: 8 scratch doubles). At a change
     * of dance (every {@value #DANCE_TICKS} ticks) it is blended from the previous dance, and after a change of slot or
     * count (someone joined or left, a new turn, at {@code slotChangeTick}) from its previous place in the figure (both
     * at once when they come together).
     */
    public static void position(BlockPos forge, int slot, int count, int prevSlot, int prevCount, long slotChangeTick,
                                long worldTime, float partialTick, double[] out, double[] tmp) {
        figure(forge, slot, count, worldTime, partialTick, out, 0, tmp);
        double sinceSlot = worldTime - slotChangeTick + partialTick;
        if (prevCount <= 0 || sinceSlot >= BLEND_TICKS) return;
        figure(forge, prevSlot, prevCount, worldTime, partialTick, tmp, 4, tmp);
        double k = Easing.smoothstep(MathHelper.clamp(sinceSlot / BLEND_TICKS, 0, 1));
        for (int i = 0; i < 3; i++) out[i] = tmp[4 + i] + (out[i] - tmp[4 + i]) * k;
        if (k < 0.5) out[3] = tmp[7];
    }

    // ------------------------------------------------------------------------------------------ spectators

    /** Spectators watch from this far from the forge's axis: clear of the widest figure of {@link #CAP} dancers. */
    public static final double SPECTATOR_RADIUS;
    /** Spectators per row (about 1.2 blocks apart); more stand on rows above, like terraces. */
    public static final int SPECTATORS_PER_ROW;

    static {
        double widest = 0;
        for (int d = 0; d < COUNT; d++) widest = Math.max(widest, extent(d, CAP));
        SPECTATOR_RADIUS = widest + 2;
        SPECTATORS_PER_ROW = (int) (MathHelper.TAU * SPECTATOR_RADIUS / 1.2);
    }

    /**
     * Spot of spectator {@code index} of {@code count} (offset from the forge's centre, y from the forge's base), spread
     * evenly round the dance on rows of {@link #SPECTATORS_PER_ROW}; out[0..2].
     */
    public static void spectatorSpot(int index, int count, double[] out) {
        int row = index / SPECTATORS_PER_ROW;
        int onRow = Math.min(SPECTATORS_PER_ROW, count - row * SPECTATORS_PER_ROW);
        double a = MathHelper.TAU * (index % SPECTATORS_PER_ROW + 0.5) / Math.max(1, onRow) + 0.4 * row;
        out[0] = SPECTATOR_RADIUS * Math.cos(a);
        out[1] = 1.4 + 1.3 * row;
        out[2] = SPECTATOR_RADIUS * Math.sin(a);
    }
}
