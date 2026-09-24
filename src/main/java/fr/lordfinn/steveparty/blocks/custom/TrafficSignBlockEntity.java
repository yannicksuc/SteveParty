package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.signs.StencilCanvasBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/** Traffic sign block entity: a stencil canvas under its original id ({@code steveparty:traffic_sign}). */
public class TrafficSignBlockEntity extends StencilCanvasBlockEntity {
    public TrafficSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRAFFIC_SIGN_ENTITY, pos, state);
    }

    public int getRotation() {
        return getCachedState().get(TrafficSignBlock.ROTATION);
    }
}
