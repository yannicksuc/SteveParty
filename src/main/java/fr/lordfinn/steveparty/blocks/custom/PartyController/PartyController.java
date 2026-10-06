package fr.lordfinn.steveparty.blocks.custom.PartyController;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.utils.MessageUtils;
import fr.lordfinn.steveparty.utils.VoxelShapeUtils;
import net.minecraft.block.*;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemActionResult;
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
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.util.Color;

import java.util.HashMap;
import java.util.Map;

public class PartyController extends HorizontalFacingBlock implements BlockEntityProvider {
    public static final BooleanProperty POWERED = Properties.POWERED;
    public static final BooleanProperty CATALOGUED = BooleanProperty.of("catalogued");

    public static final MapCodec<PartyController> CODEC = Block.createCodec(PartyController::new);

    public static final Map<Direction, VoxelShape> SHAPES = new HashMap<>();
    public static final VoxelShape SHAPE = VoxelShapes.union(
            VoxelShapes.cuboid(0, 0, 0, 1, 0.125, 1),
            VoxelShapes.cuboid(0.25, 0.125, 0.25, 0.75, 0.5, 0.75),
            VoxelShapes.cuboid(0.25, 0.6875, 0.125, 0.875, 1.0625, 0.3125),
            VoxelShapes.cuboid(0.125, 0.8125, 0.25, 0.25, 1.4375, 0.375),
            VoxelShapes.cuboid(0.0625, 0.1875, 0.6875, 0.9375, 0.75, 0.9375),
            VoxelShapes.cuboid(0.0625, 0.3125, 0.4375, 0.9375, 0.875, 0.6875),
            VoxelShapes.cuboid(0.0625, 0.4375, 0.1875, 0.9375, 1, 0.4375),
            VoxelShapes.cuboid(0.1875, 0.6875, 0.375, 0.8125, 0.8359375, 0.9796875)
    );

