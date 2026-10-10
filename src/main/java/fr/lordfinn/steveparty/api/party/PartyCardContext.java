package fr.lordfinn.steveparty.api.party;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import net.minecraft.server.world.ServerWorld;

import java.util.List;
import java.util.UUID;

/**
 * Where a party card adds its steps: the party being generated, in the world of its Party Controller.
 *
 * @param world the world of the Party Controller
 * @param party the party: its tokens, its steps so far
 */
public record PartyCardContext(ServerWorld world, PartyData party) {
    /** The tokens of the party, in the play order. */
    public List<UUID> tokens() {
        return party.getTokens();
    }

    /** Adds a step at the end of the party (so far). */
    public void addStep(PartyStep step) {
        party.addStep(step);
    }
}
