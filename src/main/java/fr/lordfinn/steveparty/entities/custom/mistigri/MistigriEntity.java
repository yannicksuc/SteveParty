package fr.lordfinn.steveparty.entities.custom.mistigri;

import fr.lordfinn.steveparty.effect.ModEffects;
import fr.lordfinn.steveparty.entities.BoardActor;
import fr.lordfinn.steveparty.entities.FollowsOwnerAnywhere;
import fr.lordfinn.steveparty.entities.PetTeleports;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
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
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Mistigri: a big all-black witch's tomcat, a capricious tyrant who hands out bad luck. Never spawns by
 * itself: a black cat by a witch hut becomes one when a die bound to it lands on a 1 or a 0 ({@link MistigriSummoning}).
 * <ul>
 *     <li><b>Bad luck</b>: crossing in front of him (his path, a few blocks ahead) gives Bad Luck for a while
 *     ({@link MistigriBadLuck}). He never hurts anyone himself.</li>
 *     <li><b>A cat</b>: knocks the items off item frames and the books out of chiseled bookshelves, falls asleep
 *     sprawled on chests (they won't open under him; he sleeps there until woken: raw fish, a blow, his owner's hand),
 *     follows a player about staring at them, grooms, stretches, yawns, naps.</li>
 *     <li><b>Angry</b> ({@link #isAngry}): hit, or a wolf too close. Arched back, hackles and tail bristling, he hisses;
 *     whoever hit him gets a long Bad Luck. Raw fish calms him.</li>
 *     <li><b>Fish</b>: each raw fish fed to a wild one counts; enough of them ({@link #fishToTame}) tame him.</li>
 *     <li><b>Tamed</b>: follows his owner like the mod's other pets (through teleports, each its own place:
 *     FollowsOwnerAnywhere, PetSlots), sits on their word, gives them Luck nearby and makes the monsters around him
 *     miss now and then ({@link MistigriBadLuck}). Crossing him never brings his owner bad luck.</li>
 *     <li><b>Loot</b>: the Mistigri's Die, a classic die whose faces are the cursed 1, 2 and 3 (loot table entities/mistigri).</li>
 * </ul>
 * A board space's Mistigri ({@link #isBoardActor}) does none of that: invulnerable, moved by the board, never saved.
 * His acts (grooming, a swat...) are a tracked {@link Action} played by the client's animation controller; a command
 * may ask for one ({@code Action:"groom"} in his data: groom, stretch, yawn, swat, eat, leap, summon), as it may set
 * {@code Angry} (ticks), {@code Loafing} or {@code Sitting}: building blocks for players' mini-games.
 */
public class MistigriEntity extends TameableEntity implements GeoEntity, FollowsOwnerAnywhere, BoardActor {
    public static final float WIDTH = 1.2f, HEIGHT = 1.5f;
    public static final double MAX_HEALTH = 30.0;
    /** How long he stays angry (ticks), and the Bad Luck given to whoever hit him. */
    public static final int ANGRY_TICKS = 140, HIT_UNLUCK_TICKS = 2400;
    /** A wolf this close makes him angry (blocks). */
    public static final double WOLF_RANGE = 6.0;
    /** Health a raw fish gives back to a tamed one. */
    public static final float FISH_HEAL = 4.0f;
    /** A wild one needs this many raw fish (at least), up to {@link #FISH_TO_TAME_MAX}: "lots of fish". */
    public static final int FISH_TO_TAME_MIN = 6, FISH_TO_TAME_MAX = 10;

    /** What he is doing for a moment: an act played once by the animations. */
    public enum Action {
        NONE("", 0), GROOM("groom", 64), STRETCH("stretch", 52), YAWN("yawn", 44), SWAT("swat", 22), EAT("eat", 32),
        LEAP("leap", 20), SUMMON("summon", 52),
        // playing (an acorn, a Glandouille): a paw tap, a toss in the air, a pounce
        BAT("bat", 18), TOSS("toss", 26), POUNCE("pounce", 26);

        public final String animation;
        /** Its length (ticks): the animation's. */
        public final int ticks;

        Action(String animation, int ticks) {
            this.animation = animation;
            this.ticks = ticks;
        }

        static Action byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : NONE;
        }
    }

    private static final TrackedData<Integer> ACTION =
            DataTracker.registerData(MistigriEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> ANGRY =
            DataTracker.registerData(MistigriEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    /** Lying in a loaf (on a chest, napping). */
    private static final TrackedData<Boolean> LOAFING =
            DataTracker.registerData(MistigriEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    /** Asleep on a chest (it won't open under him), sprawled: until woken (fish, a blow, his owner's hand). */
    private static final TrackedData<Boolean> ON_CHEST =
            DataTracker.registerData(MistigriEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    /** Staring at someone (following them about). */
    /** Playing: lying in wait (stalking) or rolled on his back (see {@link PlayPose}). */
    private static final TrackedData<Integer> PLAY_POSE =
            DataTracker.registerData(MistigriEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> STARING =
            DataTracker.registerData(MistigriEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private int actionTicks;
    private int angryTicks;
    private int fishFed;
    private int fishToTame;
    private boolean boardActor;
    /** The chest he sits on, while loafing on it. */
    private @Nullable BlockPos chest;
    /** The bad luck's watch of the players in front of him (see MistigriBadLuck). */
    final Map<UUID, MistigriBadLuck.Watch> watches = new HashMap<>();
    /** Each player's next possible bad luck (world time). */
    final Map<UUID, Long> badLuckCooldowns = new HashMap<>();
    /** The next time he may knock something off (world time): not every minute. */
    long nextSwatTime;
    /** What his swat in progress knocks off, and in how many ticks (the paw landing). */
    private @Nullable ItemFrameEntity swatFrame;
    private @Nullable BlockPos swatShelf;
    private int swatIn;
    /** Playing (server side): the play goals' flag. */
    private boolean playing;
    /** No new play before (world time): after a game, a while without. */
    long nextPlayTime;

    /** His swat knocks {@code frame}'s item (or a book out of {@code shelf}) off in {@code ticks}. */
    void swatAt(@Nullable ItemFrameEntity frame, @Nullable BlockPos shelf, int ticks) {
        swatFrame = frame;
        swatShelf = shelf;
        swatIn = ticks;
    }

    private void tickSwat(ServerWorld world) {
        if (swatIn <= 0 || --swatIn > 0) return;
        if (swatFrame != null && swatFrame.isAlive()) MistigriGoals.knock(world, swatFrame);
        else if (swatShelf != null) MistigriGoals.knock(world, swatShelf);
        swatFrame = null;
        swatShelf = null;
        nextSwatTime = world.getTime() + MathHelper.nextInt(random, MistigriGoals.SWAT_COOLDOWN_MIN, MistigriGoals.SWAT_COOLDOWN_MAX);
    }

    public MistigriEntity(EntityType<? extends TameableEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = 10;
    }

    public static DefaultAttributeContainer.Builder setAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.26)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 20.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new SitGoal(this));
        this.goalSelector.add(1, new MistigriPlay.PlayWithAcorn(this));
        this.goalSelector.add(2, new MistigriGoals.Hold(this));
        this.goalSelector.add(3, new MistigriGoals.FollowOwner(this));
        this.goalSelector.add(4, new MistigriGoals.KnockOff(this));
        this.goalSelector.add(5, new MistigriGoals.SitOnChest(this));
        this.goalSelector.add(6, new MistigriGoals.StareFollow(this));
        this.goalSelector.add(6, new MistigriPlay.CatAndMouse(this));
        this.goalSelector.add(7, new MistigriGoals.IdleActs(this));
        this.goalSelector.add(8, new WanderAroundFarGoal(this, 0.8) {
            @Override
            public boolean canStart() {
                return isFree() && super.canStart();
            }
        });
        this.goalSelector.add(9, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(10, new LookAroundGoal(this));
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(ACTION, 0);
        builder.add(ANGRY, false);
        builder.add(LOAFING, false);
        builder.add(ON_CHEST, false);
        builder.add(STARING, false);
        builder.add(PLAY_POSE, 0);
    }

    // ---------------------------------------------------------------- state

    public Action getAction() {
        return Action.byId(this.dataTracker.get(ACTION));
    }

    /** Starts an act (its animation plays on the clients); he stands still meanwhile. */
    public void act(Action action) {
        this.dataTracker.set(ACTION, action.ordinal());
        actionTicks = action.ticks;
        if (action != Action.NONE) {
            getNavigation().stop();
            setLoafing(false);
        }
    }

    public boolean isActing() {
        return getAction() != Action.NONE;
    }

    public boolean isAngry() {
        return this.dataTracker.get(ANGRY);
    }

    /** Puffs up for {@code ticks} (0: calms down). */
    public void setAngry(int ticks) {
        boolean was = isAngry();
        angryTicks = ticks;
        this.dataTracker.set(ANGRY, ticks > 0);
        if (ticks > 0) {
            getNavigation().stop();
            setLoafing(false);
            if (!was) playSound(ModSounds.MISTIGRI_ANGRY, 1.0f, 1.0f);
        }
    }

    public boolean isLoafing() {
        return this.dataTracker.get(LOAFING);
    }

    public void setLoafing(boolean loafing) {
        this.dataTracker.set(LOAFING, loafing);
        if (!loafing) {
            chest = null;
            this.dataTracker.set(ON_CHEST, false);
        }
    }

    /**
     * Lies down: a loaf where he is ({@code pos} null, a nap), or asleep sprawled on the chest at {@code pos} (it won't
     * open under him: MistigriBadLuck), until woken.
     */
    public void loafOn(@Nullable BlockPos pos) {
        setLoafing(true);
        chest = pos == null ? null : pos.toImmutable();
        this.dataTracker.set(ON_CHEST, pos != null);
    }

    /** Asleep on a chest (both sides). */
    public boolean isAsleepOnChest() {
        return isLoafing() && this.dataTracker.get(ON_CHEST);
    }

    /** Whether he stands on the block at {@code pos} (a chest is lower than a block: his feet are in its cell). */
    public boolean standsOn(BlockPos pos) {
        return BlockPos.ofFloored(getX(), getY() - 0.3, getZ()).equals(pos);
    }

    /** The chest he sits on (null if none). */
    public @Nullable BlockPos chest() {
        return isLoafing() ? chest : null;
    }

    /** A held playing posture (its animation loops). */
    public enum PlayPose {
        NONE, STALK, ON_BACK
    }

    public PlayPose getPlayPose() {
        int id = this.dataTracker.get(PLAY_POSE);
        return id >= 0 && id < PlayPose.values().length ? PlayPose.values()[id] : PlayPose.NONE;
    }

    void setPlayPose(PlayPose pose) {
        this.dataTracker.set(PLAY_POSE, pose.ordinal());
    }

    /** Playing (an acorn, cat and mouse with a Glandouille): see MistigriPlay. */
    public boolean isPlaying() {
        return playing;
    }

    void setPlaying(boolean playing) {
        this.playing = playing;
        if (!playing) setPlayPose(PlayPose.NONE);
    }

    /** Forgets what he was up to: no more anger, no swat on its way, up from his loaf (an acorn caught his eye). */
    void distract() {
        setAngry(0);
        swatAt(null, null, 0);
        setLoafing(false);
        setStaring(false);
    }

    public boolean isStaring() {
        return this.dataTracker.get(STARING);
    }

    void setStaring(boolean staring) {
        this.dataTracker.set(STARING, staring);
    }

    /** Free to do as he likes: alive, not the board's, not angry, not acting, not told to sit, not asleep on a chest. */
    public boolean isFree() {
        return isAlive() && !boardActor && !isAngry() && !isActing() && !isSitting() && !isLeashed() && !isAsleepOnChest()
                && !playing;
    }

    public int fishFed() {
        return fishFed;
    }

    /** How many raw fish a wild one needs. */
    public int fishToTame() {
        if (fishToTame <= 0) fishToTame = MathHelper.nextInt(random, FISH_TO_TAME_MIN, FISH_TO_TAME_MAX);
        return fishToTame;
    }

    // ---------------------------------------------------------------- board actor

    /** A Mistigri of a board space: invulnerable, no will of his own, never saved. */
    @Override
    public boolean isBoardActor() {
        return boardActor;
    }

    @Override
    public void setBoardActor() {
        this.boardActor = true;
    }

    @Override
    public boolean shouldSave() {
        return !boardActor && super.shouldSave();
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false; // a summoned one is rare: he stays
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        if (getWorld() instanceof ServerWorld world) tickServer(world);
    }

    /** The board's Mistigri: his acts still end (the board picks them), nothing else of his own. */
    private void tickServer(ServerWorld world) {
        if (actionTicks > 0 && --actionTicks == 0) this.dataTracker.set(ACTION, Action.NONE.ordinal());
        if (angryTicks > 0 && --angryTicks == 0) this.dataTracker.set(ANGRY, false);
        if (boardActor || !isAlive()) return;
        tickSwat(world);
        if (age % 2 == 0) MistigriBadLuck.tickCrossings(world, this);
        tickFollow(isTamed());
        if (age % 20 == 5) {
            if (isTamed()) MistigriBadLuck.giveLuck(world, this);
            if (!isAngry() && wolfNearby(world)) setAngry(ANGRY_TICKS);
        }
        // got off his chest (pushed, the chest broken): no longer sitting on it
        if (chest != null && !standsOn(chest)) chest = null;
        // asleep on his chest: a slow purr and a note now and then, his snore
        if (chest != null && age % 50 == 0) {
            playSound(ModSounds.MISTIGRI_PURR, 0.35f, 0.85f);
            world.spawnParticles(ParticleTypes.NOTE, getX(), getY() + 0.9, getZ(), 1, 0.2, 0.1, 0.2, 0.0);
        }
        if (isAngry() && age % 32 == 0) playSound(ModSounds.MISTIGRI_HISS, 1.0f, 0.95f + random.nextFloat() * 0.1f);
        if (isAngry() && age % 4 == 0) {
            world.spawnParticles(ParticleTypes.SMOKE, getX(), getY() + HEIGHT + 0.1, getZ(), 1, 0.3, 0.05, 0.3, 0.005);
        }
    }

    private boolean wolfNearby(ServerWorld world) {
        return !world.getEntitiesByClass(WolfEntity.class, getBoundingBox().expand(WOLF_RANGE),
                wolf -> wolf.isAlive() && wolf.squaredDistanceTo(this) < WOLF_RANGE * WOLF_RANGE).isEmpty();
    }

    // ---------------------------------------------------------------- hits

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient) return false;
        if (boardActor && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        boolean hurt = super.damage(source, amount);
        if (hurt && !boardActor && isAlive()) {
            setAngry(ANGRY_TICKS);
            setSitting(false);
            if (source.getAttacker() instanceof LivingEntity attacker && !isOwner(attacker)) {
                attacker.addStatusEffect(new StatusEffectInstance(ModEffects.BAD_LUCK, HIT_UNLUCK_TICKS, 0), this);
                getLookControl().lookAt(attacker);
            }
        }
        return hurt;
    }

    @Override
    protected boolean shouldDropLoot() {
        return !boardActor && super.shouldDropLoot();
    }

    // ---------------------------------------------------------------- fish, taming, his owner's word

    public static boolean isFish(ItemStack stack) {
        return stack.isIn(ItemTags.CAT_FOOD);
    }

    /**
     * Raw fish: calms an angry one; a wild one counts it (enough of them tame him); heals a tamed one. His owner's empty
     * hand: sits down or gets up.
     */
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (boardActor || !isAlive()) return ActionResult.PASS;
        if (isFish(stack)) {
            boolean useful = isAngry() || !isTamed() || isLoafing() || getHealth() < getMaxHealth();
            if (!useful) return ActionResult.PASS;
            if (getWorld() instanceof ServerWorld world) {
                stack.decrementUnlessCreative(1, player);
                feed(world, player);
            }
            return ActionResult.success(getWorld().isClient);
        }
        if (hand == Hand.MAIN_HAND && stack.isEmpty() && isTamed() && isOwner(player)) {
            if (!getWorld().isClient) {
                setSitting(!isSitting());
                setLoafing(false);
                getNavigation().stop();
                playSound(isSitting() ? ModSounds.MISTIGRI_PURR : ModSounds.MISTIGRI_MEOW, 0.8f, 1.0f);
            }
            return ActionResult.success(getWorld().isClient);
        }
        return ActionResult.PASS;
    }

    /** One raw fish eaten from {@code player}'s hand. */
    public void feed(ServerWorld world, PlayerEntity player) {
        boolean calmed = isAngry();
        setAngry(0);
        act(Action.EAT);
        playSound(ModSounds.MISTIGRI_EAT, 1.0f, 1.0f);
        getLookControl().lookAt(player);
        if (isTamed()) {
            heal(FISH_HEAL);
            world.spawnParticles(ParticleTypes.HEART, getX(), getBodyY(0.9), getZ(), 3, 0.3, 0.2, 0.3, 0.02);
            return;
        }
        if (calmed) { // calming him is all a fish does then
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getBodyY(0.9), getZ(), 6, 0.4, 0.2, 0.4, 0.02);
            return;
        }
        if (++fishFed >= fishToTame()) tame(player);
        else world.spawnParticles(ParticleTypes.SMOKE, getX(), getBodyY(0.9), getZ(), 6, 0.3, 0.2, 0.3, 0.02);
    }

    /** Tamed by {@code player}: theirs from now on (hearts, a purr). */
    public void tame(PlayerEntity player) {
        setOwner(player);
        setSitting(false);
        setAngry(0);
        setStaring(false);
        getNavigation().stop();
        if (getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.HEART, getX(), getBodyY(0.9), getZ(), 9, 0.4, 0.3, 0.4, 0.02);
            world.sendEntityStatus(this, (byte) 7);
            if (player instanceof ServerPlayerEntity serverPlayer) {
                Criteria.TAME_ANIMAL.trigger(serverPlayer, this);
            }
        }
        playSound(ModSounds.MISTIGRI_PURR, 1.0f, 1.0f);
        PetTeleports.remember(this);
    }

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return false; // fish is handled in interactMob: no Mistigri kittens
    }

    @Override
    public @Nullable PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
        return null;
    }

    @Override
    public boolean canBreedWith(AnimalEntity other) {
        return false;
    }

    // ---------------------------------------------------------------- with his owner anywhere (PetTeleports)

    @Override
    public @Nullable UUID followedOwner() {
        return getOwnerUuid();
    }

    /** Going along: tamed by them, not told to sit, not on a lead or riding, not the board's. */
    @Override
    public boolean goesWithOwner(ServerPlayerEntity owner) {
        return followsFreely(owner) && isTamed() && !isSitting() && !boardActor;
    }

    /** Far behind: recreated by them (PetTeleports) rather than moved in place, which could leave him unseen. */
    @Override
    public void tryTeleportToOwner() {
        if (!catchUpFar(getOwner())) super.tryTeleportToOwner();
    }

    // ---------------------------------------------------------------- save

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (fishFed > 0) nbt.putInt("FishFed", fishFed);
        if (fishToTame > 0) nbt.putInt("FishToTame", fishToTame);
        if (angryTicks > 0) nbt.putInt("Angry", angryTicks);
        if (chest() != null) nbt.putLong("Chest", chest.asLong());
        else if (isLoafing()) nbt.putBoolean("Loafing", true);
        if (nextSwatTime > 0) nbt.putLong("NextSwat", nextSwatTime);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        fishFed = nbt.getInt("FishFed");
        fishToTame = nbt.getInt("FishToTame");
        angryTicks = nbt.getInt("Angry");
        this.dataTracker.set(ANGRY, angryTicks > 0);
        if (nbt.contains("Chest")) loafOn(BlockPos.fromLong(nbt.getLong("Chest")));
        else setLoafing(nbt.getBoolean("Loafing"));
        nextSwatTime = nbt.getLong("NextSwat");
        // an act asked by a command (never saved): /data merge entity @e[type=steveparty:mistigri,limit=1] {Action:"groom"}
        if (nbt.contains("Action")) {
            for (Action action : Action.values()) {
                if (action != Action.NONE && action.animation.equals(nbt.getString("Action"))) act(action);
            }
        }
    }

    // ---------------------------------------------------------------- sounds

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        if (boardActor) return null;
        if (isAngry()) return ModSounds.MISTIGRI_HISS;
        if (isTamed() && (isLoafing() || isInSittingPose()) && random.nextInt(3) == 0) return ModSounds.MISTIGRI_PURR;
        return random.nextInt(4) == 0 ? ModSounds.MISTIGRI_MEOW : ModSounds.MISTIGRI_AMBIENT;
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return 240;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.MISTIGRI_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MISTIGRI_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // a cat's padded step: nothing
    }

    // ---------------------------------------------------------------- animations

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("sit");
    private static final RawAnimation LOAF = RawAnimation.begin().thenLoop("loaf");
    private static final RawAnimation SLEEP = RawAnimation.begin().thenLoop("sleep");
    private static final RawAnimation STARE = RawAnimation.begin().thenLoop("stare");
    private static final RawAnimation STALK = RawAnimation.begin().thenLoop("stalk");
    private static final RawAnimation ON_BACK = RawAnimation.begin().thenLoop("on_back");
    private static final RawAnimation ANGRY_ANIM = RawAnimation.begin().thenPlay("angry_in").thenLoop("angry");
    private static final Map<Action, RawAnimation> ACTS = new EnumMap<>(Action.class);

    static {
        for (Action action : Action.values()) {
            if (action != Action.NONE) ACTS.put(action, RawAnimation.begin().thenPlay(action.animation));
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::animate));
    }

    private PlayState animate(AnimationState<MistigriEntity> state) {
        Action action = getAction();
        if (action != Action.NONE) return state.setAndContinue(ACTS.get(action));
        PlayPose play = getPlayPose();
        if (play == PlayPose.STALK) return state.setAndContinue(STALK);
        if (play == PlayPose.ON_BACK) return state.setAndContinue(ON_BACK);
        if (isAngry()) return state.setAndContinue(ANGRY_ANIM);
        if (isInSittingPose()) return state.setAndContinue(SIT);
        if (isAsleepOnChest()) return state.setAndContinue(SLEEP);
        if (isLoafing()) return state.setAndContinue(LOAF);
        if (state.isMoving()) return state.setAndContinue(WALK);
        if (isStaring()) return state.setAndContinue(STARE);
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** Where he looks from, for his bad luck (his body's facing, a flat unit vector). */
    Vec3d facing() {
        float yaw = getBodyYaw() * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
    }
}
