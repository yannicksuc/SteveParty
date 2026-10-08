package fr.lordfinn.steveparty.entities.custom.frousseux;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.FollowsOwnerAnywhere;
import fr.lordfinn.steveparty.entities.PetTeleports;
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
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.AbstractWindChargeEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
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

import java.util.Optional;
import java.util.UUID;

/**
 * The Frousseux (Wickling): a little candle ghost of the caves, one per candle colour ({@link FrousseuxColor}).
 * <ul>
 *     <li><b>Light</b>: it lights the cave around it for real ({@link FrousseuxLight}), from 15 down to 6 as its
 *     flame weakens.</li>
 *     <li><b>Flame = health</b>: four stages ({@link Flame}), shown by the flame's size and brightness.</li>
 *     <li><b>Floats</b> near the ground and wanders a little, through <b>thin walls</b> (see {@link FrousseuxFlight});
 *     it never suffocates.</li>
 *     <li><b>Shy</b>: a player within {@link #LOOK_RANGE} blocks looking at it, it freezes and hides its eyes; it goes
 *     on once nobody looks.</li>
 *     <li><b>Hits</b>: blows in melee do nothing (a puff, and it slips a little away); projectiles hurt it; a wind charge
 *     blows its flame out, whether it hits it or bursts by it; fire and lava do nothing; potions work.</li>
 *     <li><b>Flint and steel</b>: relights its flame, giving back health; on a wild one, a try at taming it.</li>
 *     <li><b>Loot</b>: sometimes its candle, always with Looting (loot table entities/frousseux); and what it stole.</li>
 *     <li><b>A thief</b>: a wild one steals one shiny thing ({@link #SHINY}) off a player coming within
 *     {@link #STEAL_RANGE} blocks; the item flies to it, it carries it under itself and flees laughing. Killed, it
 *     drops it; tamed, it gives it back.</li>
 *     <li><b>Tamed</b> ({@link #getOwner()}): it follows its owner about, lighting the way, out of their sight line
 *     and their crosshair ({@link FrousseuxCompanion}); never steals; sits and stays on its owner's word.</li>
 * </ul>
 * The board's Frousseux ({@link #isBoardActor()}) do none of the above: invulnerable, moved by the board, never
 * saved, no light.
 */
public class FrousseuxEntity extends PathAwareEntity implements GeoEntity, FollowsOwnerAnywhere {
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
    private static final double DODGE_MIN = 2.5, DODGE_MAX = 4.5;
    private static final int DODGE_COOLDOWN = 8;
    /** What it steals: one of these, never more than one at a time. */
    public static final TagKey<Item> SHINY = TagKey.of(RegistryKeys.ITEM, Steveparty.id("frousseux_shiny"));
    /** Held in either hand, these keep a player from being robbed. */
    public static final TagKey<Item> WARDS = TagKey.of(RegistryKeys.ITEM, Steveparty.id("frousseux_wards"));
    /** A player this close to a wild one gets robbed, at most once every {@link #STEAL_COOLDOWN} ticks. */
    public static final double STEAL_RANGE = 2.0;
    public static final int STEAL_COOLDOWN = 300;
    /** How long a stolen (or given back) item flies, and how long it flees laughing after a theft (ticks). */
    public static final int ITEM_FLIGHT_TICKS = 12, FLEE_TICKS = 120;
    /** One strike of flint and steel in this many tames a wild one. */
    public static final int TAME_CHANCE = 3;

