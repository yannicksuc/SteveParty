package fr.lordfinn.steveparty.client.tokenspell;

import net.minecraft.util.math.MathHelper;

/**
 * The shape a spell asks the player to draw, and where the drawing starts: when a spell screen opens, the cursor is
 * put on {@link #startPoint} so the wand already points there, ready to trace.
 * <p>
 * The start is an angle around the guide's centre, measured from the side of the hand holding the wand (0 = the
 * guide's edge on the wand hand's side, 90 = its top, 180 = the other side), mirrored for a wand in the left hand.
 * A future spell only declares its own shape, e.g. {@code new SpellShape("star", 90)} to start at the top.
 *
 * @param name              what is drawn (for readers; the token spell draws a circle)
 * @param startAngleDegrees where the drawing starts on the guide, from the wand hand's side, counterclockwise
 */
public record SpellShape(String name, float startAngleDegrees) {
    /** The token spell: a circle, started on its edge on the wand hand's side. */
    public static final SpellShape TOKEN_CIRCLE = new SpellShape("circle", 0);

    /**
     * Where the drawing starts, in screen coordinates (y down), for a guide of {@code radius} centred on
     * ({@code centerX}, {@code centerY}).
     */
    public float[] startPoint(float centerX, float centerY, float radius, boolean rightHanded) {
        float angle = startAngleDegrees * MathHelper.RADIANS_PER_DEGREE;
        float side = rightHanded ? 1 : -1;
        return new float[]{centerX + side * MathHelper.cos(angle) * radius, centerY - MathHelper.sin(angle) * radius};
    }
}
