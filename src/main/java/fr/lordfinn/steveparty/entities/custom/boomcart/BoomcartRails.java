package fr.lordfinn.steveparty.entities.custom.boomcart;

import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * How a Boomcart rolls on rails, like a minecart: on a rail it snaps onto the track and follows it, curves and slopes
 * included, at a speed of its own ({@link #speed}, blocks per tick, always along the way it goes).
 * <ul>
 *     <li>The track through a rail block is the straight line between its two exits' edge midpoints (the vanilla
 *     minecart's: curves are cut diagonally), the high exit of a slope one block up.</li>
 *     <li>Powered rails boost it up to {@link #MAX_SPEED}; unpowered ones brake it to a stop, as minecarts.</li>
 *     <li>Slopes slow it going up and speed it going down; it slows down by itself otherwise ({@link #FRICTION}).</li>
 *     <li>Detector rails see it (DetectorRailBlockMixin): its box is the minecart's there.</li>
 *     <li>At the end of the track it rolls off, the rails behind it; against a rail that doesn't join it stops.</li>
 * </ul>
 * Pushes (a player, an explosion) count along the track: the speed is read back from its velocity each tick.
 */
public final class BoomcartRails {
    /** The vanilla minecart's top speed on rails (blocks per tick). */
    public static final double MAX_SPEED = 0.4;
    /** A powered rail's push each tick, and the push that starts it from a stop. */
    private static final double BOOST = 0.06, KICK = 0.02;
    /** An unpowered powered rail's brake, and below this it stops. */
    private static final double BRAKE = 0.5, STOP = 0.03;
    /** A slope's pull each tick (the vanilla minecart's). */
    private static final double SLOPE = 0.0078125;
    private static final double FRICTION = 0.985;

    private BoomcartRails() {
    }

    /** One rail block's track: its block, the two exits (unit steps on x/z) and whether each is the high end. */
    public record Track(BlockPos pos, RailShape shape, int ax, int az, boolean aUp, int bx, int bz, boolean bUp) {
        Vec3d exit(boolean b) {
            return new Vec3d(pos.getX() + 0.5 + 0.5 * (b ? bx : ax), pos.getY() + ((b ? bUp : aUp) ? 1 : 0),
                    pos.getZ() + 0.5 + 0.5 * (b ? bz : az));
        }

        /** Horizontal unit direction from exit a to exit b. */
        Vec3d direction() {
            return exit(true).subtract(exit(false)).multiply(1, 0, 1).normalize();
        }

        boolean joins(int dx, int dz) {
            return (ax == dx && az == dz) || (bx == dx && bz == dz);
        }
    }

    /** The track at a block (exits from its shape), or null without a rail there. */
    public static @Nullable Track track(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof AbstractRailBlock rail)) return null;
        RailShape shape = state.get(rail.getShapeProperty());
        return switch (shape) {
            case NORTH_SOUTH -> new Track(pos, shape, 0, -1, false, 0, 1, false);
            case EAST_WEST -> new Track(pos, shape, -1, 0, false, 1, 0, false);
            case ASCENDING_EAST -> new Track(pos, shape, -1, 0, false, 1, 0, true);
            case ASCENDING_WEST -> new Track(pos, shape, 1, 0, false, -1, 0, true);
            case ASCENDING_NORTH -> new Track(pos, shape, 0, 1, false, 0, -1, true);
            case ASCENDING_SOUTH -> new Track(pos, shape, 0, -1, false, 0, 1, true);
            case SOUTH_EAST -> new Track(pos, shape, 0, 1, false, 1, 0, false);
            case SOUTH_WEST -> new Track(pos, shape, 0, 1, false, -1, 0, false);
            case NORTH_WEST -> new Track(pos, shape, 0, -1, false, -1, 0, false);
            case NORTH_EAST -> new Track(pos, shape, 0, -1, false, 1, 0, false);
        };
    }

    /** The track it stands on: the rail at its feet, or one below (on the high end of a slope). */
    public static @Nullable Track under(World world, Vec3d pos) {
        BlockPos at = BlockPos.ofFloored(pos.x, pos.y + 0.2, pos.z);
        Track track = track(world, at);
        return track != null ? track : track(world, at.down());
    }

    /** The point of the track nearest to pos, and how far along it (0 at exit a, 1 at exit b). */
    private static double along(Track track, Vec3d pos) {
        Vec3d a = track.exit(false), ab = track.exit(true).subtract(a);
        double t = (pos.x - a.x) * ab.x + (pos.z - a.z) * ab.z;
        return MathHelper.clamp(t / (ab.x * ab.x + ab.z * ab.z), 0, 1);
    }

    private static Vec3d at(Track track, double t) {
        Vec3d a = track.exit(false);
        return a.add(track.exit(true).subtract(a).multiply(t));
    }

    /** The result of a tick on the rails: where it ends, which way it goes (horizontal unit), its speed, still on? */
    public record Step(Vec3d pos, Vec3d heading, double speed, boolean onRails) {
    }

    /**
     * A tick on the rails.
     *
     * @param velocity its velocity (pushes included): its part along the track is its speed, its sign the way it goes
     * @param motor    the way it would like to go (a horizontal vector, or zero) and how hard it pushes (its length,
     *                 blocks per tick per tick), up to {@code cruise}
     */
    public static Step roll(World world, Track track, Vec3d pos, Vec3d velocity, Vec3d motor, double cruise) {
        Vec3d dir = track.direction();
        double signed = velocity.x * dir.x + velocity.z * dir.z;
        boolean forward = Math.abs(signed) > 1.0e-3 ? signed > 0 : motor.x * dir.x + motor.z * dir.z >= 0;
        double speed = Math.abs(signed);
        Vec3d heading = forward ? dir : dir.negate();
        // its own push, up to its cruising speed, the way it wants to go (it turns round when it's slow enough)
        double want = motor.x * heading.x + motor.z * heading.z;
        if (want > 0 && speed < cruise) speed = Math.min(cruise, speed + want);
        else if (want < 0) speed -= -want;
        if (speed < 0) {
            speed = -speed;
            forward = !forward;
            heading = heading.negate();
        }
        BlockState state = world.getBlockState(track.pos);
        if (state.isOf(Blocks.POWERED_RAIL)) {
            if (state.get(PoweredRailBlock.POWERED)) {
                speed = speed < STOP ? speed + KICK : Math.min(MAX_SPEED, speed + BOOST);
            } else {
                speed *= BRAKE;
                if (speed < STOP) speed = 0;
            }
        }
        if (track.aUp || track.bUp) speed += (forward == track.bUp) ? -SLOPE : SLOPE; // up the slope: slower
        speed = MathHelper.clamp(speed * FRICTION, 0, MAX_SPEED);
        return move(world, track, pos, forward, speed);
    }

    /** Moves speed blocks along the track from pos, from one rail block to the next, the way it goes. */
    private static Step move(World world, Track track, Vec3d pos, boolean forward, double speed) {
        double t = along(track, pos);
        Vec3d p = at(track, t);
        double left = speed;
        for (int guard = 0; guard < 8; guard++) {
            Vec3d end = track.exit(forward);
            double toEnd = end.distanceTo(p);
            Vec3d heading = (forward ? track.direction() : track.direction().negate());
            if (left <= toEnd || speed == 0) {
                Vec3d next = toEnd < 1.0e-6 ? p : p.add(end.subtract(p).multiply(left / toEnd));
                return new Step(next, heading, speed, true);
            }
            left -= toEnd;
            int dx = forward ? track.bx : track.ax, dz = forward ? track.bz : track.az;
            boolean up = forward ? track.bUp : track.aUp;
            BlockPos ahead = track.pos.add(dx, up ? 1 : 0, dz);
            Track next = track(world, ahead);
            if (next == null) next = track(world, ahead.down());
            if (next == null) {
                // the end of the line: it rolls off
                return new Step(end.add(heading.multiply(left)), heading, speed, false);
            }
            if (!next.joins(-dx, -dz)) {
                return new Step(end, heading, 0, true); // a rail that doesn't join: it bumps into it
            }
            forward = next.ax == -dx && next.az == -dz; // it enters by exit a: it goes toward b
            track = next;
            p = track.exit(!forward);
        }
        return new Step(p, forward ? track.direction() : track.direction().negate(), speed, true);
    }
}
