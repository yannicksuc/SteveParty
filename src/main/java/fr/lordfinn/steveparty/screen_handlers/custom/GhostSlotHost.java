package fr.lordfinn.steveparty.screen_handlers.custom;

import net.minecraft.entity.player.PlayerEntity;

/** A screen handler with ghost slots: the target of {@link fr.lordfinn.steveparty.payloads.custom.CartridgeGhostEditPayload}. */
public interface GhostSlotHost {
    /** Server side: {@code edit} of ghost slot {@code slotIndex} (a handler slot index), after the host's checks. */
    void handleGhostEdit(PlayerEntity player, int slotIndex, GhostSlot.Edit edit, int amount);
}
