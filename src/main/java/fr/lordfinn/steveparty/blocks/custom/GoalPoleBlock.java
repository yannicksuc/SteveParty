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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
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
        // Both properties in one state: two separate updates from the same old state would undo each other
        BlockState updated = state.with(ON_BASE, isOnBase(world, pos)).with(TOP, isTop(world, pos));
        if (updated != state) world.setBlockState(pos, updated);

        // Refresh the cached base of this pole and the ones above. Not only when the source is a pole or a base:
        // the source is the block that was there BEFORE the change, so a base or a pole placed below reports air
        if (world.getBlockEntity(pos) instanceof GoalPoleBlockEntity poleEntity) {
            poleEntity.refreshFromBase();
        }
    }

    private boolean isOnBase(World world, BlockPos pos) {
        return world.getBlockState(pos.down()).getBlock() instanceof GoalPoleBaseBlock;
    }

    private boolean isTop(World world, BlockPos pos) {
        return !(world.getBlockState(pos.up()).getBlock() instanceof GoalPoleBlock);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        ItemStack stack = player.getStackInHand(Hand.MAIN_HAND);
        boolean shears = stack.isOf(Items.SHEARS) && state.get(FLAG);
        boolean flag = stack.getItem() instanceof FlagItem;
        boolean wrench = stack.getItem() instanceof WrenchItem;
        // A dye on the flag: only when it changes the colour (the colour is synced, so the client knows too)
        boolean dye = stack.getItem() instanceof DyeItem dyeItem && state.get(FLAG)
                && world.getBlockEntity(pos) instanceof GoalPoleBlockEntity pole
                && pole.getFlagColor() != FlagItem.dyeColor(dyeItem.getColor());
        // The client predicts the same result as the server (arm swing, no item use behind it)
        if (world.isClient) return shears || flag || wrench || dye ? ActionResult.SUCCESS : ActionResult.PASS;

        if (dye) {
            return handleDyeUse(world, pos, player, stack, ((DyeItem) stack.getItem()).getColor());
        }

        if (shears) {
            return handleShearsUse(world, pos, state, player, stack);
        }

        if (flag) {
            return handleFlagUse(world, pos, state, player, stack, hit);
        }

        if (wrench && world.getBlockEntity(pos) instanceof GoalPoleBlockEntity goalPoleBlockEntity) {
            goalPoleBlockEntity.openScreen((ServerPlayerEntity) player);
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
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

    private ActionResult handleDyeUse(World world, BlockPos pos, PlayerEntity player, ItemStack dye, DyeColor color) {
        if (!(world.getBlockEntity(pos) instanceof GoalPoleBlockEntity pole)) return ActionResult.PASS;
        pole.setFlagColor(FlagItem.dyeColor(color));
        if (!player.isCreative()) dye.decrement(1);
        world.playSound(null, pos, SoundEvents.ITEM_DYE_USE, SoundCategory.BLOCKS, 1f, 1f);
        return ActionResult.SUCCESS;
    }

    private ActionResult handleShearsUse(World world, BlockPos pos, BlockState state, PlayerEntity player, ItemStack shears) {
        // The flag keeps its colour as an item; the pole forgets it
        ItemStack dropped = new ItemStack(ModItems.FLAG);
        if (world.getBlockEntity(pos) instanceof GoalPoleBlockEntity pole) {
            dropped = pole.createFlagStack();
            pole.setFlagColor(FlagItem.NO_COLOR);
        }
        world.setBlockState(pos, state.with(FLAG, false), 3);
        ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), dropped);
        if (!player.isCreative()) shears.damage(1, player, EquipmentSlot.MAINHAND);
        world.playSound(null, pos, SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.BLOCKS, 1f, 1f);
        return ActionResult.SUCCESS;
    }

    private ActionResult handleFlagUse(World world, BlockPos pos, BlockState state, PlayerEntity player, ItemStack flag, BlockHitResult hit) {
        if (!state.get(FLAG)) placeFlag(world, pos, state, player, flag, hit);
        else rotateFlag(world, pos, state, player, hit);
        return ActionResult.SUCCESS;
    }

    private void placeFlag(World world, BlockPos pos, BlockState state, PlayerEntity player, ItemStack flag, BlockHitResult hit) {
        // The colour first: the block update then carries it to the clients in the same packet batch
        if (world.getBlockEntity(pos) instanceof GoalPoleBlockEntity pole) pole.setFlagColor(FlagItem.getColor(flag));
        world.setBlockState(pos, state.with(FLAG, true).with(FACING, flagFacing(hit, player)), 3);
        if (!player.isCreative()) flag.decrement(1);
        world.playSound(null, pos, SoundEvents.BLOCK_WOOL_FALL, SoundCategory.BLOCKS, 1f, 1f);
    }

    private void rotateFlag(World world, BlockPos pos, BlockState state, PlayerEntity player, BlockHitResult hit) {
        world.setBlockState(pos, state.with(FACING, flagFacing(hit, player)), 3);
        world.playSound(null, pos, SoundEvents.BLOCK_WOOL_STEP, SoundCategory.BLOCKS, 0.8f, 1f);
    }

    /**
     * Facing of a flag put on the clicked side. The top and bottom faces (the top of the pole, its ball) have no
     * horizontal rotation: the side facing the player is used instead (rotating UP/DOWN threw an exception).
     */
    public static Direction flagFacing(BlockHitResult hit, PlayerEntity player) {
        Direction side = hit.getSide();
        if (side.getAxis() == Direction.Axis.Y) side = player.getHorizontalFacing().getOpposite();
        return side.rotateYClockwise();
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
        if (state.getBlock() != newState.getBlock()) {
            if (state.get(FLAG)) {
                ItemStack flag = world.getBlockEntity(pos) instanceof GoalPoleBlockEntity pole ? pole.createFlagStack() : new ItemStack(ModItems.FLAG);
                ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), flag);
            }
            // The poles above are no longer connected to the base (with or without a flag on this one)
            BlockEntity beAbove = world.getBlockEntity(pos.up());
            if (beAbove instanceof GoalPoleBlockEntity poleAbove) {
                // At the end of the tick: the column is not in its new state yet
                GoalPoleNetwork.schedule(poleAbove);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new GoalPoleBlockEntity(pos, state);
    }
}