    /** Its flame by health: size and brightness drawn (FrousseuxModel, FrousseuxRenderer), and its light level. */
    public enum Flame {
        FULL(15, 1.0f, 1.0f), HIGH(12, 0.82f, 0.92f), LOW(9, 0.62f, 0.78f), EMBER(6, 0.38f, 0.6f);

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
    private static final TrackedData<Optional<UUID>> OWNER =
            DataTracker.registerData(FrousseuxEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    /**
     * Its flame's stage (a {@link Flame} ordinal), set with its health: always sent with its spawn (it starts at -1),
     * so a Frousseux summoned weak shows its weak flame at once (its health alone is not sent when it equals the
     * tracker's first value).
     */
    private static final TrackedData<Byte> FLAME_STAGE =
            DataTracker.registerData(FrousseuxEntity.class, TrackedDataHandlerRegistry.BYTE);
    /** A tamed one in a lit place (FrousseuxCompanion): in front of its owner, in their crosshair. */
    private static final TrackedData<Boolean> LIT_MODE =
            DataTracker.registerData(FrousseuxEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> SITTING =
            DataTracker.registerData(FrousseuxEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    /** The item drawn under it (what it carries), or flying to or from it. */
    private static final TrackedData<ItemStack> SHOWN_ITEM =
            DataTracker.registerData(FrousseuxEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    /** An item flying: 0 none; id + 1 of whom it flies from (stolen); -(id + 1) of whom it flies to (given back). */
    private static final TrackedData<Integer> ITEM_FLIGHT =
            DataTracker.registerData(FrousseuxEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation MOVE = RawAnimation.begin().thenLoop("move");
    private static final RawAnimation SHY_ANIM = RawAnimation.begin().thenLoop("shy");
    public static final String MAIN_CONTROLLER = "main";

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private final FrousseuxLight light = new FrousseuxLight();
    private final FrousseuxFlight.Control flight;
    private boolean colorFromData;
    private boolean boardActor;
    private int shyTicks;
    private int dodgeCooldown;
    /** What it stole (one item), or empty. */
    private ItemStack stolen = ItemStack.EMPTY;
    private int stealCooldown;
    /** Fleeing whom it robbed, this many ticks more. */
    private int fleeTicks;
    private @Nullable PlayerEntity fleeFrom;
    /** The item's flight going on, this many ticks more (server); when it started (client, its age). */
    private int itemFlightTicks;
    private int itemFlightStart = Integer.MIN_VALUE;
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
        this.goalSelector.add(1, new FrousseuxFlight.Flee(this));
        this.goalSelector.add(2, new FrousseuxCompanion.Follow(this));
        this.goalSelector.add(5, new FrousseuxFlight.Wander(this));
        // where it looks, wild or tamed (still or following): at a player close by (its owner, mostly), else about it
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 6.0f, 0.06f) {
            @Override
            public boolean canStart() {
                return looksAbout() && super.canStart();
            }
        });
        this.goalSelector.add(7, new LookAroundGoal(this) {
            @Override
            public boolean canStart() {
                return looksAbout() && super.canStart();
            }
        });
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(COLOR, 0);
        builder.add(SHY, false);
        builder.add(BLOWN_OUT, false);
        builder.add(FLAME_STAGE, (byte) -1);
        builder.add(OWNER, Optional.empty());
        builder.add(SITTING, false);
        builder.add(LIT_MODE, false);
        builder.add(SHOWN_ITEM, ItemStack.EMPTY);
        builder.add(ITEM_FLIGHT, 0);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (ITEM_FLIGHT.equals(data) && getWorld().isClient) {
            itemFlightStart = this.dataTracker.get(ITEM_FLIGHT) != 0 ? age : Integer.MIN_VALUE;
        }
    }

    // ---------------------------------------------------------------- colour, flame, state

    public FrousseuxColor getColor() {
        return FrousseuxColor.byId(this.dataTracker.get(COLOR));
    }

    public void setColor(FrousseuxColor color) {
        this.dataTracker.set(COLOR, color.ordinal());
    }

    public Flame getFlame() {
        byte stage = this.dataTracker.get(FLAME_STAGE);
        return stage >= 0 && stage < Flame.values().length ? Flame.values()[stage] : Flame.of(getHealth(), getMaxHealth());
    }

    @Override
    public void setHealth(float health) {
        super.setHealth(health);
        updateFlameStage();
    }

    private void updateFlameStage() {
        byte stage = (byte) Flame.of(getHealth(), getMaxHealth()).ordinal();
        if (this.dataTracker.get(FLAME_STAGE) != stage) this.dataTracker.set(FLAME_STAGE, stage);
    }

    /** Hiding its eyes: a player is looking at it. */
    public boolean isShy() {
        return this.dataTracker.get(SHY);
    }

    /** Its flame was blown out (a wind charge): no flame drawn, it is dying. */
    public boolean isBlownOut() {
        return this.dataTracker.get(BLOWN_OUT);
    }

    /** Free to look about: alive, not hiding its eyes, not fleeing, not the board's. */
    private boolean looksAbout() {
        return isAlive() && !isShy() && !boardActor && fleeTicks <= 0;
    }

    /** Free to wander: alive, wild, not hiding its eyes, not fleeing. */
    public boolean isFree() {
        return isAlive() && !isShy() && !boardActor && !isTamed() && fleeTicks <= 0;
    }

    FrousseuxFlight.Control flight() {
        return flight;
    }

    public @Nullable UUID getOwner() {
        return this.dataTracker.get(OWNER).orElse(null);
    }

    public void setOwner(@Nullable UUID owner) {
        this.dataTracker.set(OWNER, Optional.ofNullable(owner));
        if (owner != null) setPersistent();
    }

    public boolean isTamed() {
        return this.dataTracker.get(OWNER).isPresent();
    }

    public boolean isOwner(PlayerEntity player) {
        return player.getUuid().equals(getOwner());
    }

    /** Its owner, if they are in its world. */
    public @Nullable PlayerEntity getOwnerPlayer() {
        UUID owner = getOwner();
        return owner == null ? null : getWorld().getPlayerByUuid(owner);
    }

    /** Sitting: it stays where it was told to, instead of following. */
    public boolean isSitting() {
        return this.dataTracker.get(SITTING);
    }

    public void setSitting(boolean sitting) {
        this.dataTracker.set(SITTING, sitting);
    }

    /** What it stole and carries (server side), or empty. */
    public ItemStack getStolen() {
        return stolen;
    }

    /** The item drawn under it or flying (both sides). */
    public ItemStack getShownItem() {
        return this.dataTracker.get(SHOWN_ITEM);
    }

    public boolean isFleeing() {
        return fleeTicks > 0;
    }

    @Nullable PlayerEntity fleeFrom() {
        return fleeFrom;
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
        return !isTamed() && !boardActor && stolen.isEmpty();
    }

    /**
     * In the dark, its owner's crosshair goes through a following Frousseux (it is never in the way of their mining,
     * building or fighting), except when they reach for it: flint and steel in hand, or sneaking with an empty hand
     * ({@link FrousseuxCompanion#reachesFor}). In a lit place ({@link #isLitMode}) it floats in front of them and is
     * clicked as usual. Asked by the owner's client (the crosshair); the server, other players and projectiles see
     * it as usual.
     */
    @Override
    public boolean canHit() {
        if (getWorld().isClient && isTamed() && !isSitting() && !isLitMode() && CLIENT_PASS_THROUGH.test(this)) return false;
        return super.canHit();
    }

    /** Tamed, its owner in a lit place: it floats in front of them (FrousseuxCompanion). */
    public boolean isLitMode() {
        return this.dataTracker.get(LIT_MODE);
    }

    /** Ticks the ambient light around its owner has asked for the other mode (it switches after a while). */
    private int lightModeTicks;

    /**
     * Lit or dark mode, once a second, from the light around its owner without its own
     * ({@link FrousseuxCompanion#ambientLight}):
     * lit from {@link FrousseuxCompanion#LIT_FROM}, dark again at {@link FrousseuxCompanion#DARK_UNDER} or less,
     * each after {@link FrousseuxCompanion#MODE_DELAY} ticks of it (no flicker at the threshold).
     */
    private void tickLightMode(ServerWorld world) {
        PlayerEntity owner = isTamed() ? getOwnerPlayer() : null;
        boolean lit = isLitMode();
        boolean wants = lit;
        if (owner != null) {
            int ambient = FrousseuxCompanion.ambientLight(world, BlockPos.ofFloored(owner.getEyePos()));
            wants = lit ? ambient > FrousseuxCompanion.DARK_UNDER : ambient >= FrousseuxCompanion.LIT_FROM;
        } else if (!isTamed()) {
            wants = false;
        }
        if (wants == lit) {
            lightModeTicks = 0;
        } else if ((lightModeTicks += 20) >= FrousseuxCompanion.MODE_DELAY || !isTamed()) {
            lightModeTicks = 0;
            this.dataTracker.set(LIT_MODE, wants);
        }
    }

    /** Set by the client: whether the local player is this one's owner, not reaching for it. */
    public static java.util.function.Predicate<FrousseuxEntity> CLIENT_PASS_THROUGH = frousseux -> false;

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
        if (stealCooldown > 0) stealCooldown--;
        tickItemFlight();
        if (fleeTicks > 0) {
            fleeTicks--;
            if (fleeFrom != null && (!fleeFrom.isAlive() || fleeFrom.getWorld() != world)) fleeFrom = null;
            if (random.nextInt(50) == 0) laugh();
        }
        if (!isTamed() && age % 5 == 0 && stolen.isEmpty() && stealCooldown <= 0) tryStealing(world);
        if (age % 4 == 0) tickShy(world);
        if (age % 20 == 7) tickLightMode(world);
        if (age % 20 == 0) updateFlameStage();
        if (age % 20 == 3 && isTamed()) PetTeleports.remember(this); // its greatest health may change (effects, attributes)
        if (age % 2 == 0) light.update(world, BlockPos.ofFloored(getBoundingBox().getCenter()), getFlame().light);
        if (age % 10 == 0 && !flight.isMovingTo()) {
            Vec3d out = FrousseuxFlight.escape(this);
            if (out != null) flight.moveTo(out.x, out.y, out.z, FLY_SPEED);
        }
    }

    private void tickClient() {
        tickFlameLean();
        if (!isAlive() || isBlownOut()) return;
        // the stolen (or given back) item's sparkles on its way
        Vec3d flying = itemFlightOffset(0);
        if (flying != null && random.nextInt(2) == 0) {
            getWorld().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + flying.x, getY() + flying.y, getZ() + flying.z,
                    0, 0, 0);
        }
        // a weak flame smokes a little
        if (getFlame() == Flame.EMBER && random.nextInt(8) == 0) {
            getWorld().addParticle(ParticleTypes.SMOKE, getX(), getY() + HEIGHT + 0.25, getZ(), 0, 0.02, 0);
        }
    }

