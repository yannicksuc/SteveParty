package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.entities.TokenBase;
import fr.lordfinn.steveparty.minigame.zone.MiniGameZone;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubble;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubbles;
import fr.lordfinn.steveparty.minigame.zone.ZoneForbidden;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The zone of a mini-game while one of its rounds is played: when the page has a zone ({@link MiniGamePageData#zone()}),
 * each round is played in a bubble of its own ({@link ZoneBubbles}), whatever plays it: out of a party
 * ({@link MiniGameTest}: the Mini-game Controller's « Play », the editor's test), the practice round of a party or its
 * real round ({@code MiniGamePartyStep}). A page without zone is played as ever, and so is every page when the bubble
 * is turned off on the server.
 * <p>
 * <b>Visits</b>: those who come by a mini-game pipe, out of any round ({@code MiniGamePipes}), play in a bubble too:
 * one per page, begun with its first visitor ({@link #visit}), each next one joining it; a visitor leaves it by a way
 * out ({@link #endVisit}), and the zone is put back once the last one is gone. A round of the page ends the visits
 * first (the visitors get back what they own and are sent out of the zone): the round waits for the zone as usual.
 * <p>
 * One arena per session, used round after round, always in this order:
 * <ol>
 *     <li>{@link #whenZoneFree}: the round before is over and its zone is whole again (waited for, when it is put
 *     back over several ticks);</li>
 *     <li>the session empties its podiums and counters, then {@link #begin}s the round with those it sends into
 *     the arena: the zone is remembered as the builder left it, and they leave their inventory at the door; then
 *     it sends them through their pipes;</li>
 *     <li>{@link #end}, as soon as the round is over and its results are read on the podiums: everyone gets back
 *     what it owns (what the round pays goes to a real inventory) and the zone is put back; only then are the
 *     players brought back where they stood. {@link #leave} for one who goes before the end (the exit pipe).</li>
 * </ol>
 * Nothing here runs every tick: a session left without its owner (a party controller broken during a round) is
 * found by a look every second, only while a round is played in a zone.
 */
public final class MiniGameArena {
    private static final int WAIT_POLL_TICKS = 2;
    private static final int WATCH_INTERVAL_TICKS = 20;
    /** The arenas with a round going on in a bubble. */
    private static final List<MiniGameArena> PLAYED = new ArrayList<>();

    /** What the last look at the zone of a page found of forbidden blocks, and when. */
    private record Seen(MiniGameZone zone, int tick, ZoneForbidden.@Nullable FoundBlock block) {
    }

    private static final Map<UUID, Seen> SEEN = new HashMap<>();

    /** The bubble of the visits of each page with visitors. */
    private static final Map<UUID, ZoneBubble> VISITS = new HashMap<>();
    /** The visitors found out of the zone at the last look: gone at the next one if they still are (see {@link #watchVisits}). */
    private static final java.util.Set<UUID> AWAY = new java.util.HashSet<>();

    private @Nullable ZoneBubble bubble;
    /** The participants of a round played without bubble put in adventure mode by the page's option. */
    private final java.util.Set<UUID> adventurers = new java.util.HashSet<>();
    private @Nullable MinecraftServer server;
    private BooleanSupplier stillOn = () -> true;
    private @Nullable UUID waitTask;
    private @Nullable Text refused;

    public static void initialize() {
        // What session members use whatever the rules of the bubble: the ready vote of the controller (its slots
        // and its option are locked for them), and the podiums they register on
        ZoneBubbles.allowUse(ModBlocks.MINI_GAME_CONTROLLER);
        for (var podium : List.of(ModBlocks.PODIUM, ModBlocks.GOLD_PODIUM, ModBlocks.SILVER_PODIUM, ModBlocks.BRONZE_PODIUM)) ZoneBubbles.allowUse(podium);
        // A party whose controller stands in the zone goes on through the round: it is not put back to what it was
        ZoneBubbles.keepLive(ModBlockEntities.PARTY_CONTROLLER_ENTITY);
        ZoneBubbles.keepLive(entity -> TokenBase.isToken(entity) && PartyControllerEntity.isTokenInRunningParty(entity.getUuid()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if ((PLAYED.isEmpty() && VISITS.isEmpty()) || server.getTicks() % WATCH_INTERVAL_TICKS != 0) return;
            for (MiniGameArena arena : new ArrayList<>(PLAYED)) {
                if (!arena.stillOn.getAsBoolean()) arena.end();
            }
            watchVisits(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PLAYED.clear();
            SEEN.clear();
            VISITS.clear();
            AWAY.clear();
        });
    }

    /**
     * The zone of a page's mini-game as the bubble takes it, null when the page has none or its « Remettre l'arène en
     * état » is off (it is then played without bubble, like a page without zone).
     */
    public static @Nullable MiniGameZone zoneOf(MinecraftServer server, UUID pageId) {
        MiniGamePageData page = MiniGamePages.find(server, pageId).orElse(null);
        return page == null || !page.restores() ? null : new MiniGameZone(page.zone().dimension(), page.zone().box());
    }

    /**
     * Whether a round of the page could be played in its zone now, as far as can be told before it begins.
     *
     * @return {@link ZoneBubble.Refusal#NONE} if it could, also when the page has no zone or the bubble is turned
     * off (it is then played without one); else why not
     */
    public static ZoneBubble.Refusal check(MinecraftServer server, UUID pageId) {
        MiniGameZone zone = zoneOf(server, pageId);
        if (zone == null) return ZoneBubble.Refusal.NONE;
        // Its own visits are no hindrance: a round ends them, and those of any other page over its zone
        ZoneBubble.Refusal place = ZoneBubbles.checkPlace(server, zone, VISITS.get(pageId));
        if (place == ZoneBubble.Refusal.OVERLAP && onlyVisitsOver(zone)) place = ZoneBubble.Refusal.NONE;
        if (place != ZoneBubble.Refusal.NONE) return playable(place);
        return forbiddenBlock(server, pageId) != null ? ZoneBubble.Refusal.FORBIDDEN_BLOCK : ZoneBubble.Refusal.NONE;
    }

    private static ZoneBubble.Refusal playable(ZoneBubble.Refusal refusal) {
        return refusal == ZoneBubble.Refusal.DISABLED ? ZoneBubble.Refusal.NONE : refusal;
    }

    /**
     * Runs {@code go} once the zone of the page is free of the round before: at once, unless it is still being put
     * back; then those of {@code audience} are told, and {@code go} runs when it is done, if {@code stillWanted}.
     */
    public void whenZoneFree(MinecraftServer server, @Nullable UUID pageId, BooleanSupplier stillWanted,
                             Supplier<Collection<ServerPlayerEntity>> audience, Runnable go) {
        cancelWait();
        if (pageId != null) endVisits(pageId);
        MiniGameZone zone = pageId == null ? null : zoneOf(server, pageId);
        if (zone != null) endVisitsOver(zone);
        if (zone == null || !ZoneBubbles.isBeingRestored(zone)) {
            go.run();
            return;
        }
        for (ServerPlayerEntity player : audience.get()) {
            player.sendMessage(Text.translatable("message.steveparty.zone_bubble.wait_restore").formatted(Formatting.GOLD), true);
        }
        waitFor(zone, stillWanted, go);
    }

    private void waitFor(MiniGameZone zone, BooleanSupplier stillWanted, Runnable go) {
        UUID task = UUID.randomUUID();
        waitTask = task;
        Steveparty.SCHEDULER.schedule(task, WAIT_POLL_TICKS, () -> {
            if (!task.equals(waitTask)) return;
            waitTask = null;
            if (!stillWanted.getAsBoolean()) return;
            if (ZoneBubbles.isBeingRestored(zone)) waitFor(zone, stillWanted, go);
            else go.run();
        });
    }

    private void cancelWait() {
        if (waitTask != null) Steveparty.SCHEDULER.cancel(waitTask);
        waitTask = null;
    }

    /**
     * A round begins: if the page has a zone, in a bubble, with those the session sends into the arena.
     *
     * @param stillOn false once the session is gone without ending its round: the bubble then ends by itself
     * @return {@link ZoneBubble.Refusal#NONE} when the round is played as it should (in its bubble, or without one
     * for a page without zone or a server without bubbles); else why the zone could not take it: no bubble began
     */
    public ZoneBubble.Refusal begin(MinecraftServer server, UUID pageId, Collection<ServerPlayerEntity> participants,
                                    Collection<ServerPlayerEntity> spectators, BooleanSupplier stillOn) {
        end();
        refused = null;
        endVisits(pageId);
        for (ServerPlayerEntity player : participants) endVisit(player);
        for (ServerPlayerEntity player : spectators) endVisit(player);
        MiniGameZone zone = zoneOf(server, pageId);
        boolean adventure = MiniGamePages.get(server, pageId).adventure();
        if (zone == null) return adventureWithoutBubble(adventure, participants, stillOn);
        endVisitsOver(zone);
        // visits just ended (or ended after the wait for the zone): put back now, they don't stand in the round's way
        ZoneBubbles.finishRestoring(zone);
        ZoneBubble begun = ZoneBubbles.begin(server, UUID.randomUUID(), zone, participants, spectators, new ZoneBubble.Options(adventure));
        if (!begun.isActive()) {
            refused = begun.refusalText();
            ZoneBubble.Refusal refusal = playable(begun.refusal());
            return refusal == ZoneBubble.Refusal.NONE ? adventureWithoutBubble(adventure, participants, stillOn) : refusal;
        }
        bubble = begun;
        this.stillOn = stillOn;
        PLAYED.add(this);
        return ZoneBubble.Refusal.NONE;
    }

    /** Why the zone could not take the round {@link #begin} was last asked for (with the forbidden block or entity and where it is), null if it could. */
    public @Nullable Text refusalText() {
        return refused;
    }

    /**
     * The block of the page's zone the server does not allow in a zone, as far as its loaded chunks show; null for
     * none. Asked again and again by the screens that say whether the mini-game can be played: the zone is looked at
     * once a second at most (a block just put there is seen when the round begins anyway).
     */
    public static ZoneForbidden.@Nullable FoundBlock forbiddenBlock(MinecraftServer server, UUID pageId) {
        MiniGameZone zone = zoneOf(server, pageId);
        if (zone == null) return null;
        int now = server.getTicks();
        Seen seen = SEEN.get(pageId);
        if (seen != null && seen.zone().equals(zone) && now >= seen.tick() && now - seen.tick() < WATCH_INTERVAL_TICKS) return seen.block();
        ZoneForbidden.FoundBlock block = ZoneBubbles.forbiddenBlock(server, zone);
        SEEN.put(pageId, new Seen(zone, now, block));
        return block;
    }

    // ------------------------------------------------------------------ visits

    /**
     * {@code player} is about to come to the mini-game of {@code pageId} by a mini-game pipe, out of any round: when
     * the page has a zone, he joins the bubble of its visits (begun now if it has none).
     *
     * @return false if the zone can't take him (a round is played in it, it is being put back, it holds a forbidden
     * block...): he is told why and should not go
     */
    public static boolean visit(MinecraftServer server, UUID pageId, ServerPlayerEntity player) {
        MiniGameZone zone = zoneOf(server, pageId);
        ZoneBubble bubble = VISITS.get(pageId);
        if (bubble != null && (!bubble.isActive() || !bubble.zone().equals(zone))) {
            endVisits(pageId);
            bubble = null;
        }
        if (zone == null) return true;
        AWAY.remove(player.getUuid());
        if (bubble != null) {
            if (!bubble.isMember(player.getUuid())) {
                endVisit(player);
                bubble.addParticipant(player);
            }
            return true;
        }
        endVisit(player);
        ZoneBubble begun = ZoneBubbles.begin(server, UUID.randomUUID(), zone, List.of(player), List.of(),
                new ZoneBubble.Options(MiniGamePages.get(server, pageId).adventure()));
        if (begun.isActive()) {
            VISITS.put(pageId, begun);
            return true;
        }
        if (begun.refusal() == ZoneBubble.Refusal.DISABLED) return true;
        Text why = begun.refusalText();
        player.sendMessage(Text.translatable("message.steveparty.zone_bubble.visit_refused", why == null ? Text.empty() : why)
                .formatted(Formatting.RED), true);
        return false;
    }

    /** {@code player} leaves the visits he is in (a way out): he gets back what he owns; the last one out puts the zone back. */
    public static void endVisit(ServerPlayerEntity player) {
        AWAY.remove(player.getUuid());
        for (Map.Entry<UUID, ZoneBubble> entry : new ArrayList<>(VISITS.entrySet())) {
            ZoneBubble bubble = entry.getValue();
            if (!bubble.isMember(player.getUuid())) continue;
            bubble.removePlayer(player);
            if (bubble.members().isEmpty()) {
                VISITS.remove(entry.getKey());
                bubble.end();
            }
        }
    }

    /** The visits of the page end (a round of it begins): the visitors get back what they own, the zone is put back. */
    public static void endVisits(UUID pageId) {
        ZoneBubble bubble = VISITS.remove(pageId);
        if (bubble == null) return;
        AWAY.removeAll(bubble.members());
        MiniGamePipes.forgetVisits(pageId);
        bubble.end();
    }

    /**
     * The visits of every page whose zone {@code zone} overlaps end: a round takes the place before them, whatever page
     * they visit (another page over the same place, an unlinked copy of the round's page), else they would hold it up.
     */
    private static void endVisitsOver(MiniGameZone zone) {
        for (Map.Entry<UUID, ZoneBubble> entry : new ArrayList<>(VISITS.entrySet())) {
            if (entry.getValue().zone().intersects(zone)) endVisits(entry.getKey());
        }
    }

    /** @return true if what is in session over {@code zone} is only visits (which a round ends) */
    private static boolean onlyVisitsOver(MiniGameZone zone) {
        for (ZoneBubble other : ZoneBubbles.all()) {
            if (other.zone().intersects(zone) && !VISITS.containsValue(other)) return false;
        }
        return true;
    }

    /**
     * Once a second: a visitor out of the zone (not travelling through pipes) at two looks in a row never came in, or was
     * sent away: he leaves the visits. A bubble left empty (its last visitor gone) ends.
     */
    private static void watchVisits(MinecraftServer server) {
        java.util.Set<UUID> away = new java.util.HashSet<>();
        for (Map.Entry<UUID, ZoneBubble> entry : new ArrayList<>(VISITS.entrySet())) {
            ZoneBubble bubble = entry.getValue();
            for (UUID member : List.copyOf(bubble.members())) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(member);
                if (player == null || fr.lordfinn.steveparty.blocks.custom.pipe.PipeTravel.isTravelling(player)
                        || (bubble.zone().isIn(player.getWorld()) && bubble.zone().contains(player.getBlockPos()))) continue;
                if (!AWAY.contains(member)) {
                    away.add(member);
                    continue;
                }
                bubble.removePlayer(player);
                MiniGamePipes.forgetVisit(member);
            }
            if (bubble.members().isEmpty() || !bubble.isActive()) {
                VISITS.remove(entry.getKey());
                bubble.end();
            }
        }
        AWAY.clear();
        AWAY.addAll(away);
    }

    /**
     * A round played without bubble: its participants play in adventure mode when the page says so (their own mode
     * given back by {@link #leave} and {@link #end}, see {@link MiniGameAdventure}).
     */
    private ZoneBubble.Refusal adventureWithoutBubble(boolean adventure, Collection<ServerPlayerEntity> participants, BooleanSupplier stillOn) {
        if (!adventure || participants.isEmpty()) return ZoneBubble.Refusal.NONE;
        for (ServerPlayerEntity player : participants) if (MiniGameAdventure.apply(player)) adventurers.add(player.getUuid());
        this.stillOn = stillOn;
        server = participants.iterator().next().getServer();
        PLAYED.add(this);
        return ZoneBubble.Refusal.NONE;
    }

    /** A player leaves the round before its end: it gets back what it owns, and may then be taken out of the zone. */
    public void leave(ServerPlayerEntity player) {
        if (bubble != null) bubble.removePlayer(player);
        if (adventurers.remove(player.getUuid())) MiniGameAdventure.restore(player);
    }

    /**
     * The round is over (or stopped): everyone gets back what it owns and the zone is put back as it was. Nothing
     * to do when no round is played in a bubble.
     */
    public void end() {
        cancelWait();
        PLAYED.remove(this);
        if (!adventurers.isEmpty() && server != null) {
            for (UUID id : adventurers) MiniGameAdventure.restore(server.getPlayerManager().getPlayer(id));
        }
        adventurers.clear();
        if (bubble == null) return;
        bubble.end();
        bubble = null;
    }
}
