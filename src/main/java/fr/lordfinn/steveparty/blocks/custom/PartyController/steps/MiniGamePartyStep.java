package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.minigame.MiniGameMode;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeLink;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static fr.lordfinn.steveparty.components.ModComponents.*;
import static fr.lordfinn.steveparty.utils.SoundsUtils.playSoundToPlayers;

/**
 * A mini-game: teams from the board spaces the tokens stand on (positive: team A, negative: team B, see
 * {@link TeamDispositionGenerator}), a page of the catalogue that can be played by them drawn by a roulette, then:
 * <ol>
 *     <li>the "mini-game chosen" party bells ring (a waiting bell pauses here),</li>
 *     <li>a 3 second countdown, and the players come out of the pipes linked to the page, by team: one after the
 *     other in each pipe of their role in turn, in turn order ({@link MiniGamePipes#distribute}); the audience
 *     comes out of the spectators pipes,</li>
 *     <li>the mini-game is played until a podium names the winners (or a step controller goes on),</li>
 *     <li>the winners are announced, and the players are brought back where they were.</li>
 * </ol>
 */
public class MiniGamePartyStep extends PartyStep {
    private static final int COUNTDOWN_SECONDS = 3;
    private static final int RETURN_DELAY_TICKS = 60;

    public enum Phase { ROULETTE, CHOSEN_WAIT, COUNTDOWN, PLAYING, FINISHED }

    /** Ticks between two players coming out of the same pipe. */
    private static final int EMERGE_GAP_TICKS = 8;

    /** Where a player sent to the mini-game stood before it. */
    private record ReturnPos(RegistryKey<World> dimension, double x, double y, double z, float yaw, float pitch) {}

    // No initializer: it would run after super(nbt) and wipe what fromNbt just read
    private List<UUID> tokens;
    private boolean miniGameChosen; // no initializer, see above
    private Phase phase;
    /** Players taking part (owners of the tokens), in turn order. */
    private List<UUID> participants;
    private Map<UUID, ReturnPos> returnPositions;
    private List<UUID> winners;
    private UUID rouletteTaskId = null;
    private UUID flowTaskId = null;
    /** The players still waiting for their turn to come out of a pipe. */
    private final List<UUID> emergeTasks = new ArrayList<>();
    /** The catalogue slot of the page the roulette chose, plus one (0: none yet). No initializer, see above. */
    private int chosenPageSlot;

    public MiniGamePartyStep(List<UUID> tokens) {
        if (tokens == null)
            tokens = new ArrayList<>();
        this.tokens = tokens;
        initCollections();
        setType(PartyStepType.MINI_GAME);
    }

    public MiniGamePartyStep(NbtCompound nbt) {
        super(nbt);
        if (this.tokens == null)
            this.tokens = new ArrayList<>();
        initCollections();
    }

    private void initCollections() {
        if (phase == null) phase = Phase.ROULETTE;
        if (participants == null) participants = new ArrayList<>();
        if (returnPositions == null) returnPositions = new LinkedHashMap<>();
        if (winners == null) winners = new ArrayList<>();
    }

    public Phase getPhase() {
        return phase;
    }

    public List<UUID> getWinners() {
        return Collections.unmodifiableList(winners);
    }

    public List<UUID> getParticipants() {
        return Collections.unmodifiableList(participants);
    }

    /** @return true while the mini-game is being played: a podium may end it. */
    public boolean isPlaying() {
        return status == Status.IN_PROGRESS && phase == Phase.PLAYING;
    }

    @Override
    public boolean isWaitingForBell() {
        return status == Status.IN_PROGRESS && phase == Phase.CHOSEN_WAIT;
    }

