package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.gui.ToolHud;
import fr.lordfinn.steveparty.client.gui.party.DiceRevealHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a tool HUD (Tile Linker Brush, Stencil Hammer: see {@link ToolHud}) sits above the hotbar, the action bar
 * message goes up above it instead of being drawn over its plates, and the held item's name is not written where it
 * sits (the tool shows what matters: see {@link ToolHud#top}). The same over the reveal of a throw ({@link DiceRevealHud}).
 */
@Mixin(InGameHud.class)
public class InGameHudToolHudMixin {
    @Inject(method = "renderOverlayMessage", at = @At("HEAD"))
    private void steveparty$overlayAboveToolHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        context.getMatrices().push();
        int tool = ToolHud.liftFor(context, 63), reveal = DiceRevealHud.liftFor(context, 63);
        context.getMatrices().translate(0, -Math.max(tool, reveal), 0);
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
    private void steveparty$noItemNameUnderToolHud(DrawContext context, CallbackInfo ci) {
        if (ToolHud.occupiedTop() >= 0 || DiceRevealHud.isShown()) ci.cancel();
    }

    @Inject(method = "renderOverlayMessage", at = @At("RETURN"))
    private void steveparty$overlayAboveToolHudEnd(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        context.getMatrices().pop();
    }
}
