package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.payloads.custom.GoalPolePayload;
import fr.lordfinn.steveparty.screen_handlers.custom.GoalPoleScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.FlagItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static fr.lordfinn.steveparty.criteria.ModScoreboardCriteria.LANDED_ON_POLE;
import static fr.lordfinn.steveparty.sounds.ModSounds.GOAL_POLE_REACH;
import static fr.lordfinn.steveparty.utils.FloatingTextParticleHelper.spawnFloatingText;

/**
 * A goal pole segment. It never ticks: its base pushes it the total ({@link #acceptTotal}), and it compares it with
 * its goal to set its comparator output. A landing on it is recognised with a timestamp per player (the block is told
 * every tick while a player stands on it), and handed to its base through {@link GoalPoleNetwork}.
 */
public class GoalPoleBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<GoalPolePayload> {
    // --- Cached base ---
    private GoalPoleBaseBlockEntity cachedBase;
    /** Whether {@link #cachedBase} was looked up (null then means "no base under this pole"). */
    private boolean baseResolved = false;
    private int redstoneOutput = 0;
    private int flagColor = FlagItem.NO_COLOR;
    /** Total of the base below, as last pushed (0 without a base). */
    private long total = 0;
    /** Whether a base stands under this pole (synced: the progress above the ball is only shown then). */
    private boolean linked = false;
    private boolean goalMet = false;
    private long goalMetTick = 0;
    /** Last tick each player touched the top of this pole (the block is told every tick while they stand on it). */
    private final Map<UUID, Long> standing = new HashMap<>();
    /** A player who left the pole for less than this is still "on it" (jumps on the spot are not new landings). */
    private static final long LANDING_GRACE_TICKS = 40;

    /** Sets this segment's goal only (see {@link #applyGoal} for the pole's setting, whole column or per segment). */
    public void update(Comparator comparator, int value) {
        this.comparator = comparator;
        this.value = value;
        markDirty();
        sync();
        recompare();
    }

    /**
     * A player touches the top of this pole (every tick while they stand on it).
     * @return true for a new landing (not touched in the last {@link #LANDING_GRACE_TICKS} ticks)
     */
    public boolean onPlayerTouch(ServerPlayerEntity player) {
        if (world == null) return false;
        long now = world.getTime();
        Long last = standing.put(player.getUuid(), now);
        if (standing.size() > 16) standing.values().removeIf(tick -> now - tick > LANDING_GRACE_TICKS);
        return last == null || now - last > LANDING_GRACE_TICKS;
    }

    public void onPlayerArrive(ServerPlayerEntity player, BlockView view, BlockPos pos) {
        if (!onPlayerTouch(player)) return;

        player.getScoreboard().forEachScore(
                LANDED_ON_POLE,
                ScoreHolder.fromProfile(player.getGameProfile()),
                scoreAccess -> scoreAccess.incrementScore(1)  // increment by 1
        );

        grantGoldenHeart(player);
        world.playSound(null, pos, GOAL_POLE_REACH, SoundCategory.BLOCKS, 1f, 1.2f);
        spawnFloatingText((ServerWorld) this.world,
                "1up", player.getPos().add(0,2,0).toVector3f(),
                0x43FA44, 50, 0.04f);
        GoalPoleNetwork.onLanding(this, player);
    }

