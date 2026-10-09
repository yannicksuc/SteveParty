package fr.lordfinn.steveparty.entities.custom.fumarole;

/**
 * One of the Fumarole's heads, a turret of its own: its neck chain in the model ({@code neck<suffix>_NN},
 * {@code head<suffix>}, {@code vent<suffix>}), its spit animation ({@code animation.nether_turtle.spit<suffix>}), and
 * where its nozzle rests in the S pose (blocks, from the turtle's feet): its neck's base {@code side} to the turtle's
 * right (negative: left) and {@code base} ahead, the nozzle {@code reach} further on along {@code restYaw} (degrees
 * from the body, positive to the right) and {@code up} high, pointing {@code restPitch} down. A head turns about its
 * neck's base.
 */
public record FumaroleHead(int index, String suffix, double side, double base, double reach, double up,
                           float restYaw, float restPitch) {
    /** Bone or animation name for this head: {@code base} + its suffix ("head" -> "head_c"). */
    public String name(String base) {
        return base + suffix;
    }
}
