package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.dice.DiceModule;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.dice.DiceModulesComponent;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.recipes.DiceModuleRecipe;
import fr.lordfinn.steveparty.recipes.MultiDiceRecipe;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.StonecuttingRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.collection.DefaultedList;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.die;
import static fr.lordfinn.steveparty.gametest.DiceTestKit.face;
import static fr.lordfinn.steveparty.gametest.DiceTestKit.with;

/**
 * The recipes of the special dice faces and of the dice modules, the modules added to a die at the crafting table,
 * and the Double / Triple Dice keeping what their dice carry.
 */
public class DiceRecipeGameTests implements FabricGameTest {

    private static Optional<RecipeEntry<CraftingRecipe>> recipe(TestContext context, ItemStack... grid) {
        CraftingRecipeInput input = CraftingRecipeInput.create(grid.length, 1, List.of(grid));
        return context.getWorld().getServer().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, context.getWorld());
    }

    /** What a row of the crafting grid gives (empty if no recipe matches). */
    private static ItemStack craft(TestContext context, ItemStack... grid) {
        CraftingRecipeInput input = CraftingRecipeInput.create(grid.length, 1, List.of(grid));
        return recipe(context, grid).map(entry -> entry.value().craft(input, context.getWorld().getRegistryManager())).orElse(ItemStack.EMPTY);
    }

    /** What the stonecutter offers for an item. */
    private static Set<Item> cut(TestContext context, Item input) {
        Set<Item> results = new HashSet<>();
        SingleStackRecipeInput single = new SingleStackRecipeInput(new ItemStack(input));
        for (RecipeEntry<?> entry : context.getWorld().getServer().getRecipeManager().values()) {
            if (entry.value() instanceof StonecuttingRecipe recipe && recipe.matches(single, context.getWorld()))
                results.add(recipe.craft(single, context.getWorld().getRegistryManager()).getItem());
        }
        return results;
    }

    private static ItemStack module(DiceModule module) {
        return new ItemStack(module.item());
    }

    private static ItemStack blank() {
        return new ItemStack(ModItems.blankDiceFace());
    }

    // ---------------------------------------------------------------- faces and module items

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void specialFacesAreCraftedFromABlankFace(TestContext context) {
        context.assertTrue(craft(context, blank(), new ItemStack(ModItems.COIN)).isOf(face("coin_dice_face_1")), "blank + coin: coins +1");
        context.assertTrue(craft(context, blank(), new ItemStack(Items.SPIDER_EYE)).isOf(face("debt_dice_face_1")), "blank + spider eye: debt -1");
        context.assertTrue(craft(context, blank(), new ItemStack(Items.ENDER_PEARL)).isOf(face("swap_dice_face")), "blank + ender pearl: swap");

        // The stonecutter: numbers (0 included) from a blank face, any value of a coin / debt face from another one
        Set<Item> fromBlank = cut(context, ModItems.blankDiceFace());
        context.assertTrue(fromBlank.contains(face("dice_face_0")) && fromBlank.contains(face("dice_face_7"))
                && fromBlank.contains(face("premium_dice_face_3")) && fromBlank.contains(face("cursed_dice_face_2")), "the numbers are cut from a blank face");
        context.assertTrue(!fromBlank.contains(face("coin_dice_face_1")) && !fromBlank.contains(face("debt_dice_face_4"))
                && !fromBlank.contains(face("swap_dice_face")), "the special faces are not: they need their ingredient");
        Set<Item> fromCoin = cut(context, face("coin_dice_face_1"));
        for (int value = 2; value <= 10; value++)
            context.assertTrue(fromCoin.contains(face("coin_dice_face_" + value)), "coins +1 is cut into +" + value);
        context.assertTrue(fromCoin.contains(ModItems.blankDiceFace()) && !fromCoin.contains(face("debt_dice_face_2")), "and back into a blank face; not into a debt");
        Set<Item> fromDebt = cut(context, face("debt_dice_face_6"));
        context.assertTrue(fromDebt.contains(face("debt_dice_face_1")) && fromDebt.contains(face("debt_dice_face_10"))
                && !fromDebt.contains(face("debt_dice_face_6")), "a debt face is cut into the other values");
        context.assertTrue(cut(context, face("swap_dice_face")).equals(Set.of(ModItems.blankDiceFace())), "a swap face goes back to a blank one");
        context.assertTrue(cut(context, face("dice_face_0")).contains(ModItems.blankDiceFace()), "a face 0 too");
        context.complete();
    }

    /** What a 3 x 3 crafting grid gives, its slots row by row (empty if no recipe matches). */
    private static ItemStack craft3x3(TestContext context, ItemStack... grid) {
        CraftingRecipeInput input = CraftingRecipeInput.create(3, 3, List.of(grid));
        return context.getWorld().getServer().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, context.getWorld())
                .map(entry -> entry.value().craft(input, context.getWorld().getRegistryManager())).orElse(ItemStack.EMPTY);
    }

    /** A blank module is a blank face set in 4 gold nuggets. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aBlankModuleIsABlankFaceInGold(TestContext context) {
        ItemStack none = ItemStack.EMPTY, gold = new ItemStack(Items.GOLD_NUGGET);
        ItemStack result = craft3x3(context, none, gold, none, gold, blank(), gold, none, gold, none);
        context.assertTrue(result.isOf(ModItems.BLANK_DICE_MODULE) && result.getCount() == 1, "a blank module, got " + result);
        context.complete();
    }

    /** A module: its ingredient on top of a blank module, ringed with seven star fragments of its colour. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void moduleItemsAreCraftedFromABlankModuleAndFragments(TestContext context) {
        Map<DiceModule, Item[]> recipes = Map.of(
                DiceModules.SLOW, new Item[]{ModItems.LIGHT_BLUE_STAR_FRAGMENT, Items.CLOCK},
                DiceModules.CHOICE, new Item[]{ModItems.BLUE_STAR_FRAGMENT, Items.COMPASS},
                DiceModules.POWER_UP, new Item[]{ModItems.PURPLE_STAR_FRAGMENT, Items.ECHO_SHARD},
                DiceModules.LUCKY, new Item[]{ModItems.GREEN_STAR_FRAGMENT, Items.RABBIT_FOOT},
                DiceModules.REROLL, new Item[]{ModItems.ORANGE_STAR_FRAGMENT, Items.WIND_CHARGE},
                DiceModules.REVERSED, new Item[]{ModItems.RED_STAR_FRAGMENT, Items.FERMENTED_SPIDER_EYE},
                DiceModules.SKELETON_KEY, new Item[]{ModItems.YELLOW_STAR_FRAGMENT, Items.TRIPWIRE_HOOK},
                DiceModules.HOMING, new Item[]{ModItems.MAGENTA_STAR_FRAGMENT, Items.ENDER_EYE},
                DiceModules.FIRECRACKER, new Item[]{ModItems.PINK_STAR_FRAGMENT, Items.TNT});
        context.assertEquals(recipes.size(), DiceModules.all().size(), "every module has its recipe");
        recipes.forEach((module, parts) -> {
            ItemStack f = new ItemStack(parts[0]), i = new ItemStack(parts[1]), m = new ItemStack(ModItems.BLANK_DICE_MODULE);
            ItemStack result = craft3x3(context, f, i, f, f, m, f, f, f, f);
            context.assertTrue(result.isOf(module.item()) && result.getCount() == 1,
                    module + " from " + parts[0] + " and " + parts[1] + ", got " + result);
            // Another colour does not make it
            ItemStack w = new ItemStack(ModItems.WHITE_STAR_FRAGMENT);
            context.assertTrue(craft3x3(context, w, i, w, w, m, w, w, w, w).isEmpty(), module + " needs its own colour");
        });
        context.complete();
    }

    // ---------------------------------------------------------------- modules added at the crafting table

    /** A die + a module item: the die with the module; the module item stays in the grid. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aModuleIsAddedToADieAtTheCraftingTable(TestContext context) {
        ItemStack forged = die("dice_face_2", "coin_dice_face_5");
        ItemStack result = craft(context, forged, module(DiceModules.POWER_UP));
        context.assertTrue(result.isOf(ModItems.DEFAULT_DICE) && result.getCount() == 1, "a die, got " + result);
        context.assertTrue(DiceModules.has(result, DiceModules.POWER_UP), "carrying the module");
        context.assertEquals(result.get(DiceFacesComponent.TYPE), forged.get(DiceFacesComponent.TYPE), "its faces are kept");
        context.assertTrue(recipe(context, forged, module(DiceModules.POWER_UP)).orElseThrow().value() instanceof DiceModuleRecipe, "the module recipe");

        // The module item is not consumed: it is the remainder of its slot
        CraftingRecipeInput input = CraftingRecipeInput.create(3, 1, List.of(forged, module(DiceModules.POWER_UP), new ItemStack(DiceModules.LUCKY.item(), 3)));
        DefaultedList<ItemStack> remainders = recipe(context, forged, module(DiceModules.POWER_UP), new ItemStack(DiceModules.LUCKY.item(), 3))
                .orElseThrow().value().getRemainder(input);
        context.assertTrue(remainders.get(0).isEmpty(), "the die is used up");
        context.assertTrue(remainders.get(1).isOf(DiceModules.POWER_UP.item()) && remainders.get(1).getCount() == 1, "the module comes back");
        context.assertTrue(remainders.get(2).isOf(DiceModules.LUCKY.item()) && remainders.get(2).getCount() == 1, "one per slot comes back");

        // The modules a die already carries are kept
        ItemStack more = craft(context, result, module(DiceModules.SLOW));
        context.assertTrue(DiceModules.has(more, DiceModules.POWER_UP) && DiceModules.has(more, DiceModules.SLOW), "Power-up kept, Slow added");
        context.complete();
    }

    /** One recipe per module, shown in the recipe book: a die and the module item give the die carrying it. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void eachModuleHasItsRecipeInTheRecipeBook(TestContext context) {
        for (DiceModule module : DiceModules.all()) {
            net.minecraft.util.Identifier key = fr.lordfinn.steveparty.Steveparty.id("dice_with_module_" + module.id());
            RecipeEntry<?> entry = context.getWorld().getServer().getRecipeManager().get(key).orElse(null);
            context.assertTrue(entry != null && entry.value() instanceof DiceModuleRecipe recipe && recipe.module() == module, "the recipe of " + module);
            context.assertTrue(!entry.value().isIgnoredInRecipeBook(), "shown in the recipe book");
            context.assertTrue(!entry.value().getIngredients().isEmpty(), "it can be placed from the recipe book");
            List<ItemStack> results = List.of(entry.value().getResult(context.getWorld().getRegistryManager()));
            context.assertTrue(results.size() == 1 && DiceModules.of(results.getFirst()).equals(Map.of(module, 1)), "its result: a die carrying " + module);
            // Each grid is crafted by one recipe: the one of its first module
            ItemStack plain = new ItemStack(ModItems.DEFAULT_DICE);
            context.assertTrue(recipe(context, plain, module(module)).orElseThrow().id().equals(key), "a die and " + module + ": its own recipe");
        }
        RecipeEntry<CraftingRecipe> mixed = recipe(context, module(DiceModules.HOMING), new ItemStack(ModItems.DEFAULT_DICE), module(DiceModules.SLOW)).orElseThrow();
        context.assertTrue(mixed.value() instanceof DiceModuleRecipe recipe && recipe.module() == DiceModules.SLOW, "several modules: the recipe of the first one");
        context.complete();
    }

    /** Several modules at once; a module that stacks adds as many as there are items of it in the grid. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void severalModulesAndStacking(TestContext context) {
        ItemStack plain = new ItemStack(ModItems.DEFAULT_DICE);
        ItemStack several = craft(context, module(DiceModules.LUCKY), plain, module(DiceModules.REVERSED), module(DiceModules.LUCKY), module(DiceModules.HOMING));
        context.assertEquals(DiceModules.of(several), Map.of(DiceModules.LUCKY, 2, DiceModules.REVERSED, 1, DiceModules.HOMING, 1), "three modules, Lucky twice");
        context.assertTrue(!several.contains(DiceFacesComponent.TYPE), "a plain die stays a plain die (1 to 10) carrying modules");

        ItemStack stacked = craft(context, several, module(DiceModules.LUCKY));
        context.assertEquals(DiceModules.count(stacked, DiceModules.LUCKY), 3, "one more Lucky: x3");
        ItemStack full = with(new ItemStack(ModItems.DEFAULT_DICE), DiceModules.REROLL, DiceModules.REROLL.maxCount() - 1);
        context.assertEquals(DiceModules.count(craft(context, full, module(DiceModules.REROLL)), DiceModules.REROLL), DiceModules.REROLL.maxCount(), "up to its maximum");

        // On a Double and a Triple Dice too
        ItemStack doubleDie = craft(context, new ItemStack(ModItems.DOUBLE_DICE), module(DiceModules.CHOICE));
        context.assertTrue(doubleDie.isOf(ModItems.DOUBLE_DICE) && DiceModules.has(doubleDie, DiceModules.CHOICE), "a Double Dice with Choice, got " + doubleDie);
        ItemStack tripleDie = craft(context, module(DiceModules.SKELETON_KEY), new ItemStack(ModItems.TRIPLE_DICE));
        context.assertTrue(tripleDie.isOf(ModItems.TRIPLE_DICE) && DiceModules.has(tripleDie, DiceModules.SKELETON_KEY), "a Triple Dice with the key");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void modulesThatCannotBeAddedGiveNoRecipe(TestContext context) {
        ItemStack plain = new ItemStack(ModItems.DEFAULT_DICE);
        ItemStack slow = with(plain.copy(), DiceModules.SLOW, 1);
        context.assertTrue(craft(context, slow, module(DiceModules.SLOW)).isEmpty(), "a module that doesn't stack, already there");
        context.assertTrue(craft(context, plain, module(DiceModules.SLOW), module(DiceModules.SLOW)).isEmpty(), "a module that doesn't stack, twice in the grid");
        ItemStack maxed = with(plain.copy(), DiceModules.LUCKY, DiceModules.LUCKY.maxCount());
        context.assertTrue(craft(context, maxed, module(DiceModules.LUCKY)).isEmpty(), "a module that stacks, beyond its maximum");
        context.assertTrue(craft(context, plain, plain.copy(), module(DiceModules.LUCKY)).isEmpty(), "two dice");
        context.assertTrue(craft(context, plain, module(DiceModules.LUCKY), new ItemStack(Items.STICK)).isEmpty(), "anything else in the grid");
        context.assertTrue(craft(context, module(DiceModules.LUCKY), module(DiceModules.SLOW)).isEmpty(), "no die");
        context.assertTrue(craft(context, blank(), module(DiceModules.LUCKY)).isEmpty(), "a face is not a die");
        context.complete();
    }

    // ---------------------------------------------------------------- Double / Triple Dice keep what their dice carry

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void multiDiceKeepTheModulesAndFacesOfTheirDice(TestContext context) {
        ItemStack plain = new ItemStack(ModItems.DEFAULT_DICE);
        ItemStack lucky2 = with(plain.copy(), DiceModules.LUCKY, 2);
        ItemStack lucky1PowerUp = with(with(plain.copy(), DiceModules.LUCKY, 1), DiceModules.POWER_UP, 1);

        // The union of the modules: the highest count of each
        ItemStack doubled = craft(context, lucky2, lucky1PowerUp);
        context.assertTrue(doubled.isOf(ModItems.DOUBLE_DICE), "a Double Dice, got " + doubled);
        context.assertEquals(DiceModules.of(doubled), Map.of(DiceModules.LUCKY, 2, DiceModules.POWER_UP, 1), "Lucky x2 (the highest), Power-up");
        context.assertTrue(recipe(context, lucky2, lucky1PowerUp).orElseThrow().value() instanceof MultiDiceRecipe, "the multi dice recipe");
        context.assertEquals(DiceModules.of(craft(context, lucky2, plain)), Map.of(DiceModules.LUCKY, 2), "with a plain die: its modules");

        ItemStack tripled = craft(context, lucky2, plain, with(plain.copy(), DiceModules.REVERSED, 1));
        context.assertTrue(tripled.isOf(ModItems.TRIPLE_DICE), "a Triple Dice, got " + tripled);
        context.assertEquals(DiceModules.of(tripled), Map.of(DiceModules.LUCKY, 2, DiceModules.REVERSED, 1), "the modules of the three dice");
        ItemStack fromDouble = craft(context, doubled, with(plain.copy(), DiceModules.SLOW, 1));
        context.assertTrue(fromDouble.isOf(ModItems.TRIPLE_DICE), "a Double Dice and a die: a Triple Dice, got " + fromDouble);
        context.assertEquals(DiceModules.of(fromDouble), Map.of(DiceModules.LUCKY, 2, DiceModules.POWER_UP, 1, DiceModules.SLOW, 1), "the modules of both");

        // Faces: the same on every die are kept; different faces don't combine
        ItemStack forged = die("dice_face_3", "coin_dice_face_2");
        ItemStack sameFaces = with(die("dice_face_3", "coin_dice_face_2"), DiceModules.CHOICE, 1);
        ItemStack forgedDouble = craft(context, forged, sameFaces);
        context.assertTrue(forgedDouble.isOf(ModItems.DOUBLE_DICE), "two dice with the same faces: a Double Dice, got " + forgedDouble);
        context.assertEquals(forgedDouble.get(DiceFacesComponent.TYPE), forged.get(DiceFacesComponent.TYPE), "carrying those faces");
        context.assertTrue(DiceModules.has(forgedDouble, DiceModules.CHOICE), "and the modules");
        context.assertTrue(craft(context, forged, die("dice_face_4")).isEmpty(), "different faces don't combine");
        context.assertTrue(craft(context, forged, lucky2).isEmpty(), "a forged die and a plain one neither");
        context.assertTrue(craft(context, forgedDouble, forged).isOf(ModItems.TRIPLE_DICE), "a forged Double Dice and a die with the same faces");

        // Plain dice: the ordinary recipes, nothing carried
        ItemStack plainDouble = craft(context, plain, plain.copy());
        context.assertTrue(plainDouble.isOf(ModItems.DOUBLE_DICE) && !plainDouble.contains(DiceModulesComponent.TYPE)
                && !plainDouble.contains(DiceFacesComponent.TYPE), "a plain Double Dice");
        context.assertTrue(!(recipe(context, plain, plain.copy()).orElseThrow().value() instanceof MultiDiceRecipe), "by the recipe of the recipe book");

        // Taking a Double Dice apart: plain ones only (the modules would be lost or doubled)
        ItemStack apart = craft(context, new ItemStack(ModItems.DOUBLE_DICE));
        context.assertTrue(apart.isOf(ModItems.DEFAULT_DICE) && apart.getCount() == 2, "a plain Double Dice gives its 2 dice back");
        context.assertTrue(craft(context, doubled).isEmpty(), "one carrying modules is not taken apart");
        context.assertTrue(craft(context, forgedDouble).isEmpty(), "one carrying faces neither");
        context.complete();
    }
}
