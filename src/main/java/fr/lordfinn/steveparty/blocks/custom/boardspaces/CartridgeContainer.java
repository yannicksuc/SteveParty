package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.items.custom.CartridgeContainerOpener;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.util.ActionResult.*;

public abstract class CartridgeContainer extends HorizontalFacingBlock implements BlockEntityProvider {
    protected CartridgeContainer(Settings settings) {
        super(settings);
    }

    @Override
    public NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, net.minecraft.world.World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof NamedScreenHandlerFactory) {
            return (NamedScreenHandlerFactory) blockEntity;
        }
        return null;
    }

    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return ScreenHandler.calculateComparatorOutput(world.getBlockEntity(pos));
    }

    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return SUCCESS;
        ItemStack mainHandStack = player.getMainHandStack();
        ItemStack offHandStack = player.getOffHandStack();
        if (mainHandStack.isEmpty() && offHandStack.isEmpty()) return onUse(state, world, pos, player, hit);
        if (!(mainHandStack.getItem() instanceof CartridgeContainerOpener) && !(offHandStack.getItem() instanceof CartridgeContainerOpener)) {
            return onUseWithoutCartridgeContainerOpener(stack, state, world, pos, player, hand, hit);
        }
        ActionResult.Success success = openScreen(state, world, pos, player);
        if (success != null) return success;
        return FAIL;
    }

    protected ActionResult.@Nullable Success openScreen(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        NamedScreenHandlerFactory screenHandlerFactory = state.createScreenHandlerFactory(world, pos);
        if (screenHandlerFactory != null) {
            world.playSound(null, pos, ModSounds.OPEN_TILE_GUI_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
            player.openHandledScreen(screenHandlerFactory);
            return SUCCESS;
        }
        return null;
    }

    protected abstract ActionResult onUseWithoutCartridgeContainerOpener(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit);

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return TickableBlockEntity.getTicker(world);
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        BlockEntity blockEntity = world.getBlockEntity(pos);

        if (blockEntity instanceof CartridgeContainerBlockEntity inventory) {
            // Drop all items in the inventory
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.getStack(i);
                if (!stack.isEmpty()) {
                    dropStack(world, pos, stack);
                }
            }
        }

        super.onBreak(world, pos, state, player);
        return state;
    }
}
