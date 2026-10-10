package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.minigame.MiniGameControllers;
import fr.lordfinn.steveparty.minigame.MiniGameIntro;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import fr.lordfinn.steveparty.minigame.MiniGameResults;
import fr.lordfinn.steveparty.minigame.MiniGameReturns;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import fr.lordfinn.steveparty.podium.PodiumGroup;
import fr.lordfinn.steveparty.podium.Podiums;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static fr.lordfinn.steveparty.utils.SoundsUtils.playSoundToPlayers;

/**
 * A mini-game: teams from the board spaces the tokens stand on (positive: team A, negative: team B, see
 * {@link TeamDispositionGenerator}), a page of the catalogue that can be played by them drawn by a roulette, then:
 * <ol>
 *     <li>the "mini-game chosen" party bells ring (a waiting bell pauses here),</li>
 *     <li>a 3 second countdown, and the players come out of the pipes linked to the page, by team: one after the
 *     other in each pipe of their role in turn, in turn order ({@link MiniGamePipes#distribute}); the audience
 *     comes out of the spectators pipes,</li>
 *     <li>when the page has a Mini-game Controller and the party its practice rounds (a setting of the party
 *     controller, on by default), they first play a <b>practice round</b>: the same mini-game, whose results are
 *     shown and pay nothing, started again after its results for as long as someone is not ready. Each player says
 *     he is ready whenever he wants ({@link #toggleReady}: a key, or the Mini-game Controller's screen); once every
 *     connected player is, the practice stops at once and everyone comes out of the pipes again, for the real
 *     round,</li>
 *     <li>the mini-game is played. The podiums linked to its page record who takes which place ({@link Podiums});
 *     it ends when every place is taken, or every player (every team) has one, or when a step controller goes
 *     on,</li>
 *     <li>the results are read on the podiums (the place of each player, of each team in a team mini-game; no place:
 *     « participant »), the party controller pays the gains of its Gains page, everyone sees the results card, and
 *     the players are brought back where they were. The winners (kept for the party bells and the piggy banks) are
 *     the players of the first place.</li>
 * </ol>
 * A page without podium can be played too: it only ends with a step controller, and everyone is a participant.
 * <p>
 * This class holds the state of the step (saved) and its phases; the draw is {@link MiniGameRoulette}'s, the trips
 * {@link MiniGameDeparture}'s, what the players see {@link MiniGameScreens}'s and the gains {@link MiniGamePayout}'s.
 */
public class MiniGamePartyStep extends PartyStep {
    private static final int COUNTDOWN_SECONDS = 3;
    /** The results card stays on screen that long before the players go back. */
    public static final int RETURN_DELAY_TICKS = 100;

    public enum Phase { ROULETTE, CHOSEN_WAIT, COUNTDOWN, PRACTICE, PLAYING, FINISHED }

    // No initializer: it would run after super(nbt) and wipe what fromNbt just read
    private List<UUID> tokens;
    boolean miniGameChosen; // no initializer, see above
    Phase phase;
    /** Players taking part (owners of the tokens), in turn order. */
    List<UUID> participants;
    /** Where a player sent to the mini-game stood before it. */
    Map<UUID, MiniGameReturns.Return> returnPositions;
    private List<UUID> winners;
    /** The place of each participant once the mini-game is over (0: none, a « participant »), in turn order. */
    private Map<UUID, Integer> places;
    private UUID flowTaskId = null;
    /** The catalogue slot of the page the roulette chose, plus one (0: none yet). No initializer, see above. */
    int chosenPageSlot;
    /** The players who said they are ready for the real round, during the practice round. */
    private Set<UUID> ready;
    /** The practice round showed its results: it starts again in a few seconds. Not saved. */
    boolean practiceOver;

