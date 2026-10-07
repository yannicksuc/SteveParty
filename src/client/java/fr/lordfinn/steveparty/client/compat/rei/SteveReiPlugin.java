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
import me.shedaniel.rei.api.client.registry.screen.DisplayBoundsProvider;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import fr.lordfinn.steveparty.client.screens.DiceForgeScreen;
import me.shedaniel.math.Rectangle;
import net.minecraft.client.gui.screen.Screen;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Optional REI plugin (entrypoint {@code rei_client}, only loaded by REI): every tile in each size and with each role in
 * the list, the « Cartridge application » category (see {@link CartridgeApplications}), the « Dice Forge » category,
 * and the crafts of the dice modules (special recipes: shown as ordinary crafting recipes).
 */
public class SteveReiPlugin implements REIClientPlugin {
    public static final CategoryIdentifier<CartridgeApplicationDisplay> CARTRIDGE_APPLICATION =
            CategoryIdentifier.of(Steveparty.MOD_ID, "cartridge_application");
    public static final CategoryIdentifier<DiceForgeDisplay> DICE_FORGE = CategoryIdentifier.of(Steveparty.MOD_ID, "dice_forge");

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new CartridgeApplicationCategory());
        registry.add(new DiceForgeCategory());
        registry.addWorkstations(DICE_FORGE, EntryStacks.of(ModBlocks.DICE_FORGE));
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
        registerDiceForgeDisplay(registry);
        registerTileCartridgeDisplays(registry);
    }

    /**
     * The crafts of TileCartridgeRecipe (a special recipe REI can't read): a Tile + a cartridge (its old one back, said
     * by the tooltips), and an Advanced Tile filled with Tiles (slot 0, then 15).
     */
    private static void registerTileCartridgeDisplays(DisplayRegistry registry) {
        ItemStack plain = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
        ItemStack tile = CartridgeApplications.holding(new ItemStack(ModBlocks.TILE), plain);
        List<ItemStack> cartridges = CartridgeApplications.cartridges();
        List<ItemStack> results = new ArrayList<>();
        for (ItemStack cartridge : cartridges) results.add(CartridgeApplications.holding(new ItemStack(ModBlocks.TILE), cartridge));
        registry.add(DefaultCustomShapelessDisplay.simple(
                List.of(EntryIngredients.of(tile), EntryIngredients.ofItemStacks(cartridges)),
                List.of(EntryIngredients.ofItemStacks(results)),
                Optional.of(Steveparty.id("tile_cartridge/tile"))));
        ItemStack stop = CartridgeApplications.holding(new ItemStack(ModBlocks.TILE), new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP));
        ItemStack filled = fr.lordfinn.steveparty.recipes.TileCartridgeRecipe.fill(new ItemStack(ModBlocks.ADVANCED_TILE),
                List.of(plain, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP)));
        if (filled != null) registry.add(DefaultCustomShapelessDisplay.simple(
                List.of(EntryIngredients.of(ModBlocks.ADVANCED_TILE), EntryIngredients.of(tile), EntryIngredients.of(stop)),
                List.of(EntryIngredients.of(filled)),
                Optional.of(Steveparty.id("tile_cartridge/advanced_tile"))));
    }

    /**
     * The Dice Forge (no recipe type REI could read): any die faces, their blank faces, 5 fragments of different colours
     * (each slot cycles through the colours, offset so that they never match), any module, the Gravity Core; out come
     * forged dice, and the plain Default Die so that its recipe lookup lands here; each also with the Power-up module,
     * so that a power-up die's lookup lands here too.
     */
    private static void registerDiceForgeDisplay(DisplayRegistry registry) {
        List<ItemStack> faces = ModItems.DICE_FACES.subList(1, ModItems.DICE_FACES.size()).stream().map(ItemStack::new).toList();
        List<Item> colours = ModItems.STAR_FRAGMENTS;
        List<EntryIngredient> fragments = new ArrayList<>();
        for (int slot = 0; slot < 5; slot++) {
            List<ItemStack> cycle = new ArrayList<>();
            for (int i = 0; i < colours.size(); i++) cycle.add(new ItemStack(colours.get((i + slot * 3) % colours.size())));
            fragments.add(EntryIngredients.ofItemStacks(cycle));
        }
        List<ItemStack> dice = new ArrayList<>();
        dice.add(DiceFacesComponent.createDie(List.of(new ItemStack(ModItems.DICE_FACES.get(2)), new ItemStack(ModItems.DICE_FACES.get(7)))));
        dice.add(new ItemStack(ModItems.DEFAULT_DICE));
        for (ItemStack die : List.copyOf(dice)) dice.add(powerUp(die.copy()));
        registry.add(new DiceForgeDisplay(EntryIngredients.ofItemStacks(faces), EntryIngredients.of(ModItems.blankDiceFace()),
                fragments, EntryIngredients.ofItemStacks(ModItems.DICE_MODULES.stream().map(ItemStack::new).toList()), EntryIngredients.of(ModBlocks.GRAVITY_CORE),
                EntryIngredients.ofItemStacks(dice)));
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
            registry.add(DefaultCustomShapelessDisplay.simple(
                    List.of(EntryIngredients.ofItemStacks(dice), EntryIngredients.of(module.item())),
                    List.of(EntryIngredients.ofItemStacks(results)),
                    Optional.of(Steveparty.id("dice_module/" + module.id()))));
        }
        ItemStack lucky = DiceModules.set(new ItemStack(ModItems.DEFAULT_DICE), Map.of(DiceModules.LUCKY, 2));
        ItemStack slow = DiceModules.set(new ItemStack(ModItems.DEFAULT_DICE), Map.of(DiceModules.SLOW, 1));
        Map<DiceModule, Integer> both = DiceModules.union(DiceModules.of(lucky), DiceModules.of(slow));
        registry.add(DefaultCustomShapelessDisplay.simple(
                List.of(EntryIngredients.of(lucky), EntryIngredients.of(slow)),
                List.of(EntryIngredients.of(DiceModules.set(new ItemStack(ModItems.DOUBLE_DICE), both))),
                Optional.of(Steveparty.id("multi_dice/double"))));
        registry.add(DefaultCustomShapelessDisplay.simple(
                List.of(EntryIngredients.of(lucky), EntryIngredients.of(slow), EntryIngredients.of(ModItems.DEFAULT_DICE)),
                List.of(EntryIngredients.of(DiceModules.set(new ItemStack(ModItems.TRIPLE_DICE), both))),
                Optional.of(Steveparty.id("multi_dice/triple"))));
        Steveparty.LOGGER.info("REI: {} die module displays", DiceModules.all().size() + 2);
    }

    /**
     * The Dice Forge screen is drawn shrunk at large GUI scales (DiceForgeScreen#init): REI gets its bounds as drawn, so
     * that its panels lay out around the forge and not around its unshrunk layout.
     */
    @Override
    public void registerScreens(ScreenRegistry registry) {
        registry.registerDecider(new DisplayBoundsProvider<DiceForgeScreen>() {
            @Override
            public Rectangle getScreenBounds(DiceForgeScreen screen) {
                int[] bounds = screen.getFittedBounds();
                return new Rectangle(bounds[0], bounds[1], bounds[2], bounds[3]);
            }

            @Override
            public <R extends Screen> boolean isHandingScreen(Class<R> screen) {
                return DiceForgeScreen.class.isAssignableFrom(screen);
            }

            @Override
            public double getPriority() {
                return 10;
            }
        });
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
        // Each die: its power-up version (carrying the Power-up module) after the plain one
        for (Item die : List.of(ModItems.DEFAULT_DICE, ModItems.DOUBLE_DICE, ModItems.TRIPLE_DICE))
            registry.addEntriesAfter(EntryStacks.of(die), List.of(EntryStacks.of(powerUp(new ItemStack(die)))));
    }

    /** {@code die} carrying the Power-up module (on top of its own modules). */
    private static ItemStack powerUp(ItemStack die) {
        Map<DiceModule, Integer> modules = new java.util.LinkedHashMap<>(DiceModules.of(die));
        modules.put(DiceModules.POWER_UP, 1);
        return DiceModules.set(die, modules);
    }

    /** {@code tile} (holding a cartridge) as a REI entry; its tooltip and face tell its role (TileContents). */
    private static EntryStack<ItemStack> roleTile(ItemStack tile) {
        return EntryStacks.of(tile);
    }
}
