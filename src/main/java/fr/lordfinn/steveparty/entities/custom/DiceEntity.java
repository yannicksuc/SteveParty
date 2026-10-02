package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.dice.DiceModule;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.dice.DiceOutcome;
import fr.lordfinn.steveparty.dice.DiceRollSequence;
import fr.lordfinn.steveparty.events.DiceRollEvent;
import fr.lordfinn.steveparty.mixin.FireworkRocketEntityAccessor;
import fr.lordfinn.steveparty.data.handler.ListUuidTrackedDataHandler;
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
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.*;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.animation.AnimationState;

import java.util.*;
import java.util.stream.Collectors;

import static fr.lordfinn.steveparty.items.ModItems.DEFAULT_DICE;
import static fr.lordfinn.steveparty.utils.EntitiesUtils.getPlayerNameByUuid;
import static net.minecraft.component.DataComponentTypes.FIREWORKS;

/**
 * A thrown die. It rolls until a player hits it, then shows its result for a moment and goes away (a die carrying the
 * Infinity module goes back to its roller). The dice of a Double / Triple Dice are linked: they stop together and add
 * up into one roll ({@link DiceOutcome}); the first one (the lead) holds the item and runs the roll
 * ({@link DiceRollSequence}: the modules of the die decide how it stops), the others follow it.
 */
