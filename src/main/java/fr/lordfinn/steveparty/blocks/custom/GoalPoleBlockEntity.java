package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.payloads.custom.GoalPolePayload;
import fr.lordfinn.steveparty.screen_handlers.custom.GoalPoleScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import static fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlock.POWERED;

public class GoalPoleBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<GoalPolePayload>, BlockEntityTicker {
    // --- Cached base ---
    private GoalPoleBaseBlockEntity cachedBase;
    private int redstoneOutput = 0;

    public void update(Comparator comparator, int value) {
        this.setValue(value);
        this.setComparator(comparator);
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
        if (cachedBase == null) updateCachedBase();
        return cachedBase;
    }

    public void updateCachedBase() {
        World world = getWorld();
        if (world == null || world.isClient) return;

        BlockPos currentPos = getPos();
        cachedBase = null;

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

        BlockPos currentPos = getPos().up();
        while (currentPos.getY() < world.getHeight()) {
            var be = world.getBlockEntity(currentPos);
            if (!(be instanceof GoalPoleBlockEntity pole)) break;
            pole.updateCachedBase();
            currentPos = currentPos.up();
        }
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        World world = getWorld();
        if (world == null || world.isClient) return;

        var above = world.getBlockEntity(getPos().up());
        if (above instanceof GoalPoleBlockEntity poleAbove) {
            poleAbove.updateCachedBase();
        }
    }

    // --- Comparator + Value NBT ---
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("Comparator", comparator.ordinal());
        nbt.putInt("Value", value);
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
    }

    public void updateComparatorOutput() {
        if (world == null || world.isClient) return;

        GoalPoleBaseBlockEntity base = getCachedBase();
        if (base == null || base.getCachedObjective() == null || !base.getCachedState().get(POWERED)) {
            setRedstoneOutput(0);
            return;
        }

        int totalScore = 0;

        var scoreboard = world.getServer().getScoreboard();
        // Sum scores of all tracked players
        for (ServerPlayerEntity player : base.getTrackedPlayers()) {
            var score = scoreboard.getOrCreateScore(player, base.getCachedObjective()).getScore();
            totalScore += score;
        }

        // Compare sum against this block's value
        boolean conditionMet = switch (comparator) {
            case LESS_OR_EQUAL -> totalScore <= value;
            case GREATER_OR_EQUAL -> totalScore >= value;
            case EQUAL -> totalScore == value;
            case GREATER -> totalScore > value;
            case LESS -> totalScore < value;
        };

        // Set comparator output
        setRedstoneOutput(conditionMet ? 15 : 0);

        world.updateComparators(pos, getCachedState().getBlock());
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
        return Text.literal("Goal Pole");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, net.minecraft.entity.player.PlayerEntity player) {
        return new GoalPoleScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        updateComparatorOutput();
    }
}
