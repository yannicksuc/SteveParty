package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import fr.lordfinn.steveparty.entities.FollowsOwnerAnywhere;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenBase;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.goals.FollowOwnerWhileFlyingGoal;
import fr.lordfinn.steveparty.entities.custom.goals.FloatHoverGoal;
import fr.lordfinn.steveparty.entities.custom.goals.MulaBodyControl;
import fr.lordfinn.steveparty.entities.custom.goals.MulaBrain;
import fr.lordfinn.steveparty.entities.custom.goals.MulaGoals;
import fr.lordfinn.steveparty.entities.custom.goals.MulaSitGoal;
import fr.lordfinn.steveparty.entities.custom.goals.SimpleFlyingMoveControl;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.TokenItem;
import fr.lordfinn.steveparty.items.custom.TokenizerWandItem;
import fr.lordfinn.steveparty.particles.MulaSparkleEffect;
import fr.lordfinn.steveparty.utils.Argb;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.control.BodyControl;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
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

public class MulaEntity extends TameableEntity implements GeoEntity, FollowsOwnerAnywhere {

	private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

	// ------------------------------------------------------------------------------------------ animations
	// Authored in the art sources (it writes mula.animation.json and the .bbmodel animations).
	public static final String MAIN_CONTROLLER = "main_controller";
	public static final String BLINK_CONTROLLER = "blink_controller";
	private static final String[] CONTROLLERS = {MAIN_CONTROLLER, BLINK_CONTROLLER};
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
	/** Dancing round a Dice Forge: twirl (arms up, spins), sway (waving, blissful), hold (arms out, holding hands). */
	protected static final RawAnimation[] DANCE_ANIMS = {RawAnimation.begin().thenLoop("dance_twirl"),
			RawAnimation.begin().thenLoop("dance_sway"), RawAnimation.begin().thenLoop("dance_hold")};
	protected static final RawAnimation EXPLODE_ANIM = RawAnimation.begin().thenPlay("explode");
	protected static final RawAnimation CELEBRATE_ANIM = RawAnimation.begin().thenPlay("celebrate");
	protected static final RawAnimation NO_ANIM = RawAnimation.begin().thenPlay("no");
	protected static final RawAnimation BLINK_ANIM = RawAnimation.begin().thenPlay("blink");
	protected static final RawAnimation BLINK_DOUBLE_ANIM = RawAnimation.begin().thenPlay("blink_double");

	/** Length of the triggered animations, in ticks (json length + the blend in). */
	private static final int EXPLODE_TICKS = 84 + MAIN_TRANSITION_TICKS, CELEBRATE_TICKS = 36 + MAIN_TRANSITION_TICKS,
			NO_TICKS = 19 + MAIN_TRANSITION_TICKS, TAME_JOY_TICKS = 44 + MAIN_TRANSITION_TICKS,
			SHY_TICKS = 28 + MAIN_TRANSITION_TICKS, OUCH_TICKS = 15 + MAIN_TRANSITION_TICKS,
			SIT_DOWN_TICKS = 18 + MAIN_TRANSITION_TICKS, STAND_UP_TICKS = 18 + MAIN_TRANSITION_TICKS;
	/** Feedback of the Mula's features, played by the server when they happen (see playSpecial). */
	private static final String[] FEATURE_ANIMS = {"tame_joy", "shy", "ouch", "sit_down", "stand_up"};
	/** After bursting, the last food is forgotten with the pop (the pop is ~1.3 s after the order). */
	private static final int BELLY_CLEAR_TICKS = 26;
	/** "Just spawned" (it pops in from nothing on the clients) lasts this long. */
	private static final int FRESH_TICKS = 40;
	/**
	 * A nearly full Mula trembles only while it is on edge (see {@link #isShaking}): when a player is within this many
	 * blocks of its body (from the edge of its hitbox, so the same arm's length for a small or a swollen one: well
	 * inside the 3 blocks you can feed it from, you really have to come up to it)...
	 */
	public static final double SHAKE_NEAR = 2.0;
	/**
	 * ...or for this long after a meal: the meal's light sinks in (MulaEffects.ABSORB_TICKS, when it grows), then it
	 * trembles for 2.5 s more.
	 */
	public static final int SHAKE_AFTER_MEAL_TICKS = MulaEffects.ABSORB_TICKS + 50;

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
		TWIRL("twirl", 1.6f, 3, false, true),
		FLIP("flip", 1.8f, 1, false, false),
		STAR_ORBIT("star_orbit", 2.8f, 2, false, true),
		COMET_LOOP("comet_loop", 1.9f, 2, false, true),
		STAR_SHOWER("star_shower", 1.7f, 2, false, false),
		GLOW_RINGS("glow_rings", 2.6f, 2, false, false),
		YAWN("yawn", 2.8f, 3, true, false),
		SLEEPY_NOD("sleepy_nod", 3.2f, 2, true, false),
		SNOOZE("snooze", 3.8f, 3, true, false);

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
	/** Server: the triggered animation being played (null when none or when it is a random one). */
	private @Nullable String currentSpecial = null;
	/** Server: ticks before the last food is forgotten (after a burst), 0 when nothing is pending. */
	private int bellyClearTicks = 0;
	/** Server: ticks left of being "just spawned". */
	private int freshTicks = 0;
	private @Nullable Emote lastEmote = null;

	/** Client: the float layer and the fly / hover state (see {@link MulaMotion}). */
	private final MulaMotion motion;
	/** Server: what is around it, refreshed rarely (see {@link MulaBrain}). */
	private final MulaBrain brain = new MulaBrain(this);
	/** Client: ticks before the next blink (each client blinks on its own: purely cosmetic, never synced). */
	private int blinkCooldown = 40;
	/** Client: last age at which the renderer spawned its particles (at most once per tick, only when drawn). */
	public int lastEffectsAge = -1;
	/** Client: particles, sounds and flying food of this Mula (see {@link MulaEffects}). */
	private final MulaEffects effects;
	/** Client: a player close by holds its food, and where they are (relative yaw), refreshed every few ticks. */
	private boolean excited;
	private float excitedYaw;

	// ------------------------------------------------------------------------------------------ state

