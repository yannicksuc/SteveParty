package fr.lordfinn.steveparty.events;

import fr.lordfinn.steveparty.dice.DiceThrow;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Server side, once per throw: its dice were all revealed, one after the other, and the total shown
 * ({@link fr.lordfinn.steveparty.dice.DiceRollSequence}). Fired just before {@link DiceRollEvent} (which moves the
 * roller's token): what to do on a double or a triple ({@link DiceThrow#isDouble}, {@link DiceThrow#isTriple}) plugs
 * in here. A single die fires it too.
 */
public interface DiceThrowRevealed {
    Event<DiceThrowRevealed> EVENT = EventFactory.createArrayBacked(DiceThrowRevealed.class, listeners -> (lead, roller, diceThrow) -> {
        for (DiceThrowRevealed listener : listeners) listener.onRevealed(lead, roller, diceThrow);
    });

    /**
     * @param lead      the die that ran the throw (the first of a Double / Triple Dice)
     * @param roller    the player who threw it, null for a die without an owner
     * @param diceThrow its faces, in the order they stopped, and what it does
     */
    void onRevealed(DiceEntity lead, @Nullable UUID roller, DiceThrow diceThrow);
}
