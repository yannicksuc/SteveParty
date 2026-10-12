package fr.lordfinn.steveparty.entities.custom.bubble;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.payloads.Payloads;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * What reaches a bubble from its riders beyond its hitbox: a rider's punch ({@link RiderPunch}, sent by the client in
 * place of the vanilla attack: inside it, his crosshair only ever finds his own bubble), which pops it.
 */
public final class BubbleEvents {
    private BubbleEvents() {
    }

    public static void initialize() {
        Payloads.c2s(RiderPunch.ID, RiderPunch.CODEC, (player, payload) -> {
            if (player.getVehicle() instanceof BubbleEntity bubble) bubble.riderPunch(player);
        });
    }

    /** A rider punched: he leaves his bubble, it pops. */
    public record RiderPunch() implements CustomPayload {
        public static final Id<RiderPunch> ID = new Id<>(Steveparty.id("bubble_rider_punch"));
        public static final PacketCodec<PacketByteBuf, RiderPunch> CODEC = PacketCodec.unit(new RiderPunch());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
