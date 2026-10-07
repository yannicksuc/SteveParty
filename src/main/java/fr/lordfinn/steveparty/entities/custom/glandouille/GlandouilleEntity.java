package fr.lordfinn.steveparty.entities.custom.glandouille;

import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.particles.MulaSparkleEffect;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Glandouille: a grumpy little acorn. It never hurts anything, it shoves.
 * <ul>
 *     <li><b>Charge</b>: a player stepping into its zone ({@link #ZONE} blocks), one it holds a grudge against
 *     ({@link #GRUDGE_ZONE}), or a dancing Mula: it stomps twice, its stem rises, brows down ({@link #TELEGRAPH_TICKS}),
 *     then it runs straight ahead without turning. What it meets is shoved hard, without damage; a wall stuns it.</li>
 *     <li><b>Stomped</b> on its cap: flattened ("pouic"), the stomper bounces off; it pops back up sulking. The next
 *     stomp finishes it (see {@link GlandouilleVariant#flattenAt} / {@link GlandouilleVariant#dieAt}).</li>
 *     <li><b>Hit</b>: pushed, angry, it remembers who did it for {@link #GRUDGE_TICKS} (the frosty one slides
 *     instead, see {@link #startSlide}; one inside a tower is flicked out of it, see {@link GlandouilleTowers}). Only the
 *     one hit goes: the ones above it hop off and come back down ({@link #hop}).</li>
 *     <li><b>Towers</b>: see {@link GlandouilleTowers}. Only the bottom one thinks: the ones above are passengers.</li>
 *     <li><b>Hat</b>: a hard crash may, very rarely, send its cap flying ({@link #HAT_LOSS_CHANCE}), never while it
 *     carries a tower. Hatless, it is shy: it flees players and looks for its cap.</li>
 *     <li><b>Lazy</b>: at night, in the rain or now and then, it naps by the nearest leaves on the ground; woken by a
 *     player, it charges at once.</li>
 * </ul>
 * The Glandouilles of a board space ({@link #isBoardActor()}) do none of that: invulnerable, moved by the board, never
 * saved.
 */
public class GlandouilleEntity extends PathAwareEntity implements GeoEntity {
    /** Every Glandouille's size relative to its model (hitbox and drawing): a little thing. */
    public static final float SIZE = 0.6f;
    /** The classic one's hitbox (its model's 12 px wide cap, 15 px up to the top of the cap, at {@link #SIZE}); each variant has its own. */
    public static final float MODEL_WIDTH = 0.75f * SIZE, MODEL_HEIGHT = 0.9375f * SIZE;
    /** A player closer than this gets charged. */
    public static final double ZONE = 4.0;
    /** The player it holds a grudge against gets charged from this far. */
    public static final double GRUDGE_ZONE = 10.0;
    /** A dancing Mula this close gets charged. */
    public static final double MULA_ZONE = 8.0;
    public static final int GRUDGE_TICKS = 20 * 60 * 3;
    public static final int TELEGRAPH_TICKS = 16;
    public static final int CHARGE_MAX_TICKS = 40;
    public static final int CHARGE_COOLDOWN_TICKS = 80;
    public static final int FLAT_TICKS = 50, REINFLATE_TICKS = 24, SULK_TICKS = 80;
    public static final int FLIGHT_TICKS = 30;
    /** Let go of by the one under it (hit away): it hops straight up this hard, and gives up landing on a tower after {@link #HOP_TICKS}. */
    public static final double HOP_VELOCITY = 0.45;
    public static final int HOP_TICKS = 50;
    /** Hit out of a tower, it leaves its old tower mates alone for that long (they hop off, it goes away alone). */
    public static final int SPARE_TICKS = 30;
    public static final int BONE_MEAL_COOLDOWN_TICKS = 1200;
    public static final float BONE_MEAL_ACORN_CHANCE = 0.4f;
    /** A crash or a hit of its charge sends its cap flying, very rarely (never while it carries a tower). */
    public static final float HAT_LOSS_CHANCE = 0.03f;
    /** Spawned without its cap, rarely. */
    public static final float HATLESS_SPAWN_CHANCE = 0.03f;
    /** How hard its charge (and a flicked one) shoves. */
    public static final double SHOVE = 1.6;

    public enum Mood {
        CALM, TELEGRAPH, CHARGING, STUNNED, FLAT, REINFLATE, SULK, SLEEPING, FLYING, SLIDING, PUSH_FAIL, HOPPING;

        static Mood byId(int id) {
            Mood[] values = values();
            return id >= 0 && id < values.length ? values[id] : CALM;
        }
    }

    private static final TrackedData<Integer> VARIANT =
            DataTracker.registerData(GlandouilleEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> MOOD =
            DataTracker.registerData(GlandouilleEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> HAT =
            DataTracker.registerData(GlandouilleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation CHARGE = RawAnimation.begin().thenLoop("charge");
    private static final RawAnimation TELEGRAPH = RawAnimation.begin().thenPlayAndHold("telegraph");
    private static final RawAnimation STUNNED = RawAnimation.begin().thenLoop("stunned");
    private static final RawAnimation FLAT = RawAnimation.begin().thenPlayAndHold("flat");
    private static final RawAnimation REINFLATE = RawAnimation.begin().thenPlayAndHold("reinflate");
    private static final RawAnimation SULK = RawAnimation.begin().thenLoop("sulk");
    private static final RawAnimation SLEEP = RawAnimation.begin().thenLoop("sleep");
    private static final RawAnimation CARRIED = RawAnimation.begin().thenLoop("carried");
    private static final RawAnimation STACKED = RawAnimation.begin().thenLoop("stacked");
    private static final RawAnimation SHY = RawAnimation.begin().thenLoop("shy");
    private static final RawAnimation PUSH_FAIL = RawAnimation.begin().thenPlayAndHold("push_fail");
    public static final String MAIN_CONTROLLER = "main", FX_CONTROLLER = "fx";

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    // ---------------------------------------------------------------- server state
    private int moodTicks;
    private int chargeTicks;
    private Vec3d chargeDir = Vec3d.ZERO;
    private @Nullable Entity chargeTarget;
    private boolean mulaDodged;
    private int chargeCooldown;
    private int stomps;
    private int stompCooldown;
    private @Nullable UUID grudge;
    private long grudgeUntil;
    private int boneMealCooldown;
    private int hatPopTicks = -1;
    private boolean boardActor;
    private boolean squashKill;
    private boolean variantFromData;
    private Vec3d flyDir = Vec3d.ZERO;
    private Vec3d slideVelocity = Vec3d.ZERO;
    private int slideRelaunches;
    /** Hopping off a tower: the tower it lands back on (null: the ground). */
    private @Nullable GlandouilleEntity hopOnto;
    /** Its old tower mates, left alone (never shoved) until {@link #sparedUntil}. */
    private List<GlandouilleEntity> spared = List.of();
    private long sparedUntil;
    /** The height of the players around it last tick, to see them come down on its cap. */
    private final Map<UUID, Double> playerY = new HashMap<>(2);

    // ---------------------------------------------------------------- client state
    /** How far its brows are lowered into a frown (0 calm: a gentle one, 1 angry), last tick and now. */
    public float prevBrows, brows;
    /** How much the tower it carries sways (0..1), last tick and now. */
    public float prevSway, sway;

    public GlandouilleEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = 2;
    }

    public static DefaultAttributeContainer.Builder setAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 6.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, GlandouilleVariant.CLASSIC.speed)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new GlandouilleGoals.FleeWithoutHat(this));
        this.goalSelector.add(2, new GlandouilleGoals.SeekHat(this));
        this.goalSelector.add(3, new GlandouilleGoals.Nap(this));
        this.goalSelector.add(4, new GlandouilleGoals.Climb(this));
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 1.0) {
            @Override
            public boolean canStart() {
                return isFree() && !isAnchored() && super.canStart();
            }

            @Override
            public boolean shouldContinue() {
                return isFree() && !isAnchored() && super.shouldContinue();
            }
        });
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 6.0f) {
            @Override
            public boolean canStart() {
                return isFree() && super.canStart();
            }
        });
        this.goalSelector.add(7, new LookAroundGoal(this) {
            @Override
            public boolean canStart() {
                return isFree() && super.canStart();
            }
        });
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(VARIANT, 0);
        builder.add(MOOD, 0);
        builder.add(HAT, true);
    }

    // ---------------------------------------------------------------- variant, hat, mood

    public GlandouilleVariant getVariant() {
        return GlandouilleVariant.byId(this.dataTracker.get(VARIANT));
    }

    public void setVariant(GlandouilleVariant variant) {
        this.dataTracker.set(VARIANT, variant.ordinal());
        EntityAttributeInstance speed = getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(variant.speed);
        calculateDimensions();
    }

    public boolean hasHat() {
        return this.dataTracker.get(HAT);
    }

    public void setHat(boolean hat) {
        this.dataTracker.set(HAT, hat);
    }

    public Mood getMood() {
        return Mood.byId(this.dataTracker.get(MOOD));
    }

    private void setMood(Mood mood, int ticks) {
        this.dataTracker.set(MOOD, mood.ordinal());
        this.moodTicks = ticks;
        if (mood != Mood.CALM) {
            getNavigation().stop();
            setForwardSpeed(0);
        }
    }

    /** Free to wander, look around, climb, nap: calm, not riding anything, not a board actor. */
    public boolean isFree() {
        return getMood() == Mood.CALM && !hasVehicle() && !boardActor;
    }

    /** An old mossy one carrying a tower: it does not move at all. */
    public boolean isAnchored() {
        return getVariant() == GlandouilleVariant.MOSSY && GlandouilleTowers.hasRider(this);
    }

    public int stomps() {
        return stomps;
    }

    public @Nullable UUID grudge() {
        return grudge != null && getWorld().getTime() < grudgeUntil ? grudge : null;
    }

    @Override
    public float getScaleFactor() {
        return getVariant().scale;
    }

    /** Its variant's model: its own width, height and eyes (a tower's floors stand on the cap of the one below). */
    @Override
    public EntityDimensions getBaseDimensions(net.minecraft.entity.EntityPose pose) {
        return getVariant().dimensions().scaled(getScaleFactor());
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        if (VARIANT.equals(data)) calculateDimensions();
        super.onTrackedDataSet(data);
    }

    // ---------------------------------------------------------------- board actors

    /** A Glandouille of a board space: invulnerable, no will of its own, never saved. */
    public boolean isBoardActor() {
        return boardActor;
    }

    public void makeBoardActor() {
        this.boardActor = true;
        setInvulnerable(true);
        setAiDisabled(true);
        setNoGravity(true);
        setPersistent();
    }

    /** Board actors: what they act out (the charge of a tower walking the path, the lone one's failed push). */
    public void actOut(Mood mood) {
        if (boardActor) this.dataTracker.set(MOOD, mood.ordinal());
    }

    @Override
    public boolean shouldSave() {
        if (boardActor) return false;
        // carried by a player: saved where it is (with the tower above it), it would be lost with its vehicle else
        if (getVehicle() instanceof PlayerEntity) return !isRemoved();
        return super.shouldSave();
    }

    // ---------------------------------------------------------------- riding (towers)

    /** The bottom one keeps the tower: the one above never steers it. */
    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return null;
    }

    /** Only the bottom one of a tower thinks; the others ride. */
    @Override
    public boolean isAiDisabled() {
        return super.isAiDisabled() || hasVehicle();
    }

    @Override
    protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        // on top of its cap, sunk a little into it
        return new Vec3d(0, dimensions.height() - 0.05 * scaleFactor, 0);
    }

    @Override
    public boolean isPushable() {
        return !isAnchored() && !boardActor && super.isPushable();
    }

    @Override
    public void takeKnockback(double strength, double x, double z) {
        if (isAnchored() || boardActor) return;
        super.takeKnockback(strength, x, z);
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        Entity vehicle = getVehicle();
        if (vehicle != null) {
            // a tower faces the way its bottom one does; a carried one faces with the player
            float yaw = vehicle instanceof LivingEntity living ? living.bodyYaw : vehicle.getYaw();
            setYaw(yaw);
            this.bodyYaw = yaw;
            this.headYaw = yaw;
        }
        if (getWorld().isClient) {
            tickClient();
        } else {
            tickServer((ServerWorld) getWorld());
        }
    }

    private void tickClient() {
        prevBrows = brows;
        Mood mood = getMood();
        float target = switch (mood) {
            case TELEGRAPH, CHARGING, SULK, FLYING, PUSH_FAIL -> 1f;
            case STUNNED, FLAT, REINFLATE -> 0.4f;
            default -> 0f;
        };
        brows += (target - brows) * 0.35f;
        prevSway = sway;
        double speed = getVelocity().horizontalLength();
        if (getVehicle() == null && GlandouilleTowers.hasRider(this)) {
            float wanted = (float) Math.min(1.0, speed * 7.0 + 0.12) * getVariant().sway;
            sway += (wanted - sway) * 0.1f;
        } else {
            sway *= 0.9f;
        }
    }

    private void tickServer(ServerWorld world) {
        if (boardActor) return;
        if (chargeCooldown > 0) chargeCooldown--;
        if (stompCooldown > 0) stompCooldown--;
        if (boneMealCooldown > 0) boneMealCooldown--;
        if (hatPopTicks > 0 && --hatPopTicks == 0) dropHat(world);

        Entity vehicle = getVehicle();
        if (vehicle != null) {
            // a tower member: nothing of its own but being stomped on (the top one)
            if (!(vehicle instanceof PlayerEntity)) detectStomps(world);
            if (getMood() != Mood.CALM && getMood() != Mood.FLAT && getMood() != Mood.REINFLATE) setMood(Mood.CALM, 0);
            else if (getMood() != Mood.CALM) tickMood(world);
            return;
        }
        detectStomps(world);
        tickMood(world);
    }

    private void tickMood(ServerWorld world) {
        Mood mood = getMood();
        switch (mood) {
            case CALM -> {
                if (!hasHat() || (this.age + getId()) % 5 != 0) break;
                Entity target = findTarget(world);
                if (target != null) startTelegraph(target);
            }
            case TELEGRAPH -> {
                setVelocity(0, getVelocity().y, 0);
                if (chargeTarget != null && chargeTarget.isAlive()) lookAt(chargeTarget);
                int elapsed = TELEGRAPH_TICKS - moodTicks;
                if (elapsed == 4 || elapsed == 9) playSound(ModSounds.GLANDOUILLE_STOMP, 0.9f, 0.9f + random.nextFloat() * 0.2f);
                if (--moodTicks <= 0) launchCharge();
            }
            case CHARGING -> tickCharge(world);
            case STUNNED -> {
                setVelocity(getVelocity().multiply(0.6, 1, 0.6));
                if (moodTicks % 4 == 0) dizzyStars(world);
                if (--moodTicks <= 0) setMood(Mood.CALM, 0);
            }
            case FLAT -> {
                setVelocity(0, getVelocity().y, 0);
                if (--moodTicks <= 0) {
                    setMood(Mood.REINFLATE, REINFLATE_TICKS);
                    playSound(ModSounds.GLANDOUILLE_REINFLATE, 1f, 1f);
                }
            }
            case REINFLATE -> {
                if (--moodTicks <= 0) {
                    setMood(Mood.SULK, SULK_TICKS);
                    playSound(ModSounds.GLANDOUILLE_SULK, 0.8f, 1f);
                }
            }
            case SULK -> {
                if (--moodTicks <= 0) setMood(Mood.CALM, 0);
            }
            case SLEEPING -> tickSleep(world);
            case FLYING -> tickFlight(world);
            case SLIDING -> tickSlide(world);
            case PUSH_FAIL -> {
                if (--moodTicks <= 0) setMood(Mood.CALM, 0);
            }
            case HOPPING -> tickHop();
        }
    }

    // ---------------------------------------------------------------- the charge

    /** Whom it charges now, or null: a grudge first, then a player in its zone, then a dancing Mula. */
    private @Nullable Entity findTarget(ServerWorld world) {
        if (!getVariant().charges() || chargeCooldown > 0 || boardActor) return null;
        UUID grudged = grudge();
        if (grudged != null) {
            PlayerEntity player = world.getPlayerByUuid(grudged);
            if (fair(player) && squaredDistanceTo(player) < GRUDGE_ZONE * GRUDGE_ZONE && canSee(player)) return player;
        }
        PlayerEntity nearest = null;
        double best = ZONE * ZONE;
        for (PlayerEntity player : world.getPlayers()) {
            double d = squaredDistanceTo(player);
            if (d < best && fair(player) && canSee(player)) {
                best = d;
                nearest = player;
            }
        }
        if (nearest != null) return nearest;
        List<MulaEntity> mulas = world.getEntitiesByClass(MulaEntity.class, getBoundingBox().expand(MULA_ZONE),
                mula -> mula.isDancing() && canSee(mula));
        return mulas.isEmpty() ? null : mulas.getFirst();
    }

    /** A player it may charge: alive, playing (not in creative nor spectating). */
    private static boolean fair(@Nullable PlayerEntity player) {
        return player != null && player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    private void lookAt(Entity target) {
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90f;
        setYaw(yaw);
        this.bodyYaw = yaw;
        this.headYaw = yaw;
    }

    /** Stomps twice, raises its stem, brows down: then it charges {@code target}. False if it never charges. */
    public boolean startTelegraph(Entity target) {
        if (!getVariant().charges() || !hasHat() || hasVehicle() || boardActor) return false;
        this.chargeTarget = target;
        lookAt(target);
        setMood(Mood.TELEGRAPH, TELEGRAPH_TICKS);
        playSound(ModSounds.GLANDOUILLE_GROWL, 0.8f, 1f);
        return true;
    }

    private void launchCharge() {
        Vec3d dir;
        if (chargeTarget != null && chargeTarget.isAlive()) {
            dir = new Vec3d(chargeTarget.getX() - getX(), 0, chargeTarget.getZ() - getZ());
        } else {
            dir = Vec3d.fromPolar(0, getYaw());
        }
        startCharge(dir);
    }

    /**
     * Runs straight along {@code direction} (horizontal) at once, without turning: the whole tower on it with it.
     * False if it never charges (the mossy one) or cannot now.
     */
    public boolean startCharge(Vec3d direction) {
        if (!getVariant().charges() || hasVehicle() || boardActor) return false;
        Vec3d flat = new Vec3d(direction.x, 0, direction.z);
        if (flat.lengthSquared() < 1.0E-6) return false;
        this.chargeDir = flat.normalize();
        float yaw = (float) (MathHelper.atan2(chargeDir.z, chargeDir.x) * MathHelper.DEGREES_PER_RADIAN) - 90f;
        setYaw(yaw);
        this.bodyYaw = yaw;
        this.headYaw = yaw;
        this.chargeTicks = 0;
        this.mulaDodged = false;
        setMood(Mood.CHARGING, CHARGE_MAX_TICKS);
        return true;
    }

    private void tickCharge(ServerWorld world) {
        chargeTicks++;
        float yaw = (float) (MathHelper.atan2(chargeDir.z, chargeDir.x) * MathHelper.DEGREES_PER_RADIAN) - 90f;
        setYaw(yaw);
        this.bodyYaw = yaw;
        this.headYaw = yaw;
        double speed = getVariant().chargeSpeed;
        setVelocity(chargeDir.x * speed, getVelocity().y, chargeDir.z * speed);
        if (chargeTicks % 3 == 0) {
            world.spawnParticles(ParticleTypes.POOF, getX(), getY() + 0.1, getZ(), 1, 0.1, 0.02, 0.1, 0.0);
        }

        // a dancing Mula always gets away, with a pirouette
        if (!mulaDodged) {
            for (MulaEntity mula : world.getEntitiesByClass(MulaEntity.class, getBoundingBox().expand(1.6), MulaEntity::isDancing)) {
                GlandouilleTowers.mulaDodge(mula, chargeDir);
                mulaDodged = true;
            }
        }
        boolean hit = false;
        for (LivingEntity other : world.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(0.2),
                e -> e != this && e.isAlive() && !e.isSpectator() && !GlandouilleTowers.sameTower(this, e) && !spares(e)
                        && !(e instanceof MulaEntity mula && mula.isDancing()))) {
            shove(other, chargeDir, SHOVE);
            hit = true;
        }
        if (hit) {
            playSound(ModSounds.GLANDOUILLE_RAM, 1f, 1f);
            if (!GlandouilleTowers.hasRider(this) && random.nextFloat() < HAT_LOSS_CHANCE) popHat();
            setMood(Mood.CALM, 0);
            chargeCooldown = CHARGE_COOLDOWN_TICKS;
            setVelocity(getVelocity().multiply(0.2, 1, 0.2));
            return;
        }
        if (chargeTicks > 2 && this.horizontalCollision) {
            bonk(world);
            return;
        }
        if (--moodTicks <= 0) {
            setMood(Mood.CALM, 0);
            chargeCooldown = CHARGE_COOLDOWN_TICKS;
        }
    }

    /** Shoves {@code other} along {@code dir}: knockback only, never damage. */
    public static void shove(LivingEntity other, Vec3d dir, double strength) {
        if (other instanceof GlandouilleEntity glandouille && glandouille.isBoardActor()) return;
        // takeKnockback pushes away from (x, z): the opposite of where it goes
        other.takeKnockback(strength, -dir.x, -dir.z);
        other.addVelocity(0, 0.2, 0);
        other.velocityModified = true;
    }

    /** Its charge ended in a wall: dizzy (a tower on it falls down). */
    private void bonk(ServerWorld world) {
        playSound(ModSounds.GLANDOUILLE_BONK, 1f, 1f);
        world.spawnParticles(ParticleTypes.CRIT, getX() + chargeDir.x * 0.4, getY() + getHeight() * 0.6,
                getZ() + chargeDir.z * 0.4, 8, 0.2, 0.2, 0.2, 0.2);
        chargeCooldown = CHARGE_COOLDOWN_TICKS;
        if (GlandouilleTowers.hasRider(this)) {
            GlandouilleTowers.collapse(this, chargeDir.multiply(-1));
            return;
        }
        if (random.nextFloat() < HAT_LOSS_CHANCE) popHat();
        setVelocity(-chargeDir.x * 0.25, 0.25, -chargeDir.z * 0.25);
        stun();
    }

    /** Dizzy for its variant's time: stars around its head, spinning pupils. */
    public void stun() {
        stun(getVariant().stunTicks);
    }

    public void stun(int ticks) {
        if (boardActor) return;
        setMood(Mood.STUNNED, ticks);
    }

    private void dizzyStars(ServerWorld world) {
        double angle = this.age * 0.6;
        double r = 0.35 * getScaleFactor();
        double y = getY() + getHeight() + 0.15;
        for (int i = 0; i < 3; i++) {
            double a = angle + i * Math.PI * 2 / 3;
            world.spawnParticles(new MulaSparkleEffect(0xFFE066, 0.7f, MulaSparkleEffect.STAR_BIT),
                    getX() + Math.cos(a) * r, y, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    // ---------------------------------------------------------------- stomped

    /** Players coming down on its cap (the top one of a tower only: the others' caps are under a Glandouille). */
    private void detectStomps(ServerWorld world) {
        if (GlandouilleTowers.hasRider(this)) {
            playerY.clear();
            return;
        }
        Box box = getBoundingBox();
        playerY.keySet().removeIf(uuid -> {
            PlayerEntity player = world.getPlayerByUuid(uuid);
            return player == null || player.squaredDistanceTo(this) > 25;
        });
        for (PlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || player.squaredDistanceTo(this) > 16) continue;
            double y = player.getY();
            Double before = playerY.put(player.getUuid(), y);
            if (before == null) continue;
            Box feet = player.getBoundingBox();
            boolean over = feet.maxX > box.minX && feet.minX < box.maxX && feet.maxZ > box.minZ && feet.minZ < box.maxZ;
            if (over && before >= box.maxY - 0.1 && y <= box.maxY + 0.3 && y < before - 0.02) squash(player);
        }
    }

    /**
     * A player came down on its cap: the player bounces, it is flattened ("pouic") or, the last time, finished (its
     * loot drops). The top one of a tower falls off it. True if it took the stomp.
     */
    public boolean squash(PlayerEntity player) {
        if (boardActor || isRemoved() || !isAlive() || stompCooldown > 0) return false;
        stompCooldown = 8;
        // the player bounces off the cap
        Vec3d v = player.getVelocity();
        player.setVelocity(v.x, 0.55, v.z);
        player.velocityModified = true;
        player.fallDistance = 0;
        if (getVehicle() instanceof GlandouilleEntity) {
            // off the tower: the one under it is the top now
            stopRiding();
            setVelocity(random.nextGaussian() * 0.1, 0.2, random.nextGaussian() * 0.1);
        }
        stomps++;
        remember(player);
        World world = getWorld();
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.POOF, getX(), getY() + getHeight() * 0.5, getZ(), 6, 0.25, 0.1, 0.25, 0.02);
        }
        GlandouilleVariant variant = getVariant();
        if (stomps >= variant.dieAt) {
            playSound(ModSounds.GLANDOUILLE_SQUASH, 1.2f, 0.8f);
            squashKill = true;
            try {
                damage(getDamageSources().playerAttack(player), Float.MAX_VALUE);
            } finally {
                squashKill = false;
            }
            return true;
        }
        if (stomps >= variant.flattenAt) {
            playSound(ModSounds.GLANDOUILLE_SQUASH, 1.2f, 1f);
            setMood(Mood.FLAT, FLAT_TICKS);
        } else {
            // the old mossy one only gets dented: a low "pouic", and it is cross
            playSound(ModSounds.GLANDOUILLE_SQUASH, 1f, 0.6f);
            setMood(Mood.SULK, SULK_TICKS / 2);
        }
        return true;
    }

    private void remember(@Nullable Entity attacker) {
        if (!(attacker instanceof PlayerEntity player) || boardActor) return;
        this.grudge = player.getUuid();
        this.grudgeUntil = getWorld().getTime() + GRUDGE_TICKS;
    }

    // ---------------------------------------------------------------- hit

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient || boardActor) return false;
        if (squashKill) return super.damage(source, amount);
        if (source.isOf(DamageTypes.FALL)) return false; // a light acorn
        Entity attacker = source.getAttacker();
        if (attacker instanceof LivingEntity living && !source.isIn(DamageTypeTags.IS_EXPLOSION)
                && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            onHit(living, source.getSource());
            return false;
        }
        return super.damage(source, amount);
    }

    /**
     * Hit by {@code attacker} (a blow, or what it threw): no damage. One inside a tower is flicked out of it; the
     * frosty one slides; the others are pushed and get angry.
     */
    public void onHit(LivingEntity attacker, @Nullable Entity direct) {
        if (boardActor) return;
        Vec3d dir;
        if (direct != null && direct != attacker && direct.getVelocity().horizontalLengthSquared() > 1.0E-4) {
            dir = direct.getVelocity();
        } else if (attacker instanceof PlayerEntity) {
            dir = attacker.getRotationVector();
        } else {
            dir = getPos().subtract(attacker.getPos());
        }
        dir = new Vec3d(dir.x, 0, dir.z);
        if (dir.lengthSquared() < 1.0E-6) dir = Vec3d.fromPolar(0, attacker.getYaw());
        dir = dir.normalize();
        remember(attacker);
        playSound(ModSounds.GLANDOUILLE_HURT, 1f, 1f);
        Entity vehicle = getVehicle();
        if (vehicle instanceof PlayerEntity) return;
        if (vehicle instanceof GlandouilleEntity) {
            GlandouilleTowers.flick(this, dir);
            return;
        }
        if (isAnchored()) {
            setMood(Mood.SULK, SULK_TICKS / 2);
            return;
        }
        // only the one hit goes: the tower on it hops off and comes down on the ground
        if (GlandouilleTowers.hasRider(this)) {
            leaveTower();
            GlandouilleTowers.hopOff(this, null);
        }
        if (getMood() == Mood.HOPPING) setMood(Mood.CALM, 0);
        if (getVariant() == GlandouilleVariant.FROSTY) {
            startSlide(dir.multiply(0.85));
            return;
        }
        takeKnockback(0.6, -dir.x, -dir.z);
        this.velocityModified = true;
        Mood mood = getMood();
        if (mood == Mood.SLEEPING) wakeUp(attacker);
        else if (mood == Mood.CALM || mood == Mood.SULK) {
            if (!(getVariant().charges() && hasHat() && attacker.isAlive() && startTelegraph(attacker))) {
                setMood(Mood.SULK, SULK_TICKS / 2);
            }
        }
    }

    // ---------------------------------------------------------------- flicked out of a tower

    /** Flies along {@code dir} like a missile (out of a tower), shoving what it meets; lands dizzy. */
    public void launch(Vec3d dir) {
        this.flyDir = new Vec3d(dir.x, 0, dir.z).normalize();
        setNoGravity(true);
        setVelocity(flyDir.x * 1.1, 0.08, flyDir.z * 1.1);
        this.velocityModified = true;
        setMood(Mood.FLYING, FLIGHT_TICKS);
        playSound(ModSounds.GLANDOUILLE_FLICK, 1f, 1f);
    }

    public Vec3d flyDirection() {
        return flyDir;
    }

    private void tickFlight(ServerWorld world) {
        int flown = FLIGHT_TICKS - moodTicks;
        if (flown < 10) setVelocity(flyDir.x * 1.1, getVelocity().y * 0.5, flyDir.z * 1.1);
        else setNoGravity(false);
        for (LivingEntity other : world.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(0.3),
                e -> e != this && e.isAlive() && !e.isSpectator() && !GlandouilleTowers.sameTower(this, e) && !spares(e))) {
            shove(other, flyDir, SHOVE);
            playSound(ModSounds.GLANDOUILLE_RAM, 1f, 1.2f);
        }
        if (flown % 2 == 0) world.spawnParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 1, 0, 0, 0, 0);
        if (--moodTicks <= 0 || (flown > 2 && (this.horizontalCollision || isOnGround()))) {
            setNoGravity(false);
            if (this.horizontalCollision) playSound(ModSounds.GLANDOUILLE_BONK, 1f, 1.2f);
            setVelocity(getVelocity().multiply(0.2, 1, 0.2));
            stun();
        }
    }

    // ---------------------------------------------------------------- hopping off a tower

    /** It leaves its tower (hit away): its tower mates are spared its flight, slide or charge for a moment. */
    public void leaveTower() {
        List<GlandouilleEntity> mates = new ArrayList<>(GlandouilleTowers.members(this));
        mates.remove(this);
        this.spared = mates;
        this.sparedUntil = getWorld().getTime() + SPARE_TICKS;
    }

    /** One of its old tower mates, left alone a moment after it was hit out of the tower. */
    private boolean spares(Entity other) {
        return getWorld().getTime() < sparedUntil && other instanceof GlandouilleEntity mate && spared.contains(mate);
    }

    /**
     * The one under it was hit away: it hops straight up (with whoever rides it) and comes back down onto the tower
     * of {@code onto}, or onto the ground if that one is gone or null.
     */
    public void hop(@Nullable GlandouilleEntity onto) {
        if (boardActor) return;
        this.hopOnto = onto;
        setMood(Mood.HOPPING, HOP_TICKS);
        setVelocity(0, HOP_VELOCITY, 0);
        this.velocityModified = true;
    }

    public @Nullable GlandouilleEntity hopOnto() {
        return hopOnto;
    }

    private void tickHop() {
        // straight up and down: nothing pushes it aside
        setVelocity(0, getVelocity().y, 0);
        GlandouilleEntity onto = hopOnto;
        if (onto != null && (onto.isRemoved() || !onto.isAlive())) onto = hopOnto = null;
        if (onto != null && getVelocity().y < 0) {
            GlandouilleEntity top = GlandouilleTowers.top(onto);
            Box cap = top.getBoundingBox();
            double reach = (top.getWidth() + getWidth()) * 0.5;
            boolean over = Math.abs(getX() - top.getX()) < reach && Math.abs(getZ() - top.getZ()) < reach;
            // came down through the top of its cap this tick
            if (over && this.prevY >= cap.maxY - 0.1 && getY() <= cap.maxY + 0.1) {
                hopOnto = null;
                setMood(Mood.CALM, 0);
                GlandouilleTowers.climb(this, top, false);
                return;
            }
        }
        if (--moodTicks <= 0 || (moodTicks < HOP_TICKS - 3 && isOnGround())) {
            hopOnto = null;
            setMood(Mood.CALM, 0);
        }
    }

    // ---------------------------------------------------------------- the frosty one: curling stone

    /** Slides along {@code velocity} with almost no friction, bouncing off walls (a tower on it slides along). */
    public void startSlide(Vec3d velocity) {
        if (boardActor) return;
        this.slideVelocity = new Vec3d(velocity.x, 0, velocity.z);
        this.slideRelaunches = 0;
        setMood(Mood.SLIDING, 200);
        playSound(ModSounds.GLANDOUILLE_SLIDE, 1f, 1f);
    }

    public Vec3d slideVelocity() {
        return slideVelocity;
    }

    private void tickSlide(ServerWorld world) {
        Vec3d v = getVelocity();
        // a wall stopped it on an axis last tick: it bounces back on that axis
        boolean bounced = false;
        if (this.horizontalCollision) {
            double x = slideVelocity.x, z = slideVelocity.z;
            if (Math.abs(v.x) < 1.0E-4 && Math.abs(x) > 1.0E-3) {
                x = -x * 0.8;
                bounced = true;
            }
            if (Math.abs(v.z) < 1.0E-4 && Math.abs(z) > 1.0E-3) {
                z = -z * 0.8;
                bounced = true;
            }
            slideVelocity = new Vec3d(x, 0, z);
            if (bounced) {
                playSound(ModSounds.GLANDOUILLE_BONK, 0.7f, 1.4f);
                world.spawnParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 0.3, getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                // it pushes off the wall by itself, a few times
                if (slideVelocity.horizontalLength() < 0.35 && slideRelaunches < 3) {
                    slideVelocity = slideVelocity.normalize().multiply(0.6);
                    slideRelaunches++;
                }
            }
        }
        slideVelocity = slideVelocity.multiply(0.985);
        setVelocity(slideVelocity.x, v.y, slideVelocity.z);
        for (LivingEntity other : world.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(0.15),
                e -> e != this && e.isAlive() && !e.isSpectator() && !GlandouilleTowers.sameTower(this, e) && !spares(e))) {
            shove(other, slideVelocity.normalize(), slideVelocity.horizontalLength() * 2);
        }
        if (this.age % 3 == 0) world.spawnParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 0.05, getZ(), 1, 0.1, 0, 0.1, 0);
        if (--moodTicks <= 0 || slideVelocity.horizontalLength() < 0.03) {
            setMood(Mood.STUNNED, getVariant().stunTicks / 2);
        }
    }

    /** The bottom one of a tower hit something: whoever is up there slides with it (frosty), see GlandouilleTowers. */
    public int slideRelaunches() {
        return slideRelaunches;
    }

    // ---------------------------------------------------------------- nap

    public boolean isSleeping() {
        return getMood() == Mood.SLEEPING;
    }

    public void fallAsleep(int ticks) {
        if (boardActor || hasVehicle()) return;
        setMood(Mood.SLEEPING, ticks);
    }

    private void tickSleep(ServerWorld world) {
        setVelocity(0, getVelocity().y, 0);
        if (this.age % 40 == 0) {
            playSound(ModSounds.GLANDOUILLE_SNORE, 0.6f, 1f);
            world.spawnParticles(new MulaSparkleEffect(0xE8E8FF, 0.8f, MulaSparkleEffect.Z),
                    getX(), getY() + getHeight() + 0.2, getZ(), 1, 0.05, 0.05, 0.05, 0);
        }
        for (PlayerEntity player : world.getPlayers()) {
            if (fair(player) && squaredDistanceTo(player) < 2.5 * 2.5) {
                wakeUp(player);
                return;
            }
        }
        if (--moodTicks <= 0 && (world.isDay() && !world.isRaining() || random.nextInt(4) == 0)) setMood(Mood.CALM, 0);
        else if (moodTicks < -2400) setMood(Mood.CALM, 0);
    }

    /** Woken up by {@code by}: it charges at once, no warning (the old mossy one only sulks). */
    public void wakeUp(@Nullable Entity by) {
        setMood(Mood.CALM, 0);
        if (by != null && getVariant().charges() && hasHat()) {
            this.chargeTarget = by;
            playSound(ModSounds.GLANDOUILLE_GROWL, 1f, 1.2f);
            launchCharge();
        } else {
            setMood(Mood.SULK, SULK_TICKS);
        }
    }

    // ---------------------------------------------------------------- hat

    /** Its cap flies off (an animation, then the Acorn Hat item drops); never while it carries a tower. */
    public void popHat() {
        if (!hasHat() || hatPopTicks > 0 || GlandouilleTowers.hasRider(this) || boardActor) return;
        triggerAnim(FX_CONTROLLER, "hat_pop");
        playSound(ModSounds.GLANDOUILLE_HAT_POP, 1f, 1f);
        hatPopTicks = 14;
    }

    private void dropHat(ServerWorld world) {
        hatPopTicks = -1;
        if (!hasHat()) return;
        setHat(false);
        ItemEntity item = new ItemEntity(world, getX(), getY() + getHeight() + 0.3, getZ(), new ItemStack(ModItems.ACORN_HAT));
        item.setVelocity(random.nextGaussian() * 0.08, 0.3, random.nextGaussian() * 0.08);
        item.setPickupDelay(30);
        world.spawnEntity(item);
    }

    /** Puts its cap back on (found on the ground, or given). */
    public void putHatOn() {
        setHat(true);
        playSound(ModSounds.GLANDOUILLE_HAT_ON, 1f, 1f);
        if (getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getHeight(), getZ(), 6, 0.3, 0.2, 0.3, 0);
        }
    }

    // ---------------------------------------------------------------- interactions

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (boardActor) return ActionResult.PASS;
        ItemStack stack = player.getStackInHand(hand);
        World world = getWorld();
        if (stack.isOf(Items.BONE_MEAL)) {
            if (!world.isClient) feedBoneMeal(player, stack);
            return ActionResult.success(world.isClient);
        }
        if (stack.isOf(ModItems.ACORN_HAT) && !hasHat() && hatPopTicks < 0) {
            if (!world.isClient) {
                putHatOn();
                stack.decrementUnlessCreative(1, player);
            }
            return ActionResult.success(world.isClient);
        }
        if (hand == Hand.MAIN_HAND && stack.isEmpty()) {
            if (GlandouilleTowers.carried(player) != null) {
                if (!world.isClient) GlandouilleTowers.stackCarriedOn(player, this);
                return ActionResult.success(world.isClient);
            }
            if (player.isSneaking()) {
                if (!world.isClient) GlandouilleTowers.pickUp(player, this);
                return ActionResult.success(world.isClient);
            }
        }
        return super.interactMob(player, hand);
    }

    /** Bone meal: now and then it drops an acorn (once per {@link #BONE_MEAL_COOLDOWN_TICKS}). */
    public boolean feedBoneMeal(@Nullable PlayerEntity player, ItemStack stack) {
        ServerWorld world = (ServerWorld) getWorld();
        if (boneMealCooldown > 0) {
            world.spawnParticles(ParticleTypes.SMOKE, getX(), getY() + getHeight(), getZ(), 4, 0.2, 0.1, 0.2, 0);
            playSound(ModSounds.GLANDOUILLE_SULK, 0.6f, 1.2f);
            return false;
        }
        boneMealCooldown = BONE_MEAL_COOLDOWN_TICKS;
        if (player != null) stack.decrementUnlessCreative(1, player);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getHeight() * 0.6, getZ(), 8, 0.3, 0.3, 0.3, 0);
        if (random.nextFloat() < BONE_MEAL_ACORN_CHANCE) {
            dropStack(new ItemStack(ModItems.ACORN));
            return true;
        }
        return false;
    }

    public int boneMealCooldown() {
        return boneMealCooldown;
    }

    // ---------------------------------------------------------------- spawn, save

    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
                                 @Nullable EntityData entityData) {
        if (!variantFromData) {
            setVariant(GlandouilleSpawns.variantFor(world, getBlockPos(), random));
            if (spawnReason == SpawnReason.NATURAL || spawnReason == SpawnReason.CHUNK_GENERATION) {
                setHat(random.nextFloat() >= HATLESS_SPAWN_CHANCE);
            }
        }
        return super.initialize(world, difficulty, spawnReason, entityData);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Variant", getVariant().ordinal());
        nbt.putBoolean("Hat", hasHat() || hatPopTicks > 0);
        nbt.putInt("Stomps", stomps);
        UUID grudged = grudge();
        if (grudged != null) {
            nbt.putUuid("Grudge", grudged);
            nbt.putLong("GrudgeUntil", grudgeUntil);
        }
        nbt.putInt("BoneMealCooldown", boneMealCooldown);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Variant")) {
            setVariant(GlandouilleVariant.byId(nbt.getInt("Variant")));
            variantFromData = true;
        }
        if (nbt.contains("Hat")) setHat(nbt.getBoolean("Hat"));
        stomps = nbt.getInt("Stomps");
        if (nbt.containsUuid("Grudge")) {
            grudge = nbt.getUuid("Grudge");
            grudgeUntil = nbt.getLong("GrudgeUntil");
        }
        boneMealCooldown = nbt.getInt("BoneMealCooldown");
    }

    // ---------------------------------------------------------------- sounds

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return isSleeping() || boardActor ? null : ModSounds.GLANDOUILLE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GLANDOUILLE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GLANDOUILLE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.block.BlockState state) {
        playSound(ModSounds.GLANDOUILLE_STEP, 0.4f, 1f);
    }

    // ---------------------------------------------------------------- animations

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, MAIN_CONTROLLER, 4, this::animate));
        controllers.add(new AnimationController<>(this, FX_CONTROLLER, 0, state -> PlayState.STOP)
                .triggerableAnim("hat_pop", RawAnimation.begin().thenPlay("hat_pop")));
    }

    private PlayState animate(AnimationState<GlandouilleEntity> state) {
        Entity vehicle = getVehicle();
        if (vehicle instanceof PlayerEntity) return state.setAndContinue(CARRIED);
        Mood mood = getMood();
        RawAnimation animation = switch (mood) {
            case TELEGRAPH -> TELEGRAPH;
            case CHARGING, FLYING -> CHARGE;
            case STUNNED -> STUNNED;
            case FLAT -> FLAT;
            case REINFLATE -> REINFLATE;
            case SULK -> SULK;
            case SLEEPING -> SLEEP;
            case SLIDING, HOPPING -> CARRIED;
            case PUSH_FAIL -> PUSH_FAIL;
            case CALM -> null;
        };
        if (animation != null) return state.setAndContinue(animation);
        if (vehicle instanceof GlandouilleEntity) return state.setAndContinue(STACKED);
        if (!hasHat() && !state.isMoving()) return state.setAndContinue(SHY);
        return state.setAndContinue(state.isMoving() ? WALK : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
