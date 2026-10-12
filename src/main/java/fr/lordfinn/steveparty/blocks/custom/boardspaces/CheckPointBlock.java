package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.components.ModComponents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.TileGlow.GLOWING;

public class CheckPointBlock extends ABoardSpaceBlock {
    public static final MapCodec<CheckPointBlock> CODEC = Block.createCodec(CheckPointBlock::new);
    public CheckPointBlock(Settings settings) {
        super(settings.sounds(BlockSoundGroup.GLASS).nonOpaque().luminance(state -> 7), 16);
        setDefaultState(getDefaultState().with(GLOWING, false).with(TileDye.DYED, false));
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    /**
     * Takes glow ink, as a tile does (see TileGlow); shows its cartridge's dye with a tinted model (see TileDye: the
     * dye stays on the cartridge).
     */
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(GLOWING, TileDye.DYED);
    }

    /** Placed from an item painted with glow ink (see {@link #getDroppedStacks}): glowing again. */
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState state = super.getPlacementState(ctx);
        return state == null ? null : state.with(GLOWING, ctx.getStack().contains(ModComponents.GLOWING_TILE));
    }

    /** A glowing check point isn't baked in the chunk (lit as the world is): its renderer draws it at full light. */
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return state.get(GLOWING) ? BlockRenderType.INVISIBLE : super.getRenderType(state);
    }

    /** A broken check point painted with glow ink drops an item that keeps it (its cartridges spill out as before). */
    @Override
    protected List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        List<ItemStack> drops = super.getDroppedStacks(state, builder);
        if (state.get(GLOWING)) {
            for (ItemStack drop : drops) if (drop.isOf(asItem())) drop.set(ModComponents.GLOWING_TILE, Unit.INSTANCE);
        }
        return drops;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.cuboid(0.34375, 0.15625, 0.34375, 0.65625, 0.46875, 0.65625);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty(); // No collision shape
    }

    @Override
    protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CheckPointBlockEntity(pos, state);
    }
}
