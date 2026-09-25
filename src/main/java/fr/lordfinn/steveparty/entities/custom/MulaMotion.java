package fr.lordfinn.steveparty.entities.custom;

import net.minecraft.util.math.MathHelper;

/**
 * Client-side motion state of a Mula, the "float layer" of its animation: a Luma-like hover that never stops (bob with
 * squash &amp; stretch, a slow sway, a lean into its flight and a bank into its turns), the fly / idle choice, and a
 * springy visual size. Updated once per client tick with plain arithmetic (no allocation), read at render time with
 * the partial tick so everything is interpolated between ticks.
 * <p>
 * The bob runs on its own phase, continuous across every animation change (idle, fly, sit, the random ones...): a
 * switch of animation never restarts or breaks the float. Its speed and depth ease from one state to the other, and
 * each Mula starts at its own phase so they never bob in sync.
 * <p>
 * Readable states on top of it: how full it is (the core swells and glows faster, the halo grows, and near bursting it
 * trembles, but only while it is on edge: a player very close or a meal just taken, see {@link MulaEntity#isShaking};
 * the rest of the time a big Mula stays calm and only its glow and leaking twinkles tell it is nearly full), and the
 * excitement when a player close by holds its food (it turns to face them, eyes wide, bobbing and flapping faster).
 * <p>
 * Keep the constants in sync with {@code the art sources} (Motion), which renders the previews.
 */
public final class MulaMotion {
	/** Bob amplitude (px), period (ticks), squash &amp; stretch amount, sway (deg): idle, fly, sit. */
	private static final float[] AMPLITUDE = {1.1f, 0.6f, 0.45f};
	private static final float[] PERIOD = {44f, 30f, 70f};
	private static final float[] SQUASH = {0.035f, 0.02f, 0.02f};
	private static final float[] SWAY = {3.0f, 1.5f, 1.0f};
	private static final int IDLE = 0, FLY = 1, SIT = 2;
	/** Share of the way to the new state's bob settings covered each tick (smooth change of rhythm). */
	private static final float MODE_EASE = 0.06f;

	/** Smoothed speed (blocks/tick) above which the Mula flies, below which it hovers again (hysteresis). */
	private static final float FLY_ABOVE = 0.07f, HOVER_BELOW = 0.03f;
	/** Minimum ticks in a state before switching (no flicker on a speed hovering around a threshold). */
	private static final int MIN_FLY_TICKS = 10, MIN_HOVER_TICKS = 6;

	/** Lean / bank / look springs: pulled by this share of the gap, velocity kept by this share (slight overshoot). */
	private static final float SPRING = 0.10f, DAMPING = 0.72f;
	/** Springy visual size: it swells with a bouncy "gulp" when fed, pops in from nothing when it appears. */
	private static final float SCALE_SPRING = 0.24f, SCALE_DAMPING = 0.64f;
	/** The phase is kept below this (the sway runs at half the bob frequency: 4 pi is a whole cycle of both). */
	private static final float PHASE_WRAP = (float) (4 * Math.PI);
	/** Fullness above which the Mula trembles (while on edge), more and more until it bursts. */
	public static final float TREMBLE_FROM = 0.7f;
	/** Share of the way to "on edge" / "calm" covered each tick: the tremble fades in over ~0.3 s, out over ~1 s. */
	private static final float SHAKE_IN = 0.18f, SHAKE_OUT = 0.07f;
	/** Most the Mula turns to face a player holding its food (degrees). */
	private static final float MAX_LOOK = 70f;

