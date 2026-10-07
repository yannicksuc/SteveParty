package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Client → server: a sector picked on the wheel of the tool in the main hand (left click with the Tile Linker Brush or
 * the Stencil Hammer). The server checks the tool is still in hand and the value valid; anything else is ignored.
 *
 * @param action what the sector does (see {@link Action})
 * @param value  its value: a level (-1: powered), a cartridge's raw item id, a hammer slot (-1: engrave)...
 */
public record ToolWheelPayload(int action, int value) implements CustomPayload {
    public static final Id<ToolWheelPayload> ID = new Id<>(Steveparty.id("tool_wheel"));
    public static final PacketCodec<RegistryByteBuf, ToolWheelPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ToolWheelPayload::action,
            PacketCodecs.VAR_INT, ToolWheelPayload::value,
            ToolWheelPayload::new);

    public enum Action {
        /** Brush: its level, -1 (the powered slot) or 0-15. */
        BRUSH_LEVEL,
        /** Brush: the kind of Cartridge the painted tiles get (raw item id), or {@link #KEEP_CARTRIDGES}. */
        BRUSH_CARTRIDGE,
        /** Brush: undo the last link edit. */
        BRUSH_UNDO,
        /** Brush: redo. */
        BRUSH_REDO,
        /** Hammer: the stencil slot (0-8) stamped. */
        HAMMER_STENCIL,
        /** Hammer: the dye slot (0-8) painted, or -1: engraved, no paint. */
        HAMMER_DYE,
        /** Hammer: opens it to load / unload its stencils and dyes. */
        HAMMER_OPEN
    }

    /** {@link Action#BRUSH_CARTRIDGE}: no kind picked, the painted tiles keep their cartridge. */
    public static final int KEEP_CARTRIDGES = -1;

    public ToolWheelPayload(Action action, int value) {
        this(action.ordinal(), value);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    /**
     * Server side.
     *
     * @return whether the selection was applied
     */
    public boolean handle(ServerPlayerEntity player) {
        if (action < 0 || action >= Action.values().length || player.isSpectator()) return false;
        ItemStack tool = player.getMainHandStack();
        Action picked = Action.values()[action];
        return switch (picked) {
            case BRUSH_LEVEL -> TileLinkerBrush.isBrush(tool) && TileLinkerBrush.setLevel(player, tool, value);
            case BRUSH_CARTRIDGE -> TileLinkerBrush.isBrush(tool) && value >= KEEP_CARTRIDGES && value < Registries.ITEM.size()
                    && TileLinkerBrush.setCartridge(player, tool, value == KEEP_CARTRIDGES ? null : Registries.ITEM.get(value));
            case BRUSH_UNDO, BRUSH_REDO -> TileLinkerBrush.isBrush(tool) && TileLinkerBrush.undo(player, tool, picked == Action.BRUSH_UNDO);
            case HAMMER_STENCIL -> tool.getItem() instanceof StencilGunItem && StencilGunItem.selectStencil(player, tool, value);
            case HAMMER_DYE -> tool.getItem() instanceof StencilGunItem && StencilGunItem.selectDye(player, tool, value);
            case HAMMER_OPEN -> tool.getItem() instanceof StencilGunItem && StencilGunItem.openLoader(player);
        };
    }
}
