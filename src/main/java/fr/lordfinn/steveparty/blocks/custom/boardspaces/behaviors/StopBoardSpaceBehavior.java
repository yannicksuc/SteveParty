package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import net.minecraft.item.ItemStack;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class StopBoardSpaceBehavior extends ABoardSpaceBehavior {
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
}
