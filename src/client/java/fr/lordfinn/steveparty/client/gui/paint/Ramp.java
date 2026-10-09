package fr.lordfinn.steveparty.client.gui.paint;

import fr.lordfinn.steveparty.utils.Argb;

/** A colour ramp of the pixel-art kit: outline, highlight, body, shadow (opaque ARGB, or with their own alpha). */
public record Ramp(int outline, int hi, int body, int shadow) {
    /** A ramp of RGB colours, all made opaque. */
    public static Ramp of(int outline, int hi, int body, int shadow) {
        return new Ramp(Argb.opaque(outline), Argb.opaque(hi), Argb.opaque(body), Argb.opaque(shadow));
    }

    /** The same colour, very light (the standings' rows). */
    public Ramp pastel() {
        return new Ramp(outline, 0xFFFFFFFF, Argb.opaque(Argb.lerp(body, 0xFFFFFFFF, 0.8f)), Argb.opaque(Argb.lerp(body, 0xFFFFFFFF, 0.6f)));
    }
}
