package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerBlockEvents;
import fr.lordfinn.steveparty.payloads.ServerboundPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * A player punched a villager block where they can't break blocks (adventure mode): vanilla sends nothing then, so
 * the client tells the server (ClientPlayerInteractionManagerVillagerPunchMixin), which checks it
 * (VillagerBlockEvents#onPunchRequest) and makes the block react.
 */
public record VillagerBlockPunchPayload(BlockPos pos) implements ServerboundPayload {
    public static final CustomPayload.Id<VillagerBlockPunchPayload> ID = new CustomPayload.Id<>(Steveparty.id("villager_block_punch"));
    public static final PacketCodec<RegistryByteBuf, VillagerBlockPunchPayload> CODEC =
            PacketCodec.tuple(BlockPos.PACKET_CODEC, VillagerBlockPunchPayload::pos, VillagerBlockPunchPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    @Override
    public void handle(ServerPlayerEntity player) {
        VillagerBlockEvents.onPunchRequest(player, pos);
    }

}
