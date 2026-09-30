package fr.lordfinn.steveparty.client.villager;

import fr.lordfinn.steveparty.blocks.custom.villager.VillagerAnim;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerExpression;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerMode;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerReaction;
import net.minecraft.util.math.MathHelper;

/**
 * The villager block's procedural animations: a pose from the reaction (or mode) and its age, cartoon style
 * (anticipation, squash and stretch, overshoot). Pure math on a reused {@link VillagerPose}.
 */
public final class VillagerBlockAnimator {
    private static final float PI = MathHelper.PI;

    private VillagerBlockAnimator() {
    }

    /**
     * @param reaction the reaction playing, or null
     * @param age      ticks since it started (with the frame's partial tick)
     * @param modeAge  ticks since the mode started (same)
     * @param blinking whether its eyes blink this tick
     * @param miningStage how far a player is in breaking it (0..9), -1 when nobody is
     */
    public static void pose(VillagerPose pose, VillagerReaction reaction, float age, VillagerMode mode, float modeAge,
                            boolean blinking, int miningStage) {
        pose.reset();
        // The lasting mode is the base
        switch (mode) {
            case SLEEP -> {
                pose.squash(1f + 0.035f * MathHelper.sin(modeAge * 0.15f));
                pose.roll = 5f;
                pose.lookWeight = 0f;
                pose.expression = VillagerExpression.CLOSED;
            }
            case DANCE -> {
                pose.offsetY = Math.abs(MathHelper.sin(modeAge * 0.3f)) * 0.12f;
                pose.roll = 8f * MathHelper.sin(modeAge * 0.15f);
                pose.expression = VillagerExpression.HAPPY;
            }
            case DANCE_FUNKY -> {
                pose.offsetY = Math.abs(MathHelper.sin(modeAge * 0.35f)) * 0.18f;
                pose.roll = 12f * MathHelper.sin(modeAge * 0.175f);
                pose.tilt = 8f * MathHelper.cos(modeAge * 0.35f);
                float twirl = (modeAge % 80f) / 80f;
                if (twirl > 0.8f) pose.yaw = 360f * ease((twirl - 0.8f) / 0.2f);
                pose.expression = VillagerExpression.STARRY;
            }
            default -> {
            }
        }
        if (reaction != null) {
            float d = reaction.duration;
            animate(pose, reaction.anim, age, Math.min(age / d, 1f), d);
            pose.expression = expression(reaction, age);
        }
        if (miningStage >= 0) {
            // Being broken: cowers and trembles more and more, pleading eyes, then tears, then sobbing
            float k = (miningStage + 1) / 10f;
            tremble(pose, modeAge, 0.012f + 0.035f * k);
            pose.squash(1f - 0.14f * k);
            pose.tilt -= 8f * k;
            pose.lookWeight = 1f;
            pose.expression = miningStage < 3 ? VillagerExpression.WIDE
                    : miningStage < 7 ? VillagerExpression.SAD : VillagerExpression.SHOUT;
        }
        if (pose.expression == VillagerExpression.NONE && blinking) pose.expression = VillagerExpression.CLOSED;
    }

    /** The face of a reaction at an age: most keep theirs, a few change along the way. */
    static VillagerExpression expression(VillagerReaction reaction, float age) {
        return switch (reaction) {
            case IDLE_SNEEZE -> age < 20 ? VillagerExpression.YAWN : age < 28 ? VillagerExpression.SHOUT : VillagerExpression.NONE;
            case FAINT -> age < 15 ? VillagerExpression.WIDE : age < 86 ? VillagerExpression.DIZZY : VillagerExpression.CLOSED;
            case STARE_CONTEST -> age < 55 ? VillagerExpression.WIDE : age < 60 ? VillagerExpression.CLOSED : VillagerExpression.ANGRY;
            case BELL_ALARM -> age < 40 ? VillagerExpression.CLOSED : VillagerExpression.WIDE;
            case MORNING_STRETCH -> age < 30 ? VillagerExpression.YAWN : VillagerExpression.HAPPY;
            case YAWN -> age < 30 ? VillagerExpression.YAWN : VillagerExpression.CLOSED;
            case WAKE_STARTLED -> age < 20 ? VillagerExpression.WIDE : VillagerExpression.ANGRY;
            case COUSIN -> age < 40 ? VillagerExpression.SQUINT : VillagerExpression.SHOUT;
            case MIRROR -> age < 20 ? VillagerExpression.SQUINT : VillagerExpression.WIDE;
            case HUNGRY -> age < 8 ? VillagerExpression.WIDE : VillagerExpression.DROOL;
            case IDLE_DISGUISE -> age < 62 ? VillagerExpression.NONE : VillagerExpression.HAPPY;
            default -> reaction.expression;
        };
    }

