package fr.lordfinn.steveparty.entities.custom.glandouille;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.config.ServerConfig;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.payloads.Payloads;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.Arm;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
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
 *     <li><b>Carried</b>: right click with an empty hand on one of a tower picks it up with everyone above it, the ones
 *     below staying where they are (a lone one is picked up alone). The stack is held in the main hand, low and to
 *     that side like an item, so the crosshair stays clear ({@link #heldPos}). Right click with an empty hand on another tower: the carried one goes on top of
 *     it; on a block: it is put down there. A left click throws the bottom one of the stack from the hand to where
 *     the crosshair aims, shot like a flicked one ({@link #throwCarried}). A carrier hit by anyone drops it in front of him ({@link #drop}). Built that
 *     way, a tower has no height limit but the server's safety one ({@link ServerConfig#glandouilleMaxStack}).</li>
 *     <li><b>Flick</b>: hitting one inside a tower (not the bottom one) shoots it out like a missile along the blow,
 *     alone: the ones above it hop straight up and come back down onto the one below ({@link #hopOff}). The bottom one
 *     hit goes alone too, its tower hopping off it and landing on the ground.</li>
 *     <li><b>Impacts</b>: a flicked one flying into another tower (or a lone one) lands on top of it; a sliding one
 *     carries what it slides into on top of itself and slides on, a little slower per acorn; a tower standing on the old
 *     mossy one is too heavy: the slider stops and climbs on it. Over the safety limit, they are shoved as before.</li>
 *     <li><b>Collapse</b>: only when the bottom one charges into a wall: everyone falls, fanned out, dizzy.</li>
 * </ul>
 */
public final class GlandouilleTowers {
    /** Highest tower Glandouilles build by themselves. */
    public static final int SPONTANEOUS_MAX = 5;
    /**
     * A carried stack stands in the main hand: this high up the player (his hand), this far in front of his body and
     * this far to the main hand's side (blocks).
     */
    public static final double HOLD_HEIGHT = 0.38, HOLD_FORWARD = 0.15, HOLD_SIDE = 0.62;
    /** A throw goes to what the crosshair aims at, or this far along the look if nothing (blocks). */
    public static final double AIM_RANGE = 64;
    /** Aimed closer than this (blocks), a throw goes the way he looks rather than from the hand to the aimed point. */
    public static final double AIM_MIN_DISTANCE = 3;

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
        // climbed on: whoever was napping under it wakes up
        for (GlandouilleEntity one : members(bottom)) one.handled();
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

    /**
     * {@code player} picks up {@code clicked} with everyone above it (it rides the player, held in front of him); the
     * ones below it stay standing where they are.
     */
    public static boolean pickUp(PlayerEntity player, GlandouilleEntity clicked) {
        if (carried(player) != null || clicked.isBoardActor() || bottom(clicked).getVehicle() != null) return false;
        Entity below = clicked.getVehicle();
        if (below != null) clicked.stopRiding();
        if (!clicked.startRiding(player, true)) {
            if (below != null) clicked.startRiding(below, true);
            return false;
        }
        clicked.getNavigation().stop();
        clicked.setVelocity(Vec3d.ZERO);
        for (GlandouilleEntity one : members(clicked)) one.handled();
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.GLANDOUILLE_CLIMB,
                SoundCategory.PLAYERS, 1f, 0.8f);
        return true;
    }

    /** Where a carried stack stands: in {@code player}'s main hand, low and to that side, the way he looks. */
    public static Vec3d heldPos(PlayerEntity player, GlandouilleEntity carried) {
        Vec3d hand = handOffset(player);
        return player.getPos().add(hand.x, player.getHeight() * HOLD_HEIGHT, hand.z);
    }

    /** From {@code player}'s feet to his main hand, flat: a little forward, to the main hand's side. */
    private static Vec3d handOffset(PlayerEntity player) {
        float yaw = player.getYaw();
        Vec3d front = Vec3d.fromPolar(0, yaw).multiply(player.getWidth() * 0.5 + HOLD_FORWARD);
        // the right of where he looks is his yaw + 90 degrees
        Vec3d side = Vec3d.fromPolar(0, yaw + (player.getMainArm() == Arm.RIGHT ? 90f : -90f)).multiply(HOLD_SIDE);
        return front.add(side);
    }

    /** What {@code player}'s crosshair aims at: the entity or block hit, else a point {@link #AIM_RANGE} away. */
    public static Vec3d aimPoint(PlayerEntity player) {
        return aim(player).getPos();
    }

    /** What {@code player}'s crosshair aims at (an entity: its middle), a miss {@link #AIM_RANGE} away if nothing. */
    private static HitResult aim(PlayerEntity player) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1f);
        Vec3d end = eye.add(look.multiply(AIM_RANGE));
        BlockHitResult block = player.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player));
        Vec3d far = block.getType() == HitResult.Type.MISS ? end : block.getPos();
        EntityHitResult entity = ProjectileUtil.raycast(player, eye, far, player.getBoundingBox().stretch(far.subtract(eye)).expand(1),
                e -> !e.isSpectator() && e.canHit() && e.getRootVehicle() != player.getRootVehicle(), eye.squaredDistanceTo(far));
        return entity != null ? new EntityHitResult(entity.getEntity(), entity.getEntity().getBoundingBox().getCenter()) : block;
    }

    /** {@code at} if {@code glandouille} fits there, else {@code fallback}. */
    private static Vec3d freeOr(GlandouilleEntity glandouille, Vec3d at, Vec3d fallback) {
        Box box = glandouille.getDimensions(glandouille.getPose()).getBoxAt(at);
        return glandouille.getWorld().isSpaceEmpty(glandouille, box) ? at : fallback;
    }

    /** {@code player} was hit: the stack he carries falls on the ground under his hand, still stacked. */
    public static boolean drop(PlayerEntity player) {
        GlandouilleEntity carried = carried(player);
        if (carried == null) return false;
        Vec3d hand = handOffset(player);
        Vec3d at = freeOr(carried, player.getPos().add(hand.x, 0, hand.z), player.getPos());
        return putDown(player, at, player.getYaw());
    }

    /**
     * Left click with a stack in hand: its bottom one leaves the hand, a little forward, and flies to where the
     * crosshair aims, shot like a flicked one (it lands on a tower it hits, dazes a player); the ones above stay in
     * hand. False if nothing is carried.
     */
    public static boolean throwCarried(PlayerEntity player) {
        GlandouilleEntity thrown = carried(player);
        if (thrown == null) return false;
        GlandouilleEntity above = rider(thrown);
        if (above != null) above.stopRiding();
        thrown.stopRiding();
        if (above != null) above.startRiding(player, true);
        Vec3d held = heldPos(player, thrown);
        // out of the hand, clear of his body
        Vec3d ahead = held.add(Vec3d.fromPolar(0, player.getYaw()).multiply(thrown.getWidth() * 0.5));
        Vec3d at = freeOr(thrown, ahead, freeOr(thrown, held, player.getPos().add(0, player.getHeight() * HOLD_HEIGHT, 0)));
        thrown.refreshPositionAndAngles(at.x, at.y, at.z, player.getYaw(), 0);
        thrown.thrownBy(player);
        // from the hand toward what the crosshair aims at; aimed at something too close, the hand being off to the side,
        // that would throw it sideways: it goes the way he looks
        HitResult hit = aim(player);
        Vec3d target = hit.getPos();
        // aimed at the top of a block (the ground), its feet go there; else its middle
        boolean ground = hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                && blockHit.getSide() == Direction.UP;
        Vec3d aim = target.subtract(at.add(0, ground ? 0 : thrown.getHeight() * 0.5, 0));
        boolean tooClose = target.squaredDistanceTo(player.getEyePos()) < AIM_MIN_DISTANCE * AIM_MIN_DISTANCE;
        thrown.launchThrown(tooClose || aim.lengthSquared() < 1.0E-4 ? player.getRotationVec(1f) : aim);
        player.swingHand(Hand.MAIN_HAND, true);
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
        for (GlandouilleEntity one : members(carried)) one.handled();
        player.getWorld().playSound(null, at.x, at.y, at.z, ModSounds.GLANDOUILLE_CLIMB, SoundCategory.PLAYERS, 1f, 0.7f);
        return true;
    }

    /**
     * Right click on a block with an empty hand while carrying a tower: it is put down against that face. A carrier hit
     * by anyone drops it; his left click throws one ({@link ThrowCarried}, sent by the client).
     */
    public static void initialize() {
        Payloads.c2s(ThrowCarried.ID, ThrowCarried.CODEC, (player, payload) -> throwCarried(player));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (entity instanceof PlayerEntity player && !blocked && (source.getAttacker() != null || source.getSource() != null)) {
                drop(player);
            }
        });
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

    /** The client's left click with a stack in hand: throw its bottom one. */
    public record ThrowCarried() implements CustomPayload {
        public static final Id<ThrowCarried> ID = new Id<>(Steveparty.id("glandouille_throw"));
        public static final PacketCodec<PacketByteBuf, ThrowCarried> CODEC = PacketCodec.unit(new ThrowCarried());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // ---------------------------------------------------------------- impacts: a shot one, a sliding one

    /**
     * A tower (or a lone one) an impact may stack with: on its own feet (not carried by a player, nor hopping, nor
     * shot), not a board actor.
     */
    private static boolean stackable(GlandouilleEntity glandouille) {
        if (glandouille.isBoardActor() || !glandouille.isAlive()) return false;
        GlandouilleEntity bottom = bottom(glandouille);
        if (bottom.getVehicle() != null) return false;
        for (GlandouilleEntity one : members(bottom)) {
            GlandouilleEntity.Mood mood = one.getMood();
            if (mood == GlandouilleEntity.Mood.FLYING || mood == GlandouilleEntity.Mood.HOPPING) return false;
        }
        return true;
    }

    /**
     * {@code shot} (flying out of a tower, alone) ran into {@code hit}: it lands on top of that tower and stays there.
     * False if it can't (too high, a board actor, a tower in the air, or one knocked about: dizzy, flying, sliding,
     * hopping, like the one thrown just before, which another throw the same way reaches): it is shoved as before.
     */
    public static boolean joinOnImpact(GlandouilleEntity shot, GlandouilleEntity hit) {
        if (!stackable(hit) || sameTower(shot, hit)) return false;
        GlandouilleEntity.Mood mood = bottom(hit).getMood();
        if (mood == GlandouilleEntity.Mood.STUNNED || mood == GlandouilleEntity.Mood.FLYING
                || mood == GlandouilleEntity.Mood.SLIDING || mood == GlandouilleEntity.Mood.HOPPING) return false;
        return climb(shot, hit, false);
    }

    /**
     * {@code slider} slid into {@code other}: the lowest one of {@code other}'s tower it touches (with everyone above it)
     * climbs on top of the slider, which slides on carrying them. A tower standing on the old mossy one is too heavy to
     * carry (alone too): the slider stops and climbs on top of it instead. Returns the number of acorns added to the
     * slider's tower (0 if it stopped on a mossy one), -1 if nothing happened (too high, a board actor, a tower in the
     * air): it is shoved as before.
     */
    public static int carryOnImpact(GlandouilleEntity slider, GlandouilleEntity other, Box reach) {
        if (!stackable(other) || sameTower(slider, other)) return -1;
        GlandouilleEntity bottom = bottom(other);
        if (bottom.getVariant() == GlandouilleVariant.MOSSY) return climb(slider, bottom, false) ? 0 : -1;
        GlandouilleEntity hit = other;
        for (GlandouilleEntity one : members(bottom)) {
            if (one.getBoundingBox().intersects(reach)) {
                hit = one;
                break;
            }
        }
        if (height(slider) + height(hit) - level(hit) > maxStack()) return -1;
        Entity below = hit.getVehicle();
        if (below != null) hit.stopRiding();
        if (climb(hit, slider, false)) {
            hit.setVelocity(Vec3d.ZERO);
            return height(hit) - level(hit);
        }
        if (below != null) hit.startRiding(below, true);
        return -1;
    }

    // ---------------------------------------------------------------- flick, collapse

    /**
     * {@code hit} (inside a tower, not its bottom one) is shot out alone along {@code dir}; the ones above it hop off
     * and come back down onto the one below.
     */
    public static boolean flick(GlandouilleEntity hit, Vec3d dir) {
        if (!(hit.getVehicle() instanceof GlandouilleEntity below)) return false;
        hit.leaveTower();
        hopOff(hit, below);
        hit.stopRiding();
        Vec3d flat = new Vec3d(dir.x, 0, dir.z);
        if (flat.lengthSquared() < 1.0E-6) flat = Vec3d.fromPolar(0, hit.getYaw());
        hit.launch(flat.normalize());
        return true;
    }

    /**
     * The ones above {@code hit} let go of it (it is hit away, alone): they hop straight up together and land back on
     * the tower of {@code onto} (the one that was under {@code hit}), or on the ground when there is none. False if
     * nobody rides {@code hit}.
     */
    public static boolean hopOff(GlandouilleEntity hit, @Nullable GlandouilleEntity onto) {
        GlandouilleEntity above = rider(hit);
        if (above == null) return false;
        above.stopRiding();
        above.hop(onto);
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
