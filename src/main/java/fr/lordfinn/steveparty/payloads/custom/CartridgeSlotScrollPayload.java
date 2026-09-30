package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.screen_handlers.custom.GhostSlot;
import fr.lordfinn.steveparty.screen_handlers.custom.GhostSlotHost;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

import static fr.lordfinn.steveparty.payloads.ModPayloads.CARTRIDGE_SLOT_SCROLL_PAYLOAD;

/**
 * C2S: the player scrolled over a ghost slot of the cartridge screen.
 *
 * @param syncId    sync id of the screen handler (a {@link GhostSlotHost}) the scroll happened in
 * @param slotIndex index of the ghost slot in the handler
 * @param direction +1 (scroll up) or -1 (scroll down)
 */
public record CartridgeSlotScrollPayload(int syncId, int slotIndex, int direction) implements CustomPayload {
    public static final CustomPayload.Id<CartridgeSlotScrollPayload> ID = new CustomPayload.Id<>(CARTRIDGE_SLOT_SCROLL_PAYLOAD);
    public static final PacketCodec<RegistryByteBuf, CartridgeSlotScrollPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, CartridgeSlotScrollPayload::syncId,
                    PacketCodecs.VAR_INT, CartridgeSlotScrollPayload::slotIndex,
                    PacketCodecs.VAR_INT, CartridgeSlotScrollPayload::direction,
                    CartridgeSlotScrollPayload::new
            );

    /** Client-side factory: same direction convention as {@link GhostSlot#onScroll(double)}. */
    public static CartridgeSlotScrollPayload fromScroll(int syncId, int slotIndex, double amount) {
        return new CartridgeSlotScrollPayload(syncId, slotIndex, amount > 0 ? 1 : -1);
    }

    /** Server-side handler (must run on the server thread). */
    public static void handle(CartridgeSlotScrollPayload payload, ServerPlayerEntity player) {
        if (player.currentScreenHandler instanceof GhostSlotHost host && player.currentScreenHandler.syncId == payload.syncId()) {
            host.handleGhostScroll(player, payload.slotIndex(), Integer.signum(payload.direction()));
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
