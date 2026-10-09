package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** The live state of a party (current turn, standings) for its party HUDs: see {@link PartyLiveData}. */
public record PartyLivePayload(PartyLiveData data) implements CustomPayload {
    public static final CustomPayload.Id<PartyLivePayload> ID = new CustomPayload.Id<>(Steveparty.id("party_live"));
    public static final PacketCodec<RegistryByteBuf, PartyLivePayload> CODEC =
            PartyLiveData.PACKET_CODEC.xmap(PartyLivePayload::new, PartyLivePayload::data);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
