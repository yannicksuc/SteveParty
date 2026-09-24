package fr.lordfinn.steveparty.client.token;

/**
 * Token base data added to {@code LivingEntityRenderState} (see {@code TokenBaseRenderStateMixin}). The render state
 * of a renderer is shared by all the entities it draws: it is filled (or cleared) on every update.
 */
public interface TokenBaseRenderState {

    boolean steveparty$isToken();

    /** Colour of the base, 0xRRGGBB. */
    int steveparty$getBaseColor();

    /** Height of the base, in blocks. */
    float steveparty$getBaseHeight();

    /** Half-width of the base (distance from the centre to a flat side), in blocks. */
    float steveparty$getBaseRadius();

    void steveparty$setTokenBase(boolean token, int color, float height, float radius);
}
