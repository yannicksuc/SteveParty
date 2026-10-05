package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.recipes.TradingStallRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The {@code steveparty:trading_stall} recipe is a plain shaped recipe in JSON, replaced here by a
 * {@link TradingStallRecipe} (the carpets' colours go to the stall). The recipe manager keeps its recipes in two maps
 * that {@link RecipeManager#setRecipes} rebuilds: the recipes the server loads go through it at the end of
 * {@code apply}, and the ones the client receives through it directly.
 */
@Mixin(RecipeManager.class)
public abstract class PreparedRecipesMixin {
    @Shadow
    private boolean errored;

    @Shadow
    public abstract Collection<RecipeEntry<?>> values();

    @Shadow
    public abstract void setRecipes(Iterable<RecipeEntry<?>> recipes);

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
            at = @At("TAIL"))
    private void steveparty$replaceAfterLoad(CallbackInfo ci) {
        boolean wasErrored = errored;
        setRecipes(List.copyOf(values()));
        errored = wasErrored;
    }

    @ModifyVariable(method = "setRecipes", at = @At("HEAD"), argsOnly = true)
    private Iterable<RecipeEntry<?>> replaceShapedWithTrading(Iterable<RecipeEntry<?>> original) {
        List<RecipeEntry<?>> modified = new ArrayList<>();

        for (RecipeEntry<?> entry : original) {
            if (entry.value() instanceof ShapedRecipe shaped && !(shaped instanceof TradingStallRecipe)
                    && entry.id().getNamespace().equals(Steveparty.MOD_ID)
                    && entry.id().getPath().equals("trading_stall")) {
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
            modified.add(entry);
        }

        return modified;
    }
}
