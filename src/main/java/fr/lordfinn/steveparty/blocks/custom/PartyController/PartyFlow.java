package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.blocks.custom.PartyBellBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EventPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity.*;

/**
 * The course of a party, step after step: moving to the next / previous step (ringing the party bells of the moments
 * in between, waiting for them when one waits), resuming the step that was running when the controller was saved,
 * and the phase the comparators read.
 */
final class PartyFlow {
    private final PartyControllerEntity controller;
    /** Set once the step that was running when this controller was saved has been resumed. */
    private boolean resumeDone = false;
    /** Last phase given to comparators, to notify them only when it changes. */
    private int lastPhase = -1;

    PartyFlow(PartyControllerEntity controller) {
        this.controller = controller;
    }

    private PartyData data() {
        return controller.getPartyData();
    }

    /** Loaded from a save: the step that was running will be resumed. */
    void loaded() {
        resumeDone = false;
    }

    /** A party starts: nothing to resume. */
    void started() {
        resumeDone = true;
    }

    /**
     * Server tick of the current step. Scheduled tasks are not saved, so after a load the step that was running is
     * resumed once the party is really playable again (at least one token loaded and one owner online, or any player
     * for an ownerless token), otherwise a step could wrongly consider the tokens as missing.
     */
    void tick(ServerWorld serverWorld) {
        PartyData partyData = data();
        PartyStep currentStep = partyData.getCurrentStep();
        if (resumeDone) {
            // Once resumed (or started), the current step gets ticked (e.g. the countdown of an absent turn)
            if (currentStep != null && currentStep.getStatus() == PartyStep.Status.IN_PROGRESS)
                currentStep.tick(controller, serverWorld);
            return;
        }
        if (!partyData.isStarted() || currentStep == null || currentStep.getStatus() != PartyStep.Status.IN_PROGRESS) {
            resumeDone = true;
            return;
        }
        if (serverWorld.getTime() % 20 != 0) return;
        List<TokenizedEntityInterface> loadedTokens = partyData.getTokens(serverWorld);
        if (loadedTokens.isEmpty()) return;
        // An ownerless token can be played by anyone: a connected player is enough
        boolean playable = !partyData.getOwners(serverWorld).isEmpty()
                || (!serverWorld.getPlayers().isEmpty() && loadedTokens.stream().anyMatch(token -> token.steveparty$getTokenOwner() == null));
        if (!playable) return;
        resumeDone = true;
        currentStep.resume(controller);
        controller.markDirty();
        controller.sendPacketToInterestedPlayers();
    }

    void nextStep() {
        PartyData partyData = data();
        PartyStep currentStep = partyData.getCurrentStep();
        int index = partyData.getStepIndex();
        // A mini-game ended from outside (a step controller): its results are read on its podiums and paid all the same
        if (currentStep instanceof MiniGamePartyStep miniGame) miniGame.concludeIfPlaying(controller);
        endCurrentStep();
        if (currentStep instanceof EventPartyStep event && event.isTransition() && index >= 0) {
            // Its moments rang: go on with the step it was inserted before (already announced)
            partyData.getSteps().remove(index);
            startStep(index);
            return;
        }
        goToStep(index + 1, currentStep);
    }

    /**
     * Moves to the step at {@code target} coming from {@code from}, ringing the party bells of the moments of this
     * transition. If a bell waits at one of them, a transition step is inserted first: it rings the moments one
     * after the other and waits for the bells.
     */
    private void goToStep(int target, @Nullable PartyStep from) {
        PartyData partyData = data();
        List<PartyStep> steps = partyData.getSteps();
        PartyStep to = target >= 0 && target < steps.size() ? steps.get(target) : null;
        List<PartyMoment> moments = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        collectTransitionMoments(from, to, target, moments, values);
        // The index first: the bells listen to started parties (or parties standing on their end)
        partyData.setStepIndex(target);
        if (moments.stream().anyMatch(moment -> PartyBellBlockEntity.hasWaitingBell(controller, moment))) {
            steps.add(target, EventPartyStep.transition(moments, values));
        } else {
            for (int i = 0; i < moments.size(); i++)
                PartyBellBlockEntity.ring(controller, moments.get(i), values.get(i));
        }
        startStep(target);
    }

