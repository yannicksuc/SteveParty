package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.BoardSpaceBehaviorFactory;
import fr.lordfinn.steveparty.screen_handlers.custom.TileScreenHandler;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.util.ActionResult.SUCCESS;

public abstract class ABoardSpaceBlock extends CartridgeContainer {
    public static final EnumProperty<BoardSpaceType> TILE_TYPE = EnumProperty.of("tile_type", BoardSpaceType.class);

    public ABoardSpaceBlock(Settings settings) {
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
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    // Override to create a new TileEntity instance for this block
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BoardSpaceBlockEntity(pos, state);
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, @Nullable WireOrientation wireOrientation, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, wireOrientation, notify);

        if (world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity tileEntity) {
            tileEntity.updateBoardSpaceType();
        }
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

    @Override
    public NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, net.minecraft.world.World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return new SimpleNamedScreenHandlerFactory((syncId, inventory, player) ->
                new TileScreenHandler(syncId, inventory, (BoardSpaceBlockEntity) blockEntity), Text.empty());
    }

    @Override
    protected ActionResult.@Nullable Success openScreen(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        if (world.isClient) return null;
        BoardSpaceBlockEntity blockEntity = (BoardSpaceBlockEntity) world.getBlockEntity(pos);
        world.playSound(null, pos, ModSounds.OPEN_TILE_GUI_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
        player.openHandledScreen(blockEntity);
        return SUCCESS;
    }
}