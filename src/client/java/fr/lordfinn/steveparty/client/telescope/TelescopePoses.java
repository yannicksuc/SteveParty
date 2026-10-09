package fr.lordfinn.steveparty.client.telescope;

import fr.lordfinn.steveparty.blocks.custom.TelescopeBlockEntity;
import fr.lordfinn.steveparty.telescope.TelescopeMath;
import fr.lordfinn.steveparty.utils.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The players seen looking through a Telescope, from outside: each comes to the eyepiece (bent, his eye against it,
 * his body turned like the tube, a hand on it) over a few ticks, and goes back as smoothly when he stops; the tube
 * follows his eyes meanwhile.
 * <p>
 * All of it is worked out here from two things every client already has: who the telescope's watcher is (its block
 * entity) and where that player looks (his head, as for any player). Nothing else is sent.
 */
public final class TelescopePoses {
    private TelescopePoses() {
    }

    /** Ticks to come to the eyepiece, and to leave it. */
    private static final float EASE_TICKS = 7f;

    /** One watcher: his telescope and how far into the pose he is (0 not at all .. 1 at the eyepiece). */
    public static final class Pose {
        public BlockPos pos;
        private float ease, prevEase;
        private boolean watching;

        /** 0..1, eased in and out. */
        public float ease(float tickDelta) {
            float e = MathHelper.lerp(tickDelta, prevEase, ease);
            return Easing.smoothstep(e);
        }
    }

    /** Where a posed player is drawn and how: see {@link #stance}. */
    public static final class Stance {
        /** His feet, moved by this much from where he really stands. */
        public double dx, dy, dz;
        /** 0 standing .. 1 bent as low as sneaking. */
        public float bend;
    }

    private static final Map<UUID, Pose> POSES = new HashMap<>();
    private static final Map<UUID, BlockPos> SEEN = new HashMap<>();
    private static final double[] NECK = new double[3];

    public static void tick(MinecraftClient client) {
        if (client.world == null) {
            POSES.clear();
            SEEN.clear();
            TelescopeBlockEntity.takeSeen(SEEN);
            SEEN.clear();
            return;
        }
        if (client.isPaused()) return;
        SEEN.clear();
        TelescopeBlockEntity.takeSeen(SEEN);
        for (Map.Entry<UUID, BlockPos> seen : SEEN.entrySet()) {
            Pose pose = POSES.computeIfAbsent(seen.getKey(), id -> new Pose());
            // from one telescope to another: only once he is back up
            if (pose.pos == null || pose.ease <= 0) pose.pos = seen.getValue();
            pose.watching = pose.pos.equals(seen.getValue());
        }
        for (Iterator<Pose> it = POSES.values().iterator(); it.hasNext(); ) {
            Pose pose = it.next();
            pose.prevEase = pose.ease;
            pose.ease = MathHelper.clamp(pose.ease + (pose.watching ? 1 : -1) / EASE_TICKS, 0f, 1f);
            if (!pose.watching && pose.ease <= 0 && pose.prevEase <= 0) it.remove();
            pose.watching = false;
        }
    }

    public static @Nullable Pose of(UUID player) {
        return POSES.get(player);
    }

    /** The player seen at this telescope (coming, there or leaving), or null. */
    public static @Nullable PlayerEntity watcherOf(BlockPos pos, float[] easeOut, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || POSES.isEmpty()) return null;
        for (Map.Entry<UUID, Pose> entry : POSES.entrySet()) {
            if (!pos.equals(entry.getValue().pos)) continue;
            PlayerEntity player = client.world.getPlayerByUuid(entry.getKey());
            if (player == null) continue;
            easeOut[0] = entry.getValue().ease(tickDelta);
            return player;
        }
        return null;
    }

    /**
     * Where a player must stand and how much he bends for his eye to meet the eyepiece of the telescope at
     * {@code pos}, the tube pointing where he looks.
     *
     * @param x his real place (feet)
     */
    public static void stance(BlockPos pos, float yaw, float pitch, double x, double y, double z, Stance out) {
        TelescopeMath.neck(yaw, pitch, NECK);
        double neckY = pos.getY() + TelescopeMath.PIVOT_HEIGHT + NECK[1];
        double bend = TelescopeMath.bend(neckY - y);
        out.bend = (float) bend;
        out.dx = pos.getX() + 0.5 + NECK[0] - x;
        // what the bend cannot reach (a player standing lower): the model is moved down; never lifted off the ground
        out.dy = MathHelper.clamp(neckY - (TelescopeMath.NECK_HEIGHT - TelescopeMath.BEND_DROP * bend) - y, -0.5, 0.05);
        out.dz = pos.getZ() + 0.5 + NECK[2] - z;
    }

    /** The offset of his drawn feet at this ease (the bent model's own drop included). */
    public static Vec3d offset(Stance stance, float ease) {
        return new Vec3d(stance.dx * ease, stance.dy * ease, stance.dz * ease);
    }
}
