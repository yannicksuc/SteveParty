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

    // Tokenizer Wand spell: whimsical, sparkly, bubbly. Only vanilla sounds, layered and re-pitched
    // (assets/steveparty/sounds.json).
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

    // Villager block reactions: vanilla villager voices (and a few other vanilla sounds) re-pitched and layered in
    // assets/steveparty/sounds.json, cartoonish; played by the clients (VillagerReactionEffects).
    public static final SoundEvent VILLAGER_BLOCK_HMM = register("villager_block.hmm");
    public static final SoundEvent VILLAGER_BLOCK_YES = register("villager_block.yes");
    public static final SoundEvent VILLAGER_BLOCK_NO = register("villager_block.no");
    public static final SoundEvent VILLAGER_BLOCK_GRUMBLE = register("villager_block.grumble");
    public static final SoundEvent VILLAGER_BLOCK_GIGGLE = register("villager_block.giggle");
    public static final SoundEvent VILLAGER_BLOCK_OUCH = register("villager_block.ouch");
    public static final SoundEvent VILLAGER_BLOCK_FAINT = register("villager_block.faint");
    public static final SoundEvent VILLAGER_BLOCK_GASP = register("villager_block.gasp");
    public static final SoundEvent VILLAGER_BLOCK_SNORE = register("villager_block.snore");
    public static final SoundEvent VILLAGER_BLOCK_YAWN = register("villager_block.yawn");
    public static final SoundEvent VILLAGER_BLOCK_SNEEZE = register("villager_block.sneeze");
    public static final SoundEvent VILLAGER_BLOCK_WHISTLE = register("villager_block.whistle");
    public static final SoundEvent VILLAGER_BLOCK_MUNCH = register("villager_block.munch");
    public static final SoundEvent VILLAGER_BLOCK_BOING = register("villager_block.boing");
    public static final SoundEvent VILLAGER_BLOCK_DELIGHTED = register("villager_block.delighted");
    public static final SoundEvent VILLAGER_BLOCK_PURR = register("villager_block.purr");
    public static final SoundEvent VILLAGER_BLOCK_WHISPER = register("villager_block.whisper");
    public static final SoundEvent VILLAGER_BLOCK_CHEER = register("villager_block.cheer");
    public static final SoundEvent VILLAGER_BLOCK_SOB = register("villager_block.sob");
    public static final SoundEvent VILLAGER_BLOCK_FLOP = register("villager_block.flop");
    public static final SoundEvent VILLAGER_BLOCK_HICCUP = register("villager_block.hiccup");
    public static final SoundEvent VILLAGER_BLOCK_SHIVER = register("villager_block.shiver");
    public static final SoundEvent VILLAGER_BLOCK_POOF = register("villager_block.poof");
    public static final SoundEvent VILLAGER_BLOCK_BLEH = register("villager_block.bleh");
    public static final SoundEvent VILLAGER_BLOCK_SNIFF = register("villager_block.sniff");
    public static final SoundEvent VILLAGER_BLOCK_JACKPOT = register("villager_block.jackpot");
    /** Being broken: whimpers, then the farewell and a ghostly sigh when it breaks. */
    public static final SoundEvent VILLAGER_BLOCK_WHIMPER = register("villager_block.whimper");
    public static final SoundEvent VILLAGER_BLOCK_FAREWELL = register("villager_block.farewell");
    public static final SoundEvent VILLAGER_BLOCK_SIGH = register("villager_block.sigh");
    /** Pulled out by a sticky piston: back to being a villager. */
    public static final SoundEvent VILLAGER_BLOCK_FREED = register("villager_block.freed");
    // Glandouille: vanilla sounds re-pitched and layered in assets/steveparty/sounds.json (wood, slime for the
    // "pouic"...), under the mod's own events so they can be replaced later.
    public static final SoundEvent GLANDOUILLE_AMBIENT = register("glandouille.ambient");
    public static final SoundEvent GLANDOUILLE_HURT = register("glandouille.hurt");
    public static final SoundEvent GLANDOUILLE_DEATH = register("glandouille.death");
    public static final SoundEvent GLANDOUILLE_STEP = register("glandouille.step");
    /** The two stomps of its telegraph, and its growl. */
    public static final SoundEvent GLANDOUILLE_STOMP = register("glandouille.stomp");
    public static final SoundEvent GLANDOUILLE_GROWL = register("glandouille.growl");
    /** Its charge hits something (no damage: a shove). */
    public static final SoundEvent GLANDOUILLE_RAM = register("glandouille.ram");
    /** Its charge ends in a wall. */
    public static final SoundEvent GLANDOUILLE_BONK = register("glandouille.bonk");
    /** Stomped flat: "pouic". */
    public static final SoundEvent GLANDOUILLE_SQUASH = register("glandouille.squash");
    public static final SoundEvent GLANDOUILLE_REINFLATE = register("glandouille.reinflate");
    public static final SoundEvent GLANDOUILLE_SULK = register("glandouille.sulk");
    public static final SoundEvent GLANDOUILLE_SNORE = register("glandouille.snore");
    public static final SoundEvent GLANDOUILLE_HAT_POP = register("glandouille.hat_pop");
    public static final SoundEvent GLANDOUILLE_HAT_ON = register("glandouille.hat_on");
    /** Flicked out of a tower. */
    public static final SoundEvent GLANDOUILLE_FLICK = register("glandouille.flick");
    /** The frosty one sliding like a curling stone. */
    public static final SoundEvent GLANDOUILLE_SLIDE = register("glandouille.slide");
    public static final SoundEvent GLANDOUILLE_COLLAPSE = register("glandouille.collapse");
    public static final SoundEvent GLANDOUILLE_CLIMB = register("glandouille.climb");
    /** The board's Glandouilles leaving in a little cloud. */
    public static final SoundEvent GLANDOUILLE_POOF = register("glandouille.poof");
    /** A ripe acorn hatching into a young Glandouille. */
    public static final SoundEvent GLANDOUILLE_HATCH = register("glandouille.hatch");
    /** Head down, it starts its charge. */
    public static final SoundEvent GLANDOUILLE_CHARGE = register("glandouille.charge");
    /** Stunned, dizzy against a wall. */
    public static final SoundEvent GLANDOUILLE_DIZZY = register("glandouille.dizzy");
    // Frousseux (Wickling): vanilla sounds for now (assets/steveparty/sounds.json), a home-made recording later
    public static final SoundEvent FROUSSEUX_AMBIENT = register("frousseux.ambient");
    public static final SoundEvent FROUSSEUX_HURT = register("frousseux.hurt");
    public static final SoundEvent FROUSSEUX_DEATH = register("frousseux.death");
    /** Its little laugh, slipping away from a blow. */
    public static final SoundEvent FROUSSEUX_LAUGH = register("frousseux.laugh");
    /** Its flame relit with flint and steel. */
    public static final SoundEvent FROUSSEUX_RELIGHT = register("frousseux.relight");
    /** Its flame blown out by a wind charge: "pfff". */
    public static final SoundEvent FROUSSEUX_BLOWN_OUT = register("frousseux.blown_out");
    // Boomcart (Pétaroule): vanilla sounds for now (assets/steveparty/sounds.json), a home-made recording later
    /** Its grumpy grumble. */
    public static final SoundEvent BOOMCART_AMBIENT = register("boomcart.ambient");
    public static final SoundEvent BOOMCART_HURT = register("boomcart.hurt");
    public static final SoundEvent BOOMCART_DEATH = register("boomcart.death");
    /** Its iron wheels rolling, on rails or off. */
    public static final SoundEvent BOOMCART_ROLL = register("boomcart.roll");
    /** It gobbles its load (TNT or a rocket). */
    public static final SoundEvent BOOMCART_LOAD = register("boomcart.load");
    /** Its fuse lit, or passed on, with flint and steel. */
    public static final SoundEvent BOOMCART_LIGHT = register("boomcart.light");
    /** Its fuse hissing, higher as it burns down. */
    public static final SoundEvent BOOMCART_FUSE = register("boomcart.fuse");
    /** Lit, it panics. */
    public static final SoundEvent BOOMCART_PANIC = register("boomcart.panic");
    /** Hit, or someone too close: it roars. */
    public static final SoundEvent BOOMCART_ROAR = register("boomcart.roar");
    /** The same player tried to pass it on twice in a row: the flint only sparks. */
    public static final SoundEvent BOOMCART_REFUSE = register("boomcart.refuse");
    /** It blows (on top of the TNT's blast or the firework's sparks). */
    public static final SoundEvent BOOMCART_EXPLODE = register("boomcart.explode");
    // Mistigri: vanilla cat sounds pitched lower and heavier for now (assets/steveparty/sounds.json)
    public static final SoundEvent MISTIGRI_AMBIENT = register("mistigri.ambient");
    public static final SoundEvent MISTIGRI_PURR = register("mistigri.purr");
    public static final SoundEvent MISTIGRI_MEOW = register("mistigri.meow");
    public static final SoundEvent MISTIGRI_HISS = register("mistigri.hiss");
    /** Puffing up: a long angry yowl. */
    public static final SoundEvent MISTIGRI_ANGRY = register("mistigri.angry");
    public static final SoundEvent MISTIGRI_EAT = register("mistigri.eat");
    public static final SoundEvent MISTIGRI_HURT = register("mistigri.hurt");
    public static final SoundEvent MISTIGRI_DEATH = register("mistigri.death");
    /** A black cat turning into a Mistigri. */
    public static final SoundEvent MISTIGRI_TRANSFORM = register("mistigri.transform");
    /** Bad luck strikes (crossing his path, a monster's blow missing). */
    public static final SoundEvent MISTIGRI_BAD_LUCK = register("mistigri.bad_luck");
    // Fumarole (Fumerolle): vanilla sounds for now (assets/steveparty/sounds.json)
    /** Its low rumble and the crackle of its tank. */
    public static final SoundEvent FUMAROLE_AMBIENT = register("fumarole.ambient");
    public static final SoundEvent FUMAROLE_HURT = register("fumarole.hurt");
    public static final SoundEvent FUMAROLE_DEATH = register("fumarole.death");
    /** Its heavy steps. */
    public static final SoundEvent FUMAROLE_STEP = register("fumarole.step");
    /** A gulp of lava pumped from a source. */
    public static final SoundEvent FUMAROLE_PUMP = register("fumarole.pump");
    /** Its tank gurgles (a bucket taken or poured). */
    public static final SoundEvent FUMAROLE_GURGLE = register("fumarole.gurgle");
    /** The warning second before a blast: steam hisses in its vent. */
    public static final SoundEvent FUMAROLE_CHARGE = register("fumarole.charge");
    /** The thermal blast. */
    public static final SoundEvent FUMAROLE_BLAST = register("fumarole.blast");
    /** The weak puff of an empty tank. */
    public static final SoundEvent FUMAROLE_PUFF = register("fumarole.puff");
    /** Its tank spilling as it dies. */
    public static final SoundEvent FUMAROLE_SPILL = register("fumarole.spill");
    @SuppressWarnings("EmptyMethod")
    public static void initialize() {
    }

    public static SoundEvent register(String name) {
        Identifier identifier = Steveparty.id(name);
        return Registry.register(Registries.SOUND_EVENT, identifier, SoundEvent.of(identifier));
    }

    /** The "linked" click of a block (a page, a pipe, a podium, a destination), heard by everyone around. */
    public static void playSelect(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos) {
        world.playSound(null, pos, SELECT_SOUND_EVENT, net.minecraft.sound.SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    /** The "unlinked" click of a block, heard by everyone around. */
    public static void playCancel(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos) {
        world.playSound(null, pos, CANCEL_SOUND_EVENT, net.minecraft.sound.SoundCategory.BLOCKS, 1.0F, 1.0F);
    }
}
