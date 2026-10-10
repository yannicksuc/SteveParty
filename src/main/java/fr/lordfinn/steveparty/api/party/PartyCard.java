package fr.lordfinn.steveparty.api.party;

/**
 * What a playing card of the party program puts in the party: its steps. The program cards are read left to right,
 * top to bottom, once the "repeat" and "sequence start" cards are expanded (those two shape the program and play
 * nothing themselves); each playing card then adds its steps at the end of the party, the end step coming last.
 * Registered in {@link PartyCards}.
 */
@FunctionalInterface
public interface PartyCard {
    /**
     * Adds the steps of one card to the party.
     *
     * @param count the size of its stack (its number: an event card's channel...)
     */
    void addSteps(PartyCardContext context, int count);
}
