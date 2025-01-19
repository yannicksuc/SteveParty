package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.components.PersistentInventoryComponent;
import fr.lordfinn.steveparty.screens.CartridgeInventoryScreenHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_CARTRIDGE_COMPONENT;

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
            PersistentInventoryComponent inventory = new PersistentInventoryComponent(6, stack);
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
