package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The « Tile » (« Tuile »): a board space holding a single cartridge, with a white/grey border. Registry id
 * {@code tile} (it was {@code simple_tile}: see TileMigration).
 */
public class TileBlock extends ATileBlock {
    public static final MapCodec<TileBlock> CODEC = Block.createCodec(TileBlock::new);

    public TileBlock(Settings settings) {
        super(settings, 1);
    }

    @Override
    protected MapCodec<TileBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected String tooltipKey() {
        return "simple";
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TileBlockEntity(pos, state);
    }
}
