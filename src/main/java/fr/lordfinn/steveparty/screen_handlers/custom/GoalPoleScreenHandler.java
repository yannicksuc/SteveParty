package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlockEntity;
import fr.lordfinn.steveparty.payloads.custom.GoalPolePayload;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

public class GoalPoleScreenHandler extends ScreenHandler {
    private final GoalPoleBlockEntity blockEntity;
    private GoalPoleBlockEntity.Comparator comparator;
    private int value;
    private BlockPos pos;

    // Constructor from BlockEntity (server-side)
    public GoalPoleScreenHandler(int syncId, PlayerInventory playerInventory, GoalPoleBlockEntity entity) {
        super(ModScreensHandlers.GOAL_POLE_SCREEN_HANDLER, syncId);
        this.blockEntity = entity;
        this.comparator = entity.getComparator();
        this.value = entity.getValue();
        this.pos = entity.getPos();
    }

    // Constructor from Payload (client-side)
    public GoalPoleScreenHandler(int syncId, PlayerInventory playerInventory, GoalPolePayload payload) {
        super(ModScreensHandlers.GOAL_POLE_SCREEN_HANDLER, syncId);
        this.blockEntity = null;
        this.pos = payload.pos();
        this.comparator = payload.comparator();
        this.value = payload.value();
    }

    // --- Getters / setters ---
    public GoalPoleBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public GoalPoleBlockEntity.Comparator getComparator() {
        return comparator;
    }

    public void setComparator(GoalPoleBlockEntity.Comparator comparator) {
        this.comparator = comparator;
        if (blockEntity != null) {
            blockEntity.setComparator(comparator);
        }
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
        if (blockEntity != null) {
            blockEntity.setValue(value);
        }
    }

    public BlockPos getPos() {
        return pos;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public net.minecraft.item.ItemStack quickMove(PlayerEntity player, int invSlot) {
        return net.minecraft.item.ItemStack.EMPTY;
    }
}