    // ---------------------------------------------------------------- its flame in the wind (client)

    /** How far its flame leans (radians): back against its flight, aside in its turns; eased (FrousseuxModel). */
    private float flameLeanX, flameLeanZ, prevFlameLeanX, prevFlameLeanZ;
    private float lastBodyYaw = Float.NaN;

    private void tickFlameLean() {
        prevFlameLeanX = flameLeanX;
        prevFlameLeanZ = flameLeanZ;
        double dx = getX() - prevX, dz = getZ() - prevZ;
        float yaw = bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        // its speed forwards and to its left, in blocks per tick
        double forward = -dx * MathHelper.sin(yaw) + dz * MathHelper.cos(yaw);
        double left = dx * MathHelper.cos(yaw) + dz * MathHelper.sin(yaw);
        float turn = Float.isNaN(lastBodyYaw) ? 0 : MathHelper.wrapDegrees(bodyYaw - lastBodyYaw);
        lastBodyYaw = bodyYaw;
        float wantX = MathHelper.clamp((float) (forward * 4.0), -0.6f, 0.6f);
        float wantZ = MathHelper.clamp((float) (left * 4.0) + turn * 0.02f, -0.6f, 0.6f);
        flameLeanX += (wantX - flameLeanX) * 0.2f;
        flameLeanZ += (wantZ - flameLeanZ) * 0.2f;
    }

