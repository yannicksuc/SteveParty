package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.MiniGameGains;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGameTeleports;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import fr.lordfinn.steveparty.podium.PodiumGroup;
import fr.lordfinn.steveparty.podium.Podiums;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static fr.lordfinn.steveparty.utils.SoundsUtils.playSoundToPlayers;

/**
 * A mini-game played as a test, out of any party: started with the « Test » button of its page's editor, to try what
 * was built without a party controller.
 * <ul>
 *     <li><b>Who plays</b> ({@link #recruit}, {@link #plan}): every player within {@value #RECRUIT_RADIUS} blocks of
 *     a pipe linked to the page, with the role of that pipe (the nearest one): green, a player; blue, red, purple,
 *     orange, teams A to D; white or glass, a spectator. Entry and exit pipes recruit nobody. The test is played in
 *     the way to play ticked on the page that fits the most of them (each of its teams needs a player, and their number
 *     must be within the page's); those near a pipe that way does not use are left out, and told so. Whoever started
 *     it without standing near a pipe only watches.</li>
 *     <li><b>How it goes</b>: like the mini-game step of a party ({@code MiniGamePartyStep}): the podiums of the page
 *     are emptied and its counters put back to 0, its card is shown with a countdown, the players come out of the
 *     pipes of their role, the spectators out of the spectators pipes. While it is played it is the
 *     {@link MiniGameSession} of its page: its podiums only take its players, its goal pole bases count them, its
 *     step controllers end it.</li>
 *     <li><b>The end</b>: every place taken, or every player (every team) placed, or a linked step controller, as in
 *     a party: the results are shown with what each place would be paid by default, and nothing is paid; a few
 *     seconds later everyone is back where they stood when the test started. « Stop the test » (the same button), a
 *     step controller set to restart or previous, or the last player leaving the server, stop it at once, without
 *     results.</li>
 * </ul>
 * One test per page, and nobody in two mini-games at once. A party drawing a page under test stops the test first.
 * Nothing is saved: a server stopping brings the players back.
 */
public final class MiniGameTest implements MiniGameSession {
    public static final double RECRUIT_RADIUS = 5;
    public static final int COUNTDOWN_SECONDS = 3;
    private static final int EMERGE_GAP_TICKS = 8;
    private static final int WATCH_INTERVAL_TICKS = 20;

    public enum Phase { COUNTDOWN, PLAYING, FINISHED }

    /** Whether a page can be tested now ({@link #READY}), is being tested ({@link #RUNNING}), or why it can't. */
    public enum Status {
        READY,
        RUNNING,
        /** No way to play ticked on the page has the pipes its players come out of. */
        NO_PIPE,
        /** Nobody stands near a players or team pipe of the page. */
        NOBODY,
        /** Those near the pipes are too few (or too many), or a team has nobody. */
        NOT_ENOUGH,
        /** A party is playing this mini-game. */
        PARTY_PLAYING
    }

    /**
     * How a test would be played with those near the pipes.
     *
     * @param mode    the way to play, null when it can't be tested
     * @param teams   the teams (everyone in « team B » without teams), null when it can't be tested
     * @param ignored those near a pipe whose role the way to play does not use, with that role
     */
    public record Plan(Status status, @Nullable MiniGameMode mode, @Nullable TeamDisposition teams, List<UUID> players,
                       List<UUID> spectators, Map<UUID, MiniGamePipeRole> ignored) {
    }

    private record ReturnPos(RegistryKey<World> dimension, Vec3d pos, float yaw, float pitch) {
    }

