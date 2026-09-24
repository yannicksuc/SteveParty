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

import java.util.HashMap;
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
    private boolean goalMet = false;
    private long goalMetTick = 0;
    /** Last tick each player touched the top of this pole (the block is told every tick while they stand on it). */
    private final Map<UUID, Long> standing = new HashMap<>();
    /** A player who left the pole for less than this is still "on it" (jumps on the spot are not new landings). */
    private static final long LANDING_GRACE_TICKS = 40;

    public void update(Comparator comparator, int value) {
        this.setValue(value);
        this.setComparator(comparator);
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

    private Comparator comparator = Comparator.EQUAL;
    private int value = 0;

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

    /** End of the tick it was loaded or placed in: finds its base and takes its total. */
    void onLoaded() {
        refreshFromBase();
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

    /** The base below pushes its total (null: no base). */
    void acceptTotal(@Nullable GoalPoleBaseBlockEntity base) {
        if (world == null || world.isClient) return;
        cachedBase = base;
        baseResolved = true;
        long newTotal = base != null ? base.getTotal() : 0;
        boolean met = base != null && compare(comparator, (int) Math.clamp(newTotal, Integer.MIN_VALUE, Integer.MAX_VALUE), value);
        int output = met ? 15 : 0;
        boolean changed = newTotal != total || met != goalMet;
        total = newTotal;
        if (met != goalMet) {
            goalMet = met;
            goalMetTick = world.getTime();
        }
        if (output != redstoneOutput) {
            redstoneOutput = output;
            world.updateComparators(pos, getCachedState().getBlock());
        }
        if (changed) {
            markDirty();
            sync();
        }
    }

    /** The goal of this pole changed: compare again with the base's total. */
    private void recompare() {
        if (world == null || world.isClient) return;
        acceptTotal(getCachedBase());
    }

    public long getTotal() {
        return total;
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
        nbt.putInt("Comparator", comparator.ordinal());
        nbt.putInt("Value", value);
        if (flagColor != FlagItem.NO_COLOR) nbt.putInt("FlagColor", flagColor);
        nbt.putLong("Total", total);
        nbt.putBoolean("GoalMet", goalMet);
        nbt.putLong("GoalMetTick", goalMetTick);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        if (nbt.contains("Comparator")) {
            int compId = nbt.getInt("Comparator");
            comparator = Comparator.values()[Math.max(0, Math.min(compId, Comparator.values().length - 1))];
        }
        if (nbt.contains("Value")) {
            value = nbt.getInt("Value");
        }
        flagColor = nbt.contains("FlagColor", NbtElement.INT_TYPE) ? nbt.getInt("FlagColor") & 0xFFFFFF : FlagItem.NO_COLOR;
        total = nbt.getLong("Total");
        goalMet = nbt.getBoolean("GoalMet");
        goalMetTick = nbt.getLong("GoalMetTick");
        // Same signal as before the chunk was unloaded: no spurious comparator pulse on load
        redstoneOutput = goalMet ? 15 : 0;
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

    /** Sends this block entity's data to the players watching it. */
    private void sync() {
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
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
        return new GoalPolePayload(this.getPos(), this.comparator, this.value);
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
