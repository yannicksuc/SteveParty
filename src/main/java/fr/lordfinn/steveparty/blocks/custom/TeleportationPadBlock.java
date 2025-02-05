package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.teleportation_books.AbstractTeleportationBookItem;
import fr.lordfinn.steveparty.items.custom.teleportation_books.HereWeGoBookItem;
import fr.lordfinn.steveparty.persistent_state.TeleportationHistoryStorage;
import fr.lordfinn.steveparty.persistent_state.TeleportationPadBooksStorage;
import fr.lordfinn.steveparty.persistent_state.TeleportationPadStorageManager;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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
                TeleportationPadBooksStorage storage = TeleportationPadStorageManager.getBooksStorage((ServerWorld) world);
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
            TeleportationPadBooksStorage storage = TeleportationPadStorageManager.getBooksStorage((ServerWorld) world);
            ItemStack book = entity.getBook();
            if (!book.isEmpty())
                storage.addTeleportationPad(pos, book);
        }
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos padPos, Entity entity) {
        if (world.isClient || !(entity instanceof PlayerEntity player)) return;  // Only detect players

        TeleportationPadBlockEntity entityBlock = (TeleportationPadBlockEntity) world.getBlockEntity(padPos);
        if (entityBlock == null || !(entityBlock.getBook() instanceof ItemStack book) || book.isEmpty()) return;  // Ensure the pad is not empty
        if (!(book.getItem() instanceof HereWeGoBookItem)) return;
        BlockPos tpPos = HereWeGoBookItem.getTpPos(book, player);
        if (tpPos != null) {
            TeleportationHistoryStorage storage = TeleportationPadStorageManager.getTeleportationHistoryStorage((ServerWorld) player.getWorld());
            storage.addTeleportation(player.getUuid(), padPos, tpPos);
        }

        teleportPlayer((ServerPlayerEntity) player, padPos.add(5, 1, 5));
    }

    private void playTeleportationSound(World world, BlockPos pos) {
        world.playSound(null, pos, SoundEvents.ENTITY_PLAYER_TELEPORT, SoundCategory.PLAYERS, 0.4f, 1.0f);
        world.playSound(null, pos, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0f, 1.0f);
        Steveparty.SCHEDULER.schedule(
                UUID.randomUUID(),
                4,
                () -> {
                    world.playSound(null, pos, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
        );
        Steveparty.SCHEDULER.schedule(
                UUID.randomUUID(),
                2,
                () -> {
                    world.playSound(null, pos, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
        );
    }

    private void teleportPlayer(ServerPlayerEntity player, BlockPos pos) {
        Steveparty.SCHEDULER.schedule(
                player.getUuid(),
                10, // Delay by 1 tick
                () -> {
                    // Teleport the player after the tick is done
                    playTeleportationSound(player.getWorld(), pos);
                    player.teleport(pos.getX(), pos.getY(), pos.getZ(), true);
                }
        );
    }
}
