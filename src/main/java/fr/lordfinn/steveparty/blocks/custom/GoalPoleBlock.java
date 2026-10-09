package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.FlagItem;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A goal pole segment. A pole (a column of segments) has at most one flag, always on its top segment ({@link #FLAG}
 * and {@link #FACING} there; its colour in the block entity): it stays up there until the goal is met, then goes down
 * (see {@link GoalPoleFlags}). A pole without a flag has no goal shown: nothing above its top.
 * <p>
 * Right-click on any segment (see {@link #useOf}): a flag hangs it at the top, a dye colours it, shears take it off,
 * an empty hand on a side turns it towards that side; otherwise (an empty hand again, sneaking, or a Wrench) the goal
 * screen opens.
 */
public class GoalPoleBlock extends HorizontalFacingBlock implements BlockEntityProvider {

    public static final BooleanProperty FLAG = BooleanProperty.of("flag");
    public static final BooleanProperty ON_BASE = BooleanProperty.of("on_base");
    public static final BooleanProperty TOP = BooleanProperty.of("top");
    /** Longest column looked at, up or down. */
    private static final int MAX_COLUMN = 64;

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
                                  BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        // Both properties in one state: two separate updates from the same old state would undo each other
        BlockState updated = state.with(ON_BASE, isOnBase(world, pos)).with(TOP, isTop(world, pos));
        if (updated != state) world.setBlockState(pos, updated);
        // A segment put on the flagged top: the flag goes up to the new top
        if (!world.isClient && updated.get(FLAG) && !updated.get(TOP)) settleFlag(world, pos);

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

    // ------------------------------------------------------------------ the column's flag

    /** @return the top segment of the pole this segment belongs to. */
    public static BlockPos topOf(BlockView world, BlockPos anySegment) {
        BlockPos.Mutable cursor = anySegment.mutableCopy();
        for (int i = 0; i < MAX_COLUMN && world.getBlockState(cursor.up()).getBlock() instanceof GoalPoleBlock; i++) {
            cursor.move(Direction.UP);
        }
        return cursor.toImmutable();
    }

    private static BlockPos bottomOf(BlockView world, BlockPos anySegment) {
        BlockPos.Mutable cursor = anySegment.mutableCopy();
        for (int i = 0; i < MAX_COLUMN && world.getBlockState(cursor.down()).getBlock() instanceof GoalPoleBlock; i++) {
            cursor.move(Direction.DOWN);
        }
        return cursor.toImmutable();
    }

    /**
     * Whether the goal shows above this segment (the progress, the lit ball, the details with a Wrench): only on the top
     * segment of a pole with its flag. No flag, no goal shown.
     */
    public static boolean showsGoal(BlockState state) {
        return state.contains(TOP) && state.get(TOP) && state.get(FLAG);
    }

    /** @return whether the pole this segment belongs to has its flag (on its top segment). */
    public static boolean hasFlag(BlockView world, BlockPos anySegment) {
        BlockState top = world.getBlockState(topOf(world, anySegment));
        return top.contains(FLAG) && top.get(FLAG);
    }

    /**
     * One flag per pole, on its top segment: the highest flag of the column goes up to the top (colour and facing
     * kept), any other one drops as an item. Run when a segment is put on a flagged top, when two poles are joined,
     * and when poles saved with several flags are loaded. Flag changes notify no neighbour: nothing reacts to them.
     */
    public static void settleFlag(World world, BlockPos anySegment) {
        if (world.isClient) return;
        BlockPos top = topOf(world, anySegment), bottom = bottomOf(world, anySegment);
        BlockPos keeper = null;
        for (BlockPos.Mutable cursor = top.mutableCopy(); cursor.getY() >= bottom.getY(); cursor.move(Direction.DOWN)) {
            BlockState state = world.getBlockState(cursor);
            if (!state.contains(FLAG) || !state.get(FLAG)) continue;
            if (keeper == null) keeper = cursor.toImmutable();
            else dropFlag(world, cursor.toImmutable(), state);
        }
        if (keeper == null || keeper.equals(top)) return;
        BlockState from = world.getBlockState(keeper), to = world.getBlockState(top);
        if (world.getBlockEntity(keeper) instanceof GoalPoleBlockEntity old && world.getBlockEntity(top) instanceof GoalPoleBlockEntity now) {
            now.setFlagColor(old.getFlagColor());
            old.setFlagColor(FlagItem.NO_COLOR);
        }
        world.setBlockState(top, to.with(FLAG, true).with(FACING, from.get(FACING)), Block.NOTIFY_LISTENERS);
        world.setBlockState(keeper, from.with(FLAG, false), Block.NOTIFY_LISTENERS);
    }

    /** Takes the flag off this segment: it drops as an item, with its colour. */
    private static void dropFlag(World world, BlockPos pos, BlockState state) {
        ItemStack dropped = new ItemStack(ModItems.FLAG);
        if (world.getBlockEntity(pos) instanceof GoalPoleBlockEntity pole) {
            dropped = pole.createFlagStack();
            pole.setFlagColor(FlagItem.NO_COLOR);
        }
        world.setBlockState(pos, state.with(FLAG, false), Block.NOTIFY_LISTENERS);
        ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), dropped);
    }

    // ------------------------------------------------------------------ right-click

    /** What a right-click on a pole does. */
    public enum Use {
        NONE,
        /** A flag in hand on a pole without one: hangs it at the top. */
        HANG,
        /** A dye of another colour on the flag. */
        DYE,
        /** Shears: the flag drops as an item. */
        SHEAR,
        /** The flag turns towards the side clicked (an empty hand on a side, or a flag in hand). */
        TURN,
        /** The goal screen of the segment clicked. */
        GOAL
    }

    /**
     * What a right-click on a pole does, the same on both sides (the client predicts it). Only a player who may build
     * changes the pole (an adventure player in a party only opens the goal screen with a Wrench, which then changes
     * nothing).
     *
     * @param top the pole's top segment (where its flag is)
     */
    public static Use useOf(World world, BlockPos top, PlayerEntity player, ItemStack stack, BlockHitResult hit) {
        if (stack.getItem() instanceof WrenchItem) return Use.GOAL;
        if (!player.canModifyBlocks()) return Use.NONE;
        BlockState topState = world.getBlockState(top);
        boolean flagged = topState.contains(FLAG) && topState.get(FLAG);
        if (stack.isOf(Items.SHEARS)) return flagged ? Use.SHEAR : Use.NONE;
        if (stack.getItem() instanceof FlagItem) {
            if (!flagged) return Use.HANG;
            return flagFacing(hit, player) != topState.get(FACING) ? Use.TURN : Use.NONE;
        }
        if (stack.getItem() instanceof DyeItem dye) {
            return flagged && world.getBlockEntity(top) instanceof GoalPoleBlockEntity pole
                    && pole.getFlagColor() != FlagItem.dyeColor(dye.getColor()) ? Use.DYE : Use.NONE;
        }
        if (!stack.isEmpty()) return Use.NONE;
        // An empty hand: on a side the flag does not face yet, it turns the flag; anywhere else, the goal
        if (flagged && !player.isSneaking() && hit.getSide().getAxis().isHorizontal()
                && flagFacing(hit, player) != topState.get(FACING)) {
            return Use.TURN;
        }
        return Use.GOAL;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        ItemStack stack = player.getStackInHand(Hand.MAIN_HAND);
        BlockPos top = topOf(world, pos);
        Use use = useOf(world, top, player, stack, hit);
        if (use == Use.NONE) return ActionResult.PASS;
        // The client predicts the same result as the server (arm swing, no item use behind it)
        if (world.isClient) return ActionResult.SUCCESS;
        BlockState topState = world.getBlockState(top);
        GoalPoleBlockEntity topPole = world.getBlockEntity(top) instanceof GoalPoleBlockEntity entity ? entity : null;
        switch (use) {
            case HANG -> {
                // The colour first: the block update then carries it to the clients in the same packet batch
                if (topPole != null) topPole.setFlagColor(FlagItem.getColor(stack));
                world.setBlockState(top, topState.with(FLAG, true).with(FACING, flagFacing(hit, player)), Block.NOTIFY_LISTENERS);
                if (!player.isCreative()) stack.decrement(1);
                world.playSound(null, top, SoundEvents.BLOCK_WOOL_FALL, SoundCategory.BLOCKS, 1f, 1f);
            }
            case DYE -> {
                if (topPole != null) topPole.setFlagColor(FlagItem.dyeColor(((DyeItem) stack.getItem()).getColor()));
                if (!player.isCreative()) stack.decrement(1);
                world.playSound(null, top, SoundEvents.ITEM_DYE_USE, SoundCategory.BLOCKS, 1f, 1f);
            }
            case SHEAR -> {
                dropFlag(world, top, topState);
                if (!player.isCreative()) stack.damage(1, player, EquipmentSlot.MAINHAND);
                world.playSound(null, top, SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.BLOCKS, 1f, 1f);
            }
            case TURN -> {
                world.setBlockState(top, topState.with(FACING, flagFacing(hit, player)), Block.NOTIFY_LISTENERS);
                world.playSound(null, top, SoundEvents.BLOCK_WOOL_STEP, SoundCategory.BLOCKS, 0.8f, 1f);
            }
            case GOAL -> {
                if (player instanceof ServerPlayerEntity serverPlayer && world.getBlockEntity(pos) instanceof GoalPoleBlockEntity pole) {
                    pole.openScreen(serverPlayer);
                }
            }
            default -> {
            }
        }
        return ActionResult.SUCCESS;
    }

    /**
     * Facing of the flag turned towards the clicked side. The top and bottom faces (the top of the pole, its ball) have
     * no horizontal rotation: the side facing the player is used instead (rotating UP/DOWN threw an exception).
     */
    public static Direction flagFacing(BlockHitResult hit, PlayerEntity player) {
        Direction side = hit.getSide();
        if (side.getAxis() == Direction.Axis.Y) side = player.getHorizontalFacing().getOpposite();
        return side.rotateYClockwise();
    }

    /** In the inventory: the three right-clicks on a pole. */
    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        super.appendTooltip(stack, context, tooltip, options);
        for (String line : List.of("goal", "turn", "shears")) {
            tooltip.add(Text.translatable("block.steveparty.goal_pole.tooltip." + line).formatted(Formatting.GRAY));
        }
    }

    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    /** The progress towards this segment's goal, 0 to 15 (15 only once reached). */
    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof GoalPoleBlockEntity entity) {
            return entity.getRedstoneOutput();
        }
        return 0;
    }

    @Override
    public void onEntityLand(BlockView view, Entity entity) {
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
    protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
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