    public float flameLeanX(float partialTick) {
        return MathHelper.lerp(partialTick, prevFlameLeanX, flameLeanX);
    }

    public float flameLeanZ(float partialTick) {
        return MathHelper.lerp(partialTick, prevFlameLeanZ, flameLeanZ);
    }

    // ---------------------------------------------------------------- shy

    private void tickShy(ServerWorld world) {
        if (isTamed() || fleeTicks > 0) shyTicks = 0; // it trusts its owner; a thief on the run has no time to hide
        else if (lookedAt(world)) shyTicks = SHY_LINGER;
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

    // ---------------------------------------------------------------- theft

    /** A player close enough, in survival, with something shiny: robbed. */
    private void tryStealing(ServerWorld world) {
        if (boardActor || !isAlive()) return;
        Vec3d centre = getBoundingBox().getCenter();
        for (PlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || player.isCreative() || !player.isAlive()) continue;
            if (player.getBoundingBox().squaredMagnitude(centre) > STEAL_RANGE * STEAL_RANGE) continue;
            if (isWarded(world, player)) continue;
            if (stealFrom(player)) return;
        }
    }

    /** Never robbed: a player on fire, holding a torch or a lantern (either hand), or with a Frousseux of their own. */
    public static boolean isWarded(ServerWorld world, PlayerEntity player) {
        if (player.isOnFire() || player.getMainHandStack().isIn(WARDS) || player.getOffHandStack().isIn(WARDS)) return true;
        return !world.getEntitiesByType(TypeFilter.instanceOf(FrousseuxEntity.class),
                frousseux -> frousseux.isTamed() && frousseux.isOwner(player)).isEmpty();
    }

