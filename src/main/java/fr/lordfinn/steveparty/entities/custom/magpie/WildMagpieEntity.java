package fr.lordfinn.steveparty.entities.custom.magpie;

import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlockEntity;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A wild Pie (a magpie) of the woods (see {@link WildMagpieSpawns}): it flies from tree to tree, rests a while on a
 * perch (looks around, preens, hops round, flicks its tail, calls "piou piou piou"), sleeps on it at night and flies
 * off from players who come too close (sneaking, one gets closer) or hit it. It loves fences, walls, logs and chains
 * ({@link MagpiePerches#FAVOURITES}), the tops of the trees next.
 * <p>
 * Cheap on the server: no goals, no pathfinding. It is moved by hand (no gravity, no collisions): perched, it stands
 * still exactly on its perch's visual top ({@link MagpiePerches#perchOn}, its hitbox there too); a flight is one
 * curve (a quadratic Bézier from where it is, up over the way, down to the perch), checked once for obstacles with a
 * few raycasts when it is chosen, followed tick by tick (flapping up, gliding down). A perch is looked for by a few
 * dozen random samples around it (the top block of a column, or a favourite block a little lower), not a scan.
 * <p>
 * At night it sleeps in a Magpie Nest within {@link #SLEEP_RADIUS} blocks if there is a free one (its own first; one
 * Pie per nest, never a Common pot's nest), facing the nest's way (beside a pile of coins: on the free corner of the
 * rim), and leaves it at dawn. By day it picks up the shiny things lying on the ground ({@link FrousseuxEntity#SHINY},
 * item entities only, never from a player), one at a time in its beak, and brings them to its nest (the nearest
 * free one within {@link #NEST_RADIUS} blocks, adopted as its own); without a nest it drops it after a while. All by
 * checks every {@link #NEST_CHECK_INTERVAL} / {@link #SHINY_CHECK_INTERVAL} ticks, the nests known without scanning
 * blocks ({@link MagpieNestBlockEntity#loadedNests}).
 * <p>
 * Not the Common pot's Pie ({@link MagpieEntity}, bound to its nest, scripted): a separate kind sharing its model.
 */
public class WildMagpieEntity extends MobEntity implements GeoEntity {
    private static final TrackedData<Integer> VARIANT = DataTracker.registerData(WildMagpieEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Byte> STATE = DataTracker.registerData(WildMagpieEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<ItemStack> CARRIED = DataTracker.registerData(WildMagpieEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    public static final byte PERCHED = 0, FLYING = 1, GLIDING = 2, ASLEEP = 3;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.magpie.idle");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.magpie.fly");
    private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("animation.magpie.glide");
    private static final RawAnimation SLEEP = RawAnimation.begin().thenLoop("animation.magpie.sleep");
    private static final RawAnimation PREEN = RawAnimation.begin().thenPlay("animation.magpie.preen");
    private static final RawAnimation HOP = RawAnimation.begin().thenPlay("animation.magpie.hop");

    /** How far it looks for its next perch (blocks, horizontally). */
    public static final int SEARCH_RADIUS = 16;
    /** Blocks per tick in flight (on average). */
    private static final double FLIGHT_SPEED = 0.42;
    /** A player this close makes it fly off (sneaking: {@link #FLEE_RANGE_SNEAKING}). */
    public static final double FLEE_RANGE = 6, FLEE_RANGE_SNEAKING = 2.5;
    private static final int FLEE_CHECK_INTERVAL = 5, PERCH_CHECK_INTERVAL = 10;
    /** How far it looks for a nest to sleep in, and for a nest of its own (blocks). */
    public static final int SLEEP_RADIUS = 24, NEST_RADIUS = 32;
    /** How far it sees a shiny thing on the ground (blocks, horizontally; 6 up or down). */
    public static final int SHINY_RANGE = 12;
    public static final int NEST_CHECK_INTERVAL = 40, SHINY_CHECK_INTERVAL = 40;
    /** Without a nest, it drops what it carries after this long. */
    public static final int CARRY_TICKS = 1200;

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    /** The block it stands on (null: in flight, or not settled yet). */
    private @Nullable BlockPos perch;
    private @Nullable Flight flight;
    private int flightTick;
    /** Ticks until it flies off to another perch (day time only). */
    private int restTicks = 60;
    private int actionCooldown;
    /** Its nest: where it brings the shiny things (null: none adopted yet). */
    private @Nullable BlockPos nest;
    private int carryTicks;
    /** Tests: night (true) or day (false) whatever the time; null: the world's time. */
    private @Nullable Boolean nightOverride;

    /** A flight: its curve, how long, the perch at its end (or the shiny thing it goes to pick up). */
    record Flight(Vec3d from, Vec3d control, Vec3d to, BlockPos target, int ticks, @Nullable UUID item) {
        Flight(Vec3d from, Vec3d control, Vec3d to, BlockPos target, int ticks) {
            this(from, control, to, target, ticks, null);
        }

        Vec3d at(double s) {
            double u = 1 - s;
            return from.multiply(u * u).add(control.multiply(2 * u * s)).add(to.multiply(s * s));
        }
    }

    public WildMagpieEntity(EntityType<? extends MobEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        setNoGravity(true);
    }

    public static DefaultAttributeContainer.Builder setAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 4.0)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.4)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(VARIANT, MagpieVariant.CLASSIC.ordinal());
        builder.add(STATE, PERCHED);
        builder.add(CARRIED, ItemStack.EMPTY);
    }

    public MagpieVariant getVariant() {
        return MagpieVariant.byId(dataTracker.get(VARIANT));
    }

    public void setVariant(MagpieVariant variant) {
        dataTracker.set(VARIANT, variant.ordinal());
    }

    public byte getFlightState() {
        return dataTracker.get(STATE);
    }

    private void setFlightState(byte state) {
        if (dataTracker.get(STATE) != state) dataTracker.set(STATE, state);
    }

    public @Nullable BlockPos getPerch() {
        return perch;
    }

    public boolean isInFlight() {
        return flight != null;
    }

    /** The shiny thing in its beak (empty: nothing). */
    public ItemStack getCarried() {
        return dataTracker.get(CARRIED);
    }

    private void setCarried(ItemStack stack) {
        dataTracker.set(CARRIED, stack);
        carryTicks = 0;
    }

    /** Its nest (null: none adopted). */
    public @Nullable BlockPos getNest() {
        return nest;
    }

    /** Its nest from now on (born with a nest in the trees, MagpieNestFeature). */
    public void adoptNest(BlockPos pos) {
        nest = pos.toImmutable();
    }

    /** Tests: night ({@code true}) or day ({@code false}) whatever the time, {@code null}: the world's time. */
    public void setNightOverride(@Nullable Boolean night) {
        nightOverride = night;
    }

    private boolean isNight(ServerWorld world) {
        return nightOverride != null ? nightOverride : !world.isDay();
    }

    // ---------------------------------------------------------------- spawn, save

    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
                                 @Nullable EntityData entityData) {
        setVariant(spawnReason == SpawnReason.NATURAL || spawnReason == SpawnReason.CHUNK_GENERATION
                ? WildMagpieSpawns.variantFor(world, getBlockPos(), random)
                : WildMagpieSpawns.randomVariant(random, true));
        setYaw(random.nextFloat() * 360);
        restTicks = 20 + random.nextInt(60);
        return super.initialize(world, difficulty, spawnReason, entityData);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Variant", getVariant().ordinal());
        if (perch != null && flight == null) nbt.put("Perch", NbtHelper.fromBlockPos(perch));
        if (nest != null) nbt.put("Nest", NbtHelper.fromBlockPos(nest));
        if (!getCarried().isEmpty()) nbt.put("Carried", getCarried().encode(getRegistryManager()));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setVariant(MagpieVariant.byId(nbt.getInt("Variant")));
        perch = NbtHelper.toBlockPos(nbt, "Perch").orElse(null);
        nest = NbtHelper.toBlockPos(nbt, "Nest").orElse(null);
        setCarried(nbt.contains("Carried") ? ItemStack.fromNbt(getRegistryManager(), nbt.get("Carried")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY);
    }

    /** What it carries falls when it dies. */
    @Override
    protected void dropInventory() {
        super.dropInventory();
        ItemStack carried = getCarried();
        if (!carried.isEmpty()) {
            dropStack(carried);
            setCarried(ItemStack.EMPTY);
        }
    }

    /**
     * Far from every player (beyond 64 blocks) a wild one may go away, as monsters do; a named one stays, and one with
     * a nest of its own or something in its beak.
     */
    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return distanceSquared > 64 * 64 && getCarried().isEmpty() && nest == null;
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        setVelocity(Vec3d.ZERO);
        super.tick();
        setVelocity(Vec3d.ZERO);
        if (!(getWorld() instanceof ServerWorld world) || isAiDisabled() || isDead()) return;
        if (!getCarried().isEmpty()) tickCarrying(world);
        if (flight != null) tickFlight(world);
        else if (perch == null) settle(world);
        else tickPerched(world);
    }

    /** Perched: it stays, does a little something now and then, flies off when rested, scared or its perch is gone. */
    private void tickPerched(ServerWorld world) {
        if (age % PERCH_CHECK_INTERVAL == 0) {
            Vec3d at = MagpiePerches.perchOn(world, perch);
            if (at == null || !MagpiePerches.hasRoom(world, perch, at)) {
                perch = null;
                if (!takeOff(world, null)) settle(world);
                return;
            }
            if (at.squaredDistanceTo(getPos()) > 1.0E-4) setPosition(at); // its shape changed: on its new top
            MagpieNestBlockEntity onNest = MagpieNestBlockEntity.at(world, perch);
            if (onNest != null) turn(onNest.perchYaw());
        }
        if (age % FLEE_CHECK_INTERVAL == 0) {
            PlayerEntity threat = closeThreat(world);
            if (threat != null && takeOff(world, threat.getPos())) return;
        }
        boolean onNest = MagpieNestBlockEntity.at(world, perch) != null;
        if (isNight(world)) {
            if (onNest) {
                if (getFlightState() != ASLEEP) setFlightState(ASLEEP);
                return;
            }
            if ((age + getId()) % NEST_CHECK_INTERVAL == 0 && goToBed(world)) return;
            if (getFlightState() != ASLEEP && random.nextInt(200) == 0) setFlightState(ASLEEP);
            return;
        }
        if (getFlightState() != PERCHED) setFlightState(PERCHED);
        if ((age + getId()) % SHINY_CHECK_INTERVAL == 0) {
            if (getCarried().isEmpty() ? fetchShiny(world) : bringHome(world)) return;
        }
        if (--restTicks <= 0) {
            if (!takeOff(world, null)) restTicks = 40 + random.nextInt(40);
            return;
        }
        if (--actionCooldown <= 0) {
            actionCooldown = 60 + random.nextInt(140);
            float roll = random.nextFloat();
            if (roll < 0.35f) {
                triggerAnim("action", "preen");
            } else if (roll < 0.8f) {
                turn(getYaw() + (random.nextBoolean() ? 1 : -1) * (30 + random.nextInt(70)));
                triggerAnim("action", "hop");
            } else {
                turn(getYaw() + (random.nextFloat() - 0.5f) * 80);
            }
        }
    }

    private @Nullable PlayerEntity closeThreat(ServerWorld world) {
        double range = getFlightState() == ASLEEP ? FLEE_RANGE / 2 : FLEE_RANGE;
        PlayerEntity player = world.getClosestPlayer(getX(), getY(), getZ(), range, EntityPredicates.EXCEPT_SPECTATOR);
        if (player == null) return null;
        if (player.isSneaking() && player.squaredDistanceTo(this) > FLEE_RANGE_SNEAKING * FLEE_RANGE_SNEAKING) return null;
        return player;
    }

    /** Not perched and not flying (just born, loaded mid-flight, its perch gone): onto the block under it, or off. */
    private void settle(ServerWorld world) {
        BlockPos.Mutable below = BlockPos.ofFloored(getX(), getY() - 1.0E-3, getZ()).mutableCopy();
        for (int i = 0; i < 2; i++, below.move(0, -1, 0)) {
            Vec3d at = MagpiePerches.freePerchOn(world, below);
            if (at != null && at.y <= getY() + 1.0E-3 && getY() - at.y < 1.0) {
                land(below.toImmutable(), at);
                return;
            }
        }
        if (age % 20 != 0) return;
        if (takeOff(world, null)) return;
        // nothing around: down to the ground under it
        int x = MathHelper.floor(getX()), z = MathHelper.floor(getZ());
        BlockPos ground = new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1, z);
        Vec3d at = ground.getY() < getY() ? MagpiePerches.freePerchOn(world, ground) : null;
        if (at != null) startFlight(new Flight(getPos(), getPos().lerp(at, 0.5).add(0, 0.5, 0), at, ground, ticksFor(getPos().distanceTo(at))));
    }

    private void land(BlockPos on, Vec3d at) {
        flight = null;
        perch = on;
        setPosition(at);
        setFlightState(PERCHED);
        BlockState state = getWorld().getBlockState(on);
        boolean liked = MagpiePerches.isFavourite(state) || state.isIn(BlockTags.LEAVES);
        restTicks = liked ? 160 + random.nextInt(340) : 50 + random.nextInt(90);
        actionCooldown = 20 + random.nextInt(60);
        if (getWorld() instanceof ServerWorld world && MagpieNestBlockEntity.at(world, on) instanceof MagpieNestBlockEntity home) {
            turn(home.perchYaw());
            restTicks = 20 + random.nextInt(60); // up at dawn, off soon after
            if (!home.isLinked()) {
                if (nest == null) nest = on;
                ItemStack carried = getCarried();
                if (!carried.isEmpty()) {
                    ItemStack left = home.insert(carried);
                    if (!left.isEmpty()) dropStack(left);
                    setCarried(ItemStack.EMPTY);
                    world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.NEUTRAL, 0.5F, 1.8F);
                }
            }
            if (isNight(world)) setFlightState(ASLEEP);
        }
    }

    // ---------------------------------------------------------------- nests and shiny things

    /** A free nest to sleep in, its own first: flies there. False if none (or no clear way). */
    public boolean goToBed(ServerWorld world) {
        BlockPos bed = null;
        if (nest != null && isFreeNest(world, nest, SLEEP_RADIUS)) bed = nest;
        else {
            double best = Double.MAX_VALUE;
            for (BlockPos pos : MagpieNestBlockEntity.loadedNests(world)) {
                double distance = pos.getSquaredDistance(getPos());
                if (distance < best && isFreeNest(world, pos, SLEEP_RADIUS)) {
                    best = distance;
                    bed = pos;
                }
            }
        }
        if (bed == null) return false;
        if (nest == null) nest = bed;
        return flyToNest(world, bed);
    }

    private boolean flyToNest(ServerWorld world, BlockPos to) {
        Vec3d at = MagpiePerches.freePerchOn(world, to);
        if (at == null) return false;
        Flight way = wayTo(world, new Candidate(to, at, 1));
        if (way == null) return false;
        startFlight(way);
        return true;
    }

    /** A loaded nest within {@code radius}, not a Common pot's, no other Pie on it or on its way to it. */
    private boolean isFreeNest(ServerWorld world, BlockPos pos, int radius) {
        if (pos.getSquaredDistance(getPos()) > (double) radius * radius || !world.isChunkLoaded(pos)) return false;
        MagpieNestBlockEntity entity = MagpieNestBlockEntity.at(world, pos);
        if (entity == null || entity.isLinked()) return false;
        if (!world.getEntitiesByType(ModEntities.MAGPIE, new Box(pos).expand(2), pie -> pos.equals(pie.getNest())).isEmpty()) return false;
        return world.getEntitiesByClass(WildMagpieEntity.class, new Box(pos).expand(radius + 8), other -> other != this && other.isAlive()
                && (pos.equals(other.perch) || other.flight != null && pos.equals(other.flight.target))).isEmpty();
    }

    /** Its nest, or the nearest one within {@link #NEST_RADIUS} that is no Common pot's, adopted (null: none). */
    private @Nullable BlockPos home(ServerWorld world) {
        if (nest != null) {
            if (!world.isChunkLoaded(nest)) return nest;
            MagpieNestBlockEntity entity = MagpieNestBlockEntity.at(world, nest);
            if (entity != null && !entity.isLinked()) return nest;
            nest = null;
        }
        double best = (double) NEST_RADIUS * NEST_RADIUS;
        for (BlockPos pos : MagpieNestBlockEntity.loadedNests(world)) {
            double distance = pos.getSquaredDistance(getPos());
            if (distance > best) continue;
            MagpieNestBlockEntity entity = MagpieNestBlockEntity.at(world, pos);
            if (entity != null && !entity.isLinked()) {
                best = distance;
                nest = pos;
            }
        }
        return nest;
    }

    /** A shiny thing lying on the ground nearby: flies to pick it up. False if none (or no clear way). */
    public boolean fetchShiny(ServerWorld world) {
        Box around = getBoundingBox().expand(SHINY_RANGE, 6, SHINY_RANGE);
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, around, WildMagpieEntity::isPickable)) {
            double distance = item.squaredDistanceTo(this);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = item;
            }
        }
        if (best == null) return false;
        Flight way = wayTo(world, new Candidate(best.getBlockPos(), best.getPos(), 1));
        if (way == null) return false;
        startFlight(new Flight(way.from(), way.control(), way.to(), way.target(), way.ticks(), best.getUuid()));
        return true;
    }

    /** A shiny thing lying on the ground: an item entity of the tag, landed, that may be picked up. */
    public static boolean isPickable(ItemEntity item) {
        return item.isAlive() && item.isOnGround() && !item.cannotPickup() && item.getStack().isIn(FrousseuxEntity.SHINY);
    }

    /** At the end of a flight to a shiny thing: one of it in its beak, then off to its nest. */
    private void pickUp(ServerWorld world, UUID id) {
        if (world.getEntity(id) instanceof ItemEntity item && isPickable(item) && item.squaredDistanceTo(getPos()) < 2.25) {
            ItemStack stack = item.getStack().copy();
            setCarried(stack.split(1));
            if (stack.isEmpty()) item.discard();
            else item.setStack(stack);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.6F, 1.6F);
        }
        flight = null;
        perch = null;
        if (!getCarried().isEmpty() && bringHome(world)) return;
        takeOff(world, null);
    }

    /** With a shiny thing in its beak: off to its nest. False if it has none (or no clear way). */
    boolean bringHome(ServerWorld world) {
        BlockPos home = home(world);
        if (home == null || home.equals(perch) || !world.isChunkLoaded(home)) return false;
        return flyToNest(world, home);
    }

    /** Without a nest, what it carries falls after {@link #CARRY_TICKS} (with one, after twice that: no way there). */
    private void tickCarrying(ServerWorld world) {
        if (++carryTicks < CARRY_TICKS) return;
        if (carryTicks < CARRY_TICKS * 2 && home(world) != null) return;
        dropStack(getCarried());
        setCarried(ItemStack.EMPTY);
    }

    private void turn(float yaw) {
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);
    }

    // ---------------------------------------------------------------- flights

    /** Off to another perch (away from {@code threat} if there is one): false if it found none worth the flight. */
    public boolean takeOff(ServerWorld world, @Nullable Vec3d threat) {
        Flight next = planFlight(world, threat);
        if (next == null) return false;
        startFlight(next);
        return true;
    }

    private void startFlight(Flight next) {
        flight = next;
        flightTick = 0;
        perch = null;
        setFlightState(FLYING);
        getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PARROT_FLY, SoundCategory.NEUTRAL, 0.5F, 1.4F);
    }

    private void tickFlight(ServerWorld world) {
        Flight current = flight;
        flightTick++;
        double p = Math.min(1, flightTick / (double) current.ticks);
        double s = p * p * (3 - 2 * p);
        Vec3d at = current.at(s);
        Vec3d delta = at.subtract(getPos());
        if (delta.horizontalLengthSquared() > 1.0E-5) {
            turn((float) (MathHelper.atan2(delta.z, delta.x) * (180 / Math.PI)) - 90);
        }
        setPosition(at);
        boolean gliding = p > 0.2 && p < 0.85 && delta.y < -0.06;
        setFlightState(gliding ? GLIDING : FLYING);
        if (!gliding && flightTick % 7 == 0) {
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PARROT_FLY, SoundCategory.NEUTRAL, 0.2F, 1.5F);
        }
        if (p >= 1 && current.item != null) {
            pickUp(world, current.item);
            return;
        }
        if (p >= 1) {
            Vec3d top = MagpiePerches.perchOn(world, current.target);
            if (top != null && top.squaredDistanceTo(current.to) < 1.0E-4 && MagpiePerches.hasRoom(world, current.target, top)) {
                land(current.target, top);
            } else {
                flight = null; // its perch went meanwhile: it looks again
                perch = null;
            }
            return;
        }
        if (current.item != null) {
            if (flightTick % PERCH_CHECK_INTERVAL == 0 && !(world.getEntity(current.item) instanceof ItemEntity item && isPickable(item))) {
                Flight other = planFlight(world, null);
                if (other != null) startFlight(other);
                else {
                    flight = null;
                    perch = null;
                }
            }
            return;
        }
        if (flightTick % PERCH_CHECK_INTERVAL == 0 && p < 0.8) {
            Vec3d top = MagpiePerches.perchOn(world, current.target);
            if (top == null || top.squaredDistanceTo(current.to) > 1.0E-4 || !MagpiePerches.hasRoom(world, current.target, top)) {
                Flight other = planFlight(world, null);
                if (other != null) startFlight(other);
            }
        }
    }

    /** A perch to fly to and the way there, or null. */
    @Nullable Flight planFlight(ServerWorld world, @Nullable Vec3d threat) {
        List<Candidate> candidates = new ArrayList<>();
        Random r = getRandom();
        Vec3d here = getPos();
        // the tops of the columns around: tree tops, posts, the ground
        for (int i = 0; i < 20; i++) {
            double angle = r.nextDouble() * Math.PI * 2, distance = 3 + r.nextDouble() * (SEARCH_RADIUS - 3);
            int x = MathHelper.floor(here.x + Math.cos(angle) * distance), z = MathHelper.floor(here.z + Math.sin(angle) * distance);
            if (!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z))) continue;
            int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
            if (Math.abs(top - here.y) > 14) continue;
            consider(world, new BlockPos(x, top, z), threat, candidates);
        }
        // its favourites a little lower: fences, walls, logs, chains under the leaves or a roof
        BlockPos.Mutable at = new BlockPos.Mutable();
        for (int i = 0; i < 12; i++) {
            int x = MathHelper.floor(here.x) + r.nextBetween(-10, 10), z = MathHelper.floor(here.z) + r.nextBetween(-10, 10);
            if (!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z))) continue;
            at.set(x, MathHelper.floor(here.y) + r.nextBetween(-4, 6), z);
            for (int d = 0; d < 8 && at.getY() > world.getBottomY(); d++, at.move(0, -1, 0)) {
                BlockState state = world.getBlockState(at);
                if (state.isAir()) continue;
                if (MagpiePerches.isFavourite(state)) consider(world, at.toImmutable(), threat, candidates);
                break;
            }
        }
        for (int tries = 0; tries < 4 && !candidates.isEmpty(); tries++) {
            Candidate pick = pick(candidates, r);
            candidates.remove(pick);
            Flight way = wayTo(world, pick);
            if (way != null) return way;
        }
        return null;
    }

    private record Candidate(BlockPos pos, Vec3d at, double weight) {
    }

    private void consider(ServerWorld world, BlockPos pos, @Nullable Vec3d threat, List<Candidate> into) {
        if (pos.equals(perch)) return;
        if (MagpieNestBlockEntity.at(world, pos) != null && !isFreeNest(world, pos, SEARCH_RADIUS + 4)) return; // a pot's, or taken
        Vec3d at = MagpiePerches.freePerchOn(world, pos);
        if (at == null || at.squaredDistanceTo(getPos()) < 4) return;
        BlockState state = world.getBlockState(pos);
        double weight = MagpiePerches.isFavourite(state) ? 8 : state.isIn(BlockTags.LEAVES) ? 3 : 1;
        if (threat != null) {
            if (at.squaredDistanceTo(threat) < (FLEE_RANGE + 3) * (FLEE_RANGE + 3)) return;
            Vec3d away = getPos().subtract(threat), go = at.subtract(getPos());
            if (away.x * go.x + away.z * go.z < 0) return;
        }
        into.add(new Candidate(pos, at, weight));
    }

    private static Candidate pick(List<Candidate> candidates, Random r) {
        double total = 0;
        for (Candidate c : candidates) total += c.weight;
        double roll = r.nextDouble() * total;
        for (Candidate c : candidates) {
            roll -= c.weight;
            if (roll <= 0) return c;
        }
        return candidates.getLast();
    }

    /** The curve to {@code to}: up over the way, higher if the first one hits something; null if all three do. */
    private @Nullable Flight wayTo(ServerWorld world, Candidate to) {
        Vec3d from = getPos();
        double horizontal = Math.sqrt(to.at.subtract(from).horizontalLengthSquared());
        for (double extra : new double[]{0, 2.5, 5}) {
            Vec3d mid = from.lerp(to.at, 0.5);
            Vec3d control = new Vec3d(mid.x, Math.max(from.y, to.at.y) + 1.0 + horizontal * 0.2 + extra, mid.z);
            Flight way = new Flight(from, control, to.at, to.pos, ticksFor(from.distanceTo(control) + control.distanceTo(to.at)));
            if (isClear(world, way)) return way;
        }
        return null;
    }

    private static int ticksFor(double length) {
        return MathHelper.clamp((int) Math.ceil(length * 0.85 / FLIGHT_SPEED), 10, 200);
    }

    /** Eight raycasts along the curve: nothing in the way but near its start and its end (the perches themselves). */
    private boolean isClear(ServerWorld world, Flight way) {
        Vec3d lift = new Vec3d(0, MagpiePerches.HEIGHT / 2, 0);
        Vec3d previous = way.from.add(lift);
        for (int i = 1; i <= 8; i++) {
            Vec3d next = way.at(i / 8.0).add(lift);
            BlockHitResult hit = world.raycast(new RaycastContext(previous, next, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.ANY, this));
            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3d p = hit.getPos();
                if (p.squaredDistanceTo(way.to) > 1.5 * 1.5 && p.squaredDistanceTo(way.from) > 1.2 * 1.2) return false;
            }
            previous = next;
        }
        return true;
    }

    // ---------------------------------------------------------------- a bird

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean hurt = super.damage(source, amount);
        if (hurt && isAlive() && getWorld() instanceof ServerWorld world && flight == null) {
            Entity attacker = source.getAttacker();
            takeOff(world, attacker != null ? attacker.getPos() : null);
        }
        return hurt;
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean isInsideWall() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushAway(Entity entity) {
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return getFlightState() == ASLEEP ? null : ModSounds.MAGPIE_CHIRP;
    }

    /** At least 15 s between two calls of the same bird. */
    @Override
    public int getMinAmbientSoundDelay() {
        return 300;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_PARROT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_PARROT_DEATH;
    }

    @Override
    public float getSoundPitch() {
        return 0.95F + random.nextFloat() * 0.15F;
    }

    // ---------------------------------------------------------------- GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> state.setAndContinue(switch (getFlightState()) {
            case FLYING -> FLY;
            case GLIDING -> GLIDE;
            case ASLEEP -> SLEEP;
            default -> IDLE;
        })));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
                .triggerableAnim("preen", PREEN)
                .triggerableAnim("hop", HOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
