package fr.lordfinn.steveparty.payloads.custom;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import static fr.lordfinn.steveparty.payloads.ModPayloads.HERE_WE_GO_BOOK_PAYLOAD;

public record HereWeGoBookPayload(int state) implements CustomPayload {
    public static final CustomPayload.Id<HereWeGoBookPayload> ID = new CustomPayload.Id<>(HERE_WE_GO_BOOK_PAYLOAD);
    public static final PacketCodec<RegistryByteBuf, HereWeGoBookPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.INTEGER, HereWeGoBookPayload::state, HereWeGoBookPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}