package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.payloads.custom.HopSwitchPayload;
import fr.lordfinn.steveparty.screen_handlers.custom.HopSwitchScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.apache.commons.lang3.function.TriConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class HopSwitchBlockEntity extends CartridgeContainerBlockEntity implements ExtendedScreenHandlerFactory<BlockPosPayload> {
    private int durationTicks = 200; // par défaut 10s
    private int modeInt = 0; // persisted as int in NBT
    // Enum to represent the modes
    public enum Mode {
        SWITCH(0),
        FORCE_APPEAR(1),
        FORCE_DISAPPEAR(2);

        private final int id;

        Mode(int id) { this.id = id; }
        public int getId() { return id; }

        public static Mode fromId(int id) {
            for (Mode mode : values()) {
                if (mode.id == id) return mode;
            }
            return SWITCH; // default fallback
        }
    }

    private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> modeInt;
                case 1 -> durationTicks;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> setMode(Mode.fromId(value));
                case 1 -> setDurationTicks(value);
            }
        }

        @Override
        public int size() {
            return 2; // we track 2 values
        }
    };

    public PropertyDelegate getPropertyDelegate() {
        return propertyDelegate;
    }

    public HopSwitchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HOP_SWITCH_ENTITY, pos, state, 1);
    }
    @Override
    public void markDirty() {
        super.markDirty();
    }

    // -------------------------
    // NBT persistence
    // -------------------------
    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryManager) {
        super.writeNbt(nbt, registryManager);
        nbt.putInt("DurationTicks", durationTicks);
        nbt.putInt("Mode", modeInt); // persist the mode
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryManager) {
        super.readNbt(nbt, registryManager);
        if (nbt.contains("DurationTicks")) durationTicks = nbt.getInt("DurationTicks");
        if (nbt.contains("Mode")) modeInt = nbt.getInt("Mode");
    }

    private void forEachDestination(java.util.function.BiConsumer<SwitchyBlock, BlockPos> action) {
        var world = Objects.requireNonNull(getWorld());
        List<BlockPos> destinations = getDestinations(0);
        for (BlockPos destination : destinations) {
            BlockState state = world.getBlockState(destination);
            Block block = state.getBlock();
            if (block instanceof SwitchyBlock switcherBlock) {
                action.accept(switcherBlock, destination);
            }
        }
    }

    private void forEachDestination(TriConsumer<SwitchyBlock, BlockState, BlockPos> action) {
        var world = Objects.requireNonNull(getWorld());
        for (BlockPos destination : getDestinations(0)) {
            BlockState state = world.getBlockState(destination);
            Block block = state.getBlock();
            if (block instanceof SwitchyBlock switcherBlock) {
                action.accept(switcherBlock, state, destination);
            }
        }
    }

    public void switchDestinations() {
        forEachDestination((switcherBlock, state, pos) ->
                switcherBlock.trigger(state, getWorld(), pos)
        );
    }

    public void solidifyDestinations(boolean isSolid) {
        forEachDestination((switcherBlock, state, pos) ->
                switcherBlock.setSolid(state, getWorld(), pos, isSolid)
        );
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new HopSwitchScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public BlockPosPayload getScreenOpeningData(ServerPlayerEntity serverPlayerEntity) {
        return new BlockPosPayload(pos);
    }

    // -------------------------
    // Mode getters / setters
    // -------------------------
    public Mode getMode() {
        return Mode.fromId(modeInt);
    }

    public void setMode(Mode mode) {
        this.modeInt = mode.getId();
        markDirty();
    }

    // -------------------------
    // Duration
    // -------------------------
    public int getDurationTicks() {
        return durationTicks;
    }

    public void setDurationTicks(int durationTicks) {
        this.durationTicks = Math.max(20, durationTicks); // min = 1 seconde
        markDirty();
    }

    public int getDurationSeconds() {
        return durationTicks / 20;
    }

}
