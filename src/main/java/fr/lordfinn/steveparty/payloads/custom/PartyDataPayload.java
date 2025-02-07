package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
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

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static PartyDataPayload fromPartyData(PartyData partyData) {
        return new PartyDataPayload(partyData);
    }
}
