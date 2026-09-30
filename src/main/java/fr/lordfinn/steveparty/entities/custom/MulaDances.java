package fr.lordfinn.steveparty.entities.custom;

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
 */
public final class MulaDances {
    private MulaDances() {
    }

    public static final int COUNT = 10;
    public static final int DANCE_TICKS = 520;
    public static final int BLEND_TICKS = 60;
    /** Within this distance of the core's axis (blocks) a dancer leaps up, to this height above the core at its middle. */
    public static final double HOP_RADIUS = 1.4, HOP_HEIGHT = 1.5;

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

    private static double ease(double u) {
        u = MathHelper.clamp(u, 0, 1);
        return u * u * (3 - 2 * u);
    }

    /**
     * Offset from the forge's centre (blocks) of dancer {@code slot} of {@code count} in dance {@code dance}, at
     * {@code t} seconds into it; written into out[0..2] (x, y, z), out[3] = yaw the dancer faces (radians, or NaN to
     * face where it goes).
     */
    public static void offset(int dance, int slot, int count, double t, double[] out) {
        int n = Math.max(1, count);
        double phase = MathHelper.TAU * slot / n;
        double x, y, z, face = Double.NaN;
        switch (Math.floorMod(dance, COUNT)) {
            case 0 -> { // Carousel of Stars: a synchronized orbit, riding waves like a merry-go-round
                double a = 0.9 * t + phase;
                double r = 2.6 + 0.25 * Math.sin(3 * a);
                x = r * Math.cos(a);
                z = r * Math.sin(a);
                y = 0.55 * Math.sin(2 * a + 1.8 * t);
            }
            case 1 -> { // Rose Window: each draws the petals of a rose, r = cos(3 theta), never through the core
                double a = 0.55 * t + phase;
                double r = 0.9 + 2.1 * (0.5 + 0.5 * Math.cos(3 * a));
                x = r * Math.cos(a);
                z = r * Math.sin(a);
                y = 0.35 * Math.sin(3 * a) + 0.2;
            }
            case 2 -> { // Infinity: a lemniscate of Bernoulli, slowly turning, dancers chasing along it
                double s = 1.1 * t + phase;
                double d = 1 + Math.sin(s) * Math.sin(s);
                double lx = 3.0 * Math.cos(s) / d, lz = 3.0 * Math.sin(s) * Math.cos(s) / d;
                double turn = 0.15 * t;
                x = lx * Math.cos(turn) - lz * Math.sin(turn);
                z = lx * Math.sin(turn) + lz * Math.cos(turn);
                y = 0.45 * Math.sin(2 * s);
            }
            case 3 -> { // Lissajous Lace: 3:2 lace in the air, each dancer a phase along it
                double s = 0.35 * t;
                x = 2.4 * Math.sin(3 * s + phase);
                z = 2.4 * Math.sin(2 * s + phase * 0.5);
                y = 0.5 * Math.sin(s + phase);
            }
            case 4 -> { // Starlit Staircase: a helix climbing and coming down again, dancers as its steps
                double a = 1.2 * t + phase;
                double climb = 0.5 - 0.5 * Math.cos(0.5 * t);
                double r = 2.0 + 0.5 * Math.cos(2 * a);
                x = r * Math.cos(a);
                z = r * Math.sin(a);
                y = -0.6 + 2.6 * climb + 0.25 * slot / (double) n;
            }
            case 5 -> { // Planets and Moons: epicycles, a big slow circle and a quick small loop on it
                double a = 0.5 * t + phase;
                double b = -3.5 * t + phase;
                x = 2.5 * Math.cos(a) + 0.75 * Math.cos(b);
                z = 2.5 * Math.sin(a) + 0.75 * Math.sin(b);
                y = 0.3 * Math.sin(b);
            }
            case 6 -> { // Sunflower Bloom: phyllotaxis (golden angle), the flower breathing and turning
                double golden = 2.39996323;
                double a = golden * (slot + 1) + 0.35 * t;
                double r = (0.8 + 0.75 * Math.sqrt(slot + 1)) * (0.75 + 0.25 * Math.sin(1.2 * t));
                x = r * Math.cos(a);
                z = r * Math.sin(a);
                y = 0.25 * slot / (double) n + 0.3 * Math.sin(1.2 * t + slot);
            }
            case 7 -> { // Braided Comet: three strands weaving over and under each other
                double a = 1.0 * t + phase;
                double r = 2.4 + 0.55 * Math.sin(3 * a + phase);
                x = r * Math.cos(a);
                z = r * Math.sin(a);
                y = 0.8 * Math.sin(2 * a + 2 * phase);
            }
            case 8 -> { // Moonlight Waltz: pairs holding hands, turning round each other while they circle the core
                int pair = slot / 2, pairs = (n + 1) / 2;
                boolean second = (slot & 1) == 1;
                double around = 0.45 * t + MathHelper.TAU * pair / pairs;
                double cx = 2.6 * Math.cos(around), cz = 2.6 * Math.sin(around);
                double spin = 2.4 * t + (second ? Math.PI : 0);
                double hold = (slot == n - 1 && !second && n % 2 == 1) ? 0 : 0.42; // alone: waltzes by itself
                x = cx + hold * Math.cos(spin);
                z = cz + hold * Math.sin(spin);
                y = 0.3 * Math.sin(2 * around) + 0.15 * Math.sin(2 * spin);
                face = Math.atan2(cz - z, cx - x); // facing its partner
                if (hold == 0) face = spin;
            }
            default -> { // Heartbeat Nova: the ring breathes out and in on the beat, everyone hopping together
                double beat = t * 1.7;
                double pulse = Math.pow(Math.abs(Math.sin(beat)), 0.8);
                double a = 0.35 * t + phase + 0.15 * Math.sin(beat);
                double r = 1.6 + 1.3 * pulse;
                x = r * Math.cos(a);
                z = r * Math.sin(a);
                y = 1.0 * pulse - 0.2;
            }
        }
        // figures crossing the middle (Infinity, Lissajous...) leap over the core instead of going through it
        double k = Math.max(0, 1 - (x * x + z * z) / (HOP_RADIUS * HOP_RADIUS));
        y += k * k * (HOP_HEIGHT - y);
        out[0] = x;
        out[1] = y;
        out[2] = z;
        out[3] = face;
    }

