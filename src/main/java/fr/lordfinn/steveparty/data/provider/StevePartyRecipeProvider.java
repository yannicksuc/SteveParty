package fr.lordfinn.steveparty.data.provider;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.data.server.recipe.RecipeExporter;
import net.minecraft.data.server.recipe.RecipeGenerator;
import net.minecraft.recipe.book.RecipeCategory;
import net.minecraft.registry.RegistryWrapper;

import java.util.concurrent.CompletableFuture;

import static fr.lordfinn.steveparty.items.ModItems.DEFAULT_DICE;
import static net.minecraft.item.Items.*;

public class StevePartyRecipeProvider extends FabricRecipeProvider {

    public StevePartyRecipeProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeGenerator getRecipeGenerator(RegistryWrapper.WrapperLookup wrapperLookup, RecipeExporter recipeExporter) {
        return new RecipeGenerator(wrapperLookup, recipeExporter) {
            @Override
            public void generate() {
                createShaped(RecipeCategory.MISC, DEFAULT_DICE)
                        .input('I', IRON_INGOT)
                        .input('L', LAPIS_LAZULI)
                        .input('S', SPLASH_POTION)
                        .pattern("LIL")
                        .pattern("ISI")
                        .pattern("LIL")
//                        .criterion(hasItem(LAPIS_LAZULI), conditionsFromItemPredicates(new Predi))
//                        .criterion(hasItem(SPLASH_POTION), conditionsFromItemWithPotion(SPLASH_POTION, "luck")) //What do i do here ?)
                        .offerTo(exporter);
            }

/*            public static AdvancementCriterion<InventoryChangedCriterion.Conditions> conditionsFromItemWithPotion(ItemConvertible item, String potion) {
                new ComponentPredicate(List.of());
                POTION_CONTENTS
                POTION_COMPONENT
                        new SplashPotionItem()
                potionCompound.putString("potion", potion);
                ItemPredicate.Builder itemPredicateBuilder = ItemPredicate.Builder.create()
                        .items(Registries.ITEM)
                        .component(ComponentPredicate.EMPTY);
                return conditionsFromItemPredicates(itemPredicateBuilder.build());
            }*/
        };
    }

    /*@Override
    protected RecipeGenerator getRecipeGenerator(RegistryWrapper.WrapperLookup wrapperLookup, RecipeExporter recipeExporter) {
        RecipeProvider.
        offerShapedRecipe(DEFAULT_DICE, "default_dice", "LIL", "ISI", "LIL", LAPIS_LAZULI, IRON_INGOT, SPLASH_POTION);
        ShapedRecipeJsonBuilder.create(DEFAULT_DICE, RecipeCategory.MISC)
                .input('I', IRON_INGOT)
                .input('L', LAPIS_LAZULI)
                .input('S', SPLASH_POTION)
                .pattern("LIL")
                .pattern("ISI")
                .pattern("LIL")
                .criterion(hasItem(SPLASH_POTION),  )
                .offerTo(exporter);
        return null;
    }*/

    @Override
    public String getName() {
        return "Steve Party Recipes";
    }
}
