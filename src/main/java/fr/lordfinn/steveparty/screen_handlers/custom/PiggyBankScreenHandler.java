package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.custom.PiggyBankBlockEntity;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;

/** The Piggy Bank's menu: its Inventory Cartridge slot, and a button per target (button id: the target's ordinal). */
public class PiggyBankScreenHandler extends CartridgeContainerScreenHandler {
    /** Where the cartridge slot and the first target button are, from the menu's corner; the buttons' size and step. */
    public static final int SLOT_X = 48, SLOT_Y = 16, BUTTONS_X = 76, BUTTONS_Y = 15, BUTTON = 18, BUTTON_STEP = 20;

    private final PropertyDelegate properties;

    public PiggyBankScreenHandler(int syncId, PlayerInventory playerInventory, Inventory blockInventory, PropertyDelegate properties) {
        super(ModScreensHandlers.PIGGY_BANK_SCREEN_HANDLER, syncId);
        this.inventory = blockInventory;
        this.properties = properties;
        checkSize(blockInventory, 1);
        checkDataCount(properties, 1);
        addProperties(properties);
        init(playerInventory, 54);
    }

    public PiggyBankScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(1), new ArrayPropertyDelegate(1));
    }

    @Override
    public void setupScreen() {
        this.addSlot(new CartridgeCustomSlot(inventory, 0, SLOT_X, SLOT_Y) {
            @Override
            protected boolean isAllowedItem(ItemStack originalStack) {
                return originalStack.getItem() instanceof InventoryCartridgeItem;
            }
        });
    }

    public PiggyBankBlockEntity.Target getTarget() {
        return PiggyBankBlockEntity.Target.byId(properties.get(0));
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id < 0 || id >= PiggyBankBlockEntity.Target.values().length) return false;
        properties.set(0, id);
        return true;
    }
}
