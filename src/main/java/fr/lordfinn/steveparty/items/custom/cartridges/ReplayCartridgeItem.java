package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ReplayBoardSpaceBehavior;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import net.minecraft.item.ItemStack;

import java.util.List;

/** Rejouer / Roll Again: the tile gives the token's owner another turn (see ReplayBoardSpaceBehavior). */
public class ReplayCartridgeItem extends CartridgeItem {
    private static final List<CartridgeModule> MODULES = List.of(
            description("replay_cartridge", 3), colorModule(ReplayBoardSpaceBehavior.COLOR));

    public ReplayCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_REPLAY;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int menuColor(ItemStack stack) {
        return stack.getOrDefault(ModComponents.COLOR, ReplayBoardSpaceBehavior.COLOR) & 0xFFFFFF;
    }
}