    /**
     * Steals one shiny item off {@code player} (one of a stack, a random one of their shiny stacks; never their armour):
     * it flies to it with sparkles and a laugh, and it flees. False if they have nothing shiny or it already carries
     * something.
     */
    public boolean stealFrom(PlayerEntity player) {
        if (!stolen.isEmpty() || isTamed() || boardActor) return false;
        PlayerInventory inventory = player.getInventory();
        int found = 0, slot = -1;
        for (int i = 0; i < inventory.size(); i++) {
            if (i >= PlayerInventory.MAIN_SIZE && i < PlayerInventory.MAIN_SIZE + PlayerInventory.ARMOR_SLOTS.length) continue;
            if (inventory.getStack(i).isIn(SHINY) && random.nextInt(++found) == 0) slot = i; // one at random
        }
        if (slot < 0) return false;
        ItemStack taken = inventory.getStack(slot).split(1);
        inventory.markDirty();
        stolen = taken;
        setPersistent(); // never despawns with someone's diamond
        this.dataTracker.set(SHOWN_ITEM, taken.copy());
        startItemFlight(player, true);
        stealCooldown = STEAL_COOLDOWN;
        fleeTicks = FLEE_TICKS;
        fleeFrom = player;
        shyTicks = 0;
        this.dataTracker.set(SHY, false);
        if (getWorld() instanceof ServerWorld world) {
            Vec3d at = player.getPos().add(0, player.getHeight() * 0.55, 0);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.05);
            world.spawnParticles(ParticleTypes.WAX_ON, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.5);
        }
        laugh();
        return true;
    }

    /** Gives what it stole back to {@code player}: it flies to them, into their inventory (or at their feet). */
    public void giveBack(PlayerEntity player) {
        if (stolen.isEmpty()) return;
        ItemStack back = stolen;
        stolen = ItemStack.EMPTY;
        this.dataTracker.set(SHOWN_ITEM, back.copy());
        startItemFlight(player, false);
        player.getInventory().offerOrDrop(back);
        fleeTicks = 0;
        fleeFrom = null;
        playSound(SoundEvents.ENTITY_ITEM_PICKUP, 0.6f, 1.4f);
    }

    private void startItemFlight(PlayerEntity player, boolean stolen) {
        this.dataTracker.set(ITEM_FLIGHT, stolen ? player.getId() + 1 : -(player.getId() + 1));
        itemFlightTicks = ITEM_FLIGHT_TICKS;
    }

    private void tickItemFlight() {
        if (itemFlightTicks <= 0 || --itemFlightTicks > 0) return;
        this.dataTracker.set(ITEM_FLIGHT, 0);
        this.dataTracker.set(SHOWN_ITEM, stolen.copy()); // given back: nothing left under it
    }

    private static final Vec3d UNDER_BODY = new Vec3d(0, -0.2, 0);

    /** Where the shown item is drawn, from its feet (null: none): under its body, or on its way to or from someone. */
    public @Nullable Vec3d itemOffset(float partialTick) {
        if (getShownItem().isEmpty()) return null;
        Vec3d flying = itemFlightOffset(partialTick);
        if (flying != null) return flying;
        // given back and arrived: nothing under it (until the server says so)
        return this.dataTracker.get(ITEM_FLIGHT) < 0 ? null : UNDER_BODY;
    }

