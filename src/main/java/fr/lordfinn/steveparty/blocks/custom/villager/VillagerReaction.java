package fr.lordfinn.steveparty.blocks.custom.villager;

import java.util.Locale;

import static fr.lordfinn.steveparty.blocks.custom.villager.VillagerAnim.*;
import static fr.lordfinn.steveparty.blocks.custom.villager.VillagerExpression.*;

/**
 * Everything the villager block can react to, and how: the server picks the reaction
 * ({@link VillagerBlockEntity#react}), the clients play its animation, face, sounds and particles
 * ({@link VillagerReactionEffects}).
 * <p>
 * A reaction only starts when its own cooldown is over and when it outranks the one playing: a higher
 * {@link #priority} interrupts, a lower one is dropped (never queued), the same one restarts only if
 * {@link #restartable} (pokes, hops to the beat). Reactions with a {@link #hasMessage() message} also show a short
 * line in the action bar of the player who caused them.
 */
public enum VillagerReaction {
    // --- Somebody around -----------------------------------------------------------------------------------------
    /** A player comes close: hop, happy face. */
    GREET(P.SOCIAL, 24, 60, false, VillagerAnim.GREET, HAPPY, false),
    /** The same player comes back right after leaving: "you again?". */
    RETURN(P.SOCIAL, 24, 60, false, LEAN_AWAY, SQUINT, false),
    /** A player who stayed a while walks away: waves goodbye. */
    BYE(P.SOCIAL, 30, 60, false, WAVE, HAPPY, false),
    /** A player walks away right after being greeted: sulks. */
    SULK(P.SOCIAL + 5, 60, 200, false, TURN_AWAY, SAD, true),
    /** A player stares at it for 3 s: blushes and looks away. */
    STARE_SHY(P.SOCIAL, 40, 300, false, SHY, BLUSH, false),
    /** A player keeps staring for 9 s: staring contest, trembles, blinks, loses. */
    STARE_CONTEST(P.SOCIAL + 5, 70, 600, false, TREMBLE, WIDE, true),
    /** A player sneaks next to it: suspicious squint, whispers. */
    SUSPICIOUS(P.SOCIAL, 40, 200, false, LEAN_AWAY, SQUINT, false),
    /** A player crouch-spams in front of it: bows back. */
    BOW(P.INTERACT, 30, 20, true, VillagerAnim.BOW, HAPPY, false),
    /** A player jumps in front of it: hops too. */
    JUMP_ALONG(P.INTERACT, 14, 6, true, HOP, VillagerExpression.NONE, false),
    /** A player keeps jumping: jump party. */
    JUMP_PARTY(P.INTERACT + 5, 40, 100, false, HOP_PARTY, HAPPY, false),
    /** A player holds a weapon close to it: sweats, trembles. */
    WEAPON_SCARED(P.INTERACT + 5, 50, 300, false, TREMBLE, WIDE, true),
    /** A player holds an emerald, a diamond or gold close to it: greedy eyes. */
    GREEDY(P.INTERACT, 40, 200, false, LEAN_TOWARD, VillagerExpression.GREEDY, true),
    /** A player holds a villager block: existential crisis. */
    MIRROR(P.BIG, 60, 1200, false, HEAD_TILT, WIDE, true),
    /** A player eats next to it: drools. */
    HUNGRY(P.SOCIAL, 40, 200, false, LEAN_TOWARD, DROOL, false),
    /** A player drinks a potion next to it: puzzled. */
    CURIOUS_POTION(P.SOCIAL, 30, 200, false, HEAD_TILT, WIDE, false),
    /** A player throws an item next to it: ooh, what's that? */
    CATCH_ITEM(P.SOCIAL, 20, 100, false, HOP, STARRY, false),
    /** A player places a block right against it: personal space! */
    PERSONAL_SPACE(P.INTERACT, 20, 100, false, LEAN_AWAY, ANGRY, false),
    /** A player builds around it: approving nods. */
    ADMIRE_BUILD(P.SOCIAL, 24, 200, false, NOD, HAPPY, false),
    /** Something startling next to it (a block broken against it, an arrow shot...): flinches. */
    FLINCH(P.INTERACT, 12, 40, false, VillagerAnim.FLINCH, WIDE, false),
    /** A player puts on armour: admiring whistle. */
    FASHION(P.SOCIAL, 36, 400, false, LEAN_TOWARD, STARRY, false),
    /** A player teleports next to it (ender pearl, chorus fruit): whoa. */
    WHOA(P.INTERACT, 30, 200, false, SPIN, WIDE, false),
    /** A player says "hmm" (or hello) in the chat close to it: it answers. */
    CHAT_HMM(P.SOCIAL + 5, 20, 40, false, NOD, VillagerExpression.NONE, true),

