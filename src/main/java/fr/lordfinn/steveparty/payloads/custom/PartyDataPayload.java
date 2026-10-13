package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The party's steps and tokens, frozen on the server thread when the payload is made: Netty encodes it later on its
 * own thread, while the server keeps adding steps (holding the live {@link PartyData} kicked players at party launch).
 */
public record PartyDataPayload(List<NbtCompound> steps, List<UUID> tokens, int stepIndex, int nbTurn) implements CustomPayload {
    public static final CustomPayload.Id<PartyDataPayload> ID = new CustomPayload.Id<>(Steveparty.id("party_data"));
    public static final PacketCodec<PacketByteBuf, PartyDataPayload> CODEC =
            new PacketCodec<>() {
                @Override
                public PartyDataPayload decode(PacketByteBuf buf) {
                    int stepCount = buf.readInt();
                    List<NbtCompound> steps = new ArrayList<>(stepCount);
                    for (int i = 0; i < stepCount; i++) steps.add(buf.readNbt());
                    int tokenCount = buf.readInt();
                    List<UUID> tokens = new ArrayList<>(tokenCount);
                    for (int i = 0; i < tokenCount; i++) tokens.add(buf.readUuid());
                    return new PartyDataPayload(steps, tokens, buf.readInt(), buf.readInt());
                }

                @Override
                public void encode(PacketByteBuf buf, PartyDataPayload payload) {
                    buf.writeInt(payload.steps.size());
                    for (NbtCompound step : payload.steps) buf.writeNbt(step);
                    buf.writeInt(payload.tokens.size());
                    for (UUID token : payload.tokens) buf.writeUuid(token);
                    buf.writeInt(payload.stepIndex);
                    buf.writeInt(payload.nbTurn);
                }
            };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** Freezes {@code partyData} now (call it on the server thread). */
    public static PartyDataPayload fromPartyData(PartyData partyData) {
        return new PartyDataPayload(partyData.stepsNbt(), List.copyOf(partyData.getTokens()),
                partyData.getStepIndex(), partyData.getNbTurn());
    }

    /** The party as the client rebuilds it. */
    public PartyData partyData() {
        return PartyData.received(steps, tokens, stepIndex, nbTurn);
    }
}
