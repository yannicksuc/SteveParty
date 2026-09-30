package fr.lordfinn.steveparty.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Picks the most present colour of an image (a mob texture), without ever averaging distinct colours.
 * <p>
 * Pure helper (no Minecraft client class), so it can be unit-tested with synthetic pixels:
 * <ol>
 *     <li>transparent pixels ({@code alpha < 128}) are ignored, and so are near-black outline pixels
 *     (CIELAB lightness under {@value #OUTLINE_LIGHTNESS}) unless the image has nothing else;</li>
 *     <li>the remaining pixels are quantised (5 bits per channel) into a histogram;</li>
 *     <li>bins are clustered greedily, most populated first: a bin joins the closest cluster whose seed is within
 *     {@value #MERGE_DISTANCE} CIELAB ΔE, otherwise it seeds a new cluster. The representative colour of a cluster
 *     is its seed (its most present shade), never a mix;</li>
 *     <li>the most populated cluster wins. When the top clusters are nearly tied (within {@code 1 - TIE_RATIO} of
 *     the first one, at most {@value #MAX_TIED}), one of them is picked at random.</li>
 * </ol>
 */
public final class DominantColorPicker {
    /** No colour could be computed (empty / fully transparent image). */
    public static final int NO_COLOR = -1;

    static final int MIN_ALPHA = 128;
    /** CIELAB L* under which an opaque pixel counts as a dark outline. */
    static final double OUTLINE_LIGHTNESS = 12.0;
    /** CIELAB ΔE76 under which two shades are the same colour. */
    static final double MERGE_DISTANCE = 18.0;
    /** A cluster holding at least this ratio of the first cluster's pixels is tied with it (10 %). */
    static final double TIE_RATIO = 0.9;
    static final int MAX_TIED = 3;

    private static final int BITS = 5;
    private static final int LEVELS = 1 << BITS;

    private DominantColorPicker() {
    }

    /** A cluster of similar shades: its representative RGB colour (0xRRGGBB) and its pixel count. */
    public record Cluster(int rgb, int count) {
    }

    /**
     * @param argbPixels pixels in ARGB (0xAARRGGBB)
     * @return the candidate colours (0xRRGGBB): the most present cluster, plus the clusters nearly tied with it.
     * Empty if the image has no opaque pixel.
     */
    public static int[] candidates(int[] argbPixels) {
        List<Cluster> clusters = clusters(argbPixels);
        if (clusters.isEmpty()) return new int[0];
        int top = clusters.getFirst().count();
        int[] tied = new int[Math.min(MAX_TIED, clusters.size())];
        int size = 0;
        for (Cluster cluster : clusters) {
            if (size >= tied.length || cluster.count() < top * TIE_RATIO) break;
            tied[size++] = cluster.rgb();
        }
        return Arrays.copyOf(tied, size);
    }

    /** @return one of {@code candidates} at random, or {@link #NO_COLOR} if there is none. */
    public static int pick(int[] candidates, RandomGenerator random) {
        if (candidates == null || candidates.length == 0) return NO_COLOR;
        return candidates.length == 1 ? candidates[0] : candidates[random.nextInt(candidates.length)];
    }

    /** Shortcut for {@code pick(candidates(argbPixels), random)}. */
    public static int pickFromPixels(int[] argbPixels, RandomGenerator random) {
        return pick(candidates(argbPixels), random);
    }

    /** @return the colour clusters of the image, most populated first. */
    public static List<Cluster> clusters(int[] argbPixels) {
        List<Cluster> clusters = clusters(argbPixels, true);
        // A (nearly) black mob: its dark pixels are its colour, not an outline
        return clusters.isEmpty() ? clusters(argbPixels, false) : clusters;
    }

    private static List<Cluster> clusters(int[] argbPixels, boolean skipOutline) {
        int[] counts = new int[LEVELS * LEVELS * LEVELS];
        long[] sums = new long[counts.length * 3];
        for (int argb : argbPixels) {
            if ((argb >>> 24) < MIN_ALPHA) continue;
            int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
            if (skipOutline && lab(r, g, b)[0] < OUTLINE_LIGHTNESS) continue;
            int bin = ((r >> (8 - BITS)) * LEVELS + (g >> (8 - BITS))) * LEVELS + (b >> (8 - BITS));
            counts[bin]++;
            sums[bin * 3] += r;
            sums[bin * 3 + 1] += g;
            sums[bin * 3 + 2] += b;
        }

        // Bins, most populated first; their colour is the mean of pixels differing by less than 8 levels
        List<Cluster> bins = new ArrayList<>();
        for (int bin = 0; bin < counts.length; bin++) {
            int count = counts[bin];
            if (count == 0) continue;
            int r = (int) Math.round((double) sums[bin * 3] / count);
            int g = (int) Math.round((double) sums[bin * 3 + 1] / count);
            int b = (int) Math.round((double) sums[bin * 3 + 2] / count);
            bins.add(new Cluster((r << 16) | (g << 8) | b, count));
        }
        bins.sort(Comparator.comparingInt(Cluster::count).reversed().thenComparingInt(Cluster::rgb));

        // Greedy clustering around fixed seeds: seeds are pairwise distinct, and no chaining towards a mix
        List<double[]> seedsLab = new ArrayList<>();
        List<Integer> seedsRgb = new ArrayList<>();
        List<Integer> seedsCount = new ArrayList<>();
        for (Cluster bin : bins) {
            double[] lab = lab(bin.rgb());
            int closest = -1;
            double closestDistance = MERGE_DISTANCE;
            for (int i = 0; i < seedsLab.size(); i++) {
                double distance = distance(lab, seedsLab.get(i));
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = i;
                }
            }
            if (closest >= 0) {
                seedsCount.set(closest, seedsCount.get(closest) + bin.count());
            } else {
                seedsLab.add(lab);
                seedsRgb.add(bin.rgb());
                seedsCount.add(bin.count());
            }
        }

        List<Cluster> clusters = new ArrayList<>(seedsRgb.size());
        for (int i = 0; i < seedsRgb.size(); i++) {
            clusters.add(new Cluster(seedsRgb.get(i), seedsCount.get(i)));
        }
        clusters.sort(Comparator.comparingInt(Cluster::count).reversed());
        return clusters;
    }

    /** CIELAB ΔE76 between two RGB colours (0xRRGGBB). */
    public static double distance(int rgb1, int rgb2) {
        return distance(lab(rgb1), lab(rgb2));
    }

    private static double distance(double[] lab1, double[] lab2) {
        double dl = lab1[0] - lab2[0], da = lab1[1] - lab2[1], db = lab1[2] - lab2[2];
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    private static double[] lab(int rgb) {
        return lab((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    /** sRGB (D65) to CIELAB. */
    private static double[] lab(int r, int g, int b) {
        double lr = linear(r), lg = linear(g), lb = linear(b);
        double x = (0.4124564 * lr + 0.3575761 * lg + 0.1804375 * lb) / 0.95047;
        double y = 0.2126729 * lr + 0.7151522 * lg + 0.0721750 * lb;
        double z = (0.0193339 * lr + 0.1191920 * lg + 0.9503041 * lb) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double f(double t) {
        return t > 216.0 / 24389.0 ? Math.cbrt(t) : (24389.0 / 27.0 * t + 16) / 116;
    }
}
