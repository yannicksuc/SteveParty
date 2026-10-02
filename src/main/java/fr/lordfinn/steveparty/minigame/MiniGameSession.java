package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A mini-game being played right now on a page: by a party (its mini-game step, practice round included:
 * {@link PartyMiniGameSession}) or out of any party, from its controller or the page's editor ({@link MiniGameTest}). The blocks linked to a page (podiums, goal pole bases,
 * step controllers) only know the page: they ask {@link #playing} who plays it, and work the same for both.
 */
public interface MiniGameSession {
    /** The players of the mini-game (not those who watch), in their order. */
    Collection<UUID> participants();

    default boolean isParticipant(UUID player) {
        return participants().contains(player);
    }

    /** Its teams, null when everyone plays for themselves. */
    @Nullable TeamDisposition teams();

    /** The team of a player (0: A ... 3: D), -1 without teams. */
    default int teamOf(UUID player) {
        TeamDisposition teams = teams();
        return teams == null ? -1 : teams.teamOf(player);
    }

    /** Those who are told what happens in it (the places taken on its podiums...). */
    List<ServerPlayerEntity> audience();

    /** Someone took or left a podium of the mini-game: it ends when every place is taken or everyone has one. */
    void onPodiumsChanged();

    /**
     * A step controller linked to the page received a pulse.
     *
     * @param mode 0: next (the mini-game ends with its results, read on the podiums as they stand); 1 (restart) and 2
     *             (previous): it stops without results
     */
    void step(int mode);

    /** The mini-game being played right now on one of {@code pages}, in any dimension and at any distance; null for none. */
    static @Nullable MiniGameSession playing(Collection<UUID> pages) {
        if (pages.isEmpty()) return null;
        Optional<PartyControllerEntity> party = PartyControllerEntity.getPartyPlayingPage(pages);
        if (party.isPresent() && party.get().getPartyData().getCurrentStep() instanceof MiniGamePartyStep miniGame)
            return new PartyMiniGameSession(party.get(), miniGame);
        return MiniGameTest.playing(pages);
    }
}
