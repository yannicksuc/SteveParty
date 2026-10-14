package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;

/**
 * S2C: what the mode of a podium column does, shown to the player who changed it with the Wrench (see
 * {@code Podiums#preview}).
 *
 * @param columns the bottom blocks of the columns it acts on (they light up)
 * @param statue  the bottom block of the column a player would go to (a Steve statue stands there), if any
 * @param mode    the mode ({@code PodiumSignal} ordinal), for the colour
 * @param ticks   how long it is shown
 */
public record PodiumPreviewPayload(List<BlockPos> columns, Optional<BlockPos> statue, int mode, int ticks) implements CustomPayload {
    public static final Id<PodiumPreviewPayload> ID = new Id<>(Steveparty.id("podium_preview"));
    public static final PacketCodec<RegistryByteBuf, PodiumPreviewPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC.collect(PacketCodecs.toList()), PodiumPreviewPayload::columns,
            PacketCodecs.optional(BlockPos.PACKET_CODEC), PodiumPreviewPayload::statue,
            PacketCodecs.VAR_INT, PodiumPreviewPayload::mode,
            PacketCodecs.VAR_INT, PodiumPreviewPayload::ticks,
            PodiumPreviewPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