    private static void animate(VillagerPose p, VillagerAnim anim, float t, float progress, float d) {
        float env = envelope(progress);
        switch (anim) {
            case NONE -> {
            }
            case HOP -> {
                if (t < 2) p.squash(0.85f);
                p.offsetY += hop(t, 2, 10, 0.35f);
                if (t > 12) p.squash(settle(t - 12, 0.2f));
            }
            case SQUASH -> p.squash(settle(t, 0.25f));
            case SQUASH_BIG -> p.squash(settle(t * 0.8f, 0.45f));
            case PANCAKE -> p.squash(1f - 0.65f * (float) Math.exp(-t / 10f) * MathHelper.cos(t * 0.5f));
            case GREET -> {
                p.offsetY += hop(t, 0, 10, 0.3f);
                p.roll += MathHelper.sin(t * 0.8f) * 10f * (1f - progress);
                if (t > 10) p.squash(settle(t - 10, 0.15f));
            }
            case WAVE -> p.roll += MathHelper.sin(t * 0.5f) * 14f * env;
            case SINK -> {
                p.squash(1f - 0.18f * env);
                p.tilt -= 10f * env;
            }
            case TURN_AWAY -> {
                p.yaw += 180f * env;
                p.squash(1f - 0.08f * env);
            }
            case SHY -> {
                float away = progress < 0.5f || progress > 0.7f ? env : env * 0.45f;
                p.yaw += 70f * away;
                p.squash(1f - 0.08f * env);
            }
            case TREMBLE -> tremble(p, t, 0.03f * Math.max(env, 0.3f));
            case LEAN_TOWARD -> {
                p.tilt += 14f * env;
                p.offsetY += 0.03f * env * Math.abs(MathHelper.sin(t * 0.4f));
            }
            case LEAN_AWAY -> {
                p.tilt -= 16f * env;
                p.squash(1f - 0.05f * env);
            }
            case BOW -> {
                if (t < 30) p.tilt += 30f * Math.abs(MathHelper.sin(PI * t / 15f));
            }
            case HOP_PARTY -> {
                p.offsetY += hop(t, 0, 10, 0.35f) + hop(t, 12, 10, 0.45f) + hop(t, 24, 12, 0.55f);
                p.yaw += 360f * ease((t - 24f) / 12f);
                if (t > 36) p.squash(settle(t - 36, 0.25f));
            }
            case HEAD_TILT -> p.roll += 18f * MathHelper.sin(2f * PI * progress) * env;
            case NOD -> p.tilt += 14f * MathHelper.sin(t * 0.7f) * env;
            case SHAKE_NO -> p.yaw += 22f * MathHelper.sin(t * 0.9f) * env;
            case FLINCH -> {
                p.squash(1f - 0.3f * (float) Math.exp(-t / 2.5f));
                p.tilt -= 12f * (float) Math.exp(-t / 3f);
            }
            case DIZZY -> {
                p.lookWeight = 0f;
                p.yaw += 720f * ease(t / (d * 0.4f));
                float wobble = t > d * 0.3f ? env : 0f;
                p.roll += 12f * MathHelper.sin(t * 0.4f) * wobble;
                p.tilt += 10f * MathHelper.cos(t * 0.4f) * wobble;
            }
            case SPIN -> p.yaw += 360f * ease(progress);
            case SPIN_HOP -> {
                p.offsetY += hop(t, 0, 16, 0.45f);
                p.yaw += 360f * ease(t / 16f);
                if (t > 16) p.squash(settle(t - 16, 0.2f));
            }
            case JACKPOT -> {
                float cycle = t % 15f;
                if (t < d - 10) p.offsetY += hop(cycle, 0, 15, 0.5f);
                p.yaw += 360f * t / 15f * (t < d - 10 ? 1f : 0f);
                p.squash(1f + 0.12f * MathHelper.sin(t * 0.8f) * env);
            }
            case MUNCH -> {
                if (t < 28) p.squash(1f - 0.08f * Math.abs(MathHelper.sin(t * 0.8f)));
                p.tilt += 6f * env;
            }
            case SHUDDER -> {
                if (t < 14) tremble(p, t, 0.05f);
                p.squash(1f - 0.1f * env);
                p.tilt -= 8f * env;
            }
            case STOMP -> {
                p.offsetY += hop(t, 0, 8, 0.2f) + hop(t, 12, 8, 0.2f);
                if (t >= 8 && t < 12) p.squash(settle(t - 8, 0.25f));
                if (t >= 20) p.squash(settle(t - 20, 0.25f));
            }
            case FAINT -> faint(p, t);
            case STRETCH -> p.squash(1f + 0.25f * MathHelper.sin(PI * progress));
            case STARTLE -> {
                if (t < 3) p.squash(1f + 0.2f);
                p.offsetY += hop(t, 0, 10, 0.7f);
                if (t > 10) {
                    p.squash(settle(t - 10, 0.3f));
                    tremble(p, t, 0.025f * (1f - progress));
                }
            }
            case BREATHE -> {
                p.lookWeight = 0f;
                p.squash(1f + 0.035f * MathHelper.sin(t * 0.15f));
            }
            case SHAKE_OFF -> {
                if (t >= 10 && t < 30) p.yaw += 25f * MathHelper.sin((t - 10f) * 2.2f);
            }
            case PANIC -> {
                p.offsetY += Math.abs(MathHelper.sin(t * 0.6f)) * 0.25f * env;
                tremble(p, t, 0.03f);
                p.yaw += 30f * MathHelper.sin(t * 0.35f) * env;
            }
            case WIGGLE -> p.roll += 7f * MathHelper.sin(t * 0.35f) * env;
            case GOSSIP -> {
                p.tilt += 12f * env + 6f * MathHelper.sin(t * 0.45f) * env;
                p.roll += 5f * MathHelper.sin(t * 0.3f) * env;
            }
            case HIDE -> {
                float flat;
                if (t < 6) flat = 1f - 0.55f * ease(t / 6f);
                else if (t < 40) flat = 0.45f;
                else if (t < 50) flat = 0.45f + 0.25f * ease((t - 40f) / 10f);
                else flat = 0.7f + 0.3f * ease((t - 50f) / 10f);
                p.squash(flat);
                if (t >= 6 && t < 40) tremble(p, t, 0.015f);
            }
            case BOB -> p.squash(1f - 0.12f * MathHelper.sin(PI * progress));
            case LOOK_UP -> {
                p.lookWeight = 1f - env;
                p.squash(1f + 0.06f * env);
            }
            case BLAST -> {
                p.offsetY += hop(t, 0, 14, 0.9f);
                p.yaw += 180f * ease(t / 14f);
                if (t > 14) {
                    p.squash(settle(t - 14, 0.35f));
                    tremble(p, t, 0.03f * (1f - progress));
                }
            }
            case LOOK_AROUND -> {
                p.lookWeight = 0.3f;
                p.yaw += 50f * MathHelper.sin(2f * PI * progress);
            }
            case SNEEZE -> {
                if (t < 20) {
                    float in = t / 20f;
                    p.squash(1f + 0.18f * in);
                    p.tilt -= 10f * in;
                } else {
                    float u = t - 20f;
                    p.squash(1f - 0.25f * (float) Math.exp(-u / 3f) * MathHelper.cos(u * 0.9f));
                    p.tilt += 18f * (float) Math.exp(-u / 4f);
                }
            }
            case HICCUP -> {
                p.offsetY += hop(t, 5, 6, 0.18f) + hop(t, 18, 6, 0.18f);
                if ((t >= 5 && t < 11) || (t >= 18 && t < 24)) p.squash(1.1f);
            }
            case BREAKDANCE -> {
                float lean = t < 10 ? ease(t / 10f) : t > d - 10 ? ease((d - t) / 10f) : 1f;
                p.roll += 40f * lean;
                p.rollPivot = 0.5f;
                p.lookWeight = 0f;
                p.yaw += t * 25f * lean;
            }
            case DISGUISE -> {
                p.disguise = t >= 2 && t < 60;
                if (t < 4) p.squash(settle(t, 0.3f));
                if (t >= 60) p.squash(settle(t - 60, 0.3f));
            }
        }
    }

