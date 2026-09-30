package fr.lordfinn.steveparty.compat.rei;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.compat.CartridgeApplications;
import fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem;
import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.api.common.plugins.REICommonPlugin;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * Optional REI plugin (entrypoint {@code rei_common}, only loaded by REI): a tile holding a cartridge is looked up by its
 * role (recipes and usages of a Stop tile are not those of a plain tile), whatever its size.
 */
public class SteveReiCommonPlugin implements REICommonPlugin {
    @Override
    public void registerItemComparators(ItemComparatorRegistry registry) {
        registry.register((context, stack) -> {
            ItemStack cartridge = CartridgeApplications.heldCartridge(stack);
            long role = cartridge.isEmpty() ? 0 : 31L * Registries.ITEM.getRawId(cartridge.getItem())
                    + (cartridge.getItem() instanceof AdvanceBackCartridgeItem ? Integer.signum(AdvanceBackCartridgeItem.steps(cartridge)) : 0) + 2;
            // Exact: every component (sizes stay apart in the list)
            return context.isExact() ? role * 31L + stack.getComponentChanges().hashCode() : role;
        }, ModBlocks.TILE.asItem(), ModBlocks.ADVANCED_TILE.asItem());
    }
}
