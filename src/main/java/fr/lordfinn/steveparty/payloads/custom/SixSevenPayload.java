package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * S2C: a Double Dice just showed a 6 and a 7 ({@link fr.lordfinn.steveparty.dice.SixSeven}): these players do the « 6-7 »
 * with their arms from {@code startTick} (world time) on, the client plays it on its own from there.
 *
 * @param startTick the world time the gesture starts at
 * @param x         where the dice are (the two notes are heard there)
 * @param y         where the dice are
 * @param z         where the dice are
 * @param dancers   the players doing it
 */
public record SixSevenPayload(long startTick, double x, double y, double z, List<UUID> dancers) implements CustomPayload {
    public static final CustomPayload.Id<SixSevenPayload> ID = new CustomPayload.Id<>(Steveparty.id("six_seven"));
    private static final int MAX_DANCERS = 256;

    public SixSevenPayload {
        dancers = List.copyOf(dancers);
    }

    public static final PacketCodec<RegistryByteBuf, SixSevenPayload> CODEC = new PacketCodec<>() {
        @Override
        public SixSevenPayload decode(RegistryByteBuf buf) {
            long startTick = buf.readVarLong();
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            int count = Math.min(buf.readVarInt(), MAX_DANCERS);
            List<UUID> dancers = new ArrayList<>(count);
            for (int i = 0; i < count; i++) dancers.add(Uuids.PACKET_CODEC.decode(buf));
            return new SixSevenPayload(startTick, x, y, z, dancers);
        }

        @Override
        public void encode(RegistryByteBuf buf, SixSevenPayload payload) {
            buf.writeVarLong(payload.startTick);
            buf.writeDouble(payload.x);
            buf.writeDouble(payload.y);
            buf.writeDouble(payload.z);
            int count = Math.min(payload.dancers.size(), MAX_DANCERS);
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) Uuids.PACKET_CODEC.encode(buf, payload.dancers.get(i));
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
