package fr.lordfinn.steveparty.client.entity;

/**
 * In 1.21.1 the first person arm is posed by the player model's own {@code setAngles} (1.21.3 draws it from its rest
 * pose): set while it is, so that the third person poses (Box Costume, Telescope, Stencil Hammer, goal pole flip)
 * leave it alone. Render thread only.
 */
public final class FirstPersonArm {
    public static boolean posing;

    private FirstPersonArm() {
    }
}