    @Override
    public void start(PartyControllerEntity partyControllerEntity) {
        super.start(partyControllerEntity);
        cancelRoulette();
        cancelFlow();
        miniGameChosen = false;
        phase = Phase.ROULETTE;
        participants.clear();
        winners.clear();
        // Players still away from a previous run of this step (restart) keep their original return position
        chosenPageSlot = 0;

        // Step 1: Ensure the world is a ServerWorld
        if (!(partyControllerEntity.getWorld() instanceof ServerWorld serverWorld)) {
            return;  // Exit early if not a ServerWorld
        }

        // Step 2: Send a message to interested players that the mini-game is starting
        sendStartMessage(partyControllerEntity);

        // Step 3: Check if there are mini-games to play, exit early if none
        hidePreview(partyControllerEntity);
        // The pages show the title they have now
        MiniGamesCatalogueItem.refreshPages(serverWorld.getServer(), partyControllerEntity.catalogue);
        List<ItemStack> miniGames = partyControllerEntity.getMiniGames();
        if (miniGames.isEmpty()) {
            partyControllerEntity.nextStep();
            return;
        }

        // Step 4: The players in turn order, with the kind of tile their token stands on
        List<TeamDispositionGenerator.Seat> seats = seats(partyControllerEntity, serverWorld);
        seats.forEach(seat -> {
            if (!participants.contains(seat.player())) participants.add(seat.player());
        });

        // Step 5: The ways to make teams, each with the mini-games that can be played that way
        Map<TeamDisposition, List<ItemStack>> miniGamesToTeamDispositions = assignMiniGamesToTeamDispositions(seats, miniGames, serverWorld.getServer());
        if (miniGamesToTeamDispositions.isEmpty()) {
            MessageUtils.sendToPlayers(partyControllerEntity.getInterestedPlayersEntities(),
                    Text.translatableWithFallback("message.steveparty.no_compatible_minigame",
                            "No mini-game of the catalogue fits the current players, the mini-game is skipped."),
                    MessageUtils.MessageType.CHAT);
            partyControllerEntity.nextStep();
            return;
        }

        // Step 6: Choose a random disposition for the mini-games
        TeamDisposition chosenDisposition = chooseRandomDisposition(miniGamesToTeamDispositions);
        MiniGamesCatalogueItem.setCurrentMiniGameTeamDisposition(partyControllerEntity.catalogue, chosenDisposition);
        partyControllerEntity.markDirty();

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

    @Override
    public void resume(PartyControllerEntity partyControllerEntity) {
        // The roulette was interrupted before a mini-game was chosen: run the selection again.
        // If a mini-game was already chosen it is kept as is.
        if (!miniGameChosen) {
            start(partyControllerEntity);
            return;
        }
        switch (phase) {
            case COUNTDOWN -> startCountdown(partyControllerEntity);
            case FINISHED -> scheduleReturn(partyControllerEntity);
            default -> {
                // ROULETTE (chosen, but saved before the bells rang): ring them now. CHOSEN_WAIT / PLAYING: wait.
                if (phase == Phase.ROULETTE) onMiniGameChosen(partyControllerEntity);
                // Those away in the mini-game are known again as such (free pipes, the exit pipe)
                if (phase == Phase.PLAYING) returnPositions.keySet().forEach(uuid -> seat(partyControllerEntity, uuid));
            }
        }
    }

    @Override
    public void end(PartyControllerEntity partyControllerEntity) {
        super.end(partyControllerEntity);
        cancelRoulette();
        cancelFlow();
        emergeTasks.forEach(Steveparty.SCHEDULER::cancel);
        emergeTasks.clear();
        hidePreview(partyControllerEntity);
        // However the mini-game ends (podium, step controller...), the players go back where they were
        returnPlayers(partyControllerEntity);
    }

    private void cancelFlow() {
        if (flowTaskId != null) {
            Steveparty.SCHEDULER.cancel(flowTaskId);
            flowTaskId = null;
        }
    }

    // ---------------------------------------------------------------- after the roulette

    /** The roulette chose the mini-game: ring the bells, then (unless a bell waits) the countdown. */
    private void onMiniGameChosen(PartyControllerEntity controller) {
        TeamDisposition disposition = MiniGamesCatalogueItem.getCurrentMiniGameTeamDisposition(controller.catalogue);
        int value = disposition == null || disposition.isFreeForAll() ? 1 : disposition.teamCount();
        phase = Phase.CHOSEN_WAIT;
        controller.markDirty();
        if (controller.ringMoment(PartyMoment.MINIGAME_CHOSEN, value)) {
            controller.sendPacketToInterestedPlayers();
            return;
        }
        startCountdown(controller);
    }

    @Override
    public boolean onMomentReleased(PartyMoment moment, PartyControllerEntity partyControllerEntity) {
        if (moment != PartyMoment.MINIGAME_CHOSEN || !isWaitingForBell()) return false;
        startCountdown(partyControllerEntity);
        return true;
    }

    private void startCountdown(PartyControllerEntity controller) {
        phase = Phase.COUNTDOWN;
        controller.markDirty();
        countdown(controller, COUNTDOWN_SECONDS);
    }

    private void countdown(PartyControllerEntity controller, int seconds) {
        if (!isStillActive(controller)) return;
        List<ServerPlayerEntity> players = getOnlineParticipants(controller);
        if (seconds <= 0) {
            depart(controller);
            return;
        }
        // The countdown shows on the card of the mini-game
        showPreview(controller, seconds);
        playSoundToPlayers(players, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 1f, 1f);
        flowTaskId = UUID.randomUUID();
        Steveparty.SCHEDULER.schedule(flowTaskId, 20, () -> {
            flowTaskId = null;
            countdown(controller, seconds - 1);
        });
    }

    /**
     * The departure: every participant comes out of a pipe of its role, the audience out of the spectators pipes
     * (see {@link MiniGamePipes#distribute}). Those who share a pipe come out one after the other.
     */
    public void depart(PartyControllerEntity controller) {
        phase = Phase.PLAYING;
        hidePreview(controller);
        if (controller.getWorld() instanceof ServerWorld world) {
            MinecraftServer server = world.getServer();
            MiniGamePageData page = MiniGamePages.of(server, MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue));
            TeamDisposition teams = MiniGamesCatalogueItem.getCurrentMiniGameTeamDisposition(controller.catalogue);
            List<UUID> order = new ArrayList<>();
            controller.getPlayersInOrder().stream().filter(participants::contains).forEach(order::add);
            participants.stream().filter(uuid -> !order.contains(uuid)).forEach(order::add);
            List<UUID> spectators = controller.getInterestedPlayersEntities().stream().map(ServerPlayerEntity::getUuid)
                    .filter(uuid -> !participants.contains(uuid)).toList();
            Map<UUID, MiniGamePipeLink> pipes = page == null ? Map.of() : MiniGamePipes.distribute(page, teams, order, spectators);
            Map<MiniGamePipeLink, Integer> queues = new HashMap<>();
            for (UUID uuid : order) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null && !pipes.containsKey(uuid)) {
                    MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.minigame.no_pipe").formatted(Formatting.RED),
                            MessageUtils.MessageType.CHAT);
                }
            }
            pipes.forEach((uuid, link) -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player == null) return;
                returnPositions.putIfAbsent(uuid, new ReturnPos(player.getWorld().getRegistryKey(), player.getX(), player.getY(), player.getZ(),
                        player.getYaw(), player.getPitch()));
                seat(controller, uuid);
                int wait = queues.merge(link, 1, Integer::sum) - 1;
                if (wait == 0) {
                    comeOut(server, link, uuid);
                } else {
                    UUID task = UUID.randomUUID();
                    emergeTasks.add(task);
                    Steveparty.SCHEDULER.schedule(task, wait * EMERGE_GAP_TICKS, () -> {
                        emergeTasks.remove(task);
                        if (isStillActive(controller)) comeOut(server, link, uuid);
                    });
                }
            });
            MessageUtils.sendToPlayers(getOnlineParticipants(controller),
                    Text.translatableWithFallback("message.steveparty.minigame.go", "Go!").styled(style -> style.withColor(0x55FF55).withBold(true)),
                    MessageUtils.MessageType.TITLE);
        }
        controller.markDirty();
        controller.sendPacketToInterestedPlayers();
    }

    /** {@code uuid} is away in this mini-game (free pipes, the exit pipe) for as long as it is this party's step. */
    private void seat(PartyControllerEntity controller, UUID uuid) {
        MiniGamePipes.enterParty(uuid, leaving -> leaveEarly(controller, leaving), () -> isStillActive(controller));
    }

    private void comeOut(MinecraftServer server, MiniGamePipeLink link, UUID uuid) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player == null) return;
        if (!MiniGamePipes.emerge(server, link, player)) {
            MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.minigame.no_pipe").formatted(Formatting.RED),
                    MessageUtils.MessageType.CHAT);
        }
    }

    /**
     * A player leaves the mini-game before its end (the exit pipe): back where it stood before it. The mini-game
     * goes on for the others.
     *
     * @return false if the player is not away in this mini-game
     */
    public boolean leaveEarly(PartyControllerEntity controller, ServerPlayerEntity player) {
        ReturnPos back = returnPositions.remove(player.getUuid());
        MiniGamePipes.leaveParty(player.getUuid());
        if (back == null) return false;
        ServerWorld world = player.server.getWorld(back.dimension());
        if (world == null) return false;
        MiniGameTeleports.teleport(player, world, new Vec3d(back.x(), back.y(), back.z()), back.yaw(), back.pitch());
        controller.markDirty();
        return true;
    }

    // ---------------------------------------------------------------- the card of the mini-game

    /** Those who see the card: the party's audience and the players of the mini-game. */
    private List<ServerPlayerEntity> previewAudience(PartyControllerEntity controller) {
        List<ServerPlayerEntity> audience = new ArrayList<>(controller.getInterestedPlayersEntities());
        for (ServerPlayerEntity player : getOnlineParticipants(controller)) {
            if (!audience.contains(player)) audience.add(player);
        }
        return audience;
    }

    /**
     * Shows the card of the mini-game drawn (picture, title, how it is played) to the audience.
     *
     * @param countdown seconds before the departure, 0 while the countdown has not started
     */
    private void showPreview(PartyControllerEntity controller, int countdown) {
        if (controller.getWorld() == null || controller.getWorld().getServer() == null) return;
        ItemStack page = MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue);
        if (page.isEmpty()) return;
        MiniGamePageData data = MiniGamePages.of(controller.getWorld().getServer(), page);
        if (data == null) data = MiniGamePageData.empty(MiniGamePageData.NO_ID);
        if (!data.hasTitle()) data = data.withTexts(page.getName().getString(), data.description());
        MiniGameMode mode = MiniGameMode.of(MiniGamesCatalogueItem.getCurrentMiniGameTeamDisposition(controller.catalogue));
        MiniGamePagePayloads.Preview payload = new MiniGamePagePayloads.Preview(true, data, mode.ordinal(), countdown);
        for (ServerPlayerEntity player : previewAudience(controller)) {
            if (ServerPlayNetworking.canSend(player, MiniGamePagePayloads.Preview.ID)) ServerPlayNetworking.send(player, payload);
        }
    }

    /** The card goes away (the players leave for the mini-game, or it is called off). */
    private void hidePreview(PartyControllerEntity controller) {
        if (controller.getWorld() == null || controller.getWorld().getServer() == null) return;
        MiniGamePagePayloads.Preview payload = new MiniGamePagePayloads.Preview(false, MiniGamePageData.empty(MiniGamePageData.NO_ID), 0, 0);
        for (ServerPlayerEntity player : previewAudience(controller)) {
            if (ServerPlayNetworking.canSend(player, MiniGamePagePayloads.Preview.ID)) ServerPlayNetworking.send(player, payload);
        }
    }

    // ---------------------------------------------------------------- end of the mini-game

    /**
     * Ends the mini-game (called by a podium): announces the winners, then brings the players back and goes on.
     *
     * @return false if the mini-game is not being played
     */
    public boolean finish(PartyControllerEntity controller, List<UUID> winnerPlayers) {
        if (!isPlaying() && !(status == Status.IN_PROGRESS && phase == Phase.COUNTDOWN)) return false;
        cancelFlow();
        phase = Phase.FINISHED;
        winners.clear();
        winnerPlayers.stream().filter(participants::contains).distinct().forEach(winners::add);
        controller.setLastWinners(winners);
        announceWinners(controller);
        controller.markDirty();
        controller.sendPacketToInterestedPlayers();
        scheduleReturn(controller);
        return true;
    }

    private void announceWinners(PartyControllerEntity controller) {
        List<ServerPlayerEntity> players = getOnlineParticipants(controller);
        MinecraftServer server = controller.getWorld() == null ? null : controller.getWorld().getServer();
        Text title;
        if (winners.isEmpty() || server == null) {
            title = Text.translatableWithFallback("message.steveparty.minigame.no_winner", "No winner!").formatted(Formatting.GRAY);
        } else {
            String names = winners.stream().map(uuid -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                return player == null ? uuid.toString().substring(0, 8) : player.getName().getString();
            }).reduce((a, b) -> a + ", " + b).orElse("");
            title = Text.translatableWithFallback("message.steveparty.minigame.winners", "Winner: %s", names)
                    .styled(style -> style.withColor(0xFFD700).withBold(true));
        }
        MessageUtils.sendToPlayers(players, title, MessageUtils.MessageType.TITLE);
        MessageUtils.sendToPlayers(controller.getPartyAudience(), title, MessageUtils.MessageType.CHAT);
        playSoundToPlayers(players, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1f);
    }

    private void scheduleReturn(PartyControllerEntity controller) {
        cancelFlow();
        flowTaskId = UUID.randomUUID();
        Steveparty.SCHEDULER.schedule(flowTaskId, RETURN_DELAY_TICKS, () -> {
            flowTaskId = null;
            if (isStillActive(controller)) controller.nextStep();
        });
    }

    /** Brings everyone sent to the mini-game and still online back where they stood before it. */
    private void returnPlayers(PartyControllerEntity controller) {
        participants.forEach(MiniGamePipes::leaveParty);
        if (returnPositions.isEmpty() || !(controller.getWorld() instanceof ServerWorld world)) return;
        for (Map.Entry<UUID, ReturnPos> entry : returnPositions.entrySet()) {
            MiniGamePipes.leaveParty(entry.getKey());
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            ReturnPos back = entry.getValue();
            ServerWorld there = world.getServer().getWorld(back.dimension());
            if (player == null || there == null) continue;
            if (player.hasVehicle()) player.stopRiding();
            MiniGameTeleports.teleport(player, there, new Vec3d(back.x(), back.y(), back.z()), back.yaw(), back.pitch());
        }
        returnPositions.clear();
        controller.markDirty();
    }

    private List<ServerPlayerEntity> getOnlineParticipants(PartyControllerEntity controller) {
        List<ServerPlayerEntity> players = new ArrayList<>();
        if (controller.getWorld() == null || controller.getWorld().getServer() == null) return players;
        for (UUID uuid : participants) {
            ServerPlayerEntity player = controller.getWorld().getServer().getPlayerManager().getPlayer(uuid);
            if (player != null) players.add(player);
        }
        return players;
    }

    @Override
    public void onTokenExcluded(UUID tokenUUID, PartyControllerEntity partyControllerEntity) {
        tokens.remove(tokenUUID);
    }

    /** @return true once the roulette chose the mini-game. */
    public boolean isMiniGameChosen() {
        return miniGameChosen;
    }

    /** @return the catalogue slot of the page the roulette chose for this mini-game, -1 before it chose (or if unknown). */
    public int getChosenPageSlot() {
        return chosenPageSlot - 1;
    }

    /** @return true if {@code player} was sent to this mini-game and is not back yet. */
    public boolean isAway(UUID player) {
        return returnPositions.containsKey(player);
    }

    private void cancelRoulette() {
        if (rouletteTaskId != null) {
            Steveparty.SCHEDULER.cancel(rouletteTaskId);
            rouletteTaskId = null;
        }
    }

    // Helper Methods
    private void sendStartMessage(PartyControllerEntity partyControllerEntity) {
        MessageUtils.sendToPlayers(partyControllerEntity.getInterestedPlayersEntities(),
                Text.translatable("message.steveparty.minigame_start")
                        .setStyle(Style.EMPTY.withColor(0xFFA500)),
                MessageUtils.MessageType.CHAT);
    }

    /** The players with a token in the world, in turn order, each with the kind of tile its token stands on. */
    private static List<TeamDispositionGenerator.Seat> seats(PartyControllerEntity controller, ServerWorld world) {
        List<TeamDispositionGenerator.Seat> seats = new ArrayList<>();
        for (UUID tokenId : controller.getPartyData().getTokens()) {
            if (!(world.getEntity(tokenId) instanceof TokenizedEntityInterface token)) continue;
            UUID owner = token.steveparty$getTokenOwner();
            if (owner == null || !(world.getEntity(owner) instanceof PlayerEntity)) continue;
            if (seats.stream().anyMatch(seat -> seat.player().equals(owner))) continue;
            seats.add(new TeamDispositionGenerator.Seat(owner, tokenStatus((MobEntity) token)));
        }
        return seats;
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
            // A new id each time: the scheduler ignores an id that is still registered (the running task's own)
            rouletteTaskId = UUID.randomUUID();
            Steveparty.SCHEDULER.schedule(
                    rouletteTaskId,
                    currentIteration,
                    () -> {
                        rouletteTaskId = null;
                        if (isStillActive(partyControllerEntity))
                            playIterationEffect(iterations, applicableMiniGames, partyControllerEntity, players);
                    }
            );
        }
    }

    private void finalizeMiniGameSelection(AtomicInteger iterations, List<ItemStack> applicableMiniGames, PartyControllerEntity partyControllerEntity, List<ServerPlayerEntity> players) {
        int currentIteration = iterations.get();
        ItemStack chosenMiniGame = applicableMiniGames.get(currentIteration % applicableMiniGames.size());

        MessageUtils.sendToPlayers(
                players,
                formatMiniGameMessage(chosenMiniGame, true),
                MessageUtils.MessageType.ACTION_BAR
        );

        // Store the final selection
        MiniGamesCatalogueItem.setCurrentMiniGamePage(partyControllerEntity.catalogue, chosenMiniGame);
        miniGameChosen = true;
        List<ItemStack> pages = MiniGamesCatalogueItem.getStoredPages(partyControllerEntity.catalogue);
        chosenPageSlot = 0;
        for (int slot = 0; slot < pages.size(); slot++) {
            if (ItemStack.areEqual(pages.get(slot), chosenMiniGame)) {
                chosenPageSlot = slot + 1;
                break;
            }
        }
        partyControllerEntity.markDirty();
        // The mini-game drawn is shown on its card, until the players leave for it
        showPreview(partyControllerEntity, 0);

        // Play a celebratory sound for selection
        playSoundToPlayers(players, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1f, 1);
        onMiniGameChosen(partyControllerEntity);
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

    private static ABoardSpaceBehavior.Status tokenStatus(MobEntity token) {
        BoardSpaceBlockEntity boardSpaceEntity = fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces.boardSpaceOf(token);
        if (boardSpaceEntity == null) return ABoardSpaceBehavior.Status.NEUTRAL;
        ItemStack stack = boardSpaceEntity.getActiveCartridgeItemStack();
        ABoardSpaceBehavior behavior = boardSpaceEntity.getBoardSpaceBehavior(stack);
        return behavior == null ? ABoardSpaceBehavior.Status.NEUTRAL : behavior.getStatus(boardSpaceEntity, stack);
    }

    /**
     * The ways to make teams with these players, each with the pages of the catalogue that can be played that way
     * ({@link #pagePlayable}); the ways no page can be played in are left out.
     *
     * @param seats the players in turn order
     */
    public static Map<TeamDisposition, List<ItemStack>> assignMiniGamesToTeamDispositions(
            List<TeamDispositionGenerator.Seat> seats, List<ItemStack> miniGames, MinecraftServer server) {
        Map<TeamDisposition, List<ItemStack>> teamDispositionsToMiniGames = new LinkedHashMap<>();
        for (TeamDisposition disposition : TeamDispositionGenerator.generateTeamDispositions(seats)) {
            List<ItemStack> applicableMiniGames = new ArrayList<>();
            for (ItemStack page : miniGames) {
                if (pagePlayable(server, page, disposition)) applicableMiniGames.add(page);
            }
            if (!applicableMiniGames.isEmpty()) teamDispositionsToMiniGames.put(disposition, applicableMiniGames);
        }
        return teamDispositionsToMiniGames;
    }

    /**
     * @return true if the page's mini-game can be played by these teams: the page ticks that team layout, accepts
     * that many players, and has a pipe for each team to come out of (the players pipes without teams)
     */
    public static boolean pagePlayable(MinecraftServer server, ItemStack page, TeamDisposition disposition) {
        MiniGamePageData data = MiniGamePages.of(server, page);
        return data != null && data.isPlayable(disposition.size(), MiniGameMode.of(disposition));
    }

    @Override
    public void fromNbt(NbtCompound nbt) {
        super.fromNbt(nbt);
        initCollections();
        this.miniGameChosen = nbt.getBoolean("MiniGameChosen");
        try {
            this.phase = nbt.contains("Phase") ? Phase.valueOf(nbt.getString("Phase")) : Phase.ROULETTE;
        } catch (IllegalArgumentException e) {
            this.phase = Phase.ROULETTE;
        }
        participants.clear();
        readUuids(nbt.getList("Participants", NbtElement.STRING_TYPE), participants);
        winners.clear();
        readUuids(nbt.getList("Winners", NbtElement.STRING_TYPE), winners);
        returnPositions.clear();
        NbtCompound returnsNbt = nbt.getCompound("ReturnPositions");
        for (String key : returnsNbt.getKeys()) {
            try {
                NbtCompound back = returnsNbt.getCompound(key);
                Identifier dimension = Identifier.tryParse(back.getString("Dimension"));
                returnPositions.put(UUID.fromString(key), new ReturnPos(
                        dimension == null || back.getString("Dimension").isEmpty() ? World.OVERWORLD : RegistryKey.of(RegistryKeys.WORLD, dimension),
                        back.getDouble("X"), back.getDouble("Y"),
                        back.getDouble("Z"), back.getFloat("Yaw"), back.getFloat("Pitch")));
            } catch (IllegalArgumentException ignored) {
            }
        }
        this.chosenPageSlot = nbt.contains("ChosenPage") ? nbt.getInt("ChosenPage") + 1 : 0;
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
        if (miniGameChosen)
            nbtCompound.putBoolean("MiniGameChosen", true);
        nbtCompound.putString("Phase", phase.name());
        if (!participants.isEmpty()) nbtCompound.put("Participants", writeUuids(participants));
        if (!winners.isEmpty()) nbtCompound.put("Winners", writeUuids(winners));
        if (!returnPositions.isEmpty()) {
            NbtCompound returnsNbt = new NbtCompound();
            returnPositions.forEach((uuid, back) -> {
                NbtCompound backNbt = new NbtCompound();
                backNbt.putString("Dimension", back.dimension().getValue().toString());
                backNbt.putDouble("X", back.x());
                backNbt.putDouble("Y", back.y());
                backNbt.putDouble("Z", back.z());
                backNbt.putFloat("Yaw", back.yaw());
                backNbt.putFloat("Pitch", back.pitch());
                returnsNbt.put(uuid.toString(), backNbt);
            });
            nbtCompound.put("ReturnPositions", returnsNbt);
        }
        if (chosenPageSlot > 0)
            nbtCompound.putInt("ChosenPage", chosenPageSlot - 1);
        return nbtCompound;
    }

    private static void readUuids(NbtList list, List<UUID> into) {
        for (int i = 0; i < list.size(); i++) {
            try {
                into.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static NbtList writeUuids(List<UUID> uuids) {
        NbtList list = new NbtList();
        uuids.forEach(uuid -> list.add(NbtString.of(uuid.toString())));
        return list;
    }
}
