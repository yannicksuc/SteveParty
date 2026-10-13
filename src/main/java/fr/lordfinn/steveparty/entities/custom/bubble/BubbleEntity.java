package fr.lordfinn.steveparty.entities.custom.bubble;

import fr.lordfinn.steveparty.advancement.ModCriteria;
import fr.lordfinn.steveparty.blocks.custom.BubblerBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileContents;
import fr.lordfinn.steveparty.effect.ModEffects;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubbles;
import fr.lordfinn.steveparty.mixin.LivingEntityJumpingAccessor;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
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
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
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
 *     <li><b>Popping</b> ({@link #pop}): a rider's punch (any rider leaving), a puff too many (puffs chained too
 *     fast strain it: {@link #strainedPuff}), bumping into a block (a wall, the floor, a
 *     ceiling) or touching a mob, a player, a boat... ({@link #popsOnTouch}; not in its first moments, rising from its
 *     Bubbler), an arrow or any projectile from outside but a snowball (a snowball pushes it its way; what its riders
 *     shoot from inside flies out through it, {@link #letsOut}), a hit by luck ({@link BubbleRiding#HIT_POP_CHANCE}; never with a Silk Touch
 *     tool, a hit only pushes it then), anything else that hurts, left still too long ({@link #getStillTicks}: 15 s
 *     without moving, never while it travels), crossing the border of a mini-game zone, or nothing left in it. Its
 *     riders fall slowly then ({@link #fallSlowly}); its item drops.</li>
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
    /** The ticks before it bursts by itself, in its last {@link BubbleRiding#WARNING_TICKS} (-1 before: see {@link #weaken}). */
    private static final TrackedData<Integer> FADING = DataTracker.registerData(BubbleEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /**
     * Its strain right after its last puff, and how many strained puffs it took (each one tells its clients to start
     * their own count down from there: see {@link #strain}).
     */
    private static final TrackedData<Float> STRAIN = DataTracker.registerData(BubbleEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> STRAINED_PUFFS = DataTracker.registerData(BubbleEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** How long it trembles after a puff that left it badly strained (ticks), and from what strain. */
    private static final int TREMBLE_TICKS = 12;
    private static final float TREMBLE_FROM = 0.6f;
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
    /** Server: where it was when it last moved, and the ticks it has stayed near there since ({@link #trackStillness}). */
    private @Nullable Vec3d stillAt;
    private int stillTicks;
    /**
     * Server: the Bubbler it rose from and the top of its column (the height it hovers at and a little more), null for
     * a bubble blown otherwise. In that column it is unbreakable by touch and never left still ({@link #inColumn}).
     */
    private @Nullable BlockPos column;
    private double columnTop;
    /**
     * Server: blown by the Bubbled effect ({@link #encloseByEffect}), not by a Bubbler: it never merges, its pop ends
     * the effect, and an effect given for a time pops it when it ends.
     */
    private boolean fromEffect;
    /**
     * Its strain (0..1, see {@link BubbleRiding#strainPopChance}): its puffs add to it, it eases off by itself. The
     * server's; a client counts it down the same way from each strained puff it learns of. A client: the ticks it
     * still trembles after a hard puff.
     */
    private float strain;
    private int trembling;
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
        bubble.column = bubble.origin;
        bubble.columnTop = bubbler.getY() + 1 + height + BubbleRiding.BOB + BubbleRiding.COLUMN_MARGIN;
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
        blown(world, bubble);
        return bubble;
    }

    /**
     * Encloses {@code entity}, under the Bubbled effect, in a bubble rising {@link BubbleRiding#EFFECT_RISE} blocks from
     * where it is (out of its vehicle, its passengers off). A player inside steers it like a Bubbler's; it pops like
     * one (left still, a wall, a mob...), ending the effect.
     *
     * @return the bubble, or null if it can't be (too big, gone)
     */
    public static @Nullable BubbleEntity encloseByEffect(ServerWorld world, LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator() || !BubbleRiding.fits(entity)) return null;
        if (entity.hasVehicle()) entity.stopRiding();
        if (entity.hasPassengers()) entity.removeAllPassengers();
        BubbleEntity bubble = new BubbleEntity(ModEntities.BUBBLE, world);
        bubble.fromEffect = true;
        bubble.origin = entity.getBlockPos();
        bubble.setHoverY(entity.getY() + BubbleRiding.EFFECT_RISE);
        bubble.setSide(BubbleRiding.side(Math.max(entity.getWidth(), entity.getHeight()), 1));
        bubble.refreshPositionAndAngles(entity.getX(), entity.getY(), entity.getZ(), 0, 0);
        world.spawnEntity(bubble);
        entity.startRiding(bubble, true);
        blown(world, bubble);
        return bubble;
    }

    /** The soft sound of a bubble blown. */
    private static void blown(ServerWorld world, BubbleEntity bubble) {
        world.playSound(null, bubble.getX(), bubble.getY(), bubble.getZ(), ModSounds.BUBBLE_BLOW, SoundCategory.BLOCKS, 0.6f,
                0.9f + world.getRandom().nextFloat() * 0.3f);
    }

    /** Whether it was blown by the Bubbled effect (its pop ends the effect). */
    public boolean isFromEffect() {
        return fromEffect;
    }

    /**
     * Whether it is still in its Bubbler's column (server): its centre within {@link BubbleRiding#COLUMN_RADIUS} of the
     * column's axis, its bottom from the Bubbler up to a little over the height it hovers at. There it waits for its
     * riders, rising through a shaft or among a crowd: touching blocks and mobs never pops it, it is never left still.
     */
    public boolean inColumn() {
        if (column == null) return false;
        double dx = getX() - (column.getX() + 0.5), dz = getZ() - (column.getZ() + 0.5);
        return dx * dx + dz * dz <= BubbleRiding.COLUMN_RADIUS * BubbleRiding.COLUMN_RADIUS
                && getY() >= column.getY() - BubbleRiding.COLUMN_MARGIN && getY() <= columnTop;
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
        builder.add(FADING, -1);
        builder.add(STRAIN, 0f);
        builder.add(STRAINED_PUFFS, 0);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (SIDE.equals(data)) calculateDimensions();
        if (STRAINED_PUFFS.equals(data) && getWorld().isClient) {
            strain = dataTracker.get(STRAIN);
            trembling = strain >= TREMBLE_FROM ? TREMBLE_TICKS : 0;
        }
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

    /**
     * Ticks it has stayed still (server): within {@link BubbleRiding#IDLE_MOVE} blocks of where it last moved, nothing
     * propelling it. At {@link BubbleRiding#IDLE_TICKS} it pops.
     */
    public int getStillTicks() {
        return stillTicks;
    }

    public void setStillTicks(int ticks) {
        stillTicks = ticks;
    }

    /** It moves (a push, a puff, its riders' keys): still no more, from where it is. */
    private void stir() {
        stillAt = getPos();
        stillTicks = 0;
    }

    /**
     * After it moved this tick: still one tick more, or moving (far enough from where it was still) from here. Never
     * still in its Bubbler's column, waiting there for its riders.
     */
    private void trackStillness() {
        if (stillAt == null || inColumn() || getPos().squaredDistanceTo(stillAt) > BubbleRiding.IDLE_MOVE * BubbleRiding.IDLE_MOVE) stir();
        else stillTicks++;
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
        boolean worn = fromEffect && effectTicksLeft() <= 0;
        if (popPending || empty || worn || stillTicks >= BubbleRiding.IDLE_TICKS || ZoneBubbles.separated(world, origin, getBlockPos())) {
            pop();
            return;
        }
        if (hasPassengers() && !fromEffect) {
            mergeAround(world);
            if (isRemoved()) return;
        }
        Vec3d drift = BubbleRiding.drift(getPassengerList());
        if (drift.lengthSquared() > 1.0E-8) stir();
        strain = Math.max(0, strain - BubbleRiding.STRAIN_DECAY);
        Vec3d puffed = puffs(world);
        if (isRemoved()) return; // a puff too many
        Vec3d speed = getVelocity().add(drift).add(puffed);
        setVelocity(floatAlong(speed));
        boolean bumped = moveAndBounce();
        if (life > BubbleRiding.LAUNCH_GRACE && !inColumn() && ((bumped && touchesBlock(world)) || touchesEntity(world))) {
            pop();
            return;
        }
        trackStillness();
        weaken(world);
    }

    /** The ticks its riders' Bubbled effect has left (the longest one's; endless from a projectile), an effect bubble's lifetime. */
    private int effectTicksLeft() {
        int left = 0;
        for (Entity rider : getPassengerList()) {
            if (!(rider instanceof LivingEntity living)) continue;
            StatusEffectInstance effect = living.getStatusEffect(ModEffects.BUBBLED);
            if (effect != null) left = Math.max(left, effect.isInfinite() ? Integer.MAX_VALUE : effect.getDuration());
        }
        return left;
    }

    /**
     * Its last {@link BubbleRiding#WARNING_TICKS} before it bursts by itself (left still, or a timed effect ending): its
     * clients learn how long it has left (they show it weakening: {@link #weakness}), droplets fall off it, its film
     * creaks now and then. Moving it again (still from scratch) ends the warning.
     */
    private void weaken(ServerWorld world) {
        int left = BubbleRiding.IDLE_TICKS - stillTicks;
        if (fromEffect) left = Math.min(left, effectTicksLeft());
        int fading = left <= BubbleRiding.WARNING_TICKS ? left : -1;
        if (dataTracker.get(FADING) != fading) dataTracker.set(FADING, fading);
        if (fading < 0) return;
        Vec3d center = center();
        double spread = getSide() / 3;
        if (fading % 6 == 0) {
            world.spawnParticles(ParticleTypes.FALLING_WATER, center.x, center.y - getSide() / 2, center.z, 2, spread, 0.05, spread, 0);
            world.spawnParticles(ParticleTypes.BUBBLE_POP, center.x, center.y, center.z, 2, spread, spread, spread, 0.01);
        }
        if (fading % 15 == 0) {
            world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_HONEY_BLOCK_SLIDE, SoundCategory.NEUTRAL, 0.35f,
                    1.4f + 0.4f * (1 - fading / (float) BubbleRiding.WARNING_TICKS));
        }
    }

    /**
     * How weak it shows (client and server): 0 in its prime, rising to 1 over its last {@link BubbleRiding#WARNING_TICKS}
     * before it bursts by itself.
     */
    public float weakness() {
        int fading = dataTracker.get(FADING);
        return fading < 0 ? 0 : MathHelper.clamp(1 - fading / (float) BubbleRiding.WARNING_TICKS, 0, 1);
    }

    /**
     * Whether it touches a block's collision shape (a wall, the floor, a ceiling), but its Bubbler's (it rises from
     * it): fluids, grass, flowers have none.
     */
    private boolean touchesBlock(ServerWorld world) {
        Box box = getBoundingBox().expand(BubbleRiding.TOUCH);
        for (BlockPos pos : BlockPos.iterate(MathHelper.floor(box.minX), MathHelper.floor(box.minY), MathHelper.floor(box.minZ),
                MathHelper.floor(box.maxX), MathHelper.floor(box.maxY), MathHelper.floor(box.maxZ))) {
            BlockState state = world.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof BubblerBlock) continue;
            VoxelShape shape = state.getCollisionShape(world, pos, ShapeContext.of(this));
            if (shape.isEmpty()) continue;
            for (Box part : shape.getBoundingBoxes()) {
                if (part.offset(pos).intersects(box)) return true;
            }
        }
        return false;
    }

    /** Whether it touches an entity that pops it ({@link #popsOnTouch}). */
    private boolean touchesEntity(ServerWorld world) {
        return !world.getOtherEntities(this, getBoundingBox(), this::popsOnTouch).isEmpty();
    }

    /**
     * Whether touching {@code entity} pops it: a living thing (a mob, a player, an armour stand) or something solid
     * (a boat, a minecart), not in a bubble (its own riders, another bubble's: that one merges or stays apart). Not
     * a projectile (see {@link #letsOut}, {@link #damage}), a dropped item or an experience orb.
     */
    private boolean popsOnTouch(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator() || entity instanceof BubbleEntity || entity instanceof ProjectileEntity) return false;
        if (entity.getRootVehicle() instanceof BubbleEntity) return false;
        return entity instanceof LivingEntity || entity.isCollidable();
    }

    /**
     * Whether {@code projectile} passes through it, harmless: shot from inside it, by one of its riders or from within
     * its walls. Only what comes from outside pops (or pushes) it.
     */
    public boolean letsOut(ProjectileEntity projectile) {
        Entity owner = projectile.getOwner();
        if (owner != null && owner.getRootVehicle() == this) return true;
        return getBoundingBox().contains(projectile.getPos());
    }

    /**
     * A client moves it as the server does, its own rider's keys and puff at once ({@link #clientSteer}), and eases it
     * toward where the server has it (moved on since by its speed): no jerk, no lag on its own keys.
     */
    private void clientTick() {
        strain = Math.max(0, strain - BubbleRiding.STRAIN_DECAY);
        if (trembling > 0) trembling--;
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

    /**
     * Moves by its speed; a wall, a floor or a ceiling sends it back a little (on a client, until the server pops it).
     *
     * @return whether it bumped into something
     */
    private boolean moveAndBounce() {
        Vec3d speed = getVelocity();
        if (speed.lengthSquared() < 1.0E-8) return false;
        Vec3d before = getPos();
        move(MovementType.SELF, speed);
        Vec3d moved = getPos().subtract(before);
        double x = Math.abs(moved.x - speed.x) > 1.0E-4 ? -speed.x * BubbleRiding.BOUNCE : speed.x;
        double y = Math.abs(moved.y - speed.y) > 1.0E-4 ? -speed.y * BubbleRiding.BOUNCE : speed.y;
        double z = Math.abs(moved.z - speed.z) > 1.0E-4 ? -speed.z * BubbleRiding.BOUNCE : speed.z;
        setVelocity(x, y, z);
        return x != speed.x || y != speed.y || z != speed.z;
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
                ModCriteria.fire(player, "bubble_boost", 100 * charge / BubbleRiding.CHARGE_MAX);
                rested.put(id, life + BubbleRiding.PUFF_COOLDOWN);
                Vec3d behind = center().subtract(look.multiply(getSide() / 2));
                world.spawnParticles(ParticleTypes.CLOUD, behind.x, behind.y, behind.z, 5, 0.12, 0.12, 0.12, 0.03);
                world.spawnParticles(ParticleTypes.BUBBLE_POP, behind.x, behind.y, behind.z, 8, 0.2, 0.2, 0.2, 0.05);
                world.playSound(null, behind.x, behind.y, behind.z, ModSounds.BUBBLE_WHOOSH, SoundCategory.PLAYERS, 0.7f,
                        1.2f - (float) BubbleRiding.puffStrength(charge) * 0.3f);
                if (strainedPuff(world)) {
                    for (Entity popped : riders) ModCriteria.fire(popped instanceof PlayerEntity p ? p : null, "bubble_strain_pop");
                    pop();
                    return Vec3d.ZERO;
                }
            }
        }
        if (push != Vec3d.ZERO) {
            release();
            stir();
            velocityModified = true;
        }
        return push;
    }

    /**
     * A puff strains it ({@link BubbleRiding#STRAIN_PER_PUFF}); already strained, it may pop
     * ({@link BubbleRiding#strainedPuffPops}). Shown to all, the more the more strained: its clients learn its strain
     * (its film stretches, see BubbleEntityRenderer; its rider's charge bar reddens), its film creaks higher and higher
     * and sheds droplets.
     *
     * @return whether it pops
     */
    private boolean strainedPuff(ServerWorld world) {
        float before = strain;
        if (BubbleRiding.strainedPuffPops(before, random)) return true;
        strain = Math.min(1, before + BubbleRiding.STRAIN_PER_PUFF);
        dataTracker.set(STRAIN, strain);
        dataTracker.set(STRAINED_PUFFS, dataTracker.get(STRAINED_PUFFS) + 1);
        if (before > 0.05f) {
            Vec3d center = center();
            double spread = getSide() / 2;
            world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_SLIME_SQUISH_SMALL, SoundCategory.PLAYERS,
                    0.4f + 0.5f * strain, 0.9f + 0.9f * strain);
            world.spawnParticles(ParticleTypes.SPLASH, center.x, center.y, center.z, 2 + Math.round(12 * strain), spread, spread, spread, 0.15);
        }
        return false;
    }

    /** Its strain (0..1): the server's, or as its client counts it down. */
    public float strain() {
        return strain;
    }

    public void setStrain(float strain) {
        this.strain = strain;
    }

    /** How hard it still trembles after a puff that left it badly strained (client, 0..1). */
    public float trembling() {
        return trembling / (float) TREMBLE_TICKS;
    }

    /** A push (blocks a tick, before its weight): it floats freely from then on. */
    public void push(Vec3d push) {
        setVelocity(getVelocity().add(push.multiply(1.0 / mass())));
        release();
        stir();
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
            if (other.isRemoved() || other.fromEffect || !takesIn(other)) continue;
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
        strain = Math.max(strain, other.strain);
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
        int count = getPassengerList().size();
        for (Entity rider : getPassengerList()) ModCriteria.fire(rider instanceof PlayerEntity p ? p : null, "bubble_merge", count);
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
        if (fromEffect && passenger instanceof LivingEntity living) living.removeStatusEffect(ModEffects.BUBBLED);
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
        if (direct instanceof ProjectileEntity projectile && letsOut(projectile)) return false;
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

    /** Walking into it nudges it (and pops it once it has left its Bubbler: {@link #popsOnTouch}). */
    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        life = nbt.getInt("Life");
        stillTicks = nbt.getInt("StillTicks");
        stillAt = getPos(); // still from where it was saved
        fromEffect = nbt.getBoolean("FromEffect");
        column = nbt.contains("Column") ? BlockPos.fromLong(nbt.getLong("Column")) : null;
        columnTop = nbt.getDouble("ColumnTop");
        origin = nbt.contains("Origin") ? BlockPos.fromLong(nbt.getLong("Origin")) : null;
        setHoverY(nbt.contains("HoverY") ? nbt.getDouble("HoverY") : Double.NaN);
        setItem(nbt.contains("Item") ? ItemStack.fromNbt(getRegistryManager(), nbt.get("Item")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY);
        if (!getItem().isEmpty()) setSide(BubbleRiding.ITEM_SIDE);
        else if (nbt.contains("Side")) setSide(nbt.getFloat("Side"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Life", life);
        nbt.putInt("StillTicks", stillTicks);
        if (fromEffect) nbt.putBoolean("FromEffect", true);
        if (column != null) {
            nbt.putLong("Column", column.asLong());
            nbt.putDouble("ColumnTop", columnTop);
        }
        if (origin != null) nbt.putLong("Origin", origin.asLong());
        if (isHovering()) nbt.putDouble("HoverY", getHoverY());
        if (!getItem().isEmpty()) nbt.put("Item", getItem().encode(getRegistryManager()));
        nbt.putFloat("Side", getSide());
    }
}
