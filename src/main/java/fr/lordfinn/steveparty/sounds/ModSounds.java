package fr.lordfinn.steveparty.sounds;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {
    public static final SoundEvent CLOSE_TILE_GUI_SOUND_EVENT = register("close_tile_gui");
    public static final SoundEvent OPEN_TILE_GUI_SOUND_EVENT = register("open_tile_gui");
    public static final SoundEvent SELECT_SOUND_EVENT = register("select");
    public static final SoundEvent CANCEL_SOUND_EVENT = register("cancel");
    public static final SoundEvent POP_SOUND_EVENT = register("pop");
    public static final SoundEvent PLUNGER_SUCK_IN_SOUND_EVENT = register("plunger_suck_in");
    public static final SoundEvent PLUNGER_SUCK_OUT_SOUND_EVENT = register("plunger_suck_out");
    public static final SoundEvent GOAL_POLE_REACH = register("goal_pole_reach");

    // Tokenizer Wand spell, Kamek-flavoured: whimsical, sparkly, bubbly. Only vanilla sounds, layered and re-pitched
    // (assets/steveparty/sounds.json), no Nintendo audio.
    /** Charging: a rising amethyst shimmer, with an enchanting sweep. */
    public static final SoundEvent TOKEN_SPELL_CHARGE = register("token_spell.charge");
    public static final SoundEvent TOKEN_SPELL_CHARGE_SWEEP = register("token_spell.charge_sweep");
    /** Drawing: little chime / amethyst twinkles, their pitch following the stroke's speed. */
    public static final SoundEvent TOKEN_SPELL_DRAW = register("token_spell.draw");
    /** The stroke snaps into the circle: a bell, and a high chime. */
    public static final SoundEvent TOKEN_SPELL_SNAP = register("token_spell.snap");
    public static final SoundEvent TOKEN_SPELL_SNAP_SPARKLE = register("token_spell.snap_sparkle");
    /** Validation: a magic zap, and the whoosh of the shapes flying to the mob. */
    public static final SoundEvent TOKEN_SPELL_CAST = register("token_spell.cast");
    public static final SoundEvent TOKEN_SPELL_CAST_WHOOSH = register("token_spell.cast_whoosh");
    /** Transformation: a jelly boing and bubbles on each growth pulse, then a sparkle tail. */
    public static final SoundEvent TOKEN_SPELL_BOING = register("token_spell.boing");
    public static final SoundEvent TOKEN_SPELL_BUBBLE = register("token_spell.bubble");
    public static final SoundEvent TOKEN_SPELL_SPARKLE_TAIL = register("token_spell.sparkle_tail");
    /** Not a circle (or a flare that found nothing): a small fizzle. */
    public static final SoundEvent TOKEN_SPELL_FIZZLE = register("token_spell.fizzle");
    /** Cancelled: a soft poof. */
    public static final SoundEvent TOKEN_SPELL_CANCEL = register("token_spell.cancel");
    /** The flare flying from the wand. */
    public static final SoundEvent TOKEN_SPELL_FLARE = register("token_spell.flare");
    @SuppressWarnings("EmptyMethod")
    public static void initialize() {
    }

    public static SoundEvent register(String name) {
        Identifier identifier = Steveparty.id(name);
        return Registry.register(Registries.SOUND_EVENT, identifier, SoundEvent.of(identifier));
    }
}
