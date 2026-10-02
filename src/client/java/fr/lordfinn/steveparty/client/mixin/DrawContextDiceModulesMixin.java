package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.items.DiceModulePips;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dice: the pictograms of the modules a die carries, over its icon in the inventories (with the stack count). */
@Mixin(DrawContext.class)
public class DrawContextDiceModulesMixin {

    @Inject(method = "drawStackOverlay(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V",
            at = @At("TAIL"))
    private void steveparty$diceModules(TextRenderer textRenderer, ItemStack stack, int x, int y, String stackCountText, CallbackInfo ci) {
        DiceModulePips.draw((DrawContext) (Object) this, stack, x, y);
    }
}
