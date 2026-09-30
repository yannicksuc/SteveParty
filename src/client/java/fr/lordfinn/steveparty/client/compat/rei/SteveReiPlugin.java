package fr.lordfinn.steveparty.client.compat.rei;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.compat.CartridgeApplications;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional REI plugin (entrypoint {@code rei_client}, only loaded by REI): every tile in each size and with each role in
 * the list, and the « Cartridge application » category (see {@link CartridgeApplications}).
 */
public class SteveReiPlugin implements REIClientPlugin {
    public static final CategoryIdentifier<CartridgeApplicationDisplay> CARTRIDGE_APPLICATION =
            CategoryIdentifier.of(Steveparty.MOD_ID, "cartridge_application");

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new CartridgeApplicationCategory());
        for (Block tile : CartridgeApplications.tiles()) {
            registry.addWorkstations(CARTRIDGE_APPLICATION, EntryStacks.of(tile));
        }
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        int count = 0;
        for (CartridgeApplications.Application application : CartridgeApplications.all()) {
            List<EntryStack<ItemStack>> results = new ArrayList<>();
            for (ItemStack result : application.results()) results.add(roleTile(result));
            registry.add(new CartridgeApplicationDisplay(application, EntryIngredients.ofItemStacks(application.tiles()),
                    EntryIngredients.of(application.cartridge()), EntryIngredient.of(results)));
            count++;
        }
        Steveparty.LOGGER.info("REI: {} cartridge application displays", count);
    }

    @Override
    public void registerEntries(EntryRegistry registry) {
        // Each tile: its small and large sizes, then one per role, after its standard item
        int count = 0;
        for (Block tile : CartridgeApplications.tiles()) {
            List<EntryStack<?>> entries = new ArrayList<>();
            List<ItemStack> sizes = CartridgeApplications.sizes(tile);
            for (ItemStack size : sizes.subList(1, sizes.size())) entries.add(EntryStacks.of(size));
            for (ItemStack cartridge : CartridgeApplications.cartridges()) {
                entries.add(roleTile(CartridgeApplications.holding(new ItemStack(tile), cartridge)));
            }
            registry.addEntriesAfter(EntryStacks.of(tile), entries);
            count += entries.size();
        }
        // Technical blocks never show (a large tile's parts have no item; kept in case one gets one)
        registry.removeEntry(EntryStacks.of(ModBlocks.TILE_PART));
        Steveparty.LOGGER.info("REI: {} extra tile entries", count);
    }

    /** {@code tile} (holding a cartridge) as a REI entry; its tooltip and face tell its role (TileContents). */
    private static EntryStack<ItemStack> roleTile(ItemStack tile) {
        return EntryStacks.of(tile);
    }
}
