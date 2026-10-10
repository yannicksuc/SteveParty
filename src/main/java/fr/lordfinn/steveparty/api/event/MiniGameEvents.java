package fr.lordfinn.steveparty.api.event;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The mini-games of a party (server side): its mini-game step ({@link MiniGamePartyStep}) chooses a page of its
 * catalogue, sends the players to it, and reads the places on its podiums.
 */
public final class MiniGameEvents {
    private MiniGameEvents() {
    }

    /** The real round of a mini-game starts (after the countdown, or after the practice round): the players are sent out. */
    public static final Event<Started> STARTED = EventFactory.createArrayBacked(Started.class, listeners -> (controller, step) -> {
        for (Started listener : listeners) listener.onMiniGameStarted(controller, step);
    });

    /**
     * A mini-game of the party is over: {@code places} gives the place of each player (1 for the winners), its gains
     * are paid and its results shown. A practice round does not fire it.
     */
    public static final Event<Ended> ENDED = EventFactory.createArrayBacked(Ended.class, listeners -> (controller, step, places, winners) -> {
        for (Ended listener : listeners) listener.onMiniGameEnded(controller, step, places, winners);
    });

    @FunctionalInterface
    public interface Started {
        void onMiniGameStarted(PartyControllerEntity controller, MiniGamePartyStep step);
    }

    @FunctionalInterface
    public interface Ended {
        void onMiniGameEnded(PartyControllerEntity controller, MiniGamePartyStep step, Map<UUID, Integer> places, List<UUID> winners);
    }
}