    /** Stretch, wobble, tip over its edge, lie there seeing stars, pop back up. */
    private static void faint(VillagerPose p, float t) {
        p.lookWeight = 0f;
        p.rollPivot = 0.5f;
        if (t < 15) {
            p.squash(1f + 0.2f * ease(t / 15f));
            p.roll += 6f * MathHelper.sin(t * 1.2f);
        } else if (t < 30) {
            float u = (t - 15f) / 15f;
            p.roll += 90f * u * u;
            p.squash(1.2f - 0.2f * u);
        } else if (t < 80) {
            float u = t - 30f;
            p.roll += 90f - 6f * (float) Math.exp(-u / 3f) * MathHelper.cos(u * 1.2f);
        } else if (t < 92) {
            float u = (t - 80f) / 12f;
            p.roll += 90f * (1f - ease(u));
            p.offsetY += 0.4f * MathHelper.sin(PI * u);
        } else {
            p.squash(settle(t - 92f, 0.2f));
        }
    }

    // --- curves ---

    /** A hop of {@code height} from {@code start}, lasting {@code length} ticks. */
    private static float hop(float t, float start, float length, float height) {
        float u = (t - start) / length;
        return u <= 0 || u >= 1 ? 0 : MathHelper.sin(PI * u) * height;
    }

    /** A squash of {@code amount} that springs back (damped): the landing after a hop, a poke. */
    private static float settle(float t, float amount) {
        return 1f - amount * (float) Math.exp(-t / 3f) * MathHelper.cos(t * 1.1f);
    }

    /** 0 → 1 in the first 15 %, holds, 1 → 0 in the last 20 %. */
    private static float envelope(float progress) {
        if (progress < 0.15f) return ease(progress / 0.15f);
        if (progress > 0.8f) return ease((1f - progress) / 0.2f);
        return 1f;
    }

    private static float ease(float u) {
        u = MathHelper.clamp(u, 0f, 1f);
        return u * u * (3f - 2f * u);
    }

    private static void tremble(VillagerPose p, float t, float amount) {
        p.offsetX += MathHelper.sin(t * 7.1f) * amount;
        p.offsetZ += MathHelper.cos(t * 8.9f) * amount;
    }
}
