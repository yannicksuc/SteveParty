package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import fr.lordfinn.steveparty.service.ShopStops;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.village.Merchant;

/**
 * The trade screen of a shop stop (see {@link ShopStops}): the merchant's screen, plus a « Buy nothing » button (« Done »
 * once something was bought), the time left and the purchases allowed. Its player can buy at most {@link #getLimit}
 * items; the stop ends when they are bought, on the button, or when the screen is closed.
 */
public class ShopStopScreenHandler extends CustomizableMerchantScreenHandler {
    /** Button id of « Buy nothing » (see {@link #onButtonClick}). */
    public static final int BUY_NOTHING_BUTTON_ID = 0;
    /** Index of the result slot of the merchant screen. */
    private static final int OUTPUT_SLOT = 2;
    /** Synced: seconds left before the stop ends by itself, purchases made, purchases allowed. */
    private static final int PROPERTY_SECONDS_LEFT = 0, PROPERTY_PURCHASES = 1, PROPERTY_LIMIT = 2, PROPERTY_COUNT = 3;

    private final PropertyDelegate properties;
    private int purchases;
    private int limit = 1;

    /** Client side. */
    public ShopStopScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(syncId, playerInventory);
        this.properties = new ArrayPropertyDelegate(PROPERTY_COUNT);
        addProperties(properties);
    }

    /** Server side: at most {@code limit} purchases. */
    public ShopStopScreenHandler(int syncId, PlayerInventory playerInventory, Merchant merchant, int limit) {
        super(syncId, playerInventory, merchant);
        this.limit = limit;
        this.properties = new PropertyDelegate() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case PROPERTY_SECONDS_LEFT -> ShopStops.secondsLeft(ShopStopScreenHandler.this);
                    case PROPERTY_PURCHASES -> purchases;
                    case PROPERTY_LIMIT -> ShopStopScreenHandler.this.limit;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int size() {
                return PROPERTY_COUNT;
            }
        };
        addProperties(properties);
    }

    /** Not the vanilla merchant screen: the client opens the shop stop's screen (with the button). */
    @Override
    public ScreenHandlerType<?> getType() {
        return ModScreensHandlers.SHOP_STOP_SCREEN_HANDLER;
    }

    public int getSecondsLeft() {
        return properties.get(PROPERTY_SECONDS_LEFT);
    }

    /** Purchases made in this screen (synced to the client). */
    public int getPurchases() {
        return properties.get(PROPERTY_PURCHASES);
    }

    /** Purchases allowed during the stop (synced to the client). */
    public int getLimit() {
        return properties.get(PROPERTY_LIMIT);
    }

    /** True once the purchases allowed were made: nothing more can be taken from the result slot. */
    public boolean isLimitReached() {
        return getPurchases() >= getLimit();
    }

    /** Server side: a trade was completed in this screen. */
    public void onPurchase() {
        purchases++;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex == OUTPUT_SLOT && isLimitReached()) return;
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    /** A shift-click on the result buys as long as it can: never past the limit. */
    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        if (slot == OUTPUT_SLOT && isLimitReached()) return ItemStack.EMPTY;
        return super.quickMove(player, slot);
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id == BUY_NOTHING_BUTTON_ID) {
            if (!player.getWorld().isClient) ShopStops.buyNothing(this);
            return true;
        }
        return super.onButtonClick(player, id);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (!player.getWorld().isClient) ShopStops.onScreenClosed(this);
    }
}
