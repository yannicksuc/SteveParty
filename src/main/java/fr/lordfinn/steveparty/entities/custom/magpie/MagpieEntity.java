package fr.lordfinn.steveparty.entities.custom.magpie;

import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.items.custom.cartridges.PotCartridgeItem;
import fr.lordfinn.steveparty.utils.Easing;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * The Pie (a magpie), the keeper of a Common pot: it lives on its nest ({@link MagpieNestBlock}) by its board space,
 * part of the space like the nest (not a summoned board mob): saved with the world, never hurt, it only goes away with
 * the space's Common pot cartridge or its nest. No AI, no pathfinding: perched, it does nothing but look around (its
 * animation); its flights are scripted legs ({@link #flyTo}) the pot asks for: fetching a passing token's stake (a coin
 * in its beak), bringing the pot to its winner.
 * <p>
 * Server side it checks its home every {@link #CHECK_INTERVAL} ticks; a flight in progress is not saved (it is back
 * on its nest after a reload).
 */
public class MagpieEntity extends Entity implements GeoEntity {
    private static final TrackedData<Boolean> FLYING = DataTracker.registerData(MagpieEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> CARRYING = DataTracker.registerData(MagpieEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final RawAnimation PERCHED = RawAnimation.begin().thenLoop("animation.magpie.idle");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.magpie.fly");
    public static final int CHECK_INTERVAL = 40;
    /** Too far from its nest (pushed, a glitch): it flies home at once. */
    private static final double LEASH = 24;

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private @Nullable BlockPos home;
    private @Nullable BlockPos nest;

    /** A scripted leg of a flight: from, to, how long, how high it arcs, what happens on arrival. */
    private record Leg(Supplier<Vec3d> to, int ticks, double arc, @Nullable Runnable onArrival, boolean carrying) {
    }

    private final Deque<Leg> legs = new ArrayDeque<>();
    private @Nullable Leg leg;
    private Vec3d legFrom = Vec3d.ZERO;
    private Vec3d legTo = Vec3d.ZERO;
    private int legTick;

    public MagpieEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        setNoGravity(true);
    }

    /** A magpie for the Common pot of the board space at {@code home}, perched on the nest at {@code nest}. */
    public static MagpieEntity create(EntityType<MagpieEntity> type, ServerWorld world, BlockPos home, BlockPos nest) {
        MagpieEntity magpie = new MagpieEntity(type, world);
        magpie.home = home.toImmutable();
        magpie.nest = nest.toImmutable();
        Vec3d perch = perchOf(nest);
        magpie.refreshPositionAndAngles(perch.x, perch.y, perch.z, world.random.nextFloat() * 360, 0);
        return magpie;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(FLYING, false);
        builder.add(CARRYING, false);
    }

    public boolean isFlying() {
        return dataTracker.get(FLYING);
    }

    /** A coin (or the pot) in its beak. */
    public boolean isCarrying() {
        return dataTracker.get(CARRYING);
    }

    public @Nullable BlockPos getHome() {
        return home;
    }

    public @Nullable BlockPos getNest() {
        return nest;
    }

    /** Where it stands on its nest: the middle of the nest, on its rim. */
    public static Vec3d perchOf(BlockPos nest) {
        return new Vec3d(nest.getX() + 0.5, nest.getY() + MagpieNestBlock.HEIGHT / 16.0, nest.getZ() + 0.5);
    }

    // ---------------------------------------------------------------- flights

    /**
     * Adds a leg to its flight: to where {@code to} says (read when the leg starts: a token that moved meanwhile is
     * still reached), in {@code ticks} ticks, arcing {@code arc} blocks up; {@code onArrival} then runs.
     */
    public void flyTo(Supplier<Vec3d> to, int ticks, double arc, boolean carrying, @Nullable Runnable onArrival) {
        legs.add(new Leg(to, Math.max(1, ticks), arc, onArrival, carrying));
    }

    /** Back to its nest ({@code carrying}: something in its beak). */
    public void flyHome(int ticks, boolean carrying, @Nullable Runnable onArrival) {
        if (nest == null) return;
        BlockPos at = nest;
        flyTo(() -> perchOf(at), ticks, 1.2, carrying, onArrival);
    }

    /** True while it is on a flight (or about to start one). */
    public boolean isBusy() {
        return leg != null || !legs.isEmpty();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld world)) return;
        if (age % CHECK_INTERVAL == 0 && !isHomeValid(world)) {
            world.spawnParticles(ParticleTypes.POOF, getX(), getY() + 0.3, getZ(), 6, 0.15, 0.15, 0.15, 0.02);
            discard();
            return;
        }
        if (leg == null && !legs.isEmpty()) startLeg(legs.poll());
        if (leg != null) {
            tickLeg(world);
            return;
        }
        // Perched: on its nest (pushed off or loaded elsewhere: it hops back)
        if (nest != null) {
            Vec3d perch = perchOf(nest);
            if (getPos().squaredDistanceTo(perch) > 0.01) {
                if (getPos().squaredDistanceTo(perch) > LEASH * LEASH) setPosition(perch);
                else if (!isBusy()) flyHome(20, false, null);
            }
        }
        if (isFlying()) dataTracker.set(FLYING, false);
    }

    private void startLeg(Leg next) {
        leg = next;
        legTick = 0;
        legFrom = getPos();
        legTo = next.to.get();
        dataTracker.set(FLYING, true);
        dataTracker.set(CARRYING, next.carrying);
        getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PARROT_FLY, SoundCategory.NEUTRAL, 0.6F, 1.3F);
    }

    private void tickLeg(ServerWorld world) {
        Leg current = leg;
        legTick++;
        double p = Math.min(1, legTick / (double) current.ticks);
        double eased = Easing.smoothstep(p);
        Vec3d at = legFrom.lerp(legTo, eased).add(0, Math.sin(Math.PI * p) * current.arc, 0);
        Vec3d delta = at.subtract(getPos());
        if (delta.horizontalLengthSquared() > 1.0E-5) {
            float yaw = (float) (MathHelper.atan2(delta.z, delta.x) * (180 / Math.PI)) - 90;
            setYaw(yaw);
        }
        setPosition(at);
        if (legTick % 6 == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PARROT_FLY, SoundCategory.NEUTRAL, 0.25F, 1.5F);
        if (p >= 1) {
            leg = null;
            if (legs.isEmpty()) {
                dataTracker.set(FLYING, false);
                dataTracker.set(CARRYING, false);
            }
            if (current.onArrival != null) current.onArrival.run();
        }
    }

    /** Its board space still holds a Common pot as its active cartridge, and its nest is still there. */
    private boolean isHomeValid(ServerWorld world) {
        if (home == null || nest == null) return false;
        if (!world.isChunkLoaded(home) || !world.isChunkLoaded(nest)) return true; // not known yet: kept
        if (!(world.getBlockState(nest).getBlock() instanceof MagpieNestBlock)) return false;
        return world.getBlockEntity(home) instanceof BoardSpaceBlockEntity space
                && space.getActiveCartridgeItemStack().getItem() instanceof PotCartridgeItem;
    }

    // ---------------------------------------------------------------- a part of its space

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        home = NbtHelper.toBlockPos(nbt, "Home").orElse(null);
        nest = NbtHelper.toBlockPos(nbt, "Nest").orElse(null);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (home != null) nbt.put("Home", NbtHelper.fromBlockPos(home));
        if (nest != null) nbt.put("Nest", NbtHelper.fromBlockPos(nest));
    }

    // ---------------------------------------------------------------- GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3,
                state -> state.setAndContinue(isFlying() ? FLY : PERCHED)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
