package fr.lordfinn.steveparty.payloads.custom;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import static fr.lordfinn.steveparty.payloads.ModPayloads.VILLAGER_BLOCK_PUNCH_PAYLOAD;

/**
 * A player punched a villager block where they can't break blocks (adventure mode): vanilla sends nothing then, so
 * the client tells the server (ClientPlayerInteractionManagerVillagerPunchMixin), which checks it
 * (VillagerBlockEvents#onPunchRequest) and makes the block react.
 */
public record VillagerBlockPunchPayload(BlockPos pos) implements CustomPayload {
    public static final CustomPayload.Id<VillagerBlockPunchPayload> ID = new CustomPayload.Id<>(VILLAGER_BLOCK_PUNCH_PAYLOAD);
    public static final PacketCodec<RegistryByteBuf, VillagerBlockPunchPayload> CODEC =
            PacketCodec.tuple(BlockPos.PACKET_CODEC, VillagerBlockPunchPayload::pos, VillagerBlockPunchPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
