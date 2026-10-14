package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.StartRollsStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;

/**
 * What a Step Controller's redstone pulse does to a party, by its mode. Never in the middle of something: a pulse that
 * can't act now does nothing (the controller says so by its sound).
 * <ul>
 *     <li>{@link Mode#NEXT}, « Suivant »: the party stops waiting for what it waits for. A party bell holding a moment:
 *     released. The turn-order rolls: closed, who has not rolled plays last. A turn whose player has not rolled (or
 *     whose pawn is absent): passed; a turn that has rolled ends by itself. The practice round: the real round starts.
 *     The real round: it ends with the places on its podiums as they stand. Any other step (an addon's...): the next
 *     one.</li>
 *     <li>{@link Mode#REPLAY}, « Rejouer »: the step is played again. The turn-order rolls: everyone rolls again. A turn:
 *     undone by the party's turn journal (the pawn back, what the game gave and took during it back where it came from)
 *     and played again from its start ({@link PartyRewind#replay}). The practice or real round: it starts again.</li>
 *     <li>{@link Mode#PREVIOUS}, « Précédent »: the turn being played and the one before it are undone, the one before
 *     is played again from its start; each pulse goes one turn further back, as far as the journal goes
 *     ({@link PartyRewind#previous}). Not during a mini-game.</li>
 * </ul>
 * Never at the party's end, nor during the draw, the countdown or the results of a mini-game, nor while something is
 * under way (a pawn walking, a die rolling) for a rewind.
 */
public final class StepPulse {
    /**
     * The mode of a Step Controller, cycled by a click: its model points up for « next », sideways for « replay », down
     * for « previous ».
     */
    public enum Mode {
        NEXT, REPLAY, PREVIOUS;

        public static Mode of(int index) {
            return index >= 0 && index < values().length ? values()[index] : NEXT;
        }

        public Mode cycle() {
            return values()[(ordinal() + 1) % values().length];
        }

        /** Its name, as the controller says it when clicked. */
        public String translationKey() {
            return "block.steveparty.step_controller.mode." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private StepPulse() {
    }

    /** @return true if the pulse moved the party */
    public static boolean apply(PartyControllerEntity party, Mode mode) {
        PartyStep step = party.getPartyData().getCurrentStep();
        if (step == null || step.getStatus() != PartyStep.Status.IN_PROGRESS || step instanceof EndPartyStep) return false;
        if (mode == Mode.PREVIOUS) return PartyRewind.previous(party) != null;
        if (mode == Mode.NEXT && step.isWaitingForBell()) {
            for (PartyMoment moment : PartyMoment.values()) if (party.releaseMoment(moment)) return true;
            return false;
        }
        return switch (step) {
            case TokenTurnPartyStep turn -> mode == Mode.NEXT ? turn.pass(party) : PartyRewind.replay(party) != null;
            case MiniGamePartyStep miniGame -> mode == Mode.NEXT ? miniGame.pass(party) : miniGame.replayRound(party);
            case StartRollsStep rolls -> mode == Mode.NEXT ? rolls.pass(party) : rolls.rollAgain(party);
            default -> {
                if (mode != Mode.NEXT) yield false;
                party.nextStep();
                yield true;
            }
        };
    }
}