    // --- Right-click ---------------------------------------------------------------------------------------------
    /** Right-click with an empty hand: squash, "hmm?". */
    POKED(P.INTERACT, 12, 0, true, SQUASH, VillagerExpression.NONE, false),
    /** 5 clicks in 3 s: annoyed, shakes its head. */
    ANNOYED(P.BIG, 30, 0, false, SHAKE_NO, ANGRY, true),
    /** 10 clicks in 5 s: dizzy, sees stars. */
    DIZZY(P.ALARM, 80, 0, false, VillagerAnim.DIZZY, VillagerExpression.DIZZY, true),
    /** Right-click with an emerald: delighted spin. */
    TRADE_HAPPY(P.INTERACT + 5, 30, 20, false, SPIN_HOP, VillagerExpression.GREEDY, false),
    /** Right-click with food: munches happily. */
    FED(P.INTERACT + 5, 36, 40, false, MUNCH, HAPPY, false),
    /** Right-click with bread, a carrot, a potato or a beetroot (villager food): hearts. */
    FED_LOVE(P.INTERACT + 5, 40, 60, false, MUNCH, HEARTS, false),
    /** Right-click with rotten flesh, a spider eye, a poisonous potato, a pufferfish: yuck. */
    YUCK(P.INTERACT + 5, 40, 60, false, SHUDDER, SICK, true),
    /** Right-click with a flower: sniffs it and blushes. */
    SNIFF_FLOWER(P.INTERACT + 5, 40, 100, false, WIGGLE, BLUSH, false),
    /** Right-click with anything it doesn't want: shakes its head. */
    REFUSE(P.INTERACT, 20, 20, false, SHAKE_NO, SQUINT, false),

    // --- Hit, stepped on -----------------------------------------------------------------------------------------
    /** Punched (adventure mode, where it can't break) or hit by a projectile: ouch. */
    OUCH(P.BIG, 14, 0, true, VillagerAnim.FLINCH, CLOSED, false),
    /** 3 punches in 4 s: grumpy stomps. */
    GRUMPY(P.BIG + 5, 30, 0, false, STOMP, ANGRY, false),
    /** 6 punches in 6 s: dramatic faint. */
    FAINT(P.DRAMA, 100, 0, false, VillagerAnim.FAINT, VillagerExpression.DIZZY, true),
    /** A player starts breaking it: pleads (the breaking itself is animated by the clients, stage by stage). */
    PLEAD(P.DRAMA, 30, 100, false, TREMBLE, WIDE, true),
    /** A player walks onto it: squished, oof. */
    SQUISHED(P.INTERACT, 16, 30, false, SQUASH_BIG, CLOSED, false),
    /** A player lands on it from high: pancake. */
    BELLY_FLOP(P.BIG + 5, 40, 60, false, PANCAKE, VillagerExpression.DIZZY, false),
    /** Hit by a snowball: shivers. */
    SHIVER(P.BIG, 40, 60, false, TREMBLE, SAD, false),

    // --- Players' misfortunes ------------------------------------------------------------------------------------
    /** A player gets hurt next to it: winces. */
    WINCE(P.SOCIAL + 5, 20, 60, false, VillagerAnim.FLINCH, CLOSED, false),
    /** A player dies next to it: mourns, sobbing. */
    MOURN(P.DRAMA, 100, 200, false, SINK, SAD, true),
    /** A monster dies next to it: cheers. */
    CHEER(P.INTERACT, 30, 100, false, HOP_PARTY, HAPPY, false),

    // --- Time and weather ----------------------------------------------------------------------------------------
    /** Night falls and nobody's around: yawns before falling asleep. */
    YAWN(P.AMBIENT, 50, 200, false, STRETCH, VillagerExpression.YAWN, false),
    /** Morning: wakes up with a big stretch. */
    MORNING_STRETCH(P.AMBIENT, 50, 0, false, STRETCH, VillagerExpression.YAWN, false),
    /** Woken up by a player coming close (or poking it): "I wasn't sleeping!". */
    WAKE_STARTLED(P.ALARM, 40, 60, false, STARTLE, WIDE, true),
    /** Mumbles in its sleep. */
    SLEEP_TALK(P.IDLE + 5, 40, 400, false, BREATHE, CLOSED, false),
    /** Rain: grumbles and shakes the water off. */
    RAIN_GRUMBLE(P.AMBIENT, 40, 600, false, SHAKE_OFF, SQUINT, false),
    /** Lightning strikes nearby: panics. */
    THUNDER_PANIC(P.ALARM, 50, 60, false, PANIC, SHOUT, false),

