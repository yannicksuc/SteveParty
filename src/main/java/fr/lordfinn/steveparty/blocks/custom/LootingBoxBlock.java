package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainer;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class LootingBoxBlock extends CartridgeContainer implements BlockEntityProvider {
    public static final Property<Boolean> ACTIVATED = BooleanProperty.of("activated");
    public static final Property<Boolean> TRIGGERED = BooleanProperty.of("triggered");

    public static final VoxelShape COLLISION_SHAPE = Block.createCuboidShape(0, 2, 0, 16, 16, 16);
    public static final VoxelShape RENDER_SHAPE = Block.createCuboidShape(0, 0, 0, 16, 16, 16);

    public LootingBoxBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(ACTIVATED, false).with(TRIGGERED, false));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return COLLISION_SHAPE;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return RENDER_SHAPE;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ACTIVATED, TRIGGERED);
    }
    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() != newState.getBlock()) {
            // Clean up the block entity when the block is destroyed/replaced
            if (!world.isClient) {
                world.removeBlockEntity(pos);
            }
            super.onStateReplaced(state, world, pos, newState, moved);
            return;
        }

        // Client-side animation trigger
        if (world.isClient) {
            if (newState.get(TRIGGERED) && !state.get(TRIGGERED) && world.getBlockEntity(pos) instanceof LootingBoxBlockEntity lootingBox) {
                lootingBox.triggerAnim("main", "punched");
            }
        }
    }


    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return Block.createCodec(LootingBoxBlock::new);
    }

    public static void testForCollision(ServerPlayerEntity player) {
        if (player.isOnGround()) {
            LootingBoxBlockEntity.resetPlayerInteractionState(player);
            return;
        }
        double playerHeight =  player.getBoundingBox().maxY + 0.2f;
        Vec3d headPos = new Vec3d(player.getPos().x, playerHeight, player.getPos().z);
         BlockPos headPosBlock = BlockPos.ofFloored(headPos);
            BlockEntity blockentity = player.getWorld()
                    .getBlockEntity(headPosBlock);
        if (blockentity instanceof LootingBoxBlockEntity lootBox) {
            lootBox.onPlayerInteract(player);
        }
    }
    @Override
    protected ActionResult onUseWithoutCartridgeContainerOpener(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        return ActionResult.FAIL;
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (world.isClient) return;

        if (state.get(TRIGGERED)) {
            world.setBlockState(pos, state.with(TRIGGERED, false), Block.NOTIFY_ALL);
        }
    }

    @Override
    public @Nullable LootingBoxBlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new LootingBoxBlockEntity(pos, state);
    }
}
