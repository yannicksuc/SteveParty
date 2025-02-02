package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.items.custom.teleportation_books.AbstractTeleportationBookItem;
import fr.lordfinn.steveparty.persistent_state.TeleportationPadStorage;
import fr.lordfinn.steveparty.persistent_state.TeleportationPadStorageManager;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class TeleportationPadBlock extends BlockWithEntity {
    public static final MapCodec<TeleportationPadBlock> CODEC = Block.createCodec(TeleportationPadBlock::new);
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16.0, 4.0, 16.0);

    public TeleportationPadBlock(Settings settings) {
        super(settings);
    }
    @Override
    protected MapCodec<? extends TeleportationPadBlock> getCodec() {
        return CODEC;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TeleportationPadBlockEntity(pos, state);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() != newState.getBlock() && !world.isClient) {
            TeleportationPadBlockEntity entity = (TeleportationPadBlockEntity) world.getBlockEntity(pos);
            if (entity != null) {
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, entity.book);
                TeleportationPadStorage storage = TeleportationPadStorageManager.getStorage((ServerWorld) world);
                storage.removeTeleportationPad(pos);
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient || hand.equals(Hand.OFF_HAND)) return ActionResult.PASS;
        if (stack.isEmpty() || stack.getItem() instanceof AbstractTeleportationBookItem) {
            TeleportationPadBlockEntity entity = (TeleportationPadBlockEntity) world.getBlockEntity(pos);
            if (entity != null) {
                entity.setBook(stack.copyAndEmpty());
                return ActionResult.SUCCESS;
            }
        }
        return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return TickableBlockEntity.getTicker(world);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (world.isClient) return;
        TeleportationPadBlockEntity entity = (TeleportationPadBlockEntity) world.getBlockEntity(pos);
        if (entity != null) {
            TeleportationPadStorage storage = TeleportationPadStorageManager.getStorage((ServerWorld) world);
            ItemStack book = entity.getBook();
            if (!book.isEmpty())
                storage.addTeleportationPad(pos, book);
        }
    }
}
