package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.goals.FollowOwnerWhileFlyingGoal;
import fr.lordfinn.steveparty.entities.custom.goals.LumaHoverGoal;
import fr.lordfinn.steveparty.entities.custom.goals.MulaBodyControl;
import fr.lordfinn.steveparty.entities.custom.goals.MulaSitGoal;
import fr.lordfinn.steveparty.entities.custom.goals.SimpleFlyingMoveControl;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.TokenItem;
import fr.lordfinn.steveparty.items.custom.TokenizerWandItem;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.control.BodyControl;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.animation.AnimationState;

import java.util.*;

public class MulaEntity extends TameableEntity implements GeoEntity {

	private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

	// ------------------------------------------------------------------------------------------ animations
	// Authored in the art sources (it writes mula.animation.json and the .bbmodel animations).
	public static final String MAIN_CONTROLLER = "main_controller";
	public static final String BLINK_CONTROLLER = "blink_controller";
	/**
	 * Blend between two animations (ticks), eased in and out. The generator writes the timeline instructions this much
	 * later than they must happen, because GeckoLib 4.7.1 fires them that early (keep both in sync).
	 */
	public static final int MAIN_TRANSITION_TICKS = 8;
	/** Channels an animation doesn't drive go back to rest over this many ticks (GeckoLib's default 1 snapped). */
	private static final double BONE_RESET_TICKS = 6;

	protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
	protected static final RawAnimation FLY_ANIM = RawAnimation.begin().thenLoop("fly");
	protected static final RawAnimation SIT_ANIM = RawAnimation.begin().thenLoop("sit");
	protected static final RawAnimation EXPLODE_ANIM = RawAnimation.begin().thenPlay("explode");
	protected static final RawAnimation CELEBRATE_ANIM = RawAnimation.begin().thenPlay("celebrate");
	protected static final RawAnimation NO_ANIM = RawAnimation.begin().thenPlay("no");
	protected static final RawAnimation BLINK_ANIM = RawAnimation.begin().thenPlay("blink");
	protected static final RawAnimation BLINK_DOUBLE_ANIM = RawAnimation.begin().thenPlay("blink_double");

	/** Length of the triggered animations, in ticks (json length + the blend in). */
	private static final int EXPLODE_TICKS = 84 + MAIN_TRANSITION_TICKS, CELEBRATE_TICKS = 20 + MAIN_TRANSITION_TICKS,
			NO_TICKS = 19 + MAIN_TRANSITION_TICKS;

	/**
	 * The little random "character" animations. Chosen and started by the server (see {@link #tickEmotes}) with
	 * {@code triggerAnim}, so every player sees the same Mula do the same thing at the same time; one small packet every
	 * 8 to 25 s per Mula (none when no player is near).
	 */
	public enum Emote {
		LOOK_AROUND("look_around", 2.6f, 3, false, false),
		HEAD_TILT("head_tilt", 1.9f, 3, false, false),
		HAPPY_HOP("happy_hop", 1.5f, 2, false, false),
		WIGGLE("wiggle", 1.3f, 2, false, false),
		TWIRL("twirl", 1.6f, 2, false, true),
		FLIP("flip", 1.8f, 1, false, false),
		YAWN("yawn", 2.8f, 3, true, false),
		SLEEPY_NOD("sleepy_nod", 3.2f, 2, true, false);

		final String animName;
		final RawAnimation animation;
		/** Ticks during which nothing else is started: the animation plus its blend in and out. */
		final int ticks;
		final int weight;
		final boolean whileSitting, whileMoving;

		Emote(String animName, float seconds, int weight, boolean whileSitting, boolean whileMoving) {
			this.animName = animName;
			this.animation = RawAnimation.begin().thenPlay(animName);
			this.ticks = Math.round(seconds * 20) + 2 * MAIN_TRANSITION_TICKS;
			this.weight = weight;
			this.whileSitting = whileSitting;
			this.whileMoving = whileMoving;
		}
	}

