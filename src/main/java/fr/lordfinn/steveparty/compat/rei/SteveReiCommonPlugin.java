package fr.lordfinn.steveparty.compat.rei;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.compat.CartridgeApplications;
import fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem;
import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.api.common.plugins.REIServerPlugin;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * Optional REI plugin (REI 16 {@code REIServerPlugin}, entrypoint {@code rei_common} like REI's own default plugin; only loaded by REI): a tile holding a cartridge is looked up by its
 * role (recipes and usages of a Stop tile are not those of a plain tile), whatever its size.
 */
public class SteveReiCommonPlugin implements REIServerPlugin {
    @Override
    public void registerItemComparators(ItemComparatorRegistry registry) {
        registry.register((context, stack) -> {
            ItemStack cartridge = CartridgeApplications.heldCartridge(stack);
            long role = cartridge.isEmpty() ? 0 : 31L * Registries.ITEM.getRawId(cartridge.getItem())
                    + (cartridge.getItem() instanceof AdvanceBackCartridgeItem ? Integer.signum(AdvanceBackCartridgeItem.steps(cartridge)) : 0) + 2;
            // Exact: every component (sizes stay apart in the list)
            return context.isExact() ? role * 31L + stack.getComponentChanges().hashCode() : role;
        }, ModBlocks.TILE.asItem(), ModBlocks.ADVANCED_TILE.asItem());
        // Polished tiles: one item per material, each pair of colours is its own entry with its own recipe
        registry.register((context, stack) -> stack.getComponentChanges().hashCode(),
                ModBlocks.POLISHED_CONCRETE_TILES.asItem(), ModBlocks.POLISHED_TERRACOTTA_TILES.asItem());
    }
}
