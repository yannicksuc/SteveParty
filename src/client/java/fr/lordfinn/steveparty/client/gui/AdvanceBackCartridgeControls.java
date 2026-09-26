package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem;
import fr.lordfinn.steveparty.payloads.custom.AdvanceBackScrollPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/** Sneak + mouse wheel with a Move Forward / Back cartridge in the main hand: its number of spaces and direction. */
public final class AdvanceBackCartridgeControls {
    private AdvanceBackCartridgeControls() {
    }

    /**
     * Mouse wheel hook: wheel up toward forward (+6), down toward back (-6); the server answers in the action bar.
     *
     * @return true if the scroll was used
     */
    public static boolean onScroll(double vertical) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || client.player == null || !client.player.isSneaking()
                || !(client.player.getMainHandStack().getItem() instanceof AdvanceBackCartridgeItem)) return false;
        if (vertical == 0) return true;
        ClientPlayNetworking.send(new AdvanceBackScrollPayload(vertical > 0 ? 1 : -1));
        return true;
    }
}