    private static final Map<UUID, MiniGameTest> TESTS = new LinkedHashMap<>();

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (TESTS.isEmpty() || server.getTicks() % WATCH_INTERVAL_TICKS != 0) return;
            // A test nobody plays any more (its players all left the server) stops by itself
            for (MiniGameTest test : new ArrayList<>(TESTS.values())) {
                if (test.players.stream().noneMatch(uuid -> server.getPlayerManager().getPlayer(uuid) != null)) test.stop();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (MiniGameTest test : new ArrayList<>(TESTS.values())) test.stop();
            TESTS.clear();
        });
    }

    // ------------------------------------------------------------------ who plays

    /** The players near the pipes of a page, each with the role of the nearest pipe, by name. */
    public static Map<UUID, MiniGamePipeRole> recruit(MinecraftServer server, MiniGamePageData page) {
        List<ServerPlayerEntity> near = new ArrayList<>(server.getPlayerManager().getPlayerList());
        near.sort(Comparator.comparing(player -> player.getGameProfile().getName()));
        Map<UUID, MiniGamePipeRole> recruits = new LinkedHashMap<>();
        for (ServerPlayerEntity player : near) {
            if (player.isSpectator() || !player.isAlive() || MiniGamePipes.isInParty(player.getUuid())) continue;
            MiniGamePipeRole role = null;
            double best = RECRUIT_RADIUS * RECRUIT_RADIUS;
            for (MiniGamePipeLink link : page.pipeLinks()) {
                if (!link.role().isArrival() || !link.mouth().dimension().equals(player.getWorld().getRegistryKey())) continue;
                double distance = player.squaredDistanceTo(Vec3d.ofCenter(link.mouth().pos()));
                if (distance <= best) {
                    best = distance;
                    role = link.role();
                }
            }
            if (role != null) recruits.put(player.getUuid(), role);
        }
        return recruits;
    }

    /** The roles of the players of a way to play: the players pipes without teams, else one role per team. */
    private static List<MiniGamePipeRole> playerRoles(MiniGameMode mode) {
        if (mode.teams() <= 1) return List.of(MiniGamePipeRole.PLAYERS);
        List<MiniGamePipeRole> roles = new ArrayList<>();
        for (int team = 0; team < mode.teams(); team++) roles.add(MiniGamePipeRole.ofTeam(team));
        return roles;
    }

    /**
     * How the page would be tested with {@code recruits}: among the ways to play it ticks (and has the pipes of), the
     * one that takes the most of them, each of its teams having someone and their number being within the page's.
     *
     * @param recruits those near its pipes with their role, in order ({@link #recruit})
     */
    public static Plan plan(MiniGamePageData page, Map<UUID, MiniGamePipeRole> recruits) {
        List<UUID> spectators = new ArrayList<>();
        int candidates = 0;
        for (Map.Entry<UUID, MiniGamePipeRole> recruit : recruits.entrySet()) {
            if (recruit.getValue() == MiniGamePipeRole.SPECTATORS) spectators.add(recruit.getKey());
            else candidates++;
        }
        if (!page.isPlayable()) return new Plan(Status.NO_PIPE, null, null, List.of(), spectators, Map.of());
        if (candidates == 0) return new Plan(Status.NOBODY, null, null, List.of(), spectators, Map.of());
        Plan best = null;
        for (MiniGameMode mode : MiniGameMode.values()) {
            if (!page.modes().contains(mode) || !page.hasPipesFor(mode)) continue;
            List<MiniGamePipeRole> roles = playerRoles(mode);
            List<UUID> players = new ArrayList<>();
            Map<UUID, MiniGamePipeRole> ignored = new LinkedHashMap<>();
            List<Set<UUID>> teams = List.of(new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
            for (Map.Entry<UUID, MiniGamePipeRole> recruit : recruits.entrySet()) {
                int index = roles.indexOf(recruit.getValue());
                if (index >= 0) {
                    players.add(recruit.getKey());
                    teams.get(index).add(recruit.getKey());
                } else if (recruit.getValue() != MiniGamePipeRole.SPECTATORS) {
                    ignored.put(recruit.getKey(), recruit.getValue());
                }
            }
            if (players.size() < page.minPlayers() || players.size() > page.maxPlayers()) continue;
            if (mode.teams() > 1 && teams.subList(0, mode.teams()).stream().anyMatch(Set::isEmpty)) continue;
            if (best != null && players.size() <= best.players().size()) continue;
            TeamDisposition disposition = mode.teams() <= 1 ? TeamDisposition.freeForAll(players)
                    : new TeamDisposition(teams.get(0), teams.get(1), teams.get(2), teams.get(3));
            best = new Plan(Status.READY, mode, disposition, players, spectators, ignored);
        }
        return best != null ? best : new Plan(Status.NOT_ENOUGH, null, null, List.of(), spectators, Map.of());
    }

    // ------------------------------------------------------------------ the tests of a server

    /** The test of a page, whatever its phase; null when it is not being tested. */
    public static @Nullable MiniGameTest of(UUID page) {
        return TESTS.get(page);
    }

    /** The test being played right now on one of {@code pages}, null for none. */
    static @Nullable MiniGameTest playing(Collection<UUID> pages) {
        for (UUID page : pages) {
            MiniGameTest test = TESTS.get(page);
            if (test != null && test.phase == Phase.PLAYING) return test;
        }
        return null;
    }

    /** Whether the page can be tested now, and with whom. */
    public static Plan check(MinecraftServer server, UUID pageId) {
        if (TESTS.containsKey(pageId)) return new Plan(Status.RUNNING, null, null, List.of(), List.of(), Map.of());
        if (PartyControllerEntity.getPartyPlayingPage(List.of(pageId)).isPresent())
            return new Plan(Status.PARTY_PLAYING, null, null, List.of(), List.of(), Map.of());
        MiniGamePageData page = MiniGamePages.get(server, pageId);
        return plan(page, recruit(server, page));
    }

    /**
     * Starts the test of a page with those near its pipes.
     *
     * @param starter          who asked for it: if not near a pipe, an observer (told what happens, not moved)
     * @param countdownSeconds seconds the card is shown before the departure
     * @return {@link Status#READY} if it started, else why not
     */
    public static Status start(MinecraftServer server, UUID pageId, @Nullable ServerPlayerEntity starter, int countdownSeconds) {
        Plan plan = check(server, pageId);
        if (plan.status() != Status.READY) return plan.status();
        MiniGameTest test = new MiniGameTest(server, pageId, plan, starter);
        TESTS.put(pageId, test);
        test.begin(plan, countdownSeconds);
        return Status.READY;
    }

    /** Stops the test of a page, if any (its button, or a party about to play the page). @return true if one was stopped */
    public static boolean stop(UUID pageId) {
        MiniGameTest test = TESTS.get(pageId);
        if (test == null) return false;
        test.stop();
        return true;
    }

    // ------------------------------------------------------------------ a test

    private final MinecraftServer server;
    private final UUID pageId;
    private final MiniGameMode mode;
    private final TeamDisposition disposition;
    private final List<UUID> players;
    private final List<UUID> spectators;
    private final @Nullable UUID observer;
    private final Map<UUID, ReturnPos> returns = new LinkedHashMap<>();
    private final List<UUID> tasks = new ArrayList<>();
    private Phase phase = Phase.COUNTDOWN;
    private boolean closed = false;
    private Map<UUID, Integer> places = Map.of();
    private @Nullable MiniGameResults results;

    private MiniGameTest(MinecraftServer server, UUID pageId, Plan plan, @Nullable ServerPlayerEntity starter) {
        this.server = server;
        this.pageId = pageId;
        this.mode = plan.mode();
        this.disposition = plan.teams();
        this.players = List.copyOf(plan.players());
        this.spectators = List.copyOf(plan.spectators());
        this.observer = starter == null || players.contains(starter.getUuid()) || spectators.contains(starter.getUuid()) ? null : starter.getUuid();
    }

    public UUID pageId() {
        return pageId;
    }

    public Phase phase() {
        return phase;
    }

    public MiniGameMode mode() {
        return mode;
    }

    public List<UUID> spectators() {
        return spectators;
    }

    public @Nullable UUID observer() {
        return observer;
    }

    /** The place of each player once the test is over (0: none), empty before. */
    public Map<UUID, Integer> places() {
        return places;
    }

    /** The results shown at the end of the test, null before. */
    public @Nullable MiniGameResults results() {
        return results;
    }

    @Override
    public Collection<UUID> participants() {
        return players;
    }

    @Override
    public @Nullable TeamDisposition teams() {
        return disposition.isFreeForAll() ? null : disposition;
    }

    /** The players, the spectators and the observer who are connected. */
    @Override
    public List<ServerPlayerEntity> audience() {
        List<ServerPlayerEntity> audience = new ArrayList<>();
        List<UUID> everyone = new ArrayList<>(players);
        everyone.addAll(spectators);
        if (observer != null) everyone.add(observer);
        for (UUID uuid : everyone) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) audience.add(player);
        }
        return audience;
    }

    private List<ServerPlayerEntity> onlinePlayers() {
        List<ServerPlayerEntity> online = new ArrayList<>();
        for (UUID uuid : players) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) online.add(player);
        }
        return online;
    }

    private MiniGamePageData page() {
        return MiniGamePages.get(server, pageId);
    }

    private void send(CustomPayload payload) {
        for (ServerPlayerEntity player : audience()) {
            if (ServerPlayNetworking.canSend(player, payload.getId())) ServerPlayNetworking.send(player, payload);
        }
    }

    private void later(int ticks, Runnable action) {
        UUID task = UUID.randomUUID();
        tasks.add(task);
        Steveparty.SCHEDULER.schedule(task, ticks, () -> {
            tasks.remove(task);
            if (!closed) action.run();
        });
    }

    private void begin(Plan plan, int countdownSeconds) {
        // Where everyone stands now: where they go back at the end
        for (ServerPlayerEntity player : audience()) {
            if (observer != null && observer.equals(player.getUuid())) continue;
            returns.put(player.getUuid(), new ReturnPos(player.getWorld().getRegistryKey(), player.getPos(), player.getYaw(), player.getPitch()));
        }
        plan.ignored().forEach((uuid, role) -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) player.sendMessage(Text.translatable("message.steveparty.minigame.test.ignored", role.text(), mode.text())
                    .formatted(Formatting.GOLD), false);
        });
        // Away in this mini-game from now on (nobody in two mini-games at once): free pipes, the exit pipe brings back
        for (UUID uuid : returns.keySet()) MiniGamePipes.enterParty(uuid, this::leaveEarly, () -> !closed);
        send(new MiniGamePagePayloads.TestLabel(true, page().title()));
        MessageUtils.sendToPlayers(audience(), Text.translatable("message.steveparty.minigame.test.start", players.size(), mode.text())
                .formatted(Formatting.GOLD), MessageUtils.MessageType.CHAT);
        countdown(countdownSeconds);
    }

    /** The card of the mini-game with its countdown, then the departure: as in a party. */
    private void countdown(int seconds) {
        if (closed) return;
        if (seconds <= 0) {
            send(new MiniGamePagePayloads.Preview(false, MiniGamePageData.empty(MiniGamePageData.NO_ID), 0, 0));
            MiniGameIntro.play(page(), audience(), () -> {
                if (!closed) depart();
            });
            return;
        }
        send(new MiniGamePagePayloads.Preview(true, page(), mode.ordinal(), seconds));
        playSoundToPlayers(onlinePlayers(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 1f, 1f);
        later(20, () -> countdown(seconds - 1));
    }

    /** The departure, as in a party: podiums and counters start again, everyone comes out of a pipe of its role. */
    private void depart() {
        MiniGamePageData page = page();
        Podiums.resetForMiniGame(server, page);
        phase = Phase.PLAYING;
        Map<UUID, MiniGamePipeLink> pipes = MiniGamePipes.distribute(page, disposition, players, spectators, new Random());
        Map<MiniGamePipeLink, Integer> queues = new HashMap<>();
        pipes.forEach((uuid, link) -> {
            int wait = queues.merge(link, 1, Integer::sum) - 1;
            if (wait == 0) comeOut(link, uuid);
            else later(wait * EMERGE_GAP_TICKS, () -> comeOut(link, uuid));
        });
        MiniGamePartyStep.announceStart(page.title(), page, audience());
    }

    private void comeOut(MiniGamePipeLink link, UUID uuid) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null && !MiniGamePipes.emerge(server, link, player)) {
            player.sendMessage(Text.translatable("message.steveparty.minigame.no_pipe").formatted(Formatting.RED), false);
        }
    }

    /** A player takes the exit pipe: back where it stood, the test goes on for the others. */
    private boolean leaveEarly(ServerPlayerEntity player) {
        MiniGamePipes.leaveParty(player.getUuid());
        return bringBack(player, returns.remove(player.getUuid()));
    }

    private boolean bringBack(ServerPlayerEntity player, @Nullable ReturnPos back) {
        ServerWorld world = back == null ? null : server.getWorld(back.dimension());
        if (world == null) return false;
        if (player.hasVehicle()) player.stopRiding();
        MiniGameTeleports.teleport(player, world, back.pos(), back.yaw(), back.pitch());
        return true;
    }

    @Override
    public void onPodiumsChanged() {
        if (phase != Phase.PLAYING || closed) return;
        PodiumGroup group = PodiumGroup.ofPage(server, page());
        if (group == null || group.isEmpty()) return;
        if (group.isFull() || MiniGameResults.places(players, disposition, group).values().stream().allMatch(place -> place > 0)) finish();
    }

    @Override
    public void step(int stepMode) {
        if (stepMode == 0) finish();
        else stop();
    }

    /**
     * The test ends like a party's mini-game: the places are read on the podiums, the results shown with what each
     * place would be paid by default, and nothing is paid; then everyone goes back.
     */
    public void finish() {
        if (phase != Phase.PLAYING || closed) return;
        phase = Phase.FINISHED;
        MiniGamePageData page = page();
        places = MiniGameResults.places(players, disposition, PodiumGroup.ofPage(server, page));
        results = MiniGameResults.of(page.title(), PartyCurrency.COIN.defaultStack(), PartyCurrency.STAR.defaultStack(), MiniGameGains.DEFAULT,
                places, disposition, uuid -> {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                    return player != null ? player.getGameProfile().getName() : uuid.toString().substring(0, 8);
                }).asTest();
        send(new MiniGamePagePayloads.Results(results));
        List<ServerPlayerEntity> audience = audience();
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable(page.hasTitle() ? "message.steveparty.minigame.results.of" : "message.steveparty.minigame.results", page.title())
                .styled(style -> style.withColor(0xFFC52E).withBold(true)));
        for (MiniGameResults.Row row : results.rows()) lines.add(MiniGamePartyStep.resultLine(row, results.coinItem(), results.starItem()));
        lines.add(Text.translatable("message.steveparty.minigame.test.no_gain").formatted(Formatting.GRAY));
        for (Text line : lines) MessageUtils.sendToPlayers(audience, line, MessageUtils.MessageType.CHAT);
        playSoundToPlayers(onlinePlayers(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1f);
        later(MiniGamePartyStep.RETURN_DELAY_TICKS, this::close);
    }

    /** Stops the test at once, without results: everyone goes back. */
    public void stop() {
        if (closed) return;
        if (phase != Phase.FINISHED) {
            send(new MiniGamePagePayloads.Preview(false, MiniGamePageData.empty(MiniGamePageData.NO_ID), 0, 0));
            MessageUtils.sendToPlayers(audience(), Text.translatable("message.steveparty.minigame.test.stopped").formatted(Formatting.GOLD),
                    MessageUtils.MessageType.CHAT);
        }
        close();
    }

    /** The test is over: everyone still away goes back where it stood when it started. */
    private void close() {
        if (closed) return;
        send(new MiniGamePagePayloads.TestLabel(false, ""));
        closed = true;
        phase = Phase.FINISHED;
        tasks.forEach(Steveparty.SCHEDULER::cancel);
        tasks.clear();
        List<UUID> seated = new ArrayList<>(players);
        seated.addAll(spectators);
        seated.forEach(MiniGamePipes::leaveParty);
        returns.forEach((uuid, back) -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) bringBack(player, back);
        });
        returns.clear();
        TESTS.remove(pageId, this);
    }
}
