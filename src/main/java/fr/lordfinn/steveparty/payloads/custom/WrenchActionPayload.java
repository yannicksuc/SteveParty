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
        MODE,
        /** Left click in the air: undo the last link action. */
        UNDO,
        /** Sneak + left click in the air: redo. */
        REDO,
        /** Sneak + mode key: placed board spaces are linked (or not) while tracing. */
        AUTO_LINK,
        /** Sneak + mouse wheel with a 16-slot origin: the slot whose cartridge is edited. */
        SLOT,
        /** Sneak + mouse wheel with the Tile Linker Brush: its level (the slot its links go in). */
        LEVEL
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
        Action control = Action.values()[action];
        if (wrench.getItem() instanceof fr.lordfinn.steveparty.items.custom.TileLinkerBrushItem) {
            // The brush: its level, and the undo history shared with the Wrench
            switch (control) {
                case LEVEL -> fr.lordfinn.steveparty.board.TileLinkerBrush.cycleLevel(player, wrench, direction);
                case UNDO, REDO -> fr.lordfinn.steveparty.board.LinkHistory.undo(player, control == Action.UNDO, null);
                default -> {
                }
            }
            return;
        }
        if (!WrenchActions.isWrench(wrench) || control == Action.LEVEL) return;
        WrenchActions.control(player, wrench, Action.values()[action], Integer.signum(direction));
    }
}
