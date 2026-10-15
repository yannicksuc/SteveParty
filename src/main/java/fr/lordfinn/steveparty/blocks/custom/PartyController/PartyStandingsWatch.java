package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.blocks.custom.PartyBellBlockEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Watches a party's coins and Party Stars for its antennas (moments {@link PartyMoment#COINS_WON},
 * {@link PartyMoment#COINS_LOST}, {@link PartyMoment#STAR_WON}, {@link PartyMoment#NEW_LEADER}). They are items in the
 * players' inventories, changed from many places (dice faces, spaces, shops, mini-games, power-ups...): their counts
 * are compared every {@link #INTERVAL_TICKS}, only while an antenna listens to one of these moments.
 */
final class PartyStandingsWatch {
    static final int INTERVAL_TICKS = 5;
    private static final List<PartyMoment> MOMENTS = List.of(PartyMoment.COINS_WON, PartyMoment.COINS_LOST,
            PartyMoment.STAR_WON, PartyMoment.NEW_LEADER);

    private final PartyControllerEntity controller;
    /** Stars and coins of each token at the last look; empty: not looked yet (the first look tells nothing). */
    private final Map<UUID, int[]> last = new HashMap<>();
    private UUID leader;

    PartyStandingsWatch(PartyControllerEntity controller) {
        this.controller = controller;
    }

    void tick(ServerWorld world) {
        if (world.getTime() % INTERVAL_TICKS == 0) look(world);
    }

    /** Compares the coins and stars with the last look, and rings what changed. */
    void look(ServerWorld world) {
        PartyData data = controller.getPartyData();
        if (!data.isStarted() || !PartyBellBlockEntity.isListened(controller, MOMENTS)) {
            last.clear();
            leader = null;
            return;
        }
        List<PartyLiveData.Standing> standings = new ArrayList<>();
        for (UUID token : data.getTokens()) standings.add(PartyLiveData.standingOf(controller, world, token));
        boolean first = last.isEmpty();
        for (PartyLiveData.Standing standing : standings) {
            int[] before = last.put(standing.token(), new int[]{standing.stars(), standing.coins()});
            if (first || before == null) continue;
            int coins = standing.coins() - before[1];
            if (coins > 0) controller.ringMoment(PartyMoment.COINS_WON, coins);
            else if (coins < 0) controller.ringMoment(PartyMoment.COINS_LOST, -coins);
            if (standing.stars() > before[0]) controller.ringMoment(PartyMoment.STAR_WON, standing.stars());
        }
        // The leader: the only one ranked first (a tie leads nobody)
        int[] ranks = PartyLiveData.ranks(standings);
        UUID now = null;
        for (int i = 0; i < ranks.length; i++) {
            if (ranks[i] != 1) continue;
            if (now != null) {
                now = null;
                break;
            }
            now = standings.get(i).token();
        }
        if (!first && now != null && !now.equals(leader)) controller.ringMoment(PartyMoment.NEW_LEADER, data.getTokenRank(now));
        if (now != null || first) leader = now;
    }
}
