package fr.lordfinn.steveparty.entities.custom.boomcart;

import fr.lordfinn.steveparty.sounds.ModSounds;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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

import java.util.List;
import java.util.UUID;

/**
 * The Boomcart (Pétaroule): a grumpy little living mine cart, loaded with TNT, like a bulldog.
 * <ul>
 *     <li><b>Its load</b> ({@link #getLoad()}): a TNT block (the default), or a firework rocket in a barrel; drawn in
 *     it by BoomcartRenderer, saved with it.</li>
 *     <li><b>Its fuse</b> ({@link #getFuse()}): -1 while unlit, else the ticks left before it blows.</li>
 *     <li><b>Loading it</b>: a right click with TNT or a firework rocket; its mouth opens, it gobbles it, and gives
 *     back the load it had if it was the other one (another rocket: swapped too). Never while lit.</li>
 *     <li><b>The hot potato</b> ({@link BoomcartFuse}): flint and steel lights it; it panics and rushes in a zigzag at
 *     the nearest player who isn't the lighter. Flint and steel again, by someone else, passes it on: it goes for
 *     another player, its fuse a little longer (+3 s, +2 s, +1 s, then nothing). Never twice in a row by the same
 *     player. Killed while lit, it blows at once.</li>
 *     <li><b>The blast, by its load</b>: TNT, a real explosion breaking blocks, a mob's (the mobGriefing rule
 *     decides); a firework, coloured sparks (its rocket's own, if it has some) and a harmless shove, no block
 *     broken.</li>
 *     <li><b>Rails</b>: on a rail it follows the track like a minecart ({@link BoomcartRails}), powered rails boosting
 *     it, detector rails seeing it; it gives itself a push now and then. Off the rails it rolls about a little
 *     ({@link BoomcartGoals}).</li>
 *     <li><b>Never ridden</b>: nobody gets in, and it gets in nothing (a minecart, a boat).</li>
 *     <li><b>Its mouth</b>: wide open ({@link #isHungry()}, "feed me") while a player near it holds a load.</li>
 * </ul>
 */
public class BoomcartEntity extends PathAwareEntity implements GeoEntity {
    /** Its body: 16 pixels wide, as a vanilla minecart, 14 high with the TNT on top. */
    public static final float WIDTH = 0.98f, HEIGHT = 0.875f;
    public static final double MAX_HEALTH = 12.0;

