package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/** Client → server: sneak + mouse wheel with a stencil gun in the main hand picks the next stencil or colour. */
public record StencilGunScrollPayload(boolean colors, int direction) implements CustomPayload {
    public static final Id<StencilGunScrollPayload> ID = new Id<>(Steveparty.id("stencil_gun_scroll"));
    public static final PacketCodec<RegistryByteBuf, StencilGunScrollPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, StencilGunScrollPayload::colors,
            PacketCodecs.VAR_INT, StencilGunScrollPayload::direction,
            StencilGunScrollPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** Server side. */
    public void handle(ServerPlayerEntity player) {
        ItemStack gun = player.getMainHandStack();
        if (!(gun.getItem() instanceof StencilGunItem)) return;
        StencilGunItem.scroll(gun, colors, Integer.signum(direction));
    }
}
