package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: the points a goal pole base counted since its last popups, summed per holder (sent at most every
 * {@link fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity#POPUP_INTERVAL} ticks): the client shows them as
 * « +N » / « −N » over the base, merged into the holder's popup still showing.
 */
public record GoalPolePopupsPayload(BlockPos base, List<Gain> gains) implements CustomPayload {
    public static final CustomPayload.Id<GoalPolePopupsPayload> ID = new CustomPayload.Id<>(Steveparty.id("goal_pole_popups"));
    private static final int MAX_GAINS = 256;

    public record Gain(String holder, long delta) {}

    public GoalPolePopupsPayload {
        gains = List.copyOf(gains);
    }

    public static final PacketCodec<RegistryByteBuf, GoalPolePopupsPayload> CODEC = new PacketCodec<>() {
        @Override
        public GoalPolePopupsPayload decode(RegistryByteBuf buf) {
            BlockPos base = buf.readBlockPos();
            int count = Math.min(buf.readVarInt(), MAX_GAINS);
            List<Gain> gains = new ArrayList<>(count);
            for (int i = 0; i < count; i++) gains.add(new Gain(buf.readString(256), buf.readVarLong()));
            return new GoalPolePopupsPayload(base, gains);
        }

        @Override
        public void encode(RegistryByteBuf buf, GoalPolePopupsPayload payload) {
            buf.writeBlockPos(payload.base);
            int count = Math.min(payload.gains.size(), MAX_GAINS);
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                Gain gain = payload.gains.get(i);
                buf.writeString(gain.holder, 256);
                buf.writeVarLong(gain.delta);
            }
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
