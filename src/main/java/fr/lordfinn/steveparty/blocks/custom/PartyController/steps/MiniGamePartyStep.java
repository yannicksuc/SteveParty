package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.minigame.MiniGameReturns;
import fr.lordfinn.steveparty.minigame.MiniGameArena;
import fr.lordfinn.steveparty.minigame.MiniGameIntro;
import fr.lordfinn.steveparty.minigame.MiniGameText;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeLink;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import fr.lordfinn.steveparty.minigame.MiniGameResults;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.podium.PodiumGroup;
import fr.lordfinn.steveparty.podium.Podiums;
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
 */
public class MiniGamePartyStep extends PartyStep {
    private static final int COUNTDOWN_SECONDS = 3;
    /** The results card stays on screen that long before the players go back. */
    public static final int RETURN_DELAY_TICKS = 100;

    public enum Phase { ROULETTE, CHOSEN_WAIT, COUNTDOWN, PRACTICE, PLAYING, FINISHED }

    /** Ticks between two players coming out of the same pipe. */
    private static final int EMERGE_GAP_TICKS = 8;

    /** Where a player sent to the mini-game stood before it. */

    // No initializer: it would run after super(nbt) and wipe what fromNbt just read
    private List<UUID> tokens;
    private boolean miniGameChosen; // no initializer, see above
    private Phase phase;
    /** Players taking part (owners of the tokens), in turn order. */
    private List<UUID> participants;
    private Map<UUID, MiniGameReturns.Return> returnPositions;
    private List<UUID> winners;
    /** The place of each participant once the mini-game is over (0: none, a « participant »), in turn order. */
    private Map<UUID, Integer> places;
    private UUID rouletteTaskId = null;
    private UUID flowTaskId = null;
    /** The players still waiting for their turn to come out of a pipe. */
    private final List<UUID> emergeTasks = new ArrayList<>();
    /** The catalogue slot of the page the roulette chose, plus one (0: none yet). No initializer, see above. */
    private int chosenPageSlot;
    /** The players who said they are ready for the real round, during the practice round. */
    private Set<UUID> ready;
    /** The practice round showed its results: it starts again in a few seconds. Not saved. */
    private boolean practiceOver;
    /** The chat already told which mini-game starts: said once, however many times the players leave for it. Not saved. */
    private boolean toldInChat;
    /** What the practice chip last showed, and to whom: sent again only when it changes. Not saved. */
    private @Nullable Object practiceShown;
    /**
     * The zone of the mini-game, round after round: each practice round and the real round are played in a bubble
     * of their own when the page has a zone ({@link MiniGameArena}). Not saved: a round a server restart
     * interrupted goes on without one (the server put its zone back when it stopped).
     */
    private final MiniGameArena arena = new MiniGameArena();
    /** Counts the departures: one that waited for its zone only leaves if no other was asked meanwhile. Not saved. */
    private int departures;
    /** The players were told why this mini-game is played without the protection of its zone. Not saved. */
    private boolean unprotectedTold;

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
        cancelRoulette();
        cancelFlow();
        miniGameChosen = false;
        phase = Phase.ROULETTE;
        participants.clear();
        winners.clear();
        places.clear();
        ready.clear();
        practiceOver = false;
        toldInChat = false;
        unprotectedTold = false;
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
                if (phase == Phase.PLAYING || phase == Phase.PRACTICE) {
                    returnPositions.keySet().forEach(uuid -> seat(partyControllerEntity, uuid));
                    // Its podiums may have filled up meanwhile
                    onPodiumsChanged(partyControllerEntity);
                }
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
        hidePractice(partyControllerEntity);
        // A round stopped before its results: what everyone owns and the zone are given back first
        departures++;
        arena.end();
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
            // The introduction of the page, if it has one, then the departure
            MiniGamePageData page = controller.getWorld() == null || controller.getWorld().getServer() == null ? null
                    : MiniGamePages.of(controller.getWorld().getServer(), MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue));
            MiniGameIntro.play(page, previewAudience(controller), () -> {
                if (isStillActive(controller)) leaveForMiniGame(controller);
            });
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
        hidePractice(controller);
        if (afterPractice) {
            MessageUtils.sendToPlayers(previewAudience(controller), Text.translatable("message.steveparty.minigame.practice.everyone_ready")
                    .formatted(Formatting.GOLD), MessageUtils.MessageType.CHAT);
        }
        sendOut(controller, Phase.PLAYING);
    }

    // ---------------------------------------------------------------- the practice round

    /** @return true if this mini-game starts with a practice round: the party has them, and the page a Mini-game Controller. */
    private boolean practiceWanted(PartyControllerEntity controller) {
        if (!controller.hasPracticeRound() || !(controller.getWorld() instanceof ServerWorld world)) return false;
        UUID page = MiniGamePages.idOf(MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue));
        return page != null && fr.lordfinn.steveparty.minigame.MiniGameControllers.has(world.getServer(), page);
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
        sendOut(controller, Phase.PRACTICE);
        MessageUtils.sendToPlayers(previewAudience(controller), Text.translatable("message.steveparty.minigame.practice.start",
                Text.keybind("key.steveparty.minigame_ready").formatted(Formatting.WHITE)).formatted(Formatting.GOLD), MessageUtils.MessageType.CHAT);
        showPractice(controller);
    }

    /** The practice round starts again (after its results, or a step controller): the votes are kept. */
    public void restartPractice(PartyControllerEntity controller) {
        if (!isPractice()) return;
        cancelFlow();
        practiceOver = false;
        sendOut(controller, Phase.PRACTICE);
        showPractice(controller);
    }

    /** The practice round is over: its results are shown, nothing is paid, and it starts again a few seconds later. */
    public void practiceResults(PartyControllerEntity controller) {
        if (!isPractice() || practiceOver) return;
        MinecraftServer server = controller.getWorld() == null ? null : controller.getWorld().getServer();
        if (server == null) return;
        practiceOver = true;
        MiniGameResults results = results(controller, server, placesOnPodiums(controller)).asPractice();
        // The results are read: the round is over, inventories and zone are given back until the next one
        arena.end();
        tellResults(controller, results, Text.translatable("message.steveparty.minigame.practice.no_gain").formatted(Formatting.GRAY));
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
        if (!checkReady(controller)) showPractice(controller);
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

    /** The practice chip of the players and the audience: « Practice — [key] Ready 2/4 », and who is ready. */
    private void showPractice(PartyControllerEntity controller) {
        if (!isPractice() || controller.getWorld() == null || controller.getWorld().getServer() == null) return;
        ItemStack stack = MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue);
        MiniGamePageData page = MiniGamePages.of(controller.getWorld().getServer(), stack);
        String title = page != null && page.hasTitle() ? page.title() : stack.isEmpty() ? "" : stack.getName().getString();
        MiniGamePagePayloads.Practice payload = new MiniGamePagePayloads.Practice(true, title, voters(controller));
        List<ServerPlayerEntity> audience = previewAudience(controller);
        Object shown = List.of(payload, audience.stream().map(ServerPlayerEntity::getUuid).toList());
        if (shown.equals(practiceShown)) return;
        practiceShown = shown;
        for (ServerPlayerEntity player : audience) {
            if (ServerPlayNetworking.canSend(player, MiniGamePagePayloads.Practice.ID)) ServerPlayNetworking.send(player, payload);
        }
    }

    private void hidePractice(PartyControllerEntity controller) {
        if (practiceShown == null && phase != Phase.PRACTICE) return;
        practiceShown = null;
        MiniGamePagePayloads.Practice payload = new MiniGamePagePayloads.Practice(false, "", List.of());
        for (ServerPlayerEntity player : previewAudience(controller)) {
            if (ServerPlayNetworking.canSend(player, MiniGamePagePayloads.Practice.ID)) ServerPlayNetworking.send(player, payload);
        }
    }

    /** During the practice round, every second: a player who left no longer holds the vote back, one who joined sees the chip. */
    @Override
    public void tick(PartyControllerEntity partyControllerEntity, ServerWorld world) {
        super.tick(partyControllerEntity, world);
        if (!isPractice() || world.getTime() % 20 != 0) return;
        if (!checkReady(partyControllerEntity)) showPractice(partyControllerEntity);
    }

    // ---------------------------------------------------------------- the departure

    /**
     * Everyone leaves for the mini-game: every participant comes out of a pipe of its role, the audience out of the
     * spectators pipes (see {@link MiniGamePipes#distribute}). Those who share a pipe come out one after the other.
     *
     * @param round {@link Phase#PRACTICE} or {@link Phase#PLAYING}
     */
    private void sendOut(PartyControllerEntity controller, Phase round) {
        emergeTasks.forEach(Steveparty.SCHEDULER::cancel);
        emergeTasks.clear();
        // The round before (a practice round the vote stopped, or one started again): what everyone owns and the
        // zone are given back before anything else
        arena.end();
        int departure = ++departures;
        if (!(controller.getWorld() instanceof ServerWorld world)) {
            leave(controller, round);
            return;
        }
        MiniGamePageData page = MiniGamePages.of(world.getServer(), MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue));
        // A test of the page gives way to the party: it is stopped, its players go back
        if (page != null) fr.lordfinn.steveparty.minigame.MiniGameTest.stop(page.id());
        // The zone may still be put back from the round before: the departure waits for it
        arena.whenZoneFree(world.getServer(), page == null ? null : page.id(), () -> departure == departures && isStillActive(controller),
                () -> previewAudience(controller), () -> leave(controller, round));
    }

    /** The departure itself, once the zone of the mini-game is free. */
    private void leave(PartyControllerEntity controller, Phase round) {
        // A new mini-game: its podiums are emptied and its counters go back to 0 (before it is being played)
        if (controller.getWorld() instanceof ServerWorld world) {
            MiniGamePageData page = MiniGamePages.of(world.getServer(), MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue));
            if (page != null) Podiums.resetForMiniGame(world.getServer(), page);
        }
        phase = round;
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
            Map<UUID, MiniGamePipeLink> pipes = page == null ? Map.of() : MiniGamePipes.distribute(page, teams, order, spectators, new Random());
            Map<MiniGamePipeLink, Integer> queues = new HashMap<>();
            for (UUID uuid : order) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null && !pipes.containsKey(uuid)) {
                    MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.minigame.no_pipe").formatted(Formatting.RED),
                            MessageUtils.MessageType.CHAT);
                }
            }
            if (page != null) beginInZone(controller, server, page, pipes);
            pipes.forEach((uuid, link) -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player == null) return;
                returnPositions.putIfAbsent(uuid, MiniGameReturns.Return.of(player));
                seat(controller, uuid);
                int wait = queues.merge(link, 1, Integer::sum) - 1;
                if (wait == 0) {
                    comeOut(server, link, uuid, page == null ? null : page.id());
                } else {
                    UUID task = UUID.randomUUID();
                    emergeTasks.add(task);
                    Steveparty.SCHEDULER.schedule(task, wait * EMERGE_GAP_TICKS, () -> {
                        emergeTasks.remove(task);
                        if (isStillActive(controller)) comeOut(server, link, uuid, page == null ? null : page.id());
                    });
                }
            });
            announce(controller, page, !toldInChat);
            toldInChat = true;
        }
        controller.markDirty();
        controller.sendPacketToInterestedPlayers();
    }

    /**
     * The round is played in the zone of its page, when it has one, by those who have a pipe to come out of: the
     * players of the mini-game, and those of the audience sent to its spectators pipes. They leave their inventory
     * at the door until the round is over. A zone that can't take the round (too big for the server, taken, too
     * full) never holds a party up: the round is played without its protection, and everyone is told why, once.
     */
    private void beginInZone(PartyControllerEntity controller, MinecraftServer server, MiniGamePageData page, Map<UUID, MiniGamePipeLink> pipes) {
        List<ServerPlayerEntity> players = new ArrayList<>(), watching = new ArrayList<>();
        for (UUID uuid : pipes.keySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) (participants.contains(uuid) ? players : watching).add(player);
        }
        arena.begin(server, page.id(), players, watching, () -> isStillActive(controller));
        Text why = arena.refusalText();
        if (why == null || unprotectedTold) return;
        unprotectedTold = true;
        MessageUtils.sendToPlayers(previewAudience(controller), Text.translatable("message.steveparty.zone_bubble.party_unprotected", why)
                .formatted(Formatting.RED), MessageUtils.MessageType.CHAT);
    }

    /**
     * The mini-game starts: its title in big on the screen (« Go! » under it), and in the chat its title and, when
     * it has one, its description, for the players and the audience.
     */
    private void announce(PartyControllerEntity controller, @Nullable MiniGamePageData page, boolean inChat) {
        ItemStack stack = MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue);
        String name = page != null && page.hasTitle() ? page.title() : stack.isEmpty() ? "" : stack.getName().getString();
        announceStart(name, page, previewAudience(controller), inChat);
    }

    /** The start of a mini-game (a party's, or a test): its name in big with « Go! », its name and description in the chat. */
    public static void announceStart(String name, @Nullable MiniGamePageData page, Collection<ServerPlayerEntity> audience) {
        announceStart(name, page, audience, true);
    }

    /** @param inChat false: only the big title (a practice round starting again: the chat already told the mini-game) */
    public static void announceStart(String name, @Nullable MiniGamePageData page, Collection<ServerPlayerEntity> audience, boolean inChat) {
        Text go = Text.translatableWithFallback("message.steveparty.minigame.go", "Go!").styled(style -> style.withColor(0x55FF55).withBold(true));
        Text title = name.isEmpty() ? go : Text.literal(name).styled(style -> style.withColor(0xFFC52E).withBold(true));
        for (ServerPlayerEntity player : audience) {
            if (!name.isEmpty()) player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(go));
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(title));
            if (name.isEmpty() || !inChat) continue;
            player.sendMessage(Text.translatable("message.steveparty.minigame.title", title), false);
            if (page != null && !MiniGameText.strip(page.description()).isBlank()) {
                player.sendMessage(MiniGameText.parse(page.description(), Style.EMPTY.withColor(Formatting.GRAY)), false);
            }
        }
    }

    /** {@code uuid} is away in this mini-game (free pipes, the exit pipe) for as long as it is this party's step. */
    private void seat(PartyControllerEntity controller, UUID uuid) {
        UUID page = MiniGamePages.idOf(MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue));
        MiniGamePipes.enterParty(uuid, page == null ? MiniGamePageData.NO_ID : page, leaving -> leaveEarly(controller, leaving), () -> isStillActive(controller));
    }

    private void comeOut(MinecraftServer server, MiniGamePipeLink link, UUID uuid, @Nullable UUID pageId) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        // Its turn at the pipe came after the round's end (its results read): it would come out in an arena put back
        // as built, with what it owns
        if (player == null || !isOnArena() || practiceOver) return;
        if (!MiniGamePipes.emergeInRound(server, link, player, pageId)) {
            // No pipe to come out of any more: it stays where it is, with what it owns
            arena.leave(player);
            MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.minigame.no_pipe").formatted(Formatting.RED),
                    MessageUtils.MessageType.CHAT);
        }
    }

    /**
     * A player leaves the mini-game before its end (a pipe linked to its page, see {@link MiniGamePipes}): out of the
     * round, what it owns given back; the way out decides where it goes (the return position given back, null if it
     * had none). The mini-game goes on for the others.
     */
    public MiniGameReturns.@Nullable Return leaveEarly(PartyControllerEntity controller, ServerPlayerEntity player) {
        MiniGameReturns.Return back = returnPositions.remove(player.getUuid());
        MiniGamePipes.leaveParty(player.getUuid());
        // Out of the round: what it owns first
        arena.leave(player);
        if (back != null) controller.markDirty();
        return back;
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
        int format = data.formatFor(counts(MiniGamesCatalogueItem.getCurrentMiniGameTeamDisposition(controller.catalogue)));
        MiniGamePagePayloads.Preview payload = new MiniGamePagePayloads.Preview(true, data, format, countdown);
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

    /** The podiums linked to the page of the mini-game, null for none. */
    private @Nullable PodiumGroup podiums(PartyControllerEntity controller) {
        if (controller.getWorld() == null || controller.getWorld().getServer() == null) return null;
        MinecraftServer server = controller.getWorld().getServer();
        MiniGamePageData page = MiniGamePages.of(server, MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue));
        return page == null ? null : PodiumGroup.ofPage(server, page);
    }

    /** The participants in the play order. */
    private List<UUID> orderedParticipants(PartyControllerEntity controller) {
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
        departures++;
        arena.end();
        places.clear();
        places.putAll(finalPlaces);
        winners.clear();
        places.forEach((player, place) -> {
            if (place == 1) winners.add(player);
        });
        controller.setLastWinners(winners);
        MinecraftServer server = controller.getWorld() == null ? null : controller.getWorld().getServer();
        if (server != null) {
            Text note = payGains(controller, server);
            tellResults(controller, results(controller, server, places, paid), note);
        }
        controller.markDirty();
        controller.sendPacketToInterestedPlayers();
    }

    /** What each player was paid by the last results ({coins, stars}), for their card. */
    private final Map<UUID, int[]> paid = new LinkedHashMap<>();

    /**
     * Pays the gains of the places, taken from the party's bank (see {@link fr.lordfinn.steveparty.blocks.custom.PartyController.PartyBank}):
     * the 1st place first, then the 2nd..., the participants last, in turn order within a place; when the bank runs
     * short, a player gets what is left and the next ones nothing. Nothing is created.
     *
     * @return the line telling everyone the gains were not all paid, null if they were
     */
    private @Nullable Text payGains(PartyControllerEntity controller, MinecraftServer server) {
        paid.clear();
        List<Map.Entry<UUID, Integer>> order = new ArrayList<>(places.entrySet());
        order.sort(java.util.Comparator.comparingInt(entry -> entry.getValue() <= 0 ? Integer.MAX_VALUE : entry.getValue()));
        net.minecraft.inventory.Inventory bank = fr.lordfinn.steveparty.blocks.custom.PartyController.PartyBank.inventory(server, controller.getBank());
        boolean full = true;
        for (Map.Entry<UUID, Integer> entry : order) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) continue;
            // Holding a session inventory (he left the round and plays elsewhere, a visit...): what he would be paid
            // would go with it. Like one who is away, he is not paid: the bank keeps it
            if (fr.lordfinn.steveparty.minigame.zone.ZoneBubbles.ofPlayer(player) != null) continue;
            PartyControllerEntity.Paid received = controller.payGains(player, entry.getValue(), bank);
            paid.put(entry.getKey(), new int[]{received.coins(), received.stars()});
            full &= received.full();
        }
        if (full) return null;
        return Text.translatable(bank == null ? "message.steveparty.minigame.results.no_bank" : "message.steveparty.minigame.results.bank_empty")
                .formatted(net.minecraft.util.Formatting.RED);
    }

    /** The results of the mini-game for these places, with what the party pays for each. */
    private MiniGameResults results(PartyControllerEntity controller, MinecraftServer server, Map<UUID, Integer> finalPlaces) {
        return results(controller, server, finalPlaces, null);
    }

    /** The results of the mini-game for these places, with what each player was paid (null: the gains of the places). */
    private MiniGameResults results(PartyControllerEntity controller, MinecraftServer server, Map<UUID, Integer> finalPlaces,
                                    @Nullable Map<UUID, int[]> received) {
        ItemStack stack = MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue);
        MiniGamePageData page = MiniGamePages.of(server, stack);
        String title = page != null && page.hasTitle() ? page.title() : stack.isEmpty() ? "" : stack.getName().getString();
        TeamDisposition teams = MiniGamesCatalogueItem.getCurrentMiniGameTeamDisposition(controller.catalogue);
        ItemStack coin = controller.getCurrency(PartyCurrency.COIN), star = controller.getCurrency(PartyCurrency.STAR);
        return MiniGameResults.of(title, coin, star, controller.getGains(), finalPlaces, teams, uuid -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) return player.getGameProfile().getName();
            return server.getUserCache() == null ? uuid.toString().substring(0, 8)
                    : server.getUserCache().getByUuid(uuid).map(com.mojang.authlib.GameProfile::getName).orElse(uuid.toString().substring(0, 8));
        }, received);
    }

    /** Shows results to the players and the audience: the card, and the same lines in the chat ({@code note}: a last line). */
    private void tellResults(PartyControllerEntity controller, MiniGameResults results, @Nullable Text note) {
        String title = results.title();
        ItemStack coin = results.coinItem(), star = results.starItem();
        lastResults = results;
        MiniGamePagePayloads.Results payload = new MiniGamePagePayloads.Results(results);
        List<ServerPlayerEntity> audience = previewAudience(controller);
        for (ServerPlayerEntity player : controller.getPartyAudience()) if (!audience.contains(player)) audience.add(player);
        for (ServerPlayerEntity player : audience) {
            if (ServerPlayNetworking.canSend(player, MiniGamePagePayloads.Results.ID)) ServerPlayNetworking.send(player, payload);
        }
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable(title.isEmpty() ? "message.steveparty.minigame.results" : "message.steveparty.minigame.results.of", title)
                .styled(style -> style.withColor(0xFFC52E).withBold(true)));
        for (MiniGameResults.Row row : results.rows()) lines.add(resultLine(row, coin, star));
        if (note != null) lines.add(note);
        for (Text line : lines) MessageUtils.sendToPlayers(audience, line, MessageUtils.MessageType.CHAT);
        playSoundToPlayers(getOnlineParticipants(controller), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1f);
    }

    /** « 1st: Steve +10 Emerald », « Participants: Alex, Sam ». */
    public static Text resultLine(MiniGameResults.Row row, ItemStack coin, ItemStack star) {
        net.minecraft.text.MutableText line = Text.empty();
        line.append((row.place() == 0 ? Text.translatable("message.steveparty.minigame.results.participant") : Podiums.placeText(row.place()))
                .styled(style -> style.withColor(row.place() == 1 ? 0xFFD700 : row.place() == 0 ? 0xA0A0A0 : 0xFFFFFF).withBold(row.place() == 1)));
        line.append(Text.translatable("message.steveparty.minigame.results.separator"));
        if (row.team() >= 0) line.append(Podiums.teamText(row.team())).append(" (");
        line.append(String.join(", ", row.names()));
        if (row.team() >= 0) line.append(")");
        if (row.coins() > 0) line.append(Text.literal("  +" + row.coins() + " ").append(coin.getName()).formatted(Formatting.GREEN));
        if (row.stars() > 0) line.append(Text.literal("  +" + row.stars() + " ").append(star.getName()).formatted(Formatting.YELLOW));
        return line;
    }

    /** The results of the mini-game once it is over (as sent to the players), null before. Not saved. */
    private @Nullable MiniGameResults lastResults;

    public @Nullable MiniGameResults getLastResults() {
        return lastResults;
    }

    private void scheduleReturn(PartyControllerEntity controller) {
        cancelFlow();
        flowTaskId = UUID.randomUUID();
        Steveparty.SCHEDULER.schedule(flowTaskId, RETURN_DELAY_TICKS, () -> {
            flowTaskId = null;
            if (isStillActive(controller)) controller.nextStep();
        });
    }

    /**
     * Brings everyone sent to the mini-game back where they stood before it: now if they are online, else when they
     * come (see {@link MiniGameReturns}).
     */
    private void returnPlayers(PartyControllerEntity controller) {
        participants.forEach(MiniGamePipes::leaveParty);
        if (returnPositions.isEmpty() || !(controller.getWorld() instanceof ServerWorld world)) return;
        for (Map.Entry<UUID, MiniGameReturns.Return> entry : returnPositions.entrySet()) {
            MiniGamePipes.leaveParty(entry.getKey());
            MiniGameReturns.bringBack(world.getServer(), entry.getKey(), entry.getValue());
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
        // The board's groups take the sides of the format played (side 1 team A, out of the blue pipes...)
        MinecraftServer server = partyControllerEntity.getWorld() == null ? null : partyControllerEntity.getWorld().getServer();
        MiniGamePageData chosenData = server == null ? null : MiniGamePages.of(server, chosenMiniGame);
        TeamDisposition drawn = MiniGamesCatalogueItem.getCurrentMiniGameTeamDisposition(partyControllerEntity.catalogue);
        if (chosenData != null && drawn != null) {
            TeamDisposition played = arrange(chosenData, drawn);
            if (!played.equals(drawn)) MiniGamesCatalogueItem.setCurrentMiniGameTeamDisposition(partyControllerEntity.catalogue, played);
            tellSides(server, chosenData, played);
        }
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
     * @return true if the page's mini-game can be played by these teams: one of its formats fits them (see
     * {@link fr.lordfinn.steveparty.minigame.MiniGameFormat#matches}) and has a pipe for each team to come out of (the
     * players pipes without teams)
     */
    public static boolean pagePlayable(MinecraftServer server, ItemStack page, TeamDisposition disposition) {
        MiniGamePageData data = MiniGamePages.of(server, page);
        return data != null && data.isPlayable(counts(disposition));
    }

    /**
     * The teams as the page's format plays them: the board's groups (positive tiles, negative tiles...) given to the
     * format's sides by their sizes ({@link fr.lordfinn.steveparty.minigame.MiniGameFormat#assignment}): side 1 is team
     * A, side 2 team B... The board's order is kept when it already fits. Unchanged without teams, or when no format of
     * the page fits.
     */
    public static TeamDisposition arrange(MiniGamePageData page, TeamDisposition disposition) {
        List<Integer> counts = counts(disposition);
        fr.lordfinn.steveparty.minigame.MiniGameFormat format = page.format(page.formatFor(counts));
        if (format == null || format.kind() != fr.lordfinn.steveparty.minigame.MiniGameFormat.Kind.TEAMS) return disposition;
        int[] order = format.assignment(counts);
        if (order == null) return disposition;
        List<java.util.Set<UUID>> groups = new ArrayList<>();
        for (java.util.Set<UUID> team : disposition.teams()) if (!team.isEmpty()) groups.add(team);
        List<java.util.Set<UUID>> sides = new ArrayList<>(List.of(java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), java.util.Set.of()));
        for (int side = 0; side < order.length; side++) sides.set(side, new java.util.LinkedHashSet<>(groups.get(order[side])));
        return new TeamDisposition(sides.get(0), sides.get(1), sides.get(2), sides.get(3));
    }

    /** Each player is told which team he plays, in its colour (the colour of the pipes he will come out of). */
    private static void tellSides(MinecraftServer server, MiniGamePageData page, TeamDisposition teams) {
        if (teams.isFreeForAll()) return;
        for (int team = 0; team < 4; team++) {
            fr.lordfinn.steveparty.minigame.MiniGamePipeRole role = fr.lordfinn.steveparty.minigame.MiniGamePipeRole.ofTeam(team);
            for (UUID uuid : teams.teams().get(team)) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player == null) continue;
                player.sendMessage(Text.translatable("message.steveparty.minigame.your_side",
                        role.text().copy().styled(style -> style.withColor(role.color()).withBold(true))), false);
            }
        }
    }

    /**
     * The players of each team of a composition, its empty teams left out (one count when everyone is on one side, as
     * in free for all), for {@link MiniGamePageData#formatFor}.
     */
    public static List<Integer> counts(@org.jetbrains.annotations.Nullable TeamDisposition disposition) {
        List<Integer> counts = new ArrayList<>();
        if (disposition == null) return counts;
        for (java.util.Set<UUID> team : disposition.teams()) if (!team.isEmpty()) counts.add(team.size());
        return counts;
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
