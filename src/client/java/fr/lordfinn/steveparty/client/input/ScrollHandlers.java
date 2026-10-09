package fr.lordfinn.steveparty.client.input;

import fr.lordfinn.steveparty.client.minigame.PageZoneClient;
import fr.lordfinn.steveparty.client.telescope.TelescopeClient;
import fr.lordfinn.steveparty.items.SneakScrollItem;
import fr.lordfinn.steveparty.payloads.custom.HeldItemScrollPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/**
 * The mouse wheel of the tools that use it (sneaking, most of them) instead of the hotbar: the first handler, in
 * registration order, that uses a scroll keeps it from the hotbar (MouseScrollMixin).
 */
public final class ScrollHandlers {
    /** A mouse wheel hook: true if it used the scroll ({@code vertical} 0 included). */
    @FunctionalInterface
    public interface Handler {
        boolean onScroll(double vertical);
    }

    private static final List<Handler> HANDLERS = new ArrayList<>();

    static {
        // Looking through a Telescope: the wheel goes through the past nights
        register(TelescopeClient::onScroll);
        // Sneak + wheel with a SneakScrollItem (Move Forward / Back cartridge, Shop Cartridge): its setting
        register(ScrollHandlers::sneakScrollItem);
        // Sneak + wheel with a Mini-game Page in zone mode: the face of its zone looked at
        register(PageZoneClient::onScroll);
    }

    private ScrollHandlers() {
    }

    public static void register(Handler handler) {
        HANDLERS.add(handler);
    }

    /** @return true if a handler used the scroll */
    public static boolean dispatch(double vertical) {
        for (Handler handler : HANDLERS) if (handler.onScroll(vertical)) return true;
        return false;
    }

    /** Sneaking, no screen open, a {@link SneakScrollItem} in the main hand: one notch up or down, told the server. */
    private static boolean sneakScrollItem(double vertical) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || client.player == null || !client.player.isSneaking()
                || !(client.player.getMainHandStack().getItem() instanceof SneakScrollItem)) return false;
        if (vertical != 0) ClientPlayNetworking.send(new HeldItemScrollPayload(vertical > 0 ? 1 : -1));
        return true;
    }
}
