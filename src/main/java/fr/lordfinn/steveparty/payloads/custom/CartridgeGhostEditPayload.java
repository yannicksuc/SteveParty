package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.payloads.ServerboundPayload;
import fr.lordfinn.steveparty.screen_handlers.custom.GhostSlot;
import fr.lordfinn.steveparty.screen_handlers.custom.GhostSlotHost;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * C2S: the player changed a ghost slot of the cartridge screen (its − / + buttons, give / take, the wheel).
 *
 * @param syncId    sync id of the screen handler (a {@link GhostSlotHost}) the change happened in
 * @param slotIndex index of the ghost slot in the handler
 * @param edit      what changes
 * @param amount    for {@link GhostSlot.Edit#STEP}: how many more (less if negative)
 */
public record CartridgeGhostEditPayload(int syncId, int slotIndex, GhostSlot.Edit edit, int amount) implements ServerboundPayload {
    public static final CustomPayload.Id<CartridgeGhostEditPayload> ID = new CustomPayload.Id<>(Steveparty.id("cartridge_ghost_edit"));
    public static final PacketCodec<RegistryByteBuf, CartridgeGhostEditPayload> CODEC = PacketCodec.of((payload, buf) -> {
        buf.writeVarInt(payload.syncId);
        buf.writeVarInt(payload.slotIndex);
        buf.writeEnumConstant(payload.edit);
        buf.writeVarInt(payload.amount);
    }, buf -> new CartridgeGhostEditPayload(buf.readVarInt(), buf.readVarInt(), buf.readEnumConstant(GhostSlot.Edit.class), buf.readVarInt()));

    @Override
    public void handle(ServerPlayerEntity player) {
        if (player.currentScreenHandler instanceof GhostSlotHost host && player.currentScreenHandler.syncId == syncId) {
            host.handleGhostEdit(player, slotIndex, edit, Math.clamp(amount, -GhostSlot.MAX_COUNT, GhostSlot.MAX_COUNT));
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
