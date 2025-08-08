package fr.lordfinn.steveparty.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RawShapedRecipe;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ShapedRecipe.class)
public interface ShapedRecipeAccessor {
    @Accessor("raw")
    RawShapedRecipe getRaw();

    @Accessor("result")
    ItemStack getResult();

    @Accessor("group")
    String getGroup();

    @Accessor("category")
    CraftingRecipeCategory getCategory();

    @Accessor("showNotification")
    boolean getShowNotification();
}