    // --- Neighbours ----------------------------------------------------------------------------------------------
    /** A zombie or an illager comes close: panics. */
    ZOMBIE_PANIC(P.ALARM, 60, 80, false, PANIC, SHOUT, false),
    /** A zombie villager comes close (sometimes): "cousin?!". */
    COUSIN(P.ALARM + 5, 60, 2400, false, HEAD_TILT, SQUINT, true),
    /** A cat comes close: hearts, purrs. */
    CAT_LOVE(P.SOCIAL, 50, 400, false, WIGGLE, HEARTS, false),
    /** An iron golem comes close: its hero. */
    GOLEM_HERO(P.SOCIAL, 40, 600, false, HOP, STARRY, false),
    /** A villager comes close: gossips. */
    GOSSIP(P.SOCIAL, 60, 400, false, VillagerAnim.GOSSIP, SQUINT, false),
    /** A wandering trader comes close: jealous squint. */
    TRADER_JEALOUS(P.SOCIAL, 40, 600, false, LEAN_AWAY, SQUINT, false),

    // --- Sounds around -------------------------------------------------------------------------------------------
    /** A bell (or a goat horn) rings nearby: raid reflex, hides, then peeks. */
    BELL_ALARM(P.ALARM, 60, 60, false, HIDE, WIDE, false),
    /** A note block plays nearby: bobs to the beat. */
    NOTE_BOB(P.AMBIENT + 5, 10, 0, true, BOB, HAPPY, false),
    /** A firework explodes nearby: ooh, sparkly eyes. */
    FIREWORK_WONDER(P.SOCIAL, 40, 60, false, LOOK_UP, STARRY, false),
    /** Any other explosion nearby: blown up, panics. */
    EXPLOSION_PANIC(P.ALARM + 5, 50, 60, false, BLAST, SHOUT, false),

    // --- Easter eggs ---------------------------------------------------------------------------------------------
    /** Right-click with an emerald block: jackpot! */
    JACKPOT(P.DRAMA, 60, 200, false, VillagerAnim.JACKPOT, STARRY, true),
    /** The 64th right-click of its life: a whole stack of pokes. */
    STACK_OF_POKES(P.DRAMA, 60, 0, false, VillagerAnim.JACKPOT, STARRY, true),

    // --- Idle, when a player is around but nothing happens -------------------------------------------------------
    IDLE_LOOK_AROUND(P.IDLE, 60, 0, false, LOOK_AROUND, VillagerExpression.NONE, false),
    IDLE_WHISTLE(P.IDLE, 50, 0, false, BOB, HAPPY, false),
    IDLE_NOSE_WIGGLE(P.IDLE, 24, 0, false, WIGGLE, VillagerExpression.NONE, false),
    IDLE_SNEEZE(P.IDLE, 40, 0, false, SNEEZE, SQUINT, false),
    IDLE_HUM(P.IDLE, 50, 0, false, WIGGLE, CLOSED, false),
    IDLE_HICCUP(P.IDLE, 30, 0, false, HICCUP, WIDE, false),
    /** Rare: a spin, just because. */
    IDLE_SPIN(P.IDLE, 30, 0, false, SPIN, HAPPY, false),
    /** Rarer: breakdance. */
    IDLE_BREAKDANCE(P.IDLE, 80, 0, false, BREAKDANCE, HAPPY, false),
    /** Rarer: pretends to be a plain cobblestone block. */
    IDLE_DISGUISE(P.IDLE, 70, 0, false, DISGUISE, VillagerExpression.NONE, false);

    /** Priority levels (a higher one interrupts a lower one). */
    public static final class P {
        public static final int IDLE = 10, AMBIENT = 20, SOCIAL = 30, INTERACT = 40, BIG = 50, ALARM = 60, DRAMA = 70;

        private P() {
        }
    }

    private static final VillagerReaction[] VALUES = values();

    public final int priority;
    /** Ticks. */
    public final int duration;
    /** Ticks before this reaction can play again (from its start). */
    public final int cooldown;
    public final boolean restartable;
    public final VillagerAnim anim;
    public final VillagerExpression expression;
    private final boolean message;

    VillagerReaction(int priority, int duration, int cooldown, boolean restartable, VillagerAnim anim,
                     VillagerExpression expression, boolean message) {
        this.priority = priority;
        this.duration = duration;
        this.cooldown = cooldown;
        this.restartable = restartable;
        this.anim = anim;
        this.expression = expression;
        this.message = message;
    }

    public boolean hasMessage() {
        return message;
    }

    /** The action bar line (lang key) shown to the player who caused it, see {@link #hasMessage()}. */
    public String messageKey() {
        return "message.steveparty.villager_block." + name().toLowerCase(Locale.ROOT);
    }

    public static VillagerReaction byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }

    public static int count() {
        return VALUES.length;
    }
}
