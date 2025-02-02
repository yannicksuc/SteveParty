package fr.lordfinn.steveparty.payloads;

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
/*    public static final PacketCodec<PacketByteBuf, HereWeComeBookPayload> CODEC =
            new PacketCodec<>() {
                @Override
                public HereWeComeBookPayload decode(PacketByteBuf buf) {
                    int size = buf.readInt(); // Read the size of the list
                    List<TeleportingTarget> teleportingTargets = new ArrayList<>();
                    for (int i = 0; i < size; i++) {
                        teleportingTargets.add(TeleportingTarget.CODEC.decode()); // Decode each TeleportingTarget
                    }

                    return new HereWeComeBookPayload(teleportingTargets);
                }

                @Override
                public void encode(PacketByteBuf buf, HereWeComeBookPayload payload) {
                    buf.writeInt(payload.teleportingTargets.size()); // Write the size of the list
                    payload.teleportingTargets.forEach(target -> {
                        TeleportingTarget.CODEC.encode(buf, target); // Encode each TeleportingTarget
                    });
                }
            };*/

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
