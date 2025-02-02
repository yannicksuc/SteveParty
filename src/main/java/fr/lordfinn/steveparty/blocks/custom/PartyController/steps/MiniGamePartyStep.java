package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.custom.teleportation_books.TeleportingTarget;
import fr.lordfinn.steveparty.persistent_state.TeleportationPadStorage;
import fr.lordfinn.steveparty.persistent_state.TeleportationPadStorageManager;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;

import java.util.*;

import static fr.lordfinn.steveparty.components.ModComponents.DESTINATIONS_COMPONENT;
import static fr.lordfinn.steveparty.components.ModComponents.TP_TARGETS;

public class MiniGamePartyStep extends PartyStep {
    private List<UUID> tokens = new ArrayList<>();

    public MiniGamePartyStep(List<UUID> tokens) {
        if (tokens == null)
            tokens = new ArrayList<>();
        this.tokens = tokens;
        setType(PartyStepType.MINI_GAME);
    }

    public MiniGamePartyStep(NbtCompound nbt) {
        super(nbt);
    }

    @Override
    public void start(PartyControllerEntity partyControllerEntity) {
        super.start(partyControllerEntity);
        if (!(partyControllerEntity.getWorld() instanceof ServerWorld serverWorld)) return;
        List<ItemStack> miniGames = partyControllerEntity.getMiniGames();
        if (miniGames.isEmpty()) {
            partyControllerEntity.nextStep();
            return;
        }
        Map<TokenizedEntityInterface, PlayerEntity> tokensWithOwners = partyControllerEntity.getPartyData().getTokensWithOwners(serverWorld);
        List<ABoardSpaceBehavior.Status> statuses = getTokenStatuses(serverWorld, tokensWithOwners);
        Map<PossibleTeamDisposition, List<ItemStack>> miniGamesToTeamDispositions = assignMiniGamesToTeamDispositions(tokensWithOwners, statuses, miniGames, serverWorld);
        for (Map.Entry<PossibleTeamDisposition, List<ItemStack>> entry : miniGamesToTeamDispositions.entrySet()) {
            PossibleTeamDisposition disposition = entry.getKey();
            List<ItemStack> applicableMiniGames = entry.getValue();
            Steveparty.LOGGER.info("Disposition: {}", disposition);
            Steveparty.LOGGER.info("Applicable mini-games: {}",
                    applicableMiniGames.stream()
                            .map(item -> item.getName().toString())  // Convert ItemStacks to their names
                            .reduce("", (result, name) -> result + " | " + name));
        }
    }

    private static List<ABoardSpaceBehavior.Status> getTokenStatuses(ServerWorld serverWorld, Map<TokenizedEntityInterface, PlayerEntity> tokensWithOwners) {
        List<ABoardSpaceBehavior.Status> statuses = new ArrayList<>();
        tokensWithOwners.keySet().forEach(token -> {
            ABoardSpaceBehavior.Status status = ABoardSpaceBehavior.Status.NEUTRAL;
            MobEntity modToken = ((MobEntity) token);
            BoardSpaceBlockEntity boardSpaceEntity = TileBlock.getBoardSpaceEntity(serverWorld, modToken.getBlockPos());
            ItemStack stack;
            if (boardSpaceEntity != null) {
                stack = boardSpaceEntity.getActiveCartridgeItemStack();
                ABoardSpaceBehavior behavior = boardSpaceEntity.getBoardSpaceBehavior(stack);
                if (behavior != null) {
                    status = behavior.getStatus(boardSpaceEntity, stack);
                }
            }
            statuses.add(status);
        });
        return statuses;
    }

