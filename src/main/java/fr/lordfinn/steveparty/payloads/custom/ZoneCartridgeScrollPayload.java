package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.ZoneCartridgeItem;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Client → server: sneak + mouse wheel with a Zone Cartridge in the main hand moves the face of its box the player
 * looks at ({@code amount} blocks, outward when positive). The server finds the face itself.
 */
public record ZoneCartridgeScrollPayload(int amount) implements CustomPayload {
    public static final Id<ZoneCartridgeScrollPayload> ID = new Id<>(Steveparty.id("zone_cartridge_scroll"));
    public static final PacketCodec<RegistryByteBuf, ZoneCartridgeScrollPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ZoneCartridgeScrollPayload::amount,
            ZoneCartridgeScrollPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** Server side. */
    public void handle(ServerPlayerEntity player) {
        ZoneCartridgeItem.scroll(player, player.getMainHandStack(), amount);
    }
}
