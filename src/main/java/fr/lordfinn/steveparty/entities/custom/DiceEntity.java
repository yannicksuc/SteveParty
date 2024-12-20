package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.events.DiceRollEvent;
import fr.lordfinn.steveparty.mixin.FireworkRocketEntityAccessor;
import fr.lordfinn.steveparty.utils.MessageUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.animation.AnimationState;

import java.util.*;

import static fr.lordfinn.steveparty.items.ModItems.DEFAULT_DICE;
import static fr.lordfinn.steveparty.utils.EntitiesUtils.getPlayerNameByUuid;
import static net.minecraft.component.DataComponentTypes.FIREWORKS;

public class DiceEntity extends LivingEntity implements GeoEntity {
    private static final TrackedData<Integer> ROLL_VALUE = DataTracker.registerData(DiceEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> ROLLING = DataTracker.registerData(DiceEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Optional<UUID>> TARGET = DataTracker.registerData(DiceEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(DiceEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.dice.idle");
    protected static final RawAnimation ROLL_ANIM = RawAnimation.begin().thenLoop("animation.dice.rolling");
    private String skin;
    final AttractionSimulation simulation = new AttractionSimulation(null, this);
    public static final int MIN = 1;
    public static final int MAX = 10;

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

    @Override
    public @Nullable Text getCustomName() {
        return null;
    }

    @Override
    public Text getDisplayName() {
        return Text.empty();
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
        return this.dataTracker.get(ROLLING); // Retrieve the current value of ROLLING
    }

    private void setRollValue(int rollValue) {
        this.dataTracker.set(ROLL_VALUE, rollValue);
    }

    public int getRollValue() {
        return this.dataTracker.get(ROLL_VALUE);
    }

    protected void pickRollValue() {
        this.setRollValue(getRandomDiceValue());
        this.getOwner().ifPresent(owner -> {
            DiceRollEvent.EVENT.invoker().onRoll(this, owner, this.getRollValue());
            if (this.getWorld() instanceof ServerWorld world) {
                String playerName = getPlayerNameByUuid(world.getServer(), owner);
                MessageUtils.sendToNearby(this.getServer(), this.getPos(), 20, Text.translatable("message.steveparty.owned_dice_rolled", this.getRollValue(), playerName == null ? Text.translatable("message.steveparty.unknown_player") : playerName), MessageUtils.MessageType.CHAT);
                }
        });

    }


    public static DefaultAttributeContainer.Builder setAttributes() {
        return LivingEntity.createLivingAttributes().add(EntityAttributes.MAX_HEALTH, 20.0D)
                .add(EntityAttributes.ATTACK_DAMAGE, 0.0D)
                .add(EntityAttributes.ATTACK_SPEED, 0.0D)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 0.3D)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.3D);
    }

    @Override
    public void tick() {
        if (!this.getWorld().isClient) {
            if (getTick(this) % 200 == 0) {

                // Check and update the target if present
                this.getTarget().ifPresent(uuid -> {
                    LivingEntity entity = (LivingEntity) ((ServerWorld) this.getWorld()).getEntity(uuid);
                    if (entity != null) {
                        simulation.setTarget(entity);
                    }
                });
            }
            if (getTick(this) % 20 == 0 && this.isRolling()) {
                (this.getWorld()).playSound(
                        null,
                        this.getBlockPos(),
                        SoundEvents.ENTITY_BREEZE_WHIRL,
                        SoundCategory.AMBIENT,
                        1F,
                        0.7F
                );
            }
            simulation.tick();
        }
        super.tick();
    }

    public void findTarget(Class<? extends LivingEntity> clazz) {
        if (this.getWorld() instanceof ServerWorld world) {
            LivingEntity closestEntity = findClosestEntityInRange(world, clazz, 20);
            if (closestEntity != null) {
                this.setTargetEntity(closestEntity);
            }
        }
    }

    public LivingEntity findClosestEntityInRange(ServerWorld world, Class<? extends LivingEntity> clazz, double radius) {
        double closestDistance = radius; // The maximum range to search for entities
        LivingEntity closestEntity = null;

        for (Entity entity : world.iterateEntities()) {
            if (clazz.isInstance(entity) && entity instanceof LivingEntity livingEntity) {
                double distance = this.squaredDistanceTo(entity.getPos());

                if (distance <= (radius * radius)) {
                    if (closestEntity == null || distance < closestDistance) {
                        closestDistance = distance;
                        closestEntity = livingEntity;
                    }
                }
            }
        }
        return closestEntity;
    }

    @Override
    public Arm getMainArm() {
        return Arm.RIGHT;
    }

    public void setTargetEntity(@Nullable LivingEntity entity) {
        simulation.setTarget(entity);
        this.setTarget(entity == null ? null : entity.getUuid());
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
            this.getTarget().ifPresent(uuid -> simulation.setTarget((LivingEntity) ((ServerWorld) this.getWorld())
                .getEntity(uuid)));
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
            if (player.isSneaking()) {
                giveBackDice(player);
                return true;
            }
            setRolling(!isRolling());

            world.playSound(
                    null,
                    this.getPos().x,this.getPos().y,this.getPos().z,
                    SoundEvents.BLOCK_NOTE_BLOCK_BELL,
                    SoundCategory.PLAYERS,
                    1.0f,
                    1.0F);
        }
        return false;
    }

    @Override
    public boolean shouldRenderName() {
        return false;
    }

    private void giveBackDice(ServerPlayerEntity player) {
        ItemStack diceItem = new ItemStack(DEFAULT_DICE);
        player.getInventory().offerOrDrop(diceItem);
        this.remove(RemovalReason.DISCARDED);
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

    @Override
    public void remove(RemovalReason reason) {
        if (!this.getWorld().isClient && reason == RemovalReason.DISCARDED) {
            summonFirework(createStopFireworkItem());
        }
        super.remove(reason);
    }

    private void summonFirework(ItemStack itemstack) {
        if (this.getWorld() instanceof ServerWorld world) {
            // Create the firework entity
            FireworkRocketEntity firework = new FireworkRocketEntity(world, this.getX(), this.getY() + this.getHeight() / 2, this.getZ(), itemstack);
            // Set the firework to explode instantly
            ((FireworkRocketEntityAccessor) firework).setLifeTime(1);
            world.spawnEntity(firework);
        }
    }

    private ItemStack createStopFireworkItem() {
        ItemStack fireworkStack = new ItemStack(net.minecraft.item.Items.FIREWORK_ROCKET);

        List<FireworkExplosionComponent> components = new ArrayList<>();
        IntList colors = new IntArrayList(2);
        colors.add(0x569DCD);
        colors.add(0xFEDB27);
        IntList colorsFade = new IntArrayList(2);
        colorsFade.add(0x1B5FC5);
        colorsFade.add(0xC78B1D);

        components.add(new FireworkExplosionComponent(FireworkExplosionComponent.Type.SMALL_BALL, colors, colorsFade, false, false));
        FireworksComponent fireworksComponent = new FireworksComponent(0, components);
        fireworkStack.set(FIREWORKS, fireworksComponent);
        return fireworkStack;
    }
}