    public PartyController(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(POWERED, false)
                .with(CATALOGUED, false)
                .with(FACING, Direction.NORTH));
        setupShapes();
    }

    private void setupShapes() {
        Box[] boxes = VoxelShapeUtils.shapeToBoxes(SHAPE);
        SHAPES.put(Direction.SOUTH, SHAPE);
        SHAPES.put(Direction.EAST, VoxelShapeUtils.shape(VoxelShapeUtils.rotate(90, boxes)));
        SHAPES.put(Direction.NORTH, VoxelShapeUtils.shape(VoxelShapeUtils.rotate(180, boxes)));
        SHAPES.put(Direction.WEST, VoxelShapeUtils.shape(VoxelShapeUtils.rotate(270, boxes)));
    }

    @Override
    protected MapCodec<PartyController> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, CATALOGUED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        boolean isCatalogued = false;
        BlockEntity entity = context.getWorld().getBlockEntity(context.getBlockPos());
        if (entity instanceof PartyControllerEntity partyEntity) {
            isCatalogued = !partyEntity.catalogue.isEmpty();
        }
        return this.getDefaultState()
                .with(FACING, context.getHorizontalPlayerFacing().getOpposite())
                .with(CATALOGUED, isCatalogued);
    }

    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.PASS;
        // Taking the catalogue out takes the right to edit the controller (not a party's adventure players)
        if (player.isSneaking()) {
            if (!(world.getBlockEntity(pos) instanceof PartyControllerEntity controller) || !controller.canEdit(player))
                return ActionResult.SUCCESS;
            if (world.isReceivingRedstonePower(pos)) {
                sendCatalogueLocked(player);
                return ActionResult.PASS;
            }
            ActionResult success = toggleCatalogue(world, pos, ItemStack.EMPTY, state);
            if (success != null) return success;
        } else if (world.getBlockEntity(pos) instanceof PartyControllerEntity entity) {
            // The dashboard: the party, its players, its mini-games, its settings (and following it)
            player.openHandledScreen(entity);
        }

        return ActionResult.SUCCESS;
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient || hand.equals(Hand.OFF_HAND)) return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        // The Wrench checks the board (see WrenchActions)
        if (stack.getItem() instanceof fr.lordfinn.steveparty.items.custom.WrenchItem) return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        // Swapping the catalogue takes the same as taking it out: the right to edit, and no redstone lock
        if (stack.getItem() instanceof MiniGamesCatalogueItem
                && world.getBlockEntity(pos) instanceof PartyControllerEntity controller && controller.canEdit(player)) {
            if (controller.isCatalogueLocked() && !controller.catalogue.isEmpty()) {
                sendCatalogueLocked(player);
                return ItemActionResult.SUCCESS;
            }
            ActionResult success = toggleCatalogue(world, pos, stack.copyAndEmpty(), state, player);
            if (success != null) return ItemActionResult.SUCCESS;
        }
        return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
    }

    private static void sendCatalogueLocked(PlayerEntity player) {
        MessageUtils.sendToPlayer((ServerPlayerEntity) player, Text.translatable("message.steveparty.party_controller.catalogue_locked").withColor(Color.RED.getColor()), MessageUtils.MessageType.CHAT);
    }

    private static @Nullable ActionResult toggleCatalogue(World world, BlockPos pos, ItemStack empty, BlockState state) {
        return toggleCatalogue(world, pos, empty, state, null);
    }

    private static @Nullable ActionResult toggleCatalogue(World world, BlockPos pos, ItemStack empty, BlockState state, @Nullable PlayerEntity player) {
        PartyControllerEntity entity = (PartyControllerEntity) world.getBlockEntity(pos);
        if (entity != null) {
            boolean isCatalogued = entity.setCatalogue(empty, player);
            world.setBlockState(pos, state.with(PartyController.CATALOGUED, isCatalogued), Block.NOTIFY_ALL);
            return ActionResult.SUCCESS;
        }
        return null;
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (!world.isClient) {
            if (world.isReceivingRedstonePower(pos) && !state.get(POWERED)) {
                PartyControllerEntity entity = (PartyControllerEntity) world.getBlockEntity(pos);
                if (entity != null) {
                    entity.boot();
                }
            }
            updatePoweredState(state, world, pos);
        }
    }


    private void updatePoweredState(BlockState state, World world, BlockPos pos) {
        boolean isPowered = world.isReceivingRedstonePower(pos);
        if (state.get(POWERED) != isPowered) {
            world.setBlockState(pos, state.with(POWERED, isPowered), Block.NOTIFY_ALL);
        }
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        // Broken by a player: its running party stops, told as stopped by them (else onStateReplaced stops it)
        if (!world.isClient && world.getBlockEntity(pos) instanceof PartyControllerEntity entity)
            entity.stopParty(player.getDisplayName());
        return super.onBreak(world, pos, state, player);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() != newState.getBlock()) {
            PartyControllerEntity entity = (PartyControllerEntity) world.getBlockEntity(pos);
            if (entity != null) {
                // The party is gone: it stops (its mini-game still reads the catalogue), its players' steps HUD goes too
                entity.onControllerRemoved();
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, entity.catalogue);
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, entity.getBank());
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    public int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return 0;
    }

    /** Comparator: phase of the party (see {@link PartyControllerEntity#getPhase()}). */
    @Override
    protected boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof PartyControllerEntity entity ? entity.getPhase() : 0;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES.get(state.get(FACING));
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PartyControllerEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient || type != ModBlockEntities.PARTY_CONTROLLER_ENTITY) return null;
        return (w, pos, s, blockEntity) -> {
            if (w instanceof ServerWorld serverWorld && blockEntity instanceof PartyControllerEntity partyControllerEntity)
                partyControllerEntity.serverTick(serverWorld);
        };
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            updatePoweredState(state, world, pos);
        }
    }
}
