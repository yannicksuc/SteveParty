package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.recipes.TradingStallRecipe;
import net.minecraft.recipe.PreparedRecipes;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(PreparedRecipes.class)
public class PreparedRecipesMixin {

    @ModifyVariable(
            method = "of",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static Iterable<RecipeEntry<?>> replaceShapedWithTrading(Iterable<RecipeEntry<?>> original) {
        List<RecipeEntry<?>> modified = new ArrayList<>();

        for (RecipeEntry<?> entry : original) {
            if (entry.value() instanceof ShapedRecipe shaped) {
                // Ici tu peux filtrer sur ton critère (ex: ID de recette spécifique)
                if (entry.id().getValue().getPath().equals("trading_stall")) {
                    ShapedRecipeAccessor acc = (ShapedRecipeAccessor) shaped;

                    TradingStallRecipe custom = new TradingStallRecipe(
                            acc.getGroup(),
                            acc.getCategory(),
                            acc.getRaw(),
                            acc.getResult(),
                            acc.getShowNotification()
                    );

                    modified.add(new RecipeEntry<>(entry.id(), custom));
                    continue;
                }
            }
            modified.add(entry);
        }

        return modified;
    }
}
