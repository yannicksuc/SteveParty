package fr.lordfinn.steveparty.blocks.custom.villager;

import fr.lordfinn.steveparty.particles.MulaSparkleEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import static fr.lordfinn.steveparty.sounds.ModSounds.*;

/**
 * The sounds and particles of the villager block's reactions and modes, played by each client on its own (the server
 * only tells which reaction starts, with a block event): {@link #tick} is called every client tick of a reaction,
 * with its age (0 on its first tick), so the beats line up with the animation (the "ah... ah..." before the
 * "ACHOO"). Only cached particle effects, no allocation per tick.
 */
public final class VillagerReactionEffects {
    private static final MulaSparkleEffect STAR = new MulaSparkleEffect(0xFFE14D, 1.1f, MulaSparkleEffect.TWINKLE);
    private static final MulaSparkleEffect SLEEPY_Z = new MulaSparkleEffect(0xFFFFFF, 1.3f, MulaSparkleEffect.Z);
    private static final MulaSparkleEffect CONFETTI_GREEN = new MulaSparkleEffect(0x3DF08A, 1.0f, MulaSparkleEffect.STAR_BIT);
    private static final MulaSparkleEffect CONFETTI_GOLD = new MulaSparkleEffect(0xFFD23D, 1.0f, MulaSparkleEffect.STAR_BIT);
    /** Pentatonic steps (note block pitches) for the whistled tunes. */
    private static final float[] TUNE = {0.7f, 0.8f, 0.9f, 1.05f, 1.2f, 1.4f, 1.6f};

    private static ParticleEffect breadCrumbs;

    private VillagerReactionEffects() {
    }

