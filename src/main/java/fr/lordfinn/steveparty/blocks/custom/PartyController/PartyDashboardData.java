package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.StartRollsStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.board.BoardValidator;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.minigame.MiniGameMode;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


/**
 * Everything the Party Controller's dashboard shows, for one player: the state of the party (or what is missing to
 * start one), its players with their stars and coins, the mini-games of the inserted catalogue and the settings.
 * Server-authoritative: the dashboard's screen handler captures it twice a second while the screen is open and sends
 * it to that player only when it changed (see {@code PartyControllerScreenHandler}). The currency items are not in
 * it: they are the two setting slots of the screen, synced like any slot.
 *
 * @param phase           no party (setup), a party running, or a party over (on its END step)
 * @param round           the current round (1-based), 0 before the first one or with no party
 * @param rounds          the rounds of the running / ended party, else the rounds setting
 * @param roundsSetting   the rounds the next party will have
 * @param stepIndex       the current step (0-based), -1 without party
 * @param stepCount       the steps of the party
 * @param action          what is happening now (running party), empty otherwise
 * @param actionDetail    a detail of it (the roll, the steps left...), empty for none
 * @param currentPlayer   index in {@code players} of the token whose turn it is, -1 for none
 * @param players         running / ended party: its tokens in the turn order; setup: the tokens bound to the start
 *                        tiles, who would play
 * @param board           the last check of the board around (see {@link BoardValidator})
 * @param hasCatalogue    a mini-game catalogue is in the controller
 * @param pages           the mini-game pages of that catalogue, in its order
 * @param currentPage     the catalogue slot of the page of the mini-game being played, -1 for none
 * @param canEdit         the player may change the settings and start a party (see {@link PartyControllerEntity#canEdit})
 * @param following       the player follows this party (its HUDs)
 * @param catalogueLocked the controller is powered: the catalogue can't be taken out
 * @param gains           what the party pays at the end of each mini-game, by place (the Gains page)
 * @param practiceRound   the mini-games whose page has a Mini-game Controller start with a practice round
 * @param steps           running / ended party: its steps around the current one, as a timeline; empty otherwise
 * @param program         what the program (its cards, or the default party) will play, as a timeline
 */
