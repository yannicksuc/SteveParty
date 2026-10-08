package fr.lordfinn.steveparty.entities.custom.frousseux;

import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractWindChargeEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.TeleportTarget;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.UUID;

/**
 * The Frousseux (Wickling): a little candle ghost of the caves, one per candle colour ({@link FrousseuxColor}).
 * <ul>
 *     <li><b>Light</b>: it lights the cave around it for real ({@link FrousseuxLight}), from 15 down to 10 as its
 *     flame weakens.</li>
 *     <li><b>Flame = health</b>: four stages ({@link Flame}), shown by the flame's size and brightness.</li>
 *     <li><b>Floats</b> near the ground and wanders a little, through <b>thin walls</b> (see {@link FrousseuxFlight});
 *     it never suffocates.</li>
 *     <li><b>Shy</b>: a player within {@link #LOOK_RANGE} blocks looking at it, it freezes and hides its eyes; it goes
 *     on once nobody looks.</li>
 *     <li><b>Hits</b>: blows in melee do nothing (a puff, and it slips a little away); projectiles hurt it; a wind charge
 *     blows its flame out, whether it hits it or bursts by it; fire and lava do nothing; potions work.</li>
 *     <li><b>Flint and steel</b>: relights its flame, giving back health.</li>
 *     <li><b>Loot</b>: sometimes its candle, always with Looting (loot table entities/frousseux).</li>
 * </ul>
 * Kept for the next steps: its owner ({@link #getOwner()}, taming), and the board's Frousseux ({@link #isBoardActor()}),
 * which do none of the above: invulnerable, moved by the board, never saved, no light.
 */
public class FrousseuxEntity extends PathAwareEntity implements GeoEntity {
    /** Its body: 8x8 pixels, 10 high. */
    public static final float WIDTH = 0.5f, HEIGHT = 0.625f;
    public static final double MAX_HEALTH = 8.0;
    /** A player looking at it from this close makes it hide its eyes. */
    public static final double LOOK_RANGE = 24.0;
    /** Still hiding its eyes this long after the last look (ticks). */
    private static final int SHY_LINGER = 10;
    /** Its flight speed (blocks per tick). */
    static final double FLY_SPEED = 0.07;
    /** Health a strike of flint and steel gives back. */
    public static final float RELIGHT_HEAL = 4.0f;
    /** Its dodge: this far, at most this often (ticks). */
    private static final double DODGE_MIN = 1.5, DODGE_MAX = 3.0;
    private static final int DODGE_COOLDOWN = 10;

    /** Its flame by health: size and brightness drawn (FrousseuxModel, FrousseuxRenderer), and its light level. */
    public enum Flame {
        FULL(15, 1.0f, 1.0f), HIGH(14, 0.82f, 0.92f), LOW(12, 0.62f, 0.78f), EMBER(10, 0.38f, 0.6f);

        public final int light;
        public final float size;
        public final float brightness;

        Flame(int light, float size, float brightness) {
            this.light = light;
            this.size = size;
            this.brightness = brightness;
        }

        public static Flame of(float health, float maxHealth) {
            float share = maxHealth > 0 ? health / maxHealth : 0;
            if (share > 0.75f) return FULL;
            if (share > 0.5f) return HIGH;
            if (share > 0.25f) return LOW;
            return EMBER;
        }
    }