	private static final Emote[] EMOTES = Emote.values();
	/** A random animation every 8 to 25 s. */
	private static final int EMOTE_MIN_TICKS = 160, EMOTE_RANDOM_TICKS = 340;
	/** Only when a player can see it (blocks). */
	private static final double EMOTE_AUDIENCE_RANGE = 48;

	/** Server: ticks left before the next random animation (-1: not drawn yet, so every Mula starts at its own time). */
	private int emoteCooldown = -1;
	/** Server: ticks left of the triggered animation being played (random, fed, refused, burst); 0 when none. */
	private int specialAnimTicks = 0;
	/** Server: the random animation being played, to cut it when the Mula is hit or told to sit. */
	private @Nullable Emote currentEmote = null;
	private @Nullable Emote lastEmote = null;

	/** Client: the float layer and the fly / hover state (see {@link MulaMotion}). */
	private final MulaMotion motion;
	/** Client: ticks before the next blink (each client blinks on its own: purely cosmetic, never synced). */
	private int blinkCooldown = 40;
	/** Client: last age at which the renderer spawned its particles (at most once per tick, only when drawn). */
	public int lastEffectsAge = -1;

	// ------------------------------------------------------------------------------------------ state

	private static final TrackedData<Integer> VARIANT =
			DataTracker.registerData(MulaEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> HUNGER =
			DataTracker.registerData(MulaEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private int eatCooldown = 0;

	// Feedable items per variant
	private static final Map<MulaVariant, Map<Item, Integer>> FEED_ITEMS = new HashMap<>();

	static {
		// BLUE variant: blue-ish vanilla items
		FEED_ITEMS.put(MulaVariant.BLUE, Map.of(
				net.minecraft.item.Items.LAPIS_LAZULI, 10,
				net.minecraft.item.Items.BLUE_DYE, 5,
				net.minecraft.item.Items.PRISMARINE_SHARD, 8
		));

		// RED variant: red-ish items
		FEED_ITEMS.put(MulaVariant.RED, Map.of(
				net.minecraft.item.Items.RED_DYE, 10,
				Items.POPPY, 8,
				net.minecraft.item.Items.REDSTONE, 15
		));

		// GREEN variant: green-ish items
		FEED_ITEMS.put(MulaVariant.GREEN, Map.of(
				net.minecraft.item.Items.GREEN_DYE, 10,
				Items.CACTUS, 8,
				net.minecraft.item.Items.EMERALD, 20
		));

		// YELLOW variant: yellow-ish items
		FEED_ITEMS.put(MulaVariant.YELLOW, Map.of(
				net.minecraft.item.Items.YELLOW_DYE, 10,
				Items.GOLD_INGOT, 15,
				net.minecraft.item.Items.HONEYCOMB, 8
		));

		// PURPLE variant: purple-ish items
		FEED_ITEMS.put(MulaVariant.PURPLE, Map.of(
				net.minecraft.item.Items.PURPLE_DYE, 10,
				net.minecraft.item.Items.AMETHYST_SHARD, 15,
				net.minecraft.item.Items.CHORUS_FRUIT, 8
		));

		FEED_ITEMS.put(MulaVariant.BLACK, Map.of(
				net.minecraft.item.Items.INK_SAC, 15,
				net.minecraft.item.Items.COAL, 20,
				Items.NETHERITE_INGOT, 100
		));
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient) {
			tickClientAnimation();
			return;
		}
		if (eatCooldown > 0) eatCooldown--;
		tickEmotes();
	}

	@Override
	public float getScaleFactor() {
		float baseScale = this.isBaby() ? 0.5f : 1.0f;
		float hungerScale = 1.0f + ((3f - 1.0f) * ((float)this.getHunger() / MAX_HUNGER));
		return baseScale * hungerScale;
	}

	private static final int MAX_HUNGER = 100;
	/** 1 chance in TAMING_CHANCE to tame the Mula with each star fragment of its colour. */
	private static final int TAMING_CHANCE = 3;

	public MulaEntity(EntityType<MulaEntity> entityType, World world) {
		super(entityType, world);
		this.setNoGravity(true);
		this.moveControl = new SimpleFlyingMoveControl(this, 10f);
		this.motion = new MulaMotion(this.getId());
	}

	@Override protected void initGoals() {
		// Sitting (owner's order) wins; following the owner stops by itself while sitting (cannotFollowOwner);
		// idle hovering only runs (and keeps running) without an owner
		this.goalSelector.add(0, new MulaSitGoal(this));
		this.goalSelector.add(0, new FollowOwnerWhileFlyingGoal(this, 1.0, 3.0f, 20.0f));
		this.goalSelector.add(1, new LumaHoverGoal(this, 0.2, 1.5, 6.0)); super.initGoals();
	}

	/** Turns its body smoothly (vanilla snaps it after 10 ticks without moving). */
	@Override
	protected BodyControl createBodyControl() {
		return new MulaBodyControl(this);
	}

	public static DefaultAttributeContainer.Builder setAttributes() {
		return LivingEntity.createLivingAttributes()
				.add(EntityAttributes.MAX_HEALTH, 30.0D)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.25D)
				.add(EntityAttributes.FOLLOW_RANGE, 20.0D)
				.add(EntityAttributes.FLYING_SPEED, 0.3D);
	}

