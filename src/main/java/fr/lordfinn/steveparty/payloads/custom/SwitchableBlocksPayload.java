package fr.lordfinn.steveparty.payloads.custom;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

import static fr.lordfinn.steveparty.payloads.ModPayloads.SWITCHABLE_BLOCKS_PAYLOAD;

/** Blocks made switchable by the server config (the tag is already synced by vanilla). */
public record SwitchableBlocksPayload(List<Identifier> blocks) implements CustomPayload {
    public static final CustomPayload.Id<SwitchableBlocksPayload> ID = new CustomPayload.Id<>(SWITCHABLE_BLOCKS_PAYLOAD);
    public static final PacketCodec<RegistryByteBuf, SwitchableBlocksPayload> CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC.collect(PacketCodecs.toList()), SwitchableBlocksPayload::blocks,
            SwitchableBlocksPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
