package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.minigame.MiniGameArena;
import fr.lordfinn.steveparty.minigame.MiniGameFreeze;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeLink;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import fr.lordfinn.steveparty.minigame.MiniGameReturns;
import fr.lordfinn.steveparty.minigame.MiniGameRoundCards;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import fr.lordfinn.steveparty.minigame.MiniGameTest;
import fr.lordfinn.steveparty.podium.Podiums;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The players' trips of a mini-game: out of the pipes linked to its page for each round (in its zone when the page
 * has one, see {@link MiniGameArena}), and back where they stood before it once it is over.
 */
final class MiniGameDeparture {
    private final MiniGamePartyStep step;
    /**
     * The zone of the mini-game, round after round: each practice round and the real round are played in a bubble
     * of their own when the page has a zone ({@link MiniGameArena}). Not saved: a round a server restart
     * interrupted goes on without one (the server put its zone back when it stopped).
     */
    private final MiniGameArena arena = new MiniGameArena();
    /** Counts the departures: one that waited for its zone only leaves if no other was asked meanwhile. Not saved. */
    private int departures;
    /** The chat already told which mini-game starts: said once, however many times the players leave for it. Not saved. */
    private boolean toldInChat;
    /** The players were told why this mini-game is played without the protection of its zone. Not saved. */
    private boolean unprotectedTold;
    /** The players were told how the practice round's vote works. Not saved. */
    private boolean practiceTold;

    MiniGameDeparture(MiniGamePartyStep step) {
        this.step = step;
    }

    /** The step starts (again): nothing told yet. */
    void reset() {
        toldInChat = false;
        unprotectedTold = false;
        practiceTold = false;
    }

    /** The round is over: what everyone owns and the zone are given back, a departure waiting for its zone is called off. */
    void endRound() {
        departures++;
        arena.end();
    }

    /** The round's results are read: what everyone owns and the zone are given back until the next round. */
    void endArena() {
        arena.end();
    }

    /**
     * Everyone leaves for the mini-game: every participant comes out of a pipe of its role, the audience out of the
     * spectators pipes (see {@link MiniGamePipes#distribute}), all at once (those who share a pipe too); then the
     * players stand still for the countdown of {@link MiniGameFreeze}.
     *
     * @param round {@link MiniGamePartyStep.Phase#PRACTICE} or {@link MiniGamePartyStep.Phase#PLAYING}
     */
    void sendOut(PartyControllerEntity controller, MiniGamePartyStep.Phase round) {
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
        if (page != null) MiniGameTest.stop(page.id());
        // The zone may still be put back from the round before: the departure waits for it
        arena.whenZoneFree(world.getServer(), page == null ? null : page.id(), () -> departure == departures && step.isStillActive(controller),
                () -> step.screens.previewAudience(controller), () -> leave(controller, round));
    }