	private float phase, prevPhase;
	private float amplitude, omega, squash, sway;
	private float prevAmplitude, prevSquash, prevSway;
	private final float swayOffset;
	private float lean, prevLean, leanVelocity;
	private float bank, prevBank, bankVelocity;
	private float look, prevLook, lookVelocity;
	private float excitement, prevExcitement;
	private float fullness, prevFullness;
	private float glowPhase, prevGlowPhase;
	private float tremblePhase, prevTremblePhase;
	/** 0..1, how much it lets its tremble show: eased towards 1 while on edge, towards 0 when calm (no popping). */
	private float shake, prevShake;
	private float flare, prevFlare;
	/** What the flare / meal glow ease towards: set at once by a burst, a beat or a meal, then fading. */
	private float flareTarget, absorbTarget;
	/** How fast the halo follows its target each tick: a soft swell instead of a flash. */
	private static final float HALO_EASE = 0.18f;
	private float speed;
	private boolean flying;
	private int ticksInState;
	private boolean ticked;
	private float visualScale = -1, prevVisualScale, visualScaleVelocity, shownScale = -1, shownFull;
	private int shrinkHold, growHold;
	/** A meal's light reaches its heart this many ticks after it was given (MulaEffects.ABSORB_TICKS). */
	private static final int GROW_DELAY_TICKS = MulaEffects.ABSORB_TICKS;
	/** The warm glow spreading from its heart when a meal's light sinks in (1 then fades). */
	private float absorbGlow, prevAbsorbGlow;
	/** Ticks from the burst order to the pop (explode: 8 ticks of blend + 0.92 s), when its size can snap back. */
	private static final int BURST_HOLD_TICKS = 30;

	public MulaMotion(int seed) {
		// golden-ratio hashing of the entity id: neighbours get well spread phases
		float r = (seed * 0.6180339887f) % 1f;
		this.phase = this.prevPhase = r * PHASE_WRAP;
		this.glowPhase = this.prevGlowPhase = r * MathHelper.TAU;
		this.swayOffset = r * 5.1f;
		this.amplitude = this.prevAmplitude = AMPLITUDE[IDLE];
		this.omega = MathHelper.TAU / PERIOD[IDLE];
		this.squash = this.prevSquash = SQUASH[IDLE];
		this.sway = this.prevSway = SWAY[IDLE];
	}

