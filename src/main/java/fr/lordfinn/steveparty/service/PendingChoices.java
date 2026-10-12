package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.DirectionDisplayEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Whether the player of a party's token has a choice to make right now: from the moment it is asked until it is made,
 * whatever asks it. Its pawn then shows a light beam in its colour ({@link PawnBeams}), so that everyone sees who is
 * being waited for.
 * <p>
 * Derived from the waiting states that already exist, never a flag of its own (nothing to forget to clear):
 * <ul>
 *     <li>the party's current step waits for this token's player ({@link PartyStep#waitsForChoiceOf}): the turn order
 *     rolls, its turn's roll (a power-up may be used first);</li>
 *     <li>its player has a picker to answer ({@link DicePrompts}: a Lucky or Choice die, keep or reroll, the token to
 *     swap with, a power-up's target, the Frousseux's victim, a Key gate...); during a turn of another token of the
 *     same player, only that turn's token shows it;</li>
 *     <li>the arrows of a fork wait for its player to pick a way;</li>
 *     <li>a shop stop ({@link ShopStops}), the star to buy or not ({@link PartyStars}), a Key gate's question
 *     ({@link KeyGates}), the Trichaudron's heads held out ({@link TrichaudronPrizes}).</li>
 * </ul>
 * A new choice point either keeps a waiting state of its own and adds it here with {@link #register}, or asks through
 * {@link DicePrompts} or a party step, which are already heard.
 */
public final class PendingChoices {
    /** A source of choices: true while the player of {@code token} (of {@code party}) has one to make there. */
    @FunctionalInterface
    public interface Source {
        boolean waits(PartyControllerEntity party, ServerWorld world, MobEntity token, @Nullable UUID owner);
    }

    /** How far from its token the arrows of a fork stand (they start 1.5 blocks from the space's middle). */
    private static final double FORK_REACH = 2.5;

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>(List.of(
            (party, world, token, owner) -> party.getPartyData().getCurrentStep() instanceof PartyStep step
                    && step.waitsForChoiceOf(token.getUuid()),
            PendingChoices::answersPrompt,
            (party, world, token, owner) -> atFork(world, token),
            (party, world, token, owner) -> ShopStops.isShopping(token.getUuid()),
            (party, world, token, owner) -> PartyStars.isDeciding(token.getUuid()),
            (party, world, token, owner) -> KeyGates.isAsking(token),
            (party, world, token, owner) -> TrichaudronPrizes.isChoosing(token)));

    private PendingChoices() {
    }

    /** Adds a source of choices (a new space or step waiting for its player's input). */
    public static void register(Source source) {
        SOURCES.add(source);
    }

    /** True while the player of {@code token}, a token of {@code party}, has a choice to make. */
    public static boolean waits(PartyControllerEntity party, ServerWorld world, MobEntity token) {
        UUID owner = token instanceof TokenizedEntityInterface tokenized ? tokenized.steveparty$getTokenOwner() : null;
        for (Source source : SOURCES) {
            if (source.waits(party, world, token, owner)) return true;
        }
        return false;
    }

    /**
     * Its player has a picker open. A player with several tokens: during the turn of one of them, that one shows it.
     */
    private static boolean answersPrompt(PartyControllerEntity party, ServerWorld world, MobEntity token, @Nullable UUID owner) {
        if (owner == null || !DicePrompts.isAsking(owner)) return false;
        return !(party.getPartyData().getCurrentStep() instanceof TokenTurnPartyStep turn)
                || !owner.equals(turn.getOwnerUUID()) || token.getUuid().equals(turn.getTokenUUID());
    }

    /** The token waits at a fork, its arrows shown (it still has steps to walk). */
    private static boolean atFork(ServerWorld world, MobEntity token) {
        if (!(token instanceof TokenizedEntityInterface tokenized) || tokenized.steveparty$getNbSteps() == 0) return false;
        UUID id = token.getUuid();
        return !world.getEntitiesByClass(DirectionDisplayEntity.class, token.getBoundingBox().expand(FORK_REACH),
                arrow -> id.equals(arrow.getTokenUuid())).isEmpty();
    }
}
