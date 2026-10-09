package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.items.custom.cartridges.KeyGateCartridgeItem;
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
}