	/**
	 * @param dx dy dz   movement of the entity during the last tick (client interpolated position)
	 * @param bodyYaw    body yaw (degrees), to know what is "forward"
	 * @param yawDelta   change of body yaw during the last tick (degrees)
	 * @param scale      current size factor of the entity ({@code getScaleFactor})
	 * @param full       how full it is, 0..1 (hunger / max)
	 * @param excited    a player close by holds its food
	 * @param lookTarget where that player is, relative to the body yaw (degrees)
	 * @param shaking    on edge (synced from the server): a player very close, or a meal just taken
	 */
	public void tick(boolean sitting, double dx, double dy, double dz, float bodyYaw, float yawDelta, float scale,
					 float full, boolean excited, float lookTarget, boolean shaking) {
		ticked = true;
		prevPhase = phase;
		prevAmplitude = amplitude;
		prevSquash = squash;
		prevSway = sway;
		prevLean = lean;
		prevBank = bank;
		prevLook = look;
		prevExcitement = excitement;
		prevFullness = fullness;
		prevGlowPhase = glowPhase;
		prevTremblePhase = tremblePhase;
		prevShake = shake;
		prevFlare = flare;
		prevAbsorbGlow = absorbGlow;
		prevVisualScale = visualScale < 0 ? scale : visualScale;

		// fly / hover, from the smoothed speed, with hysteresis and a minimum time in each state
		float sample = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		speed += (sample - speed) * 0.35f;
		ticksInState++;
		if (!flying && speed > FLY_ABOVE && ticksInState >= MIN_HOVER_TICKS) {
			flying = true;
			ticksInState = 0;
		} else if (flying && speed < HOVER_BELOW && ticksInState >= MIN_FLY_TICKS) {
			flying = false;
			ticksInState = 0;
		}

		excitement += ((excited && !sitting ? 1f : 0f) - excitement) * 0.12f;

		int mode = sitting ? SIT : flying ? FLY : IDLE;
		amplitude += (AMPLITUDE[mode] - amplitude) * MODE_EASE;
		omega += (MathHelper.TAU / PERIOD[mode] - omega) * MODE_EASE;
		squash += (SQUASH[mode] - squash) * MODE_EASE;
		sway += (SWAY[mode] - sway) * MODE_EASE;
		phase += omega * (1f + 0.6f * excitement + 0.3f * fullness);
		if (phase > PHASE_WRAP) {
			phase -= PHASE_WRAP;
			prevPhase -= PHASE_WRAP;
		}
		// the heart beats faster the fuller it is; the tremble runs on its own quick phase
		glowPhase += 0.12f + 0.3f * fullness;
		if (glowPhase > MathHelper.TAU) {
			glowPhase -= MathHelper.TAU;
			prevGlowPhase -= MathHelper.TAU;
		}
		tremblePhase += 1.7f;
		if (tremblePhase > MathHelper.TAU) {
			tremblePhase -= MathHelper.TAU;
			prevTremblePhase -= MathHelper.TAU;
		}
		shake += ((shaking ? 1f : 0f) - shake) * (shaking ? SHAKE_IN : SHAKE_OUT);
		if (!shaking && shake < 0.002f) shake = 0f;
		flareTarget = Math.max(0f, flareTarget - 0.03f);
		flare += (flareTarget - flare) * HALO_EASE;

		// lean into the flight (forward speed), back when climbing; bank into turns; springs overshoot a little
		float yawRad = bodyYaw * MathHelper.RADIANS_PER_DEGREE;
		double forward = -dx * MathHelper.sin(yawRad) + dz * MathHelper.cos(yawRad);
		float leanTarget = MathHelper.clamp((float) forward * 80f, -8f, 20f) - MathHelper.clamp((float) dy * 50f, -6f, 6f);
		leanVelocity = (leanVelocity + (leanTarget - lean) * SPRING) * DAMPING;
		lean += leanVelocity;
		float bankTarget = MathHelper.clamp(-yawDelta * 1.2f, -12f, 12f);
		bankVelocity = (bankVelocity + (bankTarget - bank) * SPRING) * DAMPING;
		bank += bankVelocity;
		// turn to face the player holding its food
		float lookGoal = MathHelper.clamp(lookTarget, -MAX_LOOK, MAX_LOOK) * excitement;
		lookVelocity = (lookVelocity + (lookGoal - look) * SPRING) * DAMPING;
		look += lookVelocity;

		// Size and inner lights: a meal shows once its light has sunk in (GROW_DELAY_TICKS, see MulaEffects), then the
		// size springs up with a gentle overshoot; after a burst they wait for the pop (the explode animation hides it)
		if (visualScale < 0 || shownScale < 0) {
			// first tick (or just popped in: the spring grows it from nothing)
			if (visualScale < 0) visualScale = scale;
			shownScale = scale;
			shownFull = full;
		}
		if (scale < shownScale - 0.5f && shrinkHold == 0) {
			shrinkHold = BURST_HOLD_TICKS;
			growHold = 0;
		} else if (scale > shownScale + 0.001f && growHold == 0 && shrinkHold == 0) {
			growHold = GROW_DELAY_TICKS;
		}
		if (shrinkHold > 0) {
			if (--shrinkHold == 0) {
				shownScale = visualScale = scale;
				shownFull = full;
				visualScaleVelocity = 0;
			}
		} else if (growHold > 0) {
			if (--growHold == 0) {
				shownScale = scale;
				shownFull = full;
			}
		} else {
			if (scale < shownScale - 0.001f) {
				visualScale = scale;
				visualScaleVelocity = 0;
			}
			shownScale = scale;
			shownFull = full;
		}
		visualScaleVelocity = (visualScaleVelocity + (shownScale - visualScale) * SCALE_SPRING) * SCALE_DAMPING;
		visualScale += visualScaleVelocity;
		fullness += (shownFull - fullness) * 0.12f;
		absorbTarget = Math.max(0f, absorbTarget - 0.025f);
		absorbGlow += (absorbTarget - absorbGlow) * HALO_EASE;
	}

	/** It has just appeared (spawn egg, summon...): its visual size pops in from nothing. */
	public void popIn() {
		visualScale = prevVisualScale = 0.02f;
		visualScaleVelocity = 0;
		shownScale = -1;
	}

	/** A burst of light (glow_rings): the halo swells and brightens, then fades back over ~1.5 s. */
	public void flare() {
		flareTarget = 1f;
	}

	/** A beat of a dance: the halo pulses (a little less than a flare). */
	public void beat() {
		flareTarget = Math.max(flareTarget, 0.55f);
	}

	/** A meal's light has sunk in: a warm glow spreads from its heart and fades over 2 s. */
	public void absorbGlow() {
		absorbTarget = 1f;
	}

