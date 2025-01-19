package fr.lordfinn.steveparty.payloads;

import fr.lordfinn.steveparty.service.TokenData;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static fr.lordfinn.steveparty.payloads.ModPayloads.TOKENS_PAYLOAD;

public record TokenPayload(Map<UUID, TokenData> tokens) implements CustomPayload {
    public static final CustomPayload.Id<TokenPayload> ID = new CustomPayload.Id<>(TOKENS_PAYLOAD);
    public static final PacketCodec<PacketByteBuf, TokenPayload> CODEC =
            new PacketCodec<>() {
                public TokenPayload decode(PacketByteBuf buf) {
                    int size = buf.readInt();
                    Map<UUID, TokenData> map = new HashMap<>();
                    for (int i = 0; i <= size; i++) {
                        map.put(buf.readUuid(), TokenData.fromBuf(buf));
                    }
                    return new TokenPayload(map);
                }
                public void encode(PacketByteBuf buf, TokenPayload payload) {
                    buf.writeInt(payload.tokens().size());
                    payload.tokens().forEach((id, token) -> {
                        buf.writeUuid(id);
                        token.writeToPacket(buf);
                    });
                }
    };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}