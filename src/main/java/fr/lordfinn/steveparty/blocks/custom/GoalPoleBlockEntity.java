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
import net.minecraft.block.entity.BlockEntityTicker;
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import static fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlock.POWERED;
import static fr.lordfinn.steveparty.criteria.ModScoreboardCriteria.LANDED_ON_POLE;
import static fr.lordfinn.steveparty.sounds.ModSounds.GOAL_POLE_REACH;
import static fr.lordfinn.steveparty.utils.FloatingTextParticleHelper.spawnFloatingText;

public class GoalPoleBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<GoalPolePayload>, BlockEntityTicker {
    // --- Cached base ---
    private GoalPoleBaseBlockEntity cachedBase;
    /** Whether {@link #cachedBase} was looked up at least once (null then means "no base under this pole"). */
    private boolean baseResolved = false;
    private static final int BASE_RECHECK_TICKS = 20;
    private int redstoneOutput = 0;
    private int flagColor = FlagItem.NO_COLOR;
    private final Set<UUID> playersOnBlock = new HashSet<>();

    public void update(Comparator comparator, int value) {
        this.setValue(value);
        this.setComparator(comparator);
    }

    public void onPlayerArrive(ServerPlayerEntity player, BlockView view, BlockPos pos) {
        UUID uuid = player.getUuid();
        if (playersOnBlock.contains(uuid)) return;

        player.getScoreboard().forEachScore(
                LANDED_ON_POLE,
                ScoreHolder.fromProfile(player.getGameProfile()),
                scoreAccess -> scoreAccess.incrementScore(1)  // increment by 1
        );

        playersOnBlock.add(uuid);
        grantGoldenHeart(player);
        world.playSound(null, pos, GOAL_POLE_REACH, SoundCategory.BLOCKS, 1f, 1.2f);
        spawnFloatingText((ServerWorld) this.world,
                "1up", player.getPos().add(0,2,0).toVector3f(),
                0x43FA44, 50, 0.04f);
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

    // --- Cached base access ---
    public GoalPoleBaseBlockEntity getCachedBase() {
        // A pole without a base does not walk down its column every tick: neighbor updates refresh the cache,
        // and a slow re-check covers the changes that come without one (e.g. a base placed under an existing pole
        // notifies its neighbors with the old block, air)
        if (!baseResolved || (cachedBase != null && cachedBase.isRemoved())
                || (cachedBase == null && world != null && (world.getTime() + pos.getY()) % BASE_RECHECK_TICKS == 0)) {
            updateCachedBase();
        }
        return cachedBase;
    }

    public void updateCachedBase() {
        World world = getWorld();
        if (world == null || world.isClient) return;

        BlockPos currentPos = getPos();
        cachedBase = null;
        baseResolved = true;

        while (currentPos.getY() > world.getBottomY()) {
            currentPos = currentPos.down();
            var state = world.getBlockState(currentPos);
            if (state.getBlock() instanceof GoalPoleBaseBlock) {
                var be = world.getBlockEntity(currentPos);
                if (be instanceof GoalPoleBaseBlockEntity base) cachedBase = base;
                break;
            } else if (!(state.getBlock() instanceof GoalPoleBlock)) break;
        }
    }

    public void propagateCachedBaseUpwards() {
        World world = getWorld();
        if (world == null || world.isClient) return;

        if (!baseResolved) updateCachedBase();
        BlockPos.Mutable currentPos = getPos().mutableCopy().move(Direction.UP);
        while (!world.isOutOfHeightLimit(currentPos)) {
            // The poles stacked right above share this pole's base: no need for each of them to walk down again
            if (!(world.getBlockEntity(currentPos) instanceof GoalPoleBlockEntity pole)) break;
            pole.cachedBase = this.cachedBase;
            pole.baseResolved = true;
            currentPos.move(Direction.UP);
        }
    }

    // --- Comparator + Value NBT ---
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("Comparator", comparator.ordinal());
        nbt.putInt("Value", value);
        if (flagColor != FlagItem.NO_COLOR) nbt.putInt("FlagColor", flagColor);
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

    public void updateComparatorOutput() {
        if (world == null || world.isClient) return;

        GoalPoleBaseBlockEntity base = getCachedBase();
        boolean conditionMet = false;
        if (base != null && base.getCachedObjective() != null && base.getCachedState().get(POWERED)) {
            // Sum of the tracked players' scores, computed once per tick by the base for all the poles above it
            conditionMet = compare(comparator, base.getTrackedTotalScore(), value);
        }
        int output = conditionMet ? 15 : 0;
        // Comparators are only told when the signal changes (not every tick, by every segment of the pole)
        if (output != redstoneOutput) {
            setRedstoneOutput(output);
            world.updateComparators(pos, getCachedState().getBlock());
        }
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
    public void setComparator(Comparator comparator) { this.comparator = comparator; markDirty(); }

    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; markDirty(); }

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

    @Override
    public void tick(World world, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (world.isClient) return;
        updateComparatorOutput();
        if (playersOnBlock.isEmpty()) return;

        Iterator<UUID> iterator = playersOnBlock.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            PlayerEntity player = world.getPlayerByUuid(uuid);
            if (player == null || (!player.getBlockPos().down().equals(pos) && !player.getBlockPos().down().down().equals(pos))) {
                iterator.remove();
            }
        }
    }
}
