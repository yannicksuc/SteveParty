package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import static fr.lordfinn.steveparty.payloads.ModPayloads.HOP_SWITCH_PAYLOAD;

public record HopSwitchPayload(BlockPos pos, int mode, int durationTicks) implements CustomPayload {

    public static final CustomPayload.Id<HopSwitchPayload> ID = new CustomPayload.Id<>(HOP_SWITCH_PAYLOAD);

    public static final PacketCodec<RegistryByteBuf, HopSwitchPayload> PACKET_CODEC =
            PacketCodec.tuple(
                    BlockPos.PACKET_CODEC, HopSwitchPayload::pos,
                    PacketCodecs.INTEGER, HopSwitchPayload::mode,
                    PacketCodecs.INTEGER, HopSwitchPayload::durationTicks,
                    HopSwitchPayload::new
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