	private static final TrackedData<Integer> VARIANT =
			DataTracker.registerData(MulaEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> HUNGER =
			DataTracker.registerData(MulaEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** The last food it ate: the clients show it melting into light and spiralling into the Mula (nothing else uses it). */
	private static final TrackedData<ItemStack> LAST_FOOD =
			DataTracker.registerData(MulaEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
	/** Counts the meals: when it changes, the clients show the food melting into light and spiralling into it. */
	private static final TrackedData<Integer> FEED_COUNT =
			DataTracker.registerData(MulaEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** A wild Mula resting on a flower in the morning (plays the sit animation). */
	/**
	 * Round a Dice Forge: -1, or slot | count << 7 | locked << 14 | spectator << 15 (locked: on its figure, moved by
	 * formula; spectator: resting this turn, watching from spectator spot slot of count, see MulaDances#spectatorSpot).
	 */
	private static final TrackedData<Integer> DANCE =
			DataTracker.registerData(MulaEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Optional<BlockPos>> DANCE_FORGE =
			DataTracker.registerData(MulaEntity.class, TrackedDataHandlerRegistry.OPTIONAL_BLOCK_POS);
	private static final TrackedData<Boolean> RESTING =
			DataTracker.registerData(MulaEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** Just spawned: it pops in from nothing on the clients. */
	private static final TrackedData<Boolean> FRESH =
			DataTracker.registerData(MulaEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** Nearly full and on edge (a player very close, a meal just taken): the clients let it tremble (MulaMotion). */
	private static final TrackedData<Boolean> SHAKING =
			DataTracker.registerData(MulaEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** Server: ticks left of the tremble after a meal. */
	private int shakeTicks = 0;

	private int eatCooldown = 0;
	/** Carrying its lead holder up into the night sky (MulaLift): it twinkles, happy. */
	private static final TrackedData<Boolean> CARRYING =
			DataTracker.registerData(MulaEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	public boolean isCarrying() {
		return this.dataTracker.get(CARRYING);
	}

	public void setCarrying(boolean carrying) {
		if (carrying != isCarrying()) this.dataTracker.set(CARRYING, carrying);
	}
	/** Server: ticks before it flies away as a shooting star (after the pop of its burst), 0 when none. */
	private int starLaunchTicks = 0;
	/** From the order to burst to the pop of the explode animation, when it leaves as a shooting star. */
	private static final int STAR_LAUNCH_TICKS = 32;
	/** Server: ticks before it bursts in its forge's core explosion (the chain reaction), 0 when none. */
	private int coreBurstTicks = 0;
	/** Server: the way its star goes (radians), NaN for a random one. */
	private double starAngle = Double.NaN;

	// ------------------------------------------------------------------------------------------ home (MulaHome)

	/** The Dice Forge it lives at, or null; saved with it. */
	private @Nullable BlockPos homeForge;
	/** Server: its owner is leading it (following them): the only thing that may take it out of its forge's area. */
	private boolean ledByOwner;
	/**
	 * The spawn site it came down at (MulaSpawnSites: its id there, the dimension, its column), 0 for none: a Mula
	 * of another origin, or one that a player made his own or took away (it then belongs to no site, for good).
	 */
	private int spawnSite;
	private @Nullable Identifier spawnSiteWorld;
	private int spawnSiteX, spawnSiteZ;
	/** The retirements it has taken into account (MulaSpawnSites#epoch): -1 just loaded, not checked yet. */
	private int spawnSiteEpoch = -1;

	public @Nullable BlockPos homeForge() {
		return homeForge;
	}

	/** Server, from the forge conducting (once a second): it lives there now. */
	public void setHomeForge(BlockPos forge) {
		this.homeForge = forge.toImmutable();
	}

	/** Released: no home any more, and no dance. */
	public void clearHome() {
		this.homeForge = null;
		stopDancing();
	}

	public boolean isLedByOwner() {
		return ledByOwner;
	}

	public void setLedByOwner(boolean led) {
		this.ledByOwner = led;
	}

	/** A point brought back inside its forge's area (the point itself when it has no home). */
	public Vec3d keepHome(Vec3d v) {
		return homeForge == null ? v : MulaHome.clamp(homeForge, v);
	}

	/** Inside its forge's area (anywhere when it has no home). */
	public boolean isInHome(Vec3d v) {
		return homeForge == null || MulaHome.contains(homeForge, v.x, v.y, v.z);
	}

	/**
	 * Server, once a second: the home is released when its forge is gone or has lost its core (only looked at while the
	 * forge's chunk is loaded: an unloaded forge keeps its Mulas), or when its owner has led it far away.
	 */
	public void checkHome() {
		if (homeForge == null) return;
		World world = this.getWorld();
		if (world.isChunkLoaded(homeForge) && !MulaHome.holds(world, homeForge)) {
			clearHome();
			return;
		}
		double dx = this.getX() - (homeForge.getX() + 0.5), dz = this.getZ() - (homeForge.getZ() + 0.5);
		if ((isTamed() && ledByOwner || isLeashed()) && dx * dx + dz * dz > MulaHome.RELEASE_DISTANCE * MulaHome.RELEASE_DISTANCE) clearHome();
	}

	/**
	 * Server, from its forge's core exploding (DiceForgeBlockEntity#explodeCore): in {@code delay} ticks it bursts like
	 * when it has eaten too much (same animation, flash and star bits), but without dropping fragments, then flies away
	 * as a shooting star the way given (radians), 100 to 400 blocks: its forge can't hold it any more. Nothing if it is
	 * already bursting.
	 */
	public void burstFromCore(int delay, double angle) {
		if (isToken()) return; // a board pawn stays on the board
		if (this.isRemoved() || isBursting()) return;
		homeForge = null;
		stopDancing();
		starAngle = angle;
		coreBurstTicks = Math.max(1, delay);
	}

	public boolean isBursting() {
		return starLaunchTicks > 0 || coreBurstTicks > 0;
	}

	/**
	 * Server: the pop of its burst: it leaves as a shooting star (MulaStarEntity) and is recorded to be reborn where the
	 * star lands (MulaRebirths), the same Mula (colour, owner, name, UUID, home) with an empty belly. It is removed without
	 * dying (no death message, no loot: its fragments, if any, are already dropped).
	 * <p>
	 * Far from any forge: a random arc, the higher the farther, 100 to 400 blocks away. At home by a forge with its core:
	 * a short, high loop that falls back next to the forge, where it is reborn (a Mula near a forge never leaves).
	 */
	public void burstIntoStar() {
		if (!(this.getWorld() instanceof ServerWorld world) || this.isRemoved()) return;
		net.minecraft.util.math.random.Random random = this.getRandom();
		boolean atForge = homeForge != null && MulaHome.holds(world, homeForge);
		double startY = this.getY() + this.getHeight() * CENTER;
		double apex, distance, dirX, dirZ, endY;
		int x, z;
		if (atForge) {
			// lands 2 to 4 blocks from the forge, on a loop 10 to 16 blocks high
			double around = random.nextDouble() * MathHelper.TAU, r = 2 + random.nextDouble() * 2;
			double tx = homeForge.getX() + 0.5 + Math.cos(around) * r, tz = homeForge.getZ() + 0.5 + Math.sin(around) * r;
			double dx = tx - this.getX(), dz = tz - this.getZ(), d = Math.sqrt(dx * dx + dz * dz);
			dirX = d < 1.0E-3 ? 1 : dx / d;
			dirZ = d < 1.0E-3 ? 0 : dz / d;
			distance = d;
			apex = 10 + random.nextDouble() * 6;
			x = MathHelper.floor(tx);
			z = MathHelper.floor(tz);
			endY = homeForge.getY() + 2.5;
		} else {
			double angle = Double.isNaN(starAngle) ? random.nextDouble() * MathHelper.TAU : starAngle;
			apex = MulaStarEntity.MIN_APEX + random.nextDouble() * (MulaStarEntity.MAX_APEX - MulaStarEntity.MIN_APEX);
			distance = MulaStarEntity.distanceFor(apex, getVariant());
			dirX = Math.cos(angle);
			dirZ = Math.sin(angle);
			x = MathHelper.floor(this.getX() + dirX * distance);
			z = MathHelper.floor(this.getZ() + dirZ * distance);
			endY = startY;
		}
		// what is reborn: the same Mula, standing, free, empty
		this.detachLeash(true, true);
		this.stopRiding();
		this.setSitting(false);
		this.setInSittingPose(false);
		this.setHunger(0);
		this.setLastFood(ItemStack.EMPTY);
		stopDancing();
		starLaunchTicks = 0;
		coreBurstTicks = 0;
		starAngle = Double.NaN;
		NbtCompound saved = new NbtCompound();
		if (!this.saveSelfNbt(saved)) return;
		int flight = MulaStarEntity.flightTicksFor(distance);
		MulaRebirths.get(world).add(new MulaRebirths.Entry(this.getUuid(), x, z, atForge ? endY : this.getY(),
				world.getTime() + flight, saved));
		MulaStarEntity star = new MulaStarEntity(ModEntities.MULA_STAR, world);
		star.launch(this.getX(), startY, this.getZ(), this.getVariant(), dirX, dirZ, distance, apex, endY - startY);
		world.spawnEntity(star);
		world.playSound(null, this.getX(), startY, this.getZ(), SoundEvents.ENTITY_ALLAY_ITEM_THROWN,
				SoundCategory.NEUTRAL, 0.6f, 1.5f * voice());
		Steveparty.LOGGER.info("A {} Mula burst into a shooting star: reborn at {} {} in {} s{}",
				getVariant().name().toLowerCase(Locale.ROOT), x, z, flight / 20, atForge ? " (at its forge)" : "");
		this.discard();
	}

	/** Server, from MulaRebirths: it comes back where its star landed, popping in (FRESH), the burst forgotten. */
	public void onReborn() {
		starLaunchTicks = 0;
		specialAnimTicks = 0;
		currentSpecial = null;
		this.dataTracker.set(FRESH, true);
		this.freshTicks = FRESH_TICKS;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient) {
			if (isToken()) {
				tickClientAsToken();
			} else {
				tickClientAnimation();
			}
			return;
		}
		tickFollow(isTamed());
		if (spawnSite != 0 && spawnSiteEpoch != MulaSpawnSites.epoch()) {
			spawnSiteEpoch = MulaSpawnSites.epoch();
			if (leaveWithRetiredSite()) return;
		}
		if (eatCooldown > 0) eatCooldown--;
		if (bellyClearTicks > 0 && --bellyClearTicks == 0) setLastFood(ItemStack.EMPTY);
		if (freshTicks > 0 && --freshTicks == 0) this.dataTracker.set(FRESH, false);
		if (shakeTicks > 0) shakeTicks--;
		if ((this.age & 1) == 0) updateShaking();
		if ((this.age & 3) == 0) keepOutOfBlocks();
		if (coreBurstTicks > 0 && --coreBurstTicks == 0) {
			// its forge's core blew up: it bursts too (no fragments: the core's blast is not a meal)
			playSpecial("explode", EXPLODE_TICKS);
			starLaunchTicks = STAR_LAUNCH_TICKS;
		}
		if (starLaunchTicks > 0 && --starLaunchTicks == 0) {
			burstIntoStar();
			return;
		}
		if (isToken()) {
			tickAsToken();
			return;
		}
		if ((this.age + this.getId()) % 20 == 3) checkHome();
		brain.tick();
		tickEmotes();
	}

	// ---------------------------------------------------------------- its spawn site

	public int getSpawnSite() {
		return spawnSite;
	}

	/** Server, when it comes down at a site (MulaSpawnSites#spawn). */
	public void setSpawnSite(RegistryKey<World> world, int id, int x, int z) {
		this.spawnSite = id;
		this.spawnSiteWorld = world.getValue();
		this.spawnSiteX = x;
		this.spawnSiteZ = z;
	}

	/** It belongs to no site any more: nothing that happens to its site concerns it. */
	public void leaveSpawnSite() {
		this.spawnSite = 0;
		this.spawnSiteWorld = null;
	}

	/**
	 * Is it somebody's, or no longer a wild Mula of its site? Tamed, named, on a leash, riding or ridden, carrying,
	 * kept from despawning, a board pawn, living at a forge, or farther than MulaSpawnSites#AWAY blocks from where it
	 * came down (brought back to a base).
	 */
	public boolean isKeptFromSiteRetirement() {
		if (isTamed() || getOwnerUuid() != null || hasCustomName() || isLeashed() || hasVehicle() || hasPassengers()
				|| isPersistent() || isCarrying() || homeForge != null || isToken()) return true;
		double dx = this.getX() - (spawnSiteX + 0.5), dz = this.getZ() - (spawnSiteZ + 0.5);
		return dx * dx + dz * dz > MulaSpawnSites.AWAY * MulaSpawnSites.AWAY;
	}

	/**
	 * Server: its site was retired (too many in the dimension: MulaSpawnSites)? A wild Mula still there goes with it
	 * (removed without dying: no loot); any other is kept and no longer belongs to a site. Looked at once when it is
	 * loaded and once after each retirement, never otherwise.
	 *
	 * @return true if it was removed
	 */
	public boolean leaveWithRetiredSite() {
		if (spawnSite == 0 || !(this.getWorld() instanceof ServerWorld world)) return false;
		if (!MulaSpawnSites.isRetired(world.getServer(), spawnSiteWorld, spawnSite)) return false;
		if (isKeptFromSiteRetirement()) {
			leaveSpawnSite();
			return false;
		}
		this.discard();
		return true;
	}

	/** Made somebody's, or taken along: it leaves its site at once (whatever happens next, it is kept). */
	@Override
	public void setTamed(boolean tamed, boolean updateAttributes) {
		super.setTamed(tamed, updateAttributes);
		if (tamed) leaveSpawnSite();
	}

	@Override
	public void setCustomName(@Nullable Text name) {
		super.setCustomName(name);
		if (name != null) leaveSpawnSite();
	}

	@Override
	public void attachLeash(net.minecraft.entity.Entity leashHolder, boolean sendPacket) {
		super.attachLeash(leashHolder, sendPacket);
		leaveSpawnSite();
	}

	@Override
	public boolean startRiding(net.minecraft.entity.Entity entity, boolean force) {
		boolean riding = super.startRiding(entity, force);
		if (riding) leaveSpawnSite();
		return riding;
	}

	// ------------------------------------------------------------------------------------------ with its owner anywhere

	@Override
	public @Nullable java.util.UUID followedOwner() {
		return getOwnerUuid();
	}

	/**
	 * Going along with its owner through their teleports (PetTeleports): following them (one of their escort, not
	 * sitting, on a lead or riding), and not busy: no board token, no home at a Dice Forge, no dance, not carrying its
	 * lead holder up, not bursting into a star.
	 */
	@Override
	public boolean goesWithOwner(ServerPlayerEntity owner) {
		return isAlive() && isTamed() && owner.getUuid().equals(getOwnerUuid()) && !cannotFollowOwner() && !isToken()
				&& homeForge == null && !isDancing() && !isSpectating() && !isCarrying() && !isBursting()
				&& MulaEscorts.isFollower(owner.getUuid(), this);
	}

	/**
	 * Catching up with its owner (vanilla's pop next to them): far away, it is recreated by them (PetTeleports) rather
	 * than moved in place, which could leave it unseen by the clients.
	 */
	@Override
	public void tryTeleportToOwner() {
		if (!catchUpFar(getOwner())) super.tryTeleportToOwner();
	}

	/** @return true while it is a board token: a static pawn, with none of its life (see {@link #tickAsToken}). */
	public boolean isToken() {
		return TokenBase.isToken(this);
	}

	/**
	 * Server, every tick while it is a board token: no brain, home, dance, random animation nor trembling (its goals are
	 * cleared and its AI is off, see TokenEntityMixin). Only clears what was going on when it became one.
	 */
	private void tickAsToken() {
		if (specialAnimTicks > 0 || currentEmote != null || currentSpecial != null) {
			specialAnimTicks = 0;
			currentEmote = null;
			currentSpecial = null;
			stopTriggeredAnim(MAIN_CONTROLLER, null);
		}
		if (isDancing() || isSpectating()) stopDancing();
		if (isShaking()) this.dataTracker.set(SHAKING, false);
		if (isResting()) setResting(false);
	}

	@Override
	public float getScaleFactor() {
		float baseScale = this.isBaby() ? 0.5f : 1.0f;
		float hungerScale = 1.0f + ((3f - 1.0f) * ((float)this.getHunger() / MAX_HUNGER));
		return baseScale * hungerScale;
	}

	/**
	 * Satiety at which it bursts. Foods give their nutrition (1 to 10, rabbit stew 10), potions minutes x (level x 2):
	 * 4 to 40 meals, or a couple of long potions.
	 */
	public static final int MAX_HUNGER = 40;
	/** Star fragments a Mula drops when it bursts from food: 64, a black one only one (a rare, powerful fragment). */
	public static final int BURST_FRAGMENTS = 64, BLACK_BURST_FRAGMENTS = 1;

	public static int fragmentsOnBurst(MulaVariant variant) {
		return variant == MulaVariant.BLACK ? BLACK_BURST_FRAGMENTS : BURST_FRAGMENTS;
	}
	/**
	 * Size of its body cube (9 model pixels) at scale 1: its hitbox (width and height). The model is drawn with the
	 * bottom of that cube on its feet (MulaModel), so the hitbox is exactly what is seen, at every size.
	 */
	public static final float MODEL_SIZE = 9 / 16f;
	/** Height of its eyes' centre above the bottom of the cube (5 model pixels) at scale 1. */
	public static final float MODEL_EYE_HEIGHT = 5 / 16f;
	/** Height of the middle of its body, as a fraction of its height (the model is centred in its hitbox). */
	public static final double CENTER = 0.5;
	/** 1 chance in TAMING_CHANCE to tame the Mula with each star fragment of its colour. */
	private static final int TAMING_CHANCE = 3;

	public MulaEntity(EntityType<MulaEntity> entityType, World world) {
		super(entityType, world);
		this.setNoGravity(true);
		this.moveControl = new SimpleFlyingMoveControl(this, 10f);
		this.motion = new MulaMotion(this.getId());
		this.effects = new MulaEffects(this);
	}

	@Override protected void initGoals() {
		// All hold the MOVE control: a lower number interrupts a higher one. Sitting (owner's order) wins; hit, it
		// hides for a few seconds; following the owner stops by itself while sitting (cannotFollowOwner); when the
		// owner stands still it circles them; wild ones follow their curiosity, play, look at shiny things, go up to
		// the sky at night and rest on flowers in the morning, and otherwise wander in little flocks. Far from every
		// player (MulaBrain#isActive) the new behaviours don't start.
		this.goalSelector.add(0, new MulaSitGoal(this));
		this.goalSelector.add(1, new MulaGoals.Shy(this));
		this.goalSelector.add(1, new MulaGoals.Tethered(this));
		this.goalSelector.add(2, new FollowOwnerWhileFlyingGoal(this, 1.0, 3.0f, 20.0f));
		this.goalSelector.add(3, new MulaGoals.Dance(this));
		this.goalSelector.add(3, new MulaGoals.Spectate(this));
		this.goalSelector.add(4, new MulaGoals.OrbitOwner(this));
		this.goalSelector.add(5, new MulaGoals.Curious(this));
		this.goalSelector.add(6, new MulaGoals.Play(this));
		this.goalSelector.add(6, new MulaGoals.Shiny(this));
		this.goalSelector.add(7, new MulaGoals.Sky(this));
		this.goalSelector.add(8, new FloatHoverGoal(this, 0.2, 1.5, 6.0));
		super.initGoals();
	}

	public MulaBrain getMulaBrain() {
		return brain;
	}

	/** A wild Mula resting on a flower (synced: the clients play its sit animation). */
	public boolean isResting() {
		return this.dataTracker.get(RESTING);
	}

	// ------------------------------------------------------------------------------------------ dances

	/** Previous slot / count (for the blend when someone joins or leaves) and when it changed. */
	private int prevDanceSlot, prevDanceCount;
	private long danceChangeTick;
	/** Server: when the forge last counted it among its dancers. */
	private long danceAssignedTick = Long.MIN_VALUE;
	private final double[] danceOut = new double[4], danceTmp = new double[8];
	/** The DANCE value's fields (see DANCE). */
	private static final int DANCE_SLOT = 0x7F, DANCE_COUNT_SHIFT = 7, DANCE_PLACE = 0x3FFF,
			DANCE_LOCKED = 1 << 14, DANCE_SPECTATOR = 1 << 15;
	/** Client: ticks left to catch up with its place in the dance after locking onto it. */
	private int lockBlendTicks;

	/** Server, from the Dice Forge conducting (once a second): its place in the dance. */
	public void assignDance(BlockPos forge, int slot, int count) {
		if (isToken()) return; // a board pawn does not dance
		int current = this.dataTracker.get(DANCE);
		boolean sameForge = forge.equals(this.dataTracker.get(DANCE_FORGE).orElse(null));
		boolean wasDancing = sameForge && current >= 0 && (current & DANCE_SPECTATOR) == 0;
		int value = slot | count << DANCE_COUNT_SHIFT | (wasDancing ? current & DANCE_LOCKED : 0);
		if (!wasDancing || (current & DANCE_PLACE) != (value & DANCE_PLACE)) {
			noteDanceChange(wasDancing ? current : -1);
			this.dataTracker.set(DANCE_FORGE, Optional.of(forge));
			this.dataTracker.set(DANCE, value);
		}
		danceAssignedTick = this.getWorld().getTime();
	}

	/** Server, from the Dice Forge conducting: not its turn to dance, it watches from spectator spot index of count. */
	public void assignSpectator(BlockPos forge, int index, int count) {
		if (isToken()) return;
		int value = Math.min(index, DANCE_SLOT) | Math.min(count, DANCE_SLOT) << DANCE_COUNT_SHIFT | DANCE_SPECTATOR;
		if (this.dataTracker.get(DANCE) != value || !forge.equals(this.dataTracker.get(DANCE_FORGE).orElse(null))) {
			this.dataTracker.set(DANCE_FORGE, Optional.of(forge));
			this.dataTracker.set(DANCE, value);
		}
		danceAssignedTick = this.getWorld().getTime();
	}

	private void noteDanceChange(int previous) {
		boolean danced = previous >= 0 && (previous & DANCE_SPECTATOR) == 0;
		prevDanceSlot = danced ? previous & DANCE_SLOT : 0;
		prevDanceCount = danced ? (previous >> DANCE_COUNT_SHIFT) & DANCE_SLOT : 0;
		danceChangeTick = this.getWorld().getTime();
	}

	public void stopDancing() {
		this.dataTracker.set(DANCE, -1);
		this.dataTracker.set(DANCE_FORGE, Optional.empty());
	}

	/** Server: reached its place in the figure: from now on it is moved by the formula (on every side). */
	public void lockDance() {
		int current = this.dataTracker.get(DANCE);
		if (current >= 0 && (current & DANCE_SPECTATOR) == 0) this.dataTracker.set(DANCE, current | DANCE_LOCKED);
	}

	public boolean isDancing() {
		int value = this.dataTracker.get(DANCE);
		return value >= 0 && (value & DANCE_SPECTATOR) == 0 && this.dataTracker.get(DANCE_FORGE).isPresent();
	}

	/** Resting this turn (more Mulas than dance at once): it watches the dance from its spectator spot. */
	public boolean isSpectating() {
		int value = this.dataTracker.get(DANCE);
		return value >= 0 && (value & DANCE_SPECTATOR) != 0 && this.dataTracker.get(DANCE_FORGE).isPresent();
	}

	public boolean isDanceLocked() {
		return isDancing() && (this.dataTracker.get(DANCE) & DANCE_LOCKED) != 0;
	}

	public long danceAssignedTick() {
		return danceAssignedTick;
	}

	/** When its slot or count last changed (its place is blended from the previous one for a few seconds). */
	public long danceChangeTick() {
		return danceChangeTick;
	}

	/** The forge it dances around (or watches), or null. */
	public @Nullable BlockPos danceForge() {
		return this.dataTracker.get(DANCE_FORGE).orElse(null);
	}

	/** Its place among the dancers (0-based) and how many they are; spectating: its spot and how many watch. */
	public int danceSlot() {
		return Math.max(0, this.dataTracker.get(DANCE)) & DANCE_SLOT;
	}

	public int danceCount() {
		return (Math.max(0, this.dataTracker.get(DANCE)) >> DANCE_COUNT_SHIFT) & DANCE_SLOT;
	}

	/** Server: dancing round another forge than this one (counted by it less than 2 s ago). */
	public boolean dancesElsewhere(BlockPos forge) {
		return isDancing() && !forge.equals(danceForge()) && this.getWorld().getTime() - danceAssignedTick < 40;
	}

	/** The dance its forge plays now (index in {@link MulaDances}), -1 if not dancing. */
	public int currentDance() {
		return isDancing() ? MulaDances.danceAt(this.getWorld().getTime(), this.dataTracker.get(DANCE_FORGE).get()) : -1;
	}

	/**
	 * Where it is in the dance at this moment (world position into out[0..2], out[3] facing yaw in degrees or NaN),
	 * the same on the server and every client (the forge, the slot, the count and the world time).
	 */
	public void dancePosition(float partialTick, double[] out) {
		BlockPos forge = this.dataTracker.get(DANCE_FORGE).orElse(this.getBlockPos());
		MulaDances.position(forge, danceSlot(), danceCount(), prevDanceSlot, prevDanceCount, danceChangeTick,
				this.getWorld().getTime(), partialTick, out, danceTmp);
		double cx = forge.getX() + 0.5, cy = forge.getY() + 2.4, cz = forge.getZ() + 0.5;
		if (this.getWorld().getBlockEntity(forge) instanceof DiceForgeBlockEntity be) {
			cy = Math.max(cy, be.getCoreCenter().y);
		}
		out[0] += cx;
		out[1] += cy - this.getHeight() * CENTER;
		out[2] += cz;
		if (!Double.isNaN(out[3])) out[3] = out[3] * MathHelper.DEGREES_PER_RADIAN - 90;
	}

	/** Moves it onto its place in the dance (kinematic: no physics, no path), facing its way or its partner. */
	public void followDance() {
		dancePosition(0f, danceOut);
		if (lockBlendTicks > 0) {
			// client: it was a few ticks behind (interpolated server positions) when it locked: catch up smoothly
			float k = 1f / lockBlendTicks--;
			for (int i = 0; i < 3; i++) danceOut[i] = MathHelper.lerp(k, i == 0 ? getX() : i == 1 ? getY() : getZ(), danceOut[i]);
		}
		double dx = danceOut[0] - this.getX(), dz = danceOut[2] - this.getZ();
		if (!this.getWorld().isClient && !fitsAt(danceOut[0], danceOut[1], danceOut[2])) {
			// its place is in a block (a tree by the forge...): a little higher, or it leaves the figure
			boolean fits = false;
			for (int i = 1; i <= 6 && !fits; i++) {
				if (fitsAt(danceOut[0], danceOut[1] + i * 0.5, danceOut[2])) {
					danceOut[1] += i * 0.5;
					fits = true;
				}
			}
			if (!fits) {
				stopDancing();
				return;
			}
		}
		this.setPosition(danceOut[0], danceOut[1], danceOut[2]);
		this.setVelocity(Vec3d.ZERO);
		float yaw = !Double.isNaN(danceOut[3]) ? (float) danceOut[3]
				: dx * dx + dz * dz > 1.0E-5 ? (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90f
				: this.getYaw();
		this.setYaw(yaw);
	}

	private boolean fitsAt(double x, double y, double z) {
		Box box = this.getBoundingBox().offset(x - this.getX(), y - this.getY(), z - this.getZ());
		return this.getWorld().isSpaceEmpty(this, box);
	}

	/** Client: a dancer's place comes from the formula, not from the server's position updates (they would lag). */
	@Override
	public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
		if (this.getWorld().isClient && isDanceLocked()) return;
		super.updateTrackedPositionAndAngles(x, y, z, yaw, pitch, interpolationSteps);
	}

	public void setResting(boolean resting) {
		this.dataTracker.set(RESTING, resting);
	}

	/** Turns its body smoothly (vanilla snaps it after 10 ticks without moving). */
	@Override
	protected BodyControl createBodyControl() {
		return new MulaBodyControl(this);
	}

	public static DefaultAttributeContainer.Builder setAttributes() {
		return LivingEntity.createLivingAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0D)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25D)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 20.0D)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 0.3D);
	}

	@Override protected EntityNavigation createNavigation(World world) {
		BirdNavigation nav = new BirdNavigation(this, world);
		nav.setCanPathThroughDoors(false);
		nav.setCanSwim(false);
		return nav;
	}

	// ------------------------------------------------------------------------------------------ safety
	// It died of falls: it flies, but a no-gravity mob still counts the height it comes down (from the night sky to
	// its dawn perch, 10 to 18 blocks; pulled down by a lead), and landing dealt fall damage (up to 46 on its 30 health).
	// It was also a solid box (like a boat): Mulas blocked and shoved each other and could be stood on.

	/** Hurt by nothing of its own flying life: falls, crashes, walls, water, fire, cramming, nor by another Mula. */
	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		if (source.getAttacker() instanceof MulaEntity || source.getSource() instanceof MulaEntity) return true;
		for (RegistryKey<DamageType> type : IMMUNE_TO) {
			if (source.isOf(type)) return true;
		}
		return super.isInvulnerableTo(source);
	}

	private static final List<RegistryKey<DamageType>> IMMUNE_TO = List.of(
			DamageTypes.FALL, DamageTypes.FLY_INTO_WALL,
			DamageTypes.IN_WALL, DamageTypes.CRAMMING,
			DamageTypes.DROWN, DamageTypes.IN_FIRE,
			DamageTypes.ON_FIRE, DamageTypes.LAVA,
			DamageTypes.HOT_FLOOR, DamageTypes.CAMPFIRE);

	/** It flies: landing is never a fall. */
	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
		this.fallDistance = 0;
	}

	/** Not solid: nothing stands on it or is blocked by it (it can still be hit, fed and leashed). */
	@Override
	public boolean isCollidable() {
		return false;
	}

	/** Mulas pass through each other, without pushing. */
	@Override
	public boolean collidesWith(Entity other) {
		return !(other instanceof MulaEntity) && super.collidesWith(other);
	}

	@Override
	public void pushAwayFrom(Entity entity) {
		if (entity instanceof MulaEntity) return;
		super.pushAwayFrom(entity);
	}

	/**
	 * Server: it pushes nothing. Each tick the vanilla code looked through every entity in its box (in a crowd, all
	 * the Mulas around) to push the others, and Mulas never push each other. The clients still nudge their player.
	 * Players and mobs still push it (their own cramming).
	 */
	@Override
	protected void tickCramming() {
		if (this.getWorld().isClient) super.tickCramming();
	}

	/**
	 * What it bumps into among the entities (MulaCollisionMixin): only a player turned into a solid block (Box Costume).
	 * The vanilla query looked through every entity around its path at each move, the other Mulas of a crowd included
	 * (it passes through them anyway); a boat or a shulker no longer stops it.
	 */
	public static List<VoxelShape> entityCollisions(World world, Entity mula, Box box) {
		List<VoxelShape> shapes = null;
		for (PlayerEntity player : world.getPlayers()) {
			if (player.isCollidable() && mula.collidesWith(player) && box.intersects(player.getBoundingBox())) {
				if (shapes == null) shapes = new ArrayList<>(1);
				shapes.add(VoxelShapes.cuboid(player.getBoundingBox()));
			}
		}
		return shapes == null ? List.of() : shapes;
	}

	/**
	 * On a lead: pulled gently towards the holder, like a balloon on a string (the vanilla pull, made for walking mobs,
	 * flung it down and slammed it into the ground): a pull growing with how far it is, capped, never fast downwards.
	 */
	@Override
	public void applyLeashElasticity(Entity holder, float distance) {
		double dx = holder.getX() - this.getX(), dy = holder.getY() + holder.getHeight() * 0.5 - this.getY(),
				dz = holder.getZ() - this.getZ();
		double length = Math.max(1.0E-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
		double pull = Math.min(LEASH_PULL_MAX, (distance - LEASH_SLACK) * LEASH_PULL);
		if (pull <= 0) return;
		Vec3d v = this.getVelocity().add(dx / length * pull, dy / length * pull, dz / length * pull);
		double speed = v.length();
		if (speed > LEASH_SPEED_MAX) v = v.multiply(LEASH_SPEED_MAX / speed);
		if (v.y < -LEASH_DOWN_MAX) v = new Vec3d(v.x, -LEASH_DOWN_MAX, v.z);
		this.setVelocity(v);
		this.velocityModified = true;
	}

	/** Lead: slack length, pull per block beyond it, and the caps (blocks/tick). */
	private static final double LEASH_SLACK = 4, LEASH_PULL = 0.02, LEASH_PULL_MAX = 0.08, LEASH_SPEED_MAX = 0.45,
			LEASH_DOWN_MAX = 0.12;

	/**
	 * The box last found free of blocks (keepOutOfBlocks): looked at again only once it moved or changed size. A block
	 * placed into a Mula hovering still is noticed when it next moves.
	 */
	private @Nullable Box lastFreeBox;

	/** Server: it has grown (a meal) or moved on its own into blocks: it gently rises out instead of suffocating. */
	private void keepOutOfBlocks() {
		Box box = this.getBoundingBox();
		// still where it was found free, the same size: nothing to look at (most Mulas hover in place). Blocks only: the
		// entities round it were looked through too, in a crowd all the other Mulas.
		if (box.equals(lastFreeBox)) return;
		if (this.getWorld().isBlockSpaceEmpty(this, box)) {
			lastFreeBox = box;
			return;
		}
		lastFreeBox = null;
		for (int i = 1; i <= 12; i++) {
			if (this.getWorld().isBlockSpaceEmpty(this, box.offset(0, i * 0.25, 0))) {
				this.setPosition(this.getX(), this.getY() + Math.min(i * 0.25, 0.25), this.getZ());
				return;
			}
		}
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(VARIANT, 0);
		builder.add(HUNGER, 0);
		builder.add(LAST_FOOD, ItemStack.EMPTY);
		builder.add(FEED_COUNT, 0);
		builder.add(FRESH, false);
		builder.add(SHAKING, false);
		builder.add(RESTING, false);
		builder.add(DANCE, -1);
		builder.add(CARRYING, false);
		builder.add(DANCE_FORGE, Optional.empty());
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Variant", this.getVariant().getId());
		nbt.putInt("Hunger", this.getHunger());
		if (homeForge != null) nbt.putIntArray("HomeForge", new int[]{homeForge.getX(), homeForge.getY(), homeForge.getZ()});
		if (spawnSite != 0 && spawnSiteWorld != null) {
			nbt.putInt("SpawnSite", spawnSite);
			nbt.putString("SpawnSiteWorld", spawnSiteWorld.toString());
			nbt.putIntArray("SpawnSitePos", new int[]{spawnSiteX, spawnSiteZ});
		}
		if (!getLastFood().isEmpty()) {
			nbt.putString("LastFood", Registries.ITEM.getId(getLastFood().getItem()).toString());
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.setVariant(MulaVariant.byId(nbt.getInt("Variant")));
		this.setHunger(nbt.getInt("Hunger"));
		int[] home = nbt.getIntArray("HomeForge");
		this.homeForge = home.length == 3 ? new BlockPos(home[0], home[1], home[2]) : null;
		int[] sitePos = nbt.getIntArray("SpawnSitePos");
		this.spawnSiteWorld = nbt.contains("SpawnSiteWorld") ? Identifier.tryParse(nbt.getString("SpawnSiteWorld")) : null;
		this.spawnSite = spawnSiteWorld != null && sitePos.length == 2 ? nbt.getInt("SpawnSite") : 0;
		if (spawnSite != 0) {
			this.spawnSiteX = sitePos[0];
			this.spawnSiteZ = sitePos[1];
		}
		this.spawnSiteEpoch = -1;
		Identifier food = nbt.contains("LastFood") ? Identifier.tryParse(nbt.getString("LastFood")) : null;
		setLastFood(food == null ? ItemStack.EMPTY : new ItemStack(Registries.ITEM.get(food)));
		// it always floats: /summon with any NBT (no "NoGravity" in it) used to give it gravity, and it fell
		this.setNoGravity(true);
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData) {
		this.setVariant(MulaVariant.getRandomVariant());
		this.setHunger(0);
		// a new Mula (spawn egg, summon, spawner...): the clients make it pop in from nothing
		this.dataTracker.set(FRESH, true);
		this.freshTicks = FRESH_TICKS;
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

	/** The last food it ate (the item the clients see it absorb), EMPTY if none or after it burst. */
	public ItemStack getLastFood() {
		return this.dataTracker.get(LAST_FOOD);
	}

	private void setLastFood(ItemStack food) {
		this.dataTracker.set(LAST_FOOD, food);
	}

	/** How many meals it has had (only used to show each meal on the clients). */
	public int getFeedCount() {
		return this.dataTracker.get(FEED_COUNT);
	}

	/**
	 * Nearly full (past {@link MulaMotion#TREMBLE_FROM}) and on edge: a player (not a spectator) within
	 * {@link #SHAKE_NEAR} blocks of its body, or a meal taken less than {@link #SHAKE_AFTER_MEAL_TICKS} ago. Only then
	 * do the clients let it tremble; the rest of the time a big Mula stays calm (its trembling all the time was
	 * unsettling). Synced, so every player sees the same.
	 */
	public boolean isShaking() {
		return this.dataTracker.get(SHAKING);
	}

	/** Server: refreshes {@link #isShaking} (sent to the clients only when it changes). */
	private void updateShaking() {
		boolean shaking = (float) getHunger() / MAX_HUNGER > MulaMotion.TREMBLE_FROM
				&& (shakeTicks > 0 || isPlayerVeryClose());
		if (shaking != isShaking()) this.dataTracker.set(SHAKING, shaking);
	}

	private boolean isPlayerVeryClose() {
		Box near = this.getBoundingBox().expand(SHAKE_NEAR);
		for (PlayerEntity player : this.getWorld().getPlayers()) {
			if (player.isAlive() && !player.isSpectator() && near.intersects(player.getBoundingBox())) return true;
		}
		return false;
	}

	/** Just spawned (the first 2 seconds): it pops in on the clients. */
	public boolean isFresh() {
		return this.dataTracker.get(FRESH);
	}

	// -------------------
	// Feeding interaction
	// -------------------
	@Override
	public ActionResult interactMob(PlayerEntity player, Hand hand) {
		// A board token is a static pawn: not fed, tamed nor sat down (the items acting on entities still work)
		if (isToken()) return ActionResult.PASS;
		ItemStack stack = player.getStackInHand(hand);

		// Tools acting on entities (tokenizer wand, token, name tag, lead) keep working on a Mula
		if (stack.getItem() instanceof TokenizerWandItem || stack.getItem() instanceof TokenItem
				|| stack.isOf(Items.NAME_TAG) || stack.isOf(Items.LEAD)) {
			return ActionResult.PASS;
		}

		// Taming: right-click an untamed Mula with a star fragment of its own colour (1 fragment, 1 chance in
		// TAMING_CHANCE), like a bone on a wolf. On a tamed Mula a fragment is not used: see below.
		if (!this.isTamed() && stack.isOf(this.getVariant().getFragmentItem())) {
			if (!this.getWorld().isClient) {
				tryTame(player, stack);
			}
			return ActionResult.SUCCESS;
		}

		// Sit / stand: the owner right-clicks a tamed Mula with an empty hand or anything no Mula eats (a fragment
		// included: it is not used up); its food keeps feeding it, another colour's food is refused (said why). Anyone
		// else gets the "no".
		if (this.isTamed() && this.isOwner(player) && !isMulaFood(stack) && MulaFood.eatenBy(stack) == null) {
			if (!this.getWorld().isClient) {
				toggleSitting();
			}
			return ActionResult.SUCCESS;
		}

		// Still chewing, or not its food: shakes its head. Played from the server only: the client used to play it
		// too, then again when the server's order came back, which restarted it halfway (a visible hiccup).
		if (eatCooldown > 0 || !isMulaFood(stack)) {
			if (!this.getWorld().isClient) {
				refuse(player, stack);
			}
			return ActionResult.SUCCESS;
		}

		// Its colour and edible (MulaFood): a food gives its nutrition, a potion minutes x (level x 2)
		int hungerValue = MulaFood.value(this.getVariant(), stack);
		int newHunger = Math.min(getHunger() + hungerValue, MAX_HUNGER);
		setHunger(newHunger);

		if (!player.getWorld().isClient) {
			boolean potion = stack.isOf(Items.POTION);
			Text message = potion
					? Text.translatable("message.steveparty.mula.feed.potion", getHunger(), MAX_HUNGER, stack.getName(),
							hungerValue, MulaFood.potionFormula(stack.getOrDefault(
									DataComponentTypes.POTION_CONTENTS,
									PotionContentsComponent.DEFAULT).getEffects()))
					: Text.translatable("message.steveparty.mula.feed", getHunger(), MAX_HUNGER, stack.getName(), hungerValue);
			message = message.copy().styled(style -> style.withColor(this.getVariant().getColor()));
			player.sendMessage(message, true);

			// the clients see what it ate melt into light and spiral into it
			setLastFood(stack.copyWithCount(1));
			this.dataTracker.set(FEED_COUNT, getFeedCount() + 1);
			bellyClearTicks = 0;
			shakeTicks = SHAKE_AFTER_MEAL_TICKS;
			if (potion) {
				// like drinking it: the empty bottle goes back to the player
				player.setStackInHand(hand, ItemUsage.exchangeStack(stack, player,
						new ItemStack(Items.GLASS_BOTTLE)));
			} else {
				stack.decrementUnlessCreative(1, player);
			}

			// Explode if max hunger
			if (getHunger() >= MAX_HUNGER) {
				playSpecial("explode", EXPLODE_TICKS);
				setHunger(0);
				dropFragmentStars(fragmentsOnBurst(getVariant()));
				bellyClearTicks = BELLY_CLEAR_TICKS;
				// at the pop it flies away as a shooting star, to be reborn far away
				starLaunchTicks = STAR_LAUNCH_TICKS;
			} else {
				playSpecial("celebrate", CELEBRATE_TICKS);
			}
			// Set cooldown for 1 second (20 ticks)
			eatCooldown = 20;
			updateShaking();
		}

		return ActionResult.SUCCESS;
	}

	/** A player told why it refuses gets no new "no" before this many ticks (clicking on and on). */
	public static final int REFUSAL_GAP_TICKS = 20;
	private @Nullable UUID lastRefused;
	private int lastRefusalAge = -REFUSAL_GAP_TICKS;
	private int refusals;

	/** How many times it said "no" (for the tests). */
	public int refusals() {
		return refusals;
	}

	/**
	 * Server: "no" to what this player holds out. It shakes its head (its clients float the item up and back down, puff
	 * a little grey cloud) and, when something was held out, says why in the player's action bar: food of another
	 * colour (and whose), or something no Mula eats. Still taking in its last meal, it only shakes its head. At most
	 * once per {@value #REFUSAL_GAP_TICKS} ticks for the same player.
	 */
	private void refuse(PlayerEntity player, ItemStack stack) {
		if (player.getUuid().equals(lastRefused) && this.age - lastRefusalAge < REFUSAL_GAP_TICKS) return;
		lastRefused = player.getUuid();
		lastRefusalAge = this.age;
		refusals++;
		playSpecial("no", NO_TICKS);
		Text reason = refusalReason(getVariant(), stack);
		if (reason != null) player.sendMessage(reason, true);
	}

	/**
	 * Why a Mula of this colour refuses this stack (action bar), null when it would eat it or nothing is held out: food
	 * of another colour (its colour, the item, the colour that eats it), or anything no Mula eats.
	 */
	public static @Nullable Text refusalReason(MulaVariant variant, ItemStack stack) {
		if (stack.isEmpty() || MulaFood.value(variant, stack) > 0) return null;
		MulaVariant eater = MulaFood.eatenBy(stack);
		Text message = eater != null
				? Text.translatable("message.steveparty.mula.refuse.colour", colourName(variant), stack.getName(), colourName(eater))
				: Text.translatable("message.steveparty.mula.refuse.inedible", stack.getName());
		return message.copy().formatted(Formatting.GRAY);
	}

	/** A Mula colour's name, in that colour (lightened: the black one stays readable). */
	private static Text colourName(MulaVariant variant) {
		return Text.translatable("mula.steveparty.colour." + variant.name().toLowerCase(Locale.ROOT))
				.styled(style -> style.withColor(variant.getHaloColor()));
	}

	/** Every animation the server can start ({@code /mula <mulas> play <animation>}). */
	public static List<String> animationNames() {
		List<String> names = new ArrayList<>(List.of("explode", "celebrate", "no"));
		names.addAll(List.of(FEATURE_ANIMS));
		for (Emote emote : EMOTES) names.add(emote.animName);
		return names;
	}

	/** Server: plays one of its animations, like the game does (operator command). */
	public void playAnimation(String animName) {
		for (Emote emote : EMOTES) {
			if (emote.animName.equals(animName)) {
				triggerAnim(MAIN_CONTROLLER, animName);
				currentEmote = lastEmote = emote;
				currentSpecial = null;
				specialAnimTicks = emote.ticks;
				return;
			}
		}
		playSpecial(animName, 100);
	}

	/** Server: plays a triggered animation (replacing a random one) and holds the random ones back meanwhile. */
	private void playSpecial(String animName, int ticks) {
		triggerAnim(MAIN_CONTROLLER, animName);
		currentEmote = null;
		currentSpecial = animName;
		specialAnimTicks = ticks;
	}

	/** @return true if this Mula eats this item: of its colour AND edible (a food, or a potion), see {@link MulaFood}. */
	public boolean isMulaFood(ItemStack stack) {
		return MulaFood.value(this.getVariant(), stack) > 0;
	}

	/** Its own voice: a pitch factor 0.92 to 1.08 from its UUID (each Mula sounds a little different). */
	public float voice() {
		return 0.92f + Math.floorMod(this.getUuid().hashCode(), 17) / 100f;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_ALLAY_HURT;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_ALLAY_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.6f;
	}

	@Override
	public float getSoundPitch() {
		return voice() * (1f + (this.random.nextFloat() - 0.5f) * 0.1f);
	}

	/** Owner's order, like vanilla wolves: the state is saved by {@link TameableEntity} ("Sitting"). */
	public void toggleSitting() {
		this.setSitting(!this.isSitting());
		this.jumping = false;
		this.navigation.stop();
		this.setTarget(null);
		stopEmote();
		// an "okay" nod settling into its rest, or a stretch and a hop back up, with a soft Allay voice
		this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM, SoundCategory.NEUTRAL,
				0.3f, (this.isSitting() ? 0.85f : 1.25f) * voice());
		if (this.isSitting()) {
			playSpecial("sit_down", SIT_DOWN_TICKS);
		} else {
			playSpecial("stand_up", STAND_UP_TICKS);
		}
	}

	/** Like vanilla wolves: consumes one fragment, 1 in {@value #TAMING_CHANCE} chance, hearts or smoke. */
	private void tryTame(PlayerEntity player, ItemStack stack) {
		stack.decrementUnlessCreative(1, player);
		tameAttempt(player, this.random.nextInt(TAMING_CHANCE) == 0);
	}

	/** Outcome of a taming attempt (also used by the /mula operator command, which forces it). */
	public void tameAttempt(PlayerEntity player, boolean success) {
		if (success) {
			this.setOwner(player);
			// as many Mulas follow them as they may: this one will wait where it is
			if (this.getWorld() instanceof ServerWorld world && MulaEscorts.isFull(world, player.getUuid())) {
				player.sendMessage(Text.translatable("message.steveparty.mula.escort_full", MulaEscorts.max())
						.formatted(Formatting.GRAY), true);
			}
			this.navigation.stop();
			this.getWorld().sendEntityStatus(this, EntityStatuses.ADD_POSITIVE_PLAYER_REACTION_PARTICLES);
			playSpecial("tame_joy", TAME_JOY_TICKS);
		} else {
			this.getWorld().sendEntityStatus(this, EntityStatuses.ADD_NEGATIVE_PLAYER_REACTION_PARTICLES);
			playSpecial("shy", SHY_TICKS);
		}
	}

	/** Tamed: a ring of twinkles and a chime with the hearts. */
	@Override
	public void handleStatus(byte status) {
		super.handleStatus(status);
		if (status == EntityStatuses.ADD_POSITIVE_PLAYER_REACTION_PARTICLES) {
			effects.onTamed();
		} else if (status == EntityStatuses.ADD_NEGATIVE_PLAYER_REACTION_PARTICLES) {
			effects.onTameFailed();
		} else if (status == MulaRebirths.REBORN_STATUS) {
			effects.onReborn();
		}
	}

	/** Client: a meal (the feed counter changed) shows the food being absorbed. Both sides: size. */
	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		if (DANCE.equals(data) && this.getWorld() != null && this.getWorld().isClient && this.age > 0) {
			// someone joined or left the dance: blend from its previous place (the server does the same)
			int now = this.dataTracker.get(DANCE);
			boolean dancing = now >= 0 && (now & DANCE_SPECTATOR) == 0;
			boolean danced = lastSeenDance >= 0 && (lastSeenDance & DANCE_SPECTATOR) == 0;
			if (dancing && (!danced || (now & DANCE_PLACE) != (lastSeenDance & DANCE_PLACE))) {
				noteDanceChange(lastSeenDance);
			}
			if (dancing && (now & DANCE_LOCKED) != 0 && (!danced || (lastSeenDance & DANCE_LOCKED) == 0)) lockBlendTicks = 8;
			lastSeenDance = now;
		}
		super.onTrackedDataSet(data);
		// its size follows its hunger (getScaleFactor): refresh the hitbox at once, it only did on a pose change
		if (HUNGER.equals(data)) {
			this.calculateDimensions();
		}
		if (FEED_COUNT.equals(data) && this.getWorld().isClient && this.age > 0) {
			effects.onFed();
		}
	}

	private void dropFragmentStars(int count) {
		if (this.getWorld().isClient) return;
		Item fragmentItem = this.getVariant().getFragmentItem();
		for (int i = 0; i < count; i++) {
			this.dropItem(fragmentItem);
		}
	}

	// ------------------------------------------------------------------------------------------ random animations

	/** Server, every tick: a random character animation from time to time, when nothing else is going on. */
	private void tickEmotes() {
		if (specialAnimTicks > 0 && --specialAnimTicks == 0) {
			currentEmote = null;
			currentSpecial = null;
		}
		if (emoteCooldown < 0) {
			emoteCooldown = EMOTE_MIN_TICKS + this.random.nextInt(EMOTE_RANDOM_TICKS);
			return;
		}
		if (--emoteCooldown > 0) return;
		emoteCooldown = EMOTE_MIN_TICKS + this.random.nextInt(EMOTE_RANDOM_TICKS);
		if (!canEmote()) return;
		boolean sitting = this.isInSittingPose() || this.isResting();
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
				&& !brain.isShy() && !brain.isPlaying() && !isDancing()
				&& !this.hasVehicle() && !this.hasPassengers() && !this.isLeashed()
				&& !isToken()
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

	/** Client, every tick: float layer, fly / hover state, blinks, effects. Plain arithmetic, no allocation. */
	private int lastSeenDance = -1;

	private void tickClientAnimation() {
		if (isCarrying()) effects.carryTick();
		if (isDanceLocked()) {
			followDance();
			effects.danceTick();
		} else if (isSpectating()) {
			effects.spectatorTick(danceSlot());
		}
		if ((this.age & 3) == 0) {
			// a player close by holding its food: it turns to them, eyes wide (all players see the same: it only
			// depends on synced things, the players' positions and held items)
			PlayerEntity player = this.getWorld().getClosestPlayer(this, 5.0);
			excited = player != null && (isMulaFood(player.getMainHandStack()) || isMulaFood(player.getOffHandStack()));
			if (excited) {
				float toPlayer = (float) (MathHelper.atan2(player.getZ() - this.getZ(), player.getX() - this.getX())
						* MathHelper.DEGREES_PER_RADIAN) - 90f;
				excitedYaw = MathHelper.wrapDegrees(toPlayer - this.bodyYaw);
			}
		}
		effects.tick(this.serverX, this.serverY, this.serverZ);
		motion.tick(this.isInSittingPose(), this.getX() - this.prevX, this.getY() - this.prevY, this.getZ() - this.prevZ,
				this.bodyYaw, MathHelper.wrapDegrees(this.bodyYaw - this.prevBodyYaw), this.getScaleFactor(),
				(float) this.getHunger() / MAX_HUNGER, excited, excitedYaw, isShaking());

		if (--blinkCooldown <= 0) {
			blinkCooldown = 50 + this.random.nextInt(110); // every 2.5 to 8 s
			if (canBlink()) {
				triggerAnim(BLINK_CONTROLLER, this.random.nextInt(5) == 0 ? "blink_double" : "blink");
			}
		}
	}

	/**
	 * Client, every tick while it is a board token: a still pawn. No float layer (MulaMotion rests), no effects nor voice,
	 * no blink, no look at food; a triggered animation still running when it became one is cut (the model then settles
	 * in its rest pose, see animationPredicate).
	 */
	private void tickClientAsToken() {
		excited = false;
		effects.pawnTick(this.serverX, this.serverY, this.serverZ);
		motion.tickPawn(this.getScaleFactor(), (float) this.getHunger() / MAX_HUNGER);
		AnimatableManager<?> manager = getAnimatableInstanceCache().getManagerForId(this.getId());
		if (manager == null) return;
		for (String controller : CONTROLLERS) {
			AnimationController<?> c = manager.getAnimationControllers().get(controller);
			if (c != null && c.isPlayingTriggeredAnimation()) stopTriggeredAnim(controller, null);
		}
	}

	/**
	 * Age its time-driven looks (drifting inner lights...) are drawn at: frozen on the frame it became a board token
	 * (a still pawn), its age otherwise.
	 */
	public float animationAge(float partialTick) {
		int pawnAge = ((TokenizedEntityInterface) this).steveparty$getPawnAge();
		return pawnAge >= 0 ? pawnAge : this.age + partialTick;
	}

	/** Not when its eyes are already busy: sitting (half-closed, own slow blink) or in a triggered animation. */
	private boolean canBlink() {
		if (!this.isAlive() || this.isInSittingPose() || this.isResting()) return false;
		AnimatableManager<?> manager = getAnimatableInstanceCache().getManagerForId(this.getId());
		AnimationController<?> main = manager == null ? null : manager.getAnimationControllers().get(MAIN_CONTROLLER);
		return main == null || !main.isPlayingTriggeredAnimation();
	}

	public MulaMotion getMotion() {
		return motion;
	}

	public MulaEffects getEffects() {
		return effects;
	}

	// ------------------------------------------------------------------------------------------ effects (client)

	/** Star dust in the Mula's own colour, lightened so even the black one sparkles. */
	public ParticleEffect starDust() {
		return getVariant().getStarDust();
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
				.setCustomInstructionKeyframeHandler(event -> effects.instruction(event.getKeyframeData().getInstructions()));
		for (Emote emote : EMOTES) {
			main.triggerableAnim(emote.animName, emote.animation);
		}
		for (String feature : FEATURE_ANIMS) {
			main.triggerableAnim(feature, RawAnimation.begin().thenPlay(feature));
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
		// a board token: its idle, gently floating in place (stopped, the model fell back to its bind pose)
		if (isToken()) return state.setAndContinue(IDLE_ANIM);
		if (isDanceLocked()) {
			return state.setAndContinue(DANCE_ANIMS[MulaDances.STYLE[Math.max(0, currentDance())]]);
		}
		if (this.isInSittingPose() || this.isResting()) {
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
		private final int glowColor;
		private final MulaSparkleEffect twinkle;

		MulaVariant(int id, int color, Item fragmentItem) {
			this.id = id;
			this.color = color;
			this.fragmentItem = fragmentItem;
			this.starDust = new DustParticleEffect(Vec3d.unpackRgb(Argb.lighten(color, id == 5 ? 0.3f : 0.45f)).toVector3f(), 0.7f);
			// the black one glows white: a black light would not show
			this.glowColor = id == 5 ? 0xFFFFFF : Argb.lighten(color, 0.5f);
			this.twinkle = new MulaSparkleEffect(glowColor, 1f, MulaSparkleEffect.TWINKLE);
		}

		public int getId() { return id; }
		public int getColor() { return color; }
		public Item getFragmentItem() { return fragmentItem; }
		public DustParticleEffect getStarDust() { return starDust; }
		/** Colour of its twinkles, inner lights and tooltip (its colour, lightened; white for the black one). */
		public int getGlowColor() { return glowColor; }

		/** Colour of its halo: its own colour, only a little lightened so each Mula shines in its colour; white for the black one. */
		public int getHaloColor() { return id == 5 ? 0xFFFFFF : Argb.lighten(color, 0.2f); }
		public MulaSparkleEffect getTwinkle() { return twinkle; }

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
	public boolean damage(DamageSource source, float amount) {
		boolean damaged = super.damage(source, amount);
		if (damaged) {
			// shy: it flees a short way and hides (behind its owner, in leaves...), peeks out and comes back
			if (this.isAlive()) brain.onHurt(source.getAttacker());
			stopEmote();
			if (this.isSitting()) {
				this.setSitting(false);
			}
			// a flinch and a few stars knocked out of it (not over its burst)
			if (this.isAlive() && !"explode".equals(currentSpecial)) {
				playSpecial("ouch", OUCH_TICKS);
			}
		}
		return damaged;
	}
}
