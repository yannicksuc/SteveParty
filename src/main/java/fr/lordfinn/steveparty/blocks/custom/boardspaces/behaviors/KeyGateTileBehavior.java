package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import net.minecraft.util.math.Direction;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.BoardRuleCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.KeyGateCartridgeItem;
import net.minecraft.server.world.ServerWorld;
import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.service.KeyGates;
import net.minecraft.item.ItemStack;

/**
 * The role of a Key gate Cartridge: gates on some exits of its space, opened by a Gate Key (see {@link KeyGates}, which
 * TokenMovementService asks when a token leaves the space). Stopping on the space itself is a plain landing; a Router
 * reading it pulses 15 while the gate is open, the Stop level while it is closed.
 */
public class KeyGateTileBehavior extends ABoardSpaceBehavior {
    public KeyGateTileBehavior() {
        super(BoardSpaceType.TILE_KEY_GATE);
    }

    @Override
    public int comparatorLevel(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return KeyGates.isOpen(boardSpaceEntity, null) ? 15 : BoardSpaceRedstoneRouterBlockEntity.LEVEL_STOP;
    }

    /** Open or closed, and the ways it locks. */
    @Override
    public void describe(ServerWorld world, BoardSpaceBlockEntity space, ItemStack stack, TileInfo.Builder info) {
        if (!(stack.getItem() instanceof KeyGateCartridgeItem)) return;
        boolean open = BoardRuleCartridgeItem.state(stack, KeyGateCartridgeItem.OPENED, Integer.MIN_VALUE) != Integer.MIN_VALUE;
        info.line(new ItemStack(ModItems.GATE_KEY), TileInfo.line("key_gate",
                open ? TileInfo.good(TileInfo.line("key_gate.open")) : TileInfo.bad(TileInfo.line("key_gate.closed"))));
        MutableText sides = Text.empty();
        for (Direction side : KeyGateCartridgeItem.SIDES) {
            if (!KeyGateCartridgeItem.isLocked(stack, side)) continue;
            if (!sides.getSiblings().isEmpty()) sides.append(", ");
            sides.append(Text.translatable("gui.steveparty.cartridge_menu.key_gate.side." + side.asString() + ".tooltip"));
        }
        if (!open && !sides.getSiblings().isEmpty()) info.line(TileInfo.line("key_gate.locks", TileInfo.value(sides)));
    }
}
