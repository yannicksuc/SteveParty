package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.components.DiceFacesComponent.Kind;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Uuids;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * S2C: where the reveal of a throw is ({@link fr.lordfinn.steveparty.dice.DiceReveal}), the whole of it each time (a
 * client joining in the middle shows it right): the dice stopped so far, the double / triple they make, then the total.
 *
 * @param throwId the throw (its lead die's network id): a new one replaces the one shown
 * @param roller  the player who threw it (the roller's own reveal is gold), empty if none
 * @param name    the roller's name
 * @param dice    the dice of the throw
 * @param faces   the faces revealed so far, in order
 * @param combo   2: a double, 3: a triple among them, 0: none
 * @param number  the number of the double / triple
 * @param total   what the throw does (power-up note included), once it is all revealed
 */
public record DiceRevealPayload(int throwId, Optional<UUID> roller, String name, int dice, List<DiceFace> faces, int combo,
                                int number, Optional<Text> total) implements CustomPayload {
    public static final CustomPayload.Id<DiceRevealPayload> ID = new CustomPayload.Id<>(Steveparty.id("dice_reveal"));
    private static final int MAX_DICE = 16;

    public static final PacketCodec<RegistryByteBuf, DiceRevealPayload> CODEC = new PacketCodec<>() {
        @Override
        public DiceRevealPayload decode(RegistryByteBuf buf) {
            int throwId = buf.readVarInt();
            Optional<UUID> roller = buf.readBoolean() ? Optional.of(Uuids.PACKET_CODEC.decode(buf)) : Optional.empty();
            String name = buf.readString(64);
            int dice = buf.readVarInt();
            int count = Math.min(buf.readVarInt(), MAX_DICE);
            Kind[] kinds = Kind.values();
            List<DiceFace> faces = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int kind = buf.readVarInt();
                faces.add(new DiceFace(kind >= 0 && kind < kinds.length ? kinds[kind] : Kind.NORMAL, buf.readVarInt()));
            }
            int combo = buf.readVarInt();
            int number = buf.readVarInt();
            Optional<Text> total = buf.readBoolean() ? Optional.of(TextCodecs.REGISTRY_PACKET_CODEC.decode(buf)) : Optional.empty();
            return new DiceRevealPayload(throwId, roller, name, dice, faces, combo, number, total);
        }

        @Override
        public void encode(RegistryByteBuf buf, DiceRevealPayload payload) {
            buf.writeVarInt(payload.throwId);
            buf.writeBoolean(payload.roller.isPresent());
            payload.roller.ifPresent(uuid -> Uuids.PACKET_CODEC.encode(buf, uuid));
            buf.writeString(payload.name.length() > 64 ? payload.name.substring(0, 64) : payload.name, 64);
            buf.writeVarInt(payload.dice);
            List<DiceFace> faces = payload.faces.size() > MAX_DICE ? payload.faces.subList(0, MAX_DICE) : payload.faces;
            buf.writeVarInt(faces.size());
            for (DiceFace face : faces) {
                buf.writeVarInt(face.kind().ordinal());
                buf.writeVarInt(face.value());
            }
            buf.writeVarInt(payload.combo);
            buf.writeVarInt(payload.number);
            buf.writeBoolean(payload.total.isPresent());
            payload.total.ifPresent(text -> TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, text));
        }
    };

    public static DiceRevealPayload of(int throwId, @Nullable UUID roller, String name, int dice, List<DiceFace> faces,
                                       int combo, int number, @Nullable Text total) {
        return new DiceRevealPayload(throwId, Optional.ofNullable(roller), name, dice, List.copyOf(faces), combo, number,
                Optional.ofNullable(total));
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
