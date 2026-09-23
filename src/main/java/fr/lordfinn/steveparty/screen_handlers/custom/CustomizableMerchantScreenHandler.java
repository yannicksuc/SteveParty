package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.village.Merchant;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

import java.util.ArrayList;
import java.util.List;

public class CustomizableMerchantScreenHandler extends MerchantScreenHandler {
    private static final int INPUT_SLOT_1 = 0;
    private static final int INPUT_SLOT_2 = 1;
    private static final int PLAYER_INVENTORY_START = 3;
    private static final int PLAYER_INVENTORY_END = 39;
    private int selectedTradeIndex = -1; // To track the player's explicitly selected trade

    public CustomizableMerchantScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(syncId, playerInventory);
    }
    public CustomizableMerchantScreenHandler(int syncId, PlayerInventory playerInventory, Merchant merchant) {
        super(syncId, playerInventory, merchant);
    }

    public void setSelectedTradeIndex(int index) {
        this.selectedTradeIndex = index;
    }



    @Override
    public void onContentChanged(Inventory inventory) {
        // Get the current input in the first slot
        ItemStack inputStack = this.slots.getFirst().getStack();

        Steveparty.LOGGER.debug("Input stack: {}", inputStack);

        if (inputStack.isEmpty()) {
            // If there's no input, reset the selected trade index
            this.selectedTradeIndex = -1;
            Steveparty.LOGGER.debug("Resetting selected trade index");
            return;
        }

        TradeOfferList offers = this.getRecipes();
        List<TradeOffer> matchingOffers = new ArrayList<>();

        // Find matching offers for the input item
        for (TradeOffer offer : offers) {
            if (offer.getFirstBuyItem().matches(inputStack) && isExactFirstPayment(offer, inputStack)) {
                matchingOffers.add(offer);
                Steveparty.LOGGER.debug("Found matching offer: {}", offer);
            }
        }

        // Update the displayed trade
        if (selectedTradeIndex >= 0 && selectedTradeIndex < matchingOffers.size()) {
            // If a trade is explicitly selected, display it
            Steveparty.LOGGER.debug("Selected trade index: {}", selectedTradeIndex);
            this.setRecipeIndex(selectedTradeIndex);
        } else if (matchingOffers.size() == 1) {
            // If there's only one matching trade, display it
            Steveparty.LOGGER.debug("Single matching offer: {}", matchingOffers.getFirst());
            this.setRecipeIndex(offers.indexOf(matchingOffers.getFirst()));
        } else {
            // No trade is selected or multiple matching trades exist
            Steveparty.LOGGER.debug("No matching offer found");
            this.setRecipeIndex(-1);
        }
        super.onContentChanged(inventory);
    }

    /** Trading stall offers require the exact price item (components included). */
    private static boolean isExactFirstPayment(TradeOffer offer, ItemStack stack) {
        return !(offer instanceof TradingStallBlockEntity.ExactTradeOffer exactOffer)
                || TradingStallBlockEntity.isExactPayment(stack, exactOffer.getFirstPrice());
    }

    /**
     * Same as vanilla, but for trading stall offers the input slots are auto-filled only with the exact price
     * items: vanilla auto-fill accepts stacks carrying extra components (e.g. a named or enchanted item), which
     * the offer would then refuse.
     */
    @Override
    public void switchTo(int recipeIndex) {
        TradeOfferList offers = this.getRecipes();
        if (recipeIndex < 0 || recipeIndex >= offers.size()
                || !(offers.get(recipeIndex) instanceof TradingStallBlockEntity.ExactTradeOffer offer)) {
            super.switchTo(recipeIndex);
            return;
        }
        // Give the current input items back to the player
        for (int slot = INPUT_SLOT_1; slot <= INPUT_SLOT_2; slot++) {
            ItemStack input = this.slots.get(slot).getStack();
            if (!input.isEmpty()) {
                if (!this.insertItem(input, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, true)) return;
                this.slots.get(slot).setStack(input);
            }
        }
        if (this.slots.get(INPUT_SLOT_1).getStack().isEmpty() && this.slots.get(INPUT_SLOT_2).getStack().isEmpty()) {
            autofillExact(INPUT_SLOT_1, offer.getFirstPrice());
            if (!offer.getSecondPrice().isEmpty()) autofillExact(INPUT_SLOT_2, offer.getSecondPrice());
        }
    }

    private void autofillExact(int inputSlot, ItemStack price) {
        for (int i = PLAYER_INVENTORY_START; i < PLAYER_INVENTORY_END; i++) {
            ItemStack stack = this.slots.get(i).getStack();
            if (stack.isEmpty() || !TradingStallBlockEntity.isExactPayment(stack, price)) continue;
            ItemStack current = this.slots.get(inputSlot).getStack();
            int maxCount = stack.getMaxCount();
            int moved = Math.min(maxCount - current.getCount(), stack.getCount());
            ItemStack filled = stack.copyWithCount(current.getCount() + moved);
            stack.decrement(moved);
            this.slots.get(inputSlot).setStack(filled);
            if (filled.getCount() >= maxCount) break;
        }
    }

    @Override
    public void setRecipeIndex(int index) {
        Steveparty.LOGGER.debug("Setting recipe index: {}", index);
        super.setRecipeIndex(index);
        this.setSelectedTradeIndex(index); // Track the player's explicit selection
    }
}
