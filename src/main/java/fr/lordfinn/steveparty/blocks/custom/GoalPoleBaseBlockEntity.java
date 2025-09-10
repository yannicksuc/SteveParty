package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.payloads.custom.GoalPoleBasePayload;
import fr.lordfinn.steveparty.screen_handlers.custom.GoalPoleBaseScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.BlockPos;
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
        markDirty();
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        if (Objects.equals(goal, this.goal)) return;

        // Remove old objective if it exists
        if (this.cachedObjective != null && this.world != null && !this.world.isClient) {
            Scoreboard scoreboard = this.world.getServer().getScoreboard();
            scoreboard.removeObjective(this.cachedObjective);
            this.cachedObjective = null;
        }

        this.goal = goal;
        markDirty();

        if (!this.world.isClient) {
            setupScoreboard();
        }
    }

    private String getObjectiveName() {
        BlockPos pos = this.getPos();
        return "steveparty_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
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
        return Text.literal("Goal Pole");
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

        ScoreboardObjective objective = scoreboard.getNullableObjective(objectiveName);
        if (objective == null) {
            Optional<ScoreboardCriterion> criterion = ScoreboardCriterion.getOrCreateStatCriterion(goal);
            if (criterion.isEmpty()) return;

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
    }

    public List<ServerPlayerEntity> resolveSelector(MinecraftServer server, String selector) {
        if (selector == null || selector.isEmpty()) return Collections.emptyList();

        ServerCommandSource source = server.getCommandSource();

        try {
            return EntityArgumentType.players().parse(new StringReader(selector)).getPlayers(source);
        } catch (CommandSyntaxException e) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(selector);
            return player != null ? List.of(player) : Collections.emptyList();
        }
    }

    public List<ServerPlayerEntity> getTrackedPlayers(boolean forceRefresh) {
        MinecraftServer server = this.world != null ? this.world.getServer() : null;
        if (server == null) return List.of();

        if (forceRefresh || this.cacheSelectedPlayers == null || this.cacheSelectedPlayers.isEmpty()) {
            this.cacheSelectedPlayers = new ArrayList<>(resolveSelector(server, this.selector));
        }
        return this.cacheSelectedPlayers;
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (this.goal.isEmpty() || this.selector.isEmpty()) return;
        if (this.cachedObjective == null) setupScoreboard();
        if (this.cachedObjective == null) return;
        if (!this.world.getBlockState(pos).get(POWERED)) return;
        if (this.world.isClient) return;

        List<ServerPlayerEntity> players = getTrackedPlayers(true);
        if (players.isEmpty()) return;

        for (ServerPlayerEntity player : players) {
            var scoreboard = player.getServer().getScoreboard();
            var score = scoreboard.getOrCreateScore(player, this.cachedObjective);

            int current = score.getScore();
            int last = this.lastScores.getOrDefault(player.getUuid(), -1);

            // If we’ve never tracked this player before, initialize baseline
            if (last == -1) {
                this.lastScores.put(player.getUuid(), current);
                continue; // don’t pulse yet, wait for actual change
            }

            if (current > last) {
                pulseRedstone();
                spawnFloatingText((ServerWorld) this.world,  "+1", pos.toCenterPos().add(0.5).add(Math.random() - 1 ,  Math.random() / 2, Math.random() - 1).toVector3f(), TextColor.fromRgb(0xC90E0E), 50);
                this.lastScores.put(player.getUuid(), current);
            }
        }
    }

    public void pauseGoal() {
        if (this.cachedObjective == null || this.world == null || this.world.isClient) return;

        MinecraftServer server = this.world.getServer();
        if (server == null) return;
        Scoreboard scoreboard = server.getScoreboard();
        scoreboard.removeObjective(this.cachedObjective);
        this.cachedObjective = null;
        markDirty();
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
        if (this.goal.isEmpty() || this.selector.isEmpty()) return;
        if (this.cachedObjective == null) setupScoreboard();
        if (this.cachedObjective == null) return;

        List<ServerPlayerEntity> players = getTrackedPlayers(true);
        if (players.isEmpty()) return;

        for (ServerPlayerEntity player : players) {
            var scoreboard = player.getServer().getScoreboard();
            scoreboard.removeScore(player, this.cachedObjective);
            this.lastScores.put(player.getUuid(), 0);
        }
        this.cacheSelectedPlayers = resolveSelector(this.world.getServer(), this.selector);
    }

    public void removeGoal() {
        if (this.cachedObjective != null && this.world != null && !this.world.isClient) {
            Scoreboard scoreboard = this.world.getServer().getScoreboard();
            scoreboard.removeObjective(this.cachedObjective);
            this.cachedObjective = null;
        }
    }

    public void setRedstoneOutput(int value) {
        redstoneOutput = value;
    }

    public void update(String selector, String goal) {
        setSelector(selector);
        setGoal(goal);
    }

    public ScoreboardObjective getCachedObjective() {
        return cachedObjective;
    }
}
