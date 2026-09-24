package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * S2C: the server accepted a Tokenizer Wand use on a mob, the client opens the token spell screen (size slider).
 *
 * @param entityId     network id of the targeted mob
 * @param currentSize  size pre-selected in the slider (blocks): the default for a new token, the current size of a
 *                     token being resized
 * @param resize       true when the mob is already a token (resize)
 * @param currentColor colour already set on the token (0xRRGGBB, kept on resize), or -1
 */
public record OpenTokenSpellPayload(int entityId, float currentSize, boolean resize, int currentColor) implements CustomPayload {
    public static final CustomPayload.Id<OpenTokenSpellPayload> ID = new CustomPayload.Id<>(Steveparty.id("open-token-spell"));
    public static final PacketCodec<RegistryByteBuf, OpenTokenSpellPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, OpenTokenSpellPayload::entityId,
                    PacketCodecs.FLOAT, OpenTokenSpellPayload::currentSize,
                    PacketCodecs.BOOL, OpenTokenSpellPayload::resize,
                    PacketCodecs.INTEGER, OpenTokenSpellPayload::currentColor,
                    OpenTokenSpellPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
