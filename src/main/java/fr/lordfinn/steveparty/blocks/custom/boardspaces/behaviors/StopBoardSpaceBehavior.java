package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.components.ModComponents;
import net.minecraft.item.ItemStack;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class StopBoardSpaceBehavior extends ABoardSpaceBehavior {
    /** The face of a Stop tile (anthracite, the only dark role colour), unless its cartridge is dyed. */
    public static final int COLOR = 0x454B5A;

    public StopBoardSpaceBehavior() {
        super(BoardSpaceType.BOARD_SPACE_STOP);
    }

    @Override
    public boolean needToStop(World world, BlockPos pos) {
        return true;
    }

    @Override
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return TileFeedback.Landing.STOP;
    }

    /** A cartridge without colour (an old one, or cleared) still shows the Stop anthracite. */
    @Override
    public void updateBoardSpaceColor(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
        if (!stack.contains(ModComponents.COLOR)) setColor(boardSpaceBlockEntity, COLOR);
    }
}
