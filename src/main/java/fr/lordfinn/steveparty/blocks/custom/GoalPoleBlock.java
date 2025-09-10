package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.FlagItem;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;

import static fr.lordfinn.steveparty.sounds.ModSounds.GOAL_POLE_REACH;

public class GoalPoleBlock extends HorizontalFacingBlock implements BlockEntityProvider {

    public static final BooleanProperty FLAG = BooleanProperty.of("flag");
    public static final BooleanProperty ON_BASE = BooleanProperty.of("on_base");
    public static final BooleanProperty TOP = BooleanProperty.of("top");

    private static final VoxelShape POLE_SHAPE = Block.createCuboidShape(6.5, 0.0, 6.5, 9.5, 16.0, 9.5);
    private static final VoxelShape POLE_SHAPE_TOP = VoxelShapes.union(
            VoxelShapes.cuboid(0.40625, 0, 0.40625, 0.59375, 1, 0.59375),
            VoxelShapes.cuboid(0.3125, 0.84375, 0.3125, 0.6875, 1, 0.6875)
    );

    public GoalPoleBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState());
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, FLAG, ON_BASE, TOP);
    }

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState()
                .with(FACING, ctx.getHorizontalPlayerFacing().getOpposite())
                .with(FLAG, false)
                .with(ON_BASE, isOnBase(ctx.getWorld(), ctx.getBlockPos()))
                .with(TOP, isTop(ctx.getWorld(), ctx.getBlockPos()));
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock,
                                  @Nullable WireOrientation wireOrientation, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, wireOrientation, notify);
        updateOnBaseProperty(state, world, pos);
        updateTopProperty(state, world, pos);

        // Update cache if neighbor below changed
        if (sourceBlock instanceof GoalPoleBlock || sourceBlock instanceof GoalPoleBaseBlock) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof GoalPoleBlockEntity poleEntity) {
                poleEntity.updateCachedBase();
                poleEntity.propagateCachedBaseUpwards();
            }
        }
    }

    private boolean isOnBase(World world, BlockPos pos) {
        return world.getBlockState(pos.down()).getBlock() instanceof GoalPoleBaseBlock;
    }

    private boolean isTop(World world, BlockPos pos) {
        return !(world.getBlockState(pos.up()).getBlock() instanceof GoalPoleBlock);
    }

    private void updateOnBaseProperty(BlockState state, World world, BlockPos pos) {
        boolean isOnBase = isOnBase(world, pos);
        if (state.get(ON_BASE) != isOnBase) {
            world.setBlockState(pos, state.with(ON_BASE, isOnBase));
        }
    }

    private void updateTopProperty(BlockState state, World world, BlockPos pos) {
        boolean isTop = isTop(world, pos);
        if (state.get(TOP) != isTop) {
            world.setBlockState(pos, state.with(TOP, isTop));
        }
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        ItemStack stack = player.getStackInHand(Hand.MAIN_HAND);
        if (world.isClient) return ActionResult.PASS;

        if (stack.isOf(Items.SHEARS) && state.get(FLAG)) {
            return handleShearsUse(world, pos, state, player, stack);
        }

        if (stack.getItem() instanceof FlagItem) {
            return handleFlagUse(world, pos, state, player, stack, hit);
        }

        if (stack.getItem() instanceof WrenchItem) {
            if (world.getBlockEntity(pos) instanceof GoalPoleBlockEntity goalPoleBlockEntity) {
                if (stack.getItem() instanceof WrenchItem) {
                    // Open the screen
                    goalPoleBlockEntity.openScreen((ServerPlayerEntity) player);
                    return ActionResult.SUCCESS;
                } else {
                    // Send action bar message if not using wrench
                    player.sendMessage(
                            Text.translatable("message.steveparty.wrench_required").formatted(Formatting.GOLD),
                            true
                    );
                }
            }
        }

        return ActionResult.PASS;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (type == ModBlockEntities.GOAL_POLE_ENTITY) {
            return (world1, pos, state1, blockEntity) -> ((GoalPoleBlockEntity) blockEntity).tick(world1, pos, state1, blockEntity);
        }
        return null;
    }
    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof GoalPoleBlockEntity entity) {
            return entity.getRedstoneOutput();
        }
        return 0;
    }
    @Override
    public void onEntityLand(BlockView view, net.minecraft.entity.Entity entity) {
        super.onEntityLand(view, entity);

        if (!entity.getWorld().isClient && entity instanceof ServerPlayerEntity player) {
            BlockPos pos = entity.getBlockPos().down();
            if (view.getBlockEntity(pos) instanceof GoalPoleBlockEntity pole) {
                pole.onPlayerArrive(player, view, pos);
            }
        }
    }

    @Override
    public void onLandedUpon(World world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        entity.handleFallDamage(fallDistance, 0.0F, world.getDamageSources().fall()); // 0.0F = aucun dégât
    }

    private ActionResult handleShearsUse(World world, BlockPos pos, BlockState state, PlayerEntity player, ItemStack shears) {
        world.setBlockState(pos, state.with(FLAG, false), 3);
        ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(ModItems.FLAG));
        if (!player.isCreative()) shears.damage(1, player, EquipmentSlot.MAINHAND);
        world.playSound(null, pos, SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.BLOCKS, 1f, 1f);
        return ActionResult.SUCCESS;
    }

    private ActionResult handleFlagUse(World world, BlockPos pos, BlockState state, PlayerEntity player, ItemStack flag, BlockHitResult hit) {
        if (!state.get(FLAG)) placeFlag(world, pos, state, player, flag, hit);
        else rotateFlag(world, pos, state, hit);
        return ActionResult.SUCCESS;
    }

    private void placeFlag(World world, BlockPos pos, BlockState state, PlayerEntity player, ItemStack flag, BlockHitResult hit) {
        Direction hitSide = hit.getSide();
        BlockState newState = (hitSide != Direction.UP && hitSide != Direction.DOWN)
                ? state.with(FLAG, true).with(FACING, hitSide.rotateYClockwise())
                : state.with(FLAG, true);

        world.setBlockState(pos, newState, 3);
        if (!player.isCreative()) flag.decrement(1);
        world.playSound(null, pos, SoundEvents.BLOCK_WOOL_FALL, SoundCategory.BLOCKS, 1f, 1f);
    }

    private void rotateFlag(World world, BlockPos pos, BlockState state, BlockHitResult hit) {
        world.setBlockState(pos, state.with(FACING, hit.getSide().rotateYClockwise()), 3);
        world.playSound(null, pos, SoundEvents.BLOCK_WOOL_STEP, SoundCategory.BLOCKS, 0.8f, 1f);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(TOP) ? POLE_SHAPE_TOP : POLE_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return POLE_SHAPE;
    }

    @Override
    public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
        return 1f;
    }

    @Override
    protected boolean isTransparent(BlockState state) {
        return true;
    }

    @Override
    protected MapCodec<GoalPoleBlock> getCodec() {
        return createCodec(GoalPoleBlock::new);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() != newState.getBlock() && state.get(FLAG)) {
            ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(ModItems.FLAG));
            // notify pole above
            BlockEntity beAbove = world.getBlockEntity(pos.up());
            if (beAbove instanceof GoalPoleBlockEntity poleAbove) {
                poleAbove.updateCachedBase();
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new GoalPoleBlockEntity(pos, state);
    }
}