    /** The item flying (client): where it is from its feet, on an arc between the player and its place under it. */
    private @Nullable Vec3d itemFlightOffset(float partialTick) {
        int flight = this.dataTracker.get(ITEM_FLIGHT);
        if (flight == 0 || itemFlightStart == Integer.MIN_VALUE) return null;
        float progress = (age - itemFlightStart + partialTick) / ITEM_FLIGHT_TICKS;
        if (progress >= 1) return null;
        Entity who = getWorld().getEntityById(Math.abs(flight) - 1);
        if (who == null) return null;
        Vec3d me = getLerpedPos(partialTick);
        Vec3d them = who.getLerpedPos(partialTick).add(0, who.getHeight() * 0.55, 0).subtract(me);
        float t = MathHelper.clamp(progress, 0, 1);
        t = t * t * (3 - 2 * t);
        if (flight < 0) t = 1 - t; // given back: from it to them
        Vec3d at = them.lerp(UNDER_BODY, t);
        return at.add(0, MathHelper.sin(t * MathHelper.PI) * 0.6, 0);
    }

    private void laugh() {
        playSound(ModSounds.FROUSSEUX_LAUGH, 1.0f, 0.95f + random.nextFloat() * 0.3f);
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
            dodge((ServerWorld) getWorld(), source.getAttacker());
            return false;
        }
        // its owner's arrows and the like never hurt it (it follows them about, it is often in the way)
        if (isTamed() && source.getAttacker() instanceof PlayerEntity player && isOwner(player)) return false;
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

    /**
     * Melee: nothing hurts it. It vanishes in a puff and comes back a few blocks away, as far from its attacker as it
     * can, facing them, laughing.
     */
    private void dodge(ServerWorld world, @Nullable Entity attacker) {
        if (dodgeCooldown > 0) return;
        dodgeCooldown = DODGE_COOLDOWN;
        Vec3d to = null;
        double best = -1;
        for (int i = 0; i < 4; i++) {
            Vec3d spot = FrousseuxFlight.hopTarget(this, random, DODGE_MIN, DODGE_MAX);
            if (spot == null) continue;
            double away = attacker == null ? 0 : attacker.squaredDistanceTo(spot);
            if (away > best) {
                best = away;
                to = spot;
            }
        }
        Vec3d from = getBoundingBox().getCenter();
        world.spawnParticles(ParticleTypes.POOF, from.x, from.y, from.z, 10, 0.15, 0.15, 0.15, 0.03);
        world.spawnParticles(ParticleTypes.SMOKE, from.x, from.y + 0.2, from.z, 4, 0.1, 0.1, 0.1, 0.01);
        if (to != null) {
            flight.stop();
            getNavigation().stop();
            if (attacker != null) {
                float yaw = (float) (MathHelper.atan2(attacker.getZ() - to.z, attacker.getX() - to.x)
                        * MathHelper.DEGREES_PER_RADIAN) - 90f;
                setYaw(yaw);
                setHeadYaw(yaw);
                setBodyYaw(yaw);
            }
            requestTeleport(to.x, to.y, to.z);
            world.spawnParticles(ParticleTypes.POOF, to.x, to.y + HEIGHT / 2, to.z, 6, 0.12, 0.12, 0.12, 0.02);
            world.spawnParticles(ParticleTypes.WAX_OFF, to.x, to.y + HEIGHT / 2, to.z, 3, 0.2, 0.2, 0.2, 0.5);
        }
        laugh();
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
        if (getWorld().isClient) return;
        light.clear(getWorld());
        if (!stolen.isEmpty()) {
            dropStack(stolen); // what it stole falls with it
            stolen = ItemStack.EMPTY;
            this.dataTracker.set(SHOWN_ITEM, ItemStack.EMPTY);
        }
    }

    // ---------------------------------------------------------------- flint and steel, taming, its owner's word