    private static final TrackedData<Integer> COLOR =
            DataTracker.registerData(FrousseuxEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> SHY =
            DataTracker.registerData(FrousseuxEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> BLOWN_OUT =
            DataTracker.registerData(FrousseuxEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation MOVE = RawAnimation.begin().thenLoop("move");
    private static final RawAnimation SHY_ANIM = RawAnimation.begin().thenLoop("shy");
    public static final String MAIN_CONTROLLER = "main";

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private final FrousseuxLight light = new FrousseuxLight();
    private final FrousseuxFlight.Control flight;
    private boolean colorFromData;
    private boolean boardActor;
    /** Its owner, once tamed (next step); null: wild. */
    private @Nullable UUID owner;
    private int shyTicks;
    private int dodgeCooldown;
    /** A wind charge burst by it: its flame is blown out on its next tick (see {@link #isImmuneToExplosion}). */
    private @Nullable AbstractWindChargeEntity windBurst;

    public FrousseuxEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        this.flight = new FrousseuxFlight.Control(this);
        this.moveControl = flight;
        this.experiencePoints = 3;
        setNoGravity(true);
    }

    public static DefaultAttributeContainer.Builder setAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, FLY_SPEED)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(5, new FrousseuxFlight.Wander(this));
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
        builder.add(COLOR, 0);
        builder.add(SHY, false);
        builder.add(BLOWN_OUT, false);
    }

    // ---------------------------------------------------------------- colour, flame, state

    public FrousseuxColor getColor() {
        return FrousseuxColor.byId(this.dataTracker.get(COLOR));
    }

    public void setColor(FrousseuxColor color) {
        this.dataTracker.set(COLOR, color.ordinal());
    }

    public Flame getFlame() {
        return Flame.of(getHealth(), getMaxHealth());
    }

    /** Hiding its eyes: a player is looking at it. */
    public boolean isShy() {
        return this.dataTracker.get(SHY);
    }

    /** Its flame was blown out (a wind charge): no flame drawn, it is dying. */
    public boolean isBlownOut() {
        return this.dataTracker.get(BLOWN_OUT);
    }

    /** Free to wander: alive, wild, not hiding its eyes. */
    public boolean isFree() {
        return isAlive() && !isShy() && !boardActor;
    }

    FrousseuxFlight.Control flight() {
        return flight;
    }

