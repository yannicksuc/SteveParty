package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import static fr.lordfinn.steveparty.payloads.ModPayloads.PARTY_DASHBOARD_PAYLOAD;

/** What the open Party Controller dashboard {@code syncId} shows: see {@link PartyDashboardData}. */
public record PartyDashboardPayload(int syncId, PartyDashboardData data) implements CustomPayload {
    public static final CustomPayload.Id<PartyDashboardPayload> ID = new CustomPayload.Id<>(PARTY_DASHBOARD_PAYLOAD);
    public static final PacketCodec<RegistryByteBuf, PartyDashboardPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, PartyDashboardPayload::syncId,
            PartyDashboardData.PACKET_CODEC, PartyDashboardPayload::data,
            PartyDashboardPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
