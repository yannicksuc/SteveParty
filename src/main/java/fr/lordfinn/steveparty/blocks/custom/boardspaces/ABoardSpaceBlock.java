package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.screen_handlers.custom.BoardSpaceScreenHandler;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemActionResult;
import fr.lordfinn.steveparty.blocks.ItemResults;
import fr.lordfinn.steveparty.board.WrenchActions;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.util.ActionResult.PASS;
import static net.minecraft.util.ActionResult.SUCCESS;

public abstract class ABoardSpaceBlock extends CartridgeContainer {
    public static final EnumProperty<BoardSpaceType> TILE_TYPE = EnumProperty.of("tile_type", BoardSpaceType.class);

    public ABoardSpaceBlock(Settings settings, int numberOfCartridges) {
        super(settings.nonOpaque(), numberOfCartridges);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(TILE_TYPE);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        return state.get(TILE_TYPE).behavior().onUse(state, world, pos, player, hit);
    }

    @Override
    protected ItemActionResult onUseWithoutCartridgeContainerOpener(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        // Stencil + dye, the Stencil Hammer, a wet sponge: a new look for the tile (client-safe: decided the same way on both sides)
        ActionResult stamped = TileStamping.onUseWithItem(state, world, pos, player, hand, hit);
        if (stamped != null) return ItemResults.of(stamped);
        // Client prediction: every behavior only handles dyes (and returns PASS otherwise)
        if (world.isClient) return ItemResults.of(stack != null && stack.getItem() instanceof DyeItem ? SUCCESS : PASS);
        return ItemResults.of(state.get(TILE_TYPE).behavior().onUseWithItem(stack, state, world, pos, player, hit));
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    /** Broken for its item: the cartridges stay in the tile's item (the others spill out, see the parent). */
    @Override
    protected boolean keepsContents(CartridgeContainerBlockEntity blockEntity) {
        return blockEntity instanceof BoardSpaceBlockEntity tile && tile.keepsContents();
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        BoardSpaceBlockEntity tileEntity = getBoardSpaceEntity(world, pos);
        if (tileEntity == null) return;
        if (state.getBlock() != newState.getBlock()) {
            world.updateComparators(pos,this);
            tileEntity.hideDestinations();
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity tileEntity) {
            tileEntity.onPlaced();
            // Placed with the Tile Linker Brush in the off hand: linked from its anchor
            WrenchActions.onBoardSpacePlaced(world, pos, placer, itemStack);
        }
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);

        if (world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity tileEntity) {
            tileEntity.onNeighborUpdate();
        }
    }

    /**
     * Only board spaces whose role animates something tick (the start tile animates its bound token, a Common pot
     * looks after its nest and its Pie), server side.
     * The ticker is re-evaluated by the chunk whenever the block state (and so the tile type) changes.
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient || state.get(TILE_TYPE) != BoardSpaceType.TILE_START && state.get(TILE_TYPE) != BoardSpaceType.TILE_POT) return null;
        return TickableBlockEntity.getTicker(world);
    }
    /**
     * Whether reaching this block consumes one step of a token's movement.
     * Tiles and advanced tiles count; check points deliberately do not (they are waypoints).
     */
    public static boolean countsAsStep(Block block) {
        return block instanceof ATileBlock;
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
            state.get(TILE_TYPE).behavior().onSteppedOn(world, pos, state, entity);
        }
    }

    @Override
    public NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return new SimpleNamedScreenHandlerFactory((syncId, inventory, player) ->
                new BoardSpaceScreenHandler(syncId, inventory, (BoardSpaceBlockEntity) blockEntity), Text.empty());
    }

    @Override
    protected @Nullable ActionResult openScreen(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        if (world.isClient) return null;
        BoardSpaceBlockEntity blockEntity = (BoardSpaceBlockEntity) world.getBlockEntity(pos);
        world.playSound(null, pos, ModSounds.OPEN_TILE_GUI_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
        player.openHandledScreen(blockEntity);
        return SUCCESS;
    }
}