package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.screen_handlers.custom.LootingBoxScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static net.minecraft.text.Text.literal;

public class LootingBoxBlockEntity extends CartridgeContainerBlockEntity implements ExtendedScreenHandlerFactory<BlockPosPayload> {
    private int repeatTime = 1; // Number of time you can hit the block for cooldown
    private int cooldownTime = 60; // (3sec by default)
    private static final Map<PlayerEntity, Boolean> playerJumpState = new HashMap<>();

    public LootingBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOOTING_BOX_ENTITY, pos, state, 1);
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
        nbt.putInt("RepeatTime", repeatTime);
        nbt.putInt("CooldownTime", cooldownTime);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryManager) {
        super.readNbt(nbt, registryManager);
        if (nbt.contains("RepeatTime")) repeatTime = nbt.getInt("RepeatTime");
        if (nbt.contains("CooldownTime")) cooldownTime = nbt.getInt("CooldownTime");
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new LootingBoxScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public BlockPosPayload getScreenOpeningData(ServerPlayerEntity serverPlayerEntity) {
        return new BlockPosPayload(pos);
    }

    //getters and setters
    public int getCooldownTime() {
        return cooldownTime;
    }
    public void setCooldownTime(int cooldownTime) {
        this.cooldownTime = cooldownTime;
    }

    public int getRepeatTime() {
        return repeatTime;
    }

    public void setRepeatTime(int repeatTime) {
        this.repeatTime = repeatTime;
    }

    public void trigger(ServerPlayerEntity player) {
        boolean state = playerJumpState.getOrDefault(player, false);
        if (!state) {
            player.sendMessage(literal("Bravo t'as touché"), false);
            playerJumpState.put(player, true);
        }
    }

    public static void enableTriggering(ServerPlayerEntity player) {
        playerJumpState.put(player, false);
     }
}