    private static final TrackedData<ItemStack> LOAD =
            DataTracker.registerData(BoomcartEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    private static final TrackedData<Integer> FUSE =
            DataTracker.registerData(BoomcartEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> FUSE_TOTAL =
            DataTracker.registerData(BoomcartEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> HUNGRY =
            DataTracker.registerData(BoomcartEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ROLL = RawAnimation.begin().thenLoop("roll");
    private static final RawAnimation FEED_ME = RawAnimation.begin().thenLoop("feed_me");
    private static final RawAnimation PANIC = RawAnimation.begin().thenLoop("panic");
    private static final RawAnimation EAT = RawAnimation.begin().thenPlay("eat");
    private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("roar");
    public static final String MAIN_CONTROLLER = "main", ACTION_CONTROLLER = "action";

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    /** Its TNT's blast (a creeper's), and its firework's harmless shove: how far, how hard. */
    public static final float TNT_POWER = 3.0f;
    public static final double BURST_RADIUS = 4.0, BURST_PUSH = 1.1;
    /** Lit on rails, it pushes itself this hard up to this speed, toward its target. */
    private static final double PANIC_PUSH = 0.05, PANIC_CRUISE = 0.3;
    /** A player this close holding a load makes it open its mouth; it picks targets this far at most. */
    private static final double HUNGRY_RANGE = 5.0, TARGET_RANGE = 32.0;
    private static final int ROAR_COOLDOWN = 100;
    /** Sent to the clients: its firework load bursts. */
    private static final byte BURST_STATUS = 17;

    private final BoomcartFuse fuse = new BoomcartFuse();
    /** The player it rushes at, lit. */
    private @Nullable UUID target;
    private int roarCooldown;
    private boolean exploded;
    /** Its own push on the rails, unlit: how hard, up to what speed, how often it starts one (1 in so many ticks). */
    private static final double RAIL_PUSH = 0.02, RAIL_CRUISE = 0.12;
    private static final int RAIL_PUSH_CHANCE = 80, RAIL_PUSH_TICKS = 30;
    /** Turning on the rails: degrees per tick at most. */
    private static final float RAIL_TURN = 30;

    private boolean onRails;
    /** Ticks left of its own push on the rails, and which way (+1: toward the track's exit b). */
    private int railPushTicks;
    private int railPushSign = 1;
    /** Client only: its lip's angle in the last frame drawn (BoomcartModel), whether its mouth is open. */
    public float clientLipAngle;

    public BoomcartEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = 5;
    }

    public static DefaultAttributeContainer.Builder setAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new BoomcartGoals.Panic(this));
        this.goalSelector.add(5, new BoomcartGoals.Wander(this));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(7, new LookAroundGoal(this));
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(LOAD, new ItemStack(Items.TNT));
        builder.add(FUSE, -1);
        builder.add(FUSE_TOTAL, 0);
        builder.add(HUNGRY, false);
    }

    // ---------------------------------------------------------------- load, fuse, mouth

    /** What it carries: a TNT block or a firework rocket (one item, never empty). */
    public ItemStack getLoad() {
        return dataTracker.get(LOAD);
    }

    public void setLoad(ItemStack load) {
        dataTracker.set(LOAD, load.copyWithCount(1));
    }

    public boolean carriesFirework() {
        return getLoad().isOf(Items.FIREWORK_ROCKET);
    }

    /** Ticks left before it blows, -1 while unlit. */
    public int getFuse() {
        return dataTracker.get(FUSE);
    }

    /** Ticks the fuse had in all (lit, plus its extensions): how far it has burnt down. */
    public int getFuseTotal() {
        return dataTracker.get(FUSE_TOTAL);
    }

    protected void setFuse(int fuse, int total) {
        dataTracker.set(FUSE, fuse);
        dataTracker.set(FUSE_TOTAL, total);
    }

    public boolean isLit() {
        return getFuse() >= 0;
    }

    /** Its mouth wide open, "feed me". */
    public boolean isHungry() {
        return dataTracker.get(HUNGRY);
    }

    protected void setHungry(boolean hungry) {
        dataTracker.set(HUNGRY, hungry);
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void tick() {
        super.tick();
        if (getWorld() instanceof ServerWorld world && isAlive()) tickServer(world);
    }

    private void tickServer(ServerWorld world) {
        if (roarCooldown > 0) roarCooldown--;
        if (fuse.isLit()) {
            boolean blow = fuse.tick();
            setFuse(fuse.ticks(), fuse.total());
            float burnt = 1 - fuse.ticks() / (float) Math.max(1, fuse.total());
            if (age % 10 == 0) playSound(ModSounds.BOOMCART_FUSE, 0.6f, 1.0f + 0.6f * burnt);
            if (age % 30 == 0) playSound(ModSounds.BOOMCART_PANIC, 0.8f, 1.0f + 0.3f * burnt);
            if (age % 3 == 0) world.spawnParticles(ParticleTypes.SMOKE, getX(), getY() + 1.45, getZ(), 1, 0.05, 0.05, 0.05, 0.01);
            if (age % 20 == 0 && getPanicTarget() == null) retarget(fuse.last());
            if (blow) explode(world);
            return;
        }
        if (age % 10 == 0) setHungry(feeder(world) != null);
        if (onRails && getVelocity().horizontalLengthSquared() > 0.0025 && age % 6 == 0) {
            playSound(ModSounds.BOOMCART_ROLL, 0.5f, 0.8f + random.nextFloat() * 0.3f);
        }
        if (roarCooldown == 0 && age % 20 == 0 && !isHungry() && random.nextInt(4) == 0) {
            PlayerEntity close = world.getClosestPlayer(this, 3.0);
            if (close != null && !close.isSpectator()) roar(); // grumpy: no closer
        }
    }

    /** A player near it holding TNT or a rocket: it opens its mouth for them. */
    private @Nullable PlayerEntity feeder(ServerWorld world) {
        for (PlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || player.squaredDistanceTo(this) > HUNGRY_RANGE * HUNGRY_RANGE) continue;
            for (Hand hand : Hand.values()) {
                if (isLoad(player.getStackInHand(hand))) return player;
            }
        }
        return null;
    }

    public static boolean isLoad(ItemStack stack) {
        return stack.isOf(Items.TNT) || stack.isOf(Items.FIREWORK_ROCKET);
    }

    private void roar() {
        roarCooldown = ROAR_COOLDOWN;
        triggerAnim(ACTION_CONTROLLER, "roar");
        playSound(ModSounds.BOOMCART_ROAR, 0.8f, 1.0f);
    }

    // ---------------------------------------------------------------- loading, lighting

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!isAlive()) return super.interactMob(player, hand);
        if (stack.isOf(Items.FLINT_AND_STEEL)) {
            if (!getWorld().isClient) strike(player, stack, hand);
            return ActionResult.success(getWorld().isClient);
        }
        if (isLoad(stack)) {
            if (isLit()) return ActionResult.PASS;
            if (!getWorld().isClient) feed(player, stack);
            return ActionResult.success(getWorld().isClient);
        }
        return super.interactMob(player, hand);
    }

    /** Flint and steel: lights it, or passes it on (see {@link BoomcartFuse}). */
    public BoomcartFuse.Result strike(PlayerEntity player, ItemStack flint, Hand hand) {
        BoomcartFuse.Result result = fuse.strike(player.getUuid());
        if (result == BoomcartFuse.Result.SAME_PLAYER) {
            playSound(ModSounds.BOOMCART_REFUSE, 1.0f, 1.0f);
            player.sendMessage(Text.translatable("message.steveparty.boomcart.not_twice"), true);
            return result;
        }
        setFuse(fuse.ticks(), fuse.total());
        setHungry(false);
        setPersistent();
        retarget(player.getUuid());
        playSound(ModSounds.BOOMCART_LIGHT, 1.0f, 1.0f);
        if (result == BoomcartFuse.Result.PASSED) {
            player.sendMessage(fuse.extension() > 0
                    ? Text.translatable("message.steveparty.boomcart.passed", fuse.extension() / 20)
                    : Text.translatable("message.steveparty.boomcart.passed_last"), true);
        }
        flint.damage(1, player, LivingEntity.getSlotForHand(hand));
        emitGameEvent(GameEvent.PRIME_FUSE, player);
        return result;
    }

    /** TNT or a rocket: it gobbles it, giving back the other load. The same load again: it just grumbles. */
    public void feed(PlayerEntity player, ItemStack stack) {
        setPersistent();
        if (ItemStack.areItemsAndComponentsEqual(getLoad(), stack)) {
            roar();
            return;
        }
        ItemStack old = getLoad().copy();
        setLoad(stack);
        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
            player.getInventory().offerOrDrop(old);
        }
        triggerAnim(ACTION_CONTROLLER, "eat");
        playSound(ModSounds.BOOMCART_LOAD, 1.0f, 1.0f);
    }

    /** The player it rushes at, lit: still there, alive and near. */
    public @Nullable PlayerEntity getPanicTarget() {
        if (target == null) return null;
        PlayerEntity player = getWorld().getPlayerByUuid(target);
        if (player == null || !player.isAlive() || player.isSpectator()
                || player.squaredDistanceTo(this) > TARGET_RANGE * TARGET_RANGE) return null;
        return player;
    }

    /** Its next target: the nearest player who isn't {@code except} (the one who just lit it or passed it on). */
    private void retarget(@Nullable UUID except) {
        PlayerEntity best = null;
        double bestDistance = TARGET_RANGE * TARGET_RANGE;
        for (PlayerEntity player : getWorld().getPlayers()) {
            if (player.getUuid().equals(except) || !player.isAlive() || player.isSpectator()) continue;
            double distance = player.squaredDistanceTo(this);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        target = best == null ? null : best.getUuid();
    }

    /** The player it rushes at now (its UUID), for tests. */
    public @Nullable UUID panicTargetId() {
        return target;
    }

    /** The fuse's state (the hot potato's rules), for tests. */
    public BoomcartFuse fuse() {
        return fuse;
    }

    // ---------------------------------------------------------------- the blast

    /** It blows, its load deciding how (see the class's notes), and it's gone. */
    public void explode(ServerWorld world) {
        if (exploded) return;
        exploded = true;
        Vec3d at = getPos().add(0, HEIGHT * 0.6, 0);
        playSound(ModSounds.BOOMCART_EXPLODE, 1.0f, 1.0f);
        if (carriesFirework()) {
            world.sendEntityStatus(this, BURST_STATUS);
            world.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            for (Entity other : world.getOtherEntities(this, getBoundingBox().expand(BURST_RADIUS))) {
                if (!(other instanceof LivingEntity) || other.isSpectator()) continue;
                double distance = other.getPos().distanceTo(at);
                if (distance > BURST_RADIUS) continue;
                Vec3d away = other.getPos().subtract(at).multiply(1, 0, 1);
                Vec3d dir = away.lengthSquared() < 1.0e-4 ? Vec3d.ZERO : away.normalize();
                double push = (1 - distance / BURST_RADIUS) * BURST_PUSH;
                other.addVelocity(dir.x * push, 0.25 + 0.3 * push, dir.z * push);
                other.velocityModified = true;
            }
        } else {
            world.createExplosion(this, at.x, at.y, at.z, TNT_POWER, World.ExplosionSourceType.MOB);
        }
        discard();
    }

    @Override
    public void handleStatus(byte status) {
        if (status == BURST_STATUS) {
            getWorld().addFireworkParticle(getX(), getY() + HEIGHT * 0.8, getZ(), 0, 0, 0, burst());
            return;
        }
        super.handleStatus(status);
    }

    /** Its firework's sparks: its rocket's own explosions, or a big coloured ball for a plain rocket. */
    private List<FireworkExplosionComponent> burst() {
        FireworksComponent fireworks = getLoad().get(DataComponentTypes.FIREWORKS);
        if (fireworks != null && !fireworks.explosions().isEmpty()) return fireworks.explosions();
        return List.of(
                new FireworkExplosionComponent(FireworkExplosionComponent.Type.LARGE_BALL,
                        IntList.of(0xE83A2E, 0xF7B32B, 0x3FA7F5, 0x7CD13C), IntList.of(0xFFFFFF), true, true),
                new FireworkExplosionComponent(FireworkExplosionComponent.Type.STAR,
                        IntList.of(0xF04CC8, 0xFFE14D), IntList.of(), false, true));
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean hurt = super.damage(source, amount);
        if (hurt && !getWorld().isClient && !isLit() && isAlive() && roarCooldown == 0
                && source.getAttacker() instanceof PlayerEntity) roar();
        return hurt;
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        if (isLit() && getWorld() instanceof ServerWorld world) explode(world); // killed while lit: it blows at once
    }

    @Override
    protected void dropEquipment(ServerWorld world, DamageSource source, boolean causedByPlayer) {
        super.dropEquipment(world, source, causedByPlayer);
        if (!isLit()) dropStack(getLoad().copy()); // its load, unless it's burning
    }

    // ---------------------------------------------------------------- rails

    /** On a rail, the track moving it. */
    public boolean isOnRails() {
        return onRails;
    }

    @Override
    public void travel(Vec3d movementInput) {
        if (!getWorld().isClient && isAlive() && rollOnRails()) return;
        super.travel(movementInput);
    }

    /** A tick on the rails, if it's on one: true when the track moved it. */
    private boolean rollOnRails() {
        BoomcartRails.Track track = BoomcartRails.under(getWorld(), getPos());
        if (track == null || getVelocity().y > 0.2) {
            onRails = false;
            return false;
        }
        onRails = true;
        getNavigation().stop();
        BoomcartRails.Step step = BoomcartRails.roll(getWorld(), track, getPos(), getVelocity(), railMotor(track),
                railCruise());
        setPosition(step.pos());
        setVelocity(step.heading().multiply(step.speed()));
        checkBlockCollision(); // no move() on the rails: the blocks it rolls over still feel it (detector rails)
        fallDistance = 0;
        setOnGround(true);
        if (step.speed() > 0.01) {
            float yaw = (float) (MathHelper.atan2(step.heading().z, step.heading().x) * MathHelper.DEGREES_PER_RADIAN) - 90f;
            setYaw(MathHelper.stepUnwrappedAngleTowards(getYaw(), yaw, RAIL_TURN));
            bodyYaw = headYaw = getYaw();
        }
        if (!step.onRails()) onRails = false;
        return true;
    }

    /** How it pushes itself along the track: now and then, a short push one way or the other. */
    protected Vec3d railMotor(BoomcartRails.Track track) {
        Vec3d along = new Vec3d(track.bx() - track.ax(), 0, track.bz() - track.az()).normalize();
        if (isLit()) {
            // lit: toward its target, or on the way it goes
            PlayerEntity player = getPanicTarget();
            Vec3d to = player != null ? player.getPos().subtract(getPos()) : getVelocity();
            double sign = to.x * along.x + to.z * along.z >= 0 ? 1 : -1;
            return along.multiply(sign * PANIC_PUSH);
        }
        if (isHungry()) return Vec3d.ZERO;
        if (railPushTicks > 0) {
            railPushTicks--;
        } else if (getVelocity().horizontalLengthSquared() < 1.0e-4 && random.nextInt(RAIL_PUSH_CHANCE) == 0) {
            railPushTicks = RAIL_PUSH_TICKS;
            railPushSign = random.nextBoolean() ? 1 : -1;
        }
        if (railPushTicks <= 0) return Vec3d.ZERO;
        return along.multiply(railPushSign * RAIL_PUSH);
    }

    protected double railCruise() {
        return isLit() ? PANIC_CRUISE : RAIL_CRUISE;
    }

    /** A cart doesn't jump: it bumps into a step it can't roll up (it still bobs up in water). */
    @Override
    public void jump() {
        if (isTouchingWater() || isInLava()) super.jump();
    }

    @Override
    public boolean startRiding(Entity entity, boolean force) {
        return false; // it gets in nothing
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return false; // and nobody gets in
    }

    // ---------------------------------------------------------------- save

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.put("Load", getLoad().encode(getRegistryManager()));
        fuse.write(nbt);
        if (target != null) nbt.putUuid("Target", target);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Load")) {
            ItemStack.fromNbt(getRegistryManager(), nbt.get("Load"))
                    .filter(stack -> stack.isOf(Items.TNT) || stack.isOf(Items.FIREWORK_ROCKET))
                    .ifPresent(this::setLoad);
        }
        fuse.read(nbt);
        setFuse(fuse.ticks(), fuse.total());
        target = nbt.containsUuid("Target") ? nbt.getUuid("Target") : null;
    }

    // ---------------------------------------------------------------- sounds

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return isLit() ? null : ModSounds.BOOMCART_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.BOOMCART_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.BOOMCART_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.BOOMCART_ROLL, 0.4f, 1.0f);
    }

    // ---------------------------------------------------------------- animations

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, MAIN_CONTROLLER, 4, this::animate));
        controllers.add(new AnimationController<>(this, ACTION_CONTROLLER, 2, state -> PlayState.STOP)
                .triggerableAnim("eat", EAT)
                .triggerableAnim("roar", ROAR));
    }

    private PlayState animate(AnimationState<BoomcartEntity> state) {
        if (isLit()) return state.setAndContinue(PANIC);
        if (isHungry()) return state.setAndContinue(FEED_ME);
        double dx = getX() - prevX, dz = getZ() - prevZ;
        return state.setAndContinue(dx * dx + dz * dz > 1.0e-4 ? ROLL : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
