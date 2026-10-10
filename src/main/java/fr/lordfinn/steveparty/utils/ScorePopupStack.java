package fr.lordfinn.steveparty.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The score popups (« +3 », « −1 ») floating over one goal pole base, without the drawing: a gain of a holder whose
 * popup is still showing (same sign) adds to its number, bumps it and gives it its life back, instead of a new popup.
 * At most {@link #MAX_SHOWN} popups show at once: past that, the oldest fades out quickly.
 */
public final class ScorePopupStack {
    /** Popups showing at once on a base (those fading out early are not counted). */
    public static final int MAX_SHOWN = 5;
    /** Life of a popup with no new gain, in ticks. */
    public static final int LIFE = 50;
    /** Part of the life it stays fully opaque, then fades. */
    public static final float FADE_START = 0.5f;
    /** Ticks a popup takes to fade out when pushed out by newer ones. */
    public static final int PUSHED_OUT_LIFE = 6;
    /** Ticks of the scale pulse when its number goes up. */
    public static final int BUMP_TICKS = 6;
    /** Highest it rises (blocks), then it stays at that height while it keeps scoring. */
    public static final float MAX_RISE = 0.6f;
    private static final float RISE_PER_TICK = 0.02f;

    public static final class Popup {
        public final String holder;
        private long value;
        private int age;
        private int life = LIFE;
        private int rise;
        private int bump;
        private boolean pushedOut;

        private Popup(String holder, long value) {
            this.holder = holder;
            this.value = value;
        }

        public long value() {
            return value;
        }

        /** « +3 », « −2 ». */
        public String text() {
            return (value < 0 ? "−" : "+") + Math.abs(value);
        }

        /** Opacity, 0 to 1. */
        public float alpha(float tickDelta) {
            float progress = Math.min(1f, (age + tickDelta) / life);
            if (pushedOut) return Math.max(0f, 1f - progress);
            return progress < FADE_START ? 1f : Math.max(0f, 1f - (progress - FADE_START) / (1f - FADE_START));
        }

        /** Height above its spot, in blocks. */
        public float rise(float tickDelta) {
            return Math.min(MAX_RISE, (rise + tickDelta) * RISE_PER_TICK);
        }

        /** Scale factor: a short pulse when its number went up. */
        public float scale(float tickDelta) {
            if (bump <= 0) return 1f;
            float t = Math.max(0f, (bump - tickDelta) / BUMP_TICKS);
            return 1f + 0.35f * (float) Math.sin(Math.PI * t);
        }

        public boolean isPushedOut() {
            return pushedOut;
        }
    }

    private final List<Popup> popups = new ArrayList<>();

    /** A holder's points changed by {@code delta}: merged into his popup if it is still showing, else a new one. */
    public void add(String holder, long delta) {
        if (delta == 0) return;
        for (int i = popups.size() - 1; i >= 0; i--) {
            Popup popup = popups.get(i);
            if (popup.pushedOut || !popup.holder.equals(holder) || Long.signum(popup.value) != Long.signum(delta)) continue;
            popup.value += delta;
            popup.age = 0;
            popup.bump = BUMP_TICKS;
            return;
        }
        popups.add(new Popup(holder, delta));
        int shown = 0;
        for (Popup popup : popups) if (!popup.pushedOut) shown++;
        for (Popup popup : popups) {
            if (shown <= MAX_SHOWN) break;
            if (popup.pushedOut) continue;
            // The oldest leaves first, quickly, from where it is
            popup.pushedOut = true;
            popup.age = 0;
            popup.life = PUSHED_OUT_LIFE;
            shown--;
        }
    }

    /** One tick: they rise, age, and the ended ones go. */
    public void tick() {
        popups.removeIf(popup -> {
            popup.age++;
            popup.rise++;
            if (popup.bump > 0) popup.bump--;
            return popup.age >= popup.life;
        });
    }

    public boolean isEmpty() {
        return popups.isEmpty();
    }

    public List<Popup> popups() {
        return Collections.unmodifiableList(popups);
    }

    /** Popups showing (not fading out early). */
    public int shownCount() {
        int shown = 0;
        for (Popup popup : popups) if (!popup.pushedOut) shown++;
        return shown;
    }
}
