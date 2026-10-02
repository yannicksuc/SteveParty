package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Mini-game Controller block (see {@link MiniGameControllerBlockEntity}). A click with a Mini-game Page (or a
 * Zone Cartridge) puts it in when its slot is free; a sneaking click with empty hands takes the page back (the
 * cartridge when there is no page); any other click opens its screen. Putting in and taking out take the right to
 * build. No redstone, no comparator.
 */
public class MiniGameControllerBlock extends Block implements BlockEntityProvider {
    public MiniGameControllerBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof MiniGameControllerBlockEntity controller)) return ActionResult.PASS;
        if (!player.isSneaking()) {
            player.openHandledScreen(controller);
            return ActionResult.SUCCESS;
        }
        boolean takesPage = !controller.getPage().isEmpty();
        ItemStack held = takesPage ? controller.getPage() : controller.getCartridge();
        if (held.isEmpty()) return ActionResult.PASS;
        if (!MiniGamePages.canEdit(player)) {
            player.sendMessage(Text.translatable("message.steveparty.mini_game_controller.no_build").formatted(Formatting.RED), true);
            return ActionResult.SUCCESS;
        }
        if (takesPage) controller.setPage(ItemStack.EMPTY);
        else controller.setCartridge(ItemStack.EMPTY);
        player.getInventory().offerOrDrop(held);
        world.playSound(null, pos, SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 1f, 1f);
        return ActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        // The Wrench is not for this block
        if (stack.getItem() instanceof WrenchItem) return ActionResult.PASS;
        boolean isPage = MiniGamePages.isPage(stack), isCartridge = MiniGameControllerBlockEntity.isZoneCartridge(stack);
        if ((!isPage && !isCartridge) || hand == Hand.OFF_HAND) return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION;
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof MiniGameControllerBlockEntity controller)) return ActionResult.PASS;
        // Its slot is taken: the screen (where it can be swapped)
        if (!(isPage ? controller.getPage() : controller.getCartridge()).isEmpty()) return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION;
        if (!MiniGamePages.canEdit(player)) {
            player.sendMessage(Text.translatable("message.steveparty.mini_game_controller.no_build").formatted(Formatting.RED), true);
            return ActionResult.SUCCESS;
        }
        if (isPage && !controller.accepts(stack)) {
            player.sendMessage(Text.translatable("message.steveparty.mini_game_controller.other_home").formatted(Formatting.RED), true);
            return ActionResult.SUCCESS;
        }
        ItemStack put = stack.split(1);
        if (isPage) controller.setPage(put);
        else controller.setCartridge(put);
        world.playSound(null, pos, SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 1f, 1f);
        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, net.minecraft.item.Item.TooltipContext context, java.util.List<Text> tooltip,
                              net.minecraft.item.tooltip.TooltipType options) {
        super.appendTooltip(stack, context, tooltip, options);
        for (String line : java.util.List.of("page", "play", "practice", "zone")) {
            tooltip.add(Text.translatable("tooltip.steveparty.mini_game_controller." + line).formatted(Formatting.GRAY));
        }
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() != newState.getBlock() && world.getBlockEntity(pos) instanceof MiniGameControllerBlockEntity controller) {
            controller.onBroken();
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MiniGameControllerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient || type != ModBlockEntities.MINI_GAME_CONTROLLER_ENTITY) return null;
        return (w, pos, s, blockEntity) -> {
            if (w instanceof ServerWorld serverWorld && blockEntity instanceof MiniGameControllerBlockEntity controller)
                controller.serverTick(serverWorld);
        };
    }
}
