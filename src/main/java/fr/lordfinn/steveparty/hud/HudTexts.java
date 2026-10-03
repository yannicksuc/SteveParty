package fr.lordfinn.steveparty.hud;

/**
 * What the HUD layouts need of the texts: their widths in the game's font (the client's font; the tests give their
 * own), and the words they show (translated by the client).
 */
public interface HudTexts {
    /**
     * The width of a text as drawn, without the pixel the font adds after its last glyph (the mock-ups' width): «
     * Tom » is 17.
     */
    int width(String text);

    /** The name of a step shown big, as the current step: « Départ », « Mini-jeu »... */
    String step(TurnStripLayout.Kind kind);

    /** The next mini-game pinned before « +N »: « dans 3 ». */
    String pinned(int steps);

    /** Under my next chip: « toi ». */
    String toi();

    /** Under the current chip, my turn: « à toi ! ». */
    String toiNow();

    /** Under « +N », my next turn hidden: « toi dans 5 ». */
    String toiIn(int steps);

    /**
     * The text cut with « … » to fit in {@code max} pixels (unchanged if it fits).
     */
    default String fit(String text, int max) {
        if (width(text) <= max) return text;
        String cut = text;
        while (!cut.isEmpty() && width(cut + "…") > max) cut = cut.substring(0, cut.offsetByCodePoints(cut.length(), -1));
        return cut + "…";
    }
}