    /** The departure itself, once the zone of the mini-game is free. */
    private void leave(PartyControllerEntity controller, MiniGamePartyStep.Phase round) {
        // A new mini-game: its podiums are emptied and its counters go back to 0 (before it is being played)
        if (controller.getWorld() instanceof ServerWorld world) {
            MiniGamePageData page = MiniGamePages.of(world.getServer(), MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue));
            if (page != null) Podiums.resetForMiniGame(world.getServer(), page);
        }
        step.phase = round;
        step.screens.hidePreview(controller);
        if (controller.getWorld() instanceof ServerWorld world) {
            MinecraftServer server = world.getServer();
            MiniGamePageData page = MiniGamePages.of(server, MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue));
            TeamDisposition teams = MiniGamesCatalogueItem.getCurrentMiniGameTeamDisposition(controller.catalogue);
            List<UUID> order = step.orderedParticipants(controller);
            List<UUID> spectators = controller.getInterestedPlayersEntities().stream().map(ServerPlayerEntity::getUuid)
                    .filter(uuid -> !step.participants.contains(uuid)).toList();
            Map<UUID, MiniGamePipeLink> pipes = page == null ? Map.of() : MiniGamePipes.distribute(page, teams, order, spectators, new Random());
            for (UUID uuid : order) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null && !pipes.containsKey(uuid)) {
                    MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.minigame.no_pipe").formatted(Formatting.RED),
                            MessageUtils.MessageType.CHAT);
                }
            }
            if (page != null) beginInZone(controller, server, page, pipes);
            List<ServerPlayerEntity> out = new ArrayList<>();
            pipes.forEach((uuid, link) -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player == null) return;
                step.returnPositions.putIfAbsent(uuid, MiniGameReturns.Return.of(player));
                seat(controller, uuid, page == null ? null : MiniGameRoundCards.of(page, teams, pipes, step.participants, uuid));
                if (comeOut(server, link, uuid, page == null ? null : page.id()) && step.participants.contains(uuid)) out.add(player);
            });
            // Everyone is out at once: the players stand still while the chat tells the mini-game (the first time), then
            // for the countdown, then « Go! »
            MiniGameFreeze.Pace pace = toldInChat ? MiniGameFreeze.Pace.AGAIN : MiniGameFreeze.Pace.FIRST;
            if (controller.isFastParty()) pace = pace.fast();
            toldInChat = true;
            // The first practice round: at « Go! », how to say one is ready
            Runnable onGo = round == MiniGamePartyStep.Phase.PRACTICE && !practiceTold ? () -> step.tellPractice(controller) : null;
            if (round == MiniGamePartyStep.Phase.PRACTICE) practiceTold = true;
            MiniGameFreeze.start(server, out, step.screens.previewAudience(controller), step.screens.startName(controller, page), page, pace,
                    () -> step.isStillActive(controller) && step.isOnArena() && !step.practiceOver, onGo);
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
            if (player != null) (step.participants.contains(uuid) ? players : watching).add(player);
        }
        arena.begin(server, page.id(), players, watching, () -> step.isStillActive(controller));
        Text why = arena.refusalText();
        if (why == null || unprotectedTold) return;
        unprotectedTold = true;
        MessageUtils.sendToPlayers(step.screens.previewAudience(controller), Text.translatable("message.steveparty.zone_bubble.party_unprotected", why)
                .formatted(Formatting.RED), MessageUtils.MessageType.CHAT);
    }

    /** {@code uuid} is away in this mini-game (free pipes, the linked pipes closed) for as long as it is this party's step. */
    void seat(PartyControllerEntity controller, UUID uuid) {
        seat(controller, uuid, null);
    }

    /** @param card his card at the top of the screen (null: the one he has) */
    void seat(PartyControllerEntity controller, UUID uuid, MiniGamePagePayloads.@Nullable RoundCard card) {
        UUID page = MiniGamePages.idOf(MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue));
        MiniGamePipes.enterParty(uuid, page == null ? MiniGamePageData.NO_ID : page, () -> step.isStillActive(controller), card);
    }

    /** @return true if {@code uuid} comes out of the pipe */
    private boolean comeOut(MinecraftServer server, MiniGamePipeLink link, UUID uuid, @Nullable UUID pageId) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player == null || !step.isOnArena() || step.practiceOver) return false;
        if (MiniGamePipes.emergeInRound(server, link, player, pageId)) return true;
        // No pipe to come out of any more: it stays where it is, with what it owns
        arena.leave(player);
        MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.minigame.no_pipe").formatted(Formatting.RED),
                MessageUtils.MessageType.CHAT);
        return false;
    }

    /** {@code uuid} left the party: out of the arena with what he owns, back where he stood before the mini-game. */
    void playerLeft(PartyControllerEntity controller, UUID uuid) {
        MiniGamePipes.leaveParty(uuid);
        MinecraftServer server = controller.getWorld() == null ? null : controller.getWorld().getServer();
        if (server == null) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            MiniGameFreeze.release(player);
            arena.leave(player);
        }
        MiniGameReturns.Return back = step.returnPositions.remove(uuid);
        if (back != null) MiniGameReturns.bringBack(server, uuid, back);
    }

    /**
     * Brings everyone sent to the mini-game back where they stood before it: now if they are online, else when they
     * come (see {@link MiniGameReturns}).
     */
    void returnPlayers(PartyControllerEntity controller) {
        step.participants.forEach(MiniGamePipes::leaveParty);
        if (step.returnPositions.isEmpty() || !(controller.getWorld() instanceof ServerWorld world)) return;
        for (Map.Entry<UUID, MiniGameReturns.Return> entry : step.returnPositions.entrySet()) {
            MiniGamePipes.leaveParty(entry.getKey());
            MiniGameReturns.bringBack(world.getServer(), entry.getKey(), entry.getValue());
        }
        step.returnPositions.clear();
        controller.markDirty();
    }
}
