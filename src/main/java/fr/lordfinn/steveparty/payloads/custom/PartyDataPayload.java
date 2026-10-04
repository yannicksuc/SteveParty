package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import static fr.lordfinn.steveparty.payloads.ModPayloads.PARTY_DATA_PAYLOAD;

public record PartyDataPayload(PartyData partyData) implements CustomPayload {
    public static final CustomPayload.Id<PartyDataPayload> ID = new CustomPayload.Id<>(PARTY_DATA_PAYLOAD);
    public static final PacketCodec<PacketByteBuf, PartyDataPayload> CODEC =
            new PacketCodec<>() {
                @Override
                public PartyDataPayload decode(PacketByteBuf buf) {
                    return new PartyDataPayload(PartyData.fromBuf(buf));
                }

                @Override
                public void encode(PacketByteBuf buf, PartyDataPayload payload) {
                    payload.partyData.writeToPacket(buf);
                }
            };

    /**
     * A copy of the party data as it is now: the payload is encoded later, on the network thread, while the server
     * thread goes on changing the party (a live reference threw ConcurrentModificationException and kicked players).
     */
    public PartyDataPayload {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            partyData.writeToPacket(buf);
            partyData = PartyData.fromBuf(buf);
        } finally {
            buf.release();
        }
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static PartyDataPayload fromPartyData(PartyData partyData) {
        return new PartyDataPayload(partyData);
    }
}
