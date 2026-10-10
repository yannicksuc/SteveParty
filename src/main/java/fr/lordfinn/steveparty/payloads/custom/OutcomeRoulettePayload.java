package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.hud.OutcomeRoulette;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * S2C: an outcome roulette ({@link OutcomeRoulette}, service {@code OutcomeRoulettes}) starts, or goes on for a
 * player who just joined: where its panel floats in the world, its lines, the result the server drew, and how far it
 * is. Every client lights the same line
 * at the same time from these numbers alone. A payload without lines takes roulette {@code id} off the screen (its
 * show was stopped).
 *
 * @param id      the roulette: a new one replaces the one shown
 * @param trigger the player it is about (their own title is gold), empty if none
 * @param anchor  where the panel floats (the middle of its bottom edge, world coordinates), empty: on the HUD only
 * @param title   who triggered it: « LordFinn lands on the Mistigri! »
 * @param caption what is at stake, under the title: « The Mistigri's sentence »
 * @param lines   the possible outcomes, top to bottom
 * @param result  the line it stops on
 * @param steps   how many times the light moves ({@link OutcomeRoulette#steps})
 * @param elapsed ticks since it started (0 when it starts; more for a player joining in the middle)
 */
public record OutcomeRoulettePayload(int id, Optional<UUID> trigger, Optional<Vec3d> anchor, Text title, Text caption, List<Line> lines, int result,
                                     int steps, int elapsed) implements CustomPayload {
    public static final CustomPayload.Id<OutcomeRoulettePayload> ID = new CustomPayload.Id<>(Steveparty.id("outcome_roulette"));

    /** How a line reads: a loss (red), a mild one (green), or neither. */
    public enum Tone { NEUTRAL, BAD, GOOD }

    /**
     * One possible outcome.
     *
     * @param icon   a small item before it (empty: none)
     * @param text   what happens: « −10 coins »
     * @param tone   how it reads
     * @param chance its chance in percent, -1 to keep it unsaid
     */
    public record Line(ItemStack icon, Text text, Tone tone, int chance) {
    }

    public static final PacketCodec<RegistryByteBuf, OutcomeRoulettePayload> CODEC = new PacketCodec<>() {
        @Override
        public OutcomeRoulettePayload decode(RegistryByteBuf buf) {
            int id = buf.readVarInt();
            Optional<UUID> trigger = buf.readBoolean() ? Optional.of(Uuids.PACKET_CODEC.decode(buf)) : Optional.empty();
            Optional<Vec3d> anchor = buf.readBoolean() ? Optional.of(new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble())) : Optional.empty();
            Text title = TextCodecs.REGISTRY_PACKET_CODEC.decode(buf);
            Text caption = TextCodecs.REGISTRY_PACKET_CODEC.decode(buf);
            int count = Math.min(buf.readVarInt(), OutcomeRoulette.MAX_LINES);
            Tone[] tones = Tone.values();
            List<Line> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                ItemStack icon = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
                Text text = TextCodecs.REGISTRY_PACKET_CODEC.decode(buf);
                int tone = buf.readVarInt();
                lines.add(new Line(icon, text, tone >= 0 && tone < tones.length ? tones[tone] : Tone.NEUTRAL, buf.readVarInt()));
            }
            return new OutcomeRoulettePayload(id, trigger, anchor, title, caption, lines, buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public void encode(RegistryByteBuf buf, OutcomeRoulettePayload payload) {
            buf.writeVarInt(payload.id);
            buf.writeBoolean(payload.trigger.isPresent());
            payload.trigger.ifPresent(uuid -> Uuids.PACKET_CODEC.encode(buf, uuid));
            buf.writeBoolean(payload.anchor.isPresent());
            payload.anchor.ifPresent(at -> {
                buf.writeDouble(at.x);
                buf.writeDouble(at.y);
                buf.writeDouble(at.z);
            });
            TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, payload.title);
            TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, payload.caption);
            List<Line> lines = payload.lines.size() > OutcomeRoulette.MAX_LINES ? payload.lines.subList(0, OutcomeRoulette.MAX_LINES) : payload.lines;
            buf.writeVarInt(lines.size());
            for (Line line : lines) {
                ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, line.icon);
                TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, line.text);
                buf.writeVarInt(line.tone.ordinal());
                buf.writeVarInt(line.chance);
            }
            buf.writeVarInt(payload.result);
            buf.writeVarInt(payload.steps);
            buf.writeVarInt(payload.elapsed);
        }
    };

    /** Takes roulette {@code id} off the screen. */
    public static OutcomeRoulettePayload stop(int id) {
        return new OutcomeRoulettePayload(id, Optional.empty(), Optional.empty(), Text.empty(), Text.empty(), List.of(), 0, 0, 0);
    }

    /** It stops the roulette shown (no lines). */
    public boolean isStop() {
        return lines.isEmpty();
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
