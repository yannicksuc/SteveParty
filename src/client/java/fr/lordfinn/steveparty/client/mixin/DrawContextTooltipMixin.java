package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.gui.UiText;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Every tooltip drawn (an item's, a button's, a screen's own) goes through here: the tooltip of a line too long for
 * its box ({@link UiText}) is then not drawn over it the same frame.
 */
@Mixin(DrawContext.class)
public abstract class DrawContextTooltipMixin {
    @Inject(method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;IILnet/minecraft/client/gui/tooltip/TooltipPositioner;)V",
            at = @At("HEAD"))
    private void steveparty$tooltipDrawn(TextRenderer textRenderer, List<TooltipComponent> components, int x, int y,
                                        TooltipPositioner positioner, CallbackInfo ci) {
        UiText.tooltipDrawn();
    }
}
