package fr.lordfinn.steveparty.blocks.custom.villager;

/**
 * The villager block's procedural body animations (squash, hops, spins, tilts...), computed each frame by the
 * client's VillagerBlockAnimator from the reaction's age. Declared on the common side so that each
 * {@link VillagerReaction} names its animation.
 */
public enum VillagerAnim {
    NONE,
    /** One hop. */
    HOP,
    /** A quick squash and stretch bounce. */
    SQUASH,
    /** Flattened by a player stepping on it, then boing. */
    SQUASH_BIG,
    /** Flattened flat as a pancake (belly flop), wobbling back up. */
    PANCAKE,
    /** Hop and a happy wiggle. */
    GREET,
    /** Rolls left and right: waving goodbye. */
    WAVE,
    /** Sinks down, slowly, sad. */
    SINK,
    /** Turns its face away from the target and sinks a bit. */
    TURN_AWAY,
    /** Looks away, a bit smaller, then peeks back. */
    SHY,
    /** Trembles. */
    TREMBLE,
    /** Leans toward the target (interest). */
    LEAN_TOWARD,
    /** Leans away from the target (fear, suspicion). */
    LEAN_AWAY,
    /** Bows twice toward the target. */
    BOW,
    /** Three hops and a spin. */
    HOP_PARTY,
    /** Tilts its head one side then the other, puzzled. */
    HEAD_TILT,
    /** Nods yes. */
    NOD,
    /** Shakes its head no. */
    SHAKE_NO,
    /** Quick duck and recoil. */
    FLINCH,
    /** Spins and wobbles, dizzy. */
    DIZZY,
    /** One full turn. */
    SPIN,
    /** A hop with a full turn. */
    SPIN_HOP,
    /** Big hops and spins, overjoyed. */
    JACKPOT,
    /** Quick chewing bobs. */
    MUNCH,
    /** Shudders and sinks (yuck). */
    SHUDDER,
    /** Angry stomps. */
    STOMP,
    /** Stretches up, wobbles, tips over on its side, lies there, pops back up. */
    FAINT,
    /** Stretches up slowly (yawn, morning). */
    STRETCH,
    /** Jumps out of its skin, then trembles. */
    STARTLE,
    /** Slow breathing (asleep). */
    BREATHE,
    /** Fast twisting to shake the rain off. */
    SHAKE_OFF,
    /** Frantic hops and jitters. */
    PANIC,
    /** A gentle sway. */
    WIGGLE,
    /** Leans toward the target and nods along, sly. */
    GOSSIP,
    /** Flattens down to hide, trembling, then peeks back up. */
    HIDE,
    /** One bob to the beat. */
    BOB,
    /** Looks up at the sky. */
    LOOK_UP,
    /** Blown up by an explosion, lands, trembles. */
    BLAST,
    /** Looks left and right. */
    LOOK_AROUND,
    /** Ah... ah... ACHOO. */
    SNEEZE,
    /** Two little hic hops. */
    HICCUP,
    /** Spins on its edge. */
    BREAKDANCE,
    /** Pretends to be a plain cobblestone block for a moment. */
    DISGUISE
}
