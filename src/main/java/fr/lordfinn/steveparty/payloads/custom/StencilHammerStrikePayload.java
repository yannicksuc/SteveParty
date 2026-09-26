package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.joml.Vector3f;

/**
 * Server → the players around: a player struck a surface with the Stencil Hammer. The server has already applied the
 * paint; every client plays the swing, the impact, the paint splash and the stamp reveal (see
 * {@link fr.lordfinn.steveparty.items.custom.StencilHammerStrike}). The striking player plays it at once on its own
 * client, without waiting for this.
 *
 * @param entityId  the striking player
 * @param mainHand  which hand holds the hammer
 * @param canvasPos the block whose symbol gets stamped (a sign, or the paint layer in front of a wall)
 * @param hit       where the hammer head lands, in world coordinates
 * @param side      the face that was hit
 * @param color     dye id of the paint, -1 when the stencil is only engraved
 */
public record StencilHammerStrikePayload(int entityId, boolean mainHand, BlockPos canvasPos, Vector3f hit, Direction side, int color)
        implements CustomPayload {
    public static final Id<StencilHammerStrikePayload> ID = new Id<>(Steveparty.id("stencil_hammer_strike"));
    public static final PacketCodec<RegistryByteBuf, StencilHammerStrikePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, StencilHammerStrikePayload::entityId,
            PacketCodecs.BOOL, StencilHammerStrikePayload::mainHand,
            BlockPos.PACKET_CODEC, StencilHammerStrikePayload::canvasPos,
            PacketCodecs.VECTOR_3F, StencilHammerStrikePayload::hit,
            Direction.PACKET_CODEC, StencilHammerStrikePayload::side,
            PacketCodecs.VAR_INT, StencilHammerStrikePayload::color,
            StencilHammerStrikePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
