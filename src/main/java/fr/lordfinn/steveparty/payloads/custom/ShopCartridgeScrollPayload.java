package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.payloads.ServerboundPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/** Client → server: sneak + mouse wheel with a Shop Cartridge in the main hand sets how many items a stop sells. */
public record ShopCartridgeScrollPayload(int direction) implements ServerboundPayload {
    public static final Id<ShopCartridgeScrollPayload> ID = new Id<>(Steveparty.id("shop_cartridge_scroll"));
    public static final PacketCodec<RegistryByteBuf, ShopCartridgeScrollPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ShopCartridgeScrollPayload::direction,
            ShopCartridgeScrollPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** Server side. */
    @Override
    public void handle(ServerPlayerEntity player) {
        ShopCartridgeItem.scroll(player, player.getMainHandStack(), direction);
    }
}
