package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.blocks.custom.signs.AbstractStencilSignBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.WoodType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

/**
 * Wooden traffic sign of a fixed wood (the 10 original signs, kept for the worlds that have them). New signs are
 * {@link fr.lordfinn.steveparty.blocks.custom.signs.MaterialTrafficSignBlock}, made of any planks.
 * Stencils, dyes, glow ink and sponges: see {@link fr.lordfinn.steveparty.blocks.custom.signs.StencilInteractions}.
 */
public class TrafficSignBlock extends AbstractStencilSignBlock {
    public static final MapCodec<TrafficSignBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance
            .group(WoodType.CODEC.fieldOf("wood_type").forGetter(block -> block.type), createSettingsCodec())
            .apply(instance, TrafficSignBlock::new));
    protected static final VoxelShape SHAPE = VoxelShapes.cuboid(0.125, 0, 0.125, 0.875, 0.9375, 0.875);

    protected final WoodType type;

    public TrafficSignBlock(WoodType woodType, Settings settings) {
        super(settings);
        this.type = woodType;
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TrafficSignBlockEntity(pos, state);
    }

    public WoodType getWoodType() {
        return this.type;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    // Drops are handled by the loot tables (data/steveparty/loot_table/blocks/*traffic_sign.json)
}
