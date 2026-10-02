package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** The mini-game step of a party, as the blocks linked to its page see it. */
public record PartyMiniGameSession(PartyControllerEntity controller, MiniGamePartyStep miniGame) implements MiniGameSession {
    @Override
    public Collection<UUID> participants() {
        return miniGame.getParticipants();
    }

    @Override
    public @Nullable TeamDisposition teams() {
        TeamDisposition teams = MiniGamesCatalogueItem.getCurrentMiniGameTeamDisposition(controller.getCatalogue());
        return teams == null || teams.isFreeForAll() ? null : teams;
    }

    @Override
    public List<ServerPlayerEntity> audience() {
        return controller.getPartyAudience();
    }

    @Override
    public void onPodiumsChanged() {
        miniGame.onPodiumsChanged(controller);
    }

    @Override
    public void step(int mode) {
        switch (mode) {
            case 0 -> controller.nextStep();
            case 1 -> controller.restartStep();
            case 2 -> controller.previousStep();
            default -> {
            }
        }
    }
}