public class DiceEntity extends LivingEntity implements GeoEntity {
    private static final TrackedData<Integer> ROLL_VALUE = DataTracker.registerData(DiceEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Kind of the face shown (ordinal of {@link DiceFacesComponent.Kind}): the client picks its texture from it. */
    private static final TrackedData<Integer> ROLL_KIND = DataTracker.registerData(DiceEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Slow module: the rolling die shows the synced face (and turns slowly) instead of flickering at random. */
    private static final TrackedData<Boolean> FACE_SHOWN = DataTracker.registerData(DiceEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> ROLLING = DataTracker.registerData(DiceEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Optional<UUID>> TARGET = DataTracker.registerData(DiceEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(DiceEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<List<UUID>> LINKED_DICE = DataTracker.registerData(DiceEntity.class, ListUuidTrackedDataHandler.INSTANCE);

    /** The item given back / spent: the thrown die (empty on the dice following a lead, and on bare dice). */
    private ItemStack itemReference = ItemStack.EMPTY;
    /** The die this entity rolls: its faces and modules (the lead's item, copied on the dice following it). */
    private ItemStack dieStack = ItemStack.EMPTY;
    /** One of the other dice of a Double / Triple Dice: it follows the first one thrown (the lead). */
    private boolean follower;
    /** The roll of this throw (lead only, created when needed, not saved). */
    private @Nullable DiceRollSequence sequence;
    /** What the last finished roll does (lead only). */
    private DiceOutcome outcome = DiceOutcome.NONE;
    private List<DiceFace> rolledFaces = List.of();

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.dice.idle");
    protected static final RawAnimation ROLL_ANIM = RawAnimation.begin().thenLoop("animation.dice.rolling");
    private String skin;
    final AttractionSimulation simulation = new AttractionSimulation(null, this);
    public static final int MIN = 1;
    public static final int MAX = 10;
    /**
     * Ticks after the throw during which the thrower's own hits are ignored: a die thrown overhead (sneaking: it
     * comes back over its thrower) sits in their crosshair within reach, and the click of the throw (a double click, a
     * click still held) must not stop or burst it. Anyone else may hit it at once.
     */
    public static final int THROW_GRACE_TICKS = 10;

    private int secondsSinceRolled = 0;
    /** Loaded already rolled: its roll is over (it is not run again). */
    private boolean rollWasLoadedFinished;

    public DiceEntity(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
        skin = Skin.DEFAULT.toString();
    }

    public ItemStack getItemReference() {
        return itemReference;
    }

    /** The thrown item: what this die rolls (faces, modules) and what is given back or spent. */
    public void setItemReference(ItemStack itemReference) {
        this.itemReference = itemReference == null ? ItemStack.EMPTY : itemReference;
        this.dieStack = this.itemReference;
    }

    /** The die this entity rolls: its faces and its modules (empty: a plain die). */
    public ItemStack getDieStack() {
        return dieStack;
    }

    /** Makes this die one of the other dice of a Double / Triple Dice: it rolls the same die, but holds no item. */
    public void follow(ItemStack die) {
        this.itemReference = ItemStack.EMPTY;
        this.dieStack = die == null ? ItemStack.EMPTY : die;
        this.follower = true;
    }

    public boolean isFollower() {
        return follower;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(ROLLING, true);
        builder.add(ROLL_VALUE, 1);
        builder.add(ROLL_KIND, DiceFacesComponent.Kind.NORMAL.ordinal());
        builder.add(FACE_SHOWN, false);
        builder.add(TARGET, Optional.empty());
        builder.add(OWNER, Optional.empty());
        builder.add(LINKED_DICE, new ArrayList<>());
    }

    // Getter and Setter for TARGET
    public Optional<UUID> getTarget() {
        return this.dataTracker.get(TARGET);
    }

    public void setTarget(@Nullable UUID target) {
        setTarget(target, true);
    }

    public void setTarget(@Nullable UUID target, boolean propagate) {
        this.dataTracker.set(TARGET, Optional.ofNullable(target));
        if (propagate)
            propagateStateChange(dice -> dice.setTarget(target, false));
    }

    // Getter and Setter for OWNER
    public Optional<UUID> getOwner() {
        return this.dataTracker.get(OWNER);
    }

    public void setOwner(@Nullable UUID owner) {
        setOwner(owner, true);
    }

    public void setOwner(@Nullable UUID owner, boolean propagate) {
        this.dataTracker.set(OWNER, Optional.ofNullable(owner));
        if (propagate)
            propagateStateChange(dice -> dice.setOwner(owner, false));
    }

    /** Starts (again) or stops the roll of this throw, like a hit would (see {@link DiceRollSequence}). */
    public void setRolling(boolean rolling) {
        if (this.getWorld().isClient) {
            this.dataTracker.set(ROLLING, rolling);
            return;
        }
        DiceEntity lead = lead();
        if (rolling) lead.restartRoll();
        else lead.sequence().stop();
    }

    public boolean isRolling() {
        return this.dataTracker.get(ROLLING);
    }

    /** The die spins or not: what is seen only (the roll itself is {@link DiceRollSequence}). */
    public void setSpinning(boolean spinning) {
        this.dataTracker.set(ROLLING, spinning);
    }

    /** True while the rolling die shows the synced face, turning slowly (Slow module). */
    public boolean isFaceShown() {
        return this.dataTracker.get(FACE_SHOWN);
    }

    public void setFaceShown(boolean shown) {
        this.dataTracker.set(FACE_SHOWN, shown);
    }

    /** The face this die shows (its result once stopped). */
    public void showFace(DiceFace face) {
        this.dataTracker.set(ROLL_KIND, face.kind().ordinal());
        this.dataTracker.set(ROLL_VALUE, face.value());
    }

    private void setRollValue(int rollValue) {
        this.dataTracker.set(ROLL_VALUE, rollValue);
    }

    public int getRollValue() {
        return this.dataTracker.get(ROLL_VALUE);
    }

    /** Kind of the face shown. */
    public DiceFacesComponent.Kind getRollKind() {
        DiceFacesComponent.Kind[] kinds = DiceFacesComponent.Kind.values();
        int ordinal = this.dataTracker.get(ROLL_KIND);
        return ordinal >= 0 && ordinal < kinds.length ? kinds[ordinal] : DiceFacesComponent.Kind.NORMAL;
    }

    /** The face this die shows. */
    public DiceFace getRolledFace() {
        return new DiceFace(getRollKind(), getRollValue());
    }

    // ------------------------------------------------------------------ the roll

    /** The die that runs the roll of this throw: this one, or the one it follows. */
    public DiceEntity lead() {
        if (!follower) return this;
        for (DiceEntity die : getLinkedDiceEntities()) {
            if (!die.follower) return die;
        }
        return this; // its lead is gone: it goes on alone
    }

    /** The dice thrown together, the lead first. */
    public List<DiceEntity> group() {
        DiceEntity lead = lead();
        List<DiceEntity> group = new ArrayList<>();
        group.add(lead);
        for (DiceEntity die : lead.getLinkedDiceEntities()) {
            if (!group.contains(die)) group.add(die);
        }
        if (!group.contains(this)) group.add(this);
        return group;
    }

    /** The roll of this throw (ask the {@link #lead}). */
    public DiceRollSequence sequence() {
        if (sequence == null) sequence = new DiceRollSequence(this);
        return sequence;
    }

    /** The die was just thrown: its roll begins (its item, owner and linked dice are set). */
    public void startRoll() {
        if (this.getWorld().isClient) return;
        lead().sequence().start();
    }

    private void restartRoll() {
        if (sequence != null) sequence.cancel();
        sequence = new DiceRollSequence(this);
        secondsSinceRolled = 0;
        outcome = DiceOutcome.NONE;
        rolledFaces = List.of();
        sequence.start();
    }

    /** True once the roll of this throw is final. */
    public boolean isRollFinished() {
        DiceEntity lead = lead();
        return lead.rollWasLoadedFinished || (lead.sequence != null && lead.sequence.phase() == DiceRollSequence.Phase.DONE);
    }

    /**
     * The roll is final ({@link DiceRollSequence}): {@code faces} are the faces of the dice of the throw. The modules
     * of the die have their say on what it does (Reversed), then it is announced: {@link DiceRollEvent} moves the
     * roller's token.
     */
    public void onRollFinished(List<DiceFace> faces) {
        this.rolledFaces = List.copyOf(faces);
        this.secondsSinceRolled = 0;
        DiceOutcome result = DiceOutcome.of(faces);
        Map<DiceModule, Integer> modules = DiceModules.of(dieStack);
        for (Map.Entry<DiceModule, Integer> entry : modules.entrySet()) result = entry.getKey().modifyOutcome(result, entry.getValue());
        this.outcome = result;
        DiceOutcome announced = result;
        this.getOwner().ifPresent(owner -> {
            DiceRollEvent.EVENT.invoker().onRoll(this, owner, announced.steps());
            if (this.getWorld() instanceof ServerWorld world) {
                String playerName = getPlayerNameByUuid(world.getServer(), owner);
                MessageUtils.sendToNearby(world, this.getPos(), 20,
                        Text.translatable("message.steveparty.owned_dice_rolled", announced.describe(),
                                playerName == null ? Text.translatable("message.steveparty.unknown_player") : playerName),
                        MessageUtils.MessageType.CHAT);
            }
        });
        modules.forEach((module, count) -> module.afterRoll(this, announced, count));
    }

    /** What the finished roll of this throw does ({@link DiceOutcome#NONE} until then). */
    public DiceOutcome getOutcome() {
        return lead().outcome;
    }

    /** The faces the dice of this throw stopped on (empty until the roll is final). */
    public List<DiceFace> getRolledFaces() {
        return lead().rolledFaces;
    }

    public static DefaultAttributeContainer.Builder setAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0D)
                .add(EntityAttributes.ATTACK_DAMAGE, 0.0D)
                .add(EntityAttributes.ATTACK_SPEED, 0.0D)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 0.3D)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.3D);
    }

    @Override
    public void tick() {
        if (!this.getWorld().isClient) {
            if (getTick(this) % 200 == 0) {
                this.getTarget().ifPresent(uuid -> {
                    if (((ServerWorld) this.getWorld()).getEntity(uuid) instanceof LivingEntity entity) {
                        simulation.setTarget(entity);
                    }
                });
            }
            if (getTick(this) % 20 == 0) {
                if (this.isRolling())
                    this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_BREEZE_WHIRL, SoundCategory.AMBIENT, 1F, 0.7F);
                else {
                    // The result was seen: the thrown die goes away (Infinity: back to its roller)
                    if (!follower && isRollFinished()) {
                        secondsSinceRolled++;
                        if (secondsSinceRolled >= 2 && !itemReference.isEmpty()) explode(null);
                    }
                }
            }
            simulation.tick();
            if (!this.isRemoved() && !rollWasLoadedFinished && lead() == this) sequence().tick();
        }
        super.tick();
    }

    /**
     * Client side: a living entity eases towards each synced position over 3 ticks, on top of the tracking
     * interval. For a dice that keeps moving this drew it (and its client hitbox) blocks behind its real, server
     * position.
     * The dice is synced every tick, so it reaches each position within one tick (the frame interpolation keeps
     * the movement smooth).
     */
    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
        super.updateTrackedPositionAndAngles(x, y, z, yaw, pitch, Math.min(interpolationSteps, 1));
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
        double closestDistance = radius * radius;
        LivingEntity closestEntity = null;

        for (LivingEntity livingEntity : world.getEntitiesByClass(clazz, this.getBoundingBox().expand(radius), e -> e != this)) {
            double distance = this.squaredDistanceTo(livingEntity.getPos());

            if (distance <= (radius * radius)) {
                if (closestEntity == null || distance < closestDistance) {
                    closestDistance = distance;
                    closestEntity = livingEntity;
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
        try {
            super.readNbt(nbt);

            // A die saved while rolling rolls again; a stopped one keeps showing its result (nothing is announced twice)
            this.setSpinning(nbt.getBoolean("Rolling"));
            this.setRollValue(nbt.getInt("RollValue"));
            this.dataTracker.set(ROLL_KIND, nbt.contains("RollKind") ? nbt.getInt("RollKind") : DiceFacesComponent.Kind.NORMAL.ordinal());
            this.follower = nbt.getBoolean("Follower");
            this.secondsSinceRolled = 0;
            this.skin = nbt.getString("Skin");

            if (nbt.containsUuid("Target")) {
                this.setTarget(nbt.getUuid("Target"));
            } else {
                this.setTarget(null);
            }

            if (nbt.containsUuid("Owner")) {
                this.setOwner(nbt.getUuid("Owner"));
            } else {
                this.setOwner(null);
            }

            if (nbt.contains("LinkedDice", NbtElement.LIST_TYPE)) {
                NbtList linkedDiceList = nbt.getList("LinkedDice", NbtElement.STRING_TYPE);
                try {
                    List<UUID> linkedDice = linkedDiceList.stream()
                            .map(NbtElement::asString)
                            .map(UUID::fromString)
                            .collect(Collectors.toList());
                    this.setLinkedDice(linkedDice);
                } catch (IllegalArgumentException ex) {
                    // Log and clear on failure
                    this.setLinkedDice(Collections.emptyList());
                    System.err.println("Failed to parse LinkedDice UUIDs from NBT: " + ex.getMessage());
                }
            } else {
                this.setLinkedDice(Collections.emptyList());
            }

            if (nbt.contains("ItemReference", NbtElement.COMPOUND_TYPE)) {
                NbtCompound itemReferenceNbt = nbt.getCompound("ItemReference");
                Optional<ItemStack> itemReference = Optional.empty();
                try {
                    itemReference = ItemStack.fromNbt(
                            RegistryWrapper.WrapperLookup.of(this.getRegistryManager().stream()), itemReferenceNbt);
                } catch (Exception ex) {
                    System.err.println("Failed to parse ItemReference from NBT: " + ex.getMessage());
                }
                itemReference.ifPresentOrElse(
                        this::setItemReference,
                        () -> this.setItemReference(ItemStack.EMPTY) // fallback to empty ItemStack
                );
            } else {
                this.setItemReference(ItemStack.EMPTY);
            }
            if (nbt.contains("DieStack", NbtElement.COMPOUND_TYPE)) {
                try {
                    this.dieStack = ItemStack.fromNbt(RegistryWrapper.WrapperLookup.of(this.getRegistryManager().stream()),
                            nbt.getCompound("DieStack")).orElse(ItemStack.EMPTY);
                } catch (Exception ex) {
                    this.dieStack = ItemStack.EMPTY;
                }
            }
            if (!nbt.getBoolean("Rolling")) {
                // Already rolled: the roll is over, nothing to run again
                this.sequence = null;
                this.rollWasLoadedFinished = true;
            }

            this.setInvulnerable(true);
            this.setNoGravity(true);

            if (this.getWorld() instanceof ServerWorld) {
                this.getTarget().ifPresent(uuid -> {
                    Entity targetEntity = ((ServerWorld) this.getWorld()).getEntity(uuid);
                    if (targetEntity instanceof LivingEntity) {
                        simulation.setTarget((LivingEntity) targetEntity);
                    } else {
                        System.err.println("Target entity UUID does not refer to a LivingEntity");
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Error reading NBT in DiceEntity: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        try {
            nbt.putString("Skin", this.skin);
            nbt.putBoolean("Rolling", this.isRolling());
            nbt.putInt("RollValue", this.getRollValue());
            nbt.putInt("RollKind", this.dataTracker.get(ROLL_KIND));
            if (follower) nbt.putBoolean("Follower", true);

            this.getTarget().ifPresent(uuid -> nbt.putUuid("Target", uuid));
            this.getOwner().ifPresent(uuid -> nbt.putUuid("Owner", uuid));

            NbtList linkedDiceList = new NbtList();
            this.getLinkedDice().forEach(uuid -> linkedDiceList.add(NbtString.of(uuid.toString())));
            nbt.put("LinkedDice", linkedDiceList);

            ItemStack itemRef = this.getItemReference();
            if (itemRef != null && !itemRef.isEmpty()) {
                try {
                    nbt.put("ItemReference", itemRef.toNbt(
                            RegistryWrapper.WrapperLookup.of(this.getRegistryManager().stream())
                    ));
                } catch (Exception e) {
                    System.err.println("Failed to write ItemReference to NBT: " + e.getMessage());
                }
            } else if (dieStack != null && !dieStack.isEmpty()) {
                try {
                    nbt.put("DieStack", dieStack.toNbt(RegistryWrapper.WrapperLookup.of(this.getRegistryManager().stream())));
                } catch (Exception e) {
                    System.err.println("Failed to write DieStack to NBT: " + e.getMessage());
                }
            }

            return super.writeNbt(nbt);
        } catch (Exception e) {
            System.err.println("Error writing NBT in DiceEntity: " + e.getMessage());
            e.printStackTrace();
            return nbt; // fallback to partial data
        }
    }


    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        // /kill, the void, /damage with generic_kill...: damage that ignores invulnerability removes the dice
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            killDice();
            return true;
        }
        if (source.getAttacker() instanceof ServerPlayerEntity player) {
            if (isInThrowGrace(player)) return false;
            if (player.isSneaking()) {
                explode(player);
                return true;
            }
            lead().onPlayerHit(player);
            world.playSound(null, this.getPos().x, this.getPos().y, this.getPos().z, SoundEvents.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 1.0f, 1.0F);
        }
        return false;
    }

    /**
     * /kill (and any other generic kill) removes the dice at once: a dice has no health to lose, and dying would
     * play the living entity death animation. Like an explosion, an Infinity die goes back to its online owner.
     */
    @Override
    public void kill(ServerWorld world) {
        killDice();
    }

    /** True if {@code player} just threw this die: their hits don't count yet (see {@link #THROW_GRACE_TICKS}). */
    public boolean isInThrowGrace(ServerPlayerEntity player) {
        return this.age < THROW_GRACE_TICKS && this.getOwner().map(owner -> owner.equals(player.getUuid())).orElse(false);
    }

    /**
     * A player (not sneaking) hit one of the dice of this throw (lead only). Rolling: the roll stops, or its modules
     * decide ({@link DiceRollSequence#hit}). Rolled: the thrown die goes away at once (Infinity: back to its roller);
     * a bare die (no item: summoned) rolls again.
     */
    private void onPlayerHit(ServerPlayerEntity player) {
        if (rollWasLoadedFinished || (sequence != null && sequence.phase() == DiceRollSequence.Phase.DONE)) {
            if (!itemReference.isEmpty()) {
                explode(player);
            } else {
                rollWasLoadedFinished = false;
                restartRoll();
            }
            return;
        }
        sequence().hit(player);
    }

    private void killDice() {
        if (this.isRemoved()) return;
        giveBackDice(null, RemovalReason.KILLED);
        this.emitGameEvent(GameEvent.ENTITY_DIE);
    }

    private void explode(ServerPlayerEntity player) {
        giveBackDice(player);
        propagateStateChange(dice -> dice.giveBackDice(player));
    }

    @Override
    public boolean shouldRenderName() {
        return false;
    }

    /**
     * Removes the dice. A die carrying the Infinity module is not lost: it goes back to its owner when the owner is online,
     * otherwise to the player who exploded it (anyone may explode a dice).
     */
    private void giveBackDice(@Nullable ServerPlayerEntity player) {
        giveBackDice(player, RemovalReason.DISCARDED);
    }

    private void giveBackDice(@Nullable ServerPlayerEntity player, RemovalReason reason) {
        ItemStack diceItem = getItemReference();
        if (sequence != null) sequence.cancel();
        if (!diceItem.isEmpty() && DiceModules.returnsToRoller(diceItem)) {
            ServerPlayerEntity recipient = getOnlineOwner();
            if (recipient == null) recipient = player;
            if (recipient != null)
                recipient.getInventory().offerOrDrop(diceItem);
        }
        this.remove(reason);
    }

    /** The player who threw the die, if connected. */
    @Nullable
    public ServerPlayerEntity getOnlineOwner() {
        if (!(this.getWorld() instanceof ServerWorld world)) return null;
        return this.getOwner()
                .map(uuid -> world.getServer().getPlayerManager().getPlayer(uuid))
                .orElse(null);
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
        if (!isRolling()) return event.setAndContinue(IDLE_ANIM);
        return PlayState.STOP;
    }

    private PlayState rollAnimController(AnimationState<DiceEntity> event) {
        if (isRolling()) {
            // A Slow die turns slowly: its faces are read as they go by
            event.getController().setAnimationSpeed(isFaceShown() ? 0.15 : 1.0);
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
            FireworkRocketEntity firework = new FireworkRocketEntity(world, this.getX(), this.getY() + this.getHeight() / 2, this.getZ(), itemstack);
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

    @SuppressWarnings("EmptyMethod")
    @Override
    protected void tickNewAi() {
        super.tickNewAi();
    }

    @Override
    public @Nullable Text getCustomName() {
        return null;
    }

    @Override
    public Text getDisplayName() {
        return Text.empty();
    }

    // Methods for managing linked dice
    public List<UUID> getLinkedDice() {
        return this.dataTracker.get(LINKED_DICE);
    }

    public void setLinkedDice(List<UUID> linkedDice) {
        this.dataTracker.set(LINKED_DICE, linkedDice.stream().filter(uuid -> !uuid.equals(this.getUuid())).toList());
    }

    public void addLinkedDice(UUID diceUuid) {
        if (this.getLinkedDice().contains(diceUuid)) return;
        if (diceUuid.equals(this.getUuid())) return;
        List<UUID> linkedDice = new ArrayList<>(this.getLinkedDice());
        linkedDice.add(diceUuid);
        this.setLinkedDice(linkedDice);
    }

    public void removeLinkedDice(UUID diceUuid) {
        List<UUID> linkedDice = new ArrayList<>(this.getLinkedDice());
        linkedDice.remove(diceUuid);
        this.setLinkedDice(linkedDice);
    }

    private void propagateStateChange(java.util.function.Consumer<DiceEntity> stateChange) {
        if (!(this.getWorld() instanceof ServerWorld world)) return;
        for (UUID uuid : this.getLinkedDice()) {
            if (uuid.equals(this.getUuid())) continue;
            if (world.getEntity(uuid) instanceof DiceEntity linkedDice) {
                stateChange.accept(linkedDice);
            }
        }
    }

    private List<DiceEntity> getLinkedDiceEntities() {
        if (!(this.getWorld() instanceof ServerWorld world)) return Collections.emptyList();
        List<DiceEntity> result = new ArrayList<>();
        for (UUID uuid : this.getLinkedDice()) {
            if (uuid.equals(this.getUuid())) continue;
            if (world.getEntity(uuid) instanceof DiceEntity dice && dice != this) {
                result.add(dice);
            }
        }
        return result;
    }

    /** The steps of the finished roll of this throw (negative: backward). */
    public int getTotalRolledValue() {
        return getOutcome().steps();
    }
}
