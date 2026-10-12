package fr.lordfinn.steveparty.client.gui.cartridge;

import net.minecraft.client.MinecraftClient;

/** The electric current animation of the cartridge menus: always on, unless the game hides lightning flashes. */
public final class CartridgeGuiConfig {
    private CartridgeGuiConfig() {}

    /** Whether the current animation plays: off only with the game's « Hide Lightning Flashes » accessibility option. */
    public static boolean animate() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null || client.options == null || !client.options.getHideLightningFlashes().getValue();
    }
}