    /**
     * Flint and steel: a wild one gives back what it stole on the first strike, then each strike has a chance in
     * {@link #TAME_CHANCE} to tame it (hearts) or not (smoke); and it relights a weak flame. Its owner's empty hand
     * ({@link FrousseuxCompanion}): sneaking, a following one sits, a sitting one turns into a candle holder; a
     * sitting one gets up again on a plain click.
     */
    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (boardActor || !isAlive()) return super.interactMob(player, hand);
        if (stack.isOf(Items.FLINT_AND_STEEL)) {
            boolean hurt = getHealth() < getMaxHealth();
            if (isTamed() && !hurt) return ActionResult.PASS;
            if (getWorld() instanceof ServerWorld world) {
                if (!isTamed()) strikeToTame(world, player);
                if (hurt) relight(world);
                else world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_FLINTANDSTEEL_USE,
                        SoundCategory.NEUTRAL, 1.0f, 1.0f);
            }
            stack.damage(1, player, LivingEntity.getSlotForHand(hand));
            return ActionResult.success(getWorld().isClient);
        }
        if (hand == Hand.MAIN_HAND && stack.isEmpty() && isTamed() && isOwner(player)) {
            if (getWorld() instanceof ServerWorld world) {
                if (player.isSneaking() && isSitting()) FrousseuxCompanion.toCandleHolder(this, world, player);
                else if (player.isSneaking() || isSitting()) sit(world, !isSitting());
            }
            return ActionResult.success(getWorld().isClient);
        }
        return super.interactMob(player, hand);
    }

    private void strikeToTame(ServerWorld world, PlayerEntity player) {
        if (!stolen.isEmpty()) { // first, what it stole
            giveBack(player);
            return;
        }
        if (random.nextInt(TAME_CHANCE) == 0) tame(player);
        else world.spawnParticles(ParticleTypes.SMOKE, getX(), getBodyY(0.6), getZ(), 7, 0.2, 0.2, 0.2, 0.02);
    }

    /** Tamed by {@code player}: theirs from now on (hearts); it gives back what it stole. */
    public void tame(PlayerEntity player) {
        if (!stolen.isEmpty()) giveBack(player);
        setOwner(player.getUuid());
        setSitting(false);
        fleeTicks = 0;
        fleeFrom = null;
        shyTicks = 0;
        this.dataTracker.set(SHY, false);
        flight.stop();
        getNavigation().stop();
        if (getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.HEART, getX(), getBodyY(0.8), getZ(), 7, 0.25, 0.2, 0.25, 0.02);
        }
        laugh();
    }

    /** Sits down where it is (stays), or gets up and follows again. */
    public void sit(ServerWorld world, boolean sitting) {
        setSitting(sitting);
        flight.stop();
        getNavigation().stop();
        world.spawnParticles(sitting ? ParticleTypes.WAX_ON : ParticleTypes.WAX_OFF, getX(), getBodyY(0.5), getZ(),
                4, 0.25, 0.2, 0.25, 0.5);
        playSound(ModSounds.FROUSSEUX_AMBIENT, 0.6f, sitting ? 0.8f : 1.2f);
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
        // killed or discarded (despawned, /kill), or gone with its owner (recreated elsewhere): its light goes with
        // it. Unloaded with its chunk, the light stays saved with the world and comes back with it (FrousseuxLight)
        if ((reason.shouldDestroy() || reason == RemovalReason.CHANGED_DIMENSION) && !getWorld().isClient) light.clear(getWorld());
        super.remove(reason);
    }

    /** Recreated elsewhere (another dimension, or a long way with its owner): the light it left is not its own. */
    @Override
    public void copyFrom(Entity original) {
        super.copyFrom(original);
        light.forget();
    }

    // ---------------------------------------------------------------- with its owner anywhere (PetTeleports)

    @Override
    public @Nullable UUID followedOwner() {
        return getOwner();
    }

    /** Going along: tamed by them, following (not sitting, not on a lead or riding), not a board actor. */
    @Override
    public boolean goesWithOwner(ServerPlayerEntity owner) {
        return isAlive() && isOwner(owner) && !isSitting() && !boardActor && !isLeashed() && !hasVehicle();
    }

    @Override
    public Vec3d arrivalSpot(ServerPlayerEntity owner) {
        return FrousseuxCompanion.arrivalSpot(this, owner);
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
        UUID owner = getOwner();
        if (owner != null) nbt.putUuid("Owner", owner);
        if (isSitting()) nbt.putBoolean("Sitting", true);
        if (!stolen.isEmpty()) nbt.put("Stolen", stolen.encode(getRegistryManager()));
        if (stealCooldown > 0) nbt.putInt("StealCooldown", stealCooldown);
        light.write(nbt);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Color")) {
            setColor(FrousseuxColor.byName(nbt.getString("Color")));
            colorFromData = true;
        }
        setOwner(nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null);
        setSitting(nbt.getBoolean("Sitting"));
        stolen = nbt.contains("Stolen")
                ? ItemStack.fromNbt(getRegistryManager(), nbt.get("Stolen")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        this.dataTracker.set(SHOWN_ITEM, stolen.copy());
        stealCooldown = nbt.getInt("StealCooldown");
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
