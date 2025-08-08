package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.components.CarpetColorComponent;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.screen_handlers.custom.TradingStallScreenHandler;
import fr.lordfinn.steveparty.utils.VoxelShapeUtils;
import fr.lordfinn.steveparty.utils.WoolColorsUtils;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

import java.util.HashMap;
import java.util.Map;

import static fr.lordfinn.steveparty.components.ModComponents.CARPET_COLORS;
import static net.minecraft.component.DataComponentTypes.BLOCK_ENTITY_DATA;

public class TradingStallBlock extends HorizontalFacingBlock implements BlockEntityProvider {
    public static final Map<Direction, VoxelShape> SHAPES = new HashMap<>();
    private static final VoxelShape SHAPE = VoxelShapes.union(
            VoxelShapes.cuboid(0, 0, 0.125, 1, 0.875, 1),
            VoxelShapes.cuboid(0, 0.875, 0, 0.25, 1, 1),
            VoxelShapes.cuboid(0.25, 0.875, 0, 0.5, 1, 1),
            VoxelShapes.cuboid(0.5, 0.875, 0, 0.75, 1, 1),
            VoxelShapes.cuboid(0.75, 0.875, 0, 1, 1, 1),
            VoxelShapes.cuboid(0, 0.6875, 0, 0.25, 0.875, 0),
            VoxelShapes.cuboid(0.25, 0.6875, 0, 0.5, 0.875, 0),
            VoxelShapes.cuboid(0.5, 0.6875, 0, 0.75, 0.875, 0),
            VoxelShapes.cuboid(0.75, 0.6875, 0, 1, 0.875, 0)
            );
    public static final IntProperty COLOR1 = IntProperty.of("color1", 0, 15);
    public static final IntProperty COLOR2 = IntProperty.of("color2", 0, 15);


    public TradingStallBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH).with(COLOR1, 0).with(COLOR2, 0));
        setupShapes();
    }
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLOR1, COLOR2);
    }

    private void setupShapes() {
        Box[] boxes = VoxelShapeUtils.shapeToBoxes(SHAPE);
        SHAPES.put(Direction.SOUTH, SHAPE);
        SHAPES.put(Direction.EAST, VoxelShapeUtils.shape(VoxelShapeUtils.rotate(90, boxes)));
        SHAPES.put(Direction.NORTH, VoxelShapeUtils.shape(VoxelShapeUtils.rotate(180, boxes)));
        SHAPES.put(Direction.WEST, VoxelShapeUtils.shape(VoxelShapeUtils.rotate(270, boxes)));
    }

    @Override
    protected boolean isShapeFullCube(BlockState state, BlockView world, BlockPos pos) {
        return false;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES.get(state.get(FACING));
    }

    @Override
    protected MapCodec<TradingStallBlock> getCodec() {
        return createCodec(TradingStallBlock::new);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);

            if (blockEntity instanceof TradingStallBlockEntity) {
                player.openHandledScreen(new ExtendedScreenHandlerFactory() {
                    @Override
                    public Object getScreenOpeningData(ServerPlayerEntity serverPlayerEntity) {
                        return new BlockPosPayload(pos);
                    }
                    @Override
                    public Text getDisplayName() {
                        return Text.translatable("block.steveparty.trading_stall");
                    }

                    @Override
                    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
                        return new TradingStallScreenHandler(syncId, inv, pos); // You'll need this constructor
                    }
                });
            }
        }

        return ActionResult.SUCCESS;
    }

    @Override
    protected NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof NamedScreenHandlerFactory ? (NamedScreenHandlerFactory) world.getBlockEntity(pos) : null;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        ItemStack stack = context.getPlayer() != null ? context.getPlayer().getStackInHand(context.getHand()) : context.getStack();

        NbtComponent blockEntityData = stack.get(BLOCK_ENTITY_DATA);

        if (blockEntityData != null && blockEntityData.copyNbt().contains("color1") && blockEntityData.copyNbt().contains("color2")) {
            int color1 = blockEntityData.copyNbt().getInt("color1");
            int color2 = blockEntityData.copyNbt().getInt("color2");
            return this.getDefaultState()
                    .with(FACING, context.getHorizontalPlayerFacing().getOpposite())
                    .with(COLOR1, color1)
                    .with(COLOR2, color2);
        }

        // Extract colors from CarpetColorComponent in the item
        CarpetColorComponent carpetColors = stack.get(CARPET_COLORS);
        if (carpetColors == null) {
            return this.getDefaultState()
                    .with(FACING, context.getHorizontalPlayerFacing().getOpposite());
        }

        int color1 = carpetColors.color1().ordinal();
        int color2 = carpetColors.color2().ordinal();

        return this.getDefaultState()
                .with(FACING, context.getHorizontalPlayerFacing().getOpposite())
                .with(COLOR1, color1)
                .with(COLOR2, color2);
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
        ItemStack stack = super.getPickStack(world, pos, state);
        CarpetColorComponent carpetColors = new CarpetColorComponent(
                WoolColorsUtils.getDyeColorFromIndex(state.get(COLOR1)),
                WoolColorsUtils.getDyeColorFromIndex(state.get(COLOR2)));
        Steveparty.LOGGER.info("Picked up Trading Stall carpetColors: {}", carpetColors);
        stack.set(CARPET_COLORS, carpetColors);
        Steveparty.LOGGER.info("CarpetColorComponent on stack: {}", stack.get(CARPET_COLORS));
        return stack;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof TradingStallBlockEntity entity) {
                Inventory inv = entity.getInventory();
                for (int i = 0; i < inv.size(); i++) {
                    ItemStack stack = inv.getStack(i);
                    if (!stack.isEmpty()) {
                        ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), stack);
                    }
                }
                ItemStack tradingStallStack = new ItemStack(this);
                CarpetColorComponent carpetColors = new CarpetColorComponent(
                        WoolColorsUtils.getDyeColorFromIndex(state.get(COLOR1)),
                        WoolColorsUtils.getDyeColorFromIndex(state.get(COLOR2)));
                tradingStallStack.set(CARPET_COLORS, carpetColors);
                ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), tradingStallStack);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TradingStallBlockEntity(pos, state);
    }

    @Override
    public int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    public int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return 0;
    }
}
