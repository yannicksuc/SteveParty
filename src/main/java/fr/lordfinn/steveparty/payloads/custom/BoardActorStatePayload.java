package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * A board actor's state, for the crosshair of the players who see it (BoardActors): sent when they start tracking it,
 * and each time its show opens or closes a moment where it takes blows or clicks.
 *
 * @param entityId  network id of the actor
 * @param hologram  whether it is a hologram (never aimed at: the crosshair goes through it to what is behind)
 * @param touchable whether its show wants blows or clicks on it right now (it can then be aimed at)
 */
public record BoardActorStatePayload(int entityId, boolean hologram, boolean touchable) implements CustomPayload {
    public static final CustomPayload.Id<BoardActorStatePayload> ID = new CustomPayload.Id<>(Steveparty.id("board_actor_state"));
    public static final PacketCodec<RegistryByteBuf, BoardActorStatePayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, BoardActorStatePayload::entityId,
                    PacketCodecs.BOOL, BoardActorStatePayload::hologram,
                    PacketCodecs.BOOL, BoardActorStatePayload::touchable,
                    BoardActorStatePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
