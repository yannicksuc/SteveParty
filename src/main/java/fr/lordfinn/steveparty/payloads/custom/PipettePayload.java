package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.board.Pipette;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Client → server: the pipette of a board space's or router's screen pastes the destinations of slot {@code source} on slot {@code target}
 * (slot indexes of the open screen {@code syncId}); checked by {@link Pipette#apply}.
 */
public record PipettePayload(int syncId, int source, int target) implements CustomPayload {
    public static final Id<PipettePayload> ID = new Id<>(Steveparty.id("pipette"));
    public static final PacketCodec<RegistryByteBuf, PipettePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, PipettePayload::syncId,
            PacketCodecs.VAR_INT, PipettePayload::source,
            PacketCodecs.VAR_INT, PipettePayload::target,
            PipettePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void handle(ServerPlayerEntity player) {
        Pipette.apply(player, syncId, source, target);
    }
}
