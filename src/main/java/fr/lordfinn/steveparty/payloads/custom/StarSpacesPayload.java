package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Where the stars of the running parties of the player's world stand (see fr.lordfinn.steveparty.service.PartyStars):
 * the client draws a Party Star floating over each of these board spaces. The whole list, sent when a star moves and
 * every few seconds (a player joining or changing world gets it then).
 */
public record StarSpacesPayload(List<BlockPos> spaces) implements CustomPayload {
    public static final CustomPayload.Id<StarSpacesPayload> ID = new CustomPayload.Id<>(Steveparty.id("star-spaces"));
    public static final PacketCodec<RegistryByteBuf, StarSpacesPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC.collect(PacketCodecs.toList()), StarSpacesPayload::spaces,
            StarSpacesPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
