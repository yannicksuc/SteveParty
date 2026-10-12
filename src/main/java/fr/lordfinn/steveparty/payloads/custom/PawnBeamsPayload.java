package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import java.util.List;

/**
 * The pawns of the running parties of the player's world that show a light beam (see
 * fr.lordfinn.steveparty.service.PawnBeams): the whole list, sent when it changes and every second while it is not
 * empty (a player joining or changing world gets it then). A pawn missing from it loses its beam.
 */
public record PawnBeamsPayload(List<Beam> beams) implements CustomPayload {
    public static final CustomPayload.Id<PawnBeamsPayload> ID = new CustomPayload.Id<>(Steveparty.id("pawn_beams"));
    public static final PacketCodec<RegistryByteBuf, PawnBeamsPayload> CODEC = PacketCodec.tuple(
            Beam.CODEC.collect(PacketCodecs.toList()), PawnBeamsPayload::beams,
            PawnBeamsPayload::new);

    /**
     * A pawn's beam.
     *
     * @param entityId the pawn's entity id: the beam follows it where the client has it
     * @param color    its colour, 0xRRGGBB
     * @param x        where it stands (the beam's foot), for a client that does not have it (too far)
     */
    public record Beam(int entityId, int color, float x, float y, float z) {
        public static final PacketCodec<RegistryByteBuf, Beam> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, Beam::entityId,
                PacketCodecs.INTEGER, Beam::color,
                PacketCodecs.FLOAT, Beam::x,
                PacketCodecs.FLOAT, Beam::y,
                PacketCodecs.FLOAT, Beam::z,
                Beam::new);

        /** Same pawn, same colour (where it stands aside). */
        public boolean sameAs(Beam other) {
            return entityId == other.entityId && color == other.color;
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
