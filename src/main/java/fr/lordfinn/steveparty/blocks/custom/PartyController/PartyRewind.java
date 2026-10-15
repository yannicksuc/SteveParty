package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.journal.JournalFrame;
import fr.lordfinn.steveparty.blocks.custom.PartyController.journal.JournalRewind;
import fr.lordfinn.steveparty.blocks.custom.PartyController.journal.PartyJournal;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileTeleport;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.service.AdvanceBackMoves;
import fr.lordfinn.steveparty.service.BoardSequences;
import fr.lordfinn.steveparty.service.DiceRollEffects;
import fr.lordfinn.steveparty.service.KeyGates;
import fr.lordfinn.steveparty.service.PartyStars;
import fr.lordfinn.steveparty.service.ShopStops;
import fr.lordfinn.steveparty.service.TrichaudronPrizes;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * A Party Rewinder going back in a party with its turn journal ({@link PartyJournal}): « replay » undoes the turn being
 * played and plays it again from its start; « previous » undoes it and the turn before it (passing over a mini-game in
 * between, whose gains are taken back) and plays that one again. Never while something is under way (a pawn walking, a
 * die rolling or resolving, a choice asked, a show on a space, a shop open), nor during a mini-game or at the party's end.
 */
public final class PartyRewind {
    private PartyRewind() {
    }

    /**
     * The turn being played is undone and starts again.
     *
     * @return the red lines of what could not be undone (told in the chat), null if nothing was done
     */
    public static @Nullable List<Text> replay(PartyControllerEntity party) {
        PartyData data = party.getPartyData();
        if (!(data.getCurrentStep() instanceof TokenTurnPartyStep)) return null;
        JournalFrame current = party.getJournal().current();
        if (current == null || current.kind() != JournalFrame.Kind.TURN || current.stepIndex() != data.getStepIndex()) return null;
        return rewind(party, 1, "message.steveparty.journal.replayed");
    }

    /**
     * The party goes back to the start of the turn before the one being played.
     *
     * @return the red lines of what could not be undone (told in the chat), null if nothing was done
     */
    public static @Nullable List<Text> previous(PartyControllerEntity party) {
        PartyData data = party.getPartyData();
        PartyStep step = data.getCurrentStep();
        if (step == null || step.getStatus() != PartyStep.Status.IN_PROGRESS || step instanceof MiniGamePartyStep || !data.isStarted()) return null;
        List<JournalFrame> frames = party.getJournal().frames();
        int index = frames.size() - 1;
        // The turn being played is undone first, then back to the turn before it
        if (step instanceof TokenTurnPartyStep) {
            JournalFrame current = party.getJournal().current();
            if (current == null || current.kind() != JournalFrame.Kind.TURN || current.stepIndex() != data.getStepIndex()) return null;
            index--;
        }
        while (index >= 0 && frames.get(index).kind() != JournalFrame.Kind.TURN) index--;
        if (index < 0) return null;
        return rewind(party, frames.size() - index, "message.steveparty.journal.back");
    }

    /**
     * How many turns « previous » can go back now, pulse after pulse, as far as the journal goes (0 during a mini-game,
     * or with nothing to undo).
     */
    public static int turnsBack(PartyControllerEntity party) {
        PartyData data = party.getPartyData();
        PartyStep step = data.getCurrentStep();
        if (step == null || step.getStatus() != PartyStep.Status.IN_PROGRESS || step instanceof MiniGamePartyStep || !data.isStarted()) return 0;
        List<JournalFrame> frames = party.getJournal().frames();
        int turns = 0;
        for (int i = frames.size() - 1; i >= 0 && !frames.get(i).isOverflowing(); i--) {
            if (frames.get(i).kind() == JournalFrame.Kind.TURN) turns++;
        }
        JournalFrame current = party.getJournal().current();
        boolean playing = step instanceof TokenTurnPartyStep && current != null && current.kind() == JournalFrame.Kind.TURN
                && current.stepIndex() == data.getStepIndex();
        return Math.max(0, playing ? turns - 1 : turns);
    }

    private static @Nullable List<Text> rewind(PartyControllerEntity party, int count, String told) {
        if (!(party.getWorld() instanceof ServerWorld world) || busy(party, world)) return null;
        List<JournalFrame> frames = party.getJournal().frames();
        for (int i = frames.size() - count; i < frames.size(); i++) if (frames.get(i).isOverflowing()) return null;
        // The step being played ends here (its pawn may no longer move), without moving on
        party.flow().endCurrentStep();
        List<JournalFrame> undone = party.getJournal().takeLast(count);
        List<Text> lines = JournalRewind.undo(party, undone);
        int stepIndex = undone.getLast().stepIndex();
        party.flow().restart(stepIndex);
        int turnsUndone = (int) undone.stream().filter(frame -> frame.kind() == JournalFrame.Kind.TURN).count();
        party.ringMoment(PartyMoment.REWIND, Math.max(1, turnsUndone));
        Text name = party.getPartyData().getCurrentStep() instanceof TokenTurnPartyStep turn ? turn.getTokenDisplayName(world) : Text.literal("?");
        List<ServerPlayerEntity> audience = party.getPartyAudience();
        MessageUtils.sendToPlayers(audience, Text.translatable(told, name).formatted(Formatting.GOLD), MessageUtils.MessageType.CHAT);
        for (Text line : lines) MessageUtils.sendToPlayers(audience, line, MessageUtils.MessageType.CHAT);
        party.markDirty();
        party.sendPacketToInterestedPlayers();
        return lines;
    }

    /** Something under way for one of its pawns or players: nothing is undone in the middle of it. */
    static boolean busy(PartyControllerEntity party, ServerWorld world) {
        if (party.getPartyData().getCurrentStep() instanceof TokenTurnPartyStep turn && turn.isRollInFlight()) return true;
        for (UUID token : party.getPartyData().getTokens()) {
            if (Steveparty.SCHEDULER.isScheduled(token) || DiceRollEffects.isResolving(token) || BoardSequences.anyRunning(token)) return true;
            if (!(world.getEntity(token) instanceof MobEntity mob)) continue;
            if (mob instanceof TokenizedEntityInterface pawn && pawn.steveparty$getNbSteps() > 0) return true;
            // A choice asked (not the roll a turn starts with: that one is what a replay goes back to)
            if (AdvanceBackMoves.isFlying(mob) || AdvanceBackMoves.isWaiting(mob) || TileTeleport.isTeleporting(mob) || TileTeleport.isPushed(mob)
                    || ShopStops.isShopping(token) || PartyStars.isDeciding(token) || KeyGates.isAsking(mob) || TrichaudronPrizes.isChoosing(mob))
                return true;
        }
        for (UUID player : party.getPlayersInOrder()) if (DicePrompts.isAsking(player)) return true;
        return false;
    }
}