    public @Nullable UUID getOwner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        if (owner != null) setPersistent();
    }

    public boolean isTamed() {
        return owner != null;
    }

    // ---------------------------------------------------------------- board actors

    /** A Frousseux of a board space: invulnerable, no will of its own, never saved, no light. */
    public boolean isBoardActor() {
        return boardActor;
    }

    public void makeBoardActor() {
        this.boardActor = true;
        setInvulnerable(true);
        setAiDisabled(true);
        setPersistent();
        if (!getWorld().isClient) light.clear(getWorld());
    }

    @Override
    public boolean shouldSave() {
        return !boardActor && super.shouldSave();
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return !isTamed() && !boardActor;
    }

    // ---------------------------------------------------------------- a ghost: no gravity, no walls, no pushes

    @Override
    public void tick() {
        // It flies through blocks (its routes keep to thin walls, FrousseuxFlight), as the vex does
        this.noClip = true;
        super.tick();
        this.noClip = false;
        setNoGravity(true);
        if (getWorld() instanceof ServerWorld world) tickServer(world);
        else tickClient();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushAway(Entity entity) {
    }

    @Override
    public void takeKnockback(double strength, double x, double z) {
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    private void tickServer(ServerWorld world) {
        if (boardActor) return;
        if (!isAlive()) {
            light.clear(world);
            return;
        }
        if (windBurst != null) {
            AbstractWindChargeEntity charge = windBurst;
            windBurst = null;
            blowOut(world, getDamageSources().windCharge(charge, charge.getOwner() instanceof LivingEntity l ? l : null));
            return;
        }
        if (dodgeCooldown > 0) dodgeCooldown--;
        if (age % 4 == 0) tickShy(world);
        if (age % 2 == 0) light.update(world, BlockPos.ofFloored(getBoundingBox().getCenter()), getFlame().light);
        if (age % 10 == 0 && !flight.isMovingTo()) {
            Vec3d out = FrousseuxFlight.escape(this);
            if (out != null) flight.moveTo(out.x, out.y, out.z, FLY_SPEED);
        }
    }

    private void tickClient() {
        if (!isAlive() || isBlownOut()) return;
        // a weak flame smokes a little
        if (getFlame() == Flame.EMBER && random.nextInt(8) == 0) {
            getWorld().addParticle(ParticleTypes.SMOKE, getX(), getY() + HEIGHT + 0.25, getZ(), 0, 0.02, 0);
        }
    }

    // ---------------------------------------------------------------- shy

    private void tickShy(ServerWorld world) {
        if (lookedAt(world)) shyTicks = SHY_LINGER;
        else if (shyTicks > 0) shyTicks -= 4;
        boolean shy = shyTicks > 0;
        if (shy != isShy()) {
            this.dataTracker.set(SHY, shy);
            if (shy) {
                flight.stop();
                getNavigation().stop();
            }
        }
    }

    /** A player near enough looks right at it, as at an Enderman (it is small: a little wider look counts). */
    private boolean lookedAt(ServerWorld world) {
        Vec3d centre = getBoundingBox().getCenter();
        for (PlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            double distanceSq = player.squaredDistanceTo(centre);
            if (distanceSq > LOOK_RANGE * LOOK_RANGE) continue;
            Vec3d to = centre.subtract(player.getEyePos());
            double distance = to.length();
            if (distance < 1.0E-3) continue;
            double dot = player.getRotationVec(1.0f).normalize().dotProduct(to.multiply(1 / distance));
            if (dot > 1.0 - 0.06 / distance && player.canSee(this)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- hits

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient || boardActor || isRemoved()) return false;
        if (source.isOf(DamageTypes.IN_WALL) || source.isOf(DamageTypes.DROWN)) return false;
        if (isWindCharge(source)) {
            if (isAlive()) blowOut((ServerWorld) getWorld(), source);
            return true;
        }
        if (isMelee(source)) {
            dodge((ServerWorld) getWorld());
            return false;
        }
        return super.damage(source, amount);
    }

    private static boolean isWindCharge(DamageSource source) {
        return source.isOf(DamageTypes.WIND_CHARGE) || source.getSource() instanceof AbstractWindChargeEntity;
    }

    /** A blow from someone right there: no projectile, no explosion, no magic. */
    private static boolean isMelee(DamageSource source) {
        Entity attacker = source.getAttacker();
        return attacker != null && source.getSource() == attacker
                && !source.isIn(DamageTypeTags.IS_PROJECTILE) && !source.isIn(DamageTypeTags.IS_EXPLOSION)
                && !source.isIn(DamageTypeTags.WITCH_RESISTANT_TO);
    }

    /** Melee: nothing hurts it; it vanishes in a puff and comes back a little away (the full dodge: next step). */
    private void dodge(ServerWorld world) {
        if (dodgeCooldown > 0) return;
        dodgeCooldown = DODGE_COOLDOWN;
        Vec3d to = FrousseuxFlight.hopTarget(this, random, DODGE_MIN, DODGE_MAX);
        world.spawnParticles(ParticleTypes.POOF, getX(), getBodyY(0.5), getZ(), 8, 0.15, 0.15, 0.15, 0.02);
        if (to != null) {
            flight.stop();
            requestTeleport(to.x, to.y, to.z);
            world.spawnParticles(ParticleTypes.POOF, to.x, to.y + HEIGHT / 2, to.z, 6, 0.15, 0.15, 0.15, 0.02);
        }
        playSound(ModSounds.FROUSSEUX_LAUGH, 1.0f, 1.0f);
    }

    /**
     * A wind charge bursting by it: its flame is blown out. The burst never hurts what it pushes, so it is caught here
     * (asked before the push): no push, and the flame goes out on its next tick, out of the explosion's loop.
     */
    @Override
    public boolean isImmuneToExplosion(Explosion explosion) {
        if (explosion.getEntity() instanceof AbstractWindChargeEntity charge && !boardActor && isAlive()
                && !getWorld().isClient) {
            if (windBurst == null) windBurst = charge;
            return true;
        }
        return super.isImmuneToExplosion(explosion);
    }

    /** The wind charge's one-shot: the flame blown out (smoke, a "pfff"), its light gone, dead. */
    private void blowOut(ServerWorld world, DamageSource source) {
        this.dataTracker.set(BLOWN_OUT, true);
        light.clear(world);
        world.spawnParticles(ParticleTypes.SMOKE, getX(), getY() + HEIGHT + 0.2, getZ(), 12, 0.08, 0.15, 0.08, 0.03);
        world.spawnParticles(ParticleTypes.POOF, getX(), getBodyY(0.5), getZ(), 6, 0.15, 0.15, 0.15, 0.02);
        super.damage(source, Float.MAX_VALUE);
        if (isAlive()) kill(); // Resistance and the like: blown out all the same
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        if (!getWorld().isClient) light.clear(getWorld());
    }

    // ---------------------------------------------------------------- flint and steel

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (boardActor || !stack.isOf(Items.FLINT_AND_STEEL) || !isAlive()) return super.interactMob(player, hand);
        if (getHealth() >= getMaxHealth()) return ActionResult.PASS;
        if (getWorld() instanceof ServerWorld world) relight(world);
        stack.damage(1, player, LivingEntity.getSlotForHand(hand));
        return ActionResult.success(getWorld().isClient);
    }

    /** Its flame relit: health back, sparks and a little flare. */
    public void relight(ServerWorld world) {
        heal(RELIGHT_HEAL);
        double top = getY() + HEIGHT + 0.2;
        world.spawnParticles(ParticleTypes.FLAME, getX(), top, getZ(), 10, 0.1, 0.12, 0.1, 0.02);
        world.spawnParticles(ParticleTypes.SMALL_FLAME, getX(), getBodyY(0.5), getZ(), 6, 0.25, 0.2, 0.25, 0.01);
        world.playSound(null, getX(), getY(), getZ(), ModSounds.FROUSSEUX_RELIGHT, SoundCategory.NEUTRAL, 1.0f, 1.0f);
    }

    // ---------------------------------------------------------------- light: never left behind

    @Override
    public void remove(RemovalReason reason) {
        // killed or discarded (despawned, /kill): its light goes with it. Unloaded with its chunk, the light stays
        // saved with the world and comes back with it (FrousseuxLight)
        if (reason.shouldDestroy() && !getWorld().isClient) light.clear(getWorld());
        super.remove(reason);
    }

    @Override
    public @Nullable Entity teleportTo(TeleportTarget teleportTarget) {
        if (!getWorld().isClient && teleportTarget.world() != getWorld()) light.clear(getWorld());
        return super.teleportTo(teleportTarget);
    }

    // ---------------------------------------------------------------- spawn, save

    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
                                 @Nullable EntityData entityData) {
        if (!colorFromData) setColor(FrousseuxColor.random(random));
        return super.initialize(world, difficulty, spawnReason, entityData);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("Color", getColor().asString());
        if (owner != null) nbt.putUuid("Owner", owner);
        light.write(nbt);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Color")) {
            setColor(FrousseuxColor.byName(nbt.getString("Color")));
            colorFromData = true;
        }
        owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        light.read(nbt);
    }

    // ---------------------------------------------------------------- sounds

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return boardActor ? null : ModSounds.FROUSSEUX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.FROUSSEUX_HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return isBlownOut() ? ModSounds.FROUSSEUX_BLOWN_OUT : ModSounds.FROUSSEUX_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.7f; // a small thing
    }

    // ---------------------------------------------------------------- animations

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, MAIN_CONTROLLER, 5, this::animate));
    }

    private PlayState animate(AnimationState<FrousseuxEntity> state) {
        if (isShy()) return state.setAndContinue(SHY_ANIM);
        return state.setAndContinue(state.isMoving() ? MOVE : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
