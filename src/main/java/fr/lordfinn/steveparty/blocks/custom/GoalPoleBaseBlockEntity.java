package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.payloads.custom.GoalPoleBasePayload;
import fr.lordfinn.steveparty.screen_handlers.custom.GoalPoleBaseScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
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
 * <b>Redstone</b> ({@link RedstoneMode}): the back port pauses the base, or runs it, or is ignored. Paused, the base
 * counts nothing (increases seen meanwhile are dropped) but keeps its points, its objective and its outputs.
 */
public class GoalPoleBaseBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<GoalPoleBasePayload> {
    /** 2: event-driven base (1 or missing: the ticking base, migrated when loaded). */
    public static final int VERSION = 2;

    public enum RedstoneMode {
        /** The back port does nothing: the base always counts. */
        IGNORE,
        /** A signal at the back pauses the base (like a hopper). Default of new bases. */
        PAUSE_WHEN_POWERED,
        /** The base counts only while powered at the back (bases placed before this mode existed). */
        RUN_WHEN_POWERED
    }

    public enum Source {
        /** Landings on this base's own poles. */
        LANDINGS_HERE,
        /** Increases of a scoreboard criterion (see {@link #criterion}). */
        CRITERION
    }

    public enum OutputMode {
        /** A comparator gets a short pulse for each point. */
        PULSE,
        /** A comparator gets the progress towards the pole's goal, 0 to 15. */
        PROGRESS
    }

    public enum ResetPort {
        /** A rising signal on the marked reset port only (right side seen from the front). */
        MARKED_SIDE,
        /** A rising signal on any side but the back (bases placed before the marked port existed). */
        ANY_SIDE
    }

    /** Command-block permission level: enough for selectors, not more. */
    private static final int SELECTOR_PERMISSION_LEVEL = 2;
    public static final int MAX_STRING_LENGTH = 256;

    // --- Settings ---
    private RedstoneMode redstoneMode = RedstoneMode.PAUSE_WHEN_POWERED;
    /** Landings on its own poles (each base counts its own board); the global criterion stays selectable. */
    private Source source = Source.LANDINGS_HERE;
    private String criterion = LANDED_ON_POLE_ID;
    private String selector = "@p";
    private OutputMode outputMode = OutputMode.PULSE;
    private ResetPort resetPort = ResetPort.MARKED_SIDE;

    // --- State ---
    /** Points per score holder (player name). */
    private final Map<String, Integer> points = new HashMap<>();
    /** Last score seen per holder on the source objective, to turn score updates into increases. */
    private final Map<String, Integer> sourceSeen = new HashMap<>();
    private long total = 0;
    private int redstoneOutput = 0;
    /** Whether the reset input was powered at the last neighbor update (rising-edge detection). */
    private boolean resetSidePowered = false;

