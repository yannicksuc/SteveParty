package fr.lordfinn.steveparty.entities.custom.mistigri;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * The Mistigri's giant loaded die, rolled on his board space (MistigriSentences): a prop moved by the board, the mod's
 * die model with the cursed faces, big. It tumbles while {@link #isRolling()}, then shows {@link #getFace()} (always
 * a low one: it is loaded). A board actor: never saved, never hit.
 */
public class MistigriDieEntity extends Entity implements GeoEntity {
    private static final TrackedData<Boolean> ROLLING =
            DataTracker.registerData(MistigriDieEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> FACE =
            DataTracker.registerData(MistigriDieEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final RawAnimation ROLL = RawAnimation.begin().thenLoop("animation.dice.rolling");
    private static final RawAnimation REST = RawAnimation.begin().thenLoop("animation.dice.idle");

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    public MistigriDieEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        setNoGravity(true);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(ROLLING, true);
        builder.add(FACE, 1);
    }

    public boolean isRolling() {
        return this.dataTracker.get(ROLLING);
    }

    public void setRolling(boolean rolling) {
        this.dataTracker.set(ROLLING, rolling);
    }

    /** The cursed face it shows once stopped: 1 to 3. */
    public int getFace() {
        return this.dataTracker.get(FACE);
    }

    public void setFace(int face) {
        this.dataTracker.set(FACE, MathHelper.clamp(face, 1, 3));
    }

    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4,
                state -> state.setAndContinue(isRolling() ? ROLL : REST)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
