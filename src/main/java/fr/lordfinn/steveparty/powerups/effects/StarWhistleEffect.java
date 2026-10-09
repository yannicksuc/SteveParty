package fr.lordfinn.steveparty.powerups.effects;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * The Star Whistle power-up (item {@code powerup_star_whistle}): used at the start of its owner's turn, before the
 * roll, it sends the Star from the space carrying it to another active Star space, picked at random.
 * <ul>
 *     <li>Never the same space: the Star always lands on another active Star space (see
 *     {@link StarRelocator#moveStarElsewhere}).</li>
 *     <li>No other active Star space: nothing moves, the power-up is not used up and its user is told why. Same when
 *     the board has no Star at all.</li>
 *     <li>The party is told « X whistles: the Star flies off to another space! ». A two-note flute whistle sounds on the
 *     space it leaves, a chime on the space it lands on, with notes and sparkles on both.</li>
 * </ul>
 * Server side only. The caller (the power-up item) consumes the item when {@link Result#consumed()} is true, then the
 * turn goes on (the roll) either way.
 */
public final class StarWhistleEffect {
    public static final String USED_KEY = "message.steveparty.powerup.star_whistle.used";
    public static final String NO_STAR_KEY = "message.steveparty.powerup.star_whistle.no_star";
    public static final String NO_OTHER_SPACE_KEY = "message.steveparty.powerup.star_whistle.no_other_space";

    /** What using the power-up did. */
    public enum Outcome {
        /** The Star moved from {@link Result#from()} to {@link Result#to()}: the power-up is used up. */
        MOVED(true),
        /** The board has no Star: nothing happened. */
        NO_STAR(false),
        /** The Star is on the only active Star space: nothing happened. */
        NO_OTHER_SPACE(false);

        private final boolean consumed;

        Outcome(boolean consumed) {
            this.consumed = consumed;
        }
    }

    /**
     * @param outcome what happened
     * @param from    the space the Star stood on (MOVED, NO_OTHER_SPACE), else null
     * @param to      the space the Star stands on now (MOVED), else null
     */
    public record Result(Outcome outcome, @Nullable BlockPos from, @Nullable BlockPos to) {
        /** True when the power-up has done its effect and must be used up. */
        public boolean consumed() {
            return outcome.consumed;
        }
    }

    private final StarRelocator starRelocator;

    /** @param starRelocator where the Star is and how it moves (the Star cartridge's, or a test one) */
    public StarWhistleEffect(StarRelocator starRelocator) {
        this.starRelocator = Objects.requireNonNull(starRelocator);
    }

    /**
     * Uses the Star Whistle in {@code party}.
     *
     * @param party  the party being played (where the Star is, who is told)
     * @param token  the pawn of the player whose turn it is (named if there is no {@code user}), or null
     * @param user   the player using it (named in the announcement, told when nothing happens), or null
     * @param random where the new Star space is picked from
     */
    public Result use(PartyControllerEntity party, @Nullable Entity token, @Nullable ServerPlayerEntity user, Random random) {
        Optional<BlockPos> star = starRelocator.currentStarSpace(party);
        if (star.isEmpty()) {
            PowerUpStar.tell(party, user, Text.translatable(NO_STAR_KEY).formatted(Formatting.RED));
            return new Result(Outcome.NO_STAR, null, null);
        }
        BlockPos from = star.get().toImmutable();
        boolean moved = StarRelocator.pickOther(starRelocator.activeStarSpaces(party), from, random).isPresent()
                && starRelocator.moveStarElsewhere(party, random);
        BlockPos to = moved ? starRelocator.currentStarSpace(party).map(BlockPos::toImmutable).orElse(null) : null;
        if (to == null || to.equals(from)) {
            PowerUpStar.tell(party, user, Text.translatable(NO_OTHER_SPACE_KEY).formatted(Formatting.YELLOW));
            return new Result(Outcome.NO_OTHER_SPACE, from, null);
        }

        Text who = user != null ? user.getDisplayName()
                : token != null ? token.getDisplayName()
                : Text.translatable("message.steveparty.unknown_player");
        MessageUtils.sendToPlayers(party.getPartyAudience(), Text.translatable(USED_KEY, who).formatted(Formatting.GOLD),
                MessageUtils.MessageType.CHAT);
        if (party.getWorld() instanceof ServerWorld world) show(world, from, to);
        return new Result(Outcome.MOVED, from, to);
    }

    /** The whistle on the space the Star leaves, the chime on the one it lands on. */
    private static void show(ServerWorld world, BlockPos from, BlockPos to) {
        double fx = from.getX() + 0.5, fy = from.getY() + 1.2, fz = from.getZ() + 0.5;
        world.playSound(null, fx, fy, fz, SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), SoundCategory.PLAYERS, 1.0f, 1.5f);
        world.playSound(null, fx, fy, fz, SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), SoundCategory.PLAYERS, 1.0f, 2.0f);
        world.spawnParticles(ParticleTypes.NOTE, fx, fy + 0.4, fz, 6, 0.4, 0.2, 0.4, 1.0);
        world.spawnParticles(ParticleTypes.END_ROD, fx, fy, fz, 20, 0.2, 0.6, 0.2, 0.08);

        double tx = to.getX() + 0.5, ty = to.getY() + 1.2, tz = to.getZ() + 0.5;
        world.playSound(null, tx, ty, tz, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1.0f, 1.2f);
        world.spawnParticles(ParticleTypes.NOTE, tx, ty + 0.4, tz, 6, 0.4, 0.2, 0.4, 1.0);
        world.spawnParticles(ParticleTypes.FIREWORK, tx, ty, tz, 24, 0.3, 0.4, 0.3, 0.05);
    }
}
