package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class CheckPointBlockEntity extends BoardSpaceBlockEntity {
    /** Its crystal's blue, undyed. */
    public static final int BASE_COLOR = 0x41B7CC;

    public CheckPointBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state, ModBlockEntities.CHECK_POINT_ENTITY, 16);
    }

    @Override
    public int baseColor() {
        return BASE_COLOR;
    }
}
