package fr.lordfinn.steveparty.entities.custom.fumarole;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.mixin.LivingEntityJumpingAccessor;
import fr.lordfinn.steveparty.particles.ModParticles;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.mob.AbstractPiglinEntity;
import net.minecraft.entity.mob.PiglinBrain;
import net.minecraft.entity.mob.PiglinBruteEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Fumarole (Fumerolle): a huge, slow, three-headed tortoise of the Nether carrying a tank of lava on its back
 * (docs: SteveParty-Workshop/docs/tortue-du-nether.md).
 * <ul>
 *     <li><b>Its tank</b> ({@link #getTank()}): 0 to {@link #TANK_MAX} buckets, synced, saved; born at least half full
 *     ({@link #SPAWN_TANK_MIN}). Only lava goes in: a lava bucket pours one in, an empty bucket takes one out.</li>
 *     <li><b>Pumping</b> ({@link FumaroleGoals.Pump}): below a full tank it walks (or swims) to lava, dips its centre
 *     head and drinks: a bucket a gulp, the lava source left as it was.</li>
 *     <li><b>Taming</b>: empty its tank with buckets and it becomes tamable ({@link #isTamable}). Each head trusts
 *     whoever fed it a magma cream ({@link #feedHead}): it never shoots them again, whatever the others do. The
 *     three heads fed by the same player, once tamable: tamed, that player its owner.</li>
 *     <li><b>Untamed riders</b>: like a horse it lets you climb on; after a few seconds its heads fidget, then one turns
 *     round and sprays you off ({@link #sprayOff}): thrown high and back, a little fire unless fire-proof (a raised
 *     shield toward the head spares you that, not the fall off).</li>
 *     <li><b>Tamed</b>: its owner opens its saddle slot (sneaking, or with an empty hand while it has no saddle);
 *     saddled, up to three players ride it on the tank's front rim ({@link FumaroleRiding}).</li>
 *     <li><b>Its heads</b> ({@link #HEADS}): three necks, each a turret of its own: its own aim (synced:
 *     {@link #getHeadTarget}) and its own vent ({@link #getVent}). Wild, one blasts at a time, the heads taking turns
 *     ({@link FumaroleGoals.Blast}); ridden, each rider fires his own head.</li>
 *     <li><b>The thermal blast</b>: see {@link FumaroleBlast}.</li>
 *     <li><b>Neutral</b>: it leaves players alone until one provokes it ({@link #provoke}): hits it (projectiles too),
 *     takes lava from its tank while it is wild, or climbs on while it is wild. From then on it fights that player,
 *     and only him, for {@link #ANGER_TICKS} ticks, and calms down once that is over and he keeps
 *     {@link #CALM_DISTANCE} blocks from its shell. Its owner and players all its heads trust never provoke it. Mobs
 *     that hurt it are fought back (FumaroleGoals.Revenge).</li>
 *     <li><b>A hunter in the Nether</b>: wild and without piglins on, now and then it hunts a mob of
 *     {@code #steveparty:fumarole_prey} close by ({@link #findPrey}), never a player.</li>
 *     <li><b>Piglin riders</b>: near a bastion some are born with 1 to 3 piglins sat on its rim (FumaroleSpawns).
 *     Ridden by piglins it is hostile to the players its piglins are hostile to (a piglin spares a player in gold, a
 *     brute nobody), and the more piglins, the faster it walks ({@link #PIGLIN_SPEED}), the farther it spots players
 *     ({@link #PIGLIN_RANGE}) and the more often it shoots (FumaroleGoals.Blast#cooldownFactor); the piglins drive it
 *     toward its target. Once they are off, it is neutral again (unless provoked).</li>
 *     <li><b>In lava</b> it swims, floating with its tank out ({@link FumaroleRiding#SWIM_DEPTH}); it is born on the
 *     shores of the Nether's lava lakes or in them.</li>
 *     <li><b>Its death</b>: its lava spills (with mobGriefing, up to {@link #SPILL_MAX} sources), it drops its saddle and
 *     0 to 2 magma cream.</li>
 * </ul>
 */
public class FumaroleEntity extends PathAwareEntity implements GeoEntity {
    /** Its shell and legs: 52 px wide, the tank's rim 61 px high. The necks reach far beyond (not in the box). */
    public static final float WIDTH = 3.2f, HEIGHT = 3.8125f, EYE_HEIGHT = 2.76f;
    public static final int TANK_MAX = 27, SPAWN_TANK_MIN = 14, SPAWN_TANK_MAX = 20;
    public static final double MAX_HEALTH = 80, ARMOR = 10, SPEED = 0.09;
    /** The tank's front rim, where the riders sit: its height, how far ahead of the middle. */
    public static final double RIM_HEIGHT = 3.75, RIM_FORWARD = 1.06;

    /**
     * Its heads, centre first, and where each rests: measured on the v14 export (pose_s): the centre neck 11 segments
     * long, the side ones 8, splayed 30 degrees out from 13 px either side.
     */
    public static final FumaroleHead[] HEADS = {
            new FumaroleHead(0, "_c", 0.0, 1.125, 8.31, 2.76, 0, 20, 4.11, 4.33, 0.0),
            new FumaroleHead(1, "_l", -0.8125, 1.125, 7.38, 2.13, -30, 20, 3.17, 3.66, -0.8),
            new FumaroleHead(2, "_r", 0.8125, 1.125, 7.76, 2.89, 30, 5, 3.34, 3.38, 0.8),
    };
    /** The head that dips into the lava to pump. */
    public static final int PUMP_HEAD = 0;
    /**
     * A head turns at most this far (degrees) from its rest, this fast (degrees a tick) when it aims (a blast, a jump's
     * thrusters), and only {@link #HEAD_EASE} a tick otherwise (moods, a rider's look, back to rest): a heavy beast.
     */
    public static final float HEAD_YAW_MAX = 75, HEAD_TURN = 15, HEAD_EASE = 3;
    /** A wild head's neck turning to lock on its blast's target (degrees a tick): slow, readable. */
    public static final float BLAST_TURN = 6;
    /** Its body turns at most this fast (degrees a tick) walking, and its look (so its idle body) {@link #LOOK_TURN}. */
    public static final float BODY_TURN = 3, LOOK_TURN = 4;

    public static final double PUMP_MIN = 2.0, PUMP_MAX = 9.0, PUMP_DOWN = 6.0, PUMP_UP = 1.0;
    /** How long a provocation lasts (ticks), and how far (blocks from its shell) the player must keep to calm it. */
    public static final int ANGER_TICKS = 600;
    public static final double CALM_DISTANCE = 16.0;
    /** The prey it hunts (wild, no piglins on): within this range of its shell, one look every so often. */
    public static final TagKey<EntityType<?>> PREY = TagKey.of(RegistryKeys.ENTITY_TYPE, Steveparty.id("fumarole_prey"));
    public static final double HUNT_RANGE = 20.0;
    public static final int HUNT_PERIOD = 20, HUNT_CHANCE = 4;
    /** With 0 to 3 piglins on: its walking speed bonus (share of its base), how far (from its shell) it spots players. */
    public static final double[] PIGLIN_SPEED = {0, 0.3, 0.6, 0.9};
    public static final double[] PIGLIN_RANGE = {0, 16, 24, 32};
    private static final Identifier PIGLIN_SPEED_ID = Steveparty.id("fumarole_piglin_riders");
    /** Lava sources spilt on death: one per this many buckets, at most {@link #SPILL_MAX}. */
    public static final int SPILL_PER = 9, SPILL_MAX = 3;
    /** An untamed one throws its rider off after this many ticks (and up to this many more), fidgeting before. */
    public static final int THROW_MIN = 60, THROW_SPREAD = 60, FIDGET_TICKS = 40;
    /** The throw: up and back. */
    public static final double THROW_UP = 1.3, THROW_BACK = 1.0;
    public static final float THROW_FIRE_SECONDS = 2.0f;

    public static final byte VENT_IDLE = 0, VENT_CHARGING = 1, VENT_SPITTING = 2;
    /** Entity statuses (clients: its moods): a head fed (+ head), a head sulking (+ head), tamed. */
    public static final byte STATUS_FED = 100, STATUS_SULK = 110, STATUS_TAMED = 120, STATUS_TAMABLE = 121;

    /** The scalding steam's damage type (data/steveparty/damage_type/thermal_steam.json). */
    public static final RegistryKey<DamageType> THERMAL_STEAM = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Steveparty.id("thermal_steam"));

    private static final TrackedData<Integer> TANK = DataTracker.registerData(FumaroleEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> VENTS = DataTracker.registerData(FumaroleEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> PUMPING = DataTracker.registerData(FumaroleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    /** Bits: tamed, tamable, saddled, climbing. */
    private static final TrackedData<Byte> FLAGS = DataTracker.registerData(FumaroleEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Integer> CHARGE = DataTracker.registerData(FumaroleEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final int TAMED = 1, TAMABLE = 2, SADDLED = 4, CLIMBING = 8;
    private static final List<TrackedData<Integer>> HEAD_TARGETS = new ArrayList<>();

    static {
        for (FumaroleHead ignored : HEADS) {
            HEAD_TARGETS.add(DataTracker.registerData(FumaroleEntity.class, TrackedDataHandlerRegistry.INTEGER));
        }
    }

    public static final String ANIM_IDLE = "animation.nether_turtle.idle", ANIM_WALK = "animation.nether_turtle.walk",
            ANIM_PUMP = "animation.nether_turtle.pump", ANIM_SPIT = "animation.nether_turtle.spit";
    /** The idle and walk animations' playing speed (1: as authored). */
    static final double IDLE_PACE = 0.6, WALK_PACE = 0.75;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIM_IDLE);
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(ANIM_WALK);
    private static final RawAnimation PUMP = RawAnimation.begin().thenLoop(ANIM_PUMP);
    public static final String MAIN_CONTROLLER = "main", ACTION_CONTROLLER = "action";

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    final FumarolePumping pumping = new FumarolePumping();
    /** Its saddle slot. */
    public final SimpleInventory inventory = new SimpleInventory(1);
    /** The players each head trusts (fed it a magma cream). */
    private final List<Set<UUID>> trust = new ArrayList<>();
    private @Nullable UUID owner;
    /** The players who provoked it, and until when (world time) it stays angry with each. */
    private final Map<UUID, Long> grudges = new HashMap<>();
    /** Until when (age) it stays angry with a mob that hurt it. */
    private int angryUntil;
    /** The prey it hunts, while it is its target (given up beyond its hunting range). */
    private @Nullable LivingEntity hunted;
    /** Server: each head's aim (world yaw; degrees), and when it may fire again (ridden). */
    private final float[] aimYaw = new float[HEADS.length];
    private final long[] headReady = new long[HEADS.length];
    /** Server: the jump charge being held, the climb under way. */
    private int charge;
    private @Nullable Vec3d climbTo;
    private int climbTicks;
    private boolean airborneJump;
    /** Where its last leap started (height). */
    private double jumpFromY;
    /** Ticks its heads keep reaching for a wall after a click in the air. */
    private int grabArmed;
    private static final int GRAB_ARMED_TICKS = 30;
    /** Server: when it throws its untamed rider off. */
    private long throwAt = -1;

    /** Client only: the tank's drawn level, easing toward the synced one (buckets). */
    public float clientTankLevel = -1, prevClientTankLevel = -1;
    /** Client: how much lower it is drawn standing in shallow lava (eased; see {@link #lavaSink}). */
    private float clientSink, prevClientSink;
    /** Client only: each head's drawn aim (yaw from the body, Minecraft pitch), eased; and last tick's; its roll. */
    public final float[] clientYaw = new float[HEADS.length], clientPitch = new float[HEADS.length];
    public final float[] prevClientYaw = new float[HEADS.length], prevClientPitch = new float[HEADS.length];
    /** Client only: its personality. */
    public final FumaroleMoods moods = new FumaroleMoods();
    private boolean wasSwimming;

    public FumaroleEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = 15;
        this.moveControl = new HeavyMoveControl(this);
        setPathfindingPenalty(PathNodeType.LAVA, 0.0f);
        setPathfindingPenalty(PathNodeType.DANGER_FIRE, 0.0f);
        setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, 0.0f);
        setPathfindingPenalty(PathNodeType.WATER, 8.0f);
        for (int i = 0; i < HEADS.length; i++) {
            trust.add(new HashSet<>());
            clientPitch[i] = prevClientPitch[i] = HEADS[i].restPitch();
            clientYaw[i] = prevClientYaw[i] = HEADS[i].restYaw();
        }
        inventory.addListener(inv -> {
            if (!getWorld().isClient) setBit(SADDLED, inv.getStack(0).isOf(Items.SADDLE));
        });
    }

    public static DefaultAttributeContainer.Builder setAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
                .add(EntityAttributes.GENERIC_ARMOR, ARMOR)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new FumaroleGoals.Blast(this));
        this.goalSelector.add(2, new FumaroleGoals.Pump(this));
        this.goalSelector.add(3, new FumaroleGoals.Approach(this));
        this.goalSelector.add(5, new WanderAroundGoal(this, 1.0, 160));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
        this.goalSelector.add(7, new LookAroundGoal(this));
        this.targetSelector.add(1, new FumaroleGoals.Revenge(this));
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TANK, 0);
        builder.add(VENTS, 0);
        builder.add(PUMPING, false);
        builder.add(FLAGS, (byte) 0);
        builder.add(CHARGE, 0);
        for (TrackedData<Integer> target : HEAD_TARGETS) builder.add(target, -1);
    }

    @Override
    public @Nullable EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
                                           @Nullable EntityData entityData) {
        setTank(SPAWN_TANK_MIN + world.getRandom().nextInt(SPAWN_TANK_MAX - SPAWN_TANK_MIN + 1));
        EntityData data = super.initialize(world, difficulty, spawnReason, entityData);
        if (spawnReason == SpawnReason.NATURAL || spawnReason == SpawnReason.CHUNK_GENERATION) {
            int piglins = FumaroleSpawns.piglinRiders(world, getBlockPos(), world.getRandom());
            if (piglins > 0) mountPiglins(world, difficulty, piglins);
        }
        return data;
    }

    /** Born on the shore or in the lava itself: only other mobs and blocks keep it from a spot, not lava. */
    @Override
    public boolean canSpawn(WorldView world) {
        return world.doesNotIntersectEntities(this) && world.isSpaceEmpty(this);
    }

    // ---------------------------------------------------------------- state

    private boolean bit(int bit) {
        return (dataTracker.get(FLAGS) & bit) != 0;
    }

    private void setBit(int bit, boolean on) {
        byte flags = dataTracker.get(FLAGS);
        dataTracker.set(FLAGS, (byte) (on ? flags | bit : flags & ~bit));
    }

    public boolean isTamed() {
        return bit(TAMED);
    }

    public boolean isTamable() {
        return bit(TAMABLE);
    }

    public boolean isSaddled() {
        return bit(SADDLED);
    }

    public boolean isClimbing() {
        return bit(CLIMBING);
    }

    public @Nullable UUID getOwner() {
        return owner;
    }

    public boolean isOwner(PlayerEntity player) {
        return owner != null && owner.equals(player.getUuid());
    }

    /** The jump charge held by its riders (ticks). */
    public int getCharge() {
        return dataTracker.get(CHARGE);
    }

    public int getTank() {
        return dataTracker.get(TANK);
    }

    public void setTank(int buckets) {
        dataTracker.set(TANK, MathHelper.clamp(buckets, 0, TANK_MAX));
    }

    public byte getVent(int head) {
        return (byte) ((dataTracker.get(VENTS) >> (2 * head)) & 3);
    }

    public void setVent(int head, byte vent) {
        int all = dataTracker.get(VENTS) & ~(3 << (2 * head));
        dataTracker.set(VENTS, all | (vent & 3) << (2 * head));
    }

    public @Nullable Entity getHeadTarget(int head) {
        int id = dataTracker.get(HEAD_TARGETS.get(head));
        return id < 0 ? null : getWorld().getEntityById(id);
    }

    public void setHeadTarget(int head, @Nullable Entity target) {
        dataTracker.set(HEAD_TARGETS.get(head), target == null ? -1 : target.getId());
    }

    public boolean isPumping() {
        return dataTracker.get(PUMPING);
    }

    void setPumping(boolean pumping) {
        dataTracker.set(PUMPING, pumping);
    }

    public float tankLevel(float partialTick) {
        if (clientTankLevel < 0) return getTank();
        return MathHelper.lerp(partialTick, prevClientTankLevel, clientTankLevel);
    }

    /** Whether this head trusts this player (fed it a magma cream, or owns the turtle). */
    public boolean trusts(int head, PlayerEntity player) {
        return isOwner(player) || trust.get(head).contains(player.getUuid());
    }

    /** Whether every head trusts this player. */
    public boolean trustedByAll(PlayerEntity player) {
        for (int head = 0; head < HEADS.length; head++) if (!trusts(head, player)) return false;
        return true;
    }

    /** Whether it is swimming: deep in lava. */
    public boolean isSwimmingInLava() {
        return isInLava() && getFluidHeight(FluidTags.LAVA) > FumaroleRiding.SWIM_MIN_DEPTH;
    }

    // ---------------------------------------------------------------- pumping

    public boolean canReach(BlockPos source) {
        Vec3d center = Vec3d.ofCenter(source);
        double dx = center.x - getX(), dz = center.z - getZ(), dy = center.y - getY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return horizontal >= PUMP_MIN && horizontal <= PUMP_MAX && dy >= -PUMP_DOWN && dy <= PUMP_UP;
    }

    /** Whether it may drink from this lava now: tank not full, a lava source there, its pace (FumarolePumping). */
    public boolean canPump(BlockPos source) {
        return getTank() < TANK_MAX && pumping.rateAllows(getWorld().getTime()) && FumarolePumping.isSource(getWorld(), source);
    }

    /** Drinks a bucket from this lava: the tank gains one, the source stays. False if it may not. */
    public boolean pump(BlockPos source) {
        if (getWorld().isClient || !canPump(source)) return false;
        pumping.record(getWorld().getTime());
        setTank(getTank() + 1);
        playSound(ModSounds.FUMAROLE_PUMP, 1.2f, 0.8f + random.nextFloat() * 0.2f);
        if (getWorld() instanceof ServerWorld server) {
            Vec3d at = Vec3d.ofCenter(source);
            server.spawnParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 6, 0.4, 0.2, 0.4, 0.0);
            server.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.8, at.z, 4, 0.3, 0.2, 0.3, 0.02);
        }
        return true;
    }

    // ---------------------------------------------------------------- interactions

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!isAlive()) return super.interactMob(player, hand);
        boolean client = getWorld().isClient;
        if (stack.isOf(Items.BUCKET)) {
            if (getTank() <= 0) return ActionResult.PASS;
            if (!client) takeBucket(player, hand);
            return ActionResult.success(client);
        }
        if (stack.isOf(Items.LAVA_BUCKET)) {
            if (getTank() >= TANK_MAX) return ActionResult.PASS;
            if (!client) {
                setTank(getTank() + 1);
                player.setStackInHand(hand, ItemUsage.exchangeStack(stack, player, new ItemStack(Items.BUCKET)));
                playSound(SoundEvents.ITEM_BUCKET_EMPTY_LAVA, 1.0f, 1.0f);
                playSound(ModSounds.FUMAROLE_GURGLE, 0.8f, 1.0f);
            }
            return ActionResult.success(client);
        }
        if (stack.isOf(Items.MAGMA_CREAM)) {
            if (!client) {
                int head = headLookedAt(player);
                feedHead(player, head < 0 ? nearestHead(player) : head, stack);
            }
            return ActionResult.success(client);
        }
        if (isTamed() && isOwner(player) && stack.isOf(Items.SADDLE) && !isSaddled()) {
            if (!client) {
                inventory.setStack(0, stack.split(1));
                playSound(SoundEvents.ENTITY_HORSE_SADDLE, 1.0f, 0.8f);
            }
            return ActionResult.success(client);
        }
        if (isTamed() && isOwner(player) && (player.shouldCancelInteraction() || !isSaddled())) {
            if (!client) openInventory(player);
            return ActionResult.success(client);
        }
        if (stack.isEmpty() && canAddPassenger(player) && !player.shouldCancelInteraction()) {
            if (isTamed() && !isSaddled()) return ActionResult.PASS;
            if (!client) mount(player);
            return ActionResult.success(client);
        }
        return super.interactMob(player, hand);
    }

    /** An empty bucket takes a bucket of lava; emptied, an untamed one becomes tamable. */
    public void takeBucket(PlayerEntity player, Hand hand) {
        if (!isTamed()) provoke(player); // stealing a wild one's lava
        setTank(getTank() - 1);
        player.setStackInHand(hand, ItemUsage.exchangeStack(player.getStackInHand(hand), player, new ItemStack(Items.LAVA_BUCKET)));
        playSound(SoundEvents.ITEM_BUCKET_FILL_LAVA, 1.0f, 1.0f);
        playSound(ModSounds.FUMAROLE_GURGLE, 0.8f, 1.0f);
        if (getTank() == 0 && !isTamed() && !isTamable()) {
            setBit(TAMABLE, true);
            getWorld().sendEntityStatus(this, STATUS_TAMABLE);
            playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.5f);
        }
    }

    /**
     * Feeds a head a magma cream: it trusts this player from now on (never shoots him) and wiggles. When all the heads
     * trust the same player and it is tamable: tamed, him its owner. A tamed one is healed instead.
     */
    public void feedHead(PlayerEntity player, int head, ItemStack cream) {
        if (head < 0) return;
        if (isTamed()) {
            heal(10);
        } else {
            trust.get(head).add(player.getUuid());
            if (isTamable() && trustedByAll(player)) tame(player);
        }
        if (!player.getAbilities().creativeMode) cream.decrement(1);
        playSound(SoundEvents.ENTITY_GENERIC_EAT, 1.0f, 0.6f);
        getWorld().sendEntityStatus(this, (byte) (STATUS_FED + head));
        if (getTarget() instanceof PlayerEntity target && target == player && trustedByAll(player)) setTarget(null);
    }

    private void tame(PlayerEntity player) {
        owner = player.getUuid();
        setBit(TAMED, true);
        setPersistent();
        setTarget(null);
        getWorld().sendEntityStatus(this, STATUS_TAMED);
    }

    /** How far a player reaches a head with his hand (they are big and far from its shell). */
    public static final double HEAD_REACH = 12;

    /** The head this player's crosshair points at (within {@link #HEAD_REACH} blocks), or -1. */
    public int headLookedAt(PlayerEntity player) {
        Vec3d eye = player.getEyePos(), look = player.getRotationVector();
        int best = -1;
        double bestAlong = Double.MAX_VALUE;
        for (int head = 0; head < HEADS.length; head++) {
            Vec3d center = headCenter(head);
            double along = center.subtract(eye).dotProduct(look);
            if (along < 0 || along > HEAD_REACH) continue;
            if (eye.add(look.multiply(along)).squaredDistanceTo(center) > 1.8 * 1.8) continue;
            if (along < bestAlong) {
                bestAlong = along;
                best = head;
            }
        }
        return best;
    }

    private int nearestHead(PlayerEntity player) {
        int best = 0;
        for (int head = 1; head < HEADS.length; head++) {
            if (headCenter(head).squaredDistanceTo(player.getPos()) < headCenter(best).squaredDistanceTo(player.getPos())) best = head;
        }
        return best;
    }

    /** The middle of a head (a little behind its nozzle). */
    public Vec3d headCenter(int head) {
        Vec3d nozzle = nozzle(head);
        float yaw = getWorld().isClient ? bodyYaw + clientYaw[head] : aimYaw[head];
        return nozzle.subtract(Vec3d.fromPolar(0, yaw).multiply(1.6));
    }

    private void openInventory(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        serverPlayer.openHandledScreen(new ExtendedScreenHandlerFactory<Integer>() {
            @Override
            public Integer getScreenOpeningData(ServerPlayerEntity opener) {
                return getId();
            }

            @Override
            public Text getDisplayName() {
                return FumaroleEntity.this.getDisplayName();
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity opener) {
                return new FumaroleScreenHandler(syncId, playerInventory, inventory, FumaroleEntity.this);
            }
        });
    }

    private void mount(PlayerEntity player) {
        player.setYaw(getYaw());
        player.startRiding(this);
        if (!isTamed()) {
            scheduleThrow();
            provoke(player); // climbing on a wild one: once thrown off, it fights him
        }
    }

    private void scheduleThrow() {
        throwAt = getWorld().getTime() + THROW_MIN + random.nextInt(THROW_SPREAD + 1);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengerList().size() < FumaroleRiding.MAX_RIDERS;
    }

    /** Its riders, seat order (centre, left, right). */
    public List<Entity> riders() {
        return getPassengerList();
    }

    public @Nullable PlayerEntity riderOf(int head) {
        List<Entity> riders = getPassengerList();
        return head < riders.size() && riders.get(head) instanceof PlayerEntity player ? player : null;
    }

    /** Ridden and steered: tamed, saddled, with a player on. */
    public boolean isSteered() {
        return isTamed() && isSaddled() && getFirstPassenger() instanceof PlayerEntity;
    }

    @Override
    protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        int index = Math.max(0, getPassengerList().indexOf(passenger));
        return FumaroleRiding.seat(index, RIM_HEIGHT - lavaSink(1), RIM_FORWARD).rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!isTamed() && !hasPlayerRider()) throwAt = -1;
        updatePiglinSpeed();
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        updatePiglinSpeed();
    }

    /** Its piglins ride, they don't drive: its own goals keep walking and aiming it (FumaroleGoals.Approach). */
    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return null;
    }

    /** Whether a player rides it. */
    public boolean hasPlayerRider() {
        for (Entity rider : getPassengerList()) if (rider instanceof PlayerEntity) return true;
        return false;
    }

    /** How many piglins (brutes too) ride it. */
    public int piglinRiders() {
        int n = 0;
        for (Entity rider : getPassengerList()) if (rider instanceof AbstractPiglinEntity) n++;
        return Math.min(n, PIGLIN_SPEED.length - 1);
    }

    /** Whether one of its piglins is hostile to this player: a brute always, a piglin unless he wears gold. */
    public boolean ridersHostileTo(PlayerEntity player) {
        for (Entity rider : getPassengerList()) {
            if (rider instanceof PiglinBruteEntity) return true;
            if (rider instanceof PiglinEntity piglin && !piglin.isBaby() && !PiglinBrain.wearsGoldArmor(player)) return true;
        }
        return false;
    }

    private void updatePiglinSpeed() {
        if (getWorld() == null || getWorld().isClient) return;
        EntityAttributeInstance speed = getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(PIGLIN_SPEED_ID);
        int piglins = piglinRiders();
        if (piglins > 0) {
            speed.addTemporaryModifier(new EntityAttributeModifier(PIGLIN_SPEED_ID, PIGLIN_SPEED[piglins],
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    /**
     * Seats {@code count} piglins on its rim (one in ten a brute), adults armed as vanilla arms them; they are spawned
     * with it (spawnEntityAndPassengers).
     */
    public void mountPiglins(ServerWorldAccess world, LocalDifficulty difficulty, int count) {
        for (int i = 0; i < count && getPassengerList().size() < FumaroleRiding.MAX_RIDERS; i++) {
            EntityType<? extends AbstractPiglinEntity> type = random.nextInt(10) == 0 ? EntityType.PIGLIN_BRUTE : EntityType.PIGLIN;
            AbstractPiglinEntity piglin = type.create(world.toServerWorld());
            if (piglin == null) return;
            piglin.refreshPositionAndAngles(getX(), getY(), getZ(), getYaw(), 0);
            piglin.initialize(world, difficulty, SpawnReason.JOCKEY, null);
            if (piglin instanceof PiglinEntity adult) adult.setBaby(false);
            piglin.startRiding(this, true);
        }
    }

    /**
     * An untamed one's answer to a rider: a head turns round and sprays him off, throwing him high and back, a little
     * fire unless fire-proof. A shield raised toward the head spares him the throw and the fire (he still gets off).
     */
    public void sprayOff(Entity rider) {
        int head = random.nextInt(HEADS.length);
        rider.stopRiding();
        if (!(getWorld() instanceof ServerWorld world)) return;
        Vec3d from = nozzle(head);
        Vec3d away = rider.getPos().subtract(getPos());
        away = new Vec3d(away.x, 0, away.z);
        if (away.lengthSquared() < 1.0e-4) away = Vec3d.fromPolar(0, getYaw() + 180);
        away = away.normalize();
        world.spawnParticles(ModParticles.THERMAL_PLUME, rider.getX(), rider.getY() + 0.5, rider.getZ(), 12, 0.5, 0.4, 0.5, 0.05);
        world.spawnParticles(ModParticles.THERMAL_POOF, from.x, from.y, from.z, 4, 0.3, 0.3, 0.3, 0.02);
        playSound(ModSounds.FUMAROLE_PUFF, 2.0f, 0.7f);
        world.sendEntityStatus(this, (byte) (STATUS_SULK + head));
        if (rider instanceof LivingEntity living) {
            if (FumaroleBlast.shields(living, rider.getPos().subtract(from).normalize())) {
                world.playSound(null, rider.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0f, 0.8f);
                return;
            }
            if (!FumaroleBlast.fireProof(living)) living.setOnFireFor(THROW_FIRE_SECONDS);
        }
        rider.setVelocity(away.x * THROW_BACK, THROW_UP, away.z * THROW_BACK);
        rider.velocityModified = true;
    }

    // ---------------------------------------------------------------- the heads

    public Vec3d nozzle(FumaroleHead head, float bodyYaw, float yaw) {
        return getPos().add(Vec3d.fromPolar(0, bodyYaw).multiply(head.base()))
                .add(Vec3d.fromPolar(0, bodyYaw + 90).multiply(head.side()))
                .add(Vec3d.fromPolar(0, yaw).multiply(head.reach()))
                .add(0, head.up(), 0);
    }

    /** Standing on the bottom of lava deep enough to cover its knees: {@link FumaroleRiding#SHALLOW_SINK}, else 0. */
    private float sinkGoal() {
        return isOnGround() && isInLava() && getFluidHeight(FluidTags.LAVA) > FumaroleRiding.SWIM_MIN_DEPTH
                ? FumaroleRiding.SHALLOW_SINK : 0;
    }

    /** How much lower it is drawn, its riders sat (client: eased; server: as it stands). */
    public float lavaSink(float partialTick) {
        return getWorld().isClient ? MathHelper.lerp(partialTick, prevClientSink, clientSink) : sinkGoal();
    }

    public Vec3d nozzle(int head) {
        float yaw = getWorld().isClient ? bodyYaw + clientYaw[head] : aimYaw[head];
        return nozzle(HEADS[head], bodyYaw, yaw);
    }

    /** Where a rider's reins hold a head: the top of its neck (client: as drawn). */
    public Vec3d neckTop(int head, float partialTick) {
        FumaroleHead h = HEADS[head];
        float body = MathHelper.lerpAngleDegrees(partialTick, prevBodyYaw, bodyYaw);
        float yaw = body + MathHelper.lerp(partialTick, prevClientYaw[head], clientYaw[head]);
        Vec3d pos = getLerpedPos(partialTick).add(0, -lavaSink(partialTick), 0);
        return pos.add(Vec3d.fromPolar(0, body).multiply(h.base()))
                .add(Vec3d.fromPolar(0, body + 90).multiply(h.side()))
                .add(Vec3d.fromPolar(0, yaw).multiply(h.neckReach()))
                .add(0, h.neckUp(), 0);
    }

    public static Vec3d aimPoint(Entity target) {
        return target.getPos().add(0, target.getHeight() * 0.5, 0);
    }

    public Vec3d blastOrigin(int head) {
        Vec3d nozzle = nozzle(head);
        Vec3d base = getPos().add(0, HEADS[head].up(), 0);
        BlockHitResult hit = getWorld().raycast(new RaycastContext(base, nozzle, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, this));
        if (hit.getType() == HitResult.Type.MISS) return nozzle;
        return hit.getPos().add(base.subtract(hit.getPos()).normalize().multiply(0.3));
    }

    /** Server: turns a head toward a point (at most {@link #HEAD_TURN} a tick unless {@code instant}). */
    public void aimHeadAt(int head, Vec3d point, boolean instant) {
        aimHeadAt(head, point, instant ? 360 : HEAD_TURN);
    }

    /** Server: turns a head toward a point, at most {@code step} degrees a tick. */
    public void aimHeadAt(int head, Vec3d point, float step) {
        Vec3d to = point.subtract(nozzle(HEADS[head], bodyYaw, aimYaw[head]));
        float yaw = (float) (MathHelper.atan2(to.z, to.x) * MathHelper.DEGREES_PER_RADIAN) - 90;
        float rest = HEADS[head].restYaw();
        float rel = rest + MathHelper.clamp(MathHelper.wrapDegrees(yaw - bodyYaw - rest), -HEAD_YAW_MAX, HEAD_YAW_MAX);
        float current = MathHelper.wrapDegrees(aimYaw[head] - bodyYaw);
        aimYaw[head] = bodyYaw + current + MathHelper.clamp(MathHelper.wrapDegrees(rel - current), -step, step);
    }

    public void aimHead(int head, Entity target, boolean instant) {
        aimHeadAt(head, aimPoint(target), instant);
    }

    public void restHead(int head) {
        aimYaw[head] = bodyYaw + HEADS[head].restYaw();
    }

    /** How far {@code entity} stands from its shell: hitbox to hitbox (0: touching it). */
    public double distanceFromShell(Entity entity) {
        Box shell = getBoundingBox(), other = entity.getBoundingBox();
        double dx = Math.max(0, Math.max(shell.minX - other.maxX, other.minX - shell.maxX));
        double dy = Math.max(0, Math.max(shell.minY - other.maxY, other.minY - shell.maxY));
        double dz = Math.max(0, Math.max(shell.minZ - other.maxZ, other.minZ - shell.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public boolean inRange(int head, Entity target) {
        return inRange(head, aimPoint(target));
    }

    /** Whether this head's neck can turn to {@code target} without the body turning first ({@link #HEAD_YAW_MAX}). */
    public boolean faces(int head, Entity target) {
        Vec3d to = target.getPos().subtract(getPos());
        float yaw = (float) (MathHelper.atan2(to.z, to.x) * MathHelper.DEGREES_PER_RADIAN) - 90;
        return Math.abs(MathHelper.wrapDegrees(yaw - bodyYaw - HEADS[head].restYaw())) <= HEAD_YAW_MAX;
    }

    public boolean faces(Entity target) {
        for (int head = 0; head < HEADS.length; head++) if (faces(head, target)) return true;
        return false;
    }

    public boolean inRange(int head, Vec3d point) {
        double range = getTank() > 0 ? FumaroleBlast.RANGE : FumaroleBlast.PUFF_RANGE;
        return blastOrigin(head).squaredDistanceTo(point) <= range * range;
    }

    public boolean inRange(Entity target) {
        for (int head = 0; head < HEADS.length; head++) if (inRange(head, target)) return true;
        return false;
    }

    /** Whether this head would shoot at this entity: never at one it trusts. */
    public boolean mayShoot(int head, Entity target) {
        return !(target instanceof PlayerEntity player && trusts(head, player));
    }

    /** One head fires at a target (wild): see {@link FumaroleBlast}. */
    public List<LivingEntity> blast(int head, Entity target) {
        return blastAt(head, aimPoint(target));
    }

    /** One head fires straight at a point (wild: the aim it locked, no homing). */
    public List<LivingEntity> blastAt(int head, Vec3d point) {
        return FumaroleBlast.fire(this, head, blastOrigin(head), point);
    }

    /** A rider's click on his head: fires where he looks; in the air after a leap, grabs the wall instead. */
    public void riderClick(PlayerEntity rider) {
        int head = getPassengerList().indexOf(rider);
        if (head < 0 || head >= HEADS.length || !isSteered()) return;
        if (airborneJump && !isOnGround() && climbTo == null) {
            if (!tryGrab()) grabArmed = GRAB_ARMED_TICKS; // no wall within reach yet: the heads keep reaching out
            return;
        }
        long now = getWorld().getTime();
        if (now < headReady[head]) return;
        headReady[head] = now + FumaroleRiding.FIRE_COOLDOWN;
        Vec3d eye = rider.getEyePos();
        Vec3d end = eye.add(rider.getRotationVector().multiply(FumaroleBlast.RANGE));
        BlockHitResult hit = getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, rider));
        Vec3d aim = hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
        aimHeadAt(head, aim, true);
        setVent(head, VENT_SPITTING);
        FumaroleBlast.fire(this, head, blastOrigin(head), aim);
        ventIdleAt[head] = now + 10;
    }

    private final long[] ventIdleAt = new long[HEADS.length];

    // ---------------------------------------------------------------- jumping, climbing

    /** The thrusters: a leap for this charge, paid from the tank. False if it can't (no ground or lava, no lava left). */
    public boolean thrusterJump(int charge) {
        if (charge < FumaroleRiding.CHARGE_MIN || getTank() <= 0 || !(standing() || isSwimmingInLava())) return false;
        setTank(getTank() - FumaroleRiding.jumpCost(charge));
        Vec3d leap = FumaroleRiding.jumpVelocity(charge, getYaw());
        setVelocity(leap);
        velocityDirty = true;
        velocityModified = true;
        airborneJump = true;
        jumpFromY = getY();
        playSound(ModSounds.FUMAROLE_BLAST, 3.0f, 0.6f);
        if (getWorld() instanceof ServerWorld world) {
            for (int head = 0; head < HEADS.length; head++) {
                Vec3d at = nozzle(head);
                world.spawnParticles(ModParticles.THERMAL_PLUME, at.x, at.y - 0.5, at.z, 0, 0, -0.6, 0, 1.0);
                world.spawnParticles(ModParticles.THERMAL_POOF, at.x, at.y - 1, at.z, 5, 0.4, 0.2, 0.4, 0.02);
                for (int k = 1; k < 6; k++) {
                    world.spawnParticles(ModParticles.THERMAL_PLUME, at.x, at.y - k * 0.7, at.z, 0,
                            random.nextGaussian() * 0.05, -0.5, random.nextGaussian() * 0.05, 1.0);
                }
            }
            world.spawnParticles(ParticleTypes.GUST, getX(), getY(), getZ(), 2, 1, 0, 1, 0);
        }
        return true;
    }

    /** On something solid: on the ground, or a block right under its feet. */
    public boolean standing() {
        if (isOnGround()) return true;
        BlockPos under = BlockPos.ofFloored(getX(), getY() - 0.05, getZ());
        return getY() - Math.floor(getY()) < 0.05 && getWorld().getBlockState(under).isSolidBlock(getWorld(), under);
    }

    /** In the air after a leap: the heads grab the wall ahead and pull it onto the ledge, if there is one. */
    public boolean tryGrab() {
        Vec3d ledge = FumaroleRiding.findLedge(getWorld(), getPos(), getYaw(), WIDTH / 2, HEIGHT, jumpFromY);
        if (ledge == null) return false;
        climbTo = ledge;
        climbTicks = 0;
        setBit(CLIMBING, true);
        playSound(SoundEvents.BLOCK_BASALT_PLACE, 2.0f, 0.6f);
        if (getWorld() instanceof ServerWorld world) {
            for (int head = 0; head < HEADS.length; head++) {
                Vec3d at = nozzle(head);
                world.spawnParticles(ParticleTypes.POOF, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.02);
            }
        }
        return true;
    }

    private void tickClimb() {
        if (climbTo == null) return;
        climbTicks++;
        if (getY() < climbTo.y + 0.05) {
            setVelocity(0, FumaroleRiding.CLIMB_SPEED, 0);
        } else {
            Vec3d over = new Vec3d(climbTo.x - getX(), 0, climbTo.z - getZ());
            if (over.horizontalLength() < 0.3) {
                endClimb();
                return;
            }
            Vec3d step = over.normalize().multiply(Math.min(FumaroleRiding.CLIMB_OVER_SPEED, over.horizontalLength()));
            setVelocity(step.x, 0.02, step.z);
        }
        velocityDirty = true;
        if (climbTicks > FumaroleRiding.CLIMB_TIMEOUT) endClimb();
    }

    private void endClimb() {
        climbTo = null;
        airborneJump = false;
        setBit(CLIMBING, false);
    }

    @Override
    protected int computeFallDamage(float fallDistance, float damageMultiplier) {
        return super.computeFallDamage(fallDistance - 12, damageMultiplier); // its heads and steam soften a landing
    }

    // ---------------------------------------------------------------- moving

    @Override
    public void travel(Vec3d input) {
        if (climbTo != null) {
            move(MovementType.SELF, getVelocity());
            return;
        }
        if (isSteered()) {
            rideTravel();
            return;
        }
        if (isSwimmingInLava()) {
            swim(input, getMovementSpeed());
            return;
        }
        super.travel(input);
    }

    /** Floats in lava, its tank out, and moves along {@code input} at {@code speed} (blocks a tick, steady). */
    void swim(Vec3d input, float speed) {
        float drag = 0.8f;
        updateVelocity(speed * FumaroleRiding.SWIM_FACTOR * (1 - drag), input);
        double depth = getFluidHeight(FluidTags.LAVA);
        double vy = getVelocity().y + (depth > FumaroleRiding.SWIM_DEPTH ? 0.04 : -0.03);
        vy = MathHelper.clamp(vy, -0.25, 0.15) * 0.85;
        setVelocity(getVelocity().x * drag, vy, getVelocity().z * drag);
        move(MovementType.SELF, getVelocity());
    }

    private void rideTravel() {
        FumaroleRiding.Steer steer = FumaroleRiding.combine(getPassengerList(),
                rider -> ((LivingEntityJumpingAccessor) rider).steveparty$isJumping());
        float yaw = getYaw() - FumaroleRiding.turnFor(steer.turn());
        setYaw(yaw);
        prevYaw = yaw;
        setBodyYaw(yaw);
        setHeadYaw(yaw);
        float speed = FumaroleRiding.speedFor(steer.forward());
        Vec3d input = new Vec3d(0, 0, steer.moving() ? Math.signum(steer.forward()) : 0);
        if (isSwimmingInLava()) {
            swim(input, speed);
        } else {
            setMovementSpeed(speed);
            super.travel(input);
        }
        // the jump: charged while held, fired on release
        if (steer.jumping()) {
            charge = Math.min(FumaroleRiding.CHARGE_MAX, charge + 1);
        } else if (charge > 0) {
            thrusterJump(charge);
            charge = 0;
        }
        if (dataTracker.get(CHARGE) != charge) dataTracker.set(CHARGE, charge);
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        LivingEntity target = getTarget();
        if (target instanceof PlayerEntity player && (trustedByAll(player) || isOwner(player))) setTarget(null);
        long now = getWorld().getTime();
        grudges.entrySet().removeIf(grudge -> {
            if (now < grudge.getValue()) return false;
            PlayerEntity player = getWorld().getPlayerByUuid(grudge.getKey());
            return player == null || distanceFromShell(player) > CALM_DISTANCE;
        });
        if (getTarget() instanceof PlayerEntity player && !hasGrudge(player) && !ridersHostileTo(player)) setTarget(null); // calmed down
        if (hunted != null) { // a hunt given up: its prey gone, too far, piglins on or tamed
            if (getTarget() != hunted) {
                hunted = null;
            } else if (!hunted.isAlive() || distanceFromShell(hunted) > HUNT_RANGE + 8 || piglinRiders() > 0 || isTamed()) {
                setTarget(null);
                hunted = null;
            }
        }
        boolean free = getTarget() == null || !getTarget().isAlive();
        if (free && !grudges.isEmpty() && age % 10 == 0) {
            PlayerEntity foe = nearestGrudge();
            if (foe != null) setTarget(foe);
        }
        free = getTarget() == null || !getTarget().isAlive();
        if (free && piglinRiders() > 0 && age % 10 == 0) {
            PlayerEntity foe = piglinsFoe();
            if (foe != null) setTarget(foe);
        }
        free = getTarget() == null || !getTarget().isAlive();
        if (free && age % HUNT_PERIOD == 0 && random.nextInt(HUNT_CHANCE) == 0) {
            LivingEntity prey = findPrey();
            if (prey != null) {
                setTarget(prey);
                hunted = prey;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            clientTick();
            return;
        }
        long now = getWorld().getTime();
        for (int head = 0; head < HEADS.length; head++) {
            if (ventIdleAt[head] != 0 && now >= ventIdleAt[head]) {
                ventIdleAt[head] = 0;
                setVent(head, VENT_IDLE);
            }
        }
        for (int head = 0; head < HEADS.length; head++) {
            PlayerEntity rider = isSteered() ? riderOf(head) : null;
            if (rider != null) aimHeadAt(head, rider.getEyePos().add(rider.getRotationVector().multiply(20)), false);
            else if (getHeadTarget(head) == null && now >= headReady[head]) restHead(head); // idle: back to rest
        }
        if (airborneJump && isOnGround() && climbTo == null && getVelocity().y <= 0) {
            airborneJump = false;
            grabArmed = 0;
        }
        if (grabArmed > 0 && climbTo == null && --grabArmed >= 0 && tryGrab()) grabArmed = 0;
        tickClimb();
        tickUntamedRider(now);
    }

    /** An untamed one with a rider: fidgets, its heads turning to look, then throws him off. */
    private void tickUntamedRider(long now) {
        if (isTamed() || !hasPlayerRider()) return;
        if (throwAt < 0) scheduleThrow();
        Entity rider = null;
        for (Entity passenger : getPassengerList()) if (passenger instanceof PlayerEntity) rider = rider == null ? passenger : rider;
        if (rider == null) return;
        if (now >= throwAt - FIDGET_TICKS) {
            for (int head = 0; head < HEADS.length; head++) setHeadTarget(head, rider);
            if (random.nextInt(8) == 0 && getWorld() instanceof ServerWorld world) {
                Vec3d at = nozzle(random.nextInt(HEADS.length));
                world.spawnParticles(ModParticles.THERMAL_BASE, at.x, at.y, at.z, 2, 0.2, 0.2, 0.2, 0.02);
            }
        }
        if (now >= throwAt) {
            for (int head = 0; head < HEADS.length; head++) setHeadTarget(head, null);
            sprayOff(rider);
            throwAt = hasPlayerRider() ? now + THROW_MIN / 2 : -1;
        }
    }

    // ---------------------------------------------------------------- anger

    /** This player provoked it: it fights him for {@link #ANGER_TICKS} ticks (never its owner or a fully trusted one). */
    public void provoke(PlayerEntity player) {
        provoke(player, ANGER_TICKS);
    }

    public void provoke(PlayerEntity player, int ticks) {
        if (getWorld().isClient || isOwner(player) || trustedByAll(player) || player.getAbilities().creativeMode || player.isSpectator()) return;
        grudges.merge(player.getUuid(), getWorld().getTime() + ticks, Math::max);
        if (getTarget() == null || !getTarget().isAlive()) setTarget(player);
    }

    /** Whether it is (still) angry with this player. */
    public boolean hasGrudge(PlayerEntity player) {
        return grudges.containsKey(player.getUuid());
    }

    /** The nearest player it is angry with, within its follow range and in sight; null if none. */
    private @Nullable PlayerEntity nearestGrudge() {
        double range = getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE);
        PlayerEntity best = null;
        double bestDistance = range * range;
        for (UUID uuid : grudges.keySet()) {
            PlayerEntity player = getWorld().getPlayerByUuid(uuid);
            if (player == null || !player.isAlive() || player.isSpectator() || player.getAbilities().creativeMode) continue;
            double d = squaredDistanceTo(player);
            if (d < bestDistance && getVisibilityCache().canSee(player)) {
                best = player;
                bestDistance = d;
            }
        }
        return best;
    }

    /** With piglins on: the nearest player they are hostile to, within its piglin range of its shell, in sight. */
    private @Nullable PlayerEntity piglinsFoe() {
        double range = PIGLIN_RANGE[piglinRiders()];
        PlayerEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PlayerEntity player : getWorld().getPlayers()) {
            if (!player.isAlive() || player.isSpectator() || player.getAbilities().creativeMode || hasPassenger(player)) continue;
            double d = distanceFromShell(player);
            if (d <= range && d < bestDistance && ridersHostileTo(player) && getVisibilityCache().canSee(player)) {
                best = player;
                bestDistance = d;
            }
        }
        return best;
    }

    /** Wild, no piglins on: the nearest mob of {@link #PREY} within {@link #HUNT_RANGE} of its shell, in sight. */
    public @Nullable LivingEntity findPrey() {
        if (isTamed() || piglinRiders() > 0 || hasPlayerRider()) return null;
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity mob : getWorld().getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(HUNT_RANGE),
                mob -> mob.getType().isIn(PREY) && mob.isAlive() && !(mob instanceof FumaroleEntity) && !hasPassenger(mob))) {
            double d = distanceFromShell(mob);
            if (d <= HUNT_RANGE && d < bestDistance && getVisibilityCache().canSee(mob)) {
                best = mob;
                bestDistance = d;
            }
        }
        return best;
    }

    /** Whether it has calmed down about {@code target}: no grudge left (players), or the anger over and them away. */
    public boolean calmAbout(LivingEntity target) {
        if (target instanceof PlayerEntity player) return !hasGrudge(player) && !ridersHostileTo(player);
        return age >= angryUntil && distanceFromShell(target) > CALM_DISTANCE;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.getAttacker() != null && hasPassenger(source.getAttacker())) return false; // its own riders
        boolean damaged = super.damage(source, amount);
        if (damaged && !getWorld().isClient) {
            if (source.getAttacker() instanceof PlayerEntity player) provoke(player);
            else if (source.getAttacker() instanceof LivingEntity) angryUntil = age + ANGER_TICKS;
        }
        return damaged;
    }

    // ---------------------------------------------------------------- client

    private void clientTick() {
        if (isSteered()) { // ridden, it moves server side: its body faces where it goes
            prevBodyYaw = bodyYaw;
            bodyYaw = getYaw();
            headYaw = getYaw();
        }
        prevClientSink = clientSink;
        clientSink += MathHelper.clamp(sinkGoal() - clientSink, -0.05f, 0.05f);
        prevClientTankLevel = clientTankLevel < 0 ? getTank() : clientTankLevel;
        float goal = getTank();
        clientTankLevel = clientTankLevel < 0 ? goal : clientTankLevel + MathHelper.clamp(goal - clientTankLevel, -0.15f, 0.15f);
        boolean swimming = isSwimmingInLava();
        if (wasSwimming && !swimming && !isInLava()) moods.shakeOff();
        wasSwimming = swimming;
        boolean bored = !hasPassengers() && !isPumping() && getHeadTarget(0) == null && getHeadTarget(1) == null
                && getHeadTarget(HEADS.length - 1) == null;
        moods.tick(random, age, bored, hasPassengers(), getTank());
        if (deathTime > 0) return;
        if (moods.bodyRoll != 0 && random.nextInt(2) == 0) {
            getWorld().addParticle(ParticleTypes.DRIPPING_LAVA, getX() + random.nextGaussian() * 1.2, getY() + 1 + random.nextDouble() * 2,
                    getZ() + random.nextGaussian() * 1.2, 0, 0, 0);
        }
        if (moods.burp) {
            getWorld().playSound(getX(), getY(), getZ(), ModSounds.FUMAROLE_GURGLE, getSoundCategory(), 1.0f, 0.7f, false);
            for (int k = 0; k < 4; k++) {
                getWorld().addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, getX() + random.nextGaussian() * 0.4, getY() + HEIGHT,
                        getZ() + random.nextGaussian() * 0.4, 0, 0.06, 0);
            }
            getWorld().addParticle(ParticleTypes.LAVA, getX(), getY() + HEIGHT, getZ(), 0, 0, 0);
        }
        int charging = getCharge();
        for (int head = 0; head < HEADS.length; head++) {
            easeHead(head);
            byte vent = getVent(head);
            Vec3d at = nozzle(head);
            Vec3d ahead = Vec3d.fromPolar(0, bodyYaw + clientYaw[head]);
            if (moods.puff[head]) {
                for (int k = 0; k < 3; k++) {
                    getWorld().addParticle(ModParticles.THERMAL_BASE, at.x, at.y, at.z, ahead.x * 0.05, 0.06, ahead.z * 0.05);
                }
                getWorld().playSound(at.x, at.y, at.z, ModSounds.FUMAROLE_PUFF, getSoundCategory(), 0.6f, 0.6f, false);
            }
            if (charging > 0 && random.nextInt(3) == 0) {
                getWorld().addParticle(ModParticles.THERMAL_BASE, at.x, at.y - 0.4, at.z, 0, -0.08, 0);
            } else if (vent == VENT_IDLE && random.nextInt(6 * HEADS.length) == 0) {
                getWorld().addParticle(ModParticles.THERMAL_BASE, at.x + ahead.x * 0.3, at.y, at.z + ahead.z * 0.3,
                        ahead.x * 0.01, 0.03, ahead.z * 0.01);
            } else if (vent == VENT_CHARGING) {
                getWorld().addParticle(ParticleTypes.SMALL_FLAME, at.x + random.nextGaussian() * 0.2, at.y + random.nextGaussian() * 0.2,
                        at.z + random.nextGaussian() * 0.2, 0, 0.01, 0);
                if (random.nextInt(2) == 0) getWorld().addParticle(ModParticles.THERMAL_BASE, at.x, at.y, at.z, 0, 0.05, 0);
            }
        }
        if (random.nextInt(20) == 0) {
            getWorld().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX() + random.nextGaussian() * 0.8,
                    getY() + HEIGHT, getZ() + random.nextGaussian() * 0.8, 0, 0.02, 0);
        }
        if (random.nextInt(8) == 0) {
            getWorld().addParticle(random.nextBoolean() ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH,
                    getX() + random.nextGaussian() * 1.5, getY() + random.nextDouble() * HEIGHT, getZ() + random.nextGaussian() * 1.5, 0, 0, 0);
        }
    }

    /**
     * Client: eases a head's drawn aim. In order: the target it watches (combat, or a rider it is about to throw), its
     * rider's look (ridden), the thrusters (pointing down while a jump charges or it climbs), its mood, where it looks
     * (the centre head), its rest.
     */
    private void easeHead(int head) {
        prevClientYaw[head] = clientYaw[head];
        prevClientPitch[head] = clientPitch[head];
        FumaroleHead rest = HEADS[head];
        float yaw = rest.restYaw(), pitch = rest.restPitch(), turn = HEAD_TURN;
        Entity target = getHeadTarget(head);
        Entity rider = head < getPassengerList().size() ? getPassengerList().get(head) : null;
        // wild, its vent charging or spitting but no target any more: its aim is locked (FumaroleGoals.Blast), it holds
        if (target == null && getVent(head) != VENT_IDLE && !isSteered()) return;
        if (target != null && !hasPassenger(target)) {
            Vec3d to = aimPoint(target).subtract(nozzle(rest, bodyYaw, bodyYaw + clientYaw[head]));
            yaw = MathHelper.wrapDegrees((float) (MathHelper.atan2(to.z, to.x) * MathHelper.DEGREES_PER_RADIAN) - 90 - bodyYaw);
            pitch = (float) -(MathHelper.atan2(to.y, to.horizontalLength()) * MathHelper.DEGREES_PER_RADIAN);
            turn = BLAST_TURN;
        } else if (target != null) { // its own rider: turns back to look at him
            yaw = rest.restYaw() + (head == 1 ? -70 : 70);
            pitch = -40;
        } else if (getCharge() > 0 || isClimbing()) {
            pitch = isClimbing() ? -30 : 60;
        } else if (isSteered() && rider != null) {
            yaw = MathHelper.wrapDegrees(rider.getHeadYaw() - bodyYaw);
            pitch = rider.getPitch();
            turn = HEAD_EASE;
        } else if (!Float.isNaN(moods.yaw[head]) || !Float.isNaN(moods.pitch[head])) {
            if (!Float.isNaN(moods.yaw[head])) yaw = moods.yaw[head];
            if (!Float.isNaN(moods.pitch[head])) pitch = moods.pitch[head];
            turn = HEAD_EASE;
        } else if (head == 0 && !isPumping()) {
            yaw = MathHelper.wrapDegrees(headYaw - bodyYaw);
            pitch = rest.restPitch() + getPitch() * 0.5f;
            turn = HEAD_EASE;
        } else {
            turn = HEAD_EASE;
        }
        yaw = rest.restYaw() + MathHelper.clamp(MathHelper.wrapDegrees(yaw - rest.restYaw()), -HEAD_YAW_MAX, HEAD_YAW_MAX);
        clientYaw[head] += MathHelper.clamp(MathHelper.wrapDegrees(yaw - clientYaw[head]), -turn, turn);
        clientPitch[head] += MathHelper.clamp(pitch - clientPitch[head], -turn, turn);
    }

    /**
     * Vanilla's move control, but its body turns at most {@link #BODY_TURN} a tick (vanilla: 90), and while it still
     * has far to turn it barely moves: it turns on the spot, slowly, before it walks off.
     */
    static final class HeavyMoveControl extends MoveControl {
        HeavyMoveControl(MobEntity entity) {
            super(entity);
        }

        @Override
        public void tick() {
            float before = entity.getYaw();
            super.tick();
            float wanted = MathHelper.wrapDegrees(entity.getYaw() - before);
            if (Math.abs(wanted) <= BODY_TURN) return;
            entity.setYaw(before + Math.copySign(BODY_TURN, wanted));
            if (Math.abs(wanted) > 45) entity.setMovementSpeed(entity.getMovementSpeed() * 0.3f);
        }
    }

    @Override
    public void handleStatus(byte status) {
        if (status >= STATUS_FED && status < STATUS_FED + HEADS.length) {
            moods.trigger(status - STATUS_FED, FumaroleMoods.Mood.WIGGLE, 60);
            Vec3d at = nozzle(status - STATUS_FED);
            for (int k = 0; k < 4; k++) getWorld().addParticle(ParticleTypes.HEART, at.x + random.nextGaussian() * 0.4, at.y + 0.6, at.z + random.nextGaussian() * 0.4, 0, 0.1, 0);
            return;
        }
        if (status >= STATUS_SULK && status < STATUS_SULK + HEADS.length) {
            moods.trigger(status - STATUS_SULK, FumaroleMoods.Mood.SULK, 100);
            return;
        }
        if (status == STATUS_TAMED) {
            for (int head = 0; head < HEADS.length; head++) {
                moods.trigger(head, FumaroleMoods.Mood.WIGGLE, 70);
                Vec3d at = nozzle(head);
                for (int k = 0; k < 6; k++) getWorld().addParticle(ParticleTypes.HEART, at.x + random.nextGaussian() * 0.5, at.y + 0.8, at.z + random.nextGaussian() * 0.5, 0, 0.1, 0);
            }
            return;
        }
        if (status == STATUS_TAMABLE) {
            for (int head = 0; head < HEADS.length; head++) {
                moods.trigger(head, FumaroleMoods.Mood.SULK, 90);
                Vec3d at = nozzle(head);
                getWorld().addParticle(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 0, 0.05, 0);
            }
            return;
        }
        super.handleStatus(status);
    }

    // ---------------------------------------------------------------- death: the tank spills

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        if (getWorld() instanceof ServerWorld world && !isRemoved()) spill(world);
    }

    @Override
    protected void dropInventory() {
        super.dropInventory();
        ItemStack saddle = inventory.removeStack(0);
        if (!saddle.isEmpty()) dropStack(saddle);
    }

    public int spill(ServerWorld world) {
        int buckets = getTank();
        if (buckets <= 0) return 0;
        setTank(0);
        world.spawnParticles(ParticleTypes.LAVA, getX(), getY() + 1.5, getZ(), 10 + buckets, 1.0, 0.6, 1.0, 0.0);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2, getZ(), 20, 1.0, 0.8, 1.0, 0.03);
        world.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, getX(), getY() + 2.5, getZ(), 4, 0.6, 0.3, 0.6, 0.01);
        playSound(ModSounds.FUMAROLE_SPILL, 1.5f, 0.8f);
        if (!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) return 0;
        int sources = Math.min(buckets / SPILL_PER, SPILL_MAX);
        int placed = 0;
        BlockPos center = getBlockPos();
        List<BlockPos> spots = new ArrayList<>();
        spots.add(center);
        for (Direction direction : Direction.Type.HORIZONTAL) spots.add(center.offset(direction));
        for (BlockPos spot : spots) {
            if (placed >= sources) break;
            BlockState state = world.getBlockState(spot);
            if (!(state.isAir() || state.isReplaceable()) || FumarolePumping.isSource(world, spot)) continue;
            world.setBlockState(spot, Blocks.LAVA.getDefaultState());
            placed++;
        }
        return placed;
    }

    // ---------------------------------------------------------------- body

    @Override
    public Box getVisibilityBoundingBox() {
        return getBoundingBox().expand(10, 3, 10);
    }

    @Override
    public int getMaxHeadRotation() {
        return 75;
    }

    @Override
    public int getMaxLookYawChange() {
        return (int) LOOK_TURN;
    }

    @Override
    public boolean isPushedByFluids() {
        return false;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return !isTamed() && !hasPlayerRider() && super.canImmediatelyDespawn(distanceSquared);
    }

    // ---------------------------------------------------------------- save

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Tank", getTank());
        pumping.write(nbt);
        nbt.putBoolean("Tamed", isTamed());
        nbt.putBoolean("Tamable", isTamable());
        if (owner != null) nbt.putUuid("Owner", owner);
        NbtList heads = new NbtList();
        for (Set<UUID> players : trust) {
            NbtList list = new NbtList();
            for (UUID uuid : players) list.add(NbtHelper.fromUuid(uuid));
            heads.add(list);
        }
        nbt.put("Trust", heads);
        NbtList grudgeList = new NbtList();
        long now = getWorld().getTime();
        grudges.forEach((uuid, until) -> {
            NbtCompound grudge = new NbtCompound();
            grudge.putUuid("Player", uuid);
            grudge.putLong("Ticks", Math.max(0, until - now));
            grudgeList.add(grudge);
        });
        nbt.put("Grudges", grudgeList);
        if (!inventory.getStack(0).isEmpty()) nbt.put("Saddle", inventory.getStack(0).encode(getRegistryManager()));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setTank(nbt.getInt("Tank"));
        pumping.read(nbt);
        setBit(TAMED, nbt.getBoolean("Tamed"));
        setBit(TAMABLE, nbt.getBoolean("Tamable"));
        owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        NbtList heads = nbt.getList("Trust", NbtElement.LIST_TYPE);
        for (int head = 0; head < HEADS.length; head++) {
            trust.get(head).clear();
            if (head >= heads.size()) continue;
            for (NbtElement uuid : heads.getList(head)) trust.get(head).add(NbtHelper.toUuid(uuid));
        }
        grudges.clear();
        long now = getWorld() == null ? 0 : getWorld().getTime();
        for (NbtElement element : nbt.getList("Grudges", NbtElement.COMPOUND_TYPE)) {
            NbtCompound grudge = (NbtCompound) element;
            if (grudge.containsUuid("Player")) grudges.put(grudge.getUuid("Player"), now + grudge.getLong("Ticks"));
        }
        inventory.setStack(0, nbt.contains("Saddle") ? ItemStack.fromNbt(getRegistryManager(), nbt.get("Saddle"))
                .filter(stack -> stack.isOf(Items.SADDLE)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY);
        setBit(SADDLED, inventory.getStack(0).isOf(Items.SADDLE));
    }

    // ---------------------------------------------------------------- sounds

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return ModSounds.FUMAROLE_AMBIENT;
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return 160;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.FUMAROLE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.FUMAROLE_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 1.5f;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.FUMAROLE_STEP, 0.6f, 1.0f);
    }

    // ---------------------------------------------------------------- animations

    void playSpit(int head) {
        triggerAnim(HEADS[head].name(ACTION_CONTROLLER), "spit");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, MAIN_CONTROLLER, 6, this::animate));
        for (FumaroleHead head : HEADS) {
            controllers.add(new AnimationController<>(this, head.name(ACTION_CONTROLLER), 4, state -> PlayState.STOP)
                    .triggerableAnim("spit", RawAnimation.begin().thenPlay(head.name(ANIM_SPIT))));
        }
    }

    /**
     * Pumping plays at its authored pace (its gulp is timed, FumaroleGoals.Pump); the idle breath and the walk are
     * slowed down: {@link #IDLE_PACE}, {@link #WALK_PACE} (the walk's stride follows {@link #SPEED}).
     */
    private PlayState animate(AnimationState<FumaroleEntity> state) {
        if (isPumping()) {
            state.getController().setAnimationSpeed(1);
            return state.setAndContinue(PUMP);
        }
        double dx = getX() - prevX, dz = getZ() - prevZ;
        boolean walking = dx * dx + dz * dz > 1.0e-5;
        state.getController().setAnimationSpeed(walking ? WALK_PACE : IDLE_PACE);
        return state.setAndContinue(walking ? WALK : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
