package fr.lordfinn.steveparty.entities.custom.boomcart;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * The Boomcart (Pétaroule): a grumpy little living mine cart, loaded with TNT, like a bulldog.
 * <ul>
 *     <li><b>Its load</b> ({@link #getLoad()}): a TNT block (the default), or a firework rocket in a barrel; drawn in
 *     it by BoomcartRenderer, saved with it.</li>
 *     <li><b>Its fuse</b> ({@link #getFuse()}): -1 while unlit, else the ticks left before it blows.</li>
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

    // ---------------------------------------------------------------- save

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.put("Load", getLoad().encode(getRegistryManager()));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Load")) {
            ItemStack.fromNbt(getRegistryManager(), nbt.get("Load"))
                    .filter(stack -> stack.isOf(Items.TNT) || stack.isOf(Items.FIREWORK_ROCKET))
                    .ifPresent(this::setLoad);
        }
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