    // Not saved; built after super(nbt), which only reads the fields above
    private final MiniGameRoulette roulette = new MiniGameRoulette(this);
    final MiniGameScreens screens = new MiniGameScreens(this);
    private final MiniGameDeparture departure = new MiniGameDeparture(this);
    private final MiniGamePayout payout = new MiniGamePayout();

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
        if (places == null) places = new LinkedHashMap<>();
        if (ready == null) ready = new LinkedHashSet<>();
    }

    public Phase getPhase() {
        return phase;
    }

    public List<UUID> getWinners() {
        return Collections.unmodifiableList(winners);
    }

    /** The place of each participant of the mini-game once it is over (0: no place), in turn order. */
    public Map<UUID, Integer> getPlaces() {
        return Collections.unmodifiableMap(places);
    }

    public List<UUID> getParticipants() {
        return Collections.unmodifiableList(participants);
    }

    /** @return true while the real round of the mini-game is being played: a podium may end it, and it pays. */
    public boolean isPlaying() {
        return status == Status.IN_PROGRESS && phase == Phase.PLAYING;
    }

    /** @return true during the practice round (see {@link #startPractice}). */
    public boolean isPractice() {
        return status == Status.IN_PROGRESS && phase == Phase.PRACTICE;
    }

    /** @return true while the players are in the mini-game: its practice round or its real round. */
    public boolean isOnArena() {
        return isPlaying() || isPractice();
    }

    /** The players who said they are ready, during the practice round. */
    public Set<UUID> getReady() {
        return Collections.unmodifiableSet(ready);
    }

    @Override
    public boolean isWaitingForBell() {
        return status == Status.IN_PROGRESS && phase == Phase.CHOSEN_WAIT;
    }

    @Override
    public void start(PartyControllerEntity partyControllerEntity) {
        super.start(partyControllerEntity);
        roulette.cancel();
        cancelFlow();
        miniGameChosen = false;
        phase = Phase.ROULETTE;
        participants.clear();
        winners.clear();
        places.clear();
        ready.clear();
        practiceOver = false;
        departure.reset();
        // Players still away from a previous run of this step (restart) keep their original return position
        chosenPageSlot = 0;

        // Step 1: Ensure the world is a ServerWorld
        if (!(partyControllerEntity.getWorld() instanceof ServerWorld serverWorld)) {
            return;  // Exit early if not a ServerWorld
        }

        // Step 2: Check if there are mini-games to play, else tell the players and skip
        screens.hidePreview(partyControllerEntity);
        // The pages show the title they have now
        MiniGamesCatalogueItem.refreshPages(serverWorld.getServer(), partyControllerEntity.catalogue);
        List<ItemStack> miniGames = partyControllerEntity.getMiniGames();
        if (miniGames.isEmpty()) {
            MessageUtils.sendToPlayers(partyControllerEntity.getInterestedPlayersEntities(),
                    Text.translatable("message.steveparty.no_minigame").setStyle(Style.EMPTY.withColor(0xFFA500)),
                    MessageUtils.MessageType.CHAT);
            partyControllerEntity.nextStep();
            return;
        }

        // Step 4: The players in turn order, with the kind of tile their token stands on
        List<TeamDispositionGenerator.Seat> seats = MiniGameRoulette.seats(partyControllerEntity, serverWorld);
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

        // Mini-games are on: tell the players
        MessageUtils.sendToPlayers(partyControllerEntity.getInterestedPlayersEntities(),
                Text.translatable("message.steveparty.minigame_start").setStyle(Style.EMPTY.withColor(0xFFA500)),
                MessageUtils.MessageType.CHAT);

        // Step 6: Choose a random disposition for the mini-games
        TeamDisposition chosenDisposition = MiniGameRoulette.chooseRandomDisposition(miniGamesToTeamDispositions);
        MiniGamesCatalogueItem.setCurrentMiniGameTeamDisposition(partyControllerEntity.catalogue, chosenDisposition);
        partyControllerEntity.markDirty();

        // Step 7: Notify players about the chosen disposition
        MessageUtils.sendToPlayers(partyControllerEntity.getInterestedPlayersEntities(),
                Text.translatable("message.steveparty.chosen_disposition", chosenDisposition.toText(serverWorld.getServer())),
                MessageUtils.MessageType.CHAT);

        // Step 8: Shuffle and prepare mini-games for the chosen disposition
        List<ItemStack> applicableMiniGames = miniGamesToTeamDispositions.get(chosenDisposition);
        Collections.shuffle(applicableMiniGames);

        // Step 9: Start the iteration effect through the mini-games
        roulette.spin(applicableMiniGames, partyControllerEntity, partyControllerEntity.getInterestedPlayersEntities());
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
                // Those away in the mini-game are known again as such (free pipes, the linked pipes closed to them)
                if (phase == Phase.PLAYING || phase == Phase.PRACTICE) {
                    returnPositions.keySet().forEach(uuid -> departure.seat(partyControllerEntity, uuid));
                    // Its podiums may have filled up meanwhile
                    onPodiumsChanged(partyControllerEntity);
                }
            }
        }
    }

    @Override
    public void end(PartyControllerEntity partyControllerEntity) {
        super.end(partyControllerEntity);
        roulette.cancel();
        cancelFlow();
        departure.cancelEmerges();
        screens.hidePreview(partyControllerEntity);
        screens.hidePractice(partyControllerEntity);
        // A round stopped before its results: what everyone owns and the zone are given back first
        departure.endRound();
        // However the mini-game ends (podium, step controller...), the players go back where they were
        departure.returnPlayers(partyControllerEntity);
    }

    private void cancelFlow() {
        if (flowTaskId != null) {
            Steveparty.SCHEDULER.cancel(flowTaskId);
            flowTaskId = null;
        }
    }

    // ---------------------------------------------------------------- after the roulette

    /** The roulette chose the mini-game: ring the bells, then (unless a bell waits) the countdown. */
    void onMiniGameChosen(PartyControllerEntity controller) {
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
            // The introduction of the page, if it has one, then the departure
            MiniGamePageData page = controller.getWorld() == null || controller.getWorld().getServer() == null ? null
                    : MiniGamePages.of(controller.getWorld().getServer(), MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue));
            MiniGameIntro.play(page, screens.previewAudience(controller), () -> {
                if (isStillActive(controller)) leaveForMiniGame(controller);
            });
            return;
        }
        // The countdown shows on the card of the mini-game
        screens.showPreview(controller, seconds);
        playSoundToPlayers(players, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 1f, 1f);
        flowTaskId = UUID.randomUUID();
        Steveparty.SCHEDULER.schedule(flowTaskId, 20, () -> {
            flowTaskId = null;
            countdown(controller, seconds - 1);
        });
    }

    /** After the countdown: the practice round when this mini-game has one ({@link #practiceWanted}), else the real round. */
    public void leaveForMiniGame(PartyControllerEntity controller) {
        if (practiceWanted(controller)) startPractice(controller);
        else depart(controller);
    }

    /** The departure for the real round (straight after the countdown, or once everyone is ready after the practice). */
    public void depart(PartyControllerEntity controller) {
        boolean afterPractice = phase == Phase.PRACTICE;
        cancelFlow();
        ready.clear();
        practiceOver = false;
        screens.hidePractice(controller);
        if (afterPractice) {
            MessageUtils.sendToPlayers(screens.previewAudience(controller), Text.translatable("message.steveparty.minigame.practice.everyone_ready")
                    .formatted(Formatting.GOLD), MessageUtils.MessageType.CHAT);
        }
        departure.sendOut(controller, Phase.PLAYING);
        if (phase == Phase.PLAYING) fr.lordfinn.steveparty.api.event.MiniGameEvents.STARTED.invoker().onMiniGameStarted(controller, this);
    }

    // ---------------------------------------------------------------- the practice round

    /** @return true if this mini-game starts with a practice round: the party has them, and the page a Mini-game Controller. */
    private boolean practiceWanted(PartyControllerEntity controller) {
        if (!controller.hasPracticeRound() || !(controller.getWorld() instanceof ServerWorld world)) return false;
        UUID page = MiniGamePages.idOf(MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue));
        return page != null && MiniGameControllers.has(world.getServer(), page);
    }

    /**
     * The practice round: the mini-game as it will be played (same players, same teams, same pipes, its podiums and
     * its counters), but its results pay nothing and it starts again after them, until every connected player said
     * he is ready ({@link #toggleReady}).
     */
    public void startPractice(PartyControllerEntity controller) {
        cancelFlow();
        ready.clear();
        practiceOver = false;
        departure.sendOut(controller, Phase.PRACTICE);
        MessageUtils.sendToPlayers(screens.previewAudience(controller), Text.translatable("message.steveparty.minigame.practice.start",
                Text.keybind("key.steveparty.minigame_ready").formatted(Formatting.WHITE)).formatted(Formatting.GOLD), MessageUtils.MessageType.CHAT);
        screens.showPractice(controller);
    }

    /** The practice round starts again (after its results, or a step controller): the votes are kept. */
    public void restartPractice(PartyControllerEntity controller) {
        if (!isPractice()) return;
        cancelFlow();
        practiceOver = false;
        departure.sendOut(controller, Phase.PRACTICE);
        screens.showPractice(controller);
    }

    /** The practice round is over: its results are shown, nothing is paid, and it starts again a few seconds later. */
    public void practiceResults(PartyControllerEntity controller) {
        if (!isPractice() || practiceOver) return;
        MinecraftServer server = controller.getWorld() == null ? null : controller.getWorld().getServer();
        if (server == null) return;
        practiceOver = true;
        MiniGameResults results = MiniGamePayout.results(controller, server, placesOnPodiums(controller), null).asPractice();
        // The results are read: the round is over, inventories and zone are given back until the next one
        departure.endArena();
        screens.tellResults(controller, results, Text.translatable("message.steveparty.minigame.practice.no_gain").formatted(Formatting.GRAY));
        cancelFlow();
        flowTaskId = UUID.randomUUID();
        Steveparty.SCHEDULER.schedule(flowTaskId, RETURN_DELAY_TICKS, () -> {
            flowTaskId = null;
            if (isStillActive(controller)) restartPractice(controller);
        });
    }

    /**
     * A player says he is ready for the real round, or no longer is. Once every connected player is, the practice
     * stops and the real round starts.
     *
     * @return false if the player has no vote (no practice round, or not one of its players)
     */
    public boolean toggleReady(PartyControllerEntity controller, UUID player) {
        if (!isPractice() || !participants.contains(player)) return false;
        if (!ready.remove(player)) ready.add(player);
        controller.markDirty();
        if (!checkReady(controller)) screens.showPractice(controller);
        return true;
    }

    /**
     * Starts the real round if every connected player of the mini-game is ready (a player who left the server no
     * longer holds the others back).
     *
     * @return true if the real round started
     */
    public boolean checkReady(PartyControllerEntity controller) {
        if (!isPractice()) return false;
        List<ServerPlayerEntity> online = getOnlineParticipants(controller);
        if (online.isEmpty() || !online.stream().allMatch(player -> ready.contains(player.getUuid()))) return false;
        depart(controller);
        return true;
    }

    /** The player's vote in the practice round of his party, from anywhere (the key, the Mini-game Controller). */
    public static boolean toggleReady(ServerPlayerEntity player) {
        for (PartyControllerEntity controller : PartyControllerEntity.getActivePartyControllers()) {
            if (!controller.isRemoved() && controller.getPartyData().getCurrentStep() instanceof MiniGamePartyStep step
                    && step.toggleReady(controller, player.getUuid())) return true;
        }
        return false;
    }

    /** The connected players of the practice round with their vote, in the play order. */
    public List<MiniGamePagePayloads.Practice.Voter> voters(PartyControllerEntity controller) {
        List<MiniGamePagePayloads.Practice.Voter> voters = new ArrayList<>();
        MinecraftServer server = controller.getWorld() == null ? null : controller.getWorld().getServer();
        if (server == null) return voters;
        for (UUID uuid : orderedParticipants(controller)) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) voters.add(new MiniGamePagePayloads.Practice.Voter(player.getGameProfile().getName(), ready.contains(uuid)));
        }
        return voters;
    }

    /** During the practice round, every second: a player who left no longer holds the vote back, one who joined sees the chip. */
    @Override
    public void tick(PartyControllerEntity partyControllerEntity, ServerWorld world) {
        super.tick(partyControllerEntity, world);
        if (!isPractice() || world.getTime() % 20 != 0) return;
        if (!checkReady(partyControllerEntity)) screens.showPractice(partyControllerEntity);
    }

    // ---------------------------------------------------------------- the start (see MiniGameScreens)

    /** The start of a mini-game (a party's, or a test): its name in big with « Go! », its name and description in the chat. */
    public static void announceStart(String name, @Nullable MiniGamePageData page, Collection<ServerPlayerEntity> audience) {
        announceStart(name, page, audience, true);
    }

    /** @param inChat false: only the big title (a practice round starting again: the chat already told the mini-game) */
    public static void announceStart(String name, @Nullable MiniGamePageData page, Collection<ServerPlayerEntity> audience, boolean inChat) {
        MiniGameScreens.announceStart(name, page, audience, inChat);
    }

    // ---------------------------------------------------------------- end of the mini-game

    /** The podiums linked to the page of the mini-game, null for none. */
    private @Nullable PodiumGroup podiums(PartyControllerEntity controller) {
        if (controller.getWorld() == null || controller.getWorld().getServer() == null) return null;
        MinecraftServer server = controller.getWorld().getServer();
        MiniGamePageData page = MiniGamePages.of(server, MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue));
        return page == null ? null : PodiumGroup.ofPage(server, page);
    }

    /** The participants in the play order. */
    List<UUID> orderedParticipants(PartyControllerEntity controller) {
        List<UUID> order = new ArrayList<>();
        controller.getPlayersInOrder().stream().filter(participants::contains).forEach(order::add);
        participants.stream().filter(uuid -> !order.contains(uuid)).forEach(order::add);
        return order;
    }

    /** The place of each participant as the podiums of the mini-game say now (0: no place). */
    public Map<UUID, Integer> placesOnPodiums(PartyControllerEntity controller) {
        return MiniGameResults.places(orderedParticipants(controller),
                MiniGamesCatalogueItem.getCurrentMiniGameTeamDisposition(controller.catalogue), podiums(controller));
    }

    /**
     * Someone registered on a podium of the mini-game, or left it: the mini-game ends when every place is taken or
     * every player (every team) has one. Until then it goes on, whoever holds the first place: only a step controller
     * ends it earlier.
     */
    public void onPodiumsChanged(PartyControllerEntity controller) {
        if (!isOnArena() || (isPractice() && practiceOver)) return;
        PodiumGroup group = podiums(controller);
        if (group == null || group.isEmpty()) return;
        if (!group.isFull() && !placesOnPodiums(controller).values().stream().allMatch(place -> place > 0)) return;
        if (isPractice()) practiceResults(controller);
        else finish(controller);
    }

    /**
     * Ends the mini-game being played: the places are read on its podiums, the gains paid, the results shown, then
     * the players are brought back and the party goes on.
     *
     * @return false if the mini-game is not being played
     */
    public boolean finish(PartyControllerEntity controller) {
        if (!isPlaying()) return false;
        conclude(controller, placesOnPodiums(controller));
        scheduleReturn(controller);
        return true;
    }

    /**
     * Ends the mini-game with named winners, whatever its podiums say: they take the first place, the others are
     * participants.
     *
     * @return false if the mini-game is not being played
     */
    public boolean finish(PartyControllerEntity controller, List<UUID> winnerPlayers) {
        if (!isPlaying() && !(status == Status.IN_PROGRESS && phase == Phase.COUNTDOWN)) return false;
        Map<UUID, Integer> named = new LinkedHashMap<>();
        for (UUID player : orderedParticipants(controller)) named.put(player, winnerPlayers.contains(player) ? 1 : 0);
        conclude(controller, named);
        scheduleReturn(controller);
        return true;
    }

    /** The party goes on while the mini-game is being played (a step controller): its results count all the same. */
    public void concludeIfPlaying(PartyControllerEntity controller) {
        if (isPlaying()) conclude(controller, placesOnPodiums(controller));
    }

    /** The mini-game is over: the places are kept, the gains paid, the results told to everyone. */
    private void conclude(PartyControllerEntity controller, Map<UUID, Integer> finalPlaces) {
        cancelFlow();
        phase = Phase.FINISHED;
        // The places are read: the round is over. Inventories and zone are given back before the gains are paid
        // (they go to what the players own, not to a session inventory)
        departure.endRound();
        places.clear();
        places.putAll(finalPlaces);
        winners.clear();
        places.forEach((player, place) -> {
            if (place == 1) winners.add(player);
        });
        controller.setLastWinners(winners);
        MinecraftServer server = controller.getWorld() == null ? null : controller.getWorld().getServer();
        if (server != null) {
            Text note = payout.payGains(controller, server, places);
            screens.tellResults(controller, MiniGamePayout.results(controller, server, places, payout.paid()), note);
        }
        fr.lordfinn.steveparty.api.event.MiniGameEvents.ENDED.invoker().onMiniGameEnded(controller, this, Map.copyOf(places), List.copyOf(winners));
        controller.markDirty();
        controller.sendPacketToInterestedPlayers();
    }

    /** « 1st: Steve +10 Emerald », « Participants: Alex, Sam ». */
    public static Text resultLine(MiniGameResults.Row row, ItemStack coin, ItemStack star) {
        return MiniGameScreens.resultLine(row, coin, star);
    }

    /** The results of the mini-game once it is over (as sent to the players), null before. Not saved. */
    public @Nullable MiniGameResults getLastResults() {
        return screens.lastResults();
    }

    private void scheduleReturn(PartyControllerEntity controller) {
        cancelFlow();
        flowTaskId = UUID.randomUUID();
        Steveparty.SCHEDULER.schedule(flowTaskId, RETURN_DELAY_TICKS, () -> {
            flowTaskId = null;
            if (isStillActive(controller)) controller.nextStep();
        });
    }

    List<ServerPlayerEntity> getOnlineParticipants(PartyControllerEntity controller) {
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

    // ---------------------------------------------------------------- the draw (see MiniGameRoulette)

    /**
     * The ways to make teams with these players, each with the pages of the catalogue that can be played that way
     * ({@link #pagePlayable}); the ways no page can be played in are left out.
     *
     * @param seats the players in turn order
     */
    public static Map<TeamDisposition, List<ItemStack>> assignMiniGamesToTeamDispositions(
            List<TeamDispositionGenerator.Seat> seats, List<ItemStack> miniGames, MinecraftServer server) {
        return MiniGameRoulette.assignMiniGamesToTeamDispositions(seats, miniGames, server);
    }

    /**
     * @return true if the page's mini-game can be played by these teams: one of its formats fits them (see
     * {@link fr.lordfinn.steveparty.minigame.MiniGameFormat#matches}) and has a pipe for each team to come out of (the
     * players pipes without teams)
     */
    public static boolean pagePlayable(MinecraftServer server, ItemStack page, TeamDisposition disposition) {
        return MiniGameRoulette.pagePlayable(server, page, disposition);
    }

    /**
     * The teams as the page's format plays them: the board's groups (positive tiles, negative tiles...) given to the
     * format's sides by their sizes ({@link fr.lordfinn.steveparty.minigame.MiniGameFormat#assignment}): side 1 is team
     * A, side 2 team B... The board's order is kept when it already fits. Unchanged without teams, or when no format of
     * the page fits.
     */
    public static TeamDisposition arrange(MiniGamePageData page, TeamDisposition disposition) {
        return MiniGameRoulette.arrange(page, disposition);
    }

    /**
     * The players of each team of a composition, its empty teams left out (one count when everyone is on one side, as
     * in free for all), for {@link MiniGamePageData#formatFor}.
     */
    public static List<Integer> counts(@Nullable TeamDisposition disposition) {
        return MiniGameRoulette.counts(disposition);
    }

    // ---------------------------------------------------------------- save

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
        ready.clear();
        List<UUID> readyList = new ArrayList<>();
        readUuids(nbt.getList("Ready", NbtElement.STRING_TYPE), readyList);
        ready.addAll(readyList);
        winners.clear();
        readUuids(nbt.getList("Winners", NbtElement.STRING_TYPE), winners);
        places.clear();
        NbtCompound placesNbt = nbt.getCompound("Places");
        for (String key : placesNbt.getKeys()) {
            try {
                places.put(UUID.fromString(key), placesNbt.getInt(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        returnPositions.clear();
        NbtCompound returnsNbt = nbt.getCompound("ReturnPositions");
        for (String key : returnsNbt.getKeys()) {
            try {
                returnPositions.put(UUID.fromString(key), MiniGameReturns.Return.fromNbt(returnsNbt.getCompound(key)));
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
        if (!ready.isEmpty()) nbtCompound.put("Ready", writeUuids(new ArrayList<>(ready)));
        if (!winners.isEmpty()) nbtCompound.put("Winners", writeUuids(winners));
        if (!places.isEmpty()) {
            NbtCompound placesNbt = new NbtCompound();
            places.forEach((uuid, place) -> placesNbt.putInt(uuid.toString(), place));
            nbtCompound.put("Places", placesNbt);
        }
        if (!returnPositions.isEmpty()) {
            NbtCompound returnsNbt = new NbtCompound();
            returnPositions.forEach((uuid, back) -> returnsNbt.put(uuid.toString(), back.toNbt()));
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
