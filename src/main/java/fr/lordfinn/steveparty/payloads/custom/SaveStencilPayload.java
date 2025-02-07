package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import static fr.lordfinn.steveparty.payloads.ModPayloads.SAVE_STENCIL_PAYLOAD;

public record SaveStencilPayload(byte[] shape, BlockPos pos) implements CustomPayload {
    public static final CustomPayload.Id<SaveStencilPayload> ID = new CustomPayload.Id<>(SAVE_STENCIL_PAYLOAD);

    public static final PacketCodec<PacketByteBuf, SaveStencilPayload> CODEC = new PacketCodec<>() {
        @Override
        public SaveStencilPayload decode(PacketByteBuf buf) {
            byte[] shape = buf.readByteArray();
            BlockPos pos = buf.readBlockPos();
            return new SaveStencilPayload(shape, pos);
        }

        @Override
        public void encode(PacketByteBuf buf, SaveStencilPayload payload) {
            buf.writeByteArray(payload.shape);
            buf.writeBlockPos(payload.pos);
        }
    };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
