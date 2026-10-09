package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.SneakScrollItem;
import fr.lordfinn.steveparty.payloads.ServerboundPayload;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/** Client → server: sneak + mouse wheel with a {@link SneakScrollItem} in the main hand ({@code direction} ±1). */
public record HeldItemScrollPayload(int direction) implements ServerboundPayload {
    public static final Id<HeldItemScrollPayload> ID = new Id<>(Steveparty.id("held_item_scroll"));
    public static final PacketCodec<RegistryByteBuf, HeldItemScrollPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, HeldItemScrollPayload::direction,
            HeldItemScrollPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    @Override
    public void handle(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (stack.getItem() instanceof SneakScrollItem item) item.onSneakScroll(player, stack, direction);
    }
}
