package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Sent once when an entity gets squished (tokenized): the server has already set its final scale, the client
 * plays the shrink / spin / wobble animation locally from {@code startScale} to {@code targetScale}.
 *
 * @param entityId    network id of the squished entity
 * @param startScale  scale attribute base value before the squish
 * @param targetScale final scale attribute base value (already applied server side)
 * @param duration    animation duration in ticks (the effect duration)
 * @param amplifier   effect amplifier (drives the spin speed)
 * @param quiet       no boings nor sparkle sounds (a token resized again and again: only its caster hears it)
 */
public record SquishAnimationPayload(int entityId, float startScale, float targetScale, int duration, int amplifier, boolean quiet)
        implements CustomPayload {
    public static final CustomPayload.Id<SquishAnimationPayload> ID = new CustomPayload.Id<>(Steveparty.id("squish_animation"));
    public static final PacketCodec<RegistryByteBuf, SquishAnimationPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, SquishAnimationPayload::entityId,
                    PacketCodecs.FLOAT, SquishAnimationPayload::startScale,
                    PacketCodecs.FLOAT, SquishAnimationPayload::targetScale,
                    PacketCodecs.VAR_INT, SquishAnimationPayload::duration,
                    PacketCodecs.VAR_INT, SquishAnimationPayload::amplifier,
                    PacketCodecs.BOOL, SquishAnimationPayload::quiet,
                    SquishAnimationPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
