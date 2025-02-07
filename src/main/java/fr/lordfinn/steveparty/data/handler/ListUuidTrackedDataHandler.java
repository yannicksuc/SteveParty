package fr.lordfinn.steveparty.data.handler;

import net.minecraft.entity.data.TrackedDataHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ListUuidTrackedDataHandler implements TrackedDataHandler<List<UUID>> {

    public static final ListUuidTrackedDataHandler INSTANCE = new ListUuidTrackedDataHandler();

    private ListUuidTrackedDataHandler() {}

    @Override
    public PacketCodec<PacketByteBuf, List<UUID>> codec() {
        return new PacketCodec<PacketByteBuf, List<UUID>>() {
            @Override
            public void encode(PacketByteBuf buf, List<UUID> value) {
                buf.writeVarInt(value.size());
                for (UUID uuid : value) {
                    buf.writeUuid(uuid);
                }
            }

            @Override
            public List<UUID> decode(PacketByteBuf buf) {
                int size = buf.readVarInt();
                List<UUID> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(buf.readUuid());
                }
                return list;
            }
        };
    }

    @Override
    public List<UUID> copy(List<UUID> value) {
        return new ArrayList<>(value);
    }
}
