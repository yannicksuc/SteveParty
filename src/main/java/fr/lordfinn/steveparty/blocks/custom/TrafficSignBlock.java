package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentChanges;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.DyeColor;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationPropertyHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.tick.ScheduledTickView;
import org.jetbrains.annotations.Nullable;

import static fr.lordfinn.steveparty.items.ModItems.STENCIL;

public class TrafficSignBlock extends BlockWithEntity {
    public static final MapCodec<SignBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance
            .group(WoodType.CODEC.fieldOf("wood_type")
                    .forGetter(TrafficSignBlock::getWoodType), createSettingsCodec())
            .apply(instance, SignBlock::new));
    protected static final VoxelShape SHAPE;
    protected static final BooleanProperty WATERLOGGED;
    protected static final IntProperty ROTATION;

    protected final WoodType type;

    public TrafficSignBlock(WoodType woodType, Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(ROTATION, 0)
                .with(WATERLOGGED, false));
        this.type = woodType;
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TrafficSignBlockEntity(pos, state);
    }

    public Vec3d getCenter(BlockState state) {
        return new Vec3d(0.5, 0.5, 0.5);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    public WoodType getWoodType() {
        return this.type;
    }

    public static WoodType getWoodType(Block block) {
        WoodType woodType;
        if (block instanceof AbstractSignBlock) {
            woodType = ((AbstractSignBlock)block).getWoodType();
        } else {
            woodType = WoodType.OAK;
        }

        return woodType;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return world.getBlockState(pos.down()).isSolid();
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        FluidState fluidState = ctx.getWorld().getFluidState(ctx.getBlockPos());
        return this.getDefaultState()
                .with(ROTATION, RotationPropertyHelper.fromYaw(ctx.getPlayerYaw() + 180.0F))
                .with(WATERLOGGED, fluidState.getFluid() == Fluids.WATER);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, Random random) {
        if (Direction.DOWN == direction && !this.canPlaceAt(state, world, pos))
            return Blocks.AIR.getDefaultState();
        if (state.get(WATERLOGGED)) {
            tickView.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }

        return super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public boolean canMobSpawnInside(BlockState state) {
        return false;
    }

    public static float getRotationDegrees(BlockState state) {
        return RotationPropertyHelper.toDegrees(state.get(ROTATION));
    }

    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(ROTATION, rotation.rotate(state.get(ROTATION), 16));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.with(ROTATION, mirror.mirror(state.get(ROTATION), 16));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ROTATION, WATERLOGGED);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        ItemStack heldItem = player.getMainHandStack();

        if (heldItem.getItem() == STENCIL) {
            if (!world.isClient) {
                BlockEntity entity = world.getBlockEntity(pos);
                if (entity instanceof TrafficSignBlockEntity signEntity) {
                    String newName = heldItem.getName().getString();
                    byte[] shape = StencilItem.getShape();
                    signEntity.setShape(shape);
                    world.emitGameEvent(GameEvent.BLOCK_CHANGE, signEntity.getPos(), GameEvent.Emitter.of(player, signEntity.getCachedState()));
                    world.updateListeners(pos, state, state, Block.NOTIFY_ALL);
                }
            }
            return ActionResult.SUCCESS;
        } else if (heldItem.getItem() instanceof DyeItem) {
            if (!world.isClient) {
                BlockEntity entity = world.getBlockEntity(pos);
                if (entity instanceof TrafficSignBlockEntity signEntity) {
                    DyeColor color = ((DyeItem) heldItem.getItem()).getColor();
                    signEntity.setColor(color);
                    world.emitGameEvent(GameEvent.BLOCK_CHANGE, signEntity.getPos(), GameEvent.Emitter.of(player, signEntity.getCachedState()));
                    world.updateListeners(pos, state, state, Block.NOTIFY_ALL);
                    if (!player.isCreative()) {
                        heldItem.decrement(1); // Consume the dye
                    }
                }
            }
            return ActionResult.SUCCESS;
        } else if (heldItem.getItem() == Items.GLOW_INK_SAC) {
            if (!world.isClient) {
                BlockEntity entity = world.getBlockEntity(pos);
                if (entity instanceof TrafficSignBlockEntity signEntity) {
                    if (!signEntity.isGlowing()) { // Only use glow ink sac if not already glowing
                        signEntity.setGlowing(true); // Set glowing state to true
                        world.emitGameEvent(GameEvent.BLOCK_CHANGE, signEntity.getPos(), GameEvent.Emitter.of(player, signEntity.getCachedState()));
                        world.updateListeners(pos, state, state, Block.NOTIFY_ALL);
                        if (!player.isCreative()) {
                            heldItem.decrement(1); // Consume the glow ink sac
                        }
                    }
                }
            }
            return ActionResult.SUCCESS;
        } else if (heldItem.getItem() == Items.SPONGE) {
            if (!world.isClient) {
                BlockEntity entity = world.getBlockEntity(pos);
                if (entity instanceof TrafficSignBlockEntity signEntity) {
                    if (signEntity.isGlowing()) { // Only use sponge if currently glowing
                        signEntity.setGlowing(false); // Remove glowing effect
                        world.emitGameEvent(GameEvent.BLOCK_CHANGE, signEntity.getPos(), GameEvent.Emitter.of(player, signEntity.getCachedState()));
                        world.updateListeners(pos, state, state, Block.NOTIFY_ALL);
                        // Sponge is not consumed
                    }
                }
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        BlockEntity blockEntity = world.getBlockEntity(pos);

        // Check if the block has a BlockEntity (like your TrafficSignBlockEntity)
        if (blockEntity instanceof TrafficSignBlockEntity signEntity) {
            // Prevent dropping the block in creative mode
            if (!player.isCreative()) {
                // Drop the block as an item
                ItemStack itemStack = new ItemStack(this);
                Block.dropStack(world, pos, itemStack);
            }
        }

        // Remove the block from the world
        world.removeBlock(pos, false);

        return super.onBreak(world, pos, state, player);
    }

    static {
        ROTATION = Properties.ROTATION;
        WATERLOGGED = Properties.WATERLOGGED;
        SHAPE = VoxelShapes.cuboid(0.125, 0, 0.125, 0.875, 0.9375, 0.875);
    }
}