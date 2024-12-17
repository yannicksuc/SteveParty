package fr.lordfinn.steveparty.blocks.tiles;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.tiles.behaviors.ATileBehavior;
import fr.lordfinn.steveparty.blocks.tiles.behaviors.TileBehaviorFactory;
import fr.lordfinn.steveparty.items.TileOpener;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static net.minecraft.util.ActionResult.PASS;
import static net.minecraft.util.ActionResult.SUCCESS;

public class Tile extends HorizontalFacingBlock implements BlockEntityProvider {
    public static final MapCodec<Tile> CODEC = Block.createCodec(Tile::new);
    public static final EnumProperty<TileType> TILE_TYPE = EnumProperty.of("tile_type", TileType.class);

    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0.0, 0, 16.0, 2.0, 16.0);



    public Tile(Settings settings) {
        super(settings.nonOpaque());
        setDefaultState(getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Tile> getCodec() {
        return CODEC;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(TILE_TYPE, Properties.HORIZONTAL_FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return Objects.requireNonNull(super.getPlacementState(ctx))
                .with(Properties.HORIZONTAL_FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        return TileBehaviorFactory.get(state.get(TILE_TYPE)).onUse(state, world, pos, player, hit);
    }

    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return SUCCESS;
        ItemStack mainHandStack = player.getMainHandStack();
        ItemStack offHandStack = player.getOffHandStack();
        if (mainHandStack.isEmpty() && offHandStack.isEmpty()) return onUse(state, world, pos, player, hit);
        if (!(mainHandStack.getItem() instanceof TileOpener) && !(offHandStack.getItem() instanceof TileOpener)) {
            return TileBehaviorFactory.get(state.get(TILE_TYPE)).onUseWithItem(stack, state, world, pos, player, hit);
        }
        NamedScreenHandlerFactory screenHandlerFactory = state.createScreenHandlerFactory(world, pos);
        if (screenHandlerFactory != null) {
            // With this call the server will request the client to open the appropriate Screenhandler
            world.playSound(null, pos, ModSounds.OPEN_TILE_GUI_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
            player.openHandledScreen(screenHandlerFactory);
            return SUCCESS;
        }
        return PASS;
    }

    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return ScreenHandler.calculateComparatorOutput(world.getBlockEntity(pos));
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    // This method will drop all items onto the ground when the block is broken
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() != newState.getBlock()) {
            TileEntity tileEntity = getTileEntity(world, pos);
            if (tileEntity != null) {
                ItemScatterer.spawn(world, pos, tileEntity.getInventory());
                world.updateComparators(pos,this);
                tileEntity.hideDestinations();
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    // Override to create a new TileEntity instance for this block
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntity(pos, state);
    }

    @Override
    public NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, net.minecraft.world.World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof NamedScreenHandlerFactory) {
            return (NamedScreenHandlerFactory) blockEntity;
        }
        return null;
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, @Nullable WireOrientation wireOrientation, boolean notify) {
        if (world.getBlockEntity(pos) instanceof TileEntity tileEntity) {
            tileEntity.updateTileSkin();
        }
        super.neighborUpdate(state, world, pos, sourceBlock, wireOrientation, notify);
    }

    public static TileEntity getTileEntity(World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof TileEntity tileEntity)
            return tileEntity;
        return null;
    }

    public ItemStack getBehaviorItemstack(TileEntity tileEntity) {
        return tileEntity.getActiveTileBehaviorItemStack();
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        super.onSteppedOn(world, pos, state, entity);
    }

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (!world.isClient) {
            TileBehaviorFactory.get(state.get(TILE_TYPE)).onSteppedOn(world, pos, state, entity);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return TickableBlockEntity.getTicker(world);
    }
}