    private void collectTransitionMoments(@Nullable PartyStep from, @Nullable PartyStep to, int target,
                                          List<PartyMoment> moments, List<Integer> values) {
        PartyData partyData = data();
        if (from instanceof TokenTurnPartyStep turn) {
            moments.add(PartyMoment.TURN_END);
            values.add(partyData.getTokenRank(turn.getTokenUUID()));
        } else if (from instanceof MiniGamePartyStep miniGame) {
            moments.add(PartyMoment.MINIGAME_END);
            values.add(miniGame.getWinners().size());
        }
        if (to == null) return;
        if (from == null && target == 0) {
            moments.add(PartyMoment.PARTY_START);
            values.add(partyData.getTokens().size());
        }
        if (to instanceof TokenTurnPartyStep turn) {
            if (partyData.isRoundStart(target)) {
                moments.add(PartyMoment.ROUND_START);
                values.add(partyData.getRoundAt(target));
            }
            moments.add(PartyMoment.TURN_START);
            values.add(partyData.getTokenRank(turn.getTokenUUID()));
        } else if (to.getType() == PartyStepType.END) {
            moments.add(PartyMoment.PARTY_END);
            values.add(partyData.getTokens().size());
        }
    }

    /** See {@link PartyControllerEntity#releaseMoment}. */
    boolean releaseMoment(PartyMoment moment) {
        PartyStep currentStep = data().getCurrentStep();
        if (currentStep == null || currentStep.getStatus() != PartyStep.Status.IN_PROGRESS) return false;
        if (!currentStep.onMomentReleased(moment, controller)) return false;
        controller.markDirty();
        controller.sendPacketToInterestedPlayers();
        return true;
    }

    /** See {@link PartyControllerEntity#ringMoment}. */
    boolean ringMoment(PartyMoment moment, int value) {
        boolean waiting = PartyBellBlockEntity.ring(controller, moment, value);
        updatePhase();
        return waiting;
    }

    void restartStep() {
        endCurrentStep();
        startStep(data().getStepIndex());
    }

    void previousStep() {
        PartyData partyData = data();
        PartyStep currentStep = partyData.getCurrentStep();
        boolean leavingEnd = currentStep != null && currentStep.getType() == PartyStepType.END && partyData.getStepIndex() > 0;
        endCurrentStep();
        if (currentStep instanceof EventPartyStep event && event.isTransition()) {
            // Not a real step: drop it, then go back to the step before it
            partyData.getSteps().remove(partyData.getStepIndex());
        }
        // The END step released the tokens: going back into the game puts them in game again
        // (the step being resumed grants CAN_MOVE as usual)
        if (leavingEnd && controller.getWorld() instanceof ServerWorld serverWorld)
            controller.tokenHomes().restoreInGame(serverWorld, partyData.getTokens());
        startStep(partyData.getStepIndex() - 1);
    }

    void endCurrentStep() {
        PartyStep currentStep = data().getCurrentStep();
        if (currentStep != null) {
            currentStep.setStatus(PartyStep.Status.FINISHED);
            currentStep.end(controller);
        }
    }

    private void startStep(int stepIndex) {
        // The party is live: nothing left to resume from a previous load
        resumeDone = true;
        PartyData partyData = data();
        partyData.setStepIndex(stepIndex);
        PartyStep currentStep = partyData.getCurrentStep();
        if (currentStep != null)
            currentStep.start(controller);
        controller.markDirty();
        controller.sendPacketToInterestedPlayers();
    }

    /** See {@link PartyControllerEntity#getPhase}. */
    int phase() {
        PartyData partyData = data();
        PartyStep currentStep = partyData.getCurrentStep();
        if (currentStep == null || (!partyData.isStarted() && !partyData.isAtEnd())) return PHASE_IDLE;
        if (currentStep.isWaitingForBell()) return PHASE_WAITING;
        return switch (currentStep.getType()) {
            case TOKEN_TURN -> PHASE_TURN;
            case MINI_GAME -> PHASE_MINIGAME;
            case END -> PHASE_END;
            default -> PHASE_PREPARING;
        };
    }

    /** The comparators are told when the phase changed. */
    void updatePhase() {
        if (controller.getWorld() == null || controller.getWorld().isClient) return;
        int phase = phase();
        if (phase == lastPhase) return;
        lastPhase = phase;
        controller.getWorld().updateComparators(controller.getPos(), controller.getCachedState().getBlock());
    }
}