    public static Map<PossibleTeamDisposition, List<ItemStack>> assignMiniGamesToTeamDispositions(
            Map<TokenizedEntityInterface, PlayerEntity> tokensWithOwners,
            List<ABoardSpaceBehavior.Status> statuses,
            List<ItemStack> miniGames, ServerWorld world) {

        Set<PossibleTeamDisposition> teamDispositions = TeamDispositionGenerator.generateTeamDispositions(tokensWithOwners, statuses);
        Map<PossibleTeamDisposition, List<ItemStack>> teamDispositionsToMiniGames = new HashMap<>();

        for (PossibleTeamDisposition disposition : teamDispositions) {
            List<ItemStack> applicableMiniGames = new ArrayList<>();

            for (ItemStack miniGameStack : miniGames) {
                // Extract teleporting targets from the mini-game stack
                TeleportationPadStorage storage = TeleportationPadStorageManager.getStorage(world);
                List<TeleportingTarget> teleportingTargets = new ArrayList<>();
                miniGameStack.getOrDefault(DESTINATIONS_COMPONENT, DestinationsComponent.DEFAULT).destinations()
                        .forEach(pos -> {
                            ItemStack book = storage.getTeleportationPadBook(pos);
                            if (!book.isEmpty()) {
                                List<TeleportingTarget> targets = book.getOrDefault(TP_TARGETS, List.of());
                                teleportingTargets.addAll(targets);
                            }
                        });
                // Check if the mini-game fits the team disposition
                if (doesMiniGameFitTeamDisposition(disposition, teleportingTargets)) {
                    applicableMiniGames.add(miniGameStack);
                }
            }

            if (!applicableMiniGames.isEmpty()) {
                teamDispositionsToMiniGames.put(disposition, applicableMiniGames);
            }
        }

        return teamDispositionsToMiniGames;
    }

    // Check if the mini-game can accept the current team disposition based on teleporting targets
    private static boolean doesMiniGameFitTeamDisposition(PossibleTeamDisposition disposition, List<TeleportingTarget> teleportingTargets) {
        // Track how many players from each group (A or B) are available to fill the teleportation targets
        int teamAPlayersCount = disposition.teamA.size();
        int teamBPlayersCount = disposition.teamB.size();

        int teamACapacity = 0;
        int teamBCapacity = 0;
        int teamBothCapacity = 0;

        // Iterate through all teleporting targets
        for (TeleportingTarget target : teleportingTargets) {
            TeleportingTarget.Group targetGroup = target.group;
            int fillCapacity = target.fillCapacity; // Required number of players for this group
            if (fillCapacity == 0)
                fillCapacity = 999999999;

            // Check each type of group
            switch (targetGroup) {
                case PLAYER_TEAM_A:
                    teamACapacity += fillCapacity;
                    break;
                case PLAYER_TEAM_B:
                    teamBCapacity += fillCapacity;
                    break;
                case EVERYONE:
                case PLAYERS:
                    teamBothCapacity += fillCapacity;
                    break;
                default:
                    break;
            }
        }

        // Swap them because team A in the disposition is always smaller
        if (teamACapacity > teamBCapacity) {
            int temp = teamACapacity;
            teamACapacity = teamBCapacity;
            teamBCapacity = temp;
        }

        teamAPlayersCount -= teamACapacity;
        teamBPlayersCount -= teamBCapacity;

        int playersRest = (teamAPlayersCount + teamBPlayersCount) - teamBothCapacity;

        return playersRest <= 0;
    }


    @Override
    public void fromNbt(NbtCompound nbt) {
        super.fromNbt(nbt);
        if (nbt.contains("Tokens")) {
            if (tokens == null)
                tokens = new ArrayList<>();
            nbt.getList("Tokens", 8).forEach(token -> {
                String uuidStr = token.asString();
                UUID uuid = UUID.fromString(uuidStr);
                this.tokens.add(uuid);
            });
        }
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound nbtCompound = super.toNbt();
        NbtList tokensNbtList = new NbtList();
        for (UUID uuid : tokens) {
            tokensNbtList.add(NbtString.of(uuid.toString()));
        }
        if (!tokens.isEmpty())
            nbtCompound.put("Tokens", tokensNbtList);
        return nbtCompound;
    }
}
