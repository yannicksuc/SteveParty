package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/** Server → client: open the screen of the Party Antenna at {@code pos} (it reads the antenna the client already has). */
public record OpenAntennaScreenPayload(BlockPos pos) implements CustomPayload {
    public static final CustomPayload.Id<OpenAntennaScreenPayload> ID = new CustomPayload.Id<>(Steveparty.id("open_antenna_screen"));
    public static final PacketCodec<RegistryByteBuf, OpenAntennaScreenPayload> CODEC =
            PacketCodec.tuple(BlockPos.PACKET_CODEC, OpenAntennaScreenPayload::pos, OpenAntennaScreenPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
