package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.components.PersistentInventoryComponent;
import fr.lordfinn.steveparty.screens.CartridgeInventoryScreenHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_CARTRIDGE_COMPONENT;

public class InventoryCartridgeItem extends CartridgeItem {

    public InventoryCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity player, LivingEntity entity, Hand hand) {
        if (!player.getWorld().isClient) {
            // Get or create the persistent inventory component for this item
            PersistentInventoryComponent inventory = getInventory(stack);
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, inventory1, playerEntity) -> new CartridgeInventoryScreenHandler(syncId, inventory1, inventory),
                    Text.empty()
            ));
            return ActionResult.SUCCESS;
        }
        return super.useOnEntity(stack, player, entity, hand);
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
}
