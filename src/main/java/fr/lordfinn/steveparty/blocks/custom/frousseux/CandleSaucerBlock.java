package fr.lordfinn.steveparty.blocks.custom.frousseux;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * The Candle Saucer, a little gold tray, placed on its own (its model: models/block/candle_saucer.json, square to the
 * world). A Frousseux candle holder used on it stands on it: the block becomes the candle holder on its saucer (the
 * same Frousseux, facing whoever put it there), the item used up (not in creative). Broken, the candle holder on its
 * saucer drops as one item keeping both (FrousseuxCandleHolderBlock): nothing lost, nothing doubled.
 */
public class CandleSaucerBlock extends Block {
    public static final MapCodec<CandleSaucerBlock> CODEC = createCodec(CandleSaucerBlock::new);
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 2, 16);

    public CandleSaucerBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    /** A candle holder (not already on a saucer) set on it. */
    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
                                             Hand hand, BlockHitResult hit) {
        if (!stack.isOf(ModBlocks.FROUSSEUX_CANDLE_HOLDER.asItem()) || FrousseuxCandleHolderBlock.isOnSaucer(stack)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!world.isClient) {
            NbtCompound kept = FrousseuxCandleHolderBlock.keptIn(stack);
            BlockState onSaucer = ModBlocks.FROUSSEUX_CANDLE_HOLDER.getDefaultState()
                    .with(FrousseuxCandleHolderBlock.ROTATION, FrousseuxCandleHolderBlock.rotationFacing(player.getYaw()))
                    .with(FrousseuxCandleHolderBlock.COLOR, FrousseuxCandleHolderBlockEntity.colorOf(kept))
                    .with(FrousseuxCandleHolderBlock.FLAME, FrousseuxCandleHolderBlockEntity.flameOf(kept).ordinal())
                    .with(FrousseuxCandleHolderBlock.SAUCER, true);
            world.setBlockState(pos, onSaucer, Block.NOTIFY_ALL);
            if (!kept.isEmpty() && world.getBlockEntity(pos) instanceof FrousseuxCandleHolderBlockEntity holder) {
                holder.setFrousseux(kept);
            }
            world.playSound(null, pos, SoundEvents.BLOCK_CANDLE_PLACE, SoundCategory.BLOCKS, 1.0f, 1.0f);
            stack.decrementUnlessCreative(1, player);
        }
        return ItemActionResult.success(world.isClient);
    }
}
