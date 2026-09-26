package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The « Advanced Tile » (« Tuile avancée »): a board space holding 16 cartridges, the redstone power picks the
 * active one; gold border. Registry id {@code advanced_tile} (it was {@code tile}: see TileMigration).
 */
public class AdvancedTileBlock extends ATileBlock {
    public static final MapCodec<AdvancedTileBlock> CODEC = Block.createCodec(AdvancedTileBlock::new);

    public AdvancedTileBlock(Settings settings) {
        super(settings, 16);
    }

    @Override
    protected MapCodec<? extends AdvancedTileBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected String tooltipKey() {
        return "advanced";
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new AdvancedTileBlockEntity(pos, state);
    }
}
