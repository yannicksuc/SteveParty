package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.board.WrenchActions;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Client → server: a Wrench control that vanilla does not send (the mode key, sneak + mouse wheel...), applied to the
 * Wrench in the main hand.
 *
 * @param action    what to do (see {@link Action})
 * @param direction +1 / -1 for the wheel, 1 otherwise
 */
public record WrenchActionPayload(int action, int direction) implements CustomPayload {
    public static final Id<WrenchActionPayload> ID = new Id<>(Steveparty.id("wrench_action"));
    public static final PacketCodec<RegistryByteBuf, WrenchActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, WrenchActionPayload::action,
            PacketCodecs.VAR_INT, WrenchActionPayload::direction,
            WrenchActionPayload::new);

    public enum Action {
        /** Next / previous mode. */
        MODE
    }

    public WrenchActionPayload(Action action, int direction) {
        this(action.ordinal(), direction);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** Server side. */
    public void handle(ServerPlayerEntity player) {
        if (action < 0 || action >= Action.values().length) return;
        ItemStack wrench = player.getMainHandStack();
        if (!WrenchActions.isWrench(wrench)) return;
        WrenchActions.control(player, wrench, Action.values()[action], Integer.signum(direction));
    }
}
