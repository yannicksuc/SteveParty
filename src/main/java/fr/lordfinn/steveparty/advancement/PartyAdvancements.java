package fr.lordfinn.steveparty.advancement;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The advancement events of the party moments that need a little reading first (see {@link ModCriteria}). Server side. */
public final class PartyAdvancements {
    private PartyAdvancements() {}

    /** A party booted: {@code party_started} (value: its pawns) for each owner online. */
    public static void partyStarted(PartyControllerEntity party, ServerWorld world) {
        List<UUID> tokens = party.getPartyData().getTokens();
        Set<UUID> owners = new LinkedHashSet<>();
        for (UUID token : tokens) {
            if (world.getEntity(token) instanceof TokenizedEntityInterface pawn && pawn.steveparty$getTokenOwner() != null)
                owners.add(pawn.steveparty$getTokenOwner());
        }
        for (UUID owner : owners) ModCriteria.fire(world.getServer(), owner, "party_started", tokens.size(), null);
    }

    /**
     * A party ended normally: {@code party_finished} (value: rounds played) for each owner online; with 2 pawns or more,
     * {@code party_rank} (their best pawn's rank, stars then coins, ties shared) and {@code party_last} for the last.
     */
    public static void partyEnded(PartyControllerEntity party, ServerWorld world) {
        PartyData data = party.getPartyData();
        int rounds = data.getRoundAt(data.getStepIndex());
        List<PartyLiveData.Standing> standings = new ArrayList<>();
        for (UUID token : data.getTokens()) standings.add(PartyLiveData.standingOf(party, world, token));
        int[] ranks = PartyLiveData.ranks(standings);
        int worst = 0;
        Map<UUID, Integer> best = new LinkedHashMap<>();
        for (int i = 0; i < ranks.length; i++) {
            worst = Math.max(worst, ranks[i]);
            int rank = ranks[i];
            standings.get(i).owner().ifPresent(owner -> best.merge(owner, rank, Math::min));
        }
        MinecraftServer server = world.getServer();
        for (Map.Entry<UUID, Integer> entry : best.entrySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) continue;
            ModCriteria.fire(player, "party_finished", rounds);
            if (standings.size() < 2) continue;
            int rank = entry.getValue();
            ModCriteria.fire(player, "party_rank", rank);
            if (rank == worst && rank != 1) ModCriteria.fire(player, "party_last");
        }
    }

    /**
     * A pawn stopped on {@code space} at the end of its move: {@code landed_<role>} for its owner, and on a bad space
     * {@code landed_bad} (value: the bad spaces they landed on during the party).
     */
    public static void landed(MobEntity token, BoardSpaceBlockEntity space, ABoardSpaceBehavior behavior,
                              @Nullable PartyControllerEntity party) {
        if (!(token instanceof TokenizedEntityInterface pawn) || token.getServer() == null) return;
        ServerPlayerEntity owner = pawn.steveparty$getTokenOwner() == null ? null
                : token.getServer().getPlayerManager().getPlayer(pawn.steveparty$getTokenOwner());
        if (owner == null) return;
        ModCriteria.fire(owner, "landed_" + landingId(behavior.getTileType()));
        if (party != null && behavior.getStatus(space, space.getActiveCartridgeItemStack()) == ABoardSpaceBehavior.Status.BAD)
            ModCriteria.fire(owner, "landed_bad", party.getPartyData().addBadLanding(owner.getUuid()));
    }

    /** The id of a role in the {@code landed_<id>} events: its name without "tile_" / "board_space_". */
    public static String landingId(BoardSpaceType type) {
        if (type == BoardSpaceType.TILE_INVENTORY_INTERACTOR) return "inventory";
        String name = type.asString();
        if (name.startsWith("tile_")) return name.substring("tile_".length());
        if (name.startsWith("board_space_")) return name.substring("board_space_".length());
        return name;
    }

    /**
     * A throw is final ({@code faces}: the kept ones): {@code roll_<dice>} (value: the sum) for plain dice or
     * {@code roll_forged}, {@code roll_same} (value: dice), {@code roll_face_<kind>} per kind and {@code roll_over}
     * (entity: the creature the dice flew over), for the roller.
     */
    public static void rolled(DiceEntity lead, List<DiceFace> faces) {
        if (!(lead.getWorld() instanceof ServerWorld world) || faces.isEmpty()) return;
        ServerPlayerEntity player = lead.getOwner().map(world.getServer().getPlayerManager()::getPlayer).orElse(null);
        if (player == null) return;
        int sum = 0;
        boolean same = faces.size() >= 2;
        Set<DiceFacesComponent.Kind> kinds = EnumSet.noneOf(DiceFacesComponent.Kind.class);
        DiceFace first = faces.getFirst();
        for (DiceFace face : faces) {
            sum += face.value();
            same &= face.kind() == first.kind() && face.value() == first.value();
            kinds.add(face.kind());
        }
        // The dice of a throw all roll the lead's die
        if (DiceFacesComponent.hasFaces(lead.getDieStack())) ModCriteria.fire(player, "roll_forged", sum);
        else ModCriteria.fire(player, "roll_" + faces.size(), sum);
        if (same) ModCriteria.fire(player, "roll_same", faces.size());
        for (DiceFacesComponent.Kind kind : kinds) ModCriteria.fire(player, "roll_face_" + kind.asString());
        lead.getTarget().map(world::getEntity).ifPresent(target -> ModCriteria.fire(player, "roll_over", target));
    }

    /**
     * A real round of a party's mini-game is over: {@code minigame_round} (value: place, 0 none) for each player; in a
     * teams format, its winners get {@code minigame_team_win} (value: own team's size) and, alone in their team,
     * {@code minigame_solo_win} (value: opponents).
     */
    public static void miniGameRound(@Nullable MinecraftServer server, Map<UUID, Integer> places, @Nullable TeamDisposition teams) {
        if (server == null) return;
        boolean teamFormat = teams != null && !teams.isFreeForAll();
        places.forEach((uuid, place) -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) return;
            ModCriteria.fire(player, "minigame_round", place);
            int team = teamFormat ? teams.teamOf(uuid) : -1;
            if (place != 1 || team < 0) return;
            int size = teams.teams().get(team).size();
            ModCriteria.fire(player, "minigame_team_win", size);
            if (size == 1) ModCriteria.fire(player, "minigame_solo_win", teams.size() - 1);
        });
    }
}
