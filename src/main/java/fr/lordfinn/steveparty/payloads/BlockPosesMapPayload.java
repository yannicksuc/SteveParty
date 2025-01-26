package fr.lordfinn.steveparty.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

import static fr.lordfinn.steveparty.payloads.ModPayloads.BLOCK_POSES_MAP_PAYLOAD;

public record BlockPosesMapPayload(Map<BlockPos, BlockPos> blockPoses) implements CustomPayload {
    public static final CustomPayload.Id<BlockPosesMapPayload> ID = new CustomPayload.Id<>(BLOCK_POSES_MAP_PAYLOAD);
    public static final PacketCodec<PacketByteBuf, BlockPosesMapPayload> CODEC =
            new PacketCodec<>() {
                @Override
                public BlockPosesMapPayload decode(PacketByteBuf buf) {
                    int size = buf.readInt(); // Read the size of the map
                    Map<BlockPos, BlockPos> blockPoses = new HashMap<>();

                    for (int i = 0; i < size; i++) {
                        BlockPos key = buf.readBlockPos(); // Read the key (BlockPos)
                        BlockPos value = buf.readBlockPos(); // Read the value (BlockPos)
                        blockPoses.put(key, value);
                    }

                    return new BlockPosesMapPayload(blockPoses);
                }

                @Override
                public void encode(PacketByteBuf buf, BlockPosesMapPayload payload) {
                    buf.writeInt(payload.blockPoses.size());

                    payload.blockPoses.forEach((key, value) -> {
                        buf.writeBlockPos(key); // Write the key (BlockPos)
                        buf.writeBlockPos(value); // Write the value (BlockPos)
                    });
                }
            };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}

