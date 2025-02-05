package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.items.custom.teleportation_books.TeleportingTarget;
import fr.lordfinn.steveparty.persistent_state.TeleportationPadBooksStorage;
import fr.lordfinn.steveparty.persistent_state.TeleportationPadStorageManager;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static fr.lordfinn.steveparty.components.ModComponents.*;
import static fr.lordfinn.steveparty.utils.SoundsUtils.playSoundToPlayers;

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

        // Step 1: Ensure the world is a ServerWorld
        if (!(partyControllerEntity.getWorld() instanceof ServerWorld serverWorld)) {
            return;  // Exit early if not a ServerWorld
        }

        // Step 2: Send a message to interested players that the mini-game is starting
        sendStartMessage(partyControllerEntity);

        // Step 3: Check if there are mini-games to play, exit early if none
        List<ItemStack> miniGames = partyControllerEntity.getMiniGames();
        if (miniGames.isEmpty()) {
            partyControllerEntity.nextStep();
            return;
        }

        // Step 4: Get the tokens with their owners and determine their statuses
        Map<TokenizedEntityInterface, PlayerEntity> tokensWithOwners = getTokensWithOwners(partyControllerEntity, serverWorld);
        List<ABoardSpaceBehavior.Status> statuses = getTokenStatuses(serverWorld, tokensWithOwners);

        // Step 5: Assign mini-games to team dispositions
        Map<TeamDisposition, List<ItemStack>> miniGamesToTeamDispositions = assignMiniGamesToTeamDispositions(tokensWithOwners, statuses, miniGames, serverWorld);

        // Step 6: Choose a random disposition for the mini-games
        TeamDisposition chosenDisposition = chooseRandomDisposition(miniGamesToTeamDispositions);
        MiniGamesCatalogueItem.setCurrentMiniGameTeamDisposition(partyControllerEntity.catalogue, chosenDisposition);

        // Step 7: Notify players about the chosen disposition
        notifyPlayersAboutChosenDisposition(partyControllerEntity, chosenDisposition, serverWorld.getServer());

        // Step 8: Shuffle and prepare mini-games for the chosen disposition
        List<ItemStack> applicableMiniGames = miniGamesToTeamDispositions.get(chosenDisposition);
        Collections.shuffle(applicableMiniGames);

        // Step 9: Start the iteration effect through the mini-games
        AtomicInteger iterations = new AtomicInteger(0);
        List<ServerPlayerEntity> players = partyControllerEntity.getInterestedPlayersEntities();
        playIterationEffect(iterations, applicableMiniGames, partyControllerEntity, players);
    }

    // Helper Methods
    private void sendStartMessage(PartyControllerEntity partyControllerEntity) {
        MessageUtils.sendToPlayers(partyControllerEntity.getInterestedPlayersEntities(),
                Text.translatable("message.steveparty.minigame_start")
                        .setStyle(Style.EMPTY.withColor(0xFFA500)),
                MessageUtils.MessageType.CHAT);
    }

    private Map<TokenizedEntityInterface, PlayerEntity> getTokensWithOwners(PartyControllerEntity partyControllerEntity, ServerWorld serverWorld) {
        return partyControllerEntity.getPartyData().getTokensWithOwners(serverWorld);
    }

    private TeamDisposition chooseRandomDisposition(Map<TeamDisposition, List<ItemStack>> miniGamesToTeamDispositions) {
        List<TeamDisposition> teamDispositions = new ArrayList<>(miniGamesToTeamDispositions.keySet());
        Random random = new Random();
        return teamDispositions.get(random.nextInt(teamDispositions.size()));
    }

    private void notifyPlayersAboutChosenDisposition(PartyControllerEntity partyControllerEntity, TeamDisposition chosenDisposition, MinecraftServer server) {
        MessageUtils.sendToPlayers(partyControllerEntity.getInterestedPlayersEntities(),
                Text.translatable("message.steveparty.chosen_disposition", chosenDisposition.toText(server)),
                MessageUtils.MessageType.CHAT);
    }

    private void playIterationEffect(AtomicInteger iterations, List<ItemStack> applicableMiniGames, PartyControllerEntity partyControllerEntity, List<ServerPlayerEntity> players) {
        int currentIteration = iterations.incrementAndGet();

        ItemStack chosenMiniGame = applicableMiniGames.get(currentIteration % applicableMiniGames.size());

        MessageUtils.sendToPlayers(
                players,
                formatMiniGameMessage(chosenMiniGame, false),
                MessageUtils.MessageType.ACTION_BAR
        );
        if (currentIteration >= 12 + (new Random()).nextInt(8)) {
            finalizeMiniGameSelection(iterations, applicableMiniGames, partyControllerEntity, players);
        } else {
            playSoundToPlayers(players, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 0.4f, 1);
            Steveparty.SCHEDULER.schedule(
                    UUID.randomUUID(),
                    currentIteration,
                    () -> playIterationEffect(iterations, applicableMiniGames, partyControllerEntity, players)
            );
        }
    }

    private void finalizeMiniGameSelection(AtomicInteger iterations, List<ItemStack> applicableMiniGames, PartyControllerEntity partyControllerEntity, List<ServerPlayerEntity> players) {
        int currentIteration = iterations.get();
        ItemStack chosenMiniGame = applicableMiniGames.get(currentIteration % applicableMiniGames.size());

        MessageUtils.sendToPlayers(
                players,
                formatMiniGameMessage(chosenMiniGame, true),
                MessageUtils.MessageType.TITLE
        );

        // Store the final selection
        MiniGamesCatalogueItem.setCurrentMiniGamePage(partyControllerEntity.catalogue, chosenMiniGame);

        // Play a celebratory sound for selection
        playSoundToPlayers(players, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1f, 1);
    }

        private Text formatMiniGameMessage(ItemStack chosenMiniGame, boolean isFinal) {
        int whiteColor = isFinal ? 0xFFA500 : 0xFFFFFF; // Make the final text gold/orange

        return Text.literal("🎲 ")
                .styled(style -> style.withColor(0xFFA500)) // Orange start
                .append(chosenMiniGame.getName().copy()
                        .styled(style -> style.withColor(whiteColor).withBold(isFinal))) // White or Orange if final
                .append(Text.literal(" 🎲")
                        .styled(style -> style.withColor(0xFFA500))); // Orange end
    }

    private static void logMinigamesDispositions(Map<TeamDisposition, List<ItemStack>> miniGamesToTeamDispositions) {
        for (Map.Entry<TeamDisposition, List<ItemStack>> entry : miniGamesToTeamDispositions.entrySet()) {
            TeamDisposition disposition = entry.getKey();
            List<ItemStack> applicableMiniGames = entry.getValue();
            Steveparty.LOGGER.info("Disposition: {}", disposition);
            Steveparty.LOGGER.info("Applicable mini-games: {}",
                    applicableMiniGames.stream()
                            .map(item -> item.getName().toString())
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

    public static Map<TeamDisposition, List<ItemStack>> assignMiniGamesToTeamDispositions(
            Map<TokenizedEntityInterface, PlayerEntity> tokensWithOwners,
            List<ABoardSpaceBehavior.Status> statuses,
            List<ItemStack> miniGames, ServerWorld world) {

        Set<TeamDisposition> teamDispositions = TeamDispositionGenerator.generateTeamDispositions(tokensWithOwners, statuses);
        Map<TeamDisposition, List<ItemStack>> teamDispositionsToMiniGames = new HashMap<>();

        for (TeamDisposition disposition : teamDispositions) {
            List<ItemStack> applicableMiniGames = new ArrayList<>();

            for (ItemStack miniGamePageStack : miniGames) {
                // Extract teleporting targets from the mini-game stack
                TeleportationPadBooksStorage storage = TeleportationPadStorageManager.getBooksStorage(world);
                List<TeleportingTarget> teleportingTargets = new ArrayList<>();
                miniGamePageStack.getOrDefault(DESTINATIONS_COMPONENT, DestinationsComponent.DEFAULT).destinations()
                        .forEach(pos -> {
                            ItemStack book = storage.getTeleportationPadBook(pos);
                            if (!book.isEmpty()) {
                                List<TeleportingTarget> targets = book.getOrDefault(TP_TARGETS, List.of());
                                teleportingTargets.addAll(targets);
                            }
                        });
                // Check if the mini-game fits the team disposition
                if (doesMiniGameFitTeamDisposition(disposition, teleportingTargets)) {
                    applicableMiniGames.add(miniGamePageStack);
                }
            }

            if (!applicableMiniGames.isEmpty()) {
                teamDispositionsToMiniGames.put(disposition, applicableMiniGames);
            }
        }

        return teamDispositionsToMiniGames;
    }

    // Check if the mini-game can accept the current team disposition based on teleporting targets
    private static boolean doesMiniGameFitTeamDisposition(TeamDisposition disposition, List<TeleportingTarget> teleportingTargets) {
        // Track how many players from each group (A or B) are available to fill the teleportation targets
        int teamAPlayersCount = disposition.teamA.size();
        int teamBPlayersCount = disposition.teamB.size();

        int teamACapacity = 0;
        int teamBCapacity = 0;
        int teamBothCapacity = 0;

        // Iterate through all teleporting targets
        for (TeleportingTarget target : teleportingTargets) {
            TeleportingTarget.Group targetGroup = target.getGroup();
            int fillCapacity = target.getCheckedFillCapacity(); // Required number of players for this group

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
