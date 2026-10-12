package fr.lordfinn.steveparty.dice;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.powerups.PowerUpLimit;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * One throw per roll: during a roll of a running party (their turn's roll, the turn order rolls), the first die a
 * player throws is their roll, whatever die it is (a Double / Triple Dice is one throw); another throw is refused,
 * the die staying in hand, until the step opens a new roll (a roll of blank sides, a replay, the next turn). The re-roll
 * modules (Lucky, Reroll) play inside that one throw. Outside a party, nothing changes. Server side.
 */
public final class OneThrowPerRoll {
    private OneThrowPerRoll() {
    }

    /** The current step of {@code party} (the running party a player plays in), in progress; null if none. */
    private static @Nullable PartyStep stepOf(@Nullable PartyControllerEntity party) {
        if (party == null) return null;
        PartyStep step = party.getPartyData().getCurrentStep();
        return step != null && step.getStatus() == PartyStep.Status.IN_PROGRESS ? step : null;
    }

    /** Whether {@code player} must be refused a throw now (told why: action bar and a sound). */
    public static boolean refusesThrow(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return false;
        PartyControllerEntity party = PowerUpLimit.partyOf(player.getUuid());
        PartyStep step = stepOf(party);
        if (step == null || !step.refusesThrow(player.getUuid(), party)) return false;
        MessageUtils.sendToPlayer(serverPlayer, Text.translatable("message.steveparty.dice.already_rolled").formatted(Formatting.RED),
                MessageUtils.MessageType.ACTION_BAR);
        serverPlayer.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.6F, 0.8F);
        return true;
    }

    /** {@code player} threw {@code lead} (the first die of the throw): it is their roll of the current step. */
    public static void onThrown(PlayerEntity player, DiceEntity lead) {
        if (!(player instanceof ServerPlayerEntity)) return;
        PartyStep step = stepOf(PowerUpLimit.partyOf(player.getUuid()));
        if (step != null) step.onDieThrown(player.getUuid(), lead);
    }

    /** True while {@code die} is a throw still on its way to its result. */
    public static boolean inFlight(@Nullable DiceEntity die) {
        return die != null && !die.isRemoved() && die.isAlive() && !die.isRollFinished();
    }
}
