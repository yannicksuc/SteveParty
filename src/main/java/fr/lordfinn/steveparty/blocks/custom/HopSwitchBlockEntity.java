package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.screen_handlers.custom.HopSwitchScreenHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class HopSwitchBlockEntity extends CartridgeContainerBlockEntity {
    private int durationTicks = 200; // par défaut 10s

    public HopSwitchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HOP_SWITCH_ENTITY, pos, state, 1);
    }
    @Override
    public void markDirty() {
        super.markDirty();
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryManager) {
        super.writeNbt(nbt, registryManager);
        nbt.putInt("DurationTicks", durationTicks);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryManager) {
        super.readNbt(nbt, registryManager);
        if (nbt.contains("DurationTicks")) {
            durationTicks = nbt.getInt("DurationTicks");
        }
    }

    public void switchDestinations() {
        List<BlockPos> destinations = getDestinations(0);
        for (BlockPos destination : destinations) {
            BlockState state = Objects.requireNonNull(getWorld()).getBlockState(destination);
            Block block = state.getBlock();
            if (block instanceof SwitchyBlock switcherBlock) {
                switcherBlock.trigger(state, getWorld(), destination);
            }
        }
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new HopSwitchScreenHandler(syncId, playerInventory, this);
    }

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
