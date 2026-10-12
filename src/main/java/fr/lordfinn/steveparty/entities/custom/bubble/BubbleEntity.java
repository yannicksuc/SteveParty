package fr.lordfinn.steveparty.entities.custom.bubble;

import fr.lordfinn.steveparty.blocks.custom.BubblerBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileContents;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubbles;
import fr.lordfinn.steveparty.mixin.LivingEntityJumpingAccessor;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * A bubble blown by a Bubbler ({@link BubblerBlock}) around what stepped into it: a mob or a player rides it (like a
 * boat), a dropped item floats in it ({@link #getItem}). A cube of soap film (the Spawn Bubble's), its side growing with
 * its riders ({@link BubbleRiding#side}); its position is its bottom's centre.
 * <ul>
 *     <li><b>Rising, hovering</b>: it rises to its height over its Bubbler ({@link #getHoverY}, see
 *     {@link BubbleRiding#launchHeight}) and hovers there, bobbing, until it is pushed or puffed: then it floats freely,
 *     the air slowly stopping it, never falling.</li>
 *     <li><b>Steering</b>: its players' movement keys make it drift, their charged jump puffs it where they look
 *     ({@link BubbleRiding}); every rider's keys count.</li>
 *     <li><b>Merging</b>: two bubbles with riders touching become one bigger bubble with all of them
 *     ({@link #absorb}), the one with more riders (else the older) taking the other in.</li>
 *     <li><b>Popping</b> ({@link #pop}): a rider's punch (any rider leaving), an arrow or any projectile but a snowball
 *     (a snowball pushes it its way), a hit by luck ({@link BubbleRiding#HIT_POP_CHANCE}; never with a Silk Touch
 *     tool, a hit only pushes it then), anything else that hurts, its time up ({@link BubbleRiding#LIFETIME}), or
 *     crossing the border of a mini-game zone. Its riders fall slowly then ({@link #fallSlowly}); its item drops.</li>
 * </ul>
 * The server moves it; a client moves it the same way between the server's updates, its own rider's keys at once,
 * and eases it toward the server's position ({@link #clientTick}).
 */
public class BubbleEntity extends Entity {
    /** Its side (blocks). */
    private static final TrackedData<Float> SIDE = DataTracker.registerData(BubbleEntity.class, TrackedDataHandlerRegistry.FLOAT);
    /** The height its bottom hovers at (NaN: floating freely). */
    private static final TrackedData<Float> HOVER_Y = DataTracker.registerData(BubbleEntity.class, TrackedDataHandlerRegistry.FLOAT);
    /** The item floating in it (empty: riders). */
    private static final TrackedData<ItemStack> ITEM = DataTracker.registerData(BubbleEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    /** How much of the gap to the server's position a client closes a tick, and from how far it jumps there at once. */
    private static final double CORRECTION = 0.3, SNAP = 4;

    /**
     * A client's own steering for a bubble its player rides: the speed his keys and his puff add this tick (set by the
     * client: its keys are read there before the server knows them).
     */
    public static Function<BubbleEntity, Vec3d> clientSteer = bubble -> Vec3d.ZERO;

    /** Server: ticks since it was blown, where it was blown (its zone, if any, is its world), its riders' puffs. */
    private int life;
    private @Nullable BlockPos origin;
    private final Map<UUID, Integer> charges = new HashMap<>();
    private final Map<UUID, Integer> rested = new HashMap<>();
    /** Server: its riders going over to a bigger bubble (they don't pop it), it popping, one of its riders gone. */
    private boolean transferring, popping, popPending;
    /** Client: the server's last position, and the ticks since. */
    private @Nullable Vec3d serverPos;
    private int sinceUpdate;

    public BubbleEntity(EntityType<? extends BubbleEntity> type, World world) {
        super(type, world);
    }

    /**
     * Encloses {@code entity} (stepping into the Bubbler at {@code bubbler}) in a new bubble rising {@code height}
     * blocks: a rider, or a dropped item taken into it. Nothing if it can't be ({@link #canEnclose}).
     *
     * @return the bubble, or null
     */
    public static @Nullable BubbleEntity enclose(ServerWorld world, Entity entity, BlockPos bubbler, int height) {
        if (!canEnclose(entity)) return null;
        BubbleEntity bubble = new BubbleEntity(ModEntities.BUBBLE, world);
        bubble.origin = bubbler.toImmutable();
        bubble.setHoverY(bubbler.getY() + 1 + height);
        double bottom = Math.max(entity.getY(), bubbler.getY() + BubblerBlock.TOP);
        if (entity instanceof ItemEntity item) {
            bubble.setItem(item.getStack().copy());
            bubble.setSide(BubbleRiding.ITEM_SIDE);
            bubble.refreshPositionAndAngles(item.getX(), bottom, item.getZ(), 0, 0);
            world.spawnEntity(bubble);
            item.discard();
        } else {
            bubble.setSide(BubbleRiding.side(Math.max(entity.getWidth(), entity.getHeight()), 1));
            bubble.refreshPositionAndAngles(entity.getX(), bottom, entity.getZ(), 0, 0);
            world.spawnEntity(bubble);
            entity.startRiding(bubble, true);
        }
        world.playSound(null, bubble.getX(), bubble.getY(), bubble.getZ(), ModSounds.BUBBLE_BLOW, SoundCategory.BLOCKS, 0.6f,
                0.9f + world.getRandom().nextFloat() * 0.3f);
        return bubble;
    }

    /** Whether a Bubbler encloses it: a small living thing or an item, not in a vehicle, carrying none, not sneaking. */
    public static boolean canEnclose(Entity entity) {
        if (!entity.isAlive() || entity instanceof BubbleEntity || entity.hasVehicle() || entity.hasPassengers()) return false;
        if (entity.isSpectator() || entity.isSneaking() || !BubbleRiding.fits(entity)) return false;
        return !(entity instanceof ItemEntity item) || !item.getStack().isEmpty();
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(SIDE, BubbleRiding.side(1.8f, 1));
        builder.add(HOVER_Y, Float.NaN);
        builder.add(ITEM, ItemStack.EMPTY);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (SIDE.equals(data)) calculateDimensions();
    }

    // ---------------------------------------------------------------- state

    public float getSide() {
        return dataTracker.get(SIDE);
    }

    private void setSide(float side) {
        dataTracker.set(SIDE, side);
    }

    /** The height its bottom hovers at, NaN once it floats freely. */
    public double getHoverY() {
        return dataTracker.get(HOVER_Y);
    }

    public boolean isHovering() {
        return !Float.isNaN(dataTracker.get(HOVER_Y));
    }

    public void setHoverY(double y) {
        dataTracker.set(HOVER_Y, (float) y);
    }

    /** It floats freely from now on (pushed, puffed). */
    public void release() {
        if (isHovering()) setHoverY(Double.NaN);
    }

    /** The item floating in it (empty when it holds riders). */
    public ItemStack getItem() {
        return dataTracker.get(ITEM);
    }

    private void setItem(ItemStack stack) {
        dataTracker.set(ITEM, stack);
    }

    /** Ticks since it was blown (server). */
    public int getLife() {
        return life;
    }

    public void setLife(int life) {
        this.life = life;
    }

    /** How heavy it is: its riders (an item bubble weighs one). */
    public int mass() {
        return Math.max(1, getPassengerList().size());
    }

    public Vec3d center() {
        return new Vec3d(getX(), getY() + getSide() / 2, getZ());
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        float side = getSide();
        return EntityDimensions.fixed(side, side);
    }

    /** Its side again, from its riders (they came, went or merged). */
    private void resize() {
        List<Entity> riders = getPassengerList();
        if (riders.isEmpty()) return;
        float largest = 0;
        for (Entity rider : riders) largest = Math.max(largest, Math.max(rider.getWidth(), rider.getHeight()));
        Vec3d center = center();
        float side = BubbleRiding.side(largest, riders.size());
        if (side == getSide()) return;
        setSide(side);
        // grown around its centre, not from its bottom
        setPosition(center.x, center.y - side / 2, center.z);
    }

    // ---------------------------------------------------------------- moving

    @Override
    public void tick() {
        super.tick();
        if (getWorld() instanceof ServerWorld world) serverTick(world);
        else clientTick();
    }

    private void serverTick(ServerWorld world) {
        life++;
        if (origin == null) origin = getBlockPos(); // not blown by a Bubbler (summoned): its zone is where it began
        boolean empty = !hasPassengers() && getItem().isEmpty();
        if (popPending || empty || life >= BubbleRiding.LIFETIME || ZoneBubbles.separated(world, origin, getBlockPos())) {
            pop();
            return;
        }
        if (hasPassengers()) {
            mergeAround(world);
            if (isRemoved()) return;
        }
        Vec3d speed = getVelocity().add(BubbleRiding.drift(getPassengerList())).add(puffs(world));
        setVelocity(floatAlong(speed));
        moveAndBounce();
    }

    /**
     * A client moves it as the server does, its own rider's keys and puff at once ({@link #clientSteer}), and eases it
     * toward where the server has it (moved on since by its speed): no jerk, no lag on its own keys.
     */
    private void clientTick() {
        setVelocity(floatAlong(getVelocity().add(clientSteer.apply(this))));
        moveAndBounce();
        if (serverPos == null) return;
        Vec3d target = serverPos.add(getVelocity().multiply(sinceUpdate++));
        Vec3d pos = getPos();
        if (pos.squaredDistanceTo(target) > SNAP * SNAP) setPosition(target);
        else setPosition(pos.lerp(target, CORRECTION));
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
        serverPos = new Vec3d(x, y, z);
        sinceUpdate = 0;
    }

    /** Its speed this tick: hovering toward its height (bobbing) or floating freely, the air slowing it. */
    private Vec3d floatAlong(Vec3d speed) {
        double vy;
        if (isHovering()) {
            vy = BubbleRiding.hover(getY(), getHoverY() + BubbleRiding.bob(age), speed.y);
        } else {
            vy = speed.y * BubbleRiding.DRAG + (BubbleRiding.bob(age + 1) - BubbleRiding.bob(age)) * 0.2;
        }
        return new Vec3d(speed.x * BubbleRiding.DRAG, vy, speed.z * BubbleRiding.DRAG);
    }

    /** Moves by its speed; a wall, a floor or a ceiling sends it back a little. */
    private void moveAndBounce() {
        Vec3d speed = getVelocity();
        if (speed.lengthSquared() < 1.0E-8) return;
        Vec3d before = getPos();
        move(MovementType.SELF, speed);
        Vec3d moved = getPos().subtract(before);
        double x = Math.abs(moved.x - speed.x) > 1.0E-4 ? -speed.x * BubbleRiding.BOUNCE : speed.x;
        double y = Math.abs(moved.y - speed.y) > 1.0E-4 ? -speed.y * BubbleRiding.BOUNCE : speed.y;
        double z = Math.abs(moved.z - speed.z) > 1.0E-4 ? -speed.z * BubbleRiding.BOUNCE : speed.z;
        setVelocity(x, y, z);
    }

    /**
     * Its players' puffs this tick: each one's jump key held charges his puff, released it puffs (see
     * {@link BubbleRiding#puff}), a whoosh and a burst of air behind.
     */
    private Vec3d puffs(ServerWorld world) {
        Vec3d push = Vec3d.ZERO;
        List<Entity> riders = getPassengerList();
        for (Entity rider : riders) {
            if (!(rider instanceof PlayerEntity player)) continue;
            UUID id = player.getUuid();
            int charge = charges.getOrDefault(id, 0);
            if (((LivingEntityJumpingAccessor) player).steveparty$isJumping()) {
                if (life >= rested.getOrDefault(id, 0)) charges.put(id, Math.min(BubbleRiding.CHARGE_MAX, charge + 1));
            } else if (charge > 0) {
                charges.remove(id);
                if (charge < BubbleRiding.CHARGE_MIN) continue;
                Vec3d look = player.getRotationVector();
                push = push.add(BubbleRiding.puff(charge, look, riders.size()));
                rested.put(id, life + BubbleRiding.PUFF_COOLDOWN);
                Vec3d behind = center().subtract(look.multiply(getSide() / 2));
                world.spawnParticles(ParticleTypes.CLOUD, behind.x, behind.y, behind.z, 5, 0.12, 0.12, 0.12, 0.03);
                world.spawnParticles(ParticleTypes.BUBBLE_POP, behind.x, behind.y, behind.z, 8, 0.2, 0.2, 0.2, 0.05);
                world.playSound(null, behind.x, behind.y, behind.z, ModSounds.BUBBLE_WHOOSH, SoundCategory.PLAYERS, 0.7f,
                        1.2f - (float) BubbleRiding.puffStrength(charge) * 0.3f);
            }
        }
        if (push != Vec3d.ZERO) {
            release();
            velocityModified = true;
        }
        return push;
    }

    /** A push (blocks a tick, before its weight): it floats freely from then on. */
    public void push(Vec3d push) {
        setVelocity(getVelocity().add(push.multiply(1.0 / mass())));
        release();
        velocityModified = true;
    }

    /** The charge each of its players holds (server, ticks). */
    public int chargeOf(PlayerEntity player) {
        return charges.getOrDefault(player.getUuid(), 0);
    }

    // ---------------------------------------------------------------- merging

    /** The bubbles with riders it touches that it takes in (see {@link #takesIn}). */
    private void mergeAround(ServerWorld world) {
        Box box = getBoundingBox();
        for (BubbleEntity other : world.getEntitiesByClass(BubbleEntity.class, box, other -> other != this && other.hasPassengers())) {
            if (other.isRemoved() || !takesIn(other)) continue;
            if (getPassengerList().size() + other.getPassengerList().size() > BubbleRiding.MAX_RIDERS) continue;
            absorb(other);
        }
    }

    /** Of two bubbles touching, the one with more riders takes the other in; else the older one; else the first. */
    private boolean takesIn(BubbleEntity other) {
        int mine = getPassengerList().size(), theirs = other.getPassengerList().size();
        if (mine != theirs) return mine > theirs;
        if (life != other.life) return life > other.life;
        return getId() < other.getId();
    }

    /**
     * Takes {@code other}'s riders in: one bigger bubble where both were (weighed by their riders), going their way
     * together, hovering if either did (as high as the higher), as young as the younger.
     */
    public void absorb(BubbleEntity other) {
        double mine = getPassengerList().size(), theirs = other.getPassengerList().size(), all = mine + theirs;
        Vec3d center = center().multiply(mine / all).add(other.center().multiply(theirs / all));
        Vec3d speed = getVelocity().multiply(mine / all).add(other.getVelocity().multiply(theirs / all));
        if (other.isHovering()) setHoverY(isHovering() ? Math.max(getHoverY(), other.getHoverY()) : other.getHoverY());
        life = Math.min(life, other.life);
        other.transferring = true;
        for (Entity rider : List.copyOf(other.getPassengerList())) {
            rider.stopRiding();
            rider.startRiding(this, true);
        }
        other.discard();
        resize();
        setPosition(center.x, center.y - getSide() / 2, center.z);
        setVelocity(speed);
        velocityModified = true;
        if (getWorld() instanceof ServerWorld world) {
            world.playSound(null, center.x, center.y, center.z, ModSounds.BUBBLE_MERGE, SoundCategory.PLAYERS, 0.8f, 0.8f);
            world.spawnParticles(ParticleTypes.BUBBLE_POP, center.x, center.y, center.z, 12, getSide() / 3, getSide() / 3, getSide() / 3, 0.02);
        }
    }

    // ---------------------------------------------------------------- riders

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getItem().isEmpty() && getPassengerList().size() < BubbleRiding.MAX_RIDERS;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!getWorld().isClient) resize();
    }

    /** A rider leaving (sneaking, punching, teleported...) pops it, the others with him; he falls slowly. */
    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (getWorld().isClient || transferring) return;
        fallSlowly(passenger);
        if (!popping) popPending = true;
    }

    /** Its riders float inside, spread around its middle, swaying a little. */
    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
        List<Entity> riders = getPassengerList();
        int index = Math.max(0, riders.indexOf(passenger)), count = riders.size();
        double side = getSide();
        double radius = count <= 1 ? 0 : side * 0.22;
        double angle = index * MathHelper.TAU / count + age * 0.01;
        double swayX = Math.sin(age * 0.05 + index * 2.1) * 0.06, swayY = Math.sin(age * 0.07 + index * 1.7) * 0.05;
        double swayZ = Math.cos(age * 0.043 + index * 1.3) * 0.06;
        positionUpdater.accept(passenger, getX() + Math.cos(angle) * radius + swayX,
                getY() + side / 2 - passenger.getHeight() / 2 + swayY, getZ() + Math.sin(angle) * radius + swayZ);
    }

    /** Out of it, a rider stays where he floated (and falls slowly). */
    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        return passenger.getPos();
    }

    /** A rider's punch: he leaves it, and it pops. */
    public void riderPunch(PlayerEntity rider) {
        if (hasPassenger(rider)) pop();
    }

    // ---------------------------------------------------------------- hits, popping

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient || isRemoved() || isInvulnerableTo(source)) return false;
        Entity direct = source.getSource(), attacker = source.getAttacker();
        if (attacker != null && hasPassenger(attacker) && direct == attacker) {
            pop(); // a rider's own punch
            return true;
        }
        if (direct instanceof SnowballEntity snowball) {
            Vec3d way = snowball.getVelocity().lengthSquared() > 1.0E-6 ? snowball.getVelocity().normalize() : center().subtract(snowball.getPos()).normalize();
            push(way.multiply(BubbleRiding.SNOWBALL_PUSH));
            return true;
        }
        if (source.isIn(DamageTypeTags.IS_PROJECTILE)) {
            pop();
            return true;
        }
        if (direct instanceof LivingEntity hitter && direct == attacker) {
            Vec3d away = center().subtract(hitter.getEyePos());
            away = away.lengthSquared() > 1.0E-6 ? away.normalize() : hitter.getRotationVector();
            push(away.multiply(BubbleRiding.HIT_PUSH).add(0, 0.08, 0));
            boolean silk = TileContents.hasSilkTouch(getWorld(), hitter.getMainHandStack());
            if (!silk && random.nextFloat() < BubbleRiding.HIT_POP_CHANCE) pop();
            return true;
        }
        pop();
        return true;
    }

    /** It pops: a soft pop and droplets, its riders out falling slowly, its item dropped. */
    public void pop() {
        if (isRemoved() || !(getWorld() instanceof ServerWorld world)) return;
        popping = true;
        Vec3d center = center();
        world.playSound(null, center.x, center.y, center.z, ModSounds.BUBBLE_POP, SoundCategory.PLAYERS, 1.0f,
                1.1f + random.nextFloat() * 0.3f - (mass() - 1) * 0.05f);
        double spread = getSide() / 3;
        world.spawnParticles(ParticleTypes.BUBBLE_POP, center.x, center.y, center.z, 10 + 4 * mass(), spread, spread, spread, 0.03);
        world.spawnParticles(ParticleTypes.SPLASH, center.x, center.y, center.z, 12, spread, spread, spread, 0.1);
        removeAllPassengers();
        ItemStack item = getItem();
        if (!item.isEmpty()) {
            ItemEntity drop = new ItemEntity(world, center.x, center.y - 0.125, center.z, item.copy(), 0, 0, 0);
            drop.setToDefaultPickupDelay();
            world.spawnEntity(drop);
            setItem(ItemStack.EMPTY);
        }
        discard();
    }

    /** Out of a bubble: no fall damage, Slow Falling long enough to reach the ground ({@link BubbleRiding#slowFallingTicks}). */
    private void fallSlowly(Entity rider) {
        rider.fallDistance = 0;
        if (!(rider instanceof LivingEntity living)) return;
        living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING,
                BubbleRiding.slowFallingTicks(dropUnder(rider)), 0, false, true), this);
    }

    /** How far {@code rider} is above the ground (or {@link BubbleRiding#GROUND_SEARCH} if none is that near). */
    private double dropUnder(Entity rider) {
        BlockPos.Mutable pos = rider.getBlockPos().mutableCopy();
        for (int down = 0; down < BubbleRiding.GROUND_SEARCH && pos.getY() > getWorld().getBottomY(); down++) {
            if (!getWorld().getBlockState(pos).getCollisionShape(getWorld(), pos).isEmpty()) return Math.max(0, rider.getY() - (pos.getY() + 1));
            pos.move(0, -1, 0);
        }
        return BubbleRiding.GROUND_SEARCH;
    }

    // ---------------------------------------------------------------- vanilla

    /** Hit by hands and projectiles. */
    @Override
    public boolean canHit() {
        return !isRemoved();
    }

    /** Nothing stands on it. */
    @Override
    public boolean isCollidable() {
        return false;
    }

    /** Walking into it nudges it. */
    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        life = nbt.getInt("Life");
        origin = nbt.contains("Origin") ? BlockPos.fromLong(nbt.getLong("Origin")) : null;
        setHoverY(nbt.contains("HoverY") ? nbt.getDouble("HoverY") : Double.NaN);
        setItem(nbt.contains("Item") ? ItemStack.fromNbt(getRegistryManager(), nbt.get("Item")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY);
        if (!getItem().isEmpty()) setSide(BubbleRiding.ITEM_SIDE);
        else if (nbt.contains("Side")) setSide(nbt.getFloat("Side"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Life", life);
        if (origin != null) nbt.putLong("Origin", origin.asLong());
        if (isHovering()) nbt.putDouble("HoverY", getHoverY());
        if (!getItem().isEmpty()) nbt.put("Item", getItem().encode(getRegistryManager()));
        nbt.putFloat("Side", getSide());
    }
}
