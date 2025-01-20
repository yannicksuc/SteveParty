package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.components.PersistentInventoryComponent;
import fr.lordfinn.steveparty.screens.CartridgeInventoryScreenHandler;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_CARTRIDGE_COMPONENT;
import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_POS;

public class InventoryCartridgeItem extends CartridgeItem {

    public InventoryCartridgeItem(Settings settings) {
        super(settings);
    }

    private PersistentInventoryComponent getInventory(ItemStack stack) {
        // Check if the item already has the inventory component saved in NBT
        if (stack.contains(INVENTORY_CARTRIDGE_COMPONENT) && stack.get(INVENTORY_CARTRIDGE_COMPONENT) instanceof PersistentInventoryComponent inventory) {
            inventory.setHolder(stack);
            return inventory;
        } else {
            PersistentInventoryComponent inventory = new PersistentInventoryComponent(9, stack);
            stack.set(INVENTORY_CARTRIDGE_COMPONENT, inventory);
            return inventory;
        }
    }

    public static void saveInventory(PersistentInventoryComponent inventory) {
        ItemStack stack = inventory.getHolder();
        if (stack == null || stack.isEmpty()) return;
        stack.set(INVENTORY_CARTRIDGE_COMPONENT, inventory);
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

    private boolean isHoldingInventoryCartridge(PlayerEntity player) {
        ItemStack mainHandStack = player.getMainHandStack();
        return !mainHandStack.isEmpty() && mainHandStack.getItem() instanceof InventoryCartridgeItem;
    }

    private boolean isTargetingBlock(PlayerEntity player) {
        double reachDistance = player.isCreative() ? 5.0 : 4.5; // Creative players have a slightly longer reach
        HitResult hitResult = player.raycast(reachDistance, 0.0F, false);
        return hitResult.getType() == HitResult.Type.BLOCK;
    }

    private void openInventoryScreen(PlayerEntity player) {
        ItemStack stackMainHand = player.getMainHandStack();
        PersistentInventoryComponent inventory = getInventory(stackMainHand);

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory1, playerEntity) -> new CartridgeInventoryScreenHandler(syncId, inventory1, inventory),
                Text.empty()
        ));
    }
}
