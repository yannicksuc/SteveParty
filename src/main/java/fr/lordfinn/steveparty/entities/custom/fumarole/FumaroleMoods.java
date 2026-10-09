package fr.lordfinn.steveparty.entities.custom.fumarole;

import java.util.Arrays;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * Its personality, client side only (nothing synced but the triggers sent as entity statuses): little moods of its
 * heads and body, laid over its pose by FumaroleModel.
 * <ul>
 *     <li>Bored (no enemy, nobody riding, not pumping): each head looks around on its own ({@link Mood#LOOK}),
 *     yawns a puff of steam now and then ({@link Mood#YAWN}); two heads squabble, nipping at each other
 *     ({@link Mood#SQUABBLE}).</li>
 *     <li>Ridden: a head sniffs its rider ({@link Mood#SNIFF}).</li>
 *     <li>Fed magma cream: the head wiggles happily ({@link Mood#WIGGLE}); refused (no cream to take, or a rider
 *     thrown off): it sulks, drooping and looking away ({@link Mood#SULK}); a rider on an untamed one makes the
 *     heads fidget ({@link Mood#FIDGET}).</li>
 *     <li>The body shakes the lava off when it leaves a lake ({@link #shakeOff}); a full tank sloshes and burps steam
 *     ({@link #slosh}).</li>
 * </ul>
 * Outputs, per head: a wanted yaw (from the body) and pitch (Minecraft pitch of its nozzle), or NaN to leave it, and a
 * roll (radians); for the body a roll, for the shell a slosh roll.
 */
public final class FumaroleMoods {
    public enum Mood { NONE, LOOK, YAWN, SQUABBLE, SNIFF, WIGGLE, SULK, FIDGET }

    private static final int HEADS = FumaroleEntity.HEADS.length;
    private final Mood[] mood = new Mood[HEADS];
    private final int[] left = new int[HEADS], length = new int[HEADS], partner = new int[HEADS];
    private final float[] lookYaw = new float[HEADS], lookPitch = new float[HEADS];
    public final float[] yaw = new float[HEADS], pitch = new float[HEADS], roll = new float[HEADS];
    public float bodyRoll, shellRoll;
    private int shake, slosh;
    /** Set for one tick when a head yawns its puff or the tank burps (FumaroleEntity spawns the steam). */
    public final boolean[] puff = new boolean[HEADS];
    public boolean burp;

    public FumaroleMoods() {
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
        shake = 24;
    }

    /** A full tank's slosh and burp. */
    public void slosh() {
        slosh = 30;
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
                if (random.nextInt(250) == 0) {
                    lookYaw[h] = FumaroleEntity.HEADS[h].restYaw() + random.nextFloat() * 110 - 55;
                    lookPitch[h] = random.nextFloat() * 50 - 25;
                    trigger(h, Mood.LOOK, 40 + random.nextInt(50));
                } else if (random.nextInt(1400) == 0) {
                    trigger(h, Mood.YAWN, 40);
                } else if (h > 0 && random.nextInt(2400) == 0 && mood[0] == Mood.NONE) {
                    partner[h] = 0;
                    partner[0] = h;
                    trigger(h, Mood.SQUABBLE, 50);
                    trigger(0, Mood.SQUABBLE, 50);
                }
            } else if (ridden && random.nextInt(900) == 0) {
                trigger(h, Mood.SNIFF, 35);
            }
        }
        if (tank >= 20 && slosh == 0 && random.nextInt(1500) == 0) slosh();
        for (int h = 0; h < HEADS; h++) apply(h, age);
        bodyRoll = shake > 0 ? 0.14f * MathHelper.sin(shake * 1.4f) * shake / 24f : 0;
        if (shake > 0) shake--;
        shellRoll = slosh > 0 ? 0.06f * MathHelper.sin(slosh * 0.7f) * slosh / 30f : 0;
        if (slosh > 0) slosh--;
    }

    private void apply(int h, int age) {
        FumaroleHead head = FumaroleEntity.HEADS[h];
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
            case SQUABBLE -> {
                float toward = FumaroleEntity.HEADS[partner[h]].restYaw();
                yaw[h] = head.restYaw() + (toward - head.restYaw()) * 0.7f + 7 * MathHelper.sin(age * 1.3f + h);
                pitch[h] = head.restPitch() + 10 * MathHelper.sin(age * 1.9f + h * 2);
                roll[h] = 0.15f * MathHelper.sin(age * 1.1f + h);
            }
            case SNIFF -> {
                yaw[h] = head.restYaw() * 0.3f + 15 * MathHelper.sin(age * 0.9f);
                pitch[h] = -35;
            }
            case WIGGLE -> {
                pitch[h] = head.restPitch() - 12;
                roll[h] = 0.4f * MathHelper.sin(age * 1.6f) * (1 - p);
            }
            case SULK -> {
                yaw[h] = head.restYaw() + (h == 2 ? -40 : 40);
                pitch[h] = head.restPitch() + 35;
            }
            case FIDGET -> {
                yaw[h] = head.restYaw() + 30 * MathHelper.sin(age * 0.5f + h);
                pitch[h] = head.restPitch() - 15;
                roll[h] = 0.1f * MathHelper.sin(age * 0.8f + h);
            }
            default -> {
            }
        }
    }
}
