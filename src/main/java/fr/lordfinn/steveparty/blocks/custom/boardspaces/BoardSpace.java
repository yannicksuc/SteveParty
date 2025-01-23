package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.BoardSpaceBehaviorFactory;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;

import static fr.lordfinn.steveparty.events.TileUpdatedEvent.EVENT;

public abstract class BoardSpace extends CartridgeContainer {
    public static final EnumProperty<BoardSpaceType> TILE_TYPE = EnumProperty.of("tile_type", BoardSpaceType.class);

    public BoardSpace(Settings settings) {
        super(settings.nonOpaque());
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(TILE_TYPE);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        return BoardSpaceBehaviorFactory.get(state.get(TILE_TYPE)).onUse(state, world, pos, player, hit);
    }

    @Override
    protected ActionResult onUseWithoutCartridgeContainerOpener(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        return BoardSpaceBehaviorFactory.get(state.get(TILE_TYPE)).onUseWithItem(stack, state, world, pos, player, hit);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    // This method will drop all items onto the ground when the block is broken
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        BoardSpaceBlockEntity tileEntity = getBoardSpaceEntity(world, pos);
        if (tileEntity == null) return;
        if (state.getBlock() != newState.getBlock()) {
            ItemScatterer.spawn(world, pos, tileEntity);
            world.updateComparators(pos,this);
            tileEntity.hideDestinations();
        } else {
            tileEntity.getTokensOnMe().forEach(token -> EVENT.invoker().onTileUpdated(token, tileEntity));
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    // Override to create a new TileEntity instance for this block
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BoardSpaceBlockEntity(pos, state);
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, @Nullable WireOrientation wireOrientation, boolean notify) {
        if (world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity tileEntity) {
            tileEntity.updateTileSkin();
        }
        super.neighborUpdate(state, world, pos, sourceBlock, wireOrientation, notify);
    }

    public static BoardSpaceBlockEntity getBoardSpaceEntity(World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof BoardSpaceBlockEntity tileEntity)
            return tileEntity;
        return null;
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        super.onSteppedOn(world, pos, state, entity);
    }

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (!world.isClient) {
            BoardSpaceBehaviorFactory.get(state.get(TILE_TYPE)).onSteppedOn(world, pos, state, entity);
        }
    }
}
