package fr.lordfinn.steveparty.blocks.custom.PartyController;

import java.util.function.IntUnaryOperator;

/**
 * How the turn bar's strip of steps (now → next → later) fits the width it is given, whatever the number of players
 * and the length of the program: the next steps are shown in full (a turn: its player's head, rank, stars and coins)
 * as long as they fit, the following ones small (the head or the step's icon only), and a « +N » stands for the ones
 * that do not fit at all. Driven by the width only (the size and the scale of the HUD), never by a fixed count.
 */
public final class PartyStrip {
    /** Steps after the current one that stay on the strip whenever the width allows them at least small. */
    public static final int MIN_SHOWN = 2;

    private PartyStrip() {
    }

    /**
     * @param full    steps shown in full, the first ones after the current step
     * @param compact steps shown small, after them
     * @param hidden  steps not shown: a « +N » says how many
     */
    public record Fit(int full, int compact, int hidden) {
        public int shown() {
            return full + compact;
        }
    }

    /**
     * Fits the steps coming after the current one in {@code room} pixels: as many in full as possible, then small
     * ones in what is left; fewer in full when that is what lets {@link #MIN_SHOWN} steps be shown, or the « +N » fit.
     * When the room is too small for both, the {@link #MIN_SHOWN} steps come before the « +N » (which is then left
     * out: the result does not fit with it).
     *
     * @param full      the width each step takes shown in full, in the order they are played
     * @param compact   the width each one takes shown small
     * @param beyond    steps coming after those (never shown: counted in the « +N »)
     * @param moreWidth the width of the « +N » for a number of hidden steps
     */
    public static Fit fit(int[] full, int[] compact, int room, int beyond, IntUnaryOperator moreWidth) {
        int count = Math.min(full.length, compact.length);
        int least = Math.min(MIN_SHOWN, count);
        int mostFull = 0;
        for (int used = 0; mostFull < count && used + full[mostFull] <= room; mostFull++) used += full[mostFull];
        Fit best = null;
        for (int inFull = mostFull; inFull >= 0; inFull--) {
            int used = 0;
            for (int i = 0; i < inFull; i++) used += full[i];
            int small = 0;
            while (inFull + small < count && used + compact[inFull + small] <= room) used += compact[inFull + small++];
            int hidden = count - inFull - small + beyond;
            // Room for the « +N »: small steps give theirs, down to the least shown
            while (hidden > 0 && small > 0 && inFull + small > least && used + moreWidth.applyAsInt(hidden) > room) {
                used -= compact[inFull + --small];
                hidden++;
            }
            boolean moreFits = hidden == 0 || used + moreWidth.applyAsInt(hidden) <= room;
            Fit fit = new Fit(inFull, small, hidden);
            if (moreFits && fit.shown() >= least) return fit;
            // Nothing fits everything: the one showing the most steps, in full first
            if (best == null || fit.shown() > best.shown()) best = fit;
        }
        return best == null ? new Fit(0, 0, count + beyond) : best;
    }
}
