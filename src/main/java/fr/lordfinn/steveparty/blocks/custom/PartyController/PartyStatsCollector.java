package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.api.event.MiniGameEvents;
import fr.lordfinn.steveparty.api.event.PartyEvents;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.events.TileReachedEvent;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fills the {@link PartyStats} of the running parties: the coins and stars at the party's start, at each round's start,
 * after each mini-game and at its end; the spaces each pawn walks, where it stops, its dice rolls, the power-ups its
 * player uses and its places in the mini-games. Collected only: nothing shows them yet.
 */
public final class PartyStatsCollector {
    private PartyStatsCollector() {
    }

    public static void initialize() {
        PartyEvents.STARTED.register(controller -> point(controller, PartyStats.Kind.START));
        PartyEvents.STEP_STARTED.register((controller, step, index) -> {
            if (controller.getPartyData().isRoundStart(index)) point(controller, PartyStats.Kind.ROUND);
        });
        PartyEvents.ENDED.register(controller -> point(controller, PartyStats.Kind.END));
        MiniGameEvents.ENDED.register(PartyStatsCollector::miniGameEnded);
        TileReachedEvent.EVENT.register((token, tile) -> {
            PartyControllerEntity party = partyOf(token);
            if (party != null) party.getPartyData().getStats().walked(token.getUuid());
            return ActionResult.PASS;
        });
    }

    private static @Nullable PartyControllerEntity partyOf(MobEntity token) {
        if (token.getWorld().isClient) return null;
        return PartyControllerEntity.getRunningPartyOf(token.getUuid()).orElse(null);
    }

    /** A new point of the timeline, with every pawn's coins and stars (a player away keeps his last ones). */
    static void point(PartyControllerEntity controller, PartyStats.Kind kind) {
        if (!(controller.getWorld() instanceof ServerWorld world)) return;
        PartyData data = controller.getPartyData();
        PartyStats stats = data.getStats();
        Map<UUID, int[]> values = new HashMap<>();
        for (UUID token : data.getTokens()) {
            PartyLiveData.Standing standing = PartyLiveData.standingOf(controller, world, token);
            stats.name(token, standing.owner().orElse(null), standing.ownerName().isEmpty() ? standing.tokenName() : standing.ownerName());
            if (standing.online()) values.put(token, new int[]{standing.coins(), standing.stars()});
        }
        int round = Math.max(0, data.getRoundAt(Math.max(0, data.getStepIndex())));
        stats.point(new PartyStats.Point(kind, kind == PartyStats.Kind.START ? 0 : round), values);
    }

    /** A pawn of the party stopped on {@code space} (its role counted). */
    public static void landed(@Nullable PartyControllerEntity party, MobEntity token, BoardSpaceBlockEntity space) {
        if (party == null || !party.getPartyData().getTokens().contains(token.getUuid())) return;
        ItemStack cartridge = space.getActiveCartridgeItemStack();
        String role = cartridge.getItem() instanceof CartridgeItem item ? item.getBoardSpaceRole().toString()
                : space.getBoardSpaceBehavior().getTileType().id().toString();
        party.getPartyData().getStats().landed(token.getUuid(), role);
    }

    /** The die of {@code token} gave {@code value}. */
    public static void rolled(PartyControllerEntity party, UUID token, int value) {
        if (party.getPartyData().getTokens().contains(token)) party.getPartyData().getStats().rolled(token, value);
    }

    /** The player of {@code token} used {@code item} (a power-up) during its turn. */
    public static void used(PartyControllerEntity party, UUID token, ItemStack item) {
        if (party.getPartyData().getTokens().contains(token))
            party.getPartyData().getStats().used(token, Registries.ITEM.getId(item.getItem()).toString());
    }

    /** {@code places}: the place of each player (1 for the winners); his pawns get it. */
    private static void miniGameEnded(PartyControllerEntity controller, Object step, Map<UUID, Integer> places, List<UUID> winners) {
        if (!(controller.getWorld() instanceof ServerWorld)) return;
        PartyStats stats = controller.getPartyData().getStats();
        int last = places.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        int first = places.values().stream().mapToInt(Integer::intValue).min().orElse(1);
        for (UUID token : controller.getPartyData().getTokens()) {
            UUID owner = controller.getTokenOwner(token);
            Integer place = owner == null ? null : places.get(owner);
            if (place == null) continue;
            stats.miniGame(token, place, winners.contains(owner) || place == 1, last > first && place == last);
        }
        point(controller, PartyStats.Kind.MINI_GAME);
    }
}
