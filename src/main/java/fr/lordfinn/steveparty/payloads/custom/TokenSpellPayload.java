package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.TokenizerWandItem;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * C2S: the player cast the token spell (confirmed the size slider of the Tokenizer Wand). Everything is validated
 * again by the server in {@link TokenizerWandItem#castSpell}.
 *
 * @param entityId network id of the targeted mob
 * @param size     chosen size of the token's biggest dimension, in blocks (clamped by the server)
 * @param color    token colour computed from the mob texture (0xRRGGBB), or -1 if the client could not compute it
 */
public record TokenSpellPayload(int entityId, float size, int color) implements CustomPayload {
    public static final CustomPayload.Id<TokenSpellPayload> ID = new CustomPayload.Id<>(Steveparty.id("token-spell"));
    public static final PacketCodec<RegistryByteBuf, TokenSpellPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, TokenSpellPayload::entityId,
                    PacketCodecs.FLOAT, TokenSpellPayload::size,
                    PacketCodecs.INTEGER, TokenSpellPayload::color,
                    TokenSpellPayload::new);

    /** Server-side handler (must run on the server thread). */
    public void handle(ServerPlayerEntity player) {
        TokenizerWandItem.castSpell(player, entityId, size, color);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