    /**
     * The "1up": one golden heart (2 absorption points) for 10 seconds. The absorption the player already has is
     * kept: subtracting 2 from whatever the effect left used to take hearts away from players who already had
     * absorption (golden apple, or landing again while the previous heart was still there).
     */
    public static void grantGoldenHeart(PlayerEntity player) {
        float before = player.getAbsorptionAmount();
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 0, false, true));
        player.setAbsorptionAmount(Math.max(before, 2.0F));
    }

    // --- Comparator + Value fields ---
    public enum Comparator {
        LESS_OR_EQUAL,
        GREATER_OR_EQUAL,
        EQUAL,
        GREATER,
        LESS
    }

    /** 2: goal per column or per segment, "at least 1" by default (1 or missing: every segment had its own goal). */
    public static final int VERSION = 2;
    /** New poles: signal once the total reaches 1 (the old default, "equals 0", was met before anyone scored). */
    private Comparator comparator = Comparator.GREATER_OR_EQUAL;
    private int value = 1;
    /** Whether the column's segments each have their own goal (advanced), instead of one goal for the whole pole. */
    private boolean perSegment = false;
    /** How the pole's flags show the progress (a setting of the whole pole). */
    private boolean flagSteps = false;
    /**
     * The goal is each player's own: a player whose own points reach it fires the pole once (a comparator pulse, and
     * the highest free place of the linked podiums: see {@code Podiums}). A setting of the whole pole.
     */
    private boolean perPlayer = false;
    /** The holders who reached this segment's per-player goal since the last reset. */
    private final java.util.Set<String> reached = new java.util.LinkedHashSet<>();
    /** Game ticks of the comparator pulse of a per-player goal reached. */
    public static final int PLAYER_GOAL_PULSE_TICKS = 4;
    /** Loaded from before the column setting: its column decides once whether its goals were all the same. */
    private boolean legacyGoal = false;
    /** Placed, not loaded: takes the settings of the column it joins. */
    private boolean fresh = true;

    public GoalPoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GOAL_POLE_ENTITY, pos, state);
    }

    @Nullable
    @Override
    public BlockEntityType<?> getType() {
        return ModBlockEntities.GOAL_POLE_ENTITY;
    }

    // --- Lifecycle ---
    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        if (!world.isClient) GoalPoleNetwork.schedule(this);
    }

    /** End of the tick it was loaded or placed in: joins its column's settings, finds its base, takes its total. */
    void onLoaded() {
        if (world == null || world.isClient) return;
        List<GoalPoleBlockEntity> column = column(world, pos);
        if (fresh) {
            fresh = false;
            // A segment added to a pole takes the pole's goal
            for (GoalPoleBlockEntity other : column) {
                if (other != this && !other.fresh) {
                    comparator = other.comparator;
                    value = other.value;
                    perSegment = other.perSegment;
                    flagSteps = other.flagSteps;
                    perPlayer = other.perPlayer;
                    markDirty();
                    break;
                }
            }
        }
        consolidate(column);
        refreshFromBase();
    }

    // --- Column ---

    /** The segments of the pole column this position is part of, bottom first. */
    public static List<GoalPoleBlockEntity> column(World world, BlockPos anySegment) {
        BlockPos.Mutable cursor = anySegment.mutableCopy();
        while (!world.isOutOfHeightLimit(cursor.getY() - 1) && world.getBlockState(cursor.down()).getBlock() instanceof GoalPoleBlock) {
            cursor.move(Direction.DOWN);
        }
        List<GoalPoleBlockEntity> segments = new ArrayList<>();
        while (!world.isOutOfHeightLimit(cursor) && world.getBlockEntity(cursor) instanceof GoalPoleBlockEntity segment) {
            segments.add(segment);
            cursor.move(Direction.UP);
        }
        return segments;
    }

    /**
     * Poles from before the column setting: when all their segments have the same goal, the column has one goal;
     * otherwise each segment keeps its own (per segment mode). Done once, then saved.
     */
    static void consolidate(List<GoalPoleBlockEntity> column) {
        boolean legacy = false;
        for (GoalPoleBlockEntity segment : column) legacy |= segment.legacyGoal;
        if (!legacy) return;
        boolean allSame = true;
        GoalPoleBlockEntity first = column.getFirst();
        for (GoalPoleBlockEntity segment : column) {
            allSame &= segment.comparator == first.comparator && segment.value == first.value;
        }
        for (GoalPoleBlockEntity segment : column) {
            segment.legacyGoal = false;
            segment.perSegment = !allSame;
            segment.markDirty();
            segment.sync();
        }
    }

    /**
     * The pole's goal from the screen. One goal for the whole pole: every segment gets it. A goal per segment: only
     * this segment changes (the others keep theirs). The mode is the column's.
     */
    public void applyGoal(Comparator comparator, int value, boolean perSegment) {
        if (world == null || world.isClient) {
            update(comparator, value);
            return;
        }
        List<GoalPoleBlockEntity> column = column(world, pos);
        consolidate(column);
        for (GoalPoleBlockEntity segment : column) {
            segment.perSegment = perSegment;
            if (!perSegment || segment == this) {
                segment.comparator = comparator;
                segment.value = value;
            }
            segment.markDirty();
            segment.sync();
            segment.recompare();
        }
    }

    public boolean isPerSegment() {
        return perSegment;
    }

    /**
     * How the flags of this pole move: false, a flag slides down once its goal is met; true, it steps down one notch
     * per point towards its goal (the progress shows on the pole). A setting of the whole pole.
     */
    public boolean isFlagSteps() {
        return flagSteps;
    }

    /** Sets how the flags move, for the whole pole. */
    public void applyFlagSteps(boolean steps) {
        if (world == null || world.isClient) {
            flagSteps = steps;
            return;
        }
        for (GoalPoleBlockEntity segment : column(world, pos)) {
            if (segment.flagSteps == steps) continue;
            segment.flagSteps = steps;
            segment.markDirty();
            segment.sync();
        }
    }

    /** Whether the goal is each player's own (see {@link #perPlayer}). A setting of the whole pole. */
    public boolean isPerPlayer() {
        return perPlayer;
    }

    /** Sets whose points the goal is about (each player's own, or everybody's total), for the whole pole. */
    public void applyPerPlayer(boolean each) {
        if (world == null || world.isClient) {
            perPlayer = each;
            return;
        }
        for (GoalPoleBlockEntity segment : column(world, pos)) {
            if (segment.perPlayer == each) continue;
            segment.perPlayer = each;
            segment.reached.clear();
            if (segment.redstoneOutput != 0) {
                segment.redstoneOutput = 0;
                world.updateComparators(segment.pos, segment.getCachedState().getBlock());
            }
            segment.markDirty();
            segment.sync();
            segment.recompare();
        }
    }

    /**
     * The own points of a holder changed (per-player goal).
     *
     * @return true when this makes him reach the goal, the first time since the last reset: the pole fires (a chime, a
     * comparator pulse)
     */
    public boolean acceptPlayerPoints(String holder, int points) {
        if (world == null || world.isClient || !perPlayer) return false;
        if (!compare(comparator, points, value)) {
            if (reached.remove(holder)) markDirty();
            return false;
        }
        if (!reached.add(holder)) return false;
        markDirty();
        redstoneOutput = 15;
        world.updateComparators(pos, getCachedState().getBlock());
        world.scheduleBlockTick(pos, getCachedState().getBlock(), PLAYER_GOAL_PULSE_TICKS);
        world.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 1f, 1.19f);
        return true;
    }

    /** The holders who reached the per-player goal of this segment since the last reset. */
    public java.util.Set<String> getReached() {
        return java.util.Collections.unmodifiableSet(reached);
    }

    /** The base was reset: the per-player goal can be reached again by everyone. */
    public void clearReached() {
        if (reached.isEmpty()) return;
        reached.clear();
        markDirty();
    }

    /** End of the comparator pulse of a per-player goal. */
    public void endPlayerGoalPulse() {
        if (!perPlayer || redstoneOutput == 0 || world == null) return;
        redstoneOutput = 0;
        world.updateComparators(pos, getCachedState().getBlock());
    }

    /**
     * Share of the way to this segment's goal, 0 to 1 (1 when met): the total over the number to reach; goals that
     * are not a number to reach ("less than") are all or nothing.
     */
    public float progressFraction() {
        if (goalMet) return 1f;
        Long target = displayTarget();
        if (target == null || target <= 0 || total <= 0) return 0f;
        return Math.min(1f, (float) total / target);
    }

    // --- Base ---
    public GoalPoleBaseBlockEntity getCachedBase() {
        if (!baseResolved || (cachedBase != null && cachedBase.isRemoved())) updateCachedBase();
        return cachedBase;
    }

    public void updateCachedBase() {
        World world = getWorld();
        if (world == null || world.isClient) return;
        BlockPos.Mutable cursor = getPos().mutableCopy();
        cachedBase = null;
        baseResolved = true;
        while (cursor.getY() > world.getBottomY()) {
            cursor.move(Direction.DOWN);
            BlockState state = world.getBlockState(cursor);
            if (state.getBlock() instanceof GoalPoleBaseBlock) {
                if (world.getBlockEntity(cursor) instanceof GoalPoleBaseBlockEntity base) cachedBase = base;
                break;
            } else if (!(state.getBlock() instanceof GoalPoleBlock)) break;
        }
    }

    /**
     * The column changed around this pole: looks for the base again and takes its total, for this pole and every
     * pole stacked on it.
     */
    public void refreshFromBase() {
        if (world == null || world.isClient) return;
        updateCachedBase();
        if (cachedBase != null) {
            cachedBase.pushTotal();
            return;
        }
        // No base: this pole and the ones above give nothing
        BlockPos.Mutable cursor = getPos().mutableCopy();
        while (!world.isOutOfHeightLimit(cursor) && world.getBlockEntity(cursor) instanceof GoalPoleBlockEntity pole) {
            pole.cachedBase = null;
            pole.baseResolved = true;
            pole.acceptTotal(null);
            cursor.move(Direction.UP);
        }
    }

    /**
     * The base below pushes its total (null: no base).
     * @return true when this pushes the pole's goal from not met to met
     */
    boolean acceptTotal(@Nullable GoalPoleBaseBlockEntity base) {
        if (world == null || world.isClient) return false;
        cachedBase = base;
        baseResolved = true;
        // A per-player goal shows the best player's own points, and only pulses its comparator when a player reaches it
        long newTotal = base == null ? 0 : perPlayer ? base.getBestPoints() : base.getTotal();
        boolean met = base != null && compare(comparator, (int) Math.clamp(newTotal, Integer.MIN_VALUE, Integer.MAX_VALUE), value);
        int output = perPlayer ? redstoneOutput : met ? 15 : 0;
        boolean changed = newTotal != total || met != goalMet || linked != (base != null);
        // Clients see the total only above the top segment and on flags going down point by point: other segments
        // send nothing when only the total changed
        boolean shown = met != goalMet || linked != (base != null) || (newTotal != total && (isTop() || flagSteps));
        boolean justMet = met && !goalMet && !perPlayer;
        total = newTotal;
        linked = base != null;
        if (met != goalMet) {
            goalMet = met;
            goalMetTick = world.getTime();
        }
        if (output != redstoneOutput) {
            redstoneOutput = output;
            world.updateComparators(pos, getCachedState().getBlock());
        }
        if (changed) markDirty();
        if (shown) sync();
        return justMet;
    }

    /** The goal of this pole changed: compare again with the base's total. */
    private void recompare() {
        if (world == null || world.isClient) return;
        acceptTotal(getCachedBase());
    }

    public long getTotal() {
        return total;
    }

    /** Whether a base stands under this pole (its total is then the base's). */
    public boolean isLinked() {
        return linked;
    }

    /**
     * Progress towards this pole's goal, 0 to 15 (15 when met). For "at least N" and "more than N" it grows with the
     * total; for the other comparisons it is all or nothing.
     */
    public int progressLevel() {
        if (goalMet) return 15;
        long target = switch (comparator) {
            case GREATER_OR_EQUAL, EQUAL -> value;
            case GREATER -> (long) value + 1;
            default -> 0;
        };
        if (target <= 0 || total <= 0) return 0;
        return (int) Math.clamp(15 * total / target, 0, 14);
    }

    /** The number the progress display aims at ("3 / 5"), or null when the goal is not a threshold to reach. */
    @Nullable
    public Long displayTarget() {
        return switch (comparator) {
            case GREATER_OR_EQUAL, EQUAL -> (long) value;
            case GREATER -> (long) value + 1;
            default -> null;
        };
    }

    /** Whether this pole's goal is met (its comparator signal is on). Synced to clients. */
    public boolean isGoalMet() {
        return goalMet;
    }

    /** World time of the last change of {@link #isGoalMet()}. */
    public long getGoalMetTick() {
        return goalMetTick;
    }

    // --- Comparator + Value NBT ---
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("Version", VERSION);
        nbt.putInt("Comparator", comparator.ordinal());
        nbt.putInt("Value", value);
        nbt.putBoolean("PerSegment", perSegment);
        nbt.putBoolean("FlagSteps", flagSteps);
        nbt.putBoolean("PerPlayer", perPlayer);
        if (!reached.isEmpty()) {
            net.minecraft.nbt.NbtList list = new net.minecraft.nbt.NbtList();
            reached.forEach(holder -> list.add(net.minecraft.nbt.NbtString.of(holder)));
            nbt.put("Reached", list);
        }
        if (legacyGoal) nbt.putBoolean("LegacyGoal", true);
        if (flagColor != FlagItem.NO_COLOR) nbt.putInt("FlagColor", flagColor);
        nbt.putLong("Total", total);
        nbt.putBoolean("Linked", linked);
        nbt.putBoolean("GoalMet", goalMet);
        nbt.putLong("GoalMetTick", goalMetTick);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        fresh = false;
        perSegment = nbt.getBoolean("PerSegment");
        flagSteps = nbt.getBoolean("FlagSteps");
        perPlayer = nbt.getBoolean("PerPlayer");
        reached.clear();
        net.minecraft.nbt.NbtList reachedNbt = nbt.getList("Reached", NbtElement.STRING_TYPE);
        for (int i = 0; i < reachedNbt.size(); i++) reached.add(reachedNbt.getString(i));
        // Saved before the column setting (or not consolidated yet): its column decides when it loads
        legacyGoal = nbt.getInt("Version") < VERSION || nbt.getBoolean("LegacyGoal");
        if (nbt.contains("Comparator")) {
            int compId = nbt.getInt("Comparator");
            comparator = Comparator.values()[Math.max(0, Math.min(compId, Comparator.values().length - 1))];
        }
        if (nbt.contains("Value")) {
            value = nbt.getInt("Value");
        }
        flagColor = nbt.contains("FlagColor", NbtElement.INT_TYPE) ? nbt.getInt("FlagColor") & 0xFFFFFF : FlagItem.NO_COLOR;
        total = nbt.getLong("Total");
        // Saved before the flag existed: poles with a total had a base
        linked = nbt.contains("Linked") ? nbt.getBoolean("Linked") : total != 0;
        goalMet = nbt.getBoolean("GoalMet");
        goalMetTick = nbt.getLong("GoalMetTick");
        // Same signal as before the chunk was unloaded: no spurious comparator pulse on load
        redstoneOutput = goalMet && !perPlayer ? 15 : 0;
    }

    // --- Client sync (the flag colour) ---
    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    private boolean isTop() {
        BlockState state = getCachedState();
        return state.contains(GoalPoleBlock.TOP) && state.get(GoalPoleBlock.TOP);
    }

    /** Sends this block entity's data to the players watching it, once at the end of the tick however many changes. */
    private void sync() {
        if (world != null && !world.isClient) GoalPoleNetwork.requestSync(this);
    }

    // --- Flag colour ---
    /** @return the colour of the flag on this pole (0xRRGGBB), or {@link FlagItem#NO_COLOR} for the original red one. */
    public int getFlagColor() {
        return flagColor;
    }

    /** Sets the flag's colour (kept while the flag is turned; the flag item carries it when the flag comes off). */
    public void setFlagColor(int color) {
        int normalized = color == FlagItem.NO_COLOR ? FlagItem.NO_COLOR : color & 0xFFFFFF;
        if (normalized == flagColor) return;
        flagColor = normalized;
        markDirty();
        sync();
    }

    /** The flag item this pole's flag drops as: same colour. */
    public ItemStack createFlagStack() {
        return FlagItem.withColor(new ItemStack(ModItems.FLAG), flagColor);
    }

    public static boolean compare(Comparator comparator, int total, int value) {
        return switch (comparator) {
            case LESS_OR_EQUAL -> total <= value;
            case GREATER_OR_EQUAL -> total >= value;
            case EQUAL -> total == value;
            case GREATER -> total > value;
            case LESS -> total < value;
        };
    }

    public void setRedstoneOutput(int value) {
        redstoneOutput = value;
    }
    public int getRedstoneOutput() { return redstoneOutput; }

    // --- Getters & setters ---
    public Comparator getComparator() { return comparator; }
    public void setComparator(Comparator comparator) { this.comparator = comparator; markDirty(); recompare(); }

    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; markDirty(); recompare(); }

    // --- ExtendedScreenHandlerFactory ---
    public void openScreen(ServerPlayerEntity player) {
        player.openHandledScreen(this);
    }

    @Override
    public GoalPolePayload getScreenOpeningData(ServerPlayerEntity player) {
        if (world != null && !world.isClient) consolidate(column(world, pos));
        return new GoalPolePayload(this.getPos(), this.comparator, this.value, this.perSegment, this.flagSteps, this.perPlayer);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.steveparty.goal_pole");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, net.minecraft.entity.player.PlayerEntity player) {
        return new GoalPoleScreenHandler(syncId, playerInventory, this);
    }


}
