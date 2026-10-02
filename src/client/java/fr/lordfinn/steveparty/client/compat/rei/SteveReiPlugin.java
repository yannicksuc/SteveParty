package fr.lordfinn.steveparty.client.compat.rei;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.compat.CartridgeApplications;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.dice.DiceModule;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.items.ModItems;
import me.shedaniel.rei.plugin.common.displays.crafting.DefaultCustomShapelessDisplay;
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
import java.util.Map;
import java.util.Optional;

/**
 * Optional REI plugin (entrypoint {@code rei_client}, only loaded by REI): every tile in each size and with each role in
 * the list, the « Cartridge application » category (see {@link CartridgeApplications}), and the crafts of the dice
 * modules (special recipes: shown as ordinary crafting recipes).
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
        registerDiceDisplays(registry);
    }

    /**
     * The dice recipes REI can't read by itself (recipe types of the mod): one craft per module (a die and the module item:
     * the die carrying it, on a plain die, a forged one, a Double and a Triple Dice in turn), and the Double / Triple
     * Dice keeping the modules of their dice.
     */
    private static void registerDiceDisplays(DisplayRegistry registry) {
        List<ItemStack> dice = List.of(new ItemStack(ModItems.DEFAULT_DICE),
                DiceFacesComponent.createDie(List.of(new ItemStack(ModItems.DICE_FACES.get(2)), new ItemStack(ModItems.DICE_FACES.get(7)))),
                new ItemStack(ModItems.DOUBLE_DICE), new ItemStack(ModItems.TRIPLE_DICE));
        for (DiceModule module : DiceModules.all()) {
            List<ItemStack> results = new ArrayList<>();
            for (ItemStack die : dice) results.add(DiceModules.set(die.copy(), Map.of(module, 1)));
            registry.add(new DefaultCustomShapelessDisplay(
                    List.of(EntryIngredients.ofItemStacks(dice), EntryIngredients.of(module.item())),
                    List.of(EntryIngredients.ofItemStacks(results)),
                    Optional.of(Steveparty.id("dice_module/" + module.id()))));
        }
        ItemStack lucky = DiceModules.set(new ItemStack(ModItems.DEFAULT_DICE), Map.of(DiceModules.LUCKY, 2));
        ItemStack infinite = DiceModules.set(new ItemStack(ModItems.DEFAULT_DICE), Map.of(DiceModules.INFINITY, 1));
        Map<DiceModule, Integer> both = DiceModules.union(DiceModules.of(lucky), DiceModules.of(infinite));
        registry.add(new DefaultCustomShapelessDisplay(
                List.of(EntryIngredients.of(lucky), EntryIngredients.of(infinite)),
                List.of(EntryIngredients.of(DiceModules.set(new ItemStack(ModItems.DOUBLE_DICE), both))),
                Optional.of(Steveparty.id("multi_dice/double"))));
        registry.add(new DefaultCustomShapelessDisplay(
                List.of(EntryIngredients.of(lucky), EntryIngredients.of(infinite), EntryIngredients.of(ModItems.DEFAULT_DICE)),
                List.of(EntryIngredients.of(DiceModules.set(new ItemStack(ModItems.TRIPLE_DICE), both))),
                Optional.of(Steveparty.id("multi_dice/triple"))));
        Steveparty.LOGGER.info("REI: {} dice module displays", DiceModules.all().size() + 2);
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
