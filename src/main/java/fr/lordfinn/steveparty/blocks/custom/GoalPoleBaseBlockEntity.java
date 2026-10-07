package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.blocks.SyncedBlockEntity;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.payloads.custom.GoalPoleBasePayload;
import fr.lordfinn.steveparty.screen_handlers.custom.GoalPoleBaseScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlock.POWERED;
import static fr.lordfinn.steveparty.criteria.ModScoreboardCriteria.LANDED_ON_POLE_ID;
import static fr.lordfinn.steveparty.utils.FloatingTextParticleHelper.spawnFloatingText;

/**
 * The goal pole base: it counts points for the players it follows, and tells the poles above it the total.
 * <p>
 * <b>Event driven</b>, it never ticks: points come from events (a landing on one of its poles, or a score update on
 * its source objective, seen by a mixin on the server scoreboard), and after each change the base pushes the new
 * total to the poles above it. Nothing is computed while nothing happens.
 * <p>
 * <b>Points</b> are the base's own count, per score holder (player name). They are mirrored in the scoreboard
 * objective {@code steveparty_x_y_z} (criterion dummy) for commands: reading it gives the points, and changing it
 * with {@code /scoreboard} changes the points.
 * <p>
 * <b>Where points come from</b> ({@link Source}): landings on its own poles (the default: each board counts its own
 * landings), or a scoreboard criterion watched through a second objective {@code steveparty_x_y_z.src}: each increase
 * of a followed player's score there is a point ({@code steveparty:landed_on_pole}, landings on any goal pole, was
 * the only source before and stays selectable).
 * <p>
 * <b>Redstone</b>: a signal into the base (any side) pauses it, 1 to 14 only pausing it, 15 also putting the points back
 * to 0; without a signal it counts again. Paused, the base counts nothing (increases seen meanwhile are dropped) but
 * keeps its points (below 15), its objective and its outputs. A comparator on the base gets a pulse per point; on a
 * pole segment, the progress towards the goal (0 to 15, 15 only once reached).
 * <p>
 * <b>Podiums</b>: a base touching a podium (itself or its pole), or linked to the same mini-game page, is linked to
 * that podium's group (see {@code Podiums}): a per-player goal reached on its pole gives the player the highest free
 * place, and resetting the base empties the group (as resetting the group resets the base).
 * <p>
 * <b>Players</b> ({@link Players}): every player, the players near the base, the players of the party, or an advanced
 * target selector. « The party » is, for a base linked to a mini-game page (clicked with it, or touching a podium
 * linked to it), the party playing that page's mini-game right now, wherever it is: its players are those of the
 * mini-game. Without a linked page it is the nearest party controller within {@value #PARTY_LINK_RADIUS} blocks (the
 * points also go back to 0 when that party starts).
 */
public class GoalPoleBaseBlockEntity extends SyncedBlockEntity implements ExtendedScreenHandlerFactory<GoalPoleBasePayload> {
    /**
     * 3: players chosen in plain words ({@link Players}). 2: event-driven base, players by selector only (kept as the
     * advanced selector). 1 or missing: the ticking base, migrated when loaded.
     */
    public static final int VERSION = 3;

    public enum Players {
        /**
         * The players of the party: the one playing the mini-game of the page the base is linked to, however far; without
         * a linked page, the one run by the nearest party controller (the points go back to 0 when it starts).
         */
        PARTY,
        /** Every player. Default of new bases without a party controller nearby. */
        ALL,
        /** The players within {@link #radius} blocks of the base when they score. */
        RADIUS,
        /** Advanced: a target selector or a player name ({@link #selector}); bases placed before keep theirs. */
        SELECTOR
    }

    /** How far a party controller can be from its linked bases (the party's audience). */
    public static final int PARTY_LINK_RADIUS = PartyControllerEntity.PARTY_AUDIENCE_RADIUS;
    public static final int MAX_RADIUS = 256;

    /** Redstone power into the base that also puts the points back to 0 (below it, the signal only pauses). */
    public static final int RESET_POWER = 15;

    public enum Source {
        /** Landings on this base's own poles. */
        LANDINGS_HERE,
        /** Increases of a scoreboard criterion (see {@link #criterion}). */
        CRITERION
    }


    /** Command-block permission level: enough for selectors, not more. */
    private static final int SELECTOR_PERMISSION_LEVEL = 2;
    public static final int MAX_STRING_LENGTH = 256;

    // --- Settings ---
    /** Landings on its own poles (each base counts its own board); the global criterion stays selectable. */
    private Source source = Source.LANDINGS_HERE;
    private String criterion = LANDED_ON_POLE_ID;
    private Players players = Players.ALL;
    private int radius = 16;
    private String selector = "@a";
    /** Created now (not loaded from saved data): placed by a player, it picks its players from what is around. */
    private boolean fresh = true;

