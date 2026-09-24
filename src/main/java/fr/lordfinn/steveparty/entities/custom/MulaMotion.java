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

	/** Lean / bank springs: pulled by this share of the gap, velocity kept by this share (slight overshoot). */
	private static final float SPRING = 0.10f, DAMPING = 0.72f;
	/** Springy visual size ("gulp" when it grows after eating). */
	private static final float SCALE_SPRING = 0.18f, SCALE_DAMPING = 0.7f;
	/** The phase is kept below this (the sway runs at half the bob frequency: 4 pi is a whole cycle of both). */
	private static final float PHASE_WRAP = (float) (4 * Math.PI);

	private float phase, prevPhase;
	private float amplitude, omega, squash, sway;
	private float prevAmplitude, prevSquash, prevSway;
	private final float swayOffset;
	private float lean, prevLean, leanVelocity;
	private float bank, prevBank, bankVelocity;
	private float speed;
	private boolean flying;
	private int ticksInState;
	private float visualScale = -1, prevVisualScale, visualScaleVelocity;

	public MulaMotion(int seed) {
		// golden-ratio hashing of the entity id: neighbours get well spread phases
		float r = (seed * 0.6180339887f) % 1f;
		this.phase = this.prevPhase = r * PHASE_WRAP;
		this.swayOffset = r * 5.1f;
		this.amplitude = this.prevAmplitude = AMPLITUDE[IDLE];
		this.omega = MathHelper.TAU / PERIOD[IDLE];
		this.squash = this.prevSquash = SQUASH[IDLE];
		this.sway = this.prevSway = SWAY[IDLE];
	}

	/**
	 * @param dx dy dz  movement of the entity during the last tick (client interpolated position)
	 * @param bodyYaw   body yaw (degrees), to know what is "forward"
	 * @param yawDelta  change of body yaw during the last tick (degrees)
	 * @param scale     current size factor of the entity ({@code getScaleFactor})
	 */
	public void tick(boolean sitting, double dx, double dy, double dz, float bodyYaw, float yawDelta, float scale) {
		prevPhase = phase;
		prevAmplitude = amplitude;
		prevSquash = squash;
		prevSway = sway;
		prevLean = lean;
		prevBank = bank;
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

		int mode = sitting ? SIT : flying ? FLY : IDLE;
		amplitude += (AMPLITUDE[mode] - amplitude) * MODE_EASE;
		omega += (MathHelper.TAU / PERIOD[mode] - omega) * MODE_EASE;
		squash += (SQUASH[mode] - squash) * MODE_EASE;
		sway += (SWAY[mode] - sway) * MODE_EASE;
		phase += omega;
		if (phase > PHASE_WRAP) {
			phase -= PHASE_WRAP;
			prevPhase -= PHASE_WRAP;
		}

		// lean into the flight (forward speed), back when climbing; bank into turns; springs overshoot a little
		float yawRad = bodyYaw * MathHelper.RADIANS_PER_DEGREE;
		double forward = -dx * MathHelper.sin(yawRad) + dz * MathHelper.cos(yawRad);
		float leanTarget = MathHelper.clamp((float) forward * 80f, -8f, 20f) - MathHelper.clamp((float) dy * 50f, -6f, 6f);
		leanVelocity = (leanVelocity + (leanTarget - lean) * SPRING) * DAMPING;
		lean += leanVelocity;
		float bankTarget = MathHelper.clamp(-yawDelta * 1.2f, -12f, 12f);
		bankVelocity = (bankVelocity + (bankTarget - bank) * SPRING) * DAMPING;
		bank += bankVelocity;

		// visual size: springs up when it grows (fed), follows at once when it shrinks (reset after it bursts)
		if (visualScale < 0 || scale < visualScale - 0.001f) {
			visualScale = scale;
			visualScaleVelocity = 0;
		} else {
			visualScaleVelocity = (visualScaleVelocity + (scale - visualScale) * SCALE_SPRING) * SCALE_DAMPING;
			visualScale += visualScaleVelocity;
		}
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

	/** 0..1, bright at the top of the bob: drives the glow pulse of the halo. */
	public float glow(float partialTick) {
		return 0.5f + 0.5f * MathHelper.sin(phase(partialTick));
	}

	/** Visual size / real size, to draw the springy size without touching the hitbox. */
	public float visualScaleRatio(float partialTick, float scale) {
		if (visualScale < 0 || scale <= 0) return 1f;
		return MathHelper.lerp(partialTick, prevVisualScale, visualScale) / scale;
	}

	/** Values of the float layer at render time, in animation-json units (degrees, pixels), written into {@code out}. */
	public void layer(float partialTick, Layer out) {
		float p = phase(partialTick);
		float amp = MathHelper.lerp(partialTick, prevAmplitude, amplitude);
		float sq = MathHelper.lerp(partialTick, prevSquash, squash);
		float sw = MathHelper.lerp(partialTick, prevSway, sway);
		float rel = amp / AMPLITUDE[IDLE];
		float s = sq * MathHelper.sin(p - 0.5f);
		out.posY = amp * MathHelper.sin(p);
		out.posX = 0.35f * amp * MathHelper.sin(0.5f * p + swayOffset);
		out.pitch = MathHelper.lerp(partialTick, prevLean, lean) + 1.2f * rel * MathHelper.sin(p - 1.2f);
		out.roll = sw * MathHelper.sin(0.5f * p + swayOffset) + MathHelper.lerp(partialTick, prevBank, bank);
		out.scaleY = 1 + s;
		out.scaleXZ = 1 - 0.5f * s;
		out.handFlutter = 6f * rel * MathHelper.sin(p - 1.4f);
	}

	/** Reusable holder for {@link #layer} (one per renderer, no allocation per frame). */
	public static final class Layer {
		public float posX, posY, pitch, roll, scaleY, scaleXZ, handFlutter;
	}
}
