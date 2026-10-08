package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.dice.DicePrompts;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: a dice prompt to show ({@link DicePrompts}), or to close when it has no option (answered, timed out).
 *
 * @param id           the prompt, sent back with the answer
 * @param list         rows of icon and label rather than a grid of icons
 * @param timeoutTicks time left to answer (the client shows it running out)
 * @param defaultIndex the option taken when the time is over
 * @param picked       one prompt of a series (the dice of a Choice Double / Triple Dice): the answers already given
 *                     (one per earlier step, its option)
 * @param steps        the number of steps of that series (this one is step {@code picked.size()}), 1 if alone
 */
public record DicePromptPayload(int id, Text title, boolean list, List<DicePrompts.Option> options, int timeoutTicks,
                                int defaultIndex, List<DicePrompts.Option> picked, int steps) implements CustomPayload {
    public static final CustomPayload.Id<DicePromptPayload> ID = new CustomPayload.Id<>(Steveparty.id("dice-prompt"));
    private static final int MAX_OPTIONS = 64;

    public static final PacketCodec<RegistryByteBuf, DicePromptPayload> CODEC = new PacketCodec<>() {
        @Override
        public DicePromptPayload decode(RegistryByteBuf buf) {
            int id = buf.readVarInt();
            Text title = TextCodecs.REGISTRY_PACKET_CODEC.decode(buf);
            boolean list = buf.readBoolean();
            int count = Math.min(buf.readVarInt(), MAX_OPTIONS);
            List<DicePrompts.Option> options = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                ItemStack icon = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
                options.add(new DicePrompts.Option(icon, TextCodecs.REGISTRY_PACKET_CODEC.decode(buf)));
            }
            int timeoutTicks = buf.readVarInt();
            int defaultIndex = buf.readVarInt();
            int pickedCount = Math.min(buf.readVarInt(), MAX_OPTIONS);
            List<DicePrompts.Option> picked = new ArrayList<>(pickedCount);
            for (int i = 0; i < pickedCount; i++) {
                ItemStack icon = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
                picked.add(new DicePrompts.Option(icon, TextCodecs.REGISTRY_PACKET_CODEC.decode(buf)));
            }
            return new DicePromptPayload(id, title, list, options, timeoutTicks, defaultIndex, picked, buf.readVarInt());
        }

        @Override
        public void encode(RegistryByteBuf buf, DicePromptPayload payload) {
            buf.writeVarInt(payload.id);
            TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, payload.title);
            buf.writeBoolean(payload.list);
            List<DicePrompts.Option> options = payload.options.size() > MAX_OPTIONS ? payload.options.subList(0, MAX_OPTIONS) : payload.options;
            buf.writeVarInt(options.size());
            for (DicePrompts.Option option : options) {
                ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, option.icon());
                TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, option.label());
            }
            buf.writeVarInt(payload.timeoutTicks);
            buf.writeVarInt(payload.defaultIndex);
            List<DicePrompts.Option> picked = payload.picked.size() > MAX_OPTIONS ? payload.picked.subList(0, MAX_OPTIONS) : payload.picked;
            buf.writeVarInt(picked.size());
            for (DicePrompts.Option option : picked) {
                ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, option.icon());
                TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, option.label());
            }
            buf.writeVarInt(payload.steps);
        }
    };

    /** The prompt is over: the client closes its picker. */
    public static DicePromptPayload close(int id) {
        return new DicePromptPayload(id, Text.empty(), false, List.of(), 0, 0, List.of(), 1);
    }

    public boolean isClose() {
        return options.isEmpty();
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
