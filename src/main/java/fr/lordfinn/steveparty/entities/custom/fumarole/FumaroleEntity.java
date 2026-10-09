package fr.lordfinn.steveparty.entities.custom.fumarole;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.particles.ModParticles;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
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
import net.minecraft.world.event.GameEvent;
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
import java.util.List;

/**
 * The Fumarole (Fumerolle): a huge, slow tortoise of the Nether carrying a tank of lava on its back
 * (docs: SteveParty-Workshop/docs/tortue-du-nether.md).
 * <ul>
 *     <li><b>Its tank</b> ({@link #getTank()}): 0 to {@link #TANK_MAX} buckets, synced, saved. Wild ones are born
 *     nearly empty (0 to {@link #SPAWN_TANK_MAX}).</li>
 *     <li><b>Pumping</b> ({@link FumaroleGoals.Pump}, {@link FumarolePumping}): below a full tank it looks for lava
 *     sources around it, walks to the shore, dips one head's nozzle ({@link #PUMP_HEAD}, the pump animation) and drinks one: the source block
 *     disappears, the tank gains a bucket. Its rules keep it from draining a pool (see FumarolePumping).</li>
 *     <li><b>Buckets</b>: a player takes a bucket of lava from it with an empty bucket, or pours one in with a lava
 *     bucket.</li>
 *     <li><b>Neutral, territorial</b>: it fights back whoever hurts it (players and mobs alike), and a player coming
 *     within {@link #TERRITORY} blocks makes it angry for {@link #ANGER_TICKS} ticks; then it calms down if they keep
 *     away.</li>
 *     <li><b>Its heads</b> ({@link #HEADS}): several necks, like a hydra, each a turret of its own: its own aim
 *     (the target it watches, synced: {@link #getHeadTarget}) and its own vent ({@link #getVent}).</li>
 *     <li><b>The thermal blast</b> ({@link FumaroleGoals.Blast}): one head at a time, the heads taking turns, at a
 *     target up to {@link #BLAST_RANGE} blocks it can see (each head its own if several enemies are about, else they
 *     focus one): a second of warning (the head's vent turns to {@link #VENT_CHARGING}, a hiss), then a thick line of
 *     scalding steam ({@link #blast}): {@link #BLAST_DAMAGE} damage, set on fire {@link #BLAST_FIRE_SECONDS} s, a
 *     strong shove; it stops at the first solid block and costs a bucket. Balance: the heads share one rest between
 *     blasts (4 to 6 s) and the one tank, so three heads shoot no more than one would; they only spread the threat.
 *     With an empty tank a head can only puff: a short weak cloud ({@link #PUFF_RANGE} blocks, {@link #PUFF_DAMAGE}
 *     damage, no fire, a small shove), so it goes back to pump first when there is lava around.</li>
 *     <li><b>Its death</b>: its lava spills. With mobGriefing on, min(buckets / 9, {@link #SPILL_MAX}) lava sources
 *     are poured where it dies (only into air or replaceable blocks); always a burst of lava and smoke. It drops
 *     0 to 2 magma cream (loot table).</li>
 *     <li>Slow and heavy (knockback resistant), armoured by its shell, immune to fire and lava; it wades through lava
 *     (no path penalty) rather than swims.</li>
 * </ul>
 */
public class FumaroleEntity extends PathAwareEntity implements GeoEntity {
    /** Its shell and legs: 52 px wide, the tank's rim 58 px high. The necks reach far beyond (not in the box). */
    public static final float WIDTH = 3.2f, HEIGHT = 3.625f, EYE_HEIGHT = 2.575f;
    public static final int TANK_MAX = 27, SPAWN_TANK_MAX = 3;
    public static final double MAX_HEALTH = 80, ARMOR = 10, SPEED = 0.12;