	/** 0..1, the warm glow of a meal just taken in. */
	public float absorbGlow(float partialTick) {
		return MathHelper.lerp(partialTick, prevAbsorbGlow, absorbGlow);
	}

	public boolean isFlying() {
		return flying;
	}

	public float speed() {
		return speed;
	}

	public float phase(float partialTick) {
		return MathHelper.lerp(partialTick, prevPhase, phase);
	}

	/** 0..1, the heartbeat of the core and halo (faster the fuller it is). */
	public float glow(float partialTick) {
		return 0.5f + 0.5f * MathHelper.sin(MathHelper.lerp(partialTick, prevGlowPhase, glowPhase));
	}

	/** 0..1, how full it is (smoothed). */
	public float fullness(float partialTick) {
		return MathHelper.lerp(partialTick, prevFullness, fullness);
	}

	/**
	 * 0..1, how hard it trembles right now: grows from {@link #TREMBLE_FROM} to bursting, times how much it is on edge
	 * (eased, so the tremble fades in and out). 0 for a calm Mula, however full.
	 */
	public float tremble(float partialTick) {
		float full = fullness(partialTick);
		if (full <= TREMBLE_FROM) return 0f;
		return Math.min(1f, (full - TREMBLE_FROM) / (1f - TREMBLE_FROM)) * MathHelper.lerp(partialTick, prevShake, shake);
	}

	/** 0..1, the glow_rings flare. */
	public float flareLevel(float partialTick) {
		return MathHelper.lerp(partialTick, prevFlare, flare);
	}

	/**
	 * Size it is drawn at: its size factor (it grows as it eats, like its hitbox), springy (0 before its first tick).
	 * GeckoLib only applies the scale attribute, so without this the Mula never looked bigger, only its hitbox grew.
	 */
	public float visualScaleRatio(float partialTick, float scale) {
		if (!ticked) return 0f;
		if (visualScale < 0) return scale;
		return MathHelper.lerp(partialTick, prevVisualScale, visualScale);
	}

	/** Values of the float layer at render time, in animation-json units (degrees, pixels), written into {@code out}. */
	public void layer(float partialTick, Layer out) {
		float p = phase(partialTick);
		float amp = MathHelper.lerp(partialTick, prevAmplitude, amplitude);
		float sq = MathHelper.lerp(partialTick, prevSquash, squash);
		float sw = MathHelper.lerp(partialTick, prevSway, sway);
		float ex = MathHelper.lerp(partialTick, prevExcitement, excitement);
		float full = fullness(partialTick);
		float rel = amp / AMPLITUDE[IDLE];
		float s = sq * MathHelper.sin(p - 0.5f);
		float tremble = 2.2f * tremble(partialTick);
		float tp = MathHelper.lerp(partialTick, prevTremblePhase, tremblePhase);
		out.posY = amp * MathHelper.sin(p);
		out.posX = 0.35f * amp * MathHelper.sin(0.5f * p + swayOffset) + 0.25f * tremble * MathHelper.sin(tp * 1.3f);
		out.pitch = MathHelper.lerp(partialTick, prevLean, lean) + 1.2f * rel * MathHelper.sin(p - 1.2f);
		out.roll = sw * MathHelper.sin(0.5f * p + swayOffset) + MathHelper.lerp(partialTick, prevBank, bank)
				+ tremble * MathHelper.sin(tp);
		out.yaw = MathHelper.lerp(partialTick, prevLook, look);
		out.scaleY = 1 + s;
		out.scaleXZ = 1 - 0.5f * s;
		out.handFlutter = 6f * rel * MathHelper.sin(p - 1.4f) * (1f + 1.2f * ex);
		out.eyeWiden = 1f + 0.15f * ex;
		out.coreScale = 1f + 0.45f * full + (0.05f + 0.12f * full) * (2f * glow(partialTick) - 1f)
				+ 0.35f * absorbGlow(partialTick);
	}

	/** Reusable holder for {@link #layer} (one per renderer, no allocation per frame). */
	public static final class Layer {
		public float posX, posY, pitch, roll, yaw, scaleY, scaleXZ, handFlutter, eyeWiden, coreScale;
	}
}
