package fr.lordfinn.steveparty.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The mouse wheel of the tools that use it (sneaking, most of them) instead of the hotbar. */
@Mixin(Mouse.class)
public class MouseScrollMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void steveparty$toolScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        // Looking through a Telescope: the wheel goes through the past nights
        if (window == client.getWindow().getHandle() && fr.lordfinn.steveparty.client.telescope.TelescopeClient.onScroll(vertical)) ci.cancel();
        // Sneak + wheel with a Move Forward / Back cartridge: its number of spaces
        else if (window == client.getWindow().getHandle() && fr.lordfinn.steveparty.client.gui.AdvanceBackCartridgeControls.onScroll(vertical)) ci.cancel();
        // Sneak + wheel with a Mini-game Page in zone mode: the face of its zone looked at
        else if (window == client.getWindow().getHandle() && fr.lordfinn.steveparty.client.minigame.PageZoneClient.onScroll(vertical)) ci.cancel();
        // Sneak + wheel with a Shop Cartridge: the purchases a stop allows
        else if (window == client.getWindow().getHandle() && steveparty$shopCartridgeScroll(vertical)) ci.cancel();
    }

    @org.spongepowered.asm.mixin.Unique
    private boolean steveparty$shopCartridgeScroll(double vertical) {
        if (client.currentScreen != null || client.player == null || !client.player.isSneaking()
                || !(client.player.getMainHandStack().getItem() instanceof fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem)) {
            return false;
        }
        if (vertical != 0) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new fr.lordfinn.steveparty.payloads.custom.ShopCartridgeScrollPayload(vertical > 0 ? 1 : -1));
        }
        return true;
    }
}