    // --- State ---
    /** Points per score holder (player name). */
    private final Map<String, Integer> points = new HashMap<>();
    /** Last score seen per holder on the source objective, to turn score updates into increases. */
    private final Map<String, Integer> sourceSeen = new HashMap<>();
    private long total = 0;
    private int redstoneOutput = 0;
    /** Redstone power received at the last look (-1: not looked at yet), to see it rise to {@link #RESET_POWER}. */
    private int inputPower = -1;

    // --- Runtime ---
    /** Set while the base writes its own objectives, so that it does not react to its own changes. */
    private boolean writing = false;
    @Nullable private ScoreboardObjective mirror;
    @Nullable private ScoreboardObjective sourceObjective;
    /** The name of the objective of the server it follows (see {@link #followedObjective}), null for none. */
    @Nullable private String followedName;
    private boolean sourceInvalid = false;
    /** Loaded from a ticking base: its objective and remembered scores are converted by {@link #onLoaded()}. */
    private boolean legacy = false;
    private final Map<UUID, Integer> legacyScores = new HashMap<>();
    private String cachedSelectorString = null;
    @Nullable private EntitySelector cachedEntitySelector = null;

    public GoalPoleBaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GOAL_POLE_BASE_ENTITY, pos, state);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        if (!world.isClient) GoalPoleNetwork.register(this);
    }

    @Override
    public void cancelRemoval() {
        super.cancelRemoval();
        if (world != null && !world.isClient) GoalPoleNetwork.register(this);
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        // Server only, as the registration: the client thread must not touch the server's maps (singleplayer)
        if (world != null && !world.isClient) GoalPoleNetwork.unregister(this);
    }

    /** End of the tick it was loaded or placed in: objectives set up (legacy data converted), poles told the total. */
    void onLoaded() {
        if (world == null || world.isClient) return;
        // The signal already there when placed or loaded: no reset for it
        if (inputPower < 0) inputPower = world.getReceivedRedstonePower(pos);
        ensureObjectives();
        catchUpSource();
        pushTotal();
    }

    /** The base was broken: its objectives go with it. */
    public void onBroken() {
        if (world == null || world.isClient || world.getServer() == null) return;
        Scoreboard scoreboard = world.getServer().getScoreboard();
        writing = true;
        try {
            for (String name : new String[]{getObjectiveName(), getSourceObjectiveName()}) {
                ScoreboardObjective objective = scoreboard.getNullableObjective(name);
                if (objective != null) scoreboard.removeObjective(objective);
            }
        } finally {
            writing = false;
        }
        mirror = null;
        sourceObjective = null;
        GoalPoleNetwork.unregister(this);
    }

    // ------------------------------------------------------------------ objectives

    /**
     * One objective per base. Overworld bases keep their historical name; in the other dimensions the dimension is
     * part of the name, so that two bases at the same coordinates in two dimensions don't share one objective.
     */
    public String getObjectiveName() {
        return getObjectiveName(this.world, this.getPos());
    }

    public static String getObjectiveName(@Nullable World world, BlockPos pos) {
        String coordinates = pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
        if (world == null || world.getRegistryKey() == World.OVERWORLD) return "steveparty_" + coordinates;
        Identifier dimension = world.getRegistryKey().getValue();
        String name = dimension.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? dimension.getPath() : dimension.toString();
        return "steveparty_" + name.replaceAll("[^A-Za-z0-9_.+-]", ".") + "_" + coordinates;
    }

    /** The objective with the watched criterion (only for {@link Source#CRITERION}). */
    public String getSourceObjectiveName() {
        return getObjectiveName() + ".src";
    }

    /** @return the scoreboard criterion of a goal string, empty if it is not a valid criterion. */
    public static Optional<ScoreboardCriterion> parseGoal(String goal) {
        if (goal == null || goal.isEmpty()) return Optional.empty();
        try {
            return ScoreboardCriterion.getOrCreateStatCriterion(goal);
        } catch (InvalidIdentifierException e) {
            return Optional.empty();
        }
    }

    /**
     * Makes sure the mirror (dummy, the points) and, for a criterion source, the source objective exist.
     * A legacy base's objective (with its criterion) becomes the mirror: its scores are the points.
     */
    private void ensureObjectives() {
        if (world == null || world.isClient || world.getServer() == null) return;
        MinecraftServer server = world.getServer();
        Scoreboard scoreboard = server.getScoreboard();
        String name = getObjectiveName();
        writing = true;
        try {
            ScoreboardObjective objective = scoreboard.getNullableObjective(name);
            if (legacy) {
                // The old objective counted the criterion itself: its scores become the points
                if (objective != null) {
                    for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) {
                        points.put(entry.owner(), entry.value());
                    }
                    if (objective.getCriterion() != ScoreboardCriterion.DUMMY) {
                        scoreboard.removeObjective(objective);
                        objective = null;
                    }
                } else {
                    // Paused old base (its objective was removed): the scores it remembered
                    for (Map.Entry<UUID, Integer> entry : legacyScores.entrySet()) {
                        server.getUserCache().getByUuid(entry.getKey())
                                .ifPresent(profile -> points.put(profile.getName(), entry.getValue()));
                    }
                }
                legacyScores.clear();
                legacy = false;
                markDirty();
            }
            if (objective != null && objective.getCriterion() != ScoreboardCriterion.DUMMY) {
                // Made by hand under this name with another criterion: the base owns the name
                scoreboard.removeObjective(objective);
                objective = null;
            }
            if (objective == null) {
                objective = scoreboard.addObjective(name, ScoreboardCriterion.DUMMY, Text.translatable("scoreboard.steveparty.goal_pole", name.substring("steveparty_".length())),
                        ScoreboardCriterion.RenderType.INTEGER, true, null);
                for (Map.Entry<String, Integer> entry : points.entrySet()) {
                    scoreboard.getOrCreateScore(ScoreHolder.fromName(entry.getKey()), objective).setScore(entry.getValue());
                }
            } else {
                // The mirror is kept by the scoreboard: it has the last word (commands may have changed it while unloaded)
                points.clear();
                for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) points.put(entry.owner(), entry.value());
            }
            mirror = objective;
            GoalPoleNetwork.index(name, this);

            // Source objective: an objective of the server named so (followed as it is), else its own with the criterion
            String sourceName = getSourceObjectiveName();
            ScoreboardObjective src = scoreboard.getNullableObjective(sourceName);
            ScoreboardObjective followed = source == Source.CRITERION ? followedObjective(scoreboard) : null;
            Optional<ScoreboardCriterion> wanted = source == Source.CRITERION && followed == null ? parseGoal(criterion) : Optional.empty();
            sourceInvalid = source == Source.CRITERION && followed == null && wanted.isEmpty();
            if (src != null && (wanted.isEmpty() || !src.getCriterion().getName().equals(wanted.get().getName()))) {
                scoreboard.removeObjective(src);
                src = null;
                if (followed == null) sourceSeen.clear();
            }
            if (src == null && wanted.isPresent()) {
                src = scoreboard.addObjective(sourceName, wanted.get(), Text.translatable("scoreboard.steveparty.goal_pole_source", criterion),
                        ScoreboardCriterion.RenderType.INTEGER, true, null);
            }
            if (followedName != null && (followed == null || !followedName.equals(followed.getName()))) GoalPoleNetwork.unindex(followedName, this);
            followedName = followed == null ? null : followed.getName();
            sourceObjective = followed != null ? followed : src;
            if (src != null) GoalPoleNetwork.index(sourceName, this);
            else GoalPoleNetwork.unindex(sourceName, this);
            if (followed != null) GoalPoleNetwork.index(followed.getName(), this);
        } finally {
            writing = false;
        }
        recomputeTotal();
    }

    /**
     * The objective of the server the base follows: the one named as its goal (made with {@code /scoreboard
     * objectives add}), its scores' increases being the points; null when there is none of that name (the goal is
     * then a criterion), or it is one of the base's own.
     */
    @Nullable
    private ScoreboardObjective followedObjective(Scoreboard scoreboard) {
        if (criterion == null || criterion.isEmpty() || criterion.startsWith("steveparty_")) return null;
        return scoreboard.getNullableObjective(criterion);
    }

    /** The most objectives of the server offered to the screen (its completion). */
    public static final int MAX_LISTED_OBJECTIVES = 200;

    /** The objectives of the server a base can follow (not the bases' own), by name: name, criterion and display name. */
    public static NbtList listObjectives(MinecraftServer server) {
        NbtList list = new NbtList();
        server.getScoreboard().getObjectives().stream()
                .filter(objective -> !objective.getName().startsWith("steveparty_"))
                .sorted(java.util.Comparator.comparing(ScoreboardObjective::getName))
                .limit(MAX_LISTED_OBJECTIVES)
                .forEach(objective -> {
                    NbtCompound entry = new NbtCompound();
                    entry.putString("Name", objective.getName());
                    entry.putString("Criterion", objective.getCriterion().getName());
                    entry.putString("Display", objective.getDisplayName().getString());
                    list.add(entry);
                });
        return list;
    }

    /** Scores of the source objective that went up while the base was unloaded or not set up yet. */
    private void catchUpSource() {
        if (sourceObjective == null || world == null || world.getServer() == null) return;
        for (ScoreboardEntry entry : world.getServer().getScoreboard().getScoreboardEntries(sourceObjective)) {
            onSourceScore(entry.owner(), entry.value());
        }
    }

    private void recomputeTotal() {
        long sum = 0;
        for (int value : points.values()) sum += value;
        total = sum;
    }

    // ------------------------------------------------------------------ events

    /** A score changed on one of this base's objectives. */
    void onScoreUpdated(ScoreboardObjective objective, String holder, int value) {
        if (writing) return;
        if (objective == mirror || objective.getName().equals(getObjectiveName())) {
            // Changed by a command: the points follow
            int old = points.getOrDefault(holder, 0);
            if (value == old) return;
            if (value == 0) points.remove(holder);
            else points.put(holder, value);
            total += (long) value - old;
            onPointsChanged(value - old, holder);
        } else if (objective == sourceObjective || objective.getName().equals(getSourceObjectiveName())) {
            onSourceScore(holder, value);
        }
    }

    void onHolderRemoved(String holder) {
        if (writing) return;
        if (points.containsKey(holder)) {
            int old = points.remove(holder);
            total -= old;
            onPointsChanged(-old, holder);
        }
        if (sourceSeen.remove(holder) != null) markDirty();
    }

    /** One of this base's objectives was removed (by a command): it comes back at the end of the tick. */
    void onObjectiveRemoved(ScoreboardObjective objective) {
        if (writing) return;
        if (objective == mirror) mirror = null;
        if (objective == sourceObjective) {
            sourceObjective = null;
            sourceSeen.clear();
            markDirty();
        }
        GoalPoleNetwork.schedule(this);
    }

    private void onSourceScore(String holder, int value) {
        int last = sourceSeen.getOrDefault(holder, 0);
        if (value == last) return;
        sourceSeen.put(holder, value);
        markDirty();
        int gained = value - last;
        if (gained <= 0) return;
        ServerPlayerEntity player = world != null && world.getServer() != null
                ? world.getServer().getPlayerManager().getPlayer(holder) : null;
        if (player != null && follows(player)) credit(holder, gained, player);
    }

    /** A player landed on one of this base's poles. */
    void onLanding(ServerPlayerEntity player) {
        // A landing that will not count says why, instead of silently doing nothing
        boolean countsLandings = source == Source.LANDINGS_HERE
                || (source == Source.CRITERION && LANDED_ON_POLE_ID.equals(criterion));
        if (source == Source.CRITERION && sourceInvalid) {
            player.sendMessage(Text.translatable("message.steveparty.goal_pole.unknown_goal").formatted(Formatting.GOLD), true);
            return;
        }
        if (countsLandings && !isActive()) {
            player.sendMessage(Text.translatable("message.steveparty.goal_pole.paused").formatted(Formatting.GOLD), true);
            return;
        }
        if (source != Source.LANDINGS_HERE) return;
        if (!follows(player)) {
            // Not one of the players this base follows: say so (no party running, too far, not in the party...)
            String why = players == Players.PARTY && countedSession() == null && countedParty() == null ? "no_party" : "not_followed";
            player.sendMessage(Text.translatable("message.steveparty.goal_pole." + why).formatted(Formatting.GOLD), true);
            return;
        }
        credit(player.getNameForScoreboard(), 1, player);
    }

    /**
     * Gives points to a holder, if the base is counting.
     * @return whether the points were counted
     */
    public boolean credit(String holder, int amount, @Nullable ServerPlayerEntity player) {
        if (amount <= 0 || !isActive() || world == null || world.isClient) return false;
        int value = (int) Math.clamp((long) points.getOrDefault(holder, 0) + amount, Integer.MIN_VALUE, Integer.MAX_VALUE);
        points.put(holder, value);
        total += amount;
        writeMirror(holder, value);
        onPointsChanged(amount, holder);
        return true;
    }

    private void writeMirror(String holder, int value) {
        if (mirror == null || world == null || world.getServer() == null) return;
        Scoreboard scoreboard = world.getServer().getScoreboard();
        if (scoreboard.getNullableObjective(mirror.getName()) != mirror) return;
        writing = true;
        try {
            scoreboard.getOrCreateScore(ScoreHolder.fromName(holder), mirror).setScore(value);
        } finally {
            writing = false;
        }
    }

    private void onPointsChanged(long delta, String holder) {
        markDirty();
        if (delta > 0 && world instanceof ServerWorld serverWorld) {
            pulseRedstone();
            spawnFloatingText(serverWorld, "+" + delta, pos.toCenterPos().add(0.5, 0.5, 0.5).add(Math.random() - 1, Math.random() / 2, Math.random() - 1).toVector3f(),
                    TextColor.fromRgb(0xC90E0E), 50);
        }
        pushTotal();
        checkPlayerGoals(holder);
    }

    /**
     * The goals per side of the poles above ({@link GoalPoleBlockEntity#isPerPlayer}): the side of a holder (his team in
     * a team mini-game, else himself) whose score just reached one fires it once, and the holder takes the highest free
     * place of the podiums linked to this base (for his team, in a team mini-game).
     */
    private void checkPlayerGoals(String holder) {
        if (world == null || world.isClient) return;
        boolean reached = false;
        TeamDisposition teams = null;
        boolean looked = false;
        BlockPos.Mutable cursor = pos.mutableCopy().move(Direction.UP);
        while (!world.isOutOfHeightLimit(cursor) && world.getBlockEntity(cursor) instanceof GoalPoleBlockEntity pole) {
            if (pole.isPerPlayer()) {
                if (!looked) {
                    teams = countedTeams();
                    looked = true;
                }
                String side = sideOf(teams, holder);
                long score = sideScores(pole.getCount(), teams).getOrDefault(side, 0L);
                if (pole.acceptPlayerPoints(side, (int) Math.clamp(score, Integer.MIN_VALUE, Integer.MAX_VALUE))) reached = true;
            }
            cursor.move(Direction.UP);
        }
        if (reached) fr.lordfinn.steveparty.podium.Podiums.onGoalReached(this, holder);
    }

    /** The most points a single holder has. */
    public int getBestPoints() {
        int best = 0;
        for (int value : points.values()) best = Math.max(best, value);
        return best;
    }

    // ------------------------------------------------------------------ sides: a player, or a team

    /** What the side of a team is called in the goals reached ({@code #team:0} for team A...). */
    public static final String TEAM_SIDE = "#team:";

    /** The teams counted now, for the poles (worked out once per tick at most). */
    @Nullable private TeamDisposition teamsSeen;
    private long teamsSeenTick = Long.MIN_VALUE;

    /**
     * The teams of the mini-game being played on a page this base is linked to; null when there is none, or everyone
     * plays for himself.
     */
    @Nullable
    public TeamDisposition countedTeams() {
        if (world == null) return null;
        long now = world.getTime();
        if (now != teamsSeenTick) {
            teamsSeenTick = now;
            fr.lordfinn.steveparty.minigame.MiniGameSession session = countedSession();
            TeamDisposition teams = session == null ? null : session.teams();
            teamsSeen = teams == null || teams.isFreeForAll() ? null : teams;
        }
        return teamsSeen;
    }

    /** The side of a holder: his team ({@link #TEAM_SIDE} and its number) when he has one in {@code teams}, else himself. */
    public String sideOf(@Nullable TeamDisposition teams, String holder) {
        int team = teamOf(teams, holder);
        return team < 0 ? holder : TEAM_SIDE + team;
    }

    private int teamOf(@Nullable TeamDisposition teams, String holder) {
        if (teams == null || world == null || world.getServer() == null) return -1;
        MinecraftServer server = world.getServer();
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(holder);
        UUID uuid = player != null ? player.getUuid()
                : server.getUserCache() == null ? null : server.getUserCache().findByName(holder).map(com.mojang.authlib.GameProfile::getId).orElse(null);
        return uuid == null ? -1 : teams.teamOf(uuid);
    }

    /**
     * The score of each side: a player for himself, his own points; a team, its players' points added up
     * ({@link GoalPoleBlockEntity.Count#SIDES}) or its best player's ({@link GoalPoleBlockEntity.Count#TEAM_BEST}).
     */
    public Map<String, Long> sideScores(GoalPoleBlockEntity.Count count, @Nullable TeamDisposition teams) {
        Map<String, Long> scores = new HashMap<>();
        for (Map.Entry<String, Integer> entry : points.entrySet()) {
            String side = sideOf(teams, entry.getKey());
            long value = entry.getValue();
            if (count == GoalPoleBlockEntity.Count.TEAM_BEST) scores.merge(side, value, Math::max);
            else scores.merge(side, value, Long::sum);
        }
        return scores;
    }

    /** What a pole counting {@code count} shows and compares with its goal: everybody's total, or the best side's score. */
    public long shownScore(GoalPoleBlockEntity.Count count) {
        if (count == GoalPoleBlockEntity.Count.TOTAL) return total;
        long best = 0;
        for (long value : sideScores(count, countedTeams()).values()) best = Math.max(best, value);
        return best;
    }

    /**
     * All the points go back to 0 (a signal of 15, the screen button, a party or a mini-game starting), the
     * per-player goals can be reached again, and the podiums linked to the base are emptied.
     */
    public void reset() {
        if (world == null || world.isClient || world.getServer() == null) return;
        Scoreboard scoreboard = world.getServer().getScoreboard();
        writing = true;
        try {
            if (mirror != null && scoreboard.getNullableObjective(mirror.getName()) == mirror) {
                for (String holder : new ArrayList<>(points.keySet())) scoreboard.removeScore(ScoreHolder.fromName(holder), mirror);
            }
        } finally {
            writing = false;
        }
        points.clear();
        total = 0;
        markDirty();
        BlockPos.Mutable cursor = pos.mutableCopy().move(Direction.UP);
        while (!world.isOutOfHeightLimit(cursor) && world.getBlockEntity(cursor) instanceof GoalPoleBlockEntity pole) {
            pole.clearReached();
            cursor.move(Direction.UP);
        }
        pushTotal();
        fr.lordfinn.steveparty.podium.Podiums.onBaseReset(this);
        world.playSound(null, pos, SoundEvents.BLOCK_COMPARATOR_CLICK, SoundCategory.BLOCKS, 0.8f, 0.6f);
        world.playSound(null, pos, SoundEvents.BLOCK_COPPER_BULB_TURN_OFF, SoundCategory.BLOCKS, 0.7f, 0.8f);
    }

    // ------------------------------------------------------------------ poles

    /**
     * Tells every pole stacked on this base the total (and whether the base counts). A goal reached rings a chime,
     * once for the pole (at the highest segment that reached it).
     */
    public void pushTotal() {
        if (world == null || world.isClient) return;
        BlockPos.Mutable cursor = pos.mutableCopy().move(Direction.UP);
        BlockPos chime = null;
        while (!world.isOutOfHeightLimit(cursor) && world.getBlockEntity(cursor) instanceof GoalPoleBlockEntity pole) {
            if (pole.acceptTotal(this)) chime = cursor.toImmutable();
            cursor.move(Direction.UP);
        }
        world.updateComparators(pos, getCachedState().getBlock());
        if (chime != null) {
            goalChimes++;
            world.playSound(null, chime, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 1f, 1.19f);
            world.playSound(null, chime, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.2f, 1.2f);
        }
    }

    /** Chimes rung since loaded (goals reached), for tests. */
    private int goalChimes = 0;

    public int getGoalChimes() {
        return goalChimes;
    }

    // ------------------------------------------------------------------ redstone

    /** @return whether the base counts points: not while it receives a redstone signal. */
    public boolean isActive() {
        return !(getCachedState().contains(POWERED) && getCachedState().get(POWERED));
    }

    /** Redstone power received at the last look (0 to 15). */
    public int getInputPower() {
        return Math.max(0, inputPower);
    }

    /**
     * The redstone power into the base changed (any side). A signal pauses the base; rising to {@link #RESET_POWER}
     * also puts the points back to 0. A base that pauses or resumes says so with a sound.
     */
    public void onInputPower(int power) {
        if (world == null || world.isClient || power == inputPower) return;
        int before = inputPower;
        inputPower = power;
        markDirty();
        if (before >= 0 && (before > 0) != (power > 0)) {
            world.playSound(null, pos, power > 0 ? SoundEvents.BLOCK_BEACON_DEACTIVATE : SoundEvents.BLOCK_BEACON_ACTIVATE,
                    SoundCategory.BLOCKS, 0.35f, 1.8f);
        }
        if (before >= 0 && before < RESET_POWER && power >= RESET_POWER) reset();
    }

    private void pulseRedstone() {
        if (world == null || world.isClient) return;
        redstoneOutput = 15;
        world.updateComparators(pos, getCachedState().getBlock());
        world.scheduleBlockTick(pos, getCachedState().getBlock(), 2);
    }

    /** Comparator on the base: a pulse per point (the progress is read on the pole). */
    public int getComparatorOutput() {
        return redstoneOutput;
    }

    public void setRedstoneOutput(int value) {
        redstoneOutput = value;
    }


    // ------------------------------------------------------------------ players

    /** Command source for the selector: at this base, in its world, with command-block permissions. */
    private ServerCommandSource createSelectorSource(MinecraftServer server) {
        ServerWorld serverWorld = this.world instanceof ServerWorld sw ? sw : server.getOverworld();
        return new ServerCommandSource(CommandOutput.DUMMY, Vec3d.ofCenter(this.getPos()), Vec2f.ZERO, serverWorld,
                SELECTOR_PERMISSION_LEVEL, "GoalPole", Text.literal("Goal Pole"), server, null);
    }

    @Nullable
    private EntitySelector getParsedSelector(String selector) {
        if (!selector.equals(this.cachedSelectorString)) {
            this.cachedSelectorString = selector;
            try {
                this.cachedEntitySelector = EntityArgumentType.players().parse(new StringReader(selector));
            } catch (CommandSyntaxException e) {
                this.cachedEntitySelector = null;
            }
        }
        return this.cachedEntitySelector;
    }

    /** Whether the base follows this player, evaluated now (only when something happens). */
    public boolean follows(ServerPlayerEntity player) {
        if (world == null || world.getServer() == null) return false;
        return switch (players) {
            case ALL -> true;
            case RADIUS -> player.getWorld() == world
                    && player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= (double) radius * radius;
            case PARTY -> {
                // Linked to a page: the players of its mini-game (a party's or a test's; not those who only watch)
                if (!linkedPages().isEmpty()) {
                    fr.lordfinn.steveparty.minigame.MiniGameSession session = countedSession();
                    yield session != null && session.isParticipant(player.getUuid());
                }
                PartyControllerEntity party = runningParty();
                yield party != null && party.isParticipant(player);
            }
            case SELECTOR -> followsSelector(player);
        };
    }

    /** The party this base is linked to: the nearest party controller (running or not) within the link radius. */
    @Nullable
    public PartyControllerEntity linkedParty() {
        if (world == null || world.isClient) return null;
        PartyControllerEntity best = null;
        double bestDistance = (double) PARTY_LINK_RADIUS * PARTY_LINK_RADIUS;
        for (PartyControllerEntity controller : PartyControllerEntity.getActivePartyControllers()) {
            if (controller.isRemoved() || controller.getWorld() != world) continue;
            double distance = controller.getPos().getSquaredDistance(pos);
            if (distance < bestDistance) {
                best = controller;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * The mini-game pages this base is linked to: those it was clicked with, and those of the podiums it touches
     * (itself or its pole). Worked out when something happens.
     */
    public java.util.Set<UUID> linkedPages() {
        java.util.Set<UUID> pages = new java.util.LinkedHashSet<>();
        if (!(world instanceof ServerWorld serverWorld)) return pages;
        pages.addAll(fr.lordfinn.steveparty.minigame.MiniGamePages.pageIdsAt(serverWorld, pos));
        for (fr.lordfinn.steveparty.podium.PodiumGroup group : fr.lordfinn.steveparty.podium.Podiums.groupsOf(this)) pages.addAll(group.pages());
        return pages;
    }

    /** The mini-game being played on a page this base is linked to (a party's, or a test), null for none. */
    @Nullable
    public fr.lordfinn.steveparty.minigame.MiniGameSession countedSession() {
        return fr.lordfinn.steveparty.minigame.MiniGameSession.playing(linkedPages());
    }

    /**
     * The party whose players « the party's players » are, now: linked to a page, the party playing that page's
     * mini-game, at any distance and in any dimension (none playing it: nobody counts); not linked, the nearest party
     * controller's, if it runs.
     */
    @Nullable
    public PartyControllerEntity countedParty() {
        java.util.Set<UUID> pages = linkedPages();
        if (!pages.isEmpty()) return PartyControllerEntity.getPartyPlayingPage(pages).orElse(null);
        return runningParty();
    }

    /** The linked party, if it is running. */
    @Nullable
    private PartyControllerEntity runningParty() {
        PartyControllerEntity party = linkedParty();
        return party != null && party.getPartyData().isStarted() ? party : null;
    }

    /** A party started: if it is this base's party, its points go back to 0. */
    void onPartyStarted(PartyControllerEntity controller) {
        if (players == Players.PARTY && linkedParty() == controller) reset();
    }

    /** Placed by a player: the party's players when a party controller is near, else every player. */
    public void onPlacedByPlayer() {
        if (!fresh) return;
        fresh = false;
        players = linkedParty() != null ? Players.PARTY : Players.ALL;
        markDirty();
    }

    private boolean followsSelector(ServerPlayerEntity player) {
        if (selector.isEmpty()) return false;
        EntitySelector entitySelector = getParsedSelector(selector);
        if (entitySelector == null) return selector.equals(player.getGameProfile().getName());
        try {
            return entitySelector.getPlayers(createSelectorSource(world.getServer())).contains(player);
        } catch (CommandSyntaxException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ settings

    public Source getSource() { return source; }
    public String getCriterion() { return criterion; }
    public String getSelector() { return selector; }
    public Players getPlayers() { return players; }
    public int getRadius() { return radius; }
    public long getTotal() { return total; }
    public int getPoints(String holder) { return points.getOrDefault(holder, 0); }
    /** Points per player (read only; synced to clients for the wrench details). */
    public Map<String, Integer> getPointsView() { return java.util.Collections.unmodifiableMap(points); }
    public boolean isSourceInvalid() { return sourceInvalid; }
    @Nullable public ScoreboardObjective getMirror() { return mirror; }


    public void setPlayers(Players players, int radius) {
        int clamped = Math.clamp(radius, 1, MAX_RADIUS);
        if (players == this.players && clamped == this.radius) return;
        this.players = players;
        this.radius = clamped;
        markDirty();
    }

    public void setSelector(String selector) {
        if (Objects.equals(selector, this.selector)) return;
        this.selector = selector;
        this.cachedSelectorString = null;
        this.cachedEntitySelector = null;
        markDirty();
    }

    /** Where points come from; changing it keeps the points (only new increases follow the new source). */
    public void setSource(Source source, String criterion) {
        if (source == this.source && Objects.equals(criterion, this.criterion)) return;
        this.source = source;
        this.criterion = criterion == null ? "" : criterion;
        this.sourceSeen.clear();
        markDirty();
        if (world != null && !world.isClient) {
            ensureObjectives();
            // Current scores of the new source are the baseline, not points
            if (sourceObjective != null && world.getServer() != null) {
                for (ScoreboardEntry entry : world.getServer().getScoreboard().getScoreboardEntries(sourceObjective)) {
                    sourceSeen.put(entry.owner(), entry.value());
                }
            }
        }
    }


    /** Settings from the screen ({@link #writeSettings}), each value checked. */
    public void applySettings(NbtCompound settings) {
        String selector = settings.getString("Selector");
        if (selector.length() <= MAX_STRING_LENGTH) setSelector(selector);
        setPlayers(readEnum(settings, "Players", Players.values(), players), settings.contains("Radius") ? settings.getInt("Radius") : radius);
        String criterion = settings.getString("Criterion");
        if (criterion.length() <= MAX_STRING_LENGTH) setSource(readEnum(settings, "Source", Source.values(), source), criterion);
        if (settings.getBoolean("Reset")) reset();
    }

    public NbtCompound writeSettings() {
        NbtCompound settings = new NbtCompound();
        settings.putString("Source", source.name());
        settings.putString("Criterion", criterion);
        settings.putString("Selector", selector);
        settings.putString("Players", players.name());
        settings.putInt("Radius", radius);
        settings.putLong("Total", total);
        settings.putBoolean("PartyNear", linkedParty() != null);
        settings.putBoolean("PageLinked", !linkedPages().isEmpty());
        settings.putBoolean("Active", isActive());
        if (world != null && world.getServer() != null) settings.put("Objectives", listObjectives(world.getServer()));
        return settings;
    }

    public static <E extends Enum<E>> E readEnum(NbtCompound nbt, String key, E[] values, E fallback) {
        String name = nbt.getString(key);
        for (E value : values) {
            if (value.name().equals(name)) return value;
        }
        return fallback;
    }

    // ------------------------------------------------------------------ NBT

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        fresh = false;
        inputPower = nbt.contains("InputPower", NbtElement.INT_TYPE) ? Math.clamp(nbt.getInt("InputPower"), 0, 15) : -1;
        points.clear();
        sourceSeen.clear();
        if (nbt.getInt("Version") < 2) {
            // The ticking base: criterion objective
            legacy = true;
            source = Source.CRITERION;
            criterion = nbt.contains("Goal", NbtElement.STRING_TYPE) ? nbt.getString("Goal") : LANDED_ON_POLE_ID;
            selector = nbt.contains("Selector", NbtElement.STRING_TYPE) ? nbt.getString("Selector") : "@p";
            // Their selector is kept, as the advanced choice
            players = Players.SELECTOR;
            legacyScores.clear();
            NbtCompound scores = nbt.getCompound("LastScores");
            for (String key : scores.getKeys()) {
                try {
                    legacyScores.put(UUID.fromString(key), scores.getInt(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return;
        }
        legacy = false;
        source = readEnum(nbt, "Source", Source.values(), Source.CRITERION);
        criterion = nbt.getString("Criterion");
        selector = nbt.getString("Selector");
        // Version 2 had only the selector: kept as the advanced choice
        players = nbt.getInt("Version") < 3 ? Players.SELECTOR : readEnum(nbt, "Players", Players.values(), Players.ALL);
        radius = nbt.contains("Radius") ? Math.clamp(nbt.getInt("Radius"), 1, MAX_RADIUS) : 16;
        NbtCompound pointsNbt = nbt.getCompound("Points");
        for (String key : pointsNbt.getKeys()) points.put(key, pointsNbt.getInt(key));
        NbtCompound seenNbt = nbt.getCompound("SourceSeen");
        for (String key : seenNbt.getKeys()) sourceSeen.put(key, seenNbt.getInt(key));
        recomputeTotal();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("Version", VERSION);
        nbt.putString("Source", source.name());
        nbt.putString("Criterion", criterion);
        nbt.putString("Selector", selector);
        nbt.putString("Players", players.name());
        nbt.putInt("Radius", radius);
        if (inputPower >= 0) nbt.putInt("InputPower", inputPower);
        NbtCompound pointsNbt = new NbtCompound();
        points.forEach(pointsNbt::putInt);
        nbt.put("Points", pointsNbt);
        NbtCompound seenNbt = new NbtCompound();
        sourceSeen.forEach(seenNbt::putInt);
        nbt.put("SourceSeen", seenNbt);
    }

    /**
     * Sends the base's data to the players watching it (settings, total, points: shown with the wrench), once at the
     * end of the tick however many changes.
     */
    void sync() {
        if (world != null && !world.isClient) GoalPoleNetwork.requestSync(this);
    }

    @Override
    public void markDirty() {
        super.markDirty();
        sync();
    }

    // ------------------------------------------------------------------ screen

    public void openScreen(ServerPlayerEntity player) {
        player.openHandledScreen(this);
    }

    @Override
    public GoalPoleBasePayload getScreenOpeningData(ServerPlayerEntity player) {
        return new GoalPoleBasePayload(this.getPos(), writeSettings());
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.steveparty.goal_pole_base");
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new GoalPoleBaseScreenHandler(syncId, playerInventory, this);
    }

    /** The settings as a list of names (for tests and debugging). */
    public List<String> describe() {
        return List.of(source.name(), criterion, players.name(), String.valueOf(radius), selector);
    }
}
