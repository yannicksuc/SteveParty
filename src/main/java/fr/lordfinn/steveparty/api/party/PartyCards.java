package fr.lordfinn.steveparty.api.party;

import fr.lordfinn.steveparty.api.StevePartyRegistries;
import fr.lordfinn.steveparty.items.custom.PartyCardItem;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The playing cards of the party program ({@link PartyCardItem}, put in the program slots of a Party Controller), and
 * what each puts in the party ({@link PartyCard}). Registry: {@link StevePartyRegistries#PARTY_CARDS}.
 * <p>
 * Steve Party's are {@code steveparty:turns}, {@code steveparty:minigame} and {@code steveparty:event}
 * ({@code steveparty:repeat} and {@code steveparty:sequence_start} shape the program and are not registered). A new
 * card, for an addon: its {@link PartyCard} registered here under the addon's id, and an item
 * {@code new PartyCardItem(id, settings)} registered in the vanilla item registry (its tooltip:
 * the item's translation key + {@code .tooltip}, {@code %s} being the size of the stack). The steps it adds are
 * often the addon's own kinds of steps ({@link PartySteps}).
 */
public final class PartyCards {
    private PartyCards() {
    }

    public static PartyCard register(Identifier id, PartyCard card) {
        return StevePartyRegistries.PARTY_CARDS.register(id, card);
    }

    public static @Nullable PartyCard get(@Nullable Identifier id) {
        return StevePartyRegistries.PARTY_CARDS.get(id);
    }
}
