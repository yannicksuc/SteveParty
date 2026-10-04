package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.MiniGameGains;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubble;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import fr.lordfinn.steveparty.podium.PodiumGroup;
import fr.lordfinn.steveparty.podium.Podiums;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
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
 * A mini-game played out of any party, for nothing: started with « Play » on its Mini-game Controller's screen, or
 * with the « Test » button of its page's editor, to play or try what was built without a party controller. (The
 * players are only told it is played « out of a party »; the practice round of a party is not this: see
 * {@code MiniGamePartyStep}.)
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
 *     <li><b>The zone</b>: when the page has one, the round is played in a bubble ({@link MiniGameArena}): it begins
 *     at the departure (podiums emptied first) with those who have a pipe to come out of, and ends when the results
 *     are read, or when it is stopped: inventories back, zone put back, then everyone goes back. A zone that can't
 *     take the round (too big for the server, taken, too full) starts nothing: {@link #check} says why.</li>
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
        /** No format of the page has the pipes its players come out of. */
        NO_PIPE,
        /** Nobody stands near a players or team pipe of the page. */
        NOBODY,
        /** Those near the pipes fit no format of the page ({@link Plan#shortfall} says the closest and what it misses). */
        NOT_ENOUGH,
        /** A party is playing this mini-game. */
        PARTY_PLAYING,
        /** The zone of the mini-game is bigger than the server lets a zone be. */
        ZONE_TOO_BIG,
        /** The zone of the mini-game is taken: a round is played in a zone that overlaps it, or it is being put back. */
        ZONE_BUSY,
        /** The dimension of the zone is not there. */
        ZONE_NO_WORLD,
        /** The zone holds more containers or entities than a zone may (only known when the round begins). */
        ZONE_TOO_FULL,
        /** The zone holds a block or an entity the server does not allow in a zone. */
        ZONE_FORBIDDEN;

        /** Why the zone of a mini-game can't take a round, null when it can. */
        static @Nullable Status ofZone(ZoneBubble.Refusal refusal) {
            return switch (refusal) {
                case NONE, DISABLED -> null;
                case TOO_BIG -> ZONE_TOO_BIG;
                case OVERLAP -> ZONE_BUSY;
                case NO_WORLD -> ZONE_NO_WORLD;
                case TOO_MANY_BLOCK_ENTITIES, TOO_MANY_ENTITIES -> ZONE_TOO_FULL;
                case FORBIDDEN_BLOCK, FORBIDDEN_ENTITY -> ZONE_FORBIDDEN;
            };
        }
    }

    /**
     * How a test would be played with those near the pipes.
     *
     * @param format    the index of the page's format played, -1 when it can't be tested
     * @param teams     the teams (everyone in « team B » without teams), null when it can't be tested
     * @param ignored   those near a pipe whose role the format does not use, with that role
     * @param shortfall when nobody fits ({@link Status#NOT_ENOUGH}): the closest format and what it misses, else null
     */
    public record Plan(Status status, int format, @Nullable TeamDisposition teams, List<UUID> players,
                       List<UUID> spectators, Map<UUID, MiniGamePipeRole> ignored, @Nullable Shortfall shortfall) {
        Plan(Status status, List<UUID> spectators) {
            this(status, -1, null, List.of(), spectators, Map.of(), null);
        }
    }

    /**
     * What keeps those near the pipes from playing the page's closest format.
     *
     * @param format the index of that format
     * @param role   the ordinal of the role whose players don't fit ({@link MiniGamePipeRole}), -1 when they all fit
     *               but the teams are not of the same size
     * @param count  how many stand near its pipes
     * @param min    how many its side wants at least
     * @param max    and at most ({@link MiniGameFormat.Side#INFINITE}: no limit)
     */
    public record Shortfall(int format, int role, int count, int min, int max) {
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

    /**
     * How the page would be tested with {@code recruits}: among its formats (that have their pipes), those the teams
     * near the pipes fit in order (the players near the team A pipes play side 1, those near the B pipes side 2...;
     * without teams, those near the players pipes); of them the one that takes the most players, the most specific when
     * equal. When none fits, the closest one (the fewest players missing or too many) and what it misses.
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
        if (!page.isPlayable()) return new Plan(Status.NO_PIPE, spectators);
        if (candidates == 0) return new Plan(Status.NOBODY, spectators);
        Plan best = null;
        Shortfall closest = null;
        int closestDistance = Integer.MAX_VALUE, closestSpecificity = Integer.MAX_VALUE;
        for (int i = 0; i < page.formats().size(); i++) {
            MiniGameFormat format = page.formats().get(i);
            if (!page.hasPipesFor(format)) continue;
            List<MiniGamePipeRole> roles = format.neededRoles();
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
            List<Integer> counts = new ArrayList<>();
            if (format.kind() == MiniGameFormat.Kind.TEAMS) for (int team = 0; team < roles.size(); team++) counts.add(teams.get(team).size());
            else counts.add(players.size());
            if (format.matchesInOrder(counts)) {
                if (best != null && (players.size() < best.players().size()
                        || players.size() == best.players().size() && format.specificity() >= page.formats().get(best.format()).specificity())) continue;
                TeamDisposition disposition = format.kind() != MiniGameFormat.Kind.TEAMS ? TeamDisposition.freeForAll(players)
                        : new TeamDisposition(teams.get(0), teams.get(1), teams.get(2), teams.get(3));
                best = new Plan(Status.READY, i, disposition, players, spectators, ignored, null);
                continue;
            }
            // Not this one: how far from it
            // Not this one: how far from it (the teams near the pipes are in order: team A near the A pipes...)
            int distance = format.distanceInOrder(counts);
            Shortfall shortfall = null;
            for (int side = 0; side < counts.size() && shortfall == null; side++) {
                MiniGameFormat.Side range = format.sides().get(side);
                if (!range.contains(counts.get(side))) shortfall = new Shortfall(i, roles.get(side).ordinal(), counts.get(side), range.min(), range.max());
            }
            if (shortfall == null) {
                // Every team fits its range, they are not of the same size
                distance = 1;
                shortfall = new Shortfall(i, -1, 0, 0, 0);
            }
            if (distance < closestDistance || distance == closestDistance && format.specificity() < closestSpecificity) {
                closestDistance = distance;
                closestSpecificity = format.specificity();
                closest = shortfall;
            }
        }
        if (best != null) return best;
        return new Plan(Status.NOT_ENOUGH, -1, null, List.of(), spectators, Map.of(), closest);
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
        if (TESTS.containsKey(pageId)) return new Plan(Status.RUNNING, List.of());
        if (PartyControllerEntity.getPartyPlayingPage(List.of(pageId)).isPresent()) return new Plan(Status.PARTY_PLAYING, List.of());
        MiniGamePageData page = MiniGamePages.get(server, pageId);
        Plan plan = plan(page, recruit(server, page));
        Status zone = plan.status() == Status.READY ? Status.ofZone(MiniGameArena.check(server, pageId)) : null;
        return zone == null ? plan : new Plan(zone, plan.spectators());
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
    private final int format;
    private final TeamDisposition disposition;
    private final List<UUID> players;
    private final List<UUID> spectators;
    private final @Nullable UUID observer;
    private final Map<UUID, MiniGameReturns.Return> returns = new LinkedHashMap<>();
    private final List<UUID> tasks = new ArrayList<>();
    private Phase phase = Phase.COUNTDOWN;
    private boolean closed = false;
    private final MiniGameArena arena = new MiniGameArena();
    private Map<UUID, Integer> places = Map.of();
    private @Nullable MiniGameResults results;

    private MiniGameTest(MinecraftServer server, UUID pageId, Plan plan, @Nullable ServerPlayerEntity starter) {
        this.server = server;
        this.pageId = pageId;
        this.format = plan.format();
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

    /** The index of the page's format played. */
    public int format() {
        return format;
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

    /** The name of the format played. */
    private Text formatName() {
        MiniGameFormat played = page().format(format);
        return played == null ? Text.empty() : played.name();
    }

    private void begin(Plan plan, int countdownSeconds) {
        // Where everyone stands now: where they go back at the end
        for (ServerPlayerEntity player : audience()) {
            if (observer != null && observer.equals(player.getUuid())) continue;
            returns.put(player.getUuid(), MiniGameReturns.Return.of(player));
        }
        plan.ignored().forEach((uuid, role) -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) player.sendMessage(Text.translatable("message.steveparty.minigame.test.ignored", role.text(), formatName())
                    .formatted(Formatting.GOLD), false);
        });
        // Away in this mini-game from now on (nobody in two mini-games at once): free pipes, the linked pipes closed
        for (UUID uuid : returns.keySet()) MiniGamePipes.enterParty(uuid, pageId, () -> !closed);
        send(new MiniGamePagePayloads.TestLabel(true, page().title()));
        MessageUtils.sendToPlayers(audience(), Text.translatable("message.steveparty.minigame.test.start", players.size(), formatName())
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
        send(new MiniGamePagePayloads.Preview(true, page(), format, seconds));
        playSoundToPlayers(onlinePlayers(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 1f, 1f);
        later(20, () -> countdown(seconds - 1));
    }

    /** The departure, as in a party: podiums and counters start again, everyone comes out of a pipe of its role. */
    private void depart() {
        // Its zone may still be put back from the round before
        arena.whenZoneFree(server, pageId, () -> !closed, this::audience, this::leave);
    }

    private void leave() {
        MiniGamePageData page = page();
        Podiums.resetForMiniGame(server, page);
        Map<UUID, MiniGamePipeLink> pipes = MiniGamePipes.distribute(page, disposition, players, spectators, new Random());
        // The round is played in its zone, by those who have a pipe to come out of: they leave their inventory at the door
        ZoneBubble.Refusal refusal = arena.begin(server, pageId, online(players, pipes), online(spectators, pipes), () -> !closed);
        if (refusal != ZoneBubble.Refusal.NONE) {
            Text why = arena.refusalText();
            if (why != null) MessageUtils.sendToPlayers(audience(), why.copy().formatted(Formatting.RED), MessageUtils.MessageType.CHAT);
            stop();
            return;
        }
        phase = Phase.PLAYING;
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
        // Its turn at the pipe came after the results: it would come out in an arena put back as built, with what it owns
        if (phase != Phase.PLAYING) return;
        if (player != null && !MiniGamePipes.emergeInRound(server, link, player, pageId)) {
            // No pipe to come out of any more: it stays where it is, with what it owns
            arena.leave(player);
            player.sendMessage(Text.translatable("message.steveparty.minigame.no_pipe").formatted(Formatting.RED), false);
        }
    }

    /** Those of {@code uuids} who are connected and have a pipe to come out of. */
    private List<ServerPlayerEntity> online(List<UUID> uuids, Map<UUID, MiniGamePipeLink> pipes) {
        List<ServerPlayerEntity> online = new ArrayList<>();
        for (UUID uuid : uuids) {
            ServerPlayerEntity player = pipes.containsKey(uuid) ? server.getPlayerManager().getPlayer(uuid) : null;
            if (player != null) online.add(player);
        }
        return online;
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
        // The results are read: the round is over, inventories and zone are given back
        arena.end();
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
        // Stopped during its round: what everyone owns first, then the way back
        arena.end();
        List<UUID> seated = new ArrayList<>(players);
        seated.addAll(spectators);
        seated.forEach(MiniGamePipes::leaveParty);
        // Those who left the server meanwhile are brought back when they come
        returns.forEach((uuid, back) -> MiniGameReturns.bringBack(server, uuid, back));
        returns.clear();
        TESTS.remove(pageId, this);
    }
}
