package fr.lordfinn.steveparty.entities.custom.glandouille;

import fr.lordfinn.steveparty.config.ServerConfig;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Towers of Glandouilles: vanilla riding, each one a passenger of the one below. Only the bottom one has a mind
 * (the others' AI is off while they ride, see {@link GlandouilleEntity#isAiDisabled}): the tower walks, sways and
 * charges with it.
 * <ul>
 *     <li><b>Spontaneous</b>: two calm Glandouilles meeting may climb on each other, up to {@link #SPONTANEOUS_MAX}.</li>
 *     <li><b>Carried</b>: sneak + right click with an empty hand on any one of a tower picks the whole tower up (its
 *     bottom one rides the player, above the head). Right click with an empty hand on another tower: the carried one
 *     goes on top of it; on a block: it is put down there. Built that way, a tower has no height limit but the
 *     server's safety one ({@link ServerConfig#glandouilleMaxStack}).</li>
 *     <li><b>Flick</b>: hitting one inside a tower (not the bottom one) shoots it out like a missile along the blow;
 *     the ones above it come down one place.</li>
 *     <li><b>Collapse</b>: only when the bottom one charges into a wall: everyone falls, fanned out, dizzy.</li>
 * </ul>
 */
public final class GlandouilleTowers {
    /** Highest tower Glandouilles build by themselves. */
    public static final int SPONTANEOUS_MAX = 5;

    private GlandouilleTowers() {
    }

    /** The server's safety limit on any tower (player-built ones included). */
    public static int maxStack() {
        return Math.max(SPONTANEOUS_MAX, ServerConfig.get().glandouilleMaxStack);
    }

    // ---------------------------------------------------------------- reading a tower

    /** The Glandouille riding {@code glandouille}, or null. */
    public static @Nullable GlandouilleEntity rider(GlandouilleEntity glandouille) {
        for (Entity passenger : glandouille.getPassengerList()) {
            if (passenger instanceof GlandouilleEntity above) return above;
        }
        return null;
    }

    public static boolean hasRider(GlandouilleEntity glandouille) {
        return rider(glandouille) != null;
    }

    /** The bottom one of {@code glandouille}'s tower (itself if it rides no Glandouille). */
    public static GlandouilleEntity bottom(GlandouilleEntity glandouille) {
        GlandouilleEntity at = glandouille;
        for (int guard = 0; guard < 1024 && at.getVehicle() instanceof GlandouilleEntity below; guard++) at = below;
        return at;
    }

    /** The top one of {@code glandouille}'s tower. */
    public static GlandouilleEntity top(GlandouilleEntity glandouille) {
        GlandouilleEntity at = glandouille;
        for (int guard = 0; guard < 1024; guard++) {
            GlandouilleEntity above = rider(at);
            if (above == null) break;
            at = above;
        }
        return at;
    }

    /** The tower {@code glandouille} is in, bottom first. */
    public static List<GlandouilleEntity> members(GlandouilleEntity glandouille) {
        List<GlandouilleEntity> list = new ArrayList<>();
        GlandouilleEntity at = bottom(glandouille);
        for (int guard = 0; guard < 1024 && at != null; guard++) {
            list.add(at);
            at = rider(at);
        }
        return list;
    }

    public static int height(GlandouilleEntity glandouille) {
        return members(glandouille).size();
    }

    /** Its place in its tower: 0 for the bottom one. */
    public static int level(GlandouilleEntity glandouille) {
        int level = 0;
        for (Entity at = glandouille; at.getVehicle() instanceof GlandouilleEntity below; at = below) level++;
        return level;
    }

    public static boolean sameTower(GlandouilleEntity glandouille, Entity other) {
        return other instanceof GlandouilleEntity otherOne && bottom(otherOne) == bottom(glandouille);
    }

    // ---------------------------------------------------------------- building

    /**
     * {@code climber} (alone, or the bottom one of a tower) climbs on top of {@code target}'s tower. Spontaneous
     * climbs stop at {@link #SPONTANEOUS_MAX}, every tower at {@link #maxStack()}.
     */
    public static boolean climb(GlandouilleEntity climber, GlandouilleEntity target, boolean spontaneous) {
        if (climber.hasVehicle() || climber.isBoardActor() || target.isBoardActor() || sameTower(climber, target)) return false;
        GlandouilleEntity bottom = bottom(target);
        if (spontaneous && (bottom.getVehicle() != null || hasRider(climber))) return false;
        int total = height(bottom) + height(climber);
        if (total > (spontaneous ? SPONTANEOUS_MAX : maxStack())) return false;
        GlandouilleEntity top = top(target);
        if (!climber.startRiding(top, true)) return false;
        climber.playSound(ModSounds.GLANDOUILLE_CLIMB, 1f, 1f + 0.05f * total);
        return true;
    }

    // ---------------------------------------------------------------- carried by a player

    /** The bottom one of the tower {@code player} carries, or null. */
    public static @Nullable GlandouilleEntity carried(PlayerEntity player) {
        for (Entity passenger : player.getPassengerList()) {
            if (passenger instanceof GlandouilleEntity glandouille) return glandouille;
        }
        return null;
    }

    /** {@code player} picks up the whole tower {@code any} is in (it rides the player's head). */
    public static boolean pickUp(PlayerEntity player, GlandouilleEntity any) {
        if (carried(player) != null || any.isBoardActor()) return false;
        GlandouilleEntity bottom = bottom(any);
        if (bottom.getVehicle() != null) return false;
        if (!bottom.startRiding(player, true)) return false;
        bottom.getNavigation().stop();
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.GLANDOUILLE_CLIMB,
                SoundCategory.PLAYERS, 1f, 0.8f);
        return true;
    }

    /** The tower {@code player} carries goes on top of {@code target}'s. False if it can't (too high, same tower). */
    public static boolean stackCarriedOn(PlayerEntity player, GlandouilleEntity target) {
        GlandouilleEntity carried = carried(player);
        if (carried == null || target.isBoardActor() || sameTower(carried, target) || bottom(target).getVehicle() == player) return false;
        if (height(target) + height(carried) > maxStack()) {
            player.sendMessage(Text.translatable("message.steveparty.glandouille.too_high", maxStack()), true);
            return false;
        }
        carried.stopRiding();
        if (climb(carried, target, false)) return true;
        carried.startRiding(player, true);
        return false;
    }

    /** Puts the carried tower down, standing at {@code at} (the bottom of the bottom one), facing {@code yaw}. */
    public static boolean putDown(PlayerEntity player, Vec3d at, float yaw) {
        GlandouilleEntity carried = carried(player);
        if (carried == null) return false;
        carried.stopRiding();
        carried.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0);
        carried.bodyYaw = yaw;
        carried.headYaw = yaw;
        carried.setVelocity(Vec3d.ZERO);
        player.getWorld().playSound(null, at.x, at.y, at.z, ModSounds.GLANDOUILLE_CLIMB, SoundCategory.PLAYERS, 1f, 0.7f);
        return true;
    }

    /** Right click on a block with an empty hand while carrying a tower: it is put down against that face. */
    public static void initialize() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != Hand.MAIN_HAND || !player.getMainHandStack().isEmpty() || player.isSpectator()) return ActionResult.PASS;
            if (carried(player) == null) return ActionResult.PASS;
            if (world.isClient) return ActionResult.SUCCESS;
            BlockPos place = hit.getBlockPos().offset(hit.getSide());
            Vec3d at = Vec3d.ofBottomCenter(place);
            putDown(player, at, player.getYaw() + 180f);
            return ActionResult.SUCCESS;
        });
    }

    // ---------------------------------------------------------------- flick, collapse

    /**
     * {@code hit} (inside a tower, not its bottom one) is shot out along {@code dir}; the one above it (and its own
     * riders) comes down onto the one below.
     */
    public static boolean flick(GlandouilleEntity hit, Vec3d dir) {
        if (!(hit.getVehicle() instanceof GlandouilleEntity below)) return false;
        GlandouilleEntity above = rider(hit);
        hit.stopRiding();
        if (above != null) {
            above.stopRiding();
            above.startRiding(below, true);
        }
        Vec3d flat = new Vec3d(dir.x, 0, dir.z);
        if (flat.lengthSquared() < 1.0E-6) flat = Vec3d.fromPolar(0, hit.getYaw());
        hit.launch(flat.normalize());
        return true;
    }

    /**
     * The bottom one charged into a wall: the tower falls apart, everyone fanned out away from the wall
     * ({@code away}), all dizzy.
     */
    public static void collapse(GlandouilleEntity bottom, Vec3d away) {
        List<GlandouilleEntity> members = members(bottom);
        for (int i = members.size() - 1; i > 0; i--) members.get(i).stopRiding();
        int n = members.size();
        double base = MathHelper.atan2(away.z, away.x);
        for (int i = 0; i < n; i++) {
            GlandouilleEntity one = members.get(i);
            double spread = n == 1 ? 0 : ((double) i / (n - 1) - 0.5) * Math.toRadians(150);
            double angle = base + spread;
            double push = 0.2 + 0.35 * i / Math.max(1, n - 1);
            one.setVelocity(Math.cos(angle) * push, 0.25 + 0.04 * Math.min(i, 8), Math.sin(angle) * push);
            one.velocityModified = true;
            one.stun(one.getVariant().stunTicks + 10);
        }
        bottom.playSound(ModSounds.GLANDOUILLE_COLLAPSE, 1f, 1f);
    }

    // ---------------------------------------------------------------- the Mulas

    /** A dancing Mula charged at gets away with a pirouette (a little hop aside), every time. */
    public static void mulaDodge(MulaEntity mula, Vec3d chargeDir) {
        Vec3d side = new Vec3d(-chargeDir.z, 0, chargeDir.x);
        if (mula.getRandom().nextBoolean()) side = side.multiply(-1);
        mula.playAnimation("twirl");
        mula.addVelocity(side.x * 0.7, 0.35, side.z * 0.7);
        mula.velocityModified = true;
    }
}
