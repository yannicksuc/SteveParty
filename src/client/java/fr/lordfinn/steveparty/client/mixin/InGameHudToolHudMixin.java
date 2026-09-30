package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.gui.ToolHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a tool HUD (Wrench, Stencil Hammer: see {@link ToolHud}) sits above the hotbar, the action bar message and the
 * held item's name go up above it instead of being drawn over its plates.
 */
@Mixin(InGameHud.class)
public class InGameHudToolHudMixin {
    /** How much higher the action bar goes when the held item's name shows too: both are lifted to the same spot. */
    private static final int ABOVE_ITEM_NAME = 12;

    @Shadow
    private int heldItemTooltipFade;
    @Shadow
    private ItemStack currentStack;

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"))
    private void steveparty$overlayAboveToolHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        context.getMatrices().push();
        int lift = ToolHud.liftFor(context, 63);
        if (lift > 0 && heldItemTooltipFade > 0 && !currentStack.isEmpty()) lift += ABOVE_ITEM_NAME;
        context.getMatrices().translate(0, -lift, 0);
    }

    @Inject(method = "renderOverlayMessage", at = @At("RETURN"))
    private void steveparty$overlayAboveToolHudEnd(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        context.getMatrices().pop();
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"))
    private void steveparty$itemNameAboveToolHud(DrawContext context, CallbackInfo ci) {
        context.getMatrices().push();
        context.getMatrices().translate(0, -ToolHud.liftFor(context, ToolHud.itemNameBottom()), 0);
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("RETURN"))
    private void steveparty$itemNameAboveToolHudEnd(DrawContext context, CallbackInfo ci) {
        context.getMatrices().pop();
    }
}
