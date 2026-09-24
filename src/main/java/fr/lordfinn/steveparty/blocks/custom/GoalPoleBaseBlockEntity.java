package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.payloads.custom.GoalPoleBasePayload;
import fr.lordfinn.steveparty.screen_handlers.custom.GoalPoleBaseScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlock.POWERED;
import static fr.lordfinn.steveparty.criteria.ModScoreboardCriteria.LANDED_ON_POLE_ID;
import static fr.lordfinn.steveparty.utils.FloatingTextParticleHelper.spawnFloatingText;

public class GoalPoleBaseBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<GoalPoleBasePayload>, BlockEntityTicker {
    private String selector = "@p";
    private String goal = LANDED_ON_POLE_ID;
    private final Map<UUID, Integer> lastScores = new HashMap<>();

    // cache
    private ScoreboardObjective cachedObjective = null;
    private List<ServerPlayerEntity> cacheSelectedPlayers = List.of();
    private final Map<UUID, Integer> lastValues = new HashMap<>();
    private int redstoneOutput = 0;
    /** Command-block permission level: enough for selectors, not more. */
    private static final int SELECTOR_PERMISSION_LEVEL = 2;
    // Parsed selector cache (re-parsed only when the selector string changes)
    private String cachedSelectorString = null;
    @Nullable private EntitySelector cachedEntitySelector = null;
    /** Whether a reset side was powered at the last neighbor update (rising-edge detection). */
    private boolean resetSidePowered = false;
    // Per-tick caches shared by the base and the poles above it
    private long trackedPlayersTick = Long.MIN_VALUE;
    private long totalScoreTick = Long.MIN_VALUE;
    private int totalScore = 0;
    private long nextSetupAttemptTick = Long.MIN_VALUE;

    public int getComparatorOutput() {
        return redstoneOutput;
    }

