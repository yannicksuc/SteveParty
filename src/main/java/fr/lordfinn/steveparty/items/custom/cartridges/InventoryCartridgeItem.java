package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.screen_handlers.CartridgeInventoryScreenHandler;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import static fr.lordfinn.steveparty.components.ModComponents.*;
import static fr.lordfinn.steveparty.utils.RaycastUtils.isTargetingBlock;

public class InventoryCartridgeItem extends CartridgeItem {

    public InventoryCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        World world = context.getWorld();
        Hand hand = context.getHand();
        if (!world.isClient && player != null && hand == Hand.OFF_HAND) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.getItem() instanceof InventoryCartridgeItem) {
                BlockPos blockPos = context.getBlockPos();
                if (world.getBlockEntity(blockPos) instanceof Inventory) {
                    if (blockPos.equals(getSavedInventoryPos(stack))) {
                        playCancelSound(blockPos, player);
                        saveBlockPos(stack, null);
                    } else {
                        playSelectSound(blockPos, player);
                        saveBlockPos(stack, blockPos);
                    }
                    return ActionResult.SUCCESS;
                } else {
                    MessageUtils.sendToPlayer((ServerPlayerEntity) player, Text.translatable("message.steveparty.block_not_inventory"), MessageUtils.MessageType.ACTION_BAR);
                    return ActionResult.FAIL;
                }
            }
        }
        return super.useOnBlock(context);
    }

    private void saveBlockPos(ItemStack stack, BlockPos pos) {
        stack.set(INVENTORY_POS, pos);
    }

    public BlockPos getSavedInventoryPos(ItemStack stack) {
        return stack.getOrDefault(INVENTORY_POS, null);
    }

    public static void setSelectionState(ItemStack stack, int state) {
        stack.set(SELECTION_STATE, state);
    }

    public static int getSelectionState(ItemStack stack) {
        return stack.getOrDefault(SELECTION_STATE, 0);
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient || !isHoldingInventoryCartridge(player)) {
            return super.use(world, player, hand);
        }

        if (isTargetingBlock(player)) {
            return super.use(world, player, hand); // Don't activate if targeting a block
        }

        openInventoryScreen(player);
        return ActionResult.SUCCESS;
    }


    public static void openInventoryScreen(PlayerEntity player) {
        ItemStack stackMainHand = player.getMainHandStack();
        InventoryComponent inventory = InventoryComponent.getInventoryFromStack(stackMainHand, 9);

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory1, playerEntity) -> new CartridgeInventoryScreenHandler(syncId, inventory1, inventory),
                Text.empty()
        ));
    }

    private boolean isHoldingInventoryCartridge(PlayerEntity player) {
        ItemStack mainHandStack = player.getMainHandStack();
        return !mainHandStack.isEmpty() && mainHandStack.getItem() instanceof InventoryCartridgeItem;
    }
}
