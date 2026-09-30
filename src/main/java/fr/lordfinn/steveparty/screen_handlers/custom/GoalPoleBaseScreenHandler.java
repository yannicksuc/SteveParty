package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity;
import fr.lordfinn.steveparty.payloads.custom.GoalPoleBasePayload;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

public class GoalPoleBaseScreenHandler extends ScreenHandler {
    private GoalPoleBaseBlockEntity blockEntity = null;
    private final NbtCompound settings;
    private BlockPos pos;

    public GoalPoleBaseScreenHandler(int syncId, PlayerInventory playerInventory, GoalPoleBaseBlockEntity entity) {
        super(ModScreensHandlers.GOAL_POLE_BASE_SCREEN_HANDLER, syncId);
        this.blockEntity = entity;
        this.settings = entity.writeSettings();
    }

    public GoalPoleBaseScreenHandler(int syncId, PlayerInventory playerInventory, GoalPoleBasePayload payload) {
        super(ModScreensHandlers.GOAL_POLE_BASE_SCREEN_HANDLER, syncId);
        this.pos = payload.pos();
        this.settings = payload.settings();
    }

    public GoalPoleBaseBlockEntity getBlockEntity() {
        return blockEntity;
    }

    /** The base's settings when the screen opened (see {@link GoalPoleBaseBlockEntity#writeSettings}). */
    public NbtCompound getSettings() {
        return settings;
    }

    public String getSelector() {
        return settings.getString("Selector");
    }

    public String getGoal() {
        return settings.getString("Criterion");
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        // Client-side instance has no block entity; server-side, check the block still exists and is in reach
        return blockEntity == null || ScreenHandlerChecks.canUseBlockEntity(blockEntity, player);
    }

    public BlockPos getPos() {
        return this.blockEntity != null ? this.blockEntity.getPos() : pos;
    }
}
