package fr.lordfinn.steveparty.entities.custom.trichaudron;

import java.util.Arrays;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * Its personality, client side only (nothing synced but the triggers sent as entity statuses): little moods of its
 * heads and body, laid over its pose by TrichaudronModel.
 * <ul>
 *     <li>Bored (no enemy, nobody riding, not pumping): each head looks around on its own ({@link Mood#LOOK}),
 *     yawns a puff of steam now and then ({@link Mood#YAWN}).</li>
 *     <li>Ridden: a head sniffs its rider ({@link Mood#SNIFF}).</li>
 *     <li>Tamed: the heads wiggle happily ({@link Mood#WIGGLE}); emptied, or a rider thrown off: they sulk, drooping
 *     and looking away ({@link Mood#SULK}); a rider on an untamed one makes the heads fidget ({@link Mood#FIDGET}).</li>
 *     <li>The body shakes the lava off when it leaves a lake ({@link #shakeOff}); a full tank sloshes and burps steam
 *     ({@link #slosh}).</li>
 * </ul>
 * Outputs, per head: a wanted yaw (from the body) and pitch (Minecraft pitch of its nozzle), or NaN to leave it, and a
 * roll (radians); for the body a roll, for the shell a slosh roll. Everything is slow and heavy: TrichaudronEntity eases
 * the heads there at {@link TrichaudronEntity#HEAD_EASE} a tick, and the wobbles here last seconds, not blinks.
 */
public final class TrichaudronMoods {
    public enum Mood { NONE, LOOK, YAWN, SNIFF, WIGGLE, SULK, FIDGET }

    private static final int HEADS = TrichaudronEntity.HEADS.length;
    private final Mood[] mood = new Mood[HEADS];
    private final int[] left = new int[HEADS], length = new int[HEADS];
    private final float[] lookYaw = new float[HEADS], lookPitch = new float[HEADS];
    public final float[] yaw = new float[HEADS], pitch = new float[HEADS], roll = new float[HEADS];
    public float bodyRoll, shellRoll;
    private int shake, slosh;
    /** Set for one tick when a head yawns its puff or the tank burps (TrichaudronEntity spawns the steam). */
    public final boolean[] puff = new boolean[HEADS];
    public boolean burp;

    public TrichaudronMoods() {
        Arrays.fill(mood, Mood.NONE);
    }

    public Mood mood(int head) {
        return mood[head];
    }

    public void trigger(int head, Mood what, int ticks) {
        mood[head] = what;
        left[head] = length[head] = Math.max(1, ticks);
    }

    /** Leaving lava: a shake of the body. */
    public void shakeOff() {
        shake = 60;
    }

    /** A full tank's slosh and burp. */
    public void slosh() {
        slosh = 70;
        burp = true;
    }

    /** One client tick: picks new moods when it is idle ({@code bored}), runs the current ones. */
    public void tick(Random random, int age, boolean bored, boolean ridden, int tank) {
        Arrays.fill(puff, false);
        burp = false;
        for (int h = 0; h < HEADS; h++) {
            if (left[h] > 0 && --left[h] == 0) mood[h] = Mood.NONE;
            if (mood[h] != Mood.NONE) continue;
            if (bored) {
                if (random.nextInt(400) == 0) {
                    lookYaw[h] = TrichaudronEntity.HEADS[h].restYaw() + random.nextFloat() * 80 - 40;
                    lookPitch[h] = random.nextFloat() * 36 - 18;
                    trigger(h, Mood.LOOK, 100 + random.nextInt(80));
                } else if (random.nextInt(1400) == 0) {
                    trigger(h, Mood.YAWN, 80);
                }
            } else if (ridden && random.nextInt(900) == 0) {
                trigger(h, Mood.SNIFF, 80);
            }
        }
        if (tank >= 20 && slosh == 0 && random.nextInt(1500) == 0) slosh();
        for (int h = 0; h < HEADS; h++) apply(h, age);
        bodyRoll = shake > 0 ? 0.035f * MathHelper.sin(shake * 0.2f) * shake / 60f : 0; // a slow, faint shake: it is heavy
        if (shake > 0) shake--;
        shellRoll = slosh > 0 ? 0.025f * MathHelper.sin(slosh * 0.15f) * slosh / 70f : 0;
        if (slosh > 0) slosh--;
    }

    private void apply(int h, int age) {
        TrichaudronHead head = TrichaudronEntity.HEADS[h];
        yaw[h] = Float.NaN;
        pitch[h] = Float.NaN;
        roll[h] = 0;
        float p = length[h] == 0 ? 1 : 1 - left[h] / (float) length[h];
        switch (mood[h]) {
            case LOOK -> {
                yaw[h] = lookYaw[h];
                pitch[h] = lookPitch[h];
            }
            case YAWN -> {
                pitch[h] = head.restPitch() - 45 * MathHelper.sin(p * MathHelper.PI);
                roll[h] = 0.1f * MathHelper.sin(p * MathHelper.PI);
                if (left[h] == length[h] / 2) puff[h] = true;
            }
            case SNIFF -> {
                yaw[h] = head.restYaw() * 0.3f + 15 * MathHelper.sin(age * 0.12f);
                pitch[h] = -35;
            }
            case WIGGLE -> {
                pitch[h] = head.restPitch() - 12;
                roll[h] = 0.25f * MathHelper.sin(age * 0.25f) * (1 - p);
            }
            case SULK -> {
                yaw[h] = head.restYaw() + (h == 2 ? -40 : 40);
                pitch[h] = head.restPitch() + 35;
            }
            case FIDGET -> {
                yaw[h] = head.restYaw() + 25 * MathHelper.sin(age * 0.1f + h);
                pitch[h] = head.restPitch() - 15;
                roll[h] = 0.1f * MathHelper.sin(age * 0.15f + h);
            }
            default -> {
            }
        }
    }
}
