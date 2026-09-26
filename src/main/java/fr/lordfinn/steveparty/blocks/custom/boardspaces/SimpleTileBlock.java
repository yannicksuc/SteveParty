package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The « Tile » (« Tuile »): a board space holding a single cartridge, with a white/grey border. Registry id
 * {@code simple_tile} (kept for existing worlds).
 */
public class SimpleTileBlock extends ATileBlock {
    public static final MapCodec<SimpleTileBlock> CODEC = Block.createCodec(SimpleTileBlock::new);

    public SimpleTileBlock(Settings settings) {
        super(settings, 1);
    }

    @Override
    protected MapCodec<SimpleTileBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected String tooltipKey() {
        return "simple";
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SimpleTileBlockEntity(pos, state);
    }
}
