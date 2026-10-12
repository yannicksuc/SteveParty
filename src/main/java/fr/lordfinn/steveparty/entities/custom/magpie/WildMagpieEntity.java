package fr.lordfinn.steveparty.entities.custom.magpie;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlockEntity;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
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
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

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
 * Every flight stays over land that ticks its entities ({@link #routeTicks}): it never flies into a chunk that is not
 * loaded (nor loads one), and turns back when the land ahead of it unloads in flight.
 * <p>
 * Now and then (one take-off in {@link #LONG_FLIGHT_ODDS}) an adult goes on a long flight ({@link #longFlight}):
 * climbing high, {@link #LONG_FLIGHT_MIN} to {@link #LONG_FLIGHT_MAX} blocks away, cut short where the loaded land
 * ends. One with a nest comes back towards it afterwards: any flight it takes more than {@link #HOME_RANGE} blocks
 * from its nest is a long one home (its nest is never forgotten for being far, only when it is gone or a pot's).
 * <p>
 * At night it sleeps in a Magpie Nest within {@link #SLEEP_RADIUS} blocks if there is a free one (its own first; one
 * Pie per nest, never a Common pot's nest), facing the nest's way (beside a pile of coins: on the free corner of the
 * rim), and leaves it at dawn. By day it picks up the shiny things lying on the ground ({@link FrousseuxEntity#SHINY},
 * item entities only, never from a player), one at a time in its beak, and brings them to its nest (the nearest
 * free one within {@link #NEST_RADIUS} blocks, adopted as its own); without a nest it drops it after a while. All by
 * checks every {@link #NEST_CHECK_INTERVAL} / {@link #SHINY_CHECK_INTERVAL} ticks, the nests known without scanning
 * blocks ({@link MagpieNestBlockEntity#loadedNests}).
 * <p>
 * An animal: any seed ({@link #FOOD}, the vanilla seeds and the {@code c:seeds} tag) puts it in love as wheat does a
 * cow (a player holding seeds does not scare it). Two in love within {@link #LOVE_RANGE} blocks meet (one flies to a
 * perch beside the other) and a chick is born, of the colour of one of them, the nest of one of them its own. A chick
 * ({@link #isBaby()}, its own fluffy model) lives in its nest: it sits there (several chicks in one nest, side by
 * side; a chick takes no adult's room), now and then hops out onto a block nearby and back, sleeps there at night.
 * Without a nest it walks on the ground like any animal (gravity, collisions, the vanilla move control towards a spot
 * a few blocks away, no pathfinding) and only rarely hops up with a flap. It grows up in the usual time (seeds make it
 * grow faster) into an adult keeping its nest.
 * <p>
 * Not the Common pot's Pie ({@link MagpieEntity}, bound to its nest, scripted, never an animal: it never breeds): a
 * separate kind sharing its model.
 */
public class WildMagpieEntity extends AnimalEntity implements GeoEntity {
    private static final TrackedData<Integer> VARIANT = DataTracker.registerData(WildMagpieEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Byte> STATE = DataTracker.registerData(WildMagpieEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<ItemStack> CARRIED = DataTracker.registerData(WildMagpieEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    public static final byte PERCHED = 0, FLYING = 1, GLIDING = 2, ASLEEP = 3, TAKING_OFF = 4, LANDING = 5;
    /** What puts it in love (and makes a chick grow): any seed. */
    public static final TagKey<Item> FOOD = TagKey.of(RegistryKeys.ITEM, Steveparty.id("magpie_food"));

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.magpie.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.magpie.walk");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.magpie.fly");
    private static final RawAnimation TAKEOFF = RawAnimation.begin().thenLoop("animation.magpie.takeoff");
    private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("animation.magpie.glide");
    private static final RawAnimation LAND = RawAnimation.begin().thenLoop("animation.magpie.land");
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

    /** One take-off in this many is a long flight. */
    public static final int LONG_FLIGHT_ODDS = 20;
    /** How far a long flight goes (blocks, horizontally), before the loaded land cuts it short. */
    public static final int LONG_FLIGHT_MIN = 32, LONG_FLIGHT_MAX = 64;
    /** A long flight cut short this much (or more) is not worth it: an ordinary flight instead. */
    public static final int LONG_FLIGHT_SHORTEST = SEARCH_RADIUS + 8;
    /** How high above its start and its end a long flight climbs (blocks, more the longer it is). */
    private static final double LONG_FLIGHT_CLIMB = 10;
    /** Farther than this from its nest, it flies home (a long flight towards it). */
    public static final int HOME_RANGE = SEARCH_RADIUS * 2;
    /** The route of a flight is checked every this many blocks, and stays this far from land that does not tick. */
    private static final int ROUTE_STEP = 8, EDGE_MARGIN = 8;

    /** Two in love this close meet; this close they are together, and after {@link #COURTSHIP_TICKS} a chick is born. */
    public static final double LOVE_RANGE = 8, MATE_RANGE = 3;
    private static final int LOVE_CHECK_INTERVAL = 10, COURTSHIP_TICKS = 60;
    /** A chick hops out of its nest onto a block this close to it (blocks), once in this many rests. */
    public static final int CHICK_OUTING_RADIUS = 3, CHICK_OUTING_ODDS = 3;
    /** A chick without a nest adopts a free one this close; it hops home by hops this long at most. */
    public static final int CHICK_NEST_RADIUS = 8, CHICK_HOP = 5;
    /** A chick on the ground walks to a spot this close; one rest in this many it hops up with a flap instead. */
    private static final int WANDER_RADIUS = 5, WANDER_HOP_ODDS = 8;
    /** How far apart chicks sit in their nest (blocks from its middle). */
    private static final double CHICK_SPREAD = 0.16;

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
    /** In love with another one this close: ticks they have been together. */
    private int courtship;
    /** A chick without a nest: on foot, with gravity and collisions (else moved by hand). */
    private boolean walking;
    /** On foot: where it walks to (null: it stands), and for how long more. */
    private @Nullable Vec3d walkTarget;
    private int walkTicks;
    private double walkSpeed = 1.0;

    /** How high a flight curves up: an ordinary flight, a chick's low hop, a long flight. */
    enum Climb {
        HOP(1.0, 0.2, 1.0), LOW(0.4, 0.08, 1.5), LONG(LONG_FLIGHT_CLIMB, 0.12, 0.9);

        final double base, perBlock, slowness;

        Climb(double base, double perBlock, double slowness) {
            this.base = base;
            this.perBlock = perBlock;
            this.slowness = slowness;
        }
    }

    /** A flight: its curve, how long, the perch at its end (or the shiny thing it goes to pick up), a long one or not. */
    record Flight(Vec3d from, Vec3d control, Vec3d to, BlockPos target, int ticks, @Nullable UUID item, boolean far) {
        Flight(Vec3d from, Vec3d control, Vec3d to, BlockPos target, int ticks, boolean far) {
            this(from, control, to, target, ticks, null, far);
        }

        Vec3d at(double s) {
            double u = 1 - s;
            return from.multiply(u * u).add(control.multiply(2 * u * s)).add(to.multiply(s * s));
        }
    }

    public WildMagpieEntity(EntityType<? extends AnimalEntity> type, World world) {
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

    /** In a long flight ({@link #longFlight}). */
    public boolean isInLongFlight() {
        return flight != null && flight.far;
    }

    /** Where its flight ends (null: not flying). */
    public @Nullable Vec3d getFlightEnd() {
        return flight != null ? flight.to : null;
    }

    /** A chick on foot (no nest): gravity and collisions, walking. */
    public boolean isWalking() {
        return walking;
    }

    /** The shiny thing in its beak (empty: nothing). */
    public ItemStack getCarried() {
        return dataTracker.get(CARRIED);
    }

    /** Puts {@code stack} in its beak (tests, and what it picks up). */
    public void setCarried(ItemStack stack) {
        dataTracker.set(CARRIED, stack);
        carryTicks = 0;
    }

    /** Its nest (null: none adopted). */
    public @Nullable BlockPos getNest() {
        return nest;
    }

    /** Its nest from now on (born with a nest in the trees, MagpieNestFeature, or of parents with one). */
    public void adoptNest(@Nullable BlockPos pos) {
        nest = pos != null ? pos.toImmutable() : null;
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

    /** Born where its spawn rule says (WildMagpieSpawns), not where an animal's light rule would. */
    @Override
    public float getPathfindingFavor(BlockPos pos, WorldView world) {
        return 0;
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

    /** What it carries falls when it dies (its feathers: its loot table, more with Looting). */
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
     * a nest of its own or something in its beak, and a chick.
     */
    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return distanceSquared > 64 * 64 && getCarried().isEmpty() && nest == null && !isBaby();
    }

    // ---------------------------------------------------------------- an animal

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return stack.isIn(FOOD);
    }

    /** A chick of the colour of one of its parents, their nest its own (this one's first). */
    @Override
    public @Nullable PassiveEntity createChild(ServerWorld world, PassiveEntity other) {
        WildMagpieEntity chick = ModEntities.WILD_MAGPIE.create(world);
        if (chick == null) return null;
        WildMagpieEntity mate = other instanceof WildMagpieEntity magpie ? magpie : this;
        chick.setVariant(random.nextBoolean() ? getVariant() : mate.getVariant());
        chick.adoptNest(nest != null ? nest : mate.nest);
        chick.restTicks = 10;
        return chick;
    }

    /** Grown up: back to flying (if it walked), its nest kept. */
    @Override
    protected void onGrowUp() {
        super.onGrowUp();
        restTicks = 20 + random.nextInt(60);
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        if (!getWorld().isClient) {
            boolean onFoot = isBaby() && nest == null;
            if (onFoot != walking) setWalking(onFoot);
        }
        if (walking) {
            super.tick();
            if (getWorld() instanceof ServerWorld world && !isAiDisabled() && !isDead()) tickWalking(world);
            return;
        }
        setVelocity(Vec3d.ZERO);
        super.tick();
        setVelocity(Vec3d.ZERO);
        if (!(getWorld() instanceof ServerWorld world) || isAiDisabled() || isDead()) return;
        if (!getCarried().isEmpty()) tickCarrying(world);
        if (flight != null) tickFlight(world);
        else if (perch == null) settle(world);
        else if (isBaby()) tickChick(world);
        else tickPerched(world);
    }

    /** On foot (a chick without a nest: gravity, collisions) or moved by hand (settling on what is under it). */
    private void setWalking(boolean onFoot) {
        walking = onFoot;
        noClip = !onFoot;
        setNoGravity(!onFoot);
        flight = null;
        perch = null;
        walkTarget = null;
        setVelocity(Vec3d.ZERO);
        setFlightState(PERCHED);
    }

    /** Perched: it stays, does a little something now and then, flies off when rested, scared or its perch is gone. */
    private void tickPerched(ServerWorld world) {
        if (age % PERCH_CHECK_INTERVAL == 0 && !checkPerch(world)) return;
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
        if (isInLove() && (age + getId()) % LOVE_CHECK_INTERVAL == 0 && court(world)) return;
        if (isInLove() && courtship > 0) return; // stays by its mate
        if ((age + getId()) % SHINY_CHECK_INTERVAL == 0) {
            if (getCarried().isEmpty() ? fetchShiny(world) : bringHome(world)) return;
        }
        if (--restTicks <= 0) {
            boolean off = getCarried().isEmpty() && random.nextInt(LONG_FLIGHT_ODDS) == 0 && longFlight(world);
            if (!off && !takeOff(world, null)) restTicks = 40 + random.nextInt(40);
            return;
        }
        fidget();
    }

    /** Its perch still there (on its top: its shape may have changed): false if it is gone (it is off then). */
    private boolean checkPerch(ServerWorld world) {
        Vec3d at = spotOn(world, perch);
        if (at == null || !MagpiePerches.hasRoom(world, perch, at)) {
            perch = null;
            if (isBaby() || !takeOff(world, null)) settle(world);
            return false;
        }
        if (at.squaredDistanceTo(getPos()) > 1.0E-4) setPosition(at);
        MagpieNestBlockEntity onNest = MagpieNestBlockEntity.at(world, perch);
        if (onNest != null) turn(onNest.perchYaw());
        return true;
    }

    /** A little something now and then on its perch: preening, a hop round, a look aside. */
    private void fidget() {
        if (--actionCooldown > 0) return;
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

    private @Nullable PlayerEntity closeThreat(ServerWorld world) {
        double range = getFlightState() == ASLEEP ? FLEE_RANGE / 2 : FLEE_RANGE;
        PlayerEntity player = world.getClosestPlayer(getX(), getY(), getZ(), range, EntityPredicates.EXCEPT_SPECTATOR);
        if (player == null) return null;
        if (player.isSneaking() && player.squaredDistanceTo(this) > FLEE_RANGE_SNEAKING * FLEE_RANGE_SNEAKING) return null;
        if (isBreedingItem(player.getMainHandStack()) || isBreedingItem(player.getOffHandStack())) return null; // seeds!
        return player;
    }

    /** Not perched and not flying (just born, loaded mid-flight, its perch gone): onto the block under it, or off. */
    private void settle(ServerWorld world) {
        BlockPos.Mutable below = BlockPos.ofFloored(getX(), getY() - 1.0E-3, getZ()).mutableCopy();
        for (int i = 0; i < 2; i++, below.move(0, -1, 0)) {
            Vec3d at = freeSpotOn(world, below);
            if (at != null && at.y <= getY() + 1.0E-3 && getY() - at.y < 1.0) {
                land(below.toImmutable(), at);
                return;
            }
        }
        if (age % 20 != 0) return;
        if (isBaby() ? hopHome(world) : takeOff(world, null)) return;
        // nothing around: down to the ground under it
        int x = MathHelper.floor(getX()), z = MathHelper.floor(getZ());
        BlockPos ground = new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1, z);
        Vec3d at = ground.getY() < getY() ? freeSpotOn(world, ground) : null;
        if (at != null) startFlight(new Flight(getPos(), getPos().lerp(at, 0.5).add(0, 0.5, 0), at, ground, ticksFor(getPos().distanceTo(at), Climb.HOP), false));
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
            if (isBaby() && on.equals(nest)) restTicks = 300 + random.nextInt(600); // a chick stays in its nest
            if (isNight(world)) setFlightState(ASLEEP);
        } else if (isBaby()) {
            restTicks = 100 + random.nextInt(200); // a little outing, then home
        }
    }

    /**
     * Where it stands on {@code pos} ({@link MagpiePerches#perchOn}); a chick in a nest a little aside of its middle
     * (by its id: chicks side by side, and beside an adult), or null.
     */
    private @Nullable Vec3d spotOn(ServerWorld world, BlockPos pos) {
        Vec3d at = MagpiePerches.perchOn(world, pos);
        if (at == null || !isBaby()) return at;
        MagpieNestBlockEntity home = MagpieNestBlockEntity.at(world, pos);
        if (home == null) return at;
        double angle = getId() * 2.39996, spread = home.getPileCount() > 0 ? CHICK_SPREAD / 2 : CHICK_SPREAD;
        return at.add(Math.cos(angle) * spread, 0, Math.sin(angle) * spread);
    }

    private @Nullable Vec3d freeSpotOn(ServerWorld world, BlockPos pos) {
        Vec3d at = spotOn(world, pos);
        return at != null && MagpiePerches.hasRoom(world, pos, at) ? at : null;
    }

    // ---------------------------------------------------------------- love

    /**
     * In love: the nearest other one in love within {@link #LOVE_RANGE}. Apart, one of the two (the older entity) flies
     * to a perch beside the other; together for {@link #COURTSHIP_TICKS} ticks (or long enough with no way to get
     * closer), a chick. False if there is no one.
     */
    private boolean court(ServerWorld world) {
        WildMagpieEntity mate = null;
        double best = LOVE_RANGE * LOVE_RANGE;
        for (WildMagpieEntity other : world.getEntitiesByClass(WildMagpieEntity.class, getBoundingBox().expand(LOVE_RANGE),
                other -> other != this && canBreedWith(other))) {
            double distance = other.squaredDistanceTo(this);
            if (distance < best) {
                best = distance;
                mate = other;
            }
        }
        if (mate == null) {
            courtship = 0;
            return false;
        }
        Vec3d toMate = mate.getPos().subtract(getPos());
        turn((float) (MathHelper.atan2(toMate.z, toMate.x) * (180 / Math.PI)) - 90);
        boolean together = best <= MATE_RANGE * MATE_RANGE;
        if (!together && getId() < mate.getId() && mate.flight == null && flyBeside(world, mate)) {
            courtship = 0;
            return true;
        }
        courtship += LOVE_CHECK_INTERVAL;
        if (courtship >= (together ? COURTSHIP_TICKS : COURTSHIP_TICKS * 3)) {
            courtship = 0;
            mate.courtship = 0;
            breed(world, mate);
            world.playSound(null, getX(), getY(), getZ(), ModSounds.MAGPIE_CHIRP, SoundCategory.NEUTRAL, 0.8F, 1.3F);
        }
        return true;
    }

    /** Off to a free perch beside {@code mate} (within {@link #MATE_RANGE} of it): false if none (or no way). */
    private boolean flyBeside(ServerWorld world, WildMagpieEntity mate) {
        BlockPos around = mate.perch != null ? mate.perch : mate.getBlockPos().down();
        List<Candidate> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(around.add(-2, -1, -2), around.add(2, 1, 2))) {
            if (pos.equals(around) || pos.equals(perch)) continue;
            Vec3d at = freeSpotOn(world, pos);
            if (at != null && at.squaredDistanceTo(mate.getPos()) <= MATE_RANGE * MATE_RANGE && at.squaredDistanceTo(mate.getPos()) > 0.3) {
                candidates.add(new Candidate(pos.toImmutable(), at, 1));
            }
        }
        candidates.sort((a, b) -> Double.compare(a.at.squaredDistanceTo(mate.getPos()), b.at.squaredDistanceTo(mate.getPos())));
        for (int i = 0; i < Math.min(4, candidates.size()); i++) {
            Flight way = wayTo(world, candidates.get(i), Climb.HOP);
            if (way != null) {
                startFlight(way);
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- chicks

    /**
     * A chick with a nest: in it it sits (asleep at night), now and then hops out onto a block nearby
     * ({@link #CHICK_OUTING_RADIUS}); out of it, it hops back after a while. Never scared off: it hunkers down.
     */
    private void tickChick(ServerWorld world) {
        if (age % PERCH_CHECK_INTERVAL == 0 && !checkPerch(world)) return;
        if ((age + getId()) % NEST_CHECK_INTERVAL == 0 && chickHome(world) == null) return; // its nest gone: on foot
        boolean inNest = perch.equals(nest);
        if (isNight(world)) {
            if (inNest) {
                if (getFlightState() != ASLEEP) setFlightState(ASLEEP);
            } else if ((age + getId()) % NEST_CHECK_INTERVAL == 0) {
                hopHome(world);
            }
            return;
        }
        if (getFlightState() != PERCHED) setFlightState(PERCHED);
        if (--restTicks <= 0) {
            if (!inNest) {
                if (!hopHome(world)) restTicks = 40 + random.nextInt(40);
            } else if (random.nextInt(CHICK_OUTING_ODDS) != 0 || !hopOut(world)) {
                restTicks = 300 + random.nextInt(600);
            }
            return;
        }
        fidget();
    }

    /** A chick's nest: still a nest of the woods (not a pot's)? Else none (null). Kept while it is not loaded. */
    private @Nullable BlockPos chickHome(ServerWorld world) {
        if (nest == null || !world.isChunkLoaded(nest)) return nest;
        MagpieNestBlockEntity entity = MagpieNestBlockEntity.at(world, nest);
        if (entity == null || entity.isLinked()) nest = null;
        return nest;
    }

    /** A chick out of its nest: back to it, by short low hops ({@link #CHICK_HOP} blocks at most). */
    public boolean hopHome(ServerWorld world) {
        BlockPos home = chickHome(world);
        if (home == null || home.equals(perch) || !world.shouldTickEntity(home)) return false;
        Vec3d at = freeSpotOn(world, home);
        if (at != null && at.squaredDistanceTo(getPos()) <= CHICK_HOP * CHICK_HOP) {
            Flight way = wayTo(world, new Candidate(home, at, 1), Climb.LOW);
            if (way != null) {
                startFlight(way);
                return true;
            }
        }
        // farther (or no way in yet): a hop towards it
        Vec3d toward = Vec3d.ofBottomCenter(home).subtract(getPos()).multiply(1, 0, 1);
        double length = toward.length();
        if (length < 1) return false;
        Vec3d step = getPos().add(toward.multiply(Math.min(CHICK_HOP - 1, length - 1) / length));
        return hopAround(world, step, 2, home);
    }

    /** A chick in its nest: out onto a block nearby for a while. */
    public boolean hopOut(ServerWorld world) {
        return nest != null && hopAround(world, Vec3d.ofBottomCenter(nest), CHICK_OUTING_RADIUS, nest);
    }

    /** A low hop to a free perch near {@code centre} (a few samples): false if none. */
    private boolean hopAround(ServerWorld world, Vec3d centre, int radius, BlockPos home) {
        List<Candidate> candidates = new ArrayList<>();
        BlockPos.Mutable at = new BlockPos.Mutable();
        for (int i = 0; i < 12; i++) {
            int x = MathHelper.floor(centre.x) + random.nextBetween(-radius, radius), z = MathHelper.floor(centre.z) + random.nextBetween(-radius, radius);
            at.set(x, MathHelper.floor(centre.y) + 2, z);
            if (!world.shouldTickEntity(at)) continue;
            for (int d = 0; d < 6 && at.getY() > world.getBottomY(); d++, at.move(0, -1, 0)) {
                if (world.getBlockState(at).isAir()) continue;
                if (!at.equals(perch) && !at.equals(home)) {
                    Vec3d top = freeSpotOn(world, at);
                    if (top != null && top.squaredDistanceTo(getPos()) <= CHICK_HOP * CHICK_HOP && top.squaredDistanceTo(getPos()) > 0.5) {
                        candidates.add(new Candidate(at.toImmutable(), top, 1));
                    }
                }
                break;
            }
        }
        for (int tries = 0; tries < 3 && !candidates.isEmpty(); tries++) {
            Candidate pick = candidates.remove(random.nextInt(candidates.size()));
            Flight way = wayTo(world, pick, Climb.LOW);
            if (way != null) {
                startFlight(way);
                return true;
            }
        }
        return false;
    }

    /**
     * A chick without a nest, on foot: it adopts a free nest close by, walks to a spot a few blocks away now and then
     * (the vanilla move control, stepping up a block on its own), rarely hops up with a flap, walks away from players
     * coming close, sleeps at night.
     */
    private void tickWalking(ServerWorld world) {
        if ((age + getId()) % NEST_CHECK_INTERVAL == 0 && adoptNestNearby(world)) return;
        boolean airborne = !isOnGround();
        if (isNight(world) && !airborne) {
            walkTarget = null;
            if (getFlightState() != ASLEEP) setFlightState(ASLEEP);
            return;
        }
        setFlightState(airborne ? FLYING : PERCHED);
        if (age % FLEE_CHECK_INTERVAL == 0 && !airborne) {
            PlayerEntity threat = closeThreat(world);
            if (threat != null) {
                Vec3d away = getPos().subtract(threat.getPos()).multiply(1, 0, 1);
                if (away.lengthSquared() > 1.0E-4) walkTo(getPos().add(away.normalize().multiply(WANDER_RADIUS)), 1.6);
            }
        }
        if (walkTarget != null) {
            if (--walkTicks <= 0 || walkTarget.subtract(getPos()).horizontalLengthSquared() < 0.25) walkTarget = null;
            else getMoveControl().moveTo(walkTarget.x, walkTarget.y, walkTarget.z, walkSpeed);
            return;
        }
        if (airborne || --restTicks > 0) return;
        restTicks = 60 + random.nextInt(160);
        double angle = random.nextDouble() * Math.PI * 2;
        if (random.nextInt(WANDER_HOP_ODDS) == 0) {
            // a flap and a little jump forward
            turn((float) (angle * (180 / Math.PI)) - 90);
            setVelocity(Math.cos(angle) * 0.22, 0.42, Math.sin(angle) * 0.22);
            velocityDirty = true;
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PARROT_FLY, SoundCategory.NEUTRAL, 0.3F, 1.8F);
            return;
        }
        double distance = 1.5 + random.nextDouble() * (WANDER_RADIUS - 1.5);
        walkTo(getPos().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance), 1.0);
    }

    /** On foot: off to {@code target} (if the land there ticks), at {@code speed} times its walking speed. */
    private void walkTo(Vec3d target, double speed) {
        if (getWorld() instanceof ServerWorld world && !world.shouldTickEntity(BlockPos.ofFloored(target))) return;
        walkTarget = target;
        walkSpeed = speed;
        walkTicks = 100;
    }

    /** A chick on foot: a free nest of the woods within {@link #CHICK_NEST_RADIUS} blocks, its own from now on. */
    private boolean adoptNestNearby(ServerWorld world) {
        double best = (double) CHICK_NEST_RADIUS * CHICK_NEST_RADIUS;
        BlockPos found = null;
        for (BlockPos pos : MagpieNestBlockEntity.loadedNests(world)) {
            double distance = pos.getSquaredDistance(getPos());
            if (distance > best) continue;
            MagpieNestBlockEntity entity = MagpieNestBlockEntity.at(world, pos);
            if (entity != null && !entity.isLinked()) {
                best = distance;
                found = pos;
            }
        }
        if (found == null) return false;
        nest = found.toImmutable();
        return true;
    }

    // ---------------------------------------------------------------- nests and shiny things

    /** A free nest to sleep in, its own first: flies there (its own far: home first). False if none (or no clear way). */
    public boolean goToBed(ServerWorld world) {
        if (nest != null && isFarFromHome() && world.shouldTickEntity(nest)) {
            Flight back = homeward(world);
            if (back != null) {
                startFlight(back);
                return true;
            }
        }
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
        Vec3d at = freeSpotOn(world, to);
        if (at == null) return false;
        Flight way = wayTo(world, new Candidate(to, at, 1), Climb.HOP);
        if (way == null) return false;
        startFlight(way);
        return true;
    }

    /**
     * A loaded nest within {@code radius}, not a Common pot's, no other adult Pie on it or on its way to it (chicks take
     * no room: they huddle beside).
     */
    private boolean isFreeNest(ServerWorld world, BlockPos pos, int radius) {
        if (pos.getSquaredDistance(getPos()) > (double) radius * radius || !world.isChunkLoaded(pos)) return false;
        MagpieNestBlockEntity entity = MagpieNestBlockEntity.at(world, pos);
        if (entity == null || entity.isLinked()) return false;
        if (!world.getEntitiesByType(ModEntities.MAGPIE, new Box(pos).expand(2), pie -> pos.equals(pie.getNest())).isEmpty()) return false;
        return world.getEntitiesByClass(WildMagpieEntity.class, new Box(pos).expand(radius + 8), other -> other != this && other.isAlive()
                && !other.isBaby() && (pos.equals(other.perch) || other.flight != null && pos.equals(other.flight.target))).isEmpty();
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
        Flight way = wayTo(world, new Candidate(best.getBlockPos(), best.getPos(), 1), Climb.HOP);
        if (way == null) return false;
        startFlight(new Flight(way.from(), way.control(), way.to(), way.target(), way.ticks(), best.getUuid(), false));
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

    /** With a shiny thing in its beak: off to its nest (far: a long flight home). False if none (or no clear way). */
    boolean bringHome(ServerWorld world) {
        BlockPos home = home(world);
        if (home == null || home.equals(perch) || !world.shouldTickEntity(home)) return false;
        if (isFarFromHome()) {
            Flight back = homeward(world);
            if (back == null) return false;
            startFlight(back);
            return true;
        }
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

    /**
     * A long flight: climbing high, {@link #LONG_FLIGHT_MIN} to {@link #LONG_FLIGHT_MAX} blocks away (home if it is far
     * from its nest). False if the loaded land is too small for it, or no perch there.
     */
    public boolean longFlight(ServerWorld world) {
        if (isBaby()) return false;
        Flight next = isFarFromHome() ? homeward(world)
                : longFlightToward(world, random.nextDouble() * Math.PI * 2,
                LONG_FLIGHT_MIN + random.nextDouble() * (LONG_FLIGHT_MAX - LONG_FLIGHT_MIN));
        if (next == null) return false;
        startFlight(next);
        return true;
    }

    /** A long flight towards {@code angle} (radians, from +x towards +z), {@code distance} blocks or less: tests. */
    public boolean longFlight(ServerWorld world, double angle, double distance) {
        Flight next = longFlightToward(world, angle, distance);
        if (next == null) return false;
        startFlight(next);
        return true;
    }

    private void startFlight(Flight next) {
        flight = next;
        flightTick = 0;
        perch = null;
        courtship = 0;
        setFlightState(TAKING_OFF);
        getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PARROT_FLY, SoundCategory.NEUTRAL, isBaby() ? 0.3F : 0.5F, isBaby() ? 1.8F : 1.4F);
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
        // the phase is synced (STATE) for the client's wings: fast flaps off the perch, flaps while it climbs or
        // flies level, wings held open when it comes down, flaps and wings forward to brake onto the perch
        boolean gliding = p > 0.2 && p < 0.85 && delta.y < -0.06;
        setFlightState(p < 0.2 ? TAKING_OFF : p >= 0.85 ? LANDING : gliding ? GLIDING : FLYING);
        if (!gliding && flightTick % 7 == 0) {
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PARROT_FLY, SoundCategory.NEUTRAL, 0.2F, 1.5F);
        }
        if (p >= 1 && current.item != null) {
            pickUp(world, current.item);
            return;
        }
        if (p >= 1) {
            Vec3d top = spotOn(world, current.target);
            if (top != null && top.squaredDistanceTo(current.to) < 1.0E-4 && MagpiePerches.hasRoom(world, current.target, top)) {
                land(current.target, top);
            } else {
                flight = null; // its perch went meanwhile: it looks again
                perch = null;
            }
            return;
        }
        if (flightTick % PERCH_CHECK_INTERVAL == 0 && !world.shouldTickEntity(BlockPos.ofFloored(current.at(Math.min(1, s + 0.2))))) {
            // the land ahead unloaded: back to a perch around here
            Flight other = isBaby() ? null : planFlight(world, null);
            if (other != null && !other.far) startFlight(other);
            else {
                flight = null;
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
            Vec3d top = spotOn(world, current.target);
            if (top == null || top.squaredDistanceTo(current.to) > 1.0E-4 || !MagpiePerches.hasRoom(world, current.target, top)) {
                Flight other = isBaby() ? null : planFlight(world, null);
                if (other != null) startFlight(other);
                else if (isBaby()) {
                    flight = null;
                    perch = null;
                }
            }
        }
    }

    /** A perch to fly to and the way there (far from its nest: home), or null. */
    @Nullable Flight planFlight(ServerWorld world, @Nullable Vec3d threat) {
        if (threat == null && isFarFromHome()) {
            Flight back = homeward(world);
            if (back != null) return back;
        }
        List<Candidate> candidates = new ArrayList<>();
        Random r = getRandom();
        Vec3d here = getPos();
        // the tops of the columns around: tree tops, posts, the ground
        for (int i = 0; i < 20; i++) {
            double angle = r.nextDouble() * Math.PI * 2, distance = 3 + r.nextDouble() * (SEARCH_RADIUS - 3);
            int x = MathHelper.floor(here.x + Math.cos(angle) * distance), z = MathHelper.floor(here.z + Math.sin(angle) * distance);
            if (!world.shouldTickEntity(new BlockPos(x, MathHelper.floor(here.y), z))) continue;
            int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
            if (Math.abs(top - here.y) > 14) continue;
            consider(world, new BlockPos(x, top, z), threat, candidates);
        }
        // its favourites a little lower: fences, walls, logs, chains under the leaves or a roof
        BlockPos.Mutable at = new BlockPos.Mutable();
        for (int i = 0; i < 12; i++) {
            int x = MathHelper.floor(here.x) + r.nextBetween(-10, 10), z = MathHelper.floor(here.z) + r.nextBetween(-10, 10);
            at.set(x, MathHelper.floor(here.y) + r.nextBetween(-4, 6), z);
            if (!world.shouldTickEntity(at)) continue;
            for (int d = 0; d < 8 && at.getY() > world.getBottomY(); d++, at.move(0, -1, 0)) {
                BlockState state = world.getBlockState(at);
                if (state.isAir()) continue;
                if (MagpiePerches.isFavourite(state)) consider(world, at.toImmutable(), threat, candidates);
                break;
            }
        }
        return firstWay(world, candidates, Climb.HOP);
    }

    /** Farther than {@link #HOME_RANGE} from its nest (an adult). */
    private boolean isFarFromHome() {
        return !isBaby() && nest != null && nest.getSquaredDistance(getPos()) > (double) HOME_RANGE * HOME_RANGE;
    }

    /** A long flight towards its nest, ending near it (null: none, or no way). */
    private @Nullable Flight homeward(ServerWorld world) {
        if (nest == null) return null;
        Vec3d to = Vec3d.ofBottomCenter(nest).subtract(getPos());
        double distance = Math.sqrt(to.horizontalLengthSquared());
        return longFlightToward(world, MathHelper.atan2(to.z, to.x), Math.min(LONG_FLIGHT_MAX, distance - 6));
    }

    /**
     * A long flight towards {@code angle}: as far as {@code distance} blocks, or as far as the land that ticks goes
     * ({@link #loadedReach}), at least {@link #LONG_FLIGHT_SHORTEST}; to a perch around there (a few column tops), high
     * up on the way. Null if none.
     */
    private @Nullable Flight longFlightToward(ServerWorld world, double angle, double distance) {
        Vec3d here = getPos();
        double reach = loadedReach(here, angle, distance, world::shouldTickEntity);
        if (reach < LONG_FLIGHT_SHORTEST) return null;
        Vec3d far = here.add(Math.cos(angle) * reach, 0, Math.sin(angle) * reach);
        List<Candidate> candidates = new ArrayList<>();
        Random r = getRandom();
        for (int i = 0; i < 16; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * 6;
            int x = MathHelper.floor(far.x + Math.cos(a) * d), z = MathHelper.floor(far.z + Math.sin(a) * d);
            if (!world.shouldTickEntity(new BlockPos(x, MathHelper.floor(here.y), z))) continue;
            int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
            if (Math.abs(top - here.y) > 40) continue;
            consider(world, new BlockPos(x, top, z), null, candidates);
        }
        return firstWay(world, candidates, Climb.LONG);
    }

    /**
     * How far, up to {@code distance}, the land ticks its entities from {@code from} towards {@code angle} (checked every
     * {@link #ROUTE_STEP} blocks, the end and a margin beyond it too), a margin of {@link #EDGE_MARGIN} blocks kept from
     * where it stops: a long flight stops short of the loaded land's edge.
     */
    public static double loadedReach(Vec3d from, double angle, double distance, Predicate<BlockPos> ticking) {
        double dx = Math.cos(angle), dz = Math.sin(angle);
        for (double d = ROUTE_STEP; d < distance + EDGE_MARGIN + ROUTE_STEP; d += ROUTE_STEP) {
            double at = Math.min(d, distance + EDGE_MARGIN);
            if (!ticking.test(BlockPos.ofFloored(from.x + dx * at, from.y, from.z + dz * at))) {
                return Math.max(0, Math.min(distance, at - ROUTE_STEP - EDGE_MARGIN));
            }
        }
        return distance;
    }

    /** Every {@link #ROUTE_STEP} blocks under the way from {@code from} to {@code to}, land that ticks. */
    static boolean routeTicks(Vec3d from, Vec3d to, Predicate<BlockPos> ticking) {
        double length = Math.sqrt(to.subtract(from).horizontalLengthSquared());
        int steps = Math.max(1, (int) Math.ceil(length / ROUTE_STEP));
        for (int i = 0; i <= steps; i++) {
            if (!ticking.test(BlockPos.ofFloored(from.lerp(to, i / (double) steps)))) return false;
        }
        return true;
    }

    private @Nullable Flight firstWay(ServerWorld world, List<Candidate> candidates, Climb climb) {
        Random r = getRandom();
        for (int tries = 0; tries < 4 && !candidates.isEmpty(); tries++) {
            Candidate pick = pick(candidates, r);
            candidates.remove(pick);
            Flight way = wayTo(world, pick, climb);
            if (way != null) return way;
        }
        return null;
    }

    private record Candidate(BlockPos pos, Vec3d at, double weight) {
    }

    private void consider(ServerWorld world, BlockPos pos, @Nullable Vec3d threat, List<Candidate> into) {
        if (pos.equals(perch)) return;
        if (MagpieNestBlockEntity.at(world, pos) != null && !isFreeNest(world, pos, LONG_FLIGHT_MAX + 8)) return; // a pot's, or taken
        Vec3d at = freeSpotOn(world, pos);
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

    /**
     * The curve to {@code to}: up over the way ({@code climb}: a hop, a chick's low hop, a long flight high up), higher
     * if the first one hits something; null if all three do, or if the way leaves the land that ticks.
     */
    private @Nullable Flight wayTo(ServerWorld world, Candidate to, Climb climb) {
        Vec3d from = getPos();
        if (!routeTicks(from, to.at, world::shouldTickEntity)) return null;
        double horizontal = Math.sqrt(to.at.subtract(from).horizontalLengthSquared());
        double ceiling = world.getTopY() - 2;
        for (double extra : new double[]{0, 2.5, 5}) {
            Vec3d mid = from.lerp(to.at, 0.5);
            double y = Math.min(ceiling, Math.max(from.y, to.at.y) + climb.base + horizontal * climb.perBlock + extra);
            Vec3d control = new Vec3d(mid.x, y, mid.z);
            Flight way = new Flight(from, control, to.at, to.pos, ticksFor(from.distanceTo(control) + control.distanceTo(to.at), climb),
                    climb == Climb.LONG);
            if (isClear(world, way)) return way;
        }
        return null;
    }

    private static int ticksFor(double length, Climb climb) {
        return MathHelper.clamp((int) Math.ceil(length * 0.85 * climb.slowness / FLIGHT_SPEED), 10, 400);
    }

    /** Raycasts along the curve (8, a long one 12): nothing in the way but near its start and its end (the perches). */
    private boolean isClear(ServerWorld world, Flight way) {
        Vec3d lift = new Vec3d(0, MagpiePerches.HEIGHT / 2, 0);
        Vec3d previous = way.from.add(lift);
        int segments = way.far ? 12 : 8;
        for (int i = 1; i <= segments; i++) {
            Vec3d next = way.at(i / (double) segments).add(lift);
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
            if (walking) {
                if (attacker != null) walkTo(getPos().add(getPos().subtract(attacker.getPos()).multiply(1, 0, 1).normalize().multiply(WANDER_RADIUS)), 1.6);
            } else if (!isBaby()) {
                takeOff(world, attacker != null ? attacker.getPos() : null);
            }
        }
        return hurt;
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean isInsideWall() {
        return walking && super.isInsideWall();
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

    /** A chick's voice higher. */
    @Override
    public float getSoundPitch() {
        return (isBaby() ? 1.4F : 0.95F) + random.nextFloat() * 0.15F;
    }

    // ---------------------------------------------------------------- GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> state.setAndContinue(switch (getFlightState()) {
            case TAKING_OFF -> TAKEOFF;
            case FLYING -> FLY;
            case GLIDING -> GLIDE;
            case LANDING -> LAND;
            case ASLEEP -> SLEEP;
            default -> state.isMoving() && isBaby() ? WALK : IDLE;
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