    public GoalPoleBaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GOAL_POLE_BASE_ENTITY, pos, state);
    }

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        if (Objects.equals(selector, this.selector)) return;
        this.selector = selector;
        this.cachedSelectorString = null;
        this.cachedEntitySelector = null;
        markDirty();
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        if (Objects.equals(goal, this.goal)) return;

        // Remove old objective if it exists
        removeObjective();

        this.goal = goal;
        // The new objective starts from scratch: the scores remembered for the old goal no longer apply
        this.lastScores.clear();
        markDirty();

        if (this.world != null && !this.world.isClient && isPowered()) {
            setupScoreboard();
        }
    }

    /**
     * One objective per base. Overworld bases keep their historical name; in the other dimensions the dimension is
     * part of the name, so that two bases at the same coordinates in two dimensions don't share (and delete) one
     * objective.
     */
    private String getObjectiveName() {
        return getObjectiveName(this.world, this.getPos());
    }

    public static String getObjectiveName(@Nullable World world, BlockPos pos) {
        String coordinates = pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
        if (world == null || world.getRegistryKey() == World.OVERWORLD) return "steveparty_" + coordinates;
        Identifier dimension = world.getRegistryKey().getValue();
        String name = dimension.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? dimension.getPath() : dimension.toString();
        return "steveparty_" + name.replaceAll("[^A-Za-z0-9_.+-]", ".") + "_" + coordinates;
    }

    private boolean isPowered() {
        BlockState state = getCachedState();
        return state.contains(POWERED) && state.get(POWERED);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);

        if (nbt.contains("Selector", NbtElement.STRING_TYPE)) {
            this.selector = nbt.getString("Selector");
        }
        if (nbt.contains("Goal", NbtElement.STRING_TYPE)) {
            this.goal = nbt.getString("Goal");
        }
        this.resetSidePowered = nbt.getBoolean("ResetSidePowered");

        // Load lastScores
        this.lastScores.clear();
        if (nbt.contains("LastScores", NbtElement.COMPOUND_TYPE)) {
            NbtCompound scoresNbt = nbt.getCompound("LastScores");
            for (String key : scoresNbt.getKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int value = scoresNbt.getInt(key);
                    this.lastScores.put(uuid, value);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putString("Selector", selector);
        nbt.putString("Goal", goal);
        nbt.putBoolean("ResetSidePowered", resetSidePowered);

        // Save lastScores
        NbtCompound scoresNbt = new NbtCompound();
        for (var entry : lastScores.entrySet()) {
            scoresNbt.putInt(entry.getKey().toString(), entry.getValue());
        }
        nbt.put("LastScores", scoresNbt);
    }


    public void openScreen(ServerPlayerEntity player) {
        player.openHandledScreen(this);
    }

    @Override
    public GoalPoleBasePayload getScreenOpeningData(ServerPlayerEntity serverPlayerEntity) {
        return new GoalPoleBasePayload(this.getPos(), this.getSelector(), this.getGoal());
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.steveparty.goal_pole_base");
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new GoalPoleBaseScreenHandler(syncId, playerInventory, this);
    }

    /**
     * Ensure a scoreboard objective exists for the goal.
     */
    public void setupScoreboard() {
        if (this.goal.isEmpty() || this.world == null || this.world.isClient) return;

        MinecraftServer server = this.world.getServer();
        if (server == null) return;

        Scoreboard scoreboard = server.getScoreboard();

        String objectiveName = getObjectiveName();

        Optional<ScoreboardCriterion> criterion = parseGoal(goal);
        if (criterion.isEmpty()) return;

        ScoreboardObjective objective = scoreboard.getNullableObjective(objectiveName);
        if (objective != null && !objective.getCriterion().getName().equals(criterion.get().getName())) {
            // Left over with another goal (or made by hand under this name): the base owns this name
            scoreboard.removeObjective(objective);
            objective = null;
        }
        if (objective == null) {
            objective = scoreboard.addObjective(
                    objectiveName,
                    criterion.get(),
                    Text.literal("Goal: " + goal),
                    ScoreboardCriterion.RenderType.INTEGER,
                    true,
                    null
            );
        }
        this.cachedObjective = objective;
        this.totalScoreTick = Long.MIN_VALUE;
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
     * Command source used to evaluate the selector: positioned at this base, in its world, with command-block
     * permissions (so {@code @p} is relative to the base and the selector can't do more than a command block).
     */
    private ServerCommandSource createSelectorSource(MinecraftServer server) {
        ServerWorld serverWorld = this.world instanceof ServerWorld sw ? sw : server.getOverworld();
        return new ServerCommandSource(
                CommandOutput.DUMMY,
                Vec3d.ofCenter(this.getPos()),
                Vec2f.ZERO,
                serverWorld,
                SELECTOR_PERMISSION_LEVEL,
                "GoalPole",
                Text.literal("Goal Pole"),
                server,
                null
        );
    }

    /** @return the parsed selector, or null if the string is not a valid selector (it may be a player name). */
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

    public List<ServerPlayerEntity> resolveSelector(MinecraftServer server, String selector) {
        if (selector == null || selector.isEmpty()) return Collections.emptyList();

        EntitySelector entitySelector = getParsedSelector(selector);
        if (entitySelector != null) {
            try {
                return entitySelector.getPlayers(createSelectorSource(server));
            } catch (CommandSyntaxException e) {
                return Collections.emptyList();
            }
        }
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(selector);
        return player != null ? List.of(player) : Collections.emptyList();
    }

    /**
     * @param forceRefresh re-evaluate the selector, at most once per tick (the base and every pole above it share
     *                     the result of that tick)
     */
    public List<ServerPlayerEntity> getTrackedPlayers(boolean forceRefresh) {
        MinecraftServer server = this.world != null ? this.world.getServer() : null;
        if (server == null) return List.of();

        long now = this.world.getTime();
        boolean stale = forceRefresh && now != this.trackedPlayersTick;
        if (stale || this.cacheSelectedPlayers == null || this.cacheSelectedPlayers.isEmpty()) {
            this.cacheSelectedPlayers = new ArrayList<>(resolveSelector(server, this.selector));
            this.trackedPlayersTick = now;
        }
        return this.cacheSelectedPlayers;
    }

    /**
     * Sum of the tracked players' scores, computed at most once per tick. Players without a score count as 0 (no
     * score is created for them); the sum saturates instead of overflowing.
     */
    public int getTrackedTotalScore() {
        if (this.world == null || this.cachedObjective == null) return 0;
        long now = this.world.getTime();
        if (now != this.totalScoreTick) {
            this.totalScoreTick = now;
            Scoreboard scoreboard = this.world.getScoreboard();
            long sum = 0;
            for (ServerPlayerEntity player : getTrackedPlayers(true)) {
                ReadableScoreboardScore score = scoreboard.getScore(player, this.cachedObjective);
                if (score != null) sum += score.getScore();
            }
            this.totalScore = (int) Math.clamp(sum, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        return this.totalScore;
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (this.world.isClient) return;
        if (this.goal.isEmpty() || this.selector.isEmpty()) return;
        // Unpowered = paused: the objective was removed by pauseGoal and must not come back until resumeGoal
        if (!state.get(POWERED)) return;
        if (this.cachedObjective != null
                && world.getScoreboard().getNullableObjective(this.cachedObjective.getName()) != this.cachedObjective) {
            // Removed from outside (/scoreboard objectives remove): recreated below, the scores start again from 0
            this.cachedObjective = null;
        }
        if (this.cachedObjective == null) {
            // An invalid goal (unknown criterion) is retried once a second, not every tick
            if (world.getTime() < this.nextSetupAttemptTick) return;
            setupScoreboard();
            if (this.cachedObjective == null) {
                this.nextSetupAttemptTick = world.getTime() + 20;
                return;
            }
        }

        List<ServerPlayerEntity> players = getTrackedPlayers(true);
        if (players.isEmpty()) return;

        for (ServerPlayerEntity player : players) {
            var scoreboard = player.getServer().getScoreboard();
            var score = scoreboard.getOrCreateScore(player, this.cachedObjective);

            long gained = trackScore(player.getUuid(), score.getScore());
            if (gained > 0) {
                pulseRedstone();
                spawnFloatingText((ServerWorld) this.world, "+" + gained, pos.toCenterPos().add(0.5).add(Math.random() - 1, Math.random() / 2, Math.random() - 1).toVector3f(), TextColor.fromRgb(0xC90E0E), 50);
            }
        }
    }

    /**
     * Remembers a player's score.
     * @return how much it went up since last time (0 the first time: that's the baseline, and when it goes down)
     */
    public long trackScore(UUID player, int current) {
        Integer last = this.lastScores.get(player);
        // Never tracked before: baseline, no pulse yet (-1 is a valid score, not a "missing" marker)
        if (last == null || current != last) {
            // Also follow the score down (/scoreboard, another reset): otherwise no pulse until it exceeds the old value
            this.lastScores.put(player, current);
            markDirty();
        }
        return last == null ? 0 : Math.max(0, (long) current - last);
    }

    public void pauseGoal() {
        if (removeObjective()) markDirty();
    }

    /**
     * Removes this base's objective: the cached one, or the one saved under its name (a base paused or broken just
     * after its chunk loaded has not cached it yet).
     * @return whether an objective was removed
     */
    private boolean removeObjective() {
        if (this.world == null || this.world.isClient || this.world.getServer() == null) return false;
        Scoreboard scoreboard = this.world.getServer().getScoreboard();
        ScoreboardObjective objective = this.cachedObjective != null ? this.cachedObjective
                : scoreboard.getNullableObjective(getObjectiveName());
        this.cachedObjective = null;
        if (objective == null || scoreboard.getNullableObjective(objective.getName()) != objective) return false;
        scoreboard.removeObjective(objective);
        return true;
    }

    public void resumeGoal() {
        if (this.goal.isEmpty() || this.world == null || this.world.isClient) return;

        setupScoreboard(); // Recreate objective
        if (this.cachedObjective == null) return;

        MinecraftServer server = this.world.getServer();
        if (server == null) return;
        Scoreboard scoreboard = server.getScoreboard();

        // Restore saved scores
        for (var entry : lastScores.entrySet()) {
            UUID uuid = entry.getKey();
            int score = entry.getValue();

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                scoreboard.getOrCreateScore(player, this.cachedObjective).setScore(score);
            }
        }
        markDirty();
    }


    private void pulseRedstone() {
        if (world == null || world.isClient) return;

        // set comparator output to 15
        redstoneOutput = 15;
        world.updateComparators(pos, getCachedState().getBlock());

        // schedule a reset to 0 next tick (short pulse)
        world.scheduleBlockTick(pos, getCachedState().getBlock(), 2);
    }

    public void resetGoal() {
        if (this.goal.isEmpty() || this.selector.isEmpty() || this.world == null || this.world.isClient) return;
        // Paused (unpowered): no objective to clear, the remembered scores are reset and restored as 0 on resume
        if (this.cachedObjective == null && isPowered()) setupScoreboard();

        List<ServerPlayerEntity> players = getTrackedPlayers(true);
        if (players.isEmpty()) return;

        for (ServerPlayerEntity player : players) {
            if (this.cachedObjective != null) player.getServer().getScoreboard().removeScore(player, this.cachedObjective);
            this.lastScores.put(player.getUuid(), 0);
        }
        this.totalScoreTick = Long.MIN_VALUE;
        markDirty();
    }

    public void removeGoal() {
        removeObjective();
    }

    public void setRedstoneOutput(int value) {
        redstoneOutput = value;
    }

    public void update(String selector, String goal) {
        setSelector(selector);
        setGoal(goal);
    }

    /**
     * Updates the reset-side power state.
     * @return true only on a rising edge (unpowered -> powered), i.e. when a reset must be triggered.
     */
    public boolean updateResetSidePower(boolean powered) {
        boolean risingEdge = powered && !this.resetSidePowered;
        if (powered != this.resetSidePowered) {
            this.resetSidePowered = powered;
            markDirty();
        }
        return risingEdge;
    }

    public ScoreboardObjective getCachedObjective() {
        return cachedObjective;
    }
}
