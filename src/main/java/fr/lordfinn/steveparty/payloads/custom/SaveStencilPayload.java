package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.StencilMakerBlockEntity;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.payloads.ServerboundPayload;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import fr.lordfinn.steveparty.screen_handlers.custom.StencilMakerScreenHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public record SaveStencilPayload(byte[] shape, BlockPos pos) implements ServerboundPayload {
    public static final CustomPayload.Id<SaveStencilPayload> ID = new CustomPayload.Id<>(Steveparty.id("save_stencil"));
    /** A stencil is a 16x16 pixel grid. */
    public static final int SHAPE_SIZE = 256;

    public static final PacketCodec<PacketByteBuf, SaveStencilPayload> CODEC = new PacketCodec<>() {
        @Override
        public SaveStencilPayload decode(PacketByteBuf buf) {
            byte[] shape = buf.readByteArray(SHAPE_SIZE);
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

    @Override
    public void handle(ServerPlayerEntity player) {
        if (shape == null || shape.length != SHAPE_SIZE) return;
        // The stencil maker screen for this block must be open and the block in reach
        if (!(player.currentScreenHandler instanceof StencilMakerScreenHandler handler)
                || handler.getBlockEntity() == null
                || !pos.equals(handler.getBlockEntity().getPos())
                || !ScreenHandlerChecks.isInReach(player, pos)) return;
        if (player.getWorld().getBlockEntity(pos) instanceof StencilMakerBlockEntity blockEntity
                && blockEntity == handler.getBlockEntity()) {
            ItemStack stencil = blockEntity.getStencil();
            if (stencil.isEmpty() || !(stencil.getItem() instanceof StencilItem)) return;
            blockEntity.setStencilShape(shape);
        }
    }

}
