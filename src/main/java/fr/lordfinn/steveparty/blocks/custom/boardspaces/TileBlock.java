package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The « Advanced Tile » (« Tuile avancée »): a board space holding 16 cartridges, the redstone power picks the
 * active one; gold border. Registry id {@code tile} (kept for existing worlds).
 */
public class TileBlock extends ATileBlock {
    public static final MapCodec<TileBlock> CODEC = Block.createCodec(TileBlock::new);

    public TileBlock(Settings settings) {
        super(settings, 16);
    }

    @Override
    protected MapCodec<? extends TileBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected String tooltipKey() {
        return "advanced";
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TileBlockEntity(pos, state);
    }
}
