package fr.lordfinn.steveparty.screen_handlers.custom;

import net.minecraft.entity.player.PlayerEntity;

/** A screen handler with ghost slots: the target of {@link fr.lordfinn.steveparty.payloads.custom.CartridgeSlotScrollPayload}. */
public interface GhostSlotHost {
    /** Server side: the wheel over ghost slot {@code slotIndex} (a handler slot index), after the host's checks. */
    void handleGhostScroll(PlayerEntity player, int slotIndex, int direction);
}
