package fr.lordfinn.steveparty.api.event;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import java.util.List;

/**
 * The life of a party, as its Party Controller plays it (server side). The listeners are called in the order they were
 * registered; Steve Party's own reactions happen before (they are in the code firing the event).
 */
public final class PartyEvents {
    private PartyEvents() {
    }

    /**
     * A party starts: its tokens are known and its first steps (the start rolls, the generator of the program) are in
     * its list, none started yet.
     */
    public static final Event<Started> STARTED = EventFactory.createArrayBacked(Started.class, listeners -> controller -> {
        for (Started listener : listeners) listener.onPartyStarted(controller);
    });

    /**
     * The steps of the party were generated from its program cards ({@link fr.lordfinn.steveparty.api.party.PartyCards}),
     * the end step last: a listener may add, remove or reorder steps in {@code steps} (the party's own list), after
     * {@code generatorIndex} (the step that generated them).
     */
    public static final Event<StepsGenerated> STEPS_GENERATED = EventFactory.createArrayBacked(StepsGenerated.class, listeners -> (controller, steps, generatorIndex) -> {
        for (StepsGenerated listener : listeners) listener.onStepsGenerated(controller, steps, generatorIndex);
    });

    /**
     * A step starts: it is the current step, at {@code index}, and its {@link PartyStep#start} is called right after
     * the listeners (a step may end within its start: its end and the next step's start then come before the
     * start returns).
     */
    public static final Event<StepStarted> STEP_STARTED = EventFactory.createArrayBacked(StepStarted.class, listeners -> (controller, step, index) -> {
        for (StepStarted listener : listeners) listener.onStepStarted(controller, step, index);
    });

    /** A step ended ({@link PartyStep#end} was called): the party moves on to another step. */
    public static final Event<StepEnded> STEP_ENDED = EventFactory.createArrayBacked(StepEnded.class, listeners -> (controller, step) -> {
        for (StepEnded listener : listeners) listener.onStepEnded(controller, step);
    });

    /** The party is over: its end step started, its tokens are released. */
    public static final Event<Ended> ENDED = EventFactory.createArrayBacked(Ended.class, listeners -> controller -> {
        for (Ended listener : listeners) listener.onPartyEnded(controller);
    });

    @FunctionalInterface
    public interface Started {
        void onPartyStarted(PartyControllerEntity controller);
    }

    @FunctionalInterface
    public interface StepsGenerated {
        void onStepsGenerated(PartyControllerEntity controller, List<PartyStep> steps, int generatorIndex);
    }

    @FunctionalInterface
    public interface StepStarted {
        void onStepStarted(PartyControllerEntity controller, PartyStep step, int index);
    }

    @FunctionalInterface
    public interface StepEnded {
        void onStepEnded(PartyControllerEntity controller, PartyStep step);
    }

    @FunctionalInterface
    public interface Ended {
        void onPartyEnded(PartyControllerEntity controller);
    }
}
