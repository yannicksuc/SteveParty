package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static fr.lordfinn.steveparty.utils.SoundsUtils.playSoundToPlayers;

/**
 * How a mini-game is drawn: the teams the board makes (the kind of tile each token stands on, see
 * {@link TeamDispositionGenerator}), the pages of the catalogue those teams can play, and the roulette that runs
 * through them before it stops on one.
 */
final class MiniGameRoulette {
    private final MiniGamePartyStep step;
    private UUID rouletteTaskId = null;

    MiniGameRoulette(MiniGamePartyStep step) {
        this.step = step;
    }

    void cancel() {
        if (rouletteTaskId != null) {
            Steveparty.SCHEDULER.cancel(rouletteTaskId);
            rouletteTaskId = null;
        }
    }

    /** Runs the roulette through {@code applicableMiniGames} (shuffled): it slows down and stops on one. */
    void spin(List<ItemStack> applicableMiniGames, PartyControllerEntity controller, List<ServerPlayerEntity> players) {
        playIterationEffect(new AtomicInteger(0), applicableMiniGames, controller, players);
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
                        if (step.isStillActive(partyControllerEntity))
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
        step.miniGameChosen = true;
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
        step.chosenPageSlot = 0;
        for (int slot = 0; slot < pages.size(); slot++) {
            if (ItemStack.areEqual(pages.get(slot), chosenMiniGame)) {
                step.chosenPageSlot = slot + 1;
                break;
            }
        }
        partyControllerEntity.markDirty();
        // The mini-game drawn is shown on its card, until the players leave for it
        step.screens.showPreview(partyControllerEntity, 0);

        // Play a celebratory sound for selection
        playSoundToPlayers(players, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1f, 1);
        step.onMiniGameChosen(partyControllerEntity);
    }

    private static Text formatMiniGameMessage(ItemStack chosenMiniGame, boolean isFinal) {
        int whiteColor = isFinal ? 0xFFA500 : 0xFFFFFF; // Make the final text gold/orange

        return Text.literal("🎲 ")
                .styled(style -> style.withColor(0xFFA500)) // Orange start
                .append(chosenMiniGame.getName().copy()
                        .styled(style -> style.withColor(whiteColor).withBold(isFinal))) // White or Orange if final
                .append(Text.literal(" 🎲")
                        .styled(style -> style.withColor(0xFFA500))); // Orange end
    }

    // ---------------------------------------------------------------- the teams and the pages they can play

    /** The players with a token in the world, in turn order, each with the kind of tile its token stands on. */
    static List<TeamDispositionGenerator.Seat> seats(PartyControllerEntity controller, ServerWorld world) {
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

    private static ABoardSpaceBehavior.Status tokenStatus(MobEntity token) {
        BoardSpaceBlockEntity boardSpaceEntity = BoardSpaces.boardSpaceOf(token);
        if (boardSpaceEntity == null) return ABoardSpaceBehavior.Status.NEUTRAL;
        ItemStack stack = boardSpaceEntity.getActiveCartridgeItemStack();
        ABoardSpaceBehavior behavior = boardSpaceEntity.getBoardSpaceBehavior(stack);
        return behavior == null ? ABoardSpaceBehavior.Status.NEUTRAL : behavior.getStatus(boardSpaceEntity, stack);
    }

    static TeamDisposition chooseRandomDisposition(Map<TeamDisposition, List<ItemStack>> miniGamesToTeamDispositions) {
        List<TeamDisposition> teamDispositions = new ArrayList<>(miniGamesToTeamDispositions.keySet());
        Random random = new Random();
        return teamDispositions.get(random.nextInt(teamDispositions.size()));
    }

    /** See {@link MiniGamePartyStep#assignMiniGamesToTeamDispositions}. */
    static Map<TeamDisposition, List<ItemStack>> assignMiniGamesToTeamDispositions(
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

    /** See {@link MiniGamePartyStep#pagePlayable}. */
    static boolean pagePlayable(MinecraftServer server, ItemStack page, TeamDisposition disposition) {
        MiniGamePageData data = MiniGamePages.of(server, page);
        return data != null && data.isPlayable(counts(disposition));
    }

    /** See {@link MiniGamePartyStep#arrange}. */
    static TeamDisposition arrange(MiniGamePageData page, TeamDisposition disposition) {
        List<Integer> counts = counts(disposition);
        MiniGameFormat format = page.format(page.formatFor(counts));
        if (format == null || format.kind() != MiniGameFormat.Kind.TEAMS) return disposition;
        int[] order = format.assignment(counts);
        if (order == null) return disposition;
        List<Set<UUID>> groups = new ArrayList<>();
        for (Set<UUID> team : disposition.teams()) if (!team.isEmpty()) groups.add(team);
        List<Set<UUID>> sides = new ArrayList<>(List.of(Set.of(), Set.of(), Set.of(), Set.of()));
        for (int side = 0; side < order.length; side++) sides.set(side, new LinkedHashSet<>(groups.get(order[side])));
        return new TeamDisposition(sides.get(0), sides.get(1), sides.get(2), sides.get(3));
    }

    /** Each player is told which team he plays, in its colour (the colour of the pipes he will come out of). */
    private static void tellSides(MinecraftServer server, MiniGamePageData page, TeamDisposition teams) {
        if (teams.isFreeForAll()) return;
        for (int team = 0; team < 4; team++) {
            MiniGamePipeRole role = MiniGamePipeRole.ofTeam(team);
            for (UUID uuid : teams.teams().get(team)) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player == null) continue;
                player.sendMessage(Text.translatable("message.steveparty.minigame.your_side",
                        role.text().copy().styled(style -> style.withColor(role.color()).withBold(true))), false);
            }
        }
    }

    /** See {@link MiniGamePartyStep#counts}. */
    static List<Integer> counts(@Nullable TeamDisposition disposition) {
        List<Integer> counts = new ArrayList<>();
        if (disposition == null) return counts;
        for (Set<UUID> team : disposition.teams()) if (!team.isEmpty()) counts.add(team.size());
        return counts;
    }
}
