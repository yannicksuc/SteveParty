package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.village.MerchantInventory;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

// Mixin to modify MerchantScreenHandler
@Mixin(MerchantScreenHandler.class)
public abstract class MerchantScreenHandlerMixin {

    @Inject(method = "switchTo(I)V", at = @At("HEAD"))
    private void onSwitchTo(int recipeIndex, CallbackInfo ci) {
        Steveparty.LOGGER.info("Switching to recipe index: " + recipeIndex);
    }
}
