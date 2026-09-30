package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.StopBoardSpaceBehavior;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import net.minecraft.item.ItemStack;

import java.util.List;

/** Stop: a forced landing (see StopBoardSpaceBehavior). Its menu: what it does, and its tile's colour. */
public class StopCartridgeItem extends CartridgeItem {
    private static final List<CartridgeModule> MODULES = List.of(
            description("board_space_behavior_stop", 3), colorModule(StopBoardSpaceBehavior.COLOR));

    public StopCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.BOARD_SPACE_STOP;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int menuColor(ItemStack stack) {
        return stack.getOrDefault(ModComponents.COLOR, StopBoardSpaceBehavior.COLOR) & 0xFFFFFF;
    }
}
