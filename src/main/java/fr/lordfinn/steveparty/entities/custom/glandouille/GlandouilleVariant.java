package fr.lordfinn.steveparty.entities.custom.glandouille;

/**
 * The four Glandouilles: a model and a texture each (geo/entity/glandouille[_variant].geo.json: the classic one is
 * the dev's model, the others are made by the art sources, with the same bones so they
 * share the animations), their size in pixels, and a few numbers.
 *
 * @param widthPx    hitbox width: its cap's
 * @param heightPx   hitbox height: up to the top of its cap (what a Glandouille climbing on it stands on)
 * @param eyePx      eye height
 * @param browHidePx how far up its brows could slide under its cap (its calm brows sit a fifth of it higher)
 * @param scale      size relative to its model, hitbox and drawing alike ({@link GlandouilleEntity#SIZE}: every Glandouille is a
 *                   little thing, 60 % of its model)
 * @param speed      walking speed (movement speed attribute)
 * @param chargeSpeed blocks per tick of its charge (0: it never charges)
 * @param stunTicks  how long a crash into a wall leaves it dizzy
 * @param flattenAt  stomps on its cap before it is flattened
 * @param dieAt      stomps on its cap before it is done for
 * @param sway       how much a tower it carries sways while it walks (1 = a classic one)
 */
public enum GlandouilleVariant {
    /** The brown one, grumpy, charges. */
    CLASSIC("classic", 12, 15, 8.8f, 3.2f, GlandouilleEntity.SIZE, 0.23, 0.42, 60, 1, 2, 1.0f),
    /**
     * Green, small and slim (a 7x9x7 nut, a tight cap, a long stem, big baby eyes), quicker: a tower on it runs and
     * sways a lot; gets over a crash sooner.
     */
    YOUNG("young", 9, 11, 5.5f, 1.6f, GlandouilleEntity.SIZE, 0.32, 0.55, 30, 1, 2, 2.4f),
    /**
     * Old and mossy, wide and squat (a 12x11x12 nut under a broad drooping cap, thick brows, a broken stem; moss in its texture): never
     * charges, a tower on it stays put; takes two stomps to flatten, three to finish.
     */
    MOSSY("mossy", 15, 14, 5.5f, 3.2f, GlandouilleEntity.SIZE, 0.16, 0, 60, 2, 3, 0.5f),
    /**
     * A little bigger and rounder (an 11x11x11 nut), snow on its cap (in its texture): slides like a curling stone when hit, bounces
     * off walls; gets over a crash sooner.
     */
    FROSTY("frosty", 13, 15, 7.5f, 3.2f, GlandouilleEntity.SIZE, 0.23, 0.42, 35, 1, 2, 1.0f);

    private final String name;
    public final int widthPx;
    public final int heightPx;
    public final float eyePx;
    public final float browHidePx;
    public final float scale;
    public final double speed;
    public final double chargeSpeed;
    public final int stunTicks;
    public final int flattenAt;
    public final int dieAt;
    public final float sway;

    GlandouilleVariant(String name, int widthPx, int heightPx, float eyePx, float browHidePx, float scale, double speed, double chargeSpeed, int stunTicks, int flattenAt,
                       int dieAt, float sway) {
        this.name = name;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.eyePx = eyePx;
        this.browHidePx = browHidePx;
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

    /** Its hitbox (blocks), before its scale. */
    public net.minecraft.entity.EntityDimensions dimensions() {
        return net.minecraft.entity.EntityDimensions.changing(widthPx / 16f, heightPx / 16f).withEyeHeight(eyePx / 16f);
    }

    /** Its model's and texture's name: glandouille, glandouille_young... */
    public String modelName() {
        return this == CLASSIC ? "glandouille" : "glandouille_" + name;
    }

    public boolean charges() {
        return chargeSpeed > 0;
    }

    public static GlandouilleVariant byId(int id) {
        GlandouilleVariant[] values = values();
        return values[Math.floorMod(id, values.length)];
    }
}
