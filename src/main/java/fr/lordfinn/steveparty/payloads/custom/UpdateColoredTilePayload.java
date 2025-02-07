package fr.lordfinn.steveparty.payloads.custom;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import static fr.lordfinn.steveparty.payloads.ModPayloads.UPDATE_COLORED_TILE_PAYLOAD;

public record UpdateColoredTilePayload(BlockPos position, int color) implements CustomPayload {
    public static final CustomPayload.Id<UpdateColoredTilePayload> ID = new CustomPayload.Id<>(UPDATE_COLORED_TILE_PAYLOAD);
    public static final PacketCodec<RegistryByteBuf, UpdateColoredTilePayload> CODEC =
            PacketCodec.tuple(
                    BlockPos.PACKET_CODEC, UpdateColoredTilePayload::position,
                    PacketCodecs.INTEGER, UpdateColoredTilePayload::color,
                    UpdateColoredTilePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}