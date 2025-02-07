package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
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
        ItemStack mainHandItem = player.getMainHandStack();
        ItemStack offHandItem = player.getOffHandStack();

        if (isStencilAndDye(mainHandItem, offHandItem)) {
            handleDye(world, pos, player, offHandItem);
            return handleStencil(world, pos, player, mainHandItem);
        } else if (isDyeAndStencil(mainHandItem, offHandItem)) {
            handleStencil(world, pos, player, offHandItem);
            return handleDye(world, pos, player, mainHandItem);
        } else if (isGlowInkSac(mainHandItem)) {
            return handleGlowInkSac(world, pos, player, mainHandItem);
        } else if (isSponge(mainHandItem)) {
            return handleSponge(world, pos, player);
        }
        return ActionResult.PASS;
    }

    private boolean isStencilAndDye(ItemStack mainHandItem, ItemStack offHandItem) {
        return mainHandItem.getItem() == STENCIL && offHandItem.getItem() instanceof DyeItem;
    }

    private boolean isDyeAndStencil(ItemStack mainHandItem, ItemStack offHandItem) {
        return mainHandItem.getItem() instanceof DyeItem && offHandItem.getItem() == STENCIL;
    }

    private boolean isGlowInkSac(ItemStack mainHandItem) {
        return mainHandItem.getItem() == Items.GLOW_INK_SAC;
    }

    private boolean isSponge(ItemStack mainHandItem) {
        return mainHandItem.getItem() == Items.SPONGE;
    }

    private ActionResult handleStencil(World world, BlockPos pos, PlayerEntity player, ItemStack mainHandItem) {
        if (!world.isClient) {
            if (applyStencil(world, pos, player, mainHandItem)) {
                playSound(world, pos, SoundEvents.BLOCK_METAL_PLACE);
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }

    private ActionResult handleDye(World world, BlockPos pos, PlayerEntity player, ItemStack mainHandItem) {
        if (!world.isClient) {
            if (applyDye(world, pos, player, mainHandItem)) {
                playSound(world, pos, SoundEvents.ITEM_DYE_USE);
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }

    private ActionResult handleGlowInkSac(World world, BlockPos pos, PlayerEntity player, ItemStack mainHandItem) {
        if (!world.isClient) {
            if (applyGlowInkSac(world, pos, player, mainHandItem)) {
                playSound(world, pos, SoundEvents.ITEM_GLOW_INK_SAC_USE);
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }

    private ActionResult handleSponge(World world, BlockPos pos, PlayerEntity player) {
        if (!world.isClient) {
            if (removeGlow(world, pos, player)) {
                playSound(world, pos, SoundEvents.BLOCK_SPONGE_ABSORB);
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }

    private boolean applyStencil(World world, BlockPos pos, PlayerEntity player, ItemStack stencilItem) {
        BlockEntity entity = world.getBlockEntity(pos);
        if (entity instanceof TrafficSignBlockEntity signEntity) {
            byte[] shape = StencilItem.getShape(stencilItem);
            signEntity.setShape(shape);
            world.emitGameEvent(GameEvent.BLOCK_CHANGE, signEntity.getPos(), GameEvent.Emitter.of(player, signEntity.getCachedState()));
            world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), Block.NOTIFY_ALL);
            return true;
        }
        return false;
    }

    private boolean applyDye(World world, BlockPos pos, PlayerEntity player, ItemStack dyeItem) {
        BlockEntity entity = world.getBlockEntity(pos);
        if (entity instanceof TrafficSignBlockEntity signEntity) {
            DyeColor color = ((DyeItem) dyeItem.getItem()).getColor();
            signEntity.setColor(color);
            world.emitGameEvent(GameEvent.BLOCK_CHANGE, signEntity.getPos(), GameEvent.Emitter.of(player, signEntity.getCachedState()));
            world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), Block.NOTIFY_ALL);
            if (!player.isCreative()) {
                dyeItem.decrement(1);
            }
            return true;
        }
        return false;
    }

    private boolean applyGlowInkSac(World world, BlockPos pos, PlayerEntity player, ItemStack glowInkSacItem) {
        BlockEntity entity = world.getBlockEntity(pos);
        if (entity instanceof TrafficSignBlockEntity signEntity) {
            if (!signEntity.isGlowing()) {
                signEntity.setGlowing(true);
                world.emitGameEvent(GameEvent.BLOCK_CHANGE, signEntity.getPos(), GameEvent.Emitter.of(player, signEntity.getCachedState()));
                world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), Block.NOTIFY_ALL);
                if (!player.isCreative()) {
                    glowInkSacItem.decrement(1);
                }
                return true;
            }
        }
        return false;
    }

    private boolean removeGlow(World world, BlockPos pos, PlayerEntity player) {
        BlockEntity entity = world.getBlockEntity(pos);
        if (entity instanceof TrafficSignBlockEntity signEntity) {
            if (signEntity.isGlowing()) {
                signEntity.setGlowing(false);
                world.emitGameEvent(GameEvent.BLOCK_CHANGE, signEntity.getPos(), GameEvent.Emitter.of(player, signEntity.getCachedState()));
                world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), Block.NOTIFY_ALL);
                return true;
            }
        }
        return false;
    }

    private void playSound(World world, BlockPos pos, SoundEvent soundEvent) {
        world.playSound(null, pos, soundEvent, SoundCategory.BLOCKS, 1.0F, 1.0F);
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