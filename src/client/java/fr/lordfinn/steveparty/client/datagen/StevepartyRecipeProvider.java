package fr.lordfinn.steveparty.client.datagen;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.data.server.recipe.RecipeExporter;
import net.minecraft.data.server.recipe.RecipeGenerator;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.recipe.book.RecipeCategory;
import java.util.List;
import net.minecraft.registry.RegistryWrapper;
import java.util.concurrent.CompletableFuture;

public class StevepartyRecipeProvider extends FabricRecipeProvider {
    public StevepartyRecipeProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeGenerator getRecipeGenerator(RegistryWrapper.WrapperLookup wrapperLookup, RecipeExporter recipeExporter) {
        return new RecipeGenerator(wrapperLookup, recipeExporter) {
            @Override
            public void generate() {
                //TagKey<Item> diceTag = StevepartyReferenceItemTagProvider.DICE_FACES_TAG;
                List<Item> allDice = ModItems.DICE_FACES;
                for (Item dice : allDice) {
                    if (dice == ModItems.DICE_FACES.get(0))
                        continue;
                    offerStonecuttingRecipe(
                            RecipeCategory.MISC,
                            dice,
                            ModItems.DICE_FACES.get(0)
                    );
                    offerStonecuttingRecipe(
                            RecipeCategory.MISC,
                            ModItems.DICE_FACES.get(0),
                            dice
                    );
                }
                createShaped(RecipeCategory.MISC, ModItems.DICE_FACES.get(0), 4) // output 4 blank dice faces
                        .pattern("IQ")
                        .pattern("QI")
                        .input('I', Items.IRON_INGOT)
                        .input('Q', Items.QUARTZ)
                        .criterion(hasItem(Items.IRON_INGOT), conditionsFromItem(Items.IRON_INGOT))
                        .criterion(hasItem(Items.QUARTZ), conditionsFromItem(Items.QUARTZ))
                        .offerTo(recipeExporter, "blank_dice_face_from_crafting");

                offerReversibleCompactingRecipes(
                        RecipeCategory.MISC,
                        ModItems.BLACK_STAR_FRAGMENT,
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.BLACK_STAR_FRAGMENTS_BLOCK
                );
                offerReversibleCompactingRecipes(
                        RecipeCategory.MISC,
                        ModItems.PURPLE_STAR_FRAGMENT,
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.PURPLE_STAR_FRAGMENTS_BLOCK
                );
                offerReversibleCompactingRecipes(
                        RecipeCategory.MISC,
                        ModItems.RED_STAR_FRAGMENT,
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.RED_STAR_FRAGMENTS_BLOCK
                );
                offerReversibleCompactingRecipes(
                        RecipeCategory.MISC,
                        ModItems.YELLOW_STAR_FRAGMENT,
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.YELLOW_STAR_FRAGMENTS_BLOCK
                );
                offerReversibleCompactingRecipes(
                        RecipeCategory.MISC,
                        ModItems.GREEN_STAR_FRAGMENT,
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.GREEN_STAR_FRAGMENTS_BLOCK
                );
                offerReversibleCompactingRecipes(
                        RecipeCategory.MISC,
                        ModItems.BLUE_STAR_FRAGMENT,
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.BLUE_STAR_FRAGMENTS_BLOCK
                );

            }
        };
    }

    @Override
    public String getName() {
        return "Steveparty Dice Recipe Provider";
    }
}
