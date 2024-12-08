package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Arm;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.*;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

public class DiceEntity extends LivingEntity implements GeoEntity {
    private static final TrackedData<Integer> ROLL_VALUE = DataTracker.registerData(DiceEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> ROLLING = DataTracker.registerData(DiceEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Optional<UUID>> TARGET = DataTracker.registerData(DiceEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(DiceEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.dice.idle");
    protected static final RawAnimation ROLL_ANIM = RawAnimation.begin().thenLoop("animation.dice.rolling");
    private String skin;
    private static final int ROLLING_STATE = -1;
    AttractionSimulation simulation = new AttractionSimulation(null, this);
    public static final int MIN = 1;
    public static final int MAX = 10;
    public int fakeValue = -999999;

    public int getRandomDiceValue() {
        return (int) (Math.random() * MAX) + MIN;
    }

    public enum Skin {
        DEFAULT("default"),
        CURSED("cursed"),
        CUSTOM("custom");

        private final String value;

        Skin(final String text) {
            this.value = text;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    @Override
    protected void tickNewAi() {
        super.tickNewAi();
    }

    public DiceEntity(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
        skin = Skin.DEFAULT.toString();
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(ROLLING, false);
        builder.add(ROLL_VALUE, 1);
        builder.add(TARGET, Optional.empty());
        builder.add(OWNER, Optional.empty());
    }

    // Getter and Setter for TARGET
    public Optional<UUID> getTarget() {
        return this.dataTracker.get(TARGET);
    }

    public void setTarget(@Nullable UUID target) {
        this.dataTracker.set(TARGET, Optional.ofNullable(target));
    }

    // Getter and Setter for OWNER
    public Optional<UUID> getOwner() {
        return this.dataTracker.get(OWNER);
    }

    public void setOwner(@Nullable UUID owner) {
        this.dataTracker.set(OWNER, Optional.ofNullable(owner));
    }

    public void setRolling(boolean rolling) {
        if (this.isRolling() == rolling) return;
        if (!rolling)
            this.pickRollValue();
        this.dataTracker.set(ROLLING, rolling); // Update the ROLLING data
    }

    public boolean isRolling() {
        return (Boolean) this.dataTracker.get(ROLLING); // Retrieve the current value of ROLLING
    }

    private void setRollValue(int rollValue) {
        this.dataTracker.set(ROLL_VALUE, rollValue);
    }

    public int getRollValue() {
        return (Integer) this.dataTracker.get(ROLL_VALUE);
    }

    protected void pickRollValue() {
        this.setRollValue(getRandomDiceValue());
    }

    public static DefaultAttributeContainer.Builder setAttributes() {
        return LivingEntity.createLivingAttributes().add(EntityAttributes.MAX_HEALTH, 20.0D)
                .add(EntityAttributes.ATTACK_DAMAGE, 0.0D)
                .add(EntityAttributes.ATTACK_SPEED, 0.0D)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 0.3D)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.3D);
    }

    public int tickCounter = 200; // Counter for ticks
    @Override
    public void tick() {
        if (!this.getWorld().isClient) {
            tickCounter++;

            // Check if 10 seconds have passed (200 ticks at 20 TPS)
            if (tickCounter >= 200) {
                tickCounter = 0; // Reset counter

                // Check and update the target if present
                this.getTarget().ifPresent(uuid -> {
                    LivingEntity entity = (LivingEntity) ((ServerWorld) this.getWorld()).getEntity(uuid);
                    if (entity != null) {
                        simulation.setTarget(entity);
                    }
                });
            }
            simulation.tick();
        }
        super.tick();
    }

    public void findTarget(Class<? extends LivingEntity> clazz, TargetPredicate targetPredicate) {
        Steveparty.LOGGER.info("findTarget");
        if (this.getWorld() instanceof ServerWorld) {
            LivingEntity closestEntity = ((ServerWorld) this.getWorld()).getClosestEntity(
                    clazz,
                    targetPredicate,
                    this,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    this.getBoundingBox().expand(10.0) // Adjust the range as needed
            );
            if (closestEntity != null) {
                this.setTargetEntity(closestEntity);
            }
        }
    }

    @Override
    public Arm getMainArm() {
        return Arm.RIGHT;
    }

    public void setTargetEntity(@Nullable LivingEntity entity) {
        simulation.setTarget(entity);
        this.setTarget(entity == null ? null : entity.getUuid());
        Steveparty.LOGGER.info("Target set to: {}", entity == null ? "null" : entity.getName().getString());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "Rolling", 5, this::rollAnimController));
        controllers.add(new AnimationController<>(this, "Idle", 5, this::idleAnimController));
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.setRolling(nbt.getBoolean("Rolling"));
        this.setRollValue(nbt.getInt("RollValue"));
        this.skin = nbt.getString("Skin");
        if (nbt.containsUuid("Target")) {
            this.setTarget(nbt.getUuid("Target"));
        }
        if (nbt.containsUuid("Owner")) {
            this.setOwner(nbt.getUuid("Owner"));
        }
        this.setInvulnerable(true);
        this.setNoGravity(true);
        if (this.getWorld() instanceof ServerWorld) {
            Steveparty.LOGGER.info("Target UUID: {}", this.getTarget());
            this.getTarget().ifPresent(uuid -> {
                Steveparty.LOGGER.info("Entity: {}", (LivingEntity) ((ServerWorld) this.getWorld())
                        .getEntity(uuid));
                simulation.setTarget((LivingEntity) ((ServerWorld) this.getWorld())
                    .getEntity(uuid));
            });
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putString("Skin", this.skin);
        nbt.putBoolean("Rolling", this.isRolling());
        nbt.putInt("RollValue", this.getRollValue());
        this.getTarget().ifPresent(uuid -> nbt.putUuid("Target", uuid));
        this.getOwner().ifPresent(uuid -> nbt.putUuid("Owner", uuid));
        return super.writeNbt(nbt);
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        if (source.getAttacker() instanceof ServerPlayerEntity player) {
            setRolling(!isRolling());
        }
        return false;
    }

    @Override
    public Iterable<ItemStack> getArmorItems() {
        return Collections.singleton(ItemStack.EMPTY);
    }

    @Override
    public ItemStack getEquippedStack(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack) {
    }

    private PlayState idleAnimController(AnimationState<DiceEntity> event) {
        if (!isRolling())
            return event.setAndContinue(IDLE_ANIM);
        return PlayState.STOP;
    }

    private PlayState rollAnimController(AnimationState<DiceEntity> event) {
        if (isRolling()) {
            return event.setAndContinue(ROLL_ANIM);
        }
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