    /** One client tick of {@code reaction}, {@code age} ticks after it started. */
    public static void tick(World world, BlockPos pos, VillagerReaction reaction, int age, Random random) {
        switch (reaction) {
            case GREET -> {
                if (age == 0) {
                    play(world, pos, VILLAGER_BLOCK_HMM, 0.8f, 1.1f, random);
                    burst(world, pos, ParticleTypes.HAPPY_VILLAGER, 5, random);
                }
                if (age == 6) play(world, pos, VILLAGER_BLOCK_BOING, 0.6f, 1.2f, random);
            }
            case RETURN -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_HMM, 0.8f, 0.8f, random);
            }
            case BYE -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_HMM, 0.8f, 1.2f, random);
                if (age == 8) whistle(world, pos, 1.2f, random);
                if (age == 16) whistle(world, pos, 0.9f, random);
            }
            case SULK -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_GRUMBLE, 0.8f, 1f, random);
                if (age == 25) {
                    play(world, pos, VILLAGER_BLOCK_SOB, 0.4f, 1.2f, random);
                    burst(world, pos, ParticleTypes.SMOKE, 4, random);
                }
            }
            case STARE_SHY -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_GIGGLE, 0.6f, 1.1f, random);
                if (age == 10) burst(world, pos, ParticleTypes.HEART, 1, random);
            }
            case STARE_CONTEST -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_HMM, 0.7f, 0.9f, random);
                if (age < 55 && age % 10 == 5) sweat(world, pos, random);
                if (age == 55) {
                    play(world, pos, VILLAGER_BLOCK_GASP, 0.8f, 1f, random);
                    burst(world, pos, ParticleTypes.ANGRY_VILLAGER, 2, random);
                }
            }
            case SUSPICIOUS -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_WHISPER, 1f, 1f, random);
                if (age == 20) play(world, pos, VILLAGER_BLOCK_WHISPER, 1f, 0.8f, random);
            }
            case BOW -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_YES, 0.8f, 1f, random);
                if (age == 8) burst(world, pos, ParticleTypes.HAPPY_VILLAGER, 3, random);
            }
            case JUMP_ALONG -> {
                if (age == 2) play(world, pos, VILLAGER_BLOCK_BOING, 0.4f, 1f, random);
            }
            case JUMP_PARTY -> {
                if (age == 0 || age == 12 || age == 24) play(world, pos, VILLAGER_BLOCK_BOING, 0.6f, 1f + age / 24f, random);
                if (age == 30) {
                    play(world, pos, VILLAGER_BLOCK_GIGGLE, 0.8f, 1f, random);
                    notes(world, pos, 3, random);
                }
            }
            case WEAPON_SCARED -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_GASP, 0.8f, 1f, random);
                if (age % 6 == 3) sweat(world, pos, random);
            }
            case GREEDY -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_HMM, 0.8f, 1.4f, random);
                if (age == 10) {
                    play(world, pos, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.25f, 1.4f, random);
                    burst(world, pos, ParticleTypes.HAPPY_VILLAGER, 3, random);
                }
                if (age == 20) drool(world, pos, random);
            }
            case MIRROR -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_HMM, 0.8f, 0.8f, random);
                if (age == 20) {
                    play(world, pos, VILLAGER_BLOCK_HMM, 0.8f, 1.3f, random);
                    burst(world, pos, ParticleTypes.ENCHANT, 12, random);
                }
                if (age == 40) play(world, pos, VILLAGER_BLOCK_GASP, 0.8f, 1f, random);
            }
            case HUNGRY -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_HMM, 0.7f, 0.7f, random);
                if (age == 10 || age == 25) drool(world, pos, random);
                if (age == 18) play(world, pos, VILLAGER_BLOCK_MUNCH, 0.3f, 1.4f, random);
            }
            case CURIOUS_POTION -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_HMM, 0.8f, 1.6f, random);
                if (age == 5) burst(world, pos, ParticleTypes.WITCH, 6, random);
            }
            case CATCH_ITEM -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_HMM, 0.8f, 1.5f, random);
                if (age == 4) play(world, pos, VILLAGER_BLOCK_BOING, 0.4f, 1.3f, random);
            }
            case PERSONAL_SPACE -> {
                if (age == 0) {
                    play(world, pos, VILLAGER_BLOCK_NO, 0.8f, 1f, random);
                    burst(world, pos, ParticleTypes.ANGRY_VILLAGER, 2, random);
                }
            }
            case ADMIRE_BUILD -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_YES, 0.7f, 1f, random);
                if (age == 10) {
                    play(world, pos, VILLAGER_BLOCK_HMM, 0.6f, 1.2f, random);
                    burst(world, pos, ParticleTypes.HAPPY_VILLAGER, 3, random);
                }
            }
            case FLINCH, WINCE -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_GASP, reaction == VillagerReaction.WINCE ? 0.4f : 0.6f, 1f, random);
            }
            case FASHION -> {
                // wolf whistle: up... and down
                if (age == 0) whistle(world, pos, 1.1f, random);
                if (age == 4) whistle(world, pos, 1.6f, random);
                if (age == 12) whistle(world, pos, 1.0f, random);
                if (age == 17) whistle(world, pos, 1.4f, random);
            }
            case WHOA -> {
                if (age == 0) {
                    play(world, pos, VILLAGER_BLOCK_GASP, 0.8f, 1.1f, random);
                    burst(world, pos, ParticleTypes.PORTAL, 15, random);
                }
            }
            case CHAT_HMM -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_HMM, 0.9f, 1f, random);
                if (age == 8) play(world, pos, VILLAGER_BLOCK_YES, 0.7f, 1f, random);
            }
            case POKED -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_HMM, 0.8f, 1f + random.nextFloat() * 0.4f, random);
            }
            case ANNOYED -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_NO, 1f, 1f, random);
                if (age == 0 || age == 15) burst(world, pos, ParticleTypes.ANGRY_VILLAGER, 3, random);
            }
            case DIZZY -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_GASP, 0.8f, 0.9f, random);
                if (age == 20 || age == 45) play(world, pos, VILLAGER_BLOCK_HMM, 0.6f, 0.6f, random);
                if (age < 70) orbitStars(world, pos, age, random);
            }
            case TRADE_HAPPY -> {
                if (age == 0) {
                    play(world, pos, VILLAGER_BLOCK_DELIGHTED, 0.9f, 1f, random);
                    play(world, pos, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.2f, random);
                    burst(world, pos, ParticleTypes.HAPPY_VILLAGER, 8, random);
                }
            }
            case FED, FED_LOVE -> {
                if (age % 8 == 0 && age <= 24) {
                    play(world, pos, VILLAGER_BLOCK_MUNCH, 0.7f, 1f, random);
                    burst(world, pos, breadCrumbs(), 3, random);
                }
                if (age == 28) {
                    play(world, pos, reaction == VillagerReaction.FED_LOVE ? VILLAGER_BLOCK_GIGGLE : VILLAGER_BLOCK_YES,
                            0.8f, 1f, random);
                    burst(world, pos, reaction == VillagerReaction.FED_LOVE ? ParticleTypes.HEART : ParticleTypes.HAPPY_VILLAGER,
                            4, random);
                }
            }
            case YUCK -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_MUNCH, 0.6f, 0.8f, random);
                if (age == 8) {
                    play(world, pos, VILLAGER_BLOCK_BLEH, 0.9f, 1f, random);
                    burst(world, pos, ParticleTypes.SNEEZE, 6, random);
                }
                if (age == 20) play(world, pos, VILLAGER_BLOCK_NO, 0.8f, 0.8f, random);
            }
            case SNIFF_FLOWER -> {
                if (age == 0 || age == 8) play(world, pos, VILLAGER_BLOCK_SNIFF, 0.8f, 1f, random);
                if (age == 16) {
                    play(world, pos, VILLAGER_BLOCK_GIGGLE, 0.7f, 1.1f, random);
                    burst(world, pos, ParticleTypes.HEART, 2, random);
                    burst(world, pos, ParticleTypes.CHERRY_LEAVES, 5, random);
                }
            }
            case REFUSE -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_NO, 0.8f, 1f, random);
            }
            case OUCH -> {
                if (age == 0) {
                    play(world, pos, VILLAGER_BLOCK_OUCH, 0.9f, 1f, random);
                    burst(world, pos, ParticleTypes.CRIT, 5, random);
                }
            }
            case GRUMPY -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_GRUMBLE, 1f, 1f, random);
                if (age == 8 || age == 20) {
                    play(world, pos, SoundEvents.BLOCK_WOOL_FALL, 0.8f, 0.6f, random);
                    burst(world, pos, ParticleTypes.ANGRY_VILLAGER, 2, random);
                }
            }
            case FAINT -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_GASP, 0.9f, 1f, random);
                if (age == 18) play(world, pos, VILLAGER_BLOCK_FAINT, 1f, 1f, random);
                if (age == 30) {
                    play(world, pos, VILLAGER_BLOCK_FLOP, 1f, 1f, random);
                    ring(world, pos, ParticleTypes.POOF, 10, random);
                }
                if (age > 30 && age < 80) orbitStars(world, pos, age, random);
                if (age == 84) play(world, pos, VILLAGER_BLOCK_HMM, 0.7f, 0.7f, random);
                if (age == 90) play(world, pos, VILLAGER_BLOCK_BOING, 0.6f, 1f, random);
            }
            case PLEAD -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_GASP, 1f, 1.2f, random);
                if (age == 8) play(world, pos, VILLAGER_BLOCK_NO, 0.9f, 1.4f, random);
                if (age == 16) play(world, pos, VILLAGER_BLOCK_NO, 0.9f, 1.6f, random);
            }
            case SQUISHED -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_OUCH, 0.6f, 0.6f, random);
                if (age == 8) play(world, pos, VILLAGER_BLOCK_BOING, 0.5f, 1f, random);
            }
            case BELLY_FLOP -> {
                if (age == 0) {
                    play(world, pos, VILLAGER_BLOCK_FLOP, 1f, 1f, random);
                    play(world, pos, VILLAGER_BLOCK_OUCH, 0.8f, 0.5f, random);
                    ring(world, pos, ParticleTypes.CLOUD, 10, random);
                }
                if (age > 4 && age < 34) orbitStars(world, pos, age, random);
            }
            case SHIVER -> {
                if (age % 10 == 0) play(world, pos, VILLAGER_BLOCK_SHIVER, 0.8f, 1f, random);
                if (age == 0) burst(world, pos, ParticleTypes.SNOWFLAKE, 8, random);
            }
            case MOURN -> {
                if (age == 0 || age == 30 || age == 60) play(world, pos, VILLAGER_BLOCK_SOB, 0.9f, 1f, random);
                if (age % 5 == 0 && age < 90) tear(world, pos, random);
            }
            case CHEER -> {
                if (age == 0) {
                    play(world, pos, VILLAGER_BLOCK_CHEER, 0.9f, 1f, random);
                    burst(world, pos, ParticleTypes.HAPPY_VILLAGER, 6, random);
                    notes(world, pos, 3, random);
                }
            }
            case YAWN -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_YAWN, 0.8f, 1f, random);
                if (age == 30) play(world, pos, VILLAGER_BLOCK_HMM, 0.4f, 0.6f, random);
            }
            case MORNING_STRETCH -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_YAWN, 0.8f, 1.1f, random);
                if (age == 32) {
                    play(world, pos, VILLAGER_BLOCK_YES, 0.7f, 1.1f, random);
                    burst(world, pos, ParticleTypes.HAPPY_VILLAGER, 4, random);
                }
            }
            case WAKE_STARTLED -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_GASP, 1f, 1f, random);
                if (age == 10) play(world, pos, VILLAGER_BLOCK_HMM, 0.8f, 1.6f, random);
                if (age == 22) play(world, pos, VILLAGER_BLOCK_GRUMBLE, 0.7f, 1.1f, random);
                if (age % 8 == 4) sweat(world, pos, random);
            }
            case SLEEP_TALK -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_WHISPER, 1f, 1f, random);
                if (age == 18) play(world, pos, VILLAGER_BLOCK_WHISPER, 1f, 1.3f, random);
            }
            case RAIN_GRUMBLE -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_GRUMBLE, 0.8f, 1f, random);
                if (age >= 10 && age <= 30 && age % 4 == 2) burst(world, pos, ParticleTypes.SPLASH, 6, random);
            }
            case THUNDER_PANIC -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_GASP, 1f, 1f, random);
                if (age == 12) play(world, pos, VILLAGER_BLOCK_GASP, 0.8f, 1.2f, random);
                if (age % 5 == 0) sweat(world, pos, random);
            }
            case ZOMBIE_PANIC -> {
                if (age == 0 || age == 30) play(world, pos, VILLAGER_BLOCK_GASP, 1f, 1f, random);
                if (age == 15) play(world, pos, VILLAGER_BLOCK_NO, 0.9f, 1.5f, random);
                if (age % 5 == 0) sweat(world, pos, random);
            }
            case COUSIN -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_HMM, 0.8f, 0.8f, random);
                if (age == 20) play(world, pos, VILLAGER_BLOCK_HMM, 0.8f, 1.3f, random);
                if (age == 42) play(world, pos, VILLAGER_BLOCK_GASP, 1f, 1f, random);
            }
            case CAT_LOVE -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_PURR, 0.9f, 1f, random);
                if (age == 5 || age == 25) burst(world, pos, ParticleTypes.HEART, 2, random);
            }
            case GOLEM_HERO -> {
                if (age == 0) {
                    play(world, pos, VILLAGER_BLOCK_CHEER, 0.8f, 1.2f, random);
                    burst(world, pos, ParticleTypes.HAPPY_VILLAGER, 5, random);
                }
            }
            case GOSSIP -> {
                if (age % 14 == 0 && age < 56) play(world, pos, VILLAGER_BLOCK_WHISPER, 1f, 0.9f + random.nextFloat() * 0.5f, random);
                if (age == 50) play(world, pos, VILLAGER_BLOCK_GIGGLE, 0.5f, 1f, random);
            }
            case TRADER_JEALOUS -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_GRUMBLE, 0.8f, 1f, random);
                if (age == 15) play(world, pos, VILLAGER_BLOCK_HMM, 0.7f, 0.8f, random);
            }
            case BELL_ALARM -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_GASP, 1f, 1f, random);
                if (age < 40 && age % 6 == 3) sweat(world, pos, random);
                if (age == 45) play(world, pos, VILLAGER_BLOCK_HMM, 0.7f, 1.3f, random);
            }
            case NOTE_BOB -> {
                if (age == 0) notes(world, pos, 1, random);
            }
            case FIREWORK_WONDER -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_HMM, 0.7f, 1.6f, random);
                if (age == 10) {
                    play(world, pos, VILLAGER_BLOCK_GIGGLE, 0.6f, 1.2f, random);
                    burst(world, pos, ParticleTypes.FIREWORK, 5, random);
                }
            }
            case EXPLOSION_PANIC -> {
                if (age == 0) {
                    play(world, pos, VILLAGER_BLOCK_GASP, 1f, 0.9f, random);
                    burst(world, pos, ParticleTypes.LARGE_SMOKE, 6, random);
                }
                if (age == 20) play(world, pos, VILLAGER_BLOCK_OUCH, 0.8f, 1f, random);
                if (age % 6 == 0) sweat(world, pos, random);
            }
            case JACKPOT, STACK_OF_POKES -> {
                if (age == 0) {
                    play(world, pos, VILLAGER_BLOCK_JACKPOT, 1f, 1f, random);
                    play(world, pos, VILLAGER_BLOCK_DELIGHTED, 1f, 1f, random);
                    burst(world, pos, ParticleTypes.TOTEM_OF_UNDYING, 30, random);
                }
                if (age == 20 || age == 40) play(world, pos, VILLAGER_BLOCK_DELIGHTED, 0.8f, 1.2f, random);
                if (age % 4 == 0 && age < 40) {
                    burst(world, pos, reaction == VillagerReaction.JACKPOT ? CONFETTI_GREEN : CONFETTI_GOLD, 3, random);
                }
                if (age % 10 == 0) notes(world, pos, 1, random);
            }
            case IDLE_LOOK_AROUND -> {
                if (age == 12) play(world, pos, VILLAGER_BLOCK_HMM, 0.4f, 1f, random);
            }
            case IDLE_WHISTLE -> {
                if (age % 8 == 0 && age <= 32) {
                    whistle(world, pos, TUNE[random.nextInt(TUNE.length)], random);
                    notes(world, pos, 1, random);
                }
            }
            case IDLE_NOSE_WIGGLE -> {
                if (age == 0 || age == 10) play(world, pos, VILLAGER_BLOCK_SNIFF, 0.6f, 1.1f, random);
            }
            case IDLE_SNEEZE -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_HMM, 0.6f, 1.3f, random);
                if (age == 10) play(world, pos, VILLAGER_BLOCK_HMM, 0.7f, 1.5f, random);
                if (age == 20) {
                    play(world, pos, VILLAGER_BLOCK_SNEEZE, 1f, 1f, random);
                    burst(world, pos, ParticleTypes.SNEEZE, 8, random);
                }
            }
            case IDLE_HUM -> {
                if (age == 0 || age == 22) {
                    play(world, pos, VILLAGER_BLOCK_HMM, 0.4f, age == 0 ? 0.9f : 1f, random);
                    notes(world, pos, 1, random);
                }
            }
            case IDLE_HICCUP -> {
                if (age == 5 || age == 18) {
                    play(world, pos, VILLAGER_BLOCK_HICCUP, 0.7f, 1f, random);
                    burst(world, pos, ParticleTypes.BUBBLE_POP, 3, random);
                }
            }
            case IDLE_SPIN -> {
                if (age == 0) play(world, pos, VILLAGER_BLOCK_GIGGLE, 0.7f, 1.2f, random);
            }
            case IDLE_BREAKDANCE -> {
                // beatbox: kick on the beat, hi-hat in between
                if (age == 0) play(world, pos, VILLAGER_BLOCK_DELIGHTED, 0.8f, 1f, random);
                if (age % 10 == 0 && age < 72) play(world, pos, SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(), 0.6f, 0.8f, random);
                if (age % 10 == 5 && age < 72) play(world, pos, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.4f, 1.2f, random);
                if (age % 10 == 0 && age < 72) notes(world, pos, 1, random);
            }
            case IDLE_DISGUISE -> {
                if (age == 0 || age == 60) {
                    play(world, pos, VILLAGER_BLOCK_POOF, 0.8f, 1f, random);
                    burst(world, pos, ParticleTypes.POOF, 10, random);
                }
                if (age == 62) play(world, pos, VILLAGER_BLOCK_GIGGLE, 0.7f, 1.1f, random);
            }
        }
    }

    /** One client tick of a lasting mode. */
    public static void modeTick(World world, BlockPos pos, VillagerMode mode, long time, Random random) {
        int phase = (int) (pos.asLong() & 63);
        switch (mode) {
            case SLEEP -> {
                if ((time + phase) % 40 == 0) {
                    world.addParticle(SLEEPY_Z, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3, pos.getY() + 1.1,
                            pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3, 0.005, 0.03, 0.005);
                }
                if ((time + phase) % 70 == 0) play(world, pos, VILLAGER_BLOCK_SNORE, 1f, 1f, random);
            }
            case DANCE -> {
                if ((time + phase) % 12 == 0) notes(world, pos, 1, random);
            }
            case DANCE_FUNKY -> {
                if ((time + phase) % 6 == 0) notes(world, pos, 1, random);
                if ((time + phase) % 40 == 0) burst(world, pos, ParticleTypes.HAPPY_VILLAGER, 2, random);
            }
            default -> {
            }
        }
    }

    /**
     * A player's breaking progress on it changed ({@code stage} 0..9, -1: stopped). Escalating: a pleading "hmm?!",
     * gasps, whimpers, sobs; relief when they stop.
     */
    public static void miningStageChanged(World world, BlockPos pos, int previous, int stage, Random random) {
        if (stage < 0) {
            // spared (or broken: then the block is gone and this doesn't run)
            if (previous >= 0) play(world, pos, VILLAGER_BLOCK_HMM, 0.6f, 0.8f, random);
            return;
        }
        if (previous < 0) play(world, pos, VILLAGER_BLOCK_GASP, 0.9f, 1.1f, random);
        else if (stage == 3 || stage == 5) play(world, pos, VILLAGER_BLOCK_WHIMPER, 0.8f, 1.05f - stage * 0.04f, random);
        else if (stage == 7 || stage == 9) play(world, pos, VILLAGER_BLOCK_SOB, 0.9f, 1.1f - stage * 0.05f, random);
    }

    /** Each client tick while it is being broken: sweat, then tears, more and more. */
    public static void miningTick(World world, BlockPos pos, int stage, int age, Random random) {
        int every = stage < 3 ? 6 : stage < 6 ? 4 : 2;
        if (age % every != 0) return;
        if (stage < 5) sweat(world, pos, random);
        if (stage >= 3) tear(world, pos, random);
        if (stage >= 7 && random.nextInt(3) == 0) {
            world.addParticle(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.6, 0, 0.05, 0);
        }
    }

    // --- helpers ---

    private static void play(World world, BlockPos pos, SoundEvent sound, float volume, float pitch, Random random) {
        world.playSound(pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, sound, SoundCategory.BLOCKS, volume,
                pitch * (0.95f + random.nextFloat() * 0.1f), false);
    }

    private static void whistle(World world, BlockPos pos, float pitch, Random random) {
        world.playSound(pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, VILLAGER_BLOCK_WHISTLE, SoundCategory.BLOCKS,
                0.8f, pitch, false);
    }

    /** {@code count} particles just above the block, drifting up a little. */
    private static void burst(World world, BlockPos pos, ParticleEffect effect, int count, Random random) {
        for (int i = 0; i < count; i++) {
            world.addParticle(effect, pos.getX() + 0.15 + random.nextDouble() * 0.7, pos.getY() + 1.05 + random.nextDouble() * 0.3,
                    pos.getZ() + 0.15 + random.nextDouble() * 0.7,
                    (random.nextDouble() - 0.5) * 0.05, 0.02 + random.nextDouble() * 0.05, (random.nextDouble() - 0.5) * 0.05);
        }
    }

    /** A ring of particles puffing out around the base. */
    private static void ring(World world, BlockPos pos, ParticleEffect effect, int count, Random random) {
        for (int i = 0; i < count; i++) {
            float a = MathHelper.TAU * i / count;
            float c = MathHelper.cos(a), s = MathHelper.sin(a);
            world.addParticle(effect, pos.getX() + 0.5 + c * 0.6, pos.getY() + 0.1, pos.getZ() + 0.5 + s * 0.6,
                    c * 0.08, 0.02, s * 0.08);
        }
    }

    private static void notes(World world, BlockPos pos, int count, Random random) {
        for (int i = 0; i < count; i++) {
            // the note particle's colour is its x "velocity" (0..1)
            world.addParticle(ParticleTypes.NOTE, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.2,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, random.nextInt(25) / 24.0, 0, 0);
        }
    }

    /** A sweat drop sliding off one of the sides. */
    private static void sweat(World world, BlockPos pos, Random random) {
        double along = 0.2 + random.nextDouble() * 0.6;
        double x, z;
        switch (random.nextInt(4)) {
            case 0 -> { x = -0.02; z = along; }
            case 1 -> { x = 1.02; z = along; }
            case 2 -> { x = along; z = -0.02; }
            default -> { x = along; z = 1.02; }
        }
        world.addParticle(ParticleTypes.FALLING_WATER, pos.getX() + x, pos.getY() + 0.85, pos.getZ() + z, 0, 0, 0);
    }

    /** A tear rolling off the face (its eyes are on the top). */
    private static void tear(World world, BlockPos pos, Random random) {
        world.addParticle(ParticleTypes.FALLING_WATER, pos.getX() + 0.45 + random.nextDouble() * 0.35, pos.getY() + 1.02,
                pos.getZ() + 0.45 + random.nextDouble() * 0.2, 0, 0, 0);
    }

    private static void drool(World world, BlockPos pos, Random random) {
        world.addParticle(ParticleTypes.DRIPPING_WATER, pos.getX() + 0.45 + random.nextDouble() * 0.1, pos.getY() + 1.02,
                pos.getZ() + 0.8, 0, 0, 0);
    }

    /** Little stars circling above its head. */
    private static void orbitStars(World world, BlockPos pos, int age, Random random) {
        if (age % 2 != 0) return;
        float base = age * 0.35f;
        for (int i = 0; i < 3; i++) {
            float a = base + i * MathHelper.TAU / 3f;
            world.addParticle(STAR, pos.getX() + 0.5 + MathHelper.cos(a) * 0.45, pos.getY() + 1.25,
                    pos.getZ() + 0.5 + MathHelper.sin(a) * 0.45, 0, 0, 0);
        }
    }

    private static ParticleEffect breadCrumbs() {
        if (breadCrumbs == null) breadCrumbs = new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.BREAD));
        return breadCrumbs;
    }
}
