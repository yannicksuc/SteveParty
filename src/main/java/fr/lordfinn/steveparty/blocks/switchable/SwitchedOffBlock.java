package fr.lordfinn.steveparty.blocks.switchable;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A switchable block while it is switched off: invisible, no collision, only a tiny selection box in the middle.
 * Mining it takes as long as the original block and gives exactly the original drops (none without the right tool),
 * so switching never creates or loses items.
 */
public class SwitchedOffBlock extends BlockWithEntity {
    public static final MapCodec<SwitchedOffBlock> CODEC = createCodec(SwitchedOffBlock::new);
    private static final VoxelShape SMALL_CENTER_SHAPE = Block.createCuboidShape(7.0, 7.0, 7.0, 9.0, 9.0, 9.0);

    public SwitchedOffBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SwitchedOffBlockEntity(pos, state);
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SMALL_CENTER_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty();
    }

    private static BlockState storedState(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof SwitchedOffBlockEntity switchedOff ? switchedOff.getStoredState() : null;
    }

    @Override
    protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        BlockState stored = storedState(world.getBlockEntity(pos));
        return stored == null || stored.isAir() ? super.calcBlockBreakingDelta(state, player, world, pos)
                : stored.calcBlockBreakingDelta(player, world, pos);
    }

    // Exactly the drops of the original block: nothing without the tool it requires
    @Override
    protected List<ItemStack> getDroppedStacks(BlockState state, LootWorldContext.Builder builder) {
        BlockState stored = storedState(builder.getOptional(LootContextParameters.BLOCK_ENTITY));
        if (stored == null || stored.isAir()) return List.of();
        if (stored.isToolRequired()) {
            ItemStack tool = builder.getOptional(LootContextParameters.TOOL);
            if (tool == null || !tool.isSuitableFor(stored)) return List.of();
        }
        return stored.getDroppedStacks(builder);
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
        BlockState stored = storedState(world.getBlockEntity(pos));
        return stored == null || stored.isAir() ? ItemStack.EMPTY : stored.getBlock().getPickStack(world, pos, stored);
    }
}