    /**
     * Offset of a dancer around the forge at this time (out as in {@link #offset}; tmp: 4 scratch doubles). At a change
     * of dance (every {@value #DANCE_TICKS} ticks) it is blended from the previous dance, and after a change of slot or
     * count (someone joined or left, at {@code slotChangeTick}) from its previous place in the figure.
     */
    public static void position(BlockPos forge, int slot, int count, int prevSlot, int prevCount, long slotChangeTick,
                                long worldTime, float partialTick, double[] out, double[] tmp) {
        int dance = danceAt(worldTime, forge);
        long start = danceStart(worldTime);
        double sinceStart = worldTime - start + partialTick;
        offset(dance, slot, count, sinceStart / 20.0, out);
        double sinceSlot = worldTime - slotChangeTick + partialTick;
        double k;
        if (sinceStart < BLEND_TICKS) {
            int previous = danceAt(start - 1, forge);
            offset(previous, slot, count, (worldTime - (start - DANCE_TICKS) + partialTick) / 20.0, tmp);
            k = ease(sinceStart / BLEND_TICKS);
        } else if (prevCount > 0 && sinceSlot < BLEND_TICKS) {
            offset(dance, prevSlot, prevCount, sinceStart / 20.0, tmp);
            k = ease(sinceSlot / BLEND_TICKS);
        } else {
            return;
        }
        for (int i = 0; i < 3; i++) out[i] = tmp[i] + (out[i] - tmp[i]) * k;
        if (k < 0.5) out[3] = tmp[3];
    }
}
