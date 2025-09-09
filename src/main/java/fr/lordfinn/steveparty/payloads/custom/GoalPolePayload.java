package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import static fr.lordfinn.steveparty.payloads.ModPayloads.GOAL_POLE_PAYLOAD;

public record GoalPolePayload(BlockPos pos, GoalPoleBlockEntity.Comparator comparator, int value) implements CustomPayload {
    public static final CustomPayload.Id<GoalPolePayload> ID = new CustomPayload.Id<>(GOAL_POLE_PAYLOAD);
    public static final PacketCodec<ByteBuf, GoalPoleBlockEntity.Comparator> COMPARATOR_CODEC =
            new PacketCodec<>() {
                @Override
                public GoalPoleBlockEntity.Comparator decode(ByteBuf buf) {
                    int i = buf.readInt();
                    return GoalPoleBlockEntity.Comparator.values()[i];
                }

                @Override
                public void encode(ByteBuf buf, GoalPoleBlockEntity.Comparator comp) {
                    buf.writeInt(comp.ordinal());
                }
            };
    public static final PacketCodec<RegistryByteBuf, GoalPolePayload> CODEC =
            PacketCodec.tuple(
                    BlockPos.PACKET_CODEC, GoalPolePayload::pos,
                    COMPARATOR_CODEC, GoalPolePayload::comparator,
                    PacketCodecs.INTEGER, GoalPolePayload::value,
                    GoalPolePayload::new
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