public record PartyDashboardData(Phase phase, int round, int rounds, int roundsSetting, int stepIndex, int stepCount,
                                 Text action, Text actionDetail, int currentPlayer,
                                 List<PartyLiveData.Standing> players, Board board, boolean hasCatalogue,
                                 List<Page> pages, int currentPage, boolean canEdit, boolean following,
                                 boolean catalogueLocked, MiniGameGains gains, boolean practiceRound,
                                 Timeline steps, Timeline program) {

    public enum Phase { SETUP, RUNNING, ENDED }

    /** Why a party can't be started from the dashboard now ({@link #NONE}: it can). */
    public enum Blocker { NONE, RUNNING, NO_BOARD, NO_START, NO_TOKEN, NOT_ALLOWED }

    /**
     * The board around the controller, as {@link BoardValidator} sees it.
     *
     * @param spaces      board spaces found
     * @param starts      start tiles among them
     * @param startTokens tokens bound to those start tiles (they will play)
     * @param issues      the problems (errors and warnings; the start tiles without token: {@code starts} and {@code startTokens})
     */
    public record Board(int spaces, int starts, List<UUID> startTokens, List<Issue> issues) {
        public static final Board UNKNOWN = new Board(0, 0, List.of(), List.of());

        public long errors() {
            return issues.stream().filter(Issue::error).count();
        }

        public long warnings() {
            return issues.stream().filter(issue -> !issue.error()).count();
        }
    }

    /** A board problem: {@code message.steveparty.board.summary.<key>} with its count. */
    public record Issue(String key, int count, boolean error) {}

    /**
     * A page of the catalogue.
     *
     * @param slot   its slot in the catalogue
     * @param page   the page itself (its name, its tooltip)
     * @param pads   the teleportation pads it sends the players to
     * @param books  those pads holding a « Here we come » book with at least one place: 0 and the mini-game can't be played
     * @param played how many times the roulette chose it in this party
     */
    /**
     * A page of the catalogue.
     *
     * @param pipes    the pipes linked to the page
     * @param playable the number of ways it can be played among those it ticks (0: it can't be drawn)
     * @param podiums  the podiums linked to the page (0: its mini-game names no winner, everyone is a participant)
     */
    public record Page(int slot, ItemStack page, int pipes, int playable, int played, int podiums) {}

    // ------------------------------------------------------------------ timelines

    /** What a step of a timeline is. */
    public enum StepKind {
        /** The dice rolls that decide the turn order. */
        START_ROLLS,
        /** The party being generated from its program. */
        PREPARING,
        /** The turn of one token. */
        TURN,
        /** The turn of every token, in an order not known yet (a program, before the party). */
        TURNS,
        MINI_GAME,
        EVENT,
        END,
        OTHER
    }

    /**
     * A step of a timeline.
     *
     * @param round  the round it belongs to (1-based: a round ends with its mini-game), 0 for none (the start rolls,
     *               the end)
     * @param player {@link StepKind#TURN}: index in {@code players} of the token whose turn it is, -1 if unknown
     * @param value  {@link StepKind#EVENT} of a program: its channel; 0 otherwise
     */
    public record TimelineStep(StepKind kind, int round, int player, int value) {}

    /**
     * Steps in the order they are played.
     *
     * @param steps   the steps sent (a window of at most {@link #MAX_TIMELINE_STEPS})
     * @param offset  how many steps come before the first one sent
     * @param current index in {@code steps} of the step being played, -1 for none
     * @param more    how many steps come after the last one sent
     */
    public record Timeline(List<TimelineStep> steps, int offset, int current, int more) {
        public static final Timeline EMPTY = new Timeline(List.of(), 0, -1, 0);
    }

    /** The most steps of a timeline sent to a dashboard, and how many of them are before the current one. */
    public static final int MAX_TIMELINE_STEPS = 64, TIMELINE_PAST_STEPS = 8;

    /** The steps of a party around the one being played. */
    public static Timeline timelineOf(List<PartyStep> steps, int stepIndex, List<UUID> tokens) {
        if (steps.isEmpty()) return Timeline.EMPTY;
        List<TimelineStep> all = new ArrayList<>(steps.size());
        int round = 1;
        for (PartyStep step : steps) {
            switch (step.getType()) {
                case START_ROLLS -> all.add(new TimelineStep(StepKind.START_ROLLS, 0, -1, 0));
                case BASIC_GAME_GENERATOR -> all.add(new TimelineStep(StepKind.PREPARING, 0, -1, 0));
                case END -> all.add(new TimelineStep(StepKind.END, 0, -1, 0));
                case TOKEN_TURN -> all.add(new TimelineStep(StepKind.TURN, round,
                        step instanceof TokenTurnPartyStep turn && turn.getTokenUUID() != null ? tokens.indexOf(turn.getTokenUUID()) : -1, 0));
                case MINI_GAME -> all.add(new TimelineStep(StepKind.MINI_GAME, round++, -1, 0));
                case EVENT -> all.add(new TimelineStep(StepKind.EVENT, round, -1, 0));
                default -> all.add(new TimelineStep(StepKind.OTHER, round, -1, 0));
            }
        }
        int from = Math.clamp(stepIndex - TIMELINE_PAST_STEPS, 0, Math.max(0, all.size() - 1));
        int to = Math.min(all.size(), from + MAX_TIMELINE_STEPS);
        int current = stepIndex >= from && stepIndex < to ? stepIndex - from : -1;
        return new Timeline(List.copyOf(all.subList(from, to)), from, current, all.size() - to);
    }

    /**
     * What a program will play: the start rolls, its cards expanded (the loops of its Repeat and Sequence start cards
     * unrolled; the default party without cards), the end. The turn order is not known yet: a « turns » step stands
     * for the turn of every token.
     */
    public static Timeline programTimeline(List<ItemStack> program, int rounds) {
        List<TimelineStep> all = new ArrayList<>();
        all.add(new TimelineStep(StepKind.START_ROLLS, 0, -1, 0));
        int round = 1;
        for (fr.lordfinn.steveparty.blocks.custom.PartyController.steps.BasicGameGeneratorStep.ExpandedCard card
                : fr.lordfinn.steveparty.blocks.custom.PartyController.steps.BasicGameGeneratorStep.expand(program, rounds)) {
            switch (card.type()) {
                case TURNS -> all.add(new TimelineStep(StepKind.TURNS, round, -1, 0));
                case MINIGAME -> all.add(new TimelineStep(StepKind.MINI_GAME, round++, -1, 0));
                case EVENT -> all.add(new TimelineStep(StepKind.EVENT, round, -1, card.count()));
                default -> {
                }
            }
        }
        all.add(new TimelineStep(StepKind.END, 0, -1, 0));
        int to = Math.min(all.size(), MAX_TIMELINE_STEPS);
        return new Timeline(List.copyOf(all.subList(0, to)), 0, -1, all.size() - to);
    }

    /** Why a party can't be started now, the checks in the order a player meets them. */
    public static Blocker launchBlocker(boolean running, Board board, boolean canEdit) {
        if (running) return Blocker.RUNNING;
        if (board.spaces() == 0) return Blocker.NO_BOARD;
        if (board.starts() == 0) return Blocker.NO_START;
        if (board.startTokens().isEmpty()) return Blocker.NO_TOKEN;
        if (!canEdit) return Blocker.NOT_ALLOWED;
        return Blocker.NONE;
    }

    public Blocker launchBlocker() {
        return launchBlocker(phase == Phase.RUNNING, board, canEdit);
    }

    // ------------------------------------------------------------------ capture (server)

    /** Checks the board around the controller (costly: only on demand, and every few seconds while no party runs). */
    public static Board checkBoard(PartyControllerEntity controller, ServerWorld world) {
        BoardValidator.Report report = BoardValidator.check(world, controller.getPos());
        List<Issue> issues = new ArrayList<>();
        for (BoardValidator.Issue issue : report.issues()) {
            if (issue.severity() == BoardValidator.Severity.INFO) continue;
            issues.add(new Issue(issue.key(), issue.positions().size(), issue.severity() == BoardValidator.Severity.ERROR));
        }
        return new Board(report.boardSpaces(), report.starts(), controller.findStartTokens(world), issues);
    }

    public static PartyDashboardData capture(PartyControllerEntity controller, ServerWorld world, ServerPlayerEntity player,
                                             Board board) {
        PartyData data = controller.getPartyData();
        Phase phase = data.isStarted() ? Phase.RUNNING : data.isAtEnd() ? Phase.ENDED : Phase.SETUP;
        List<PartyStep> steps = data.getSteps();
        int stepIndex = phase == Phase.SETUP ? -1 : data.getStepIndex();

        // Rounds: a round is the token turns up to its mini-game (as in the party HUD)
        int round = 0, rounds = 0;
        boolean generated = false;
        for (int i = 0; i < steps.size(); i++) {
            PartyStepType type = steps.get(i).getType();
            if (type == PartyStepType.MINI_GAME) {
                rounds++;
                if (i < stepIndex) round++;
            }
            if (type == PartyStepType.BASIC_GAME_GENERATOR && i < stepIndex) generated = true;
        }
        if (phase == Phase.SETUP) {
            round = 0;
            rounds = data.getNbTurn();
        } else if (!generated) {
            round = 0;
        } else {
            round = Math.min(round + 1, Math.max(rounds, 1));
        }

        // Players and what is happening
        List<UUID> tokens = phase == Phase.SETUP ? board.startTokens() : data.getTokens();
        List<PartyLiveData.Standing> players = new ArrayList<>(tokens.size());
        for (UUID token : tokens) players.add(PartyLiveData.standingOf(controller, world, token));
        int currentPlayer = -1;
        Text action = Text.empty(), detail = Text.empty();
        PartyStep current = phase == Phase.RUNNING ? data.getCurrentStep() : null;
        PartyLiveData live = phase == Phase.RUNNING ? PartyLiveData.capture(controller, world) : PartyLiveData.EMPTY;
        if (current instanceof TokenTurnPartyStep turn && turn.getTokenUUID() != null) {
            currentPlayer = tokens.indexOf(turn.getTokenUUID());
            PartyLiveData.Standing standing = currentPlayer >= 0 ? players.get(currentPlayer) : null;
            String name = standing != null ? standing.tokenName() : "?";
            String owner = standing == null || standing.ownerName().isEmpty() ? name : standing.ownerName();
            action = Text.translatable(turn.isReplay() ? "gui.steveparty.party_controller.action.replay" : "gui.steveparty.party_controller.action.turn", name);
            if (live.absentSeconds() >= 0) {
                detail = Text.translatable("gui.steveparty.party_controller.action.absent", live.absentSeconds());
            } else if (live.shopping()) {
                detail = Text.translatable("hud.steveparty.party.shopping", owner);
            } else if (live.stepsLeft() != 0) {
                detail = Text.translatable(live.stepsLeft() > 0 ? "gui.steveparty.party_controller.action.moving" : "gui.steveparty.party_controller.action.moving_back",
                        Math.abs(live.stepsLeft()));
            } else if (live.roll() != 0 || live.effect().rolled()) {
                detail = Text.translatable("gui.steveparty.party_controller.action.rolled", owner, live.rollText());
            } else if (standing != null && standing.owner().isEmpty()) {
                detail = Text.translatable("hud.steveparty.party.roll.anyone", name);
            } else {
                detail = Text.translatable("hud.steveparty.party.roll", owner);
            }
        } else if (current instanceof StartRollsStep startRolls) {
            int rolled = startRolls.rolls == null ? 0 : startRolls.rolls.size();
            action = Text.translatable("gui.steveparty.party_controller.action.start_rolls");
            detail = Text.translatable("gui.steveparty.party_controller.action.start_rolls.count", rolled, tokens.size());
        } else if (current instanceof MiniGamePartyStep miniGame) {
            ItemStack page = MiniGamesCatalogueItem.getCurrentMiniGame(controller.catalogue).copy();
            if (world.getServer() != null) MiniGamePages.refresh(world.getServer(), page);
            action = miniGame.isMiniGameChosen() && !page.isEmpty()
                    ? Text.translatable("gui.steveparty.party_controller.action.mini_game", page.getName())
                    : Text.translatable("hud.steveparty.party.mini_game.choosing");
        } else if (current != null && current.getType() == PartyStepType.BASIC_GAME_GENERATOR) {
            action = Text.translatable("hud.steveparty.party.preparing");
        } else if (current != null) {
            action = Text.translatable(current.getName());
        }

        // Mini-games of the catalogue: their pipes, how many times they were played
        List<ItemStack> stored = controller.catalogue.isEmpty() ? List.of() : MiniGamesCatalogueItem.getStoredPages(controller.catalogue);
        int[] played = new int[stored.size()];
        int currentPage = -1;
        if (phase != Phase.SETUP) {
            for (int i = 0; i < steps.size() && i <= stepIndex; i++) {
                if (!(steps.get(i) instanceof MiniGamePartyStep miniGame)) continue;
                int slot = miniGame.getChosenPageSlot();
                if (slot < 0 || slot >= played.length) continue;
                played[slot]++;
                if (i == stepIndex && phase == Phase.RUNNING) currentPage = slot;
            }
        }
        List<Page> pages = new ArrayList<>();
        for (int slot = 0; slot < stored.size(); slot++) {
            ItemStack page = stored.get(slot);
            if (page.isEmpty()) continue;
            // The title the page has now (the stack is a copy)
            if (world.getServer() != null) MiniGamePages.refresh(world.getServer(), page);
            MiniGamePageData content = world.getServer() == null ? null : MiniGamePages.of(world.getServer(), page);
            int pipes = content == null ? 0 : content.pipeLinks().size(), playable = 0;
            if (content != null) {
                for (MiniGameMode mode : content.modes()) if (content.hasPipesFor(mode)) playable++;
            }
            int podiums = content == null ? 0 : (int) content.podiumLinks().stream()
                    .filter(link -> link.kind() == fr.lordfinn.steveparty.minigame.MiniGamePodiumLink.Kind.PODIUM).count();
            pages.add(new Page(slot, page, pipes, playable, played[slot], podiums));
        }

        return new PartyDashboardData(phase, round, rounds, data.getNbTurn(), stepIndex, steps.size(), action, detail,
                currentPlayer, players, board, !controller.catalogue.isEmpty(), pages, currentPage,
                controller.canEdit(player), controller.getInterestedPlayers().contains(player.getUuid()),
                controller.isCatalogueLocked(), controller.getGains(), controller.hasPracticeRound(),
                phase == Phase.SETUP ? Timeline.EMPTY : timelineOf(steps, stepIndex, tokens),
                programTimeline(controller.getProgram().getHeldStacks(), data.getNbTurn()));
    }

    // ------------------------------------------------------------------ network

    private static final PacketCodec<RegistryByteBuf, Board> BOARD_CODEC = new PacketCodec<>() {
        @Override
        public Board decode(RegistryByteBuf buf) {
            int spaces = buf.readVarInt(), starts = buf.readVarInt();
            List<UUID> startTokens = buf.readList(b -> b.readUuid());
            int count = buf.readVarInt();
            List<Issue> issues = new ArrayList<>(count);
            for (int i = 0; i < count; i++) issues.add(new Issue(buf.readString(), buf.readVarInt(), buf.readBoolean()));
            return new Board(spaces, starts, startTokens, issues);
        }

        @Override
        public void encode(RegistryByteBuf buf, Board board) {
            buf.writeVarInt(board.spaces());
            buf.writeVarInt(board.starts());
            buf.writeCollection(board.startTokens(), (b, uuid) -> b.writeUuid(uuid));
            buf.writeVarInt(board.issues().size());
            for (Issue issue : board.issues()) {
                buf.writeString(issue.key());
                buf.writeVarInt(issue.count());
                buf.writeBoolean(issue.error());
            }
        }
    };

    private static final PacketCodec<RegistryByteBuf, List<Page>> PAGES_CODEC = new PacketCodec<>() {
        @Override
        public List<Page> decode(RegistryByteBuf buf) {
            int count = buf.readVarInt();
            List<Page> pages = new ArrayList<>(count);
            for (int i = 0; i < count; i++)
                pages.add(new Page(buf.readVarInt(), ItemStack.PACKET_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            return pages;
        }

        @Override
        public void encode(RegistryByteBuf buf, List<Page> pages) {
            buf.writeVarInt(pages.size());
            for (Page page : pages) {
                buf.writeVarInt(page.slot());
                ItemStack.PACKET_CODEC.encode(buf, page.page());
                buf.writeVarInt(page.pipes());
                buf.writeVarInt(page.playable());
                buf.writeVarInt(page.played());
                buf.writeVarInt(page.podiums());
            }
        }
    };

    private static Timeline readTimeline(RegistryByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_TIMELINE_STEPS);
        List<TimelineStep> steps = new ArrayList<>(count);
        StepKind[] kinds = StepKind.values();
        for (int i = 0; i < count; i++) {
            steps.add(new TimelineStep(kinds[Math.clamp(buf.readVarInt(), 0, kinds.length - 1)], buf.readVarInt(), buf.readVarInt() - 1, buf.readVarInt()));
        }
        return new Timeline(steps, buf.readVarInt(), buf.readVarInt() - 1, buf.readVarInt());
    }

    private static void writeTimeline(RegistryByteBuf buf, Timeline timeline) {
        List<TimelineStep> steps = timeline.steps().size() > MAX_TIMELINE_STEPS ? timeline.steps().subList(0, MAX_TIMELINE_STEPS) : timeline.steps();
        buf.writeVarInt(steps.size());
        for (TimelineStep step : steps) {
            buf.writeVarInt(step.kind().ordinal());
            buf.writeVarInt(step.round());
            buf.writeVarInt(step.player() + 1);
            buf.writeVarInt(step.value());
        }
        buf.writeVarInt(timeline.offset());
        buf.writeVarInt(timeline.current() + 1);
        buf.writeVarInt(timeline.more());
    }

    public static final PacketCodec<RegistryByteBuf, PartyDashboardData> PACKET_CODEC = new PacketCodec<>() {
        @Override
        public PartyDashboardData decode(RegistryByteBuf buf) {
            Phase phase = Phase.values()[Math.clamp(buf.readVarInt(), 0, Phase.values().length - 1)];
            int round = buf.readVarInt(), rounds = buf.readVarInt(), roundsSetting = buf.readVarInt();
            int stepIndex = buf.readVarInt() - 1, stepCount = buf.readVarInt();
            Text action = TextCodecs.REGISTRY_PACKET_CODEC.decode(buf);
            Text detail = TextCodecs.REGISTRY_PACKET_CODEC.decode(buf);
            int currentPlayer = buf.readVarInt() - 1;
            List<PartyLiveData.Standing> players = PartyLiveData.STANDINGS_CODEC.decode(buf);
            Board board = BOARD_CODEC.decode(buf);
            boolean hasCatalogue = buf.readBoolean();
            List<Page> pages = PAGES_CODEC.decode(buf);
            int currentPage = buf.readVarInt() - 1;
            boolean canEdit = buf.readBoolean(), following = buf.readBoolean(), locked = buf.readBoolean();
            return new PartyDashboardData(phase, round, rounds, roundsSetting, stepIndex, stepCount, action, detail,
                    currentPlayer, players, board, hasCatalogue, pages, currentPage, canEdit, following, locked, MiniGameGains.read(buf),
                    buf.readBoolean(), readTimeline(buf), readTimeline(buf));
        }

        @Override
        public void encode(RegistryByteBuf buf, PartyDashboardData data) {
            buf.writeVarInt(data.phase.ordinal());
            buf.writeVarInt(data.round);
            buf.writeVarInt(data.rounds);
            buf.writeVarInt(data.roundsSetting);
            buf.writeVarInt(data.stepIndex + 1);
            buf.writeVarInt(data.stepCount);
            TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, data.action);
            TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, data.actionDetail);
            buf.writeVarInt(data.currentPlayer + 1);
            PartyLiveData.STANDINGS_CODEC.encode(buf, data.players);
            BOARD_CODEC.encode(buf, data.board);
            buf.writeBoolean(data.hasCatalogue);
            PAGES_CODEC.encode(buf, data.pages);
            buf.writeVarInt(data.currentPage + 1);
            buf.writeBoolean(data.canEdit);
            buf.writeBoolean(data.following);
            buf.writeBoolean(data.catalogueLocked);
            data.gains.write(buf);
            buf.writeBoolean(data.practiceRound);
            writeTimeline(buf, data.steps);
            writeTimeline(buf, data.program);
        }
    };
}