    // --- Runtime ---
    /** Set while the base writes its own objectives, so that it does not react to its own changes. */
    private boolean writing = false;
    @Nullable private ScoreboardObjective mirror;
    @Nullable private ScoreboardObjective sourceObjective;
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
        GoalPoleNetwork.unregister(this);
    }

    /** End of the tick it was loaded or placed in: objectives set up (legacy data converted), poles told the total. */
    void onLoaded() {
        if (world == null || world.isClient) return;
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
                objective = scoreboard.addObjective(name, ScoreboardCriterion.DUMMY, Text.literal("Goal pole " + name.substring("steveparty_".length())),
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

            // Source objective
            String sourceName = getSourceObjectiveName();
            ScoreboardObjective src = scoreboard.getNullableObjective(sourceName);
            Optional<ScoreboardCriterion> wanted = source == Source.CRITERION ? parseGoal(criterion) : Optional.empty();
            sourceInvalid = source == Source.CRITERION && wanted.isEmpty();
            if (src != null && (wanted.isEmpty() || !src.getCriterion().getName().equals(wanted.get().getName()))) {
                scoreboard.removeObjective(src);
                src = null;
                sourceSeen.clear();
            }
            if (src == null && wanted.isPresent()) {
                src = scoreboard.addObjective(sourceName, wanted.get(), Text.literal("Goal pole source " + criterion),
                        ScoreboardCriterion.RenderType.INTEGER, true, null);
            }
            sourceObjective = src;
            if (src != null) GoalPoleNetwork.index(sourceName, this);
            else GoalPoleNetwork.unindex(sourceName, this);
        } finally {
            writing = false;
        }
        recomputeTotal();
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
        if (source != Source.LANDINGS_HERE || !follows(player)) return;
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
            if (outputMode == OutputMode.PULSE) pulseRedstone();
            spawnFloatingText(serverWorld, "+" + delta, pos.toCenterPos().add(0.5).add(Math.random() - 1, Math.random() / 2, Math.random() - 1).toVector3f(),
                    TextColor.fromRgb(0xC90E0E), 50);
        }
        pushTotal();
    }

    /** All the points go back to 0 (reset port, screen button). */
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
        pushTotal();
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

    /** @return whether the base counts points (its back port and redstone mode). */
    public boolean isActive() {
        boolean powered = getCachedState().contains(POWERED) && getCachedState().get(POWERED);
        return switch (redstoneMode) {
            case IGNORE -> true;
            case PAUSE_WHEN_POWERED -> !powered;
            case RUN_WHEN_POWERED -> powered;
        };
    }

    /** The back port's power changed: a base that pauses or resumes says so with a sound. */
    public void onBackPowerChanged() {
        pushTotal();
        markDirty();
        if (redstoneMode != RedstoneMode.IGNORE && world != null && !world.isClient) {
            boolean active = isActive();
            world.playSound(null, pos, active ? SoundEvents.BLOCK_BEACON_ACTIVATE : SoundEvents.BLOCK_BEACON_DEACTIVATE,
                    SoundCategory.BLOCKS, 0.35f, 1.8f);
        }
    }

    private void pulseRedstone() {
        if (world == null || world.isClient) return;
        redstoneOutput = 15;
        world.updateComparators(pos, getCachedState().getBlock());
        world.scheduleBlockTick(pos, getCachedState().getBlock(), 2);
    }

    /** Comparator on the base: a pulse per point, or the progress towards the goal of the pole above (0-15). */
    public int getComparatorOutput() {
        if (outputMode == OutputMode.PULSE) return redstoneOutput;
        return world != null && world.getBlockEntity(pos.up()) instanceof GoalPoleBlockEntity pole ? pole.progressLevel() : 0;
    }

    public void setRedstoneOutput(int value) {
        redstoneOutput = value;
    }

    /** @return the side of the reset port (right side, seen from the front of the base). */
    public static Direction resetSide(BlockState state) {
        return state.get(GoalPoleBaseBlock.FACING).rotateYCounterclockwise();
    }

    /**
     * Updates the reset input's power.
     * @return true only on a rising edge (unpowered to powered), i.e. when the points must be reset
     */
    public boolean updateResetSidePower(boolean powered) {
        boolean risingEdge = powered && !this.resetSidePowered;
        if (powered != this.resetSidePowered) {
            this.resetSidePowered = powered;
            markDirty();
        }
        return risingEdge;
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
        if (world == null || world.getServer() == null || selector.isEmpty()) return false;
        EntitySelector entitySelector = getParsedSelector(selector);
        if (entitySelector == null) return selector.equals(player.getGameProfile().getName());
        try {
            return entitySelector.getPlayers(createSelectorSource(world.getServer())).contains(player);
        } catch (CommandSyntaxException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ settings

    public RedstoneMode getRedstoneMode() { return redstoneMode; }
    public Source getSource() { return source; }
    public String getCriterion() { return criterion; }
    public String getSelector() { return selector; }
    public OutputMode getOutputMode() { return outputMode; }
    public ResetPort getResetPort() { return resetPort; }
    public long getTotal() { return total; }
    public int getPoints(String holder) { return points.getOrDefault(holder, 0); }
    /** Points per player (read only; synced to clients for the wrench details). */
    public Map<String, Integer> getPointsView() { return java.util.Collections.unmodifiableMap(points); }
    public boolean isSourceInvalid() { return sourceInvalid; }
    @Nullable public ScoreboardObjective getMirror() { return mirror; }

    public void setRedstoneMode(RedstoneMode mode) {
        if (mode == redstoneMode) return;
        redstoneMode = mode;
        markDirty();
        pushTotal();
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

    public void setOutputMode(OutputMode mode) {
        if (mode == outputMode) return;
        outputMode = mode;
        markDirty();
        pushTotal();
    }

    public void setResetPort(ResetPort port) {
        if (port == resetPort) return;
        resetPort = port;
        markDirty();
    }

    /** Settings from the screen ({@link #writeSettings}), each value checked. */
    public void applySettings(NbtCompound settings) {
        setRedstoneMode(readEnum(settings, "RedstoneMode", RedstoneMode.values(), redstoneMode));
        String selector = settings.getString("Selector");
        if (selector.length() <= MAX_STRING_LENGTH) setSelector(selector);
        String criterion = settings.getString("Criterion");
        if (criterion.length() <= MAX_STRING_LENGTH) setSource(readEnum(settings, "Source", Source.values(), source), criterion);
        setOutputMode(readEnum(settings, "OutputMode", OutputMode.values(), outputMode));
        setResetPort(readEnum(settings, "ResetPort", ResetPort.values(), resetPort));
        if (settings.getBoolean("Reset")) reset();
    }

    public NbtCompound writeSettings() {
        NbtCompound settings = new NbtCompound();
        settings.putString("RedstoneMode", redstoneMode.name());
        settings.putString("Source", source.name());
        settings.putString("Criterion", criterion);
        settings.putString("Selector", selector);
        settings.putString("OutputMode", outputMode.name());
        settings.putString("ResetPort", resetPort.name());
        settings.putLong("Total", total);
        settings.putBoolean("Active", isActive());
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
        this.resetSidePowered = nbt.getBoolean("ResetSidePowered");
        points.clear();
        sourceSeen.clear();
        if (nbt.getInt("Version") < 2) {
            // The ticking base: counted while powered at the back, reset by any other side, criterion objective
            legacy = true;
            redstoneMode = RedstoneMode.RUN_WHEN_POWERED;
            resetPort = ResetPort.ANY_SIDE;
            outputMode = OutputMode.PULSE;
            source = Source.CRITERION;
            criterion = nbt.contains("Goal", NbtElement.STRING_TYPE) ? nbt.getString("Goal") : LANDED_ON_POLE_ID;
            selector = nbt.contains("Selector", NbtElement.STRING_TYPE) ? nbt.getString("Selector") : "@p";
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
        redstoneMode = readEnum(nbt, "RedstoneMode", RedstoneMode.values(), RedstoneMode.PAUSE_WHEN_POWERED);
        source = readEnum(nbt, "Source", Source.values(), Source.CRITERION);
        criterion = nbt.getString("Criterion");
        selector = nbt.getString("Selector");
        outputMode = readEnum(nbt, "OutputMode", OutputMode.values(), OutputMode.PULSE);
        resetPort = readEnum(nbt, "ResetPort", ResetPort.values(), ResetPort.MARKED_SIDE);
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
        nbt.putString("RedstoneMode", redstoneMode.name());
        nbt.putString("Source", source.name());
        nbt.putString("Criterion", criterion);
        nbt.putString("Selector", selector);
        nbt.putString("OutputMode", outputMode.name());
        nbt.putString("ResetPort", resetPort.name());
        nbt.putBoolean("ResetSidePowered", resetSidePowered);
        NbtCompound pointsNbt = new NbtCompound();
        points.forEach(pointsNbt::putInt);
        nbt.put("Points", pointsNbt);
        NbtCompound seenNbt = new NbtCompound();
        sourceSeen.forEach(seenNbt::putInt);
        nbt.put("SourceSeen", seenNbt);
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    /** Sends the base's data to the players watching it (settings, total: shown when holding the wrench). */
    void sync() {
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
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
        return List.of(redstoneMode.name(), source.name(), criterion, selector, outputMode.name(), resetPort.name());
    }
}
