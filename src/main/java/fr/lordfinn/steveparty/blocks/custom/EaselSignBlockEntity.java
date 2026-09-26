package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.signs.StencilCanvasBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/** Easel sign block entity: a stencil canvas under its own id ({@code steveparty:easel_sign}, was {@code steveparty:traffic_sign}). */
public class EaselSignBlockEntity extends StencilCanvasBlockEntity {
    public EaselSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EASEL_SIGN_ENTITY, pos, state);
    }

    public int getRotation() {
        return getCachedState().get(EaselSignBlock.ROTATION);
    }
}
