package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.custom.PiggyBankBlockEntity;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The Piggy Bank's menu: its Inventory Cartridge slot, a button per target (button id: the target's ordinal) and, on
 * the right, the cartridge's menu (what is given or taken, see {@link #addCartridgeMenu}).
 */
public class PiggyBankScreenHandler extends CartridgeContainerScreenHandler {
    /**
     * Where the cartridge slot and the first target button are, from the menu's corner; the buttons' size and step. On
     * the pink tab (centred over the inventory: outline from 28 to 147, flat from 31 to 144, from 3 to the inventory's
     * tab at 39): 6 px left of the slot, between the slot and the buttons and right of them; 9 px above and below.
     */
    public static final int SLOT_X = 38, SLOT_Y = 13, BUTTONS_X = 61, BUTTONS_Y = 12, BUTTON = 18, BUTTON_STEP = 20;

    private final PropertyDelegate properties;

    public PiggyBankScreenHandler(int syncId, PlayerInventory playerInventory, Inventory blockInventory, PropertyDelegate properties) {
        this(syncId, playerInventory, blockInventory, properties, posOf(blockInventory));
    }

    private PiggyBankScreenHandler(int syncId, PlayerInventory playerInventory, Inventory blockInventory, PropertyDelegate properties,
                                   @Nullable BlockPos pos) {
        super(ModScreensHandlers.PIGGY_BANK_SCREEN_HANDLER, syncId);
        this.inventory = blockInventory;
        this.properties = properties;
        checkSize(blockInventory, 1);
        checkDataCount(properties, 1);
        addProperties(properties);
        init(playerInventory, 54);
        addCartridgeMenu(playerInventory.player, pos);
    }

    /** Client side: the slot's copy, the bank's position from the opening data. */
    public PiggyBankScreenHandler(int syncId, PlayerInventory playerInventory, BlockPosPayload payload) {
        this(syncId, playerInventory, new SimpleInventory(1), new ArrayPropertyDelegate(1), payload.pos());
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
