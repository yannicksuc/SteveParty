package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.minigame.PageZoneTool;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.packet.CustomPayload;

/**
 * Client → server, a Mini-game Page in zone mode in hand: sneak + mouse wheel moves the face of its zone the player
 * looks at ({@code amount} blocks, outward when positive; the server finds the face itself); {@code amount} 0 ends
 * the mode (Échap).
 */
public record PageZonePayload(int amount) implements CustomPayload {
    public static final Id<PageZonePayload> ID = new Id<>(Steveparty.id("page_zone"));
    public static final PacketCodec<RegistryByteBuf, PageZonePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, PageZonePayload::amount,
            PageZonePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** Server side. */
    public void handle(ServerPlayerEntity player) {
        ItemStack held = PageZoneTool.held(player);
        if (amount == 0) {
            if (held != null) PageZoneTool.end(player, held);
        } else {
            PageZoneTool.scroll(player, held, amount);
        }
    }
}
