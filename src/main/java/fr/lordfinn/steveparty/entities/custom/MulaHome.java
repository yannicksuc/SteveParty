package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A Mula's home: the Dice Forge it lives at. A forge with its core in (activated) takes in the Mulas within
 * {@value #RADIUS} blocks (DiceForgeBlockEntity#conductMulas, once a second), and from then on none of their behaviours
 * takes them out of its area: every flight target is brought back inside it (SimpleFlyingMoveControl), wandering, play
 * and hiding pick their spots inside it, at night they twinkle above the forge, and a burst sends them on a short
 * arc back onto it. Only a tamed Mula whose owner walks away leaves with them (it is then released).
 * <p>
 * The home is saved with the Mula (it survives chunk reloads and restarts) and released as soon as the forge is
 * broken or loses its core (checked once a second, only while the forge's chunk is loaded).
 */
public final class MulaHome {
    private MulaHome() {
    }

    /** The forge's area (blocks, horizontally from its centre). */
    public static final double RADIUS = 16;
    /** How far below and above the forge its Mulas may fly (blocks). */
    public static final double BELOW = 4, ABOVE = 24;
    /** A tamed Mula led this far from its forge by its owner is released (blocks). */
    public static final double RELEASE_DISTANCE = 2 * RADIUS;

    /** Brings a point (x, y, z in xyz) back inside the area of the forge at {@code home}, 1 block inside its edge. */
    public static void clamp(BlockPos home, double[] xyz) {
        double cx = home.getX() + 0.5, cz = home.getZ() + 0.5;
        double dx = xyz[0] - cx, dz = xyz[2] - cz;
        double d = Math.sqrt(dx * dx + dz * dz), r = RADIUS - 1;
        if (d > r) {
            xyz[0] = cx + dx / d * r;
            xyz[2] = cz + dz / d * r;
        }
        xyz[1] = MathHelper.clamp(xyz[1], home.getY() - BELOW, home.getY() + ABOVE);
    }

    public static Vec3d clamp(BlockPos home, Vec3d v) {
        double[] xyz = {v.x, v.y, v.z};
        clamp(home, xyz);
        return new Vec3d(xyz[0], xyz[1], xyz[2]);
    }

    /** Inside the area (with the vertical limits). */
    public static boolean contains(BlockPos home, double x, double y, double z) {
        double dx = x - (home.getX() + 0.5), dz = z - (home.getZ() + 0.5);
        return dx * dx + dz * dz <= RADIUS * RADIUS && y >= home.getY() - BELOW - 0.01 && y <= home.getY() + ABOVE + 0.01;
    }

    /** A forge that holds Mulas: a Dice Forge with its core in. */
    public static boolean holds(World world, BlockPos forge) {
        return world.getBlockEntity(forge) instanceof DiceForgeBlockEntity f && f.isActivated();
    }
}
