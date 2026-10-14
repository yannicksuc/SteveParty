package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * The teams of a Mini-game space's mini-game, by its mode ({@link MiniGameMode}):
 * <ul>
 *     <li>VS: the player who landed against all the others; when no page of the catalogue plays that, the board's
 *     teams (never free for all);</li>
 *     <li>free for all: everyone for themselves;</li>
 *     <li>the player's choice: he is asked in the chat ([VS] [Free for all], or the command
 *     {@value #COMMAND} {@code vs|ffa}); after {@value #CHOICE_SECONDS} seconds, or without him, it is drawn.</li>
 * </ul>
 * When nothing of the catalogue fits the mode, the players are told and the mini-game is drawn as a card's.
 */
public final class MiniGameModeChoice {
    public static final String COMMAND = "/steveparty minigame_mode";
    static final int CHOICE_SECONDS = 20;

    /** The mini-games waiting for their player's choice, by player. */
    private static final Map<UUID, Pending> PENDING = new LinkedHashMap<>();

    private record Pending(MiniGamePartyStep step, PartyControllerEntity controller, UUID task) {
    }

    private MiniGameModeChoice() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("steveparty").then(literal("minigame_mode")
                        .then(literal("vs").executes(context -> choose(context.getSource().getPlayerOrThrow(), MiniGameMode.VS) ? 1 : 0))
                        .then(literal("ffa").executes(context -> choose(context.getSource().getPlayerOrThrow(), MiniGameMode.FFA) ? 1 : 0)))));
    }

    /** The player who landed is asked for the mode; the others are told he chooses. */
    static void ask(MiniGamePartyStep step, PartyControllerEntity controller, ServerWorld world) {
        cancel(step);
        ServerPlayerEntity lander = step.lander == null ? null : world.getServer().getPlayerManager().getPlayer(step.lander);
        if (lander == null) {
            // Nobody to ask: drawn
            decide(step, controller, null);
            return;
        }
        UUID task = UUID.randomUUID();
        PENDING.put(lander.getUuid(), new Pending(step, controller, task));
        lander.sendMessage(Text.translatable("message.steveparty.minigame_mode.ask", button(MiniGameMode.VS), button(MiniGameMode.FFA)), false);
        List<ServerPlayerEntity> others = new ArrayList<>(controller.getInterestedPlayersEntities());
        others.remove(lander);
        MessageUtils.sendToPlayers(others, Text.translatable("message.steveparty.minigame_mode.chooses", lander.getDisplayName()),
                MessageUtils.MessageType.CHAT);
        Steveparty.SCHEDULER.schedule(task, CHOICE_SECONDS * 20, () -> {
            Pending pending = PENDING.get(lander.getUuid());
            if (pending != null && pending.task().equals(task)) {
                PENDING.remove(lander.getUuid());
                decide(step, controller, null);
            }
        });
    }

    /**
     * {@code player} chooses the mode of the mini-game he landed on.
     *
     * @return false if he has nothing to choose
     */
    public static boolean choose(ServerPlayerEntity player, MiniGameMode mode) {
        Pending pending = PENDING.remove(player.getUuid());
        if (pending == null) return false;
        Steveparty.SCHEDULER.cancel(pending.task());
        decide(pending.step(), pending.controller(), mode);
        return true;
    }

    /** @return true while {@code player} has a mode to choose */
    public static boolean isAsked(UUID player) {
        return PENDING.containsKey(player);
    }

    /** The mini-game ends (or starts again): its question is withdrawn. */
    static void cancel(MiniGamePartyStep step) {
        PENDING.entrySet().removeIf(entry -> {
            if (entry.getValue().step() != step) return false;
            Steveparty.SCHEDULER.cancel(entry.getValue().task());
            return true;
        });
    }

    /** The mode is {@code mode} (null: drawn): told to everyone, and the mini-game starts. */
    private static void decide(MiniGamePartyStep step, PartyControllerEntity controller, @Nullable MiniGameMode mode) {
        if (!step.isStillActive(controller)) return;
        if (mode == null) mode = new Random().nextBoolean() ? MiniGameMode.VS : MiniGameMode.FFA;
        step.mode = mode;
        controller.markDirty();
        MessageUtils.sendToPlayers(controller.getInterestedPlayersEntities(),
                Text.translatable("message.steveparty.minigame_mode.chosen", modeText(mode).formatted(Formatting.GOLD)),
                MessageUtils.MessageType.CHAT);
        step.start(controller);
    }

    /** « [VS] », « [Free for all] »: chooses that mode. */
    private static MutableText button(MiniGameMode mode) {
        return Text.translatable("message.steveparty.minigame_mode.button", modeText(mode))
                .styled(style -> style.withColor(Formatting.GOLD).withUnderline(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, COMMAND + " " + mode.id()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Text.translatable("gui.steveparty.cartridge_menu.minigame.mode." + mode.id() + ".tooltip"))));
    }

    private static MutableText modeText(MiniGameMode mode) {
        return Text.translatable("gui.steveparty.cartridge_menu.minigame.mode." + mode.id());
    }

    // ---------------------------------------------------------------- the teams of each mode

    /**
     * The ways to make the teams of {@code step}'s mini-game, each with the pages of {@code miniGames} it can play: by
     * its mode, or the board's when the mode fits nothing (the players are then told).
     */
    static Map<TeamDisposition, List<ItemStack>> dispositions(MiniGamePartyStep step, PartyControllerEntity controller,
                                                              List<TeamDispositionGenerator.Seat> seats, List<ItemStack> miniGames,
                                                              MinecraftServer server) {
        Map<TeamDisposition, List<ItemStack>> board = MiniGameRoulette.assignMiniGamesToTeamDispositions(seats, miniGames, server);
        if (step.mode == MiniGameMode.ANY || step.mode == MiniGameMode.CHOICE) return board;
        Map<TeamDisposition, List<ItemStack>> ofMode = ofMode(step.mode, step.lander, seats, miniGames, board, server);
        if (!ofMode.isEmpty()) return ofMode;
        MessageUtils.sendToPlayers(controller.getInterestedPlayersEntities(),
                Text.translatable("message.steveparty.minigame_mode.none_fits", modeText(step.mode)), MessageUtils.MessageType.CHAT);
        return board;
    }

    /** The ways of {@code mode} (empty when no page plays them). */
    public static Map<TeamDisposition, List<ItemStack>> ofMode(MiniGameMode mode, @Nullable UUID lander, List<TeamDispositionGenerator.Seat> seats,
                                                        List<ItemStack> miniGames, Map<TeamDisposition, List<ItemStack>> board,
                                                        MinecraftServer server) {
        List<UUID> players = seats.stream().map(TeamDispositionGenerator.Seat::player).toList();
        Map<TeamDisposition, List<ItemStack>> result = new LinkedHashMap<>();
        if (mode == MiniGameMode.FFA) {
            put(result, TeamDisposition.freeForAll(players), miniGames, server);
            return result;
        }
        // VS: the one who landed against the others
        if (lander != null && players.contains(lander) && players.size() >= 2) {
            Set<UUID> others = new LinkedHashSet<>(players);
            others.remove(lander);
            put(result, new TeamDisposition(new LinkedHashSet<>(Set.of(lander)), others), miniGames, server);
            if (!result.isEmpty()) return result;
        }
        // Else the board's teams, never everyone for themselves
        board.forEach((disposition, pages) -> {
            if (!disposition.isFreeForAll()) result.put(disposition, pages);
        });
        return result;
    }

    private static void put(Map<TeamDisposition, List<ItemStack>> into, TeamDisposition disposition, List<ItemStack> miniGames, MinecraftServer server) {
        List<ItemStack> pages = new ArrayList<>();
        for (ItemStack page : miniGames) if (MiniGameRoulette.pagePlayable(server, page, disposition)) pages.add(page);
        if (!pages.isEmpty()) into.put(disposition, pages);
    }
}
