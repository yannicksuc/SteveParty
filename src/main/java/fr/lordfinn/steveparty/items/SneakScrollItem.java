package fr.lordfinn.steveparty.items;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * An item set with sneak + mouse wheel while held in the main hand (instead of changing the hotbar slot): the client
 * sends a HeldItemScrollPayload, the server calls {@link #onSneakScroll}.
 */
public interface SneakScrollItem {
    /** Server side: the wheel turned one notch, {@code direction} +1 up or -1 down. */
    void onSneakScroll(ServerPlayerEntity player, ItemStack stack, int direction);
}
