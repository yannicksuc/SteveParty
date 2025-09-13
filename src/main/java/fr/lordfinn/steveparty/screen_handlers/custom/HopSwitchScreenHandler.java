package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.custom.HopSwitchBlockEntity;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.payloads.custom.HopSwitchPayload;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PropertyDelegate;

public class HopSwitchScreenHandler extends CartridgeContainerScreenHandler {
    private final PropertyDelegate propertyDelegate;

    public HopSwitchScreenHandler(int syncId, PlayerInventory playerInventory, HopSwitchBlockEntity blockEntity) {
        super(ModScreensHandlers.HOP_SWITCH_SCREEN_HANDLER, syncId);
        this.inventory = blockEntity;
        this.propertyDelegate = blockEntity.getPropertyDelegate();

        this.addProperties(this.propertyDelegate); // <--- this makes syncing automatic
        init(playerInventory, 54);
    }

    // Client constructor (with payload)
    public HopSwitchScreenHandler(int syncId, PlayerInventory playerInventory, BlockPosPayload payload) {
        this(syncId, playerInventory, (HopSwitchBlockEntity) playerInventory.player.getWorld().getBlockEntity(payload.pos()));
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id == 0) { // mode button
            cycleMode(); // server updates
            return true;
        }
        return false;
    }

    @Override
    public void setupScreen() {
        this.addSlot(new CartridgeCustomSlot(this.inventory, 0, 52, 14));
    }

    public int getModeInt() {
        return propertyDelegate.get(0);
    }

    public int getDurationTicks() {
        return propertyDelegate.get(1);
    }

    public void cycleMode() {
        int next = (getMode().getId() + 1) % HopSwitchBlockEntity.Mode.values().length;
        propertyDelegate.set(0, next); // syncs automatically
    }

    public HopSwitchBlockEntity.Mode getMode() {
        return HopSwitchBlockEntity.Mode.fromId(propertyDelegate.get(0));
    }
}
