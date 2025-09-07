package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity;
import fr.lordfinn.steveparty.payloads.custom.GoalPoleBasePayload;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

public class GoalPoleBaseScreenHandler extends ScreenHandler {
    private GoalPoleBaseBlockEntity blockEntity = null;
    private String selector;
    private String goal;
    private BlockPos pos;

    public GoalPoleBaseScreenHandler(int syncId, PlayerInventory playerInventory, GoalPoleBaseBlockEntity entity) {
        super(ModScreensHandlers.GOAL_POLE_BASE_SCREEN_HANDLER, syncId);
        this.blockEntity = entity;
        this.selector = this.blockEntity.getSelector();
        this.goal = this.blockEntity.getGoal();
    }

    public GoalPoleBaseScreenHandler(int syncId, PlayerInventory playerInventory, GoalPoleBasePayload blockPosPayload) {
        super(ModScreensHandlers.GOAL_POLE_BASE_SCREEN_HANDLER, syncId);
        this.pos = blockPosPayload.pos();
        this.selector = blockPosPayload.selector();
        this.goal = blockPosPayload.goal();
    }

    public GoalPoleBaseBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        this.selector = selector;
        if (blockEntity != null) {
            blockEntity.setSelector(selector);
        }
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
        if (blockEntity != null) {
            blockEntity.setGoal(goal);
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    public BlockPos getPos() {
        return this.blockEntity != null ? this.blockEntity.getPos() : pos;
    }
}
