package fr.lordfinn.steveparty.payloads.custom;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import static fr.lordfinn.steveparty.payloads.ModPayloads.TRIPLE_JUMP_PAYLOAD;

public record TripleJumpPayload() implements CustomPayload {
    public static final CustomPayload.Id<TripleJumpPayload> ID = new CustomPayload.Id<>(TRIPLE_JUMP_PAYLOAD);
    public static final PacketCodec<RegistryByteBuf, TripleJumpPayload> CODEC =
            PacketCodec.of(
                    (buf, payload) -> {},
                    buf -> new TripleJumpPayload()
            );


    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
