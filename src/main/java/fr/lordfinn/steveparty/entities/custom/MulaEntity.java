package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.entities.custom.goals.FollowOwnerWhileFlyingGoal;
import fr.lordfinn.steveparty.entities.custom.goals.LumaHoverGoal;
import fr.lordfinn.steveparty.entities.custom.goals.SimpleFlyingMoveControl;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.animation.AnimationState;

import java.util.*;

import static net.minecraft.particle.ParticleTypes.WAX_OFF;

public class MulaEntity extends TameableEntity implements GeoEntity {

	private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
	protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
	protected static final RawAnimation FLY_ANIM = RawAnimation.begin().thenLoop("fly");
	protected static final RawAnimation EXPLODE_ANIM = RawAnimation.begin().thenPlay("explode");
	protected static final RawAnimation CELEBRATE_ANIM = RawAnimation.begin().thenPlay("celebrate");
	protected static final RawAnimation NO_ANIM = RawAnimation.begin().thenPlay("no");

	private boolean stackWasWrong = false;
	private boolean justAteCorrectItem = false;

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

	int tick = 0;
	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient) {
			tick++;
			if (tick % 5 == 0)
			{
				double offsetX = (this.random.nextDouble() - 0.5) * 0.9;
				double offsetY = this.random.nextDouble() * 0.8 + 0.2;
				double offsetZ = (this.random.nextDouble() - 0.5) * 0.9;
				this.getWorld()
						.addParticle( ParticleTypes.WAX_OFF,
								this.getX() + offsetX,
								this.getY() + offsetY,
								this.getZ() + offsetZ,
								0, 0, 0 );
				tick = 0;
			}
			return;
		}
		if (eatCooldown > 0) eatCooldown--;

		if (eatCooldown == 0) {
			stackWasWrong = false;
			justAteCorrectItem = false;
		}
	}

	@Override
	public float getScaleFactor() {
		float baseScale = this.isBaby() ? 0.5f : 1.0f;
		float hungerScale = 1.0f + ((3f - 1.0f) * ((float)this.getHunger() / MAX_HUNGER));
		return baseScale * hungerScale;
	}

	private static final int MAX_HUNGER = 100;

	public MulaEntity(EntityType<MulaEntity> entityType, World world) {
		super(entityType, world);
		this.setNoGravity(true);
		this.moveControl = new SimpleFlyingMoveControl(this, 10f);
	}

	@Override protected void initGoals() {
		this.goalSelector.add(0, new LumaHoverGoal(this, 0.2, 1.5, 6.0));
		this.goalSelector.add(1, new FollowOwnerWhileFlyingGoal(this, 1.0, 3.0f, 20.0f)); super.initGoals();
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

		// Check cooldown
		if (eatCooldown > 0) {
			triggerAnim("main_controller", "no");
			return ActionResult.SUCCESS;
		}

		// Check if empty or wrong item
		Map<Item, Integer> allowedItems = FEED_ITEMS.get(this.getVariant());
		if (stack.isEmpty() || !allowedItems.containsKey(stack.getItem())) {
			triggerAnim("main_controller", "no");
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

			stack.decrement(1);

			// Explode if max hunger
			if (getHunger() >= MAX_HUNGER) {
				triggerAnim("main_controller", "explode");
				setHunger(0);
				dropFragmentStars(64);
			} else {
				triggerAnim("main_controller", "celebrate");
			}
			// Set cooldown for 1 second (20 ticks)
			eatCooldown = 20;
		}

		return ActionResult.SUCCESS;
	}

	private <T extends GeoAnimatable> PlayState predicate(AnimationState<T> state) {
		if (eatCooldown > 0 && stackWasWrong) return state.setAndContinue(NO_ANIM); // triggered wrong feed
		if (justAteCorrectItem) return state.setAndContinue(CELEBRATE_ANIM);
		if (this.getVelocity().lengthSquared() > 0.01) return state.setAndContinue(FLY_ANIM);
		return state.setAndContinue(IDLE_ANIM);
	}

	private void dropFragmentStars(int count) {
		if (this.getWorld().isClient) return;
		Item fragmentItem = this.getVariant().getFragmentItem();
		for (int i = 0; i < count; i++) {
			this.dropItem((ServerWorld) this.getWorld(), fragmentItem);
		}
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
		controllers.add(
				new AnimationController<>(this, "main_controller", this::animationPredicate)
						.triggerableAnim("explode", EXPLODE_ANIM)
						.triggerableAnim("celebrate", CELEBRATE_ANIM)
						.triggerableAnim("no", NO_ANIM)
						.transitionLength(10)
		);

		/*controllers.add(new AnimationController<>(this, "explode_controller", state -> PlayState.STOP)
				.triggerableAnim("explode", EXPLODE_ANIM));

		controllers.add(new AnimationController<>(this, "idle_controller", state -> {
			if (this.getVelocity().lengthSquared() < 0.01) return state.setAndContinue(IDLE_ANIM);
			return PlayState.STOP;
		}));

		controllers.add(new AnimationController<>(this, "fly_controller", state -> {
			if (this.getVelocity().lengthSquared() > 0.01) return state.setAndContinue(FLY_ANIM);
			return PlayState.STOP;
		}));

		controllers.add(new AnimationController<>(this, "interaction_controller", state -> PlayState.STOP)
				.triggerableAnim("celebrate", CELEBRATE_ANIM)
				.triggerableAnim("no", NO_ANIM));*/
	}

	private PlayState animationPredicate(AnimationState<MulaEntity> state) {
		if (this.getVelocity().lengthSquared() > 0.01) {
			return state.setAndContinue(FLY_ANIM);
		}
		return state.setAndContinue(IDLE_ANIM);
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

		MulaVariant(int id, int color, Item fragmentItem) {
			this.id = id;
			this.color = color;
			this.fragmentItem = fragmentItem;
		}

		public int getId() { return id; }
		public int getColor() { return color; }
		public Item getFragmentItem() { return fragmentItem; }

		public static MulaVariant byId(int id) {
			for (MulaVariant v : values()) if (v.id == id) return v;
			return BLUE;
		}

		public static MulaVariant getRandomVariant() {
			if (RANDOM.nextInt(100) == 0) return BLACK;
			return COMMON_VARIANTS.get(RANDOM.nextInt(COMMON_VARIANTS.size()));
		}
	}
}
