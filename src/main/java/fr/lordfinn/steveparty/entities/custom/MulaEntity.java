package fr.lordfinn.steveparty.entities.custom;

import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.FlyGoal;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.animation.AnimationState;

public class MulaEntity extends TameableEntity implements GeoEntity {

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    protected static final RawAnimation FLY_ANIM = RawAnimation.begin().thenLoop("fly");

    public MulaEntity(EntityType<MulaEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    public @Nullable PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
        return null;
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new FlyGoal(this, 1.0));
        this.goalSelector.add(1, new FollowOwnerGoal(this, 0.3, 2, 20));
        super.initGoals();
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        this.navigation = new BirdNavigation(this, world);
        return navigation;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
    }

    public static DefaultAttributeContainer.Builder setAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(EntityAttributes.MAX_HEALTH, 30.0D)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.25D)
                .add(EntityAttributes.FOLLOW_RANGE, 20.0D)
                .add(EntityAttributes.FLYING_SPEED, 0.3D);
    }

    @Override
    public Arm getMainArm() {
        return Arm.RIGHT;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "fly", 10, this::flyAnimController));
        controllers.add(new AnimationController<>(this, "idle", 10, this::idleAnimController));
    }

    private PlayState idleAnimController(AnimationState<MulaEntity> event) {
        if (this.getVelocity().lengthSquared() < 0.01) {
            return event.setAndContinue(IDLE_ANIM);
        }
        return PlayState.STOP;
    }

    private PlayState flyAnimController(AnimationState<MulaEntity> event) {
        if (this.getVelocity().lengthSquared() > 0.01) {
            return event.setAndContinue(FLY_ANIM);
        }
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public @Nullable Text getCustomName() {
        return Text.translatable("entity.mula.name");
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("entity.mula.name");
    }

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return false;
    }
}

