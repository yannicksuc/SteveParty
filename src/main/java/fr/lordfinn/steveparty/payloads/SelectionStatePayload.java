package fr.lordfinn.steveparty.payloads;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import static fr.lordfinn.steveparty.payloads.ModPayloads.SELECTION_STATE_PAYLOAD;

public record SelectionStatePayload(int selectionState) implements CustomPayload {
    public static final CustomPayload.Id<SelectionStatePayload> ID = new CustomPayload.Id<>(SELECTION_STATE_PAYLOAD);
    public static final PacketCodec<RegistryByteBuf, SelectionStatePayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.INTEGER, SelectionStatePayload::selectionState,
                    SelectionStatePayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}