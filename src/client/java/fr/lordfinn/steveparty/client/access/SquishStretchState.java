package fr.lordfinn.steveparty.client.access;

/**
 * Squash and stretch of a living entity's render state (tokenization jelly): vertical scale factor, the horizontal
 * scale compensating so that the volume stays the same. 1 = no deformation.
 */
public interface SquishStretchState {
    float steveparty$getStretch();

    void steveparty$setStretch(float stretch);
}
