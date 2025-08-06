package fr.lordfinn.steveparty.screen_handlers;

import fr.lordfinn.steveparty.Steveparty;
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

        Steveparty.LOGGER.info("Input stack: {}", inputStack);

        if (inputStack.isEmpty()) {
            // If there's no input, reset the selected trade index
            this.selectedTradeIndex = -1;
            Steveparty.LOGGER.info("Resetting selected trade index");
            return;
        }

        TradeOfferList offers = this.getRecipes();
        List<TradeOffer> matchingOffers = new ArrayList<>();

        // Find matching offers for the input item
        for (TradeOffer offer : offers) {
            if (offer.getFirstBuyItem().matches(inputStack)) {
                matchingOffers.add(offer);
                Steveparty.LOGGER.info("Found matching offer: {}", offer);
            }
        }

        // Update the displayed trade
        if (selectedTradeIndex >= 0 && selectedTradeIndex < matchingOffers.size()) {
            // If a trade is explicitly selected, display it
            Steveparty.LOGGER.info("Selected trade index: {}", selectedTradeIndex);
            this.setRecipeIndex(selectedTradeIndex);
        } else if (matchingOffers.size() == 1) {
            // If there's only one matching trade, display it
            Steveparty.LOGGER.info("Single matching offer: {}", matchingOffers.getFirst());
            this.setRecipeIndex(offers.indexOf(matchingOffers.getFirst()));
        } else {
            // No trade is selected or multiple matching trades exist
            Steveparty.LOGGER.info("No matching offer found");
            this.setRecipeIndex(-1);
        }
        super.onContentChanged(inventory);
    }

    @Override
    public void setRecipeIndex(int index) {
        Steveparty.LOGGER.info("Setting recipe index: {}", index);
        super.setRecipeIndex(index);
        this.setSelectedTradeIndex(index); // Track the player's explicit selection
    }
}
