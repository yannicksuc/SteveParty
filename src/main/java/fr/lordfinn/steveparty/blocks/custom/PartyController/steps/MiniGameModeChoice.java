package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import com.mojang.brigadier.arguments.StringArgumentType;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.items.custom.cartridges.MiniGameCartridgeItem;
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

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * The teams of a Mini-game space's mini-game, by its format ({@link MiniGameMode}):
 * <ul>
 *     <li>1 v 1: the player who landed against one other player, the one he picks in the chat ([Alex] [Sam], or the
 *     command {@value #OPPONENT_COMMAND} {@code <uuid>}) or one drawn at random; the others watch;</li>
 *     <li>1 against all: the player who landed against all the others;</li>
 *     <li>free for all: everyone for themselves;</li>
 *     <li>the board's teams (2 v 2, 1 v 3...), never everyone for themselves;</li>
 *     <li>several formats on: the player who landed picks one in the chat ([1 v 1] [Free for all], or the command
 *     {@value #COMMAND} {@code <format>}).</li>
 * </ul>
 * A question in the chat waits {@value #CHOICE_SECONDS} seconds; without an answer, or without him, it is drawn. When
 * nothing the mini-game may play fits the format, the players are told and the mini-game is drawn as a card's.
 */
public final class MiniGameModeChoice {
    public static final String COMMAND = "/steveparty minigame_mode";
    public static final String OPPONENT_COMMAND = "/steveparty minigame_opponent";
    static final int CHOICE_SECONDS = 20;

    /** The mini-games waiting for their player's answer, by player. */
    private static final Map<UUID, Pending> PENDING = new LinkedHashMap<>();

    /** A question: the format ({@code opponents} null) or the opponent of a 1 v 1. */
    private record Pending(MiniGamePartyStep step, PartyControllerEntity controller, UUID task, @Nullable List<UUID> opponents) {
    }

    private MiniGameModeChoice() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("steveparty")
                        .then(literal("minigame_mode").then(argument("format", StringArgumentType.word())
                                .executes(context -> choose(context.getSource().getPlayerOrThrow(),
                                        MiniGameMode.byName(StringArgumentType.getString(context, "format"))) ? 1 : 0)))
                        .then(literal("minigame_opponent").then(argument("player", StringArgumentType.word())
                                .executes(context -> chooseOpponent(context.getSource().getPlayerOrThrow(),
                                        StringArgumentType.getString(context, "player")) ? 1 : 0)))));
    }

    /**
     * Before the draw: the question {@code step} still waits for, asked in the chat (the format among several, then the
     * opponent of a 1 v 1 picked by the player who landed), or a 1 v 1's opponent drawn.
     *
     * @return true if a question was asked (the mini-game starts once answered)
     */
    static boolean ask(MiniGamePartyStep step, PartyControllerEntity controller, ServerWorld world, List<UUID> players) {
        cancel(step);
        if (step.mode == MiniGameMode.CHOICE) {
            if (step.choices.size() <= 1) {
                step.mode = step.choices.isEmpty() ? MiniGameMode.ANY : step.choices.getFirst();
            } else {
                ServerPlayerEntity lander = online(world, step.lander);
                if (lander == null) {
                    step.mode = draw(step.choices);
                } else {
                    MutableText buttons = Text.empty();
                    for (MiniGameMode mode : step.choices) buttons.append(" ").append(formatButton(mode));
                    ask(step, controller, lander, null, Text.translatable("message.steveparty.minigame_mode.ask", buttons),
                            Text.translatable("message.steveparty.minigame_mode.chooses", lander.getDisplayName()));
                    return true;
                }
            }
            controller.markDirty();
        }
        if (step.mode.isDuel() && (step.opponent == null || !players.contains(step.opponent))) {
            List<UUID> others = new ArrayList<>(players);
            others.remove(step.lander);
            if (step.lander == null || !players.contains(step.lander) || others.isEmpty()) return false;
            ServerPlayerEntity lander = step.mode == MiniGameMode.DUEL ? online(world, step.lander) : null;
            List<UUID> online = others.stream().filter(uuid -> online(world, uuid) != null).toList();
            if (lander == null || online.size() < 2) {
                // Drawn (or only one to pick: him)
                step.opponent = draw(online.isEmpty() ? others : online);
                controller.markDirty();
                return false;
            }
            MutableText buttons = Text.empty();
            for (UUID uuid : online) buttons.append(" ").append(opponentButton(online(world, uuid)));
            ask(step, controller, lander, online, Text.translatable("message.steveparty.minigame_opponent.ask", buttons),
                    Text.translatable("message.steveparty.minigame_opponent.chooses", lander.getDisplayName()));
            return true;
        }
        return false;
    }

    /** {@code lander} is asked {@code question}; the others are told he chooses; drawn after the delay. */
    private static void ask(MiniGamePartyStep step, PartyControllerEntity controller, ServerPlayerEntity lander,
                            @Nullable List<UUID> opponents, Text question, Text told) {
        UUID task = UUID.randomUUID();
        PENDING.put(lander.getUuid(), new Pending(step, controller, task, opponents));
        lander.sendMessage(question, false);
        List<ServerPlayerEntity> others = new ArrayList<>(controller.getInterestedPlayersEntities());
        others.remove(lander);
        MessageUtils.sendToPlayers(others, told, MessageUtils.MessageType.CHAT);
        Steveparty.SCHEDULER.schedule(task, CHOICE_SECONDS * 20, () -> {
            Pending pending = PENDING.get(lander.getUuid());
            if (pending != null && pending.task().equals(task)) {
                PENDING.remove(lander.getUuid());
                if (opponents == null) decide(step, controller, null);
                else decideOpponent(step, controller, opponents, null);
            }
        });
    }

    /**
     * {@code player} picks the format of the mini-game he landed on.
     *
     * @return false if he has nothing to pick, or not that
     */
    public static boolean choose(ServerPlayerEntity player, MiniGameMode mode) {
        Pending pending = PENDING.get(player.getUuid());
        if (pending == null || pending.opponents() != null || !pending.step().choices.contains(mode)) return false;
        PENDING.remove(player.getUuid());
        Steveparty.SCHEDULER.cancel(pending.task());
        decide(pending.step(), pending.controller(), mode);
        return true;
    }

    /**
     * {@code player} picks the opponent of his 1 v 1 (his UUID).
     *
     * @return false if he has nothing to pick, or not him
     */
    public static boolean chooseOpponent(ServerPlayerEntity player, String opponent) {
        Pending pending = PENDING.get(player.getUuid());
        if (pending == null || pending.opponents() == null) return false;
        UUID chosen;
        try {
            chosen = UUID.fromString(opponent);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!pending.opponents().contains(chosen)) return false;
        PENDING.remove(player.getUuid());
        Steveparty.SCHEDULER.cancel(pending.task());
        decideOpponent(pending.step(), pending.controller(), pending.opponents(), chosen);
        return true;
    }

    /** @return true while {@code player} has a format or an opponent to pick */
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

    /** The format is {@code mode} (null: drawn): told to everyone, and the mini-game starts (or asks the opponent). */
    private static void decide(MiniGamePartyStep step, PartyControllerEntity controller, @Nullable MiniGameMode mode) {
        if (!step.isStillActive(controller)) return;
        if (mode == null) mode = draw(step.choices);
        step.mode = mode;
        controller.markDirty();
        MessageUtils.sendToPlayers(controller.getInterestedPlayersEntities(),
                Text.translatable("message.steveparty.minigame_mode.chosen", MiniGameCartridgeItem.formatText(mode).formatted(Formatting.GOLD)),
                MessageUtils.MessageType.CHAT);
        step.start(controller);
    }

    /** The opponent is {@code opponent} (null: drawn among {@code among}), and the mini-game starts. */
    private static void decideOpponent(MiniGamePartyStep step, PartyControllerEntity controller, List<UUID> among, @Nullable UUID opponent) {
        if (!step.isStillActive(controller)) return;
        step.opponent = opponent == null ? draw(among) : opponent;
        controller.markDirty();
        step.start(controller);
    }

    private static <T> T draw(List<T> among) {
        return among.get(new Random().nextInt(among.size()));
    }

    private static @Nullable ServerPlayerEntity online(ServerWorld world, @Nullable UUID player) {
        return player == null ? null : world.getServer().getPlayerManager().getPlayer(player);
    }

    /** « [1 contre 1] », « [Chacun] »: picks that format. */
    private static MutableText formatButton(MiniGameMode mode) {
        String tooltip = "gui.steveparty.cartridge_menu.minigame.format." + (mode == MiniGameMode.DUEL_RANDOM ? MiniGameMode.DUEL : mode).id() + ".tooltip";
        return Text.translatable("message.steveparty.minigame_mode.button", MiniGameCartridgeItem.formatText(mode))
                .styled(style -> style.withColor(Formatting.GOLD).withUnderline(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, COMMAND + " " + mode.id()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable(tooltip))));
    }

    /** « [Alex] »: picks him as the opponent. */
    private static MutableText opponentButton(ServerPlayerEntity player) {
        return Text.translatable("message.steveparty.minigame_mode.button", player.getDisplayName())
                .styled(style -> style.withColor(Formatting.GOLD).withUnderline(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, OPPONENT_COMMAND + " " + player.getUuidAsString()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Text.translatable("message.steveparty.minigame_opponent.button.tooltip", player.getDisplayName()))));
    }

    // ---------------------------------------------------------------- the teams of each format

    /**
     * The ways to make the teams of {@code step}'s mini-game, each with the pages of {@code miniGames} it can play: by
     * its format, or the board's when the format fits nothing (the players are then told).
     */
    static Map<TeamDisposition, List<ItemStack>> dispositions(MiniGamePartyStep step, PartyControllerEntity controller,
                                                              List<TeamDispositionGenerator.Seat> seats, List<ItemStack> miniGames,
                                                              MinecraftServer server) {
        Map<TeamDisposition, List<ItemStack>> board = MiniGameRoulette.assignMiniGamesToTeamDispositions(seats, miniGames, server);
        if (step.mode == MiniGameMode.ANY || step.mode == MiniGameMode.CHOICE) return board;
        Map<TeamDisposition, List<ItemStack>> ofMode = ofMode(step.mode, step.lander, step.opponent, seats, miniGames, board, server);
        if (!ofMode.isEmpty()) return ofMode;
        MessageUtils.sendToPlayers(controller.getInterestedPlayersEntities(),
                Text.translatable("message.steveparty.minigame_mode.none_fits", MiniGameCartridgeItem.formatText(step.mode)), MessageUtils.MessageType.CHAT);
        return board;
    }

    /**
     * The ways of {@code mode} (empty when no page plays them).
     *
     * @param opponent the opponent of a 1 v 1 (null: none, nothing fits)
     */
    public static Map<TeamDisposition, List<ItemStack>> ofMode(MiniGameMode mode, @Nullable UUID lander, @Nullable UUID opponent,
                                                               List<TeamDispositionGenerator.Seat> seats, List<ItemStack> miniGames,
                                                               Map<TeamDisposition, List<ItemStack>> board, MinecraftServer server) {
        List<UUID> players = seats.stream().map(TeamDispositionGenerator.Seat::player).toList();
        Map<TeamDisposition, List<ItemStack>> result = new LinkedHashMap<>();
        switch (mode) {
            case FFA -> put(result, TeamDisposition.freeForAll(players), miniGames, server);
            case DUEL, DUEL_RANDOM -> {
                if (lander != null && opponent != null && !lander.equals(opponent) && players.contains(lander) && players.contains(opponent)) {
                    put(result, new TeamDisposition(new LinkedHashSet<>(Set.of(lander)), new LinkedHashSet<>(Set.of(opponent))), miniGames, server);
                }
            }
            case VS -> {
                if (lander != null && players.contains(lander) && players.size() >= 2) {
                    Set<UUID> others = new LinkedHashSet<>(players);
                    others.remove(lander);
                    put(result, new TeamDisposition(new LinkedHashSet<>(Set.of(lander)), others), miniGames, server);
                }
            }
            case TEAMS -> board.forEach((disposition, pages) -> {
                if (!disposition.isFreeForAll()) result.put(disposition, pages);
            });
            default -> result.putAll(board);
        }
        return result;
    }

    private static void put(Map<TeamDisposition, List<ItemStack>> into, TeamDisposition disposition, List<ItemStack> miniGames, MinecraftServer server) {
        List<ItemStack> pages = new ArrayList<>();
        for (ItemStack page : miniGames) if (MiniGameRoulette.pagePlayable(server, page, disposition)) pages.add(page);
        if (!pages.isEmpty()) into.put(disposition, pages);
    }
}
