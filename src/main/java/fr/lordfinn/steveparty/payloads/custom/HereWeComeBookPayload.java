package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.items.custom.teleportation_books.TeleportingTarget;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import java.util.List;

import static fr.lordfinn.steveparty.payloads.ModPayloads.HERE_WE_COME_BOOK_PAYLOAD;

public record HereWeComeBookPayload(List<TeleportingTarget> teleportingTargets) implements CustomPayload {
    public static final CustomPayload.Id<HereWeComeBookPayload> ID = new CustomPayload.Id<>(HERE_WE_COME_BOOK_PAYLOAD);
    public static final PacketCodec<ByteBuf, HereWeComeBookPayload> CODEC =
            PacketCodecs.codec(TeleportingTarget.CODEC.listOf())
                    .xmap(HereWeComeBookPayload::new, HereWeComeBookPayload::teleportingTargets);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
