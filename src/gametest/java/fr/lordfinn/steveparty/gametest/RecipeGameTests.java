package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryKey;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Recipes: every one reachable from the recipe book, and the crafts that used to be missing or lossy. */
public class RecipeGameTests implements FabricGameTest {

    /** @return what the crafting grid gives (empty if no recipe matches). */
    private static ItemStack result(TestContext context, int width, int height, ItemStack... grid) {
        CraftingRecipeInput input = CraftingRecipeInput.create(width, height, List.of(grid));
        Optional<RecipeEntry<CraftingRecipe>> recipe = context.getWorld().getServer().getRecipeManager()
                .getFirstMatch(RecipeType.CRAFTING, input, context.getWorld());
        return recipe.map(entry -> entry.value().craft(input, context.getWorld().getRegistryManager())).orElse(ItemStack.EMPTY);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyRecipeIsUnlockedByAnAdvancement(TestContext context) {
        ServerRecipeManager recipes = context.getWorld().getServer().getRecipeManager();
        Set<RegistryKey<Recipe<?>>> unlocked = new HashSet<>();
        for (AdvancementEntry advancement : context.getWorld().getServer().getAdvancementLoader().getAdvancements()) {
            for (RegistryKey<Recipe<?>> key : advancement.value().rewards().recipes()) {
                // An advancement rewarding a recipe id that doesn't exist never shows that recipe in the book
                if (key.getValue().getNamespace().equals(Steveparty.MOD_ID) || advancement.id().getNamespace().equals(Steveparty.MOD_ID))
                    context.assertTrue(recipes.get(key).isPresent(), advancement.id() + " unlocks a missing recipe " + key.getValue());
                unlocked.add(key);
            }
        }
        for (RecipeEntry<?> recipe : recipes.values()) {
            if (!recipe.id().getValue().getNamespace().equals(Steveparty.MOD_ID) || recipe.value().isIgnoredInRecipeBook()) continue;
            context.assertTrue(unlocked.contains(recipe.id()), "no advancement unlocks " + recipe.id().getValue());
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void polishedTerracottaIsCraftable(TestContext context) {
        ItemStack red = new ItemStack(Items.RED_TERRACOTTA);
        ItemStack polished = result(context, 2, 2, red, red, red, red);
        context.assertTrue(polished.isOf(ModBlocks.POLISHED_TERRACOTTA_BLOCKS[15].asItem()) && polished.getCount() == 4,
                "4 polished red terracotta, got " + polished);
        ItemStack plain = new ItemStack(Items.TERRACOTTA);
        ItemStack polishedPlain = result(context, 2, 2, plain, plain, plain, plain);
        context.assertTrue(polishedPlain.isOf(ModBlocks.POLISHED_TERRACOTTA_BLOCKS[0].asItem()) && polishedPlain.getCount() == 4,
                "4 polished terracotta, got " + polishedPlain);
        ItemStack p = new ItemStack(ModBlocks.POLISHED_TERRACOTTA_BLOCKS[15]);
        ItemStack bricks = result(context, 2, 2, p, p, p, p);
        context.assertTrue(bricks.isOf(ModBlocks.POLISHED_TERRACOTTA_BRICKS_BLOCKS[15].asItem()), "red terracotta bricks, got " + bricks);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void forgedDiceAreNotMergedIntoPlainOnes(TestContext context) {
        ItemStack plain = new ItemStack(ModItems.DEFAULT_DICE);
        context.assertTrue(result(context, 2, 1, plain, plain.copy()).isOf(ModItems.DOUBLE_DICE), "2 dice make a double dice");
        // Merging would silently drop the forged faces
        ItemStack forged = DiceFacesComponent.createDie(List.of(new ItemStack(ModItems.DICE_FACES.get(1))));
        context.assertTrue(forged.contains(DiceFacesComponent.TYPE), "forged die has faces");
        context.assertTrue(result(context, 2, 1, forged, plain).isEmpty(), "a forged die is not merged");
        context.assertTrue(result(context, 3, 1, forged, plain, plain.copy()).isEmpty(), "a forged die is not merged into a triple");
        context.assertTrue(result(context, 2, 1, new ItemStack(ModItems.DOUBLE_DICE), forged).isEmpty(),
                "a forged die is not added to a double");
        context.complete();
    }
}