	@Override protected EntityNavigation createNavigation(World world) {
		BirdNavigation nav = new BirdNavigation(this, world);
		nav.setCanPathThroughDoors(false);
		nav.setCanSwim(false);
		return nav;
	}

	@Override
	public boolean isCollidable() {
		return true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(VARIANT, 0);
		builder.add(HUNGER, 0);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Variant", this.getVariant().getId());
		nbt.putInt("Hunger", this.getHunger());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.setVariant(MulaVariant.byId(nbt.getInt("Variant")));
		this.setHunger(nbt.getInt("Hunger"));
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData) {
		this.setVariant(MulaVariant.getRandomVariant());
		this.setHunger(0);
		return super.initialize(world, difficulty, spawnReason, entityData);
	}

	@Override
	public @Nullable PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
		return null;
	}

	// -------------------
	// Hunger management
	// -------------------
	public int getHunger() {
		return this.dataTracker.get(HUNGER);
	}

	public void setHunger(int hunger) {
		this.dataTracker.set(HUNGER, Math.min(hunger, MAX_HUNGER));
	}

	// -------------------
	// Feeding interaction
	// -------------------
	@Override
	public ActionResult interactMob(PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);

		// Tools acting on entities (tokenizer wand, token, name tag, lead) keep working on a Mula
		if (stack.getItem() instanceof TokenizerWandItem || stack.getItem() instanceof TokenItem
				|| stack.isOf(Items.NAME_TAG) || stack.isOf(Items.LEAD)) {
			return ActionResult.PASS;
		}

		// Taming: sneak + right-click with a star fragment of the Mula's own colour
		if (player.isSneaking() && !this.isTamed() && stack.isOf(this.getVariant().getFragmentItem())) {
			if (!this.getWorld().isClient) {
				tryTame(player, stack);
			}
			return ActionResult.SUCCESS;
		}

		// Sit / stand: the owner right-clicks a tamed Mula with an empty hand or anything it doesn't eat
		// (its food keeps feeding it; sneak + fragment only tames an untamed Mula, so a tamed one just toggles)
		if (this.isTamed() && this.isOwner(player) && !isMulaFood(stack)) {
			if (!this.getWorld().isClient) {
				toggleSitting();
			}
			return ActionResult.SUCCESS;
		}

		// Still chewing, or not its food: shakes its head. Played from the server only: the client used to play it
		// too, then again when the server's order came back, which restarted it halfway (a visible hiccup).
		Map<Item, Integer> allowedItems = FEED_ITEMS.get(this.getVariant());
		if (eatCooldown > 0 || !isMulaFood(stack)) {
			if (!this.getWorld().isClient) {
				playSpecial("no", NO_TICKS);
			}
			return ActionResult.SUCCESS;
		}

		// Correct item
		int hungerValue = allowedItems.get(stack.getItem());
		int newHunger = Math.min(getHunger() + hungerValue, MAX_HUNGER);
		setHunger(newHunger);

		if (!player.getWorld().isClient) {
			Text message = Text.literal(String.format(
					"Feed level: %d/%d - %s: +%d",
					getHunger(), MAX_HUNGER,
					stack.getName().getString(),
					hungerValue
			));
			message = message.copy().styled(style -> style.withColor(this.getVariant().getColor()));
			player.sendMessage(message, true);

			stack.decrementUnlessCreative(1, player);

			// Explode if max hunger
			if (getHunger() >= MAX_HUNGER) {
				playSpecial("explode", EXPLODE_TICKS);
				setHunger(0);
				dropFragmentStars(64);
			} else {
				playSpecial("celebrate", CELEBRATE_TICKS);
			}
			// Set cooldown for 1 second (20 ticks)
			eatCooldown = 20;
		}

		return ActionResult.SUCCESS;
	}

	/** Server: plays a triggered animation (replacing a random one) and holds the random ones back meanwhile. */
	private void playSpecial(String animName, int ticks) {
		triggerAnim(MAIN_CONTROLLER, animName);
		currentEmote = null;
		specialAnimTicks = ticks;
	}

	/** @return true if this Mula eats this item (depends on its colour). */
	public boolean isMulaFood(ItemStack stack) {
		return !stack.isEmpty() && FEED_ITEMS.getOrDefault(this.getVariant(), Map.of()).containsKey(stack.getItem());
	}

	/** Owner's order, like vanilla wolves: the state is saved by {@link TameableEntity} ("Sitting"). */
	public void toggleSitting() {
		this.setSitting(!this.isSitting());
		this.jumping = false;
		this.navigation.stop();
		this.setTarget(null);
		stopEmote();
	}

	/** Like vanilla wolves: consumes one fragment, 1 in {@value #TAMING_CHANCE} chance, hearts or smoke. */
	private void tryTame(PlayerEntity player, ItemStack stack) {
		stack.decrementUnlessCreative(1, player);
		if (this.random.nextInt(TAMING_CHANCE) == 0) {
			this.setOwner(player);
			this.navigation.stop();
			this.getWorld().sendEntityStatus(this, EntityStatuses.ADD_POSITIVE_PLAYER_REACTION_PARTICLES);
		} else {
			this.getWorld().sendEntityStatus(this, EntityStatuses.ADD_NEGATIVE_PLAYER_REACTION_PARTICLES);
		}
	}

	/** Tamed: a puff of star dust with the hearts. */
	@Override
	public void handleStatus(byte status) {
		super.handleStatus(status);
		if (status == EntityStatuses.ADD_POSITIVE_PLAYER_REACTION_PARTICLES) {
			sparkleRing(14, 0.09);
			chime(1.6f);
		}
	}

	private void dropFragmentStars(int count) {
		if (this.getWorld().isClient) return;
		Item fragmentItem = this.getVariant().getFragmentItem();
		for (int i = 0; i < count; i++) {
			this.dropItem((ServerWorld) this.getWorld(), fragmentItem);
		}
	}

	// ------------------------------------------------------------------------------------------ random animations

	/** Server, every tick: a random character animation from time to time, when nothing else is going on. */
	private void tickEmotes() {
		if (specialAnimTicks > 0 && --specialAnimTicks == 0) {
			currentEmote = null;
		}
		if (emoteCooldown < 0) {
			emoteCooldown = EMOTE_MIN_TICKS + this.random.nextInt(EMOTE_RANDOM_TICKS);
			return;
		}
		if (--emoteCooldown > 0) return;
		emoteCooldown = EMOTE_MIN_TICKS + this.random.nextInt(EMOTE_RANDOM_TICKS);
		if (!canEmote()) return;
		boolean sitting = this.isInSittingPose();
		boolean moving = this.getVelocity().lengthSquared() > 0.05 * 0.05;
		if (moving && this.random.nextBoolean()) return; // rarer while flying around
		Emote emote = pickEmote(sitting, moving);
		if (emote == null) return;
		triggerAnim(MAIN_CONTROLLER, emote.animName);
		currentEmote = lastEmote = emote;
		specialAnimTicks = emote.ticks;
	}

	/** Never over another triggered animation, while hurt, eating, carried, leashed or used as a board token. */
	private boolean canEmote() {
		return this.isAlive() && specialAnimTicks == 0 && eatCooldown == 0 && this.hurtTime == 0
				&& !this.hasVehicle() && !this.hasPassengers() && !this.isLeashed()
				&& !((Object) this instanceof TokenizedEntityInterface token && token.steveparty$isTokenized())
				&& this.getWorld().getClosestPlayer(this, EMOTE_AUDIENCE_RANGE) != null;
	}

	private @Nullable Emote pickEmote(boolean sitting, boolean moving) {
		int total = 0;
		for (Emote e : EMOTES) {
			if (fits(e, sitting, moving)) total += e.weight;
		}
		if (total == 0) return null;
		for (int attempt = 0; attempt < 2; attempt++) {
			int roll = this.random.nextInt(total);
			for (Emote e : EMOTES) {
				if (!fits(e, sitting, moving)) continue;
				roll -= e.weight;
				if (roll < 0) {
					if (e != lastEmote || attempt == 1) return e; // avoid the same one twice in a row
					break;
				}
			}
		}
		return null;
	}

	private static boolean fits(Emote e, boolean sitting, boolean moving) {
		if (sitting) return e.whileSitting;
		if (e.whileSitting) return false;
		return !moving || e.whileMoving;
	}

	/** Server: cuts the random animation (the controller then blends back to idle / fly / sit). */
	private void stopEmote() {
		if (currentEmote != null && !this.getWorld().isClient) {
			stopTriggeredAnim(MAIN_CONTROLLER, currentEmote.animName);
			currentEmote = null;
			specialAnimTicks = 0;
		}
	}

	// ------------------------------------------------------------------------------------------ client animation

	/** Client, every tick: float layer, fly / hover state, blinks. Plain arithmetic, no allocation. */
	private void tickClientAnimation() {
		motion.tick(this.isInSittingPose(), this.getX() - this.prevX, this.getY() - this.prevY, this.getZ() - this.prevZ,
				this.bodyYaw, MathHelper.wrapDegrees(this.bodyYaw - this.prevBodyYaw), this.getScaleFactor());

		if (--blinkCooldown <= 0) {
			blinkCooldown = 50 + this.random.nextInt(110); // every 2.5 to 8 s
			if (canBlink()) {
				triggerAnim(BLINK_CONTROLLER, this.random.nextInt(5) == 0 ? "blink_double" : "blink");
			}
		}
	}

	/** Not when its eyes are already busy: sitting (half-closed, own slow blink) or in a triggered animation. */
	private boolean canBlink() {
		if (!this.isAlive() || this.isInSittingPose()) return false;
		AnimatableManager<?> manager = getAnimatableInstanceCache().getManagerForId(this.getId());
		AnimationController<?> main = manager == null ? null : manager.getAnimationControllers().get(MAIN_CONTROLLER);
		return main == null || !main.isPlayingTriggeredAnimation();
	}

	public MulaMotion getMotion() {
		return motion;
	}

	// ------------------------------------------------------------------------------------------ effects (client)

	/** Timeline instructions of the animations ("sparkle_small", "sparkle_ring", "burst", "chime"). */
	private void onAnimationInstruction(String instructions) {
		for (String raw : instructions.split(";")) {
			switch (raw.trim()) {
				case "sparkle_small" -> sparkleSmall();
				case "sparkle_ring" -> sparkleRing(10, 0.07);
				case "burst" -> burst();
				case "chime" -> chime(1.35f + this.random.nextFloat() * 0.4f);
				default -> { }
			}
		}
	}

	/** Star dust in the Mula's own colour, lightened so even the black one sparkles. */
	public ParticleEffect starDust() {
		return getVariant().getStarDust();
	}

	private double centerY() {
		return this.getY() + this.getHeight() * 0.55;
	}

	private void sparkleSmall() {
		World world = this.getWorld();
		for (int i = 0; i < 5; i++) {
			world.addParticle(ParticleTypes.WAX_OFF, this.getX() + (random.nextDouble() - 0.5) * 0.8,
					centerY() + random.nextDouble() * 0.5, this.getZ() + (random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
		}
		world.addParticle(starDust(), this.getX(), centerY() + 0.3, this.getZ(), 0, 0.02, 0);
	}

	private void sparkleRing(int count, double speed) {
		World world = this.getWorld();
		double y = centerY();
		for (int i = 0; i < count; i++) {
			double a = MathHelper.TAU * i / count + random.nextDouble() * 0.3;
			double cos = Math.cos(a), sin = Math.sin(a);
			// little four-pointed twinkles flying out, star dust in its colour between them
			world.addParticle(ParticleTypes.WAX_OFF, this.getX() + cos * 0.3, y, this.getZ() + sin * 0.3,
					cos * speed * 40, 1.0, sin * speed * 40); // WAX_OFF scales its velocity down (x0.005 sideways)
			if (i % 2 == 0) {
				world.addParticle(starDust(), this.getX() + cos * 0.5, y + 0.1, this.getZ() + sin * 0.5, 0, 0, 0);
			}
		}
		// and a couple of soft glowing motes rising
		for (int i = 0; i < 2; i++) {
			world.addParticle(ParticleTypes.END_ROD, this.getX() + (random.nextDouble() - 0.5) * 0.4, y + 0.2,
					this.getZ() + (random.nextDouble() - 0.5) * 0.4, 0, 0.03, 0);
		}
	}

	private void burst() {
		World world = this.getWorld();
		double y = centerY();
		world.addParticle(ParticleTypes.FLASH, this.getX(), y, this.getZ(), 0, 0, 0);
		for (int i = 0; i < 24; i++) {
			double vx = random.nextGaussian() * 0.12, vy = random.nextGaussian() * 0.12 + 0.05, vz = random.nextGaussian() * 0.12;
			world.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.WAX_OFF, this.getX(), y, this.getZ(), vx, vy, vz);
			world.addParticle(starDust(), this.getX() + vx * 6, y + vy * 6, this.getZ() + vz * 6, 0, 0, 0);
		}
	}

	private void chime(float pitch) {
		this.getWorld().playSound(this.getX(), centerY(), this.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
				SoundCategory.NEUTRAL, 0.35f, pitch, false);
	}

	// -------------------
	// Variant
	// -------------------
	public void setVariant(MulaVariant variant) {
		this.dataTracker.set(VARIANT, variant.getId());
	}

	public MulaVariant getVariant() {
		return MulaVariant.byId(this.dataTracker.get(VARIANT));
	}

	// -------------------
	// Animation
	// -------------------
	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		AnimationController<MulaEntity> main = new AnimationController<>(this, MAIN_CONTROLLER, MAIN_TRANSITION_TICKS,
				this::animationPredicate)
				.triggerableAnim("explode", EXPLODE_ANIM)
				.triggerableAnim("celebrate", CELEBRATE_ANIM)
				.triggerableAnim("no", NO_ANIM)
				.setCustomInstructionKeyframeHandler(event -> onAnimationInstruction(event.getKeyframeData().getInstructions()));
		for (Emote emote : EMOTES) {
			main.triggerableAnim(emote.animName, emote.animation);
		}
		// Blends ease in and out (GeckoLib blends linearly: a visible jolt at both ends). Only while blending: the
		// keyframes themselves are already smooth curves.
		main.setOverrideEasingTypeFunction(mula -> main.getAnimationState() == AnimationController.State.TRANSITIONING
				? EasingType.EASE_IN_OUT_SINE : null);
		controllers.add(main);

		// Eyes only, on top of everything: blinks started on each client (see tickClientAnimation)
		controllers.add(new AnimationController<>(this, BLINK_CONTROLLER, 1, state -> PlayState.STOP)
				.triggerableAnim("blink", BLINK_ANIM)
				.triggerableAnim("blink_double", BLINK_DOUBLE_ANIM));
	}

	private PlayState animationPredicate(AnimationState<MulaEntity> state) {
		if (this.isInSittingPose()) {
			return state.setAndContinue(SIT_ANIM);
		}
		// Smoothed speed with hysteresis (MulaMotion): no flicker between fly and idle around a threshold
		return state.setAndContinue(motion.isFlying() ? FLY_ANIM : IDLE_ANIM);
	}

	@Override
	public double getBoneResetTime() {
		return BONE_RESET_TICKS;
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public boolean isBreedingItem(ItemStack stack) { return false; }

	// -------------------
	// Variant enum with fragment colors
	// -------------------
	public enum MulaVariant {
		BLUE(0, 0x3F76E4, ModItems.BLUE_STAR_FRAGMENT),
		RED(1, 0xE03F3F, ModItems.RED_STAR_FRAGMENT),
		GREEN(2, 0x3FE03F, ModItems.GREEN_STAR_FRAGMENT),
		YELLOW(3, 0xE0E03F, ModItems.YELLOW_STAR_FRAGMENT),
		PURPLE(4, 0x8B3FE0, ModItems.PURPLE_STAR_FRAGMENT),
		BLACK(5, 0x1C1C1C, ModItems.BLACK_STAR_FRAGMENT);

		private static final List<MulaVariant> COMMON_VARIANTS = Arrays.asList(BLUE, RED, GREEN, YELLOW, PURPLE);
		private static final Random RANDOM = new Random();

		private final int id;
		private final int color;
		private final Item fragmentItem;
		private final DustParticleEffect starDust;

		MulaVariant(int id, int color, Item fragmentItem) {
			this.id = id;
			this.color = color;
			this.fragmentItem = fragmentItem;
			this.starDust = new DustParticleEffect(lighten(color, id == 5 ? 0.3f : 0.45f), 0.7f);
		}

		private static int lighten(int rgb, float towardWhite) {
			int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
			r += (int) ((255 - r) * towardWhite);
			g += (int) ((255 - g) * towardWhite);
			b += (int) ((255 - b) * towardWhite);
			return (r << 16) | (g << 8) | b;
		}

		public int getId() { return id; }
		public int getColor() { return color; }
		public Item getFragmentItem() { return fragmentItem; }
		public DustParticleEffect getStarDust() { return starDust; }

		public static MulaVariant byId(int id) {
			for (MulaVariant v : values()) if (v.id == id) return v;
			return BLUE;
		}

		public static MulaVariant getRandomVariant() {
			if (RANDOM.nextInt(100) == 0) return BLACK;
			return COMMON_VARIANTS.get(RANDOM.nextInt(COMMON_VARIANTS.size()));
		}
	}

	/** Like wolves, a sitting Mula stands up when it gets hurt; a random animation stops at once. */
	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		boolean damaged = super.damage(world, source, amount);
		if (damaged) {
			stopEmote();
			if (this.isSitting()) {
				this.setSitting(false);
			}
		}
		return damaged;
	}
}