    /**
     * Its heads, centre first, and where each nozzle rests: measured on the v12 export (pose_s): the centre neck 11
     * segments long, the side ones 8, splayed 30 degrees out from 13 px either side.
     */
    public static final FumaroleHead[] HEADS = {
            new FumaroleHead(0, "_c", 0.0, 1.125, 8.31, 2.57, 0, 20),
            new FumaroleHead(1, "_l", -0.8125, 1.125, 7.38, 1.94, -30, 20),
            new FumaroleHead(2, "_r", 0.8125, 1.125, 7.76, 2.7, 30, 5),
    };
    /** The head that dips into the lava to pump. */
    public static final int PUMP_HEAD = 0;
    /** A head turns at most this far (degrees) from its rest, this fast (degrees a tick). */
    public static final float HEAD_YAW_MAX = 75, HEAD_TURN = 25;
    /** Pumping: the nozzle dips about 6.5 blocks ahead, 3.5 down; a source this far (horizontally) can be reached. */
    public static final double PUMP_MIN = 2.0, PUMP_MAX = 9.0, PUMP_DOWN = 6.0, PUMP_UP = 1.0;

    public static final double TERRITORY = 8.0;
    public static final int ANGER_TICKS = 600;
    public static final double BLAST_RANGE = 30.0, BLAST_RADIUS = 1.5;
    public static final float BLAST_DAMAGE = 6.0f, BLAST_FIRE_SECONDS = 4.0f;
    public static final double BLAST_PUSH = 1.6, BLAST_LIFT = 0.45;
    public static final double PUFF_RANGE = 6.0, PUFF_RADIUS = 1.2;
    public static final float PUFF_DAMAGE = 2.0f;
    public static final double PUFF_PUSH = 0.7, PUFF_LIFT = 0.25;
    /** Lava sources spilt on death: one per this many buckets, at most {@link #SPILL_MAX}. */
    public static final int SPILL_PER = 9, SPILL_MAX = 3;

    /** A head's vent state ({@link #getVent}), synced for the renderer: resting, the warning second, blasting. */
    public static final byte VENT_IDLE = 0, VENT_CHARGING = 1, VENT_SPITTING = 2;

