package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.ChestCartridgeItem;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/** A Party Controller's bank for the tests: a chest holding coins and stars, and the Chest Cartridge pointing to it. */
final class BankFixtures {
    private BankFixtures() {
    }

    /** A Chest Cartridge pointing to the chest at the relative position {@code chest}. */
    static ItemStack cartridge(TestContext context, BlockPos chest) {
        ItemStack cartridge = new ItemStack(ModItems.CHEST_CARTRIDGE);
        ChestCartridgeItem.point(cartridge, context.getWorld(), context.getAbsolutePos(chest), null);
        return cartridge;
    }

    /**
     * Puts a chest at {@code chest} holding exactly {@code coins} coins and {@code stars} stars of the controller's
     * currencies, and makes it the controller's bank.
     *
     * @return the chest
     */
    static ChestBlockEntity stock(TestContext context, PartyControllerEntity controller, BlockPos chest, int coins, int stars) {
        context.setBlockState(chest, Blocks.CHEST);
        ChestBlockEntity entity = context.getBlockEntity(chest);
        entity.clear();
        int slot = 0;
        slot = fill(entity, slot, controller.getCurrency(PartyCurrency.COIN), coins);
        fill(entity, slot, controller.getCurrency(PartyCurrency.STAR), stars);
        controller.setBank(cartridge(context, chest));
        return entity;
    }

    private static int fill(ChestBlockEntity chest, int slot, ItemStack template, int amount) {
        while (amount > 0 && slot < chest.size()) {
            int count = Math.min(amount, template.getMaxCount());
            chest.setStack(slot++, template.copyWithCount(count));
            amount -= count;
        }
        return slot;
    }

    /** How many items of exactly that kind are in an inventory. */
    static int count(net.minecraft.inventory.Inventory inventory, ItemStack template) {
        int count = 0;
        for (int i = 0; i < inventory.size(); i++) {
            if (ItemStack.areItemsAndComponentsEqual(inventory.getStack(i), template)) count += inventory.getStack(i).getCount();
        }
        return count;
    }
}
