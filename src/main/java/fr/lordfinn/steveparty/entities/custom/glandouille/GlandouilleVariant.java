package fr.lordfinn.steveparty.entities.custom.glandouille;

/**
 * The four Glandouilles: the same bones, a texture each (textures/entity/glandouille*.png, made from the classic one by
 * the art sources), and a few numbers.
 *
 * @param scale      drawn size, hitbox and eye height, relative to the model (a tower's floors follow it: each
 *                   one stands on the cap of the one below, at that one's height)
 * @param speed      walking speed (movement speed attribute)
 * @param chargeSpeed blocks per tick of its charge (0: it never charges)
 * @param stunTicks  how long a crash into a wall leaves it dizzy
 * @param flattenAt  stomps on its cap before it is flattened
 * @param dieAt      stomps on its cap before it is done for
 * @param sway       how much a tower it carries sways while it walks (1 = a classic one)
 */
public enum GlandouilleVariant {
    /** The brown one, grumpy, charges. */
    CLASSIC("classic", 1.0f, 0.23, 0.42, 60, 1, 2, 1.0f),
    /** Green, much smaller (0.7) and quicker: a tower on it runs and sways a lot; gets over a crash sooner. */
    YOUNG("young", 0.7f, 0.32, 0.55, 30, 1, 2, 2.4f),
    /** Old and mossy, much bigger (1.4): never charges, a tower on it stays put; takes two stomps to flatten, three to finish. */
    MOSSY("mossy", 1.4f, 0.16, 0, 60, 2, 3, 0.5f),
    /** Snow on its cap, a little bigger (1.1): slides like a curling stone when hit, bounces off walls; gets over a crash sooner. */
    FROSTY("frosty", 1.1f, 0.23, 0.42, 35, 1, 2, 1.0f);

    private final String name;
    public final float scale;
    public final double speed;
    public final double chargeSpeed;
    public final int stunTicks;
    public final int flattenAt;
    public final int dieAt;
    public final float sway;

    GlandouilleVariant(String name, float scale, double speed, double chargeSpeed, int stunTicks, int flattenAt,
                       int dieAt, float sway) {
        this.name = name;
        this.scale = scale;
        this.speed = speed;
        this.chargeSpeed = chargeSpeed;
        this.stunTicks = stunTicks;
        this.flattenAt = flattenAt;
        this.dieAt = dieAt;
        this.sway = sway;
    }

    public String asString() {
        return name;
    }

    public boolean charges() {
        return chargeSpeed > 0;
    }

    public static GlandouilleVariant byId(int id) {
        GlandouilleVariant[] values = values();
        return values[Math.floorMod(id, values.length)];
    }
}