    /** The scalding steam's damage type (data/steveparty/damage_type/thermal_steam.json). */
    public static final RegistryKey<DamageType> THERMAL_STEAM = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Steveparty.id("thermal_steam"));

    private static final TrackedData<Integer> TANK = DataTracker.registerData(FumaroleEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Every head's vent state, 2 bits a head. */
    private static final TrackedData<Integer> VENTS = DataTracker.registerData(FumaroleEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Each head's target (an entity id, -1: none): the clients aim the head at it. */
    private static final List<TrackedData<Integer>> HEAD_TARGETS = new ArrayList<>();

    static {
        for (FumaroleHead ignored : HEADS) {
            HEAD_TARGETS.add(DataTracker.registerData(FumaroleEntity.class, TrackedDataHandlerRegistry.INTEGER));
        }
    }
    private static final TrackedData<Boolean> PUMPING = DataTracker.registerData(FumaroleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public static final String ANIM_IDLE = "animation.nether_turtle.idle", ANIM_WALK = "animation.nether_turtle.walk",
            ANIM_PUMP = "animation.nether_turtle.pump", ANIM_SPIT = "animation.nether_turtle.spit";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIM_IDLE);
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(ANIM_WALK);
    private static final RawAnimation PUMP = RawAnimation.begin().thenLoop(ANIM_PUMP);
    public static final String MAIN_CONTROLLER = "main", ACTION_CONTROLLER = "action";

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    final FumarolePumping pumping = new FumarolePumping();
    /** Until when (age) it stays angry at its target even if they keep away. */
    private int angryUntil;
    /** Client only: the tank's drawn level, easing toward the synced one (buckets). */
    public float clientTankLevel = -1, prevClientTankLevel = -1;
    /** Server: each head's aim (world yaw; degrees), turning toward its target. */
    private final float[] aimYaw = new float[HEADS.length];
    /** Client only: each head's drawn aim relative to its rest (yaw from the body, pitch), eased; and last tick's. */
    public final float[] clientYaw = new float[HEADS.length], clientPitch = new float[HEADS.length];
    public final float[] prevClientYaw = new float[HEADS.length], prevClientPitch = new float[HEADS.length];

    public FumaroleEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = 15;
        setPathfindingPenalty(PathNodeType.LAVA, 0.0f);
        setPathfindingPenalty(PathNodeType.DANGER_FIRE, 0.0f);
        setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, 0.0f);
        setPathfindingPenalty(PathNodeType.WATER, 8.0f);
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
        this.targetSelector.add(2, new FumaroleGoals.Territory(this));
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TANK, 0);
        builder.add(VENTS, 0);
        for (TrackedData<Integer> target : HEAD_TARGETS) builder.add(target, -1);
        builder.add(PUMPING, false);
    }

    @Override
    public @Nullable EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
                                           @Nullable EntityData entityData) {
        setTank(world.getRandom().nextInt(SPAWN_TANK_MAX + 1));
        return super.initialize(world, difficulty, spawnReason, entityData);
    }

    // ---------------------------------------------------------------- tank, vent

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

    /** The entity this head aims at, or null. */
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

    /** The tank's drawn level (buckets), eased between ticks. */
    public float tankLevel(float partialTick) {
        if (clientTankLevel < 0) return getTank();
        return MathHelper.lerp(partialTick, prevClientTankLevel, clientTankLevel);
    }

    // ---------------------------------------------------------------- pumping

    /** Whether this lava source is within the dipping nozzle's reach as it stands. */
    public boolean canReach(BlockPos source) {
        Vec3d center = Vec3d.ofCenter(source);
        double dx = center.x - getX(), dz = center.z - getZ(), dy = center.y - getY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return horizontal >= PUMP_MIN && horizontal <= PUMP_MAX && dy >= -PUMP_DOWN && dy <= PUMP_UP;
    }

    /** Whether it may drink this source now: tank not full, the pool rules and the rate (FumarolePumping). */
    public boolean canPump(BlockPos source) {
        return getTank() < TANK_MAX && pumping.rateAllows(getWorld().getTime())
                && FumarolePumping.leavesEnough(getWorld(), source);
    }

    /** Drinks this source: the block goes, the tank gains a bucket. False (nothing done) if the rules forbid it. */
    public boolean pump(BlockPos source) {
        if (getWorld().isClient || !canPump(source)) return false;
        World world = getWorld();
        world.setBlockState(source, Blocks.AIR.getDefaultState());
        world.emitGameEvent(this, GameEvent.FLUID_PICKUP, source);
        pumping.record(world.getTime());
        setTank(getTank() + 1);
        playSound(ModSounds.FUMAROLE_PUMP, 1.2f, 0.8f + random.nextFloat() * 0.2f);
        if (world instanceof ServerWorld server) {
            Vec3d at = Vec3d.ofCenter(source);
            server.spawnParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 6, 0.4, 0.2, 0.4, 0.0);
            server.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 4, 0.3, 0.2, 0.3, 0.02);
        }
        return true;
    }

    // ---------------------------------------------------------------- buckets

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!isAlive()) return super.interactMob(player, hand);
        if (stack.isOf(Items.BUCKET) && getTank() > 0) {
            if (!getWorld().isClient) {
                setTank(getTank() - 1);
                player.setStackInHand(hand, ItemUsage.exchangeStack(stack, player, new ItemStack(Items.LAVA_BUCKET)));
                playSound(SoundEvents.ITEM_BUCKET_FILL_LAVA, 1.0f, 1.0f);
                playSound(ModSounds.FUMAROLE_GURGLE, 0.8f, 1.0f);
            }
            return ActionResult.success(getWorld().isClient);
        }
        if (stack.isOf(Items.LAVA_BUCKET) && getTank() < TANK_MAX) {
            if (!getWorld().isClient) {
                setTank(getTank() + 1);
                player.setStackInHand(hand, ItemUsage.exchangeStack(stack, player, new ItemStack(Items.BUCKET)));
                playSound(SoundEvents.ITEM_BUCKET_EMPTY_LAVA, 1.0f, 1.0f);
                playSound(ModSounds.FUMAROLE_GURGLE, 0.8f, 1.0f);
            }
            return ActionResult.success(getWorld().isClient);
        }
        return super.interactMob(player, hand);
    }

    // ---------------------------------------------------------------- the blast

    /** A head's nozzle in the world, its neck in the S pose turned to world yaw {@code yaw}. */
    public Vec3d nozzle(FumaroleHead head, float bodyYaw, float yaw) {
        return getPos().add(Vec3d.fromPolar(0, bodyYaw).multiply(head.base()))
                .add(Vec3d.fromPolar(0, bodyYaw + 90).multiply(head.side()))
                .add(Vec3d.fromPolar(0, yaw).multiply(head.reach()))
                .add(0, head.up(), 0);
    }

    /** A head's nozzle as it aims now (server: its aim; client: its drawn aim). */
    public Vec3d nozzle(int head) {
        float yaw = getWorld().isClient ? bodyYaw + clientYaw[head] : aimYaw[head];
        return nozzle(HEADS[head], bodyYaw, yaw);
    }

    /** Where it aims on a target: the middle of its body. */
    public static Vec3d aimPoint(Entity target) {
        return target.getPos().add(0, target.getHeight() * 0.5, 0);
    }

    /**
     * Where a head's steam leaves from: its nozzle, unless a block stands between the neck's base and the nozzle
     * (the nozzle in a wall): then just before that block.
     */
    public Vec3d blastOrigin(int head) {
        Vec3d nozzle = nozzle(head);
        Vec3d base = getPos().add(0, HEADS[head].up(), 0);
        BlockHitResult hit = getWorld().raycast(new RaycastContext(base, nozzle, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, this));
        if (hit.getType() == HitResult.Type.MISS) return nozzle;
        return hit.getPos().add(base.subtract(hit.getPos()).normalize().multiply(0.3));
    }

    /** Server: turns a head toward {@code target} (at most {@link #HEAD_TURN} a tick unless {@code instant}, within reach of the body). */
    public void aimHead(int head, Entity target, boolean instant) {
        Vec3d to = aimPoint(target).subtract(nozzle(HEADS[head], bodyYaw, aimYaw[head]));
        float yaw = (float) (MathHelper.atan2(to.z, to.x) * MathHelper.DEGREES_PER_RADIAN) - 90;
        float rest = HEADS[head].restYaw();
        float rel = rest + MathHelper.clamp(MathHelper.wrapDegrees(yaw - bodyYaw - rest), -HEAD_YAW_MAX, HEAD_YAW_MAX);
        float current = MathHelper.wrapDegrees(aimYaw[head] - bodyYaw);
        float step = instant ? 360 : HEAD_TURN;
        aimYaw[head] = bodyYaw + current + MathHelper.clamp(MathHelper.wrapDegrees(rel - current), -step, step);
    }

    /** Server: a head back to rest. */
    public void restHead(int head) {
        aimYaw[head] = bodyYaw + HEADS[head].restYaw();
    }

    /** Whether a head can reach this target now (a full blast's range, or a puff's with an empty tank). */
    public boolean inRange(int head, Entity target) {
        double range = getTank() > 0 ? BLAST_RANGE : PUFF_RANGE;
        return blastOrigin(head).squaredDistanceTo(aimPoint(target)) <= range * range;
    }

    /** Whether any head can reach this target now. */
    public boolean inRange(Entity target) {
        for (int head = 0; head < HEADS.length; head++) if (inRange(head, target)) return true;
        return false;
    }

    /**
     * One head fires at {@code target}: with lava in the tank, the thermal blast (costs a bucket), else the weak puff. Hits
     * every living thing (but other Fumaroles) within the jet's radius along the line, up to the first solid block.
     * Returns the entities it hurt.
     */
    public List<LivingEntity> blast(int head, Entity target) {
        List<LivingEntity> hurt = new ArrayList<>();
        if (!(getWorld() instanceof ServerWorld world)) return hurt;
        boolean full = getTank() > 0;
        double range = full ? BLAST_RANGE : PUFF_RANGE, radius = full ? BLAST_RADIUS : PUFF_RADIUS;
        Vec3d from = blastOrigin(head);
        Vec3d dir = aimPoint(target).subtract(from);
        if (dir.lengthSquared() < 1.0e-6) dir = getRotationVector();
        dir = dir.normalize();
        Vec3d to = from.add(dir.multiply(range));
        BlockHitResult hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, this));
        boolean impact = hit.getType() != HitResult.Type.MISS;
        if (impact) to = hit.getPos();
        if (full) setTank(getTank() - 1);

        DamageSource steam = new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(THERMAL_STEAM), this);
        Vec3d segment = to.subtract(from);
        double length = segment.length();
        Box box = new Box(from, to).expand(radius + 1);
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && !(e instanceof FumaroleEntity) && !e.isSpectator())) {
            Vec3d center = entity.getBoundingBox().getCenter();
            double along = MathHelper.clamp(center.subtract(from).dotProduct(dir), 0, length);
            Vec3d closest = from.add(dir.multiply(along));
            double reach = radius + entity.getWidth() * 0.5;
            if (closest.squaredDistanceTo(center) > reach * reach) continue;
            if (!entity.damage(steam, full ? BLAST_DAMAGE : PUFF_DAMAGE)) continue;
            hurt.add(entity);
            if (full) entity.setOnFireFor(BLAST_FIRE_SECONDS);
            double resist = 1 - entity.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
            if (resist > 0) {
                Vec3d push = new Vec3d(dir.x, 0, dir.z);
                push = push.lengthSquared() < 1.0e-6 ? Vec3d.ZERO : push.normalize();
                double strength = (full ? BLAST_PUSH : PUFF_PUSH) * resist;
                entity.addVelocity(push.x * strength, (full ? BLAST_LIFT : PUFF_LIFT) * resist, push.z * strength);
                entity.velocityModified = true;
            }
        }
        blastEffects(world, from, dir, length, impact, full);
        return hurt;
    }

    private void blastEffects(ServerWorld world, Vec3d from, Vec3d dir, double length, boolean impact, boolean full) {
        playSound(full ? ModSounds.FUMAROLE_BLAST : ModSounds.FUMAROLE_PUFF, full ? 3.0f : 1.2f, 0.9f + random.nextFloat() * 0.2f);
        double step = full ? 0.8 : 0.6;
        for (double d = 0; d <= length; d += step) {
            Vec3d at = from.add(dir.multiply(d));
            double spread = (full ? 0.5 : 0.3) + d * 0.02;
            Vec3d v = dir.multiply(full ? 0.35 : 0.18);
            world.spawnParticles(ModParticles.THERMAL_PLUME, at.x, at.y, at.z, 0,
                    v.x + random.nextGaussian() * 0.03, v.y + 0.02, v.z + random.nextGaussian() * 0.03, 1.0);
            if (random.nextInt(2) == 0) {
                world.spawnParticles(ParticleTypes.WHITE_SMOKE, at.x, at.y, at.z, 1, spread, spread, spread, 0.02);
            }
            if (full && d < length * 0.6 && random.nextInt(2) == 0) {
                world.spawnParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 1, 0.2, 0.2, 0.2, 0.05);
            }
            if (full && random.nextInt(3) == 0) {
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 1, spread, spread, spread, 0.01);
            }
        }
        world.spawnParticles(ModParticles.THERMAL_POOF, from.x, from.y, from.z, full ? 4 : 2, 0.3, 0.3, 0.3, 0.02);
        if (full) world.spawnParticles(ParticleTypes.GUST, from.x, from.y, from.z, 1, 0, 0, 0, 0);
        Vec3d end = from.add(dir.multiply(length));
        world.spawnParticles(ModParticles.THERMAL_POOF, end.x, end.y, end.z, full ? 6 : 2, 0.6, 0.6, 0.6, 0.03);
        if (impact) world.spawnParticles(ParticleTypes.CLOUD, end.x, end.y, end.z, full ? 12 : 4, 0.5, 0.5, 0.5, 0.05);
        if (impact && full) world.spawnParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 1, 0, 0, 0, 0);
    }

    // ---------------------------------------------------------------- anger

    /** Angry again for {@link #ANGER_TICKS}. */
    public void provoke() {
        angryUntil = age + ANGER_TICKS;
    }

    public boolean isAngry() {
        return age < angryUntil;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && target != getTarget()) provoke();
        super.setTarget(target);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean damaged = super.damage(source, amount);
        if (damaged && source.getAttacker() == getTarget()) provoke();
        return damaged;
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        // its target still in its territory keeps it angry (FumaroleGoals: it calms down once they keep away)
        LivingEntity target = getTarget();
        if (target != null && squaredDistanceTo(target) <= TERRITORY * TERRITORY) provoke();
    }

    // ---------------------------------------------------------------- client: tank easing, vent wisps

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient) return;
        prevClientTankLevel = clientTankLevel < 0 ? getTank() : clientTankLevel;
        float goal = getTank();
        clientTankLevel = clientTankLevel < 0 ? goal : clientTankLevel + MathHelper.clamp(goal - clientTankLevel, -0.15f, 0.15f);
        if (deathTime > 0) return;
        for (int head = 0; head < HEADS.length; head++) {
            easeHead(head);
            byte vent = getVent(head);
            Vec3d at = nozzle(head);
            Vec3d ahead = Vec3d.fromPolar(0, bodyYaw + clientYaw[head]);
            if (vent == VENT_IDLE && random.nextInt(6 * HEADS.length) == 0) {
                getWorld().addParticle(ModParticles.THERMAL_BASE, at.x + ahead.x * 0.3, at.y, at.z + ahead.z * 0.3,
                        ahead.x * 0.01, 0.03, ahead.z * 0.01);
            } else if (vent == VENT_CHARGING) {
                getWorld().addParticle(ParticleTypes.SMALL_FLAME, at.x + random.nextGaussian() * 0.2, at.y + random.nextGaussian() * 0.2,
                        at.z + random.nextGaussian() * 0.2, 0, 0.01, 0);
                if (random.nextInt(2) == 0) getWorld().addParticle(ModParticles.THERMAL_BASE, at.x, at.y, at.z, 0, 0.05, 0);
            }
        }
        if (random.nextInt(20) == 0) { // the tank's cracks smoke, and ash drifts about it
            getWorld().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX() + random.nextGaussian() * 0.8,
                    getY() + HEIGHT, getZ() + random.nextGaussian() * 0.8, 0, 0.02, 0);
        }
        if (random.nextInt(8) == 0) {
            getWorld().addParticle(random.nextBoolean() ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH,
                    getX() + random.nextGaussian() * 1.5, getY() + random.nextDouble() * HEIGHT, getZ() + random.nextGaussian() * 1.5, 0, 0, 0);
        }
    }

    /** Client: eases a head's drawn aim toward its target (or, without one, the centre head toward where it looks). */
    private void easeHead(int head) {
        prevClientYaw[head] = clientYaw[head];
        prevClientPitch[head] = clientPitch[head];
        FumaroleHead rest = HEADS[head];
        float yaw = rest.restYaw(), pitch = rest.restPitch();
        Entity target = getHeadTarget(head);
        if (target != null) {
            Vec3d to = aimPoint(target).subtract(nozzle(HEADS[head], bodyYaw, bodyYaw + clientYaw[head]));
            yaw = MathHelper.wrapDegrees((float) (MathHelper.atan2(to.z, to.x) * MathHelper.DEGREES_PER_RADIAN) - 90 - bodyYaw);
            pitch = (float) -(MathHelper.atan2(to.y, to.horizontalLength()) * MathHelper.DEGREES_PER_RADIAN);
        } else if (head == 0 && !isPumping()) {
            yaw = MathHelper.wrapDegrees(headYaw - bodyYaw);
            pitch = rest.restPitch() + getPitch() * 0.5f; // a glance from its resting pose
        }
        yaw = rest.restYaw() + MathHelper.clamp(MathHelper.wrapDegrees(yaw - rest.restYaw()), -HEAD_YAW_MAX, HEAD_YAW_MAX);
        clientYaw[head] += MathHelper.clamp(MathHelper.wrapDegrees(yaw - clientYaw[head]), -HEAD_TURN, HEAD_TURN);
        clientPitch[head] += MathHelper.clamp(pitch - clientPitch[head], -HEAD_TURN, HEAD_TURN);
    }

    // ---------------------------------------------------------------- death: the tank spills

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        if (getWorld() instanceof ServerWorld world && !isRemoved()) spill(world);
    }

    /** Its lava spills: some sources poured where it dies if mobGriefing allows, always lava bursts and smoke. */
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
    public int getMaxHeadRotation() {
        return 75;
    }

    @Override
    public int getMaxLookYawChange() {
        return 25; // its neck turns fast, a turret
    }

    /** Drawn while any of it shows: its necks reach some 10 blocks out of its box. */
    @Override
    public Box getVisibilityBoundingBox() {
        return getBoundingBox().expand(10, 3, 10);
    }

    @Override
    public boolean isPushedByFluids() {
        return false; // it wades
    }

    // ---------------------------------------------------------------- save

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Tank", getTank());
        pumping.write(nbt);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setTank(nbt.getInt("Tank"));
        pumping.read(nbt);
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

    /** Plays a head's spit animation (the warning second, then the whip as the steam leaves) on every client. */
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

    private PlayState animate(AnimationState<FumaroleEntity> state) {
        if (isPumping()) return state.setAndContinue(PUMP);
        double dx = getX() - prevX, dz = getZ() - prevZ;
        return state.setAndContinue(dx * dx + dz * dz > 1.0e-5 ? WALK : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
