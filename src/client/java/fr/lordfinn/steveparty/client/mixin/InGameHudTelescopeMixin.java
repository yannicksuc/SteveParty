package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.telescope.TelescopeClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Looking through a Telescope, its scope is the whole HUD: nothing of the usual HUD (hotbar, scoreboard, titles, the
 * HUD of other mods drawn with it) is drawn over or around it.
 */
@Mixin(InGameHud.class)
public class InGameHudTelescopeMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void steveparty$telescopeScopeOnly(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!TelescopeClient.coversHud(tickCounter.getTickDelta(false))) return;
        TelescopeClient.renderHud(context, tickCounter);
        ci.cancel();
    }
}
