package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.List;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.TileBlock.ROTATION_8;

/**
 * The « Tile » (« Tuile »): a board space holding a single cartridge, with a white/grey border. Registry id
 * {@code simple_tile} (kept for existing worlds).
 */
public class SimpleTileBlock extends ABoardSpaceBlock {
    public static final MapCodec<SimpleTileBlock> CODEC = Block.createCodec(SimpleTileBlock::new);

    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0.0, 0, 16.0, 2.0, 16.0);

    public SimpleTileBlock(Settings settings) {
        super(settings.nonOpaque(), 1);
        // Same 8-direction rotation as TileBlock (the blockstate file and the tile renderer rely on it)
        setDefaultState(getDefaultState().with(ROTATION_8, 0));
    }

    @Override
    protected MapCodec<SimpleTileBlock> getCodec() {
        return CODEC;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(ROTATION_8);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState()
                .with(ROTATION_8, TileBlock.rotation8FromYaw(ctx.getPlayerYaw()));
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return TileBlock.rotate8(state, rotation);
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return TileBlock.mirror8(state, mirror);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        super.appendTooltip(stack, context, tooltip, options);
        tooltip.add(Text.translatable("tooltip.steveparty.tile.simple").formatted(Formatting.GRAY));
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SimpleTileBlockEntity(pos, state);
    }
}
