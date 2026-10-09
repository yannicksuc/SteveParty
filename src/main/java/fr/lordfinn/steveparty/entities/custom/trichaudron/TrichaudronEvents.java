package fr.lordfinn.steveparty.entities.custom.trichaudron;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.payloads.Payloads;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * What reaches a Trichaudron from outside its hitbox: a rider's clicks (his attack and use keys fire his head, or grab a
 * wall in the air, or pour a lava bucket in: {@link RiderClick}, sent by the client in place of the vanilla attack and
 * use).
 */
public final class TrichaudronEvents {
    private TrichaudronEvents() {
    }

    public static void initialize() {
        Payloads.c2s(RiderClick.ID, RiderClick.CODEC, (player, payload) -> {
            if (player.getVehicle() instanceof TrichaudronEntity trichaudron) trichaudron.riderClick(player);
        });
    }

    /** A rider clicked (attack or use): fire his head, or grab a wall in the air. */
    public record RiderClick() implements CustomPayload {
        public static final Id<RiderClick> ID = new Id<>(Steveparty.id("trichaudron_rider_click"));
        public static final PacketCodec<PacketByteBuf, RiderClick> CODEC = PacketCodec.unit(new RiderClick());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
