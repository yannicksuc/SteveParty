package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.payloads.ServerboundPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/** C2S: the option a player picked in a dice prompt ({@link DicePrompts}: checked against their pending prompt). */
public record DicePromptAnswerPayload(int id, int index) implements ServerboundPayload {
    public static final CustomPayload.Id<DicePromptAnswerPayload> ID = new CustomPayload.Id<>(Steveparty.id("dice_prompt_answer"));
    public static final PacketCodec<RegistryByteBuf, DicePromptAnswerPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, DicePromptAnswerPayload::id,
            PacketCodecs.VAR_INT, DicePromptAnswerPayload::index,
            DicePromptAnswerPayload::new);

    @Override
    public void handle(ServerPlayerEntity player) {
        DicePrompts.answer(player, id, index);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
